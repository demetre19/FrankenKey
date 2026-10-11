#!/usr/bin/env python3
"""Generate a compact next-word table aligned to a bundled Cdict.

The AOSP wordlists list up to three ranked continuations ("bigram=") for each
word. This script keeps them for head words whose frequency is at least
MIN_HEAD_FREQUENCY and maps every word to its Cdict index, so the decoder can
predict and rank continuations without loading strings.

Usage:
  gen_word_bigrams.py BIGRAM_WORDLIST.combined DICT.dict OUT.bin

BIGRAM_WORDLIST may differ from the wordlist the Cdict was built from (en_GB
and en_AU wordlists carry no bigrams, so they reuse en_US's); words missing
from the Cdict are skipped.

Output format (big-endian):
  "FKWB" magic, u8 version (1), u32 entries, u8 samples,
  samples x (u32 index, u8 length, UTF-8 word),
  entries x (u24 head, 3 x u24 follower, strongest first, 0xFFFFFF = none),
  sorted by head index.
"""

import ctypes
import os
import re
import struct
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_word_frequencies import FindResult, SAMPLE_COUNT, load_libcdict

MIN_HEAD_FREQUENCY = 80
FOLLOWERS = 3
NONE = 0xFFFFFF
WORD_RE = re.compile(r"^ word=([^,]*),f=(\d+)")
BIGRAM_RE = re.compile(r"^  bigram=([^,]*),f=(\d+)")


def u24(value):
  return struct.pack(">I", value)[1:]


def main(wordlist, dict_path, out_path):
  with tempfile.TemporaryDirectory() as tmp:
    lib = load_libcdict(tmp)
    data = open(dict_path, "rb").read()
    data_buf = ctypes.create_string_buffer(data, len(data))
    header = ctypes.create_string_buffer(256)
    dictionary = ctypes.create_string_buffer(256)
    if lib.cdict_of_string(data_buf, len(data), header) != 0:
      sys.exit("not a supported Cdict: " + dict_path)
    lib.cdict_get_dict(header, 0, dictionary)

    def index_of(word):
      encoded = word.encode("utf-8")
      result = FindResult()
      lib.cdict_find(dictionary, encoded, len(encoded), ctypes.byref(result))
      return result.index if result.found else None

    table = {}
    head = None
    head_frequency = 0
    followers = []

    def flush():
      if head is None or head_frequency < MIN_HEAD_FREQUENCY:
        return
      head_index = index_of(head)
      if head_index is None or head_index >= NONE:
        return
      ranked = sorted(followers, key=lambda f: (-f[0], f[1]))
      indexes = []
      for _, word in ranked:
        index = index_of(word)
        if index is not None and index < NONE and index not in indexes:
          indexes.append(index)
      if indexes and head_index not in table:
        table[head_index] = indexes[:FOLLOWERS]

    for line in open(wordlist, encoding="utf-8"):
      match = WORD_RE.match(line)
      if match:
        flush()
        head = match.group(1)
        head_frequency = int(match.group(2))
        followers = []
        continue
      match = BIGRAM_RE.match(line)
      if match and head is not None:
        followers.append((int(match.group(2)), match.group(1)))
    flush()

    samples = []
    buf = ctypes.create_string_buffer(256)
    heads = sorted(table)
    step = max(1, len(heads) // SAMPLE_COUNT)
    for index in heads[::step][:SAMPLE_COUNT]:
      length = lib.cdict_word(dictionary, index, buf, 255)
      if 0 < length < 256:
        samples.append((index, buf.raw[:length]))

    out = bytearray(b"FKWB")
    out += struct.pack(">BIB", 1, len(heads), len(samples))
    for index, word in samples:
      out += struct.pack(">IB", index, len(word)) + word
    for index in heads:
      out += u24(index)
      row = table[index] + [NONE] * (FOLLOWERS - len(table[index]))
      for follower in row:
        out += u24(follower)
    with open(out_path, "wb") as f:
      f.write(out)
    print("%s: %d head words" % (out_path, len(heads)))


if __name__ == "__main__":
  if len(sys.argv) != 4:
    sys.exit(__doc__)
  main(*sys.argv[1:])
