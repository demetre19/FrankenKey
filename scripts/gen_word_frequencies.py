#!/usr/bin/env python3
"""Generate an 8-bit unigram frequency table aligned to a bundled Cdict.

The Cdict format stores only a 4-bit frequency per word, and the upstream
builder wraps the highest-frequency cluster to 0 (the, and, of, to, in, a and
was are stored as 0). This script reads the pinned AOSP wordlist that the
Cdict was built from, then writes the original 0-255 frequencies indexed by
Cdict word index, so the decoder can rank by real frequency while the
upstream .dict stays byte-identical.

Usage:
  gen_word_frequencies.py WORDLIST.combined DICT.dict OUT.bin

Output format (big-endian):
  "FKWF" magic, u8 version (1), u32 count, u8 repair_min, u8 samples,
  samples x (u32 index, u8 length, UTF-8 word), then count x u8 frequency.
  repair_min is the lowest source frequency among words whose Cdict level is
  15; the runtime uses it to repair wrapped 4-bit levels. Samples let the
  runtime refuse a table that does not match the loaded dictionary.
"""

import ctypes
import os
import re
import struct
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIBCDICT = os.path.join(ROOT, "vendor", "cdict", "libcdict")
SAMPLE_COUNT = 16
WORD_RE = re.compile(r"^ word=([^,]*),f=(\d+)")


class FindResult(ctypes.Structure):
  _fields_ = [("found", ctypes.c_bool), ("index", ctypes.c_int),
      ("prefix_ptr", ctypes.c_ssize_t), ("original_index", ctypes.c_int),
      ("owner", ctypes.c_void_p)]


def load_libcdict(tmp):
  lib_path = os.path.join(tmp, "libcdict_host.so")
  subprocess.check_call(["cc", "-O2", "-shared", "-fPIC", "-I", LIBCDICT,
      os.path.join(LIBCDICT, "libcdict.c"), "-o", lib_path])
  lib = ctypes.CDLL(lib_path)
  lib.cdict_find.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int,
      ctypes.POINTER(FindResult)]
  lib.cdict_freq.argtypes = [ctypes.c_void_p, ctypes.c_int]
  lib.cdict_word.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_char_p,
      ctypes.c_int]
  return lib


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

    frequencies = {}
    levels = {}
    missing = 0
    for line in open(wordlist, encoding="utf-8"):
      match = WORD_RE.match(line)
      if not match:
        continue
      word = match.group(1).encode("utf-8")
      result = FindResult()
      lib.cdict_find(dictionary, word, len(word), ctypes.byref(result))
      if not result.found or result.index != result.original_index:
        missing += 1
        continue
      index = result.index
      frequency = min(255, int(match.group(2)))
      frequencies[index] = max(frequencies.get(index, 0), frequency)
      levels[index] = lib.cdict_freq(dictionary, index)

    count = max(frequencies) + 1
    top = [f for i, f in frequencies.items() if levels[i] == 15 and f > 0]
    repair_min = min(top) if top else 255
    table = bytearray(count)
    for index, frequency in frequencies.items():
      table[index] = frequency

    samples = []
    step = max(1, count // SAMPLE_COUNT)
    buf = ctypes.create_string_buffer(256)
    for index in range(0, count, step):
      if len(samples) == SAMPLE_COUNT:
        break
      length = lib.cdict_word(dictionary, index, buf, 255)
      word = buf.raw[:length]
      if 0 < length < 256:
        samples.append((index, word))

    out = bytearray(b"FKWF")
    out += struct.pack(">BIBB", 1, count, repair_min, len(samples))
    for index, word in samples:
      out += struct.pack(">IB", index, len(word)) + word
    out += table
    with open(out_path, "wb") as f:
      f.write(out)
    print("%s: %d indexes, %d words, repair_min=%d, %d skipped"
        % (out_path, count, len(frequencies), repair_min, missing))


if __name__ == "__main__":
  if len(sys.argv) != 4:
    sys.exit(__doc__)
  main(*sys.argv[1:])
