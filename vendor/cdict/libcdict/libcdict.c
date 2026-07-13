#include "libcdict.h"
#include "libcdict_format.h"
#include <limits.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static inline int decode_int24(uint8_t const *ar)
{
  uint32_t value = ((uint32_t)ar[0] << 16) |
    ((uint32_t)ar[1] << 8) | ar[2];
  int64_t signed_value = (value & UINT32_C(0x800000)) ?
    (int64_t)value - INT64_C(0x1000000) : value;
  return (int)signed_value;
}

static inline int decode_int32(uint8_t const *ar)
{
  uint32_t value = ((uint32_t)ar[0] << 24) |
    ((uint32_t)ar[1] << 16) | ((uint32_t)ar[2] << 8) | ar[3];
  int64_t signed_value = (value & UINT32_C(0x80000000)) ?
    (int64_t)value - INT64_C(0x100000000) : value;
  return (int)signed_value;
}

static inline int min(int a, int b) { return (a < b) ? a : b; }

static bool format_valid(int format)
{
  return format == FORMAT_4_BITS || format == FORMAT_8_BITS ||
    format == FORMAT_16_BITS || format == FORMAT_24_BITS;
}

static bool file_range_valid(int size, int offset, size_t length)
{
  return size >= 0 && offset >= 0 && (size_t)offset <= (size_t)size &&
    length <= (size_t)size - (size_t)offset;
}

static bool initial_node_valid(uint8_t const *data, int size, int offset)
{
  if (!file_range_valid(size, offset, 1))
    return false;
  uint8_t const *node = data + offset;
  if (NODE_KIND(node) == BRANCHES)
  {
    if (!file_range_valid(size, offset, sizeof(branches_t)))
      return false;
    branches_t const *b = (void const*)node;
    int branches_format = BRANCHES_BRANCHES_FORMAT(b);
    int numbers_format = BRANCHES_NUMBERS_FORMAT(b);
    if (!format_valid(branches_format) || !format_valid(numbers_format))
      return false;
    size_t bytes = sizeof(branches_t) + b->length +
      FORMAT_ARRAY_SIZE(branches_format, b->length) +
      FORMAT_ARRAY_SIZE(numbers_format, b->length);
    return file_range_valid(size, offset, bytes);
  }
  if (NODE_KIND(node) == PREFIX)
  {
    if (!file_range_valid(size, offset, sizeof(prefix_t)))
      return false;
    prefix_t const *p = (void const*)node;
    int length = PREFIX_LENGTH(p);
    return length > 0 && length <= PREFIX_MAX_LENGTH &&
      file_range_valid(size, offset, sizeof(prefix_t) + (size_t)length);
  }
  return false;
}

/** ************************************************************************
    cdict_of_string
    ************************************************************************ */

cdict_cnstr_result_t cdict_of_string(char const *data, int size,
    cdict_header_t *dst)
{
  size_t const fixed_header_size = offsetof(header_t, dicts);
  if (data == NULL || dst == NULL || size < 0 ||
      (size_t)size < fixed_header_size)
    return CDICT_NOT_A_DICTIONARY;
  header_t const *src_h = (void const*)data;
  if (memcmp(src_h->magic, HEADER_MAGIC, sizeof(src_h->magic)) != 0)
    return CDICT_NOT_A_DICTIONARY;
  if (src_h->version != FORMAT_VERSION)
    return CDICT_UNSUPPORTED_FORMAT;
  size_t const headers_size =
    (size_t)src_h->dict_count * sizeof(dict_header_t);
  if (headers_size > (size_t)size - fixed_header_size)
    return CDICT_NOT_A_DICTIONARY;

  uint8_t const *bytes = (void const*)data;
  for (int i = 0; i < src_h->dict_count; i++)
  {
    dict_header_t const *dh = &src_h->dicts[i];
    int name_offset = decode_int32(dh->name_off);
    int root_offset = decode_int32(dh->root_ptr);
    int freq_offset = decode_int32(dh->freq_off);
    int aliases_length = decode_int24(dh->aliases_length);
    int aliases_keys_offset = decode_int32(dh->aliases_keys);
    int aliases_values_offset = decode_int32(dh->aliases_values);
    if (!file_range_valid(size, name_offset, 1) ||
        memchr(bytes + name_offset, '\0', (size_t)size - name_offset) == NULL ||
        !initial_node_valid(bytes, size, root_offset) ||
        !file_range_valid(size, freq_offset, 1) || aliases_length < 0)
      return CDICT_NOT_A_DICTIONARY;
    if (aliases_length != 0)
    {
      int keys_format = ALIASES_KEY_FORMAT(dh);
      int values_format = ALIASES_VALUES_FORMAT(dh);
      if (!format_valid(keys_format) || !format_valid(values_format))
        return CDICT_NOT_A_DICTIONARY;
      size_t keys_size = FORMAT_ARRAY_SIZE(keys_format, aliases_length);
      size_t values_size = FORMAT_ARRAY_SIZE(values_format, aliases_length);
      if (!file_range_valid(size, aliases_keys_offset, keys_size) ||
          !file_range_valid(size, aliases_values_offset, values_size))
        return CDICT_NOT_A_DICTIONARY;
    }
    else if (!file_range_valid(size, aliases_keys_offset, 0) ||
        !file_range_valid(size, aliases_values_offset, 0))
      return CDICT_NOT_A_DICTIONARY;
  }

  *dst = (cdict_header_t){
    .data = data,
    .n_dicts = src_h->dict_count,
    .total_size = size
  };
  return CDICT_OK;
}

void cdict_get_dict(cdict_header_t const *header, int i, cdict_t *dst)
{
  uint8_t const *data = (void const*)header->data;
  dict_header_t const *dh = &((header_t const*)data)->dicts[i];
  *dst = (cdict_t){
    .name = (char const*)data + decode_int32(dh->name_off),
    .root_node = data + decode_int32(dh->root_ptr),
    .freq = data + decode_int32(dh->freq_off),
    .aliases_header = dh->aliases_header,
    .aliases_length = decode_int24(dh->aliases_length),
    .aliases_keys = data + decode_int32(dh->aliases_keys),
    .aliases_values = data + decode_int32(dh->aliases_values),
    .data_begin = data,
    .data_end = data + header->total_size,
  };
}

char const* cdict_cnstr_result_to_string(cdict_cnstr_result_t r)
{
  switch (r)
  {
    case CDICT_OK: return "OK";
    case CDICT_NOT_A_DICTIONARY: return "Not a dictionary";
    case CDICT_UNSUPPORTED_FORMAT: return "Unsupported format";
  }
  return "";
}

int cdict_format_version() { return FORMAT_VERSION; }

/** ************************************************************************
    Aliases
    ************************************************************************ */

/** Search for [key] in the complete tree encoded in array [ar] with format
    [fmt] and length [length]. Return [-1] if the key is not found. */
static int complete_tree_search_unsigned(uint8_t const *ar, format_t fmt,
    unsigned int length, unsigned int key)
{
  unsigned int i = 0;
  while (i < length)
  {
    unsigned int ar_i = sized_int_array_unsigned(ar, fmt, i);
    if (ar_i == key)
      return i;
    i = i * 2 + ((key < ar_i) ? 1 : 2);
  }
  return -1;
}

/** Return the new index if an alias exists or [-1]. */
static int resolve_alias(cdict_t const *dict, int index)
{
  if (dict->aliases_length == 0)
    return -1;
  int i =
    complete_tree_search_unsigned(dict->aliases_keys,
        ALIASES_KEY_FORMAT(dict), dict->aliases_length, index);
  if (i < 0)
    return -1;
  return sized_int_array_unsigned(dict->aliases_values,
        ALIASES_VALUES_FORMAT(dict), i);
}

/** ************************************************************************
    cdict_find
    ************************************************************************ */

static void cdict_find_node(void const *parent_node, int ptr,
    char const *word, char const *end, int index, cdict_result_t *result);

static void cdict_find_branches(branches_t const *b,
    char const *word, char const *end, int index, cdict_result_t *result)
{
  uchar c = *word;
  int len = b->length;
  for (int i = 0; i < len;)
  {
    // [l] is NUL when stepping outside of the tree but [k] cannot be NUL so we
    // don't have to check for this case.
    uchar l = b->labels[i];
    if (c == l)
    {
      index += branch_number(b, i);
      cdict_find_node(b, branch(b, i), word + 1, end, index, result);
      return;
    }
    else if (c < l)
      i = i * 2 + 1;
    else
      i = i * 2 + 2;
  }
}

static void find_ends(intptr_t prefix_ptr, int index, bool found,
    cdict_result_t *result)
{
  result->found = found;
  result->index = index;
  result->prefix_ptr = prefix_ptr;
  result->original_index = index;
}

static void cdict_find_prefix(prefix_t const *node,
    char const *word, char const *end, int index, cdict_result_t *result)
{
  uchar const *prefix = node->prefix;
  uchar const *prefix_end = prefix + PREFIX_LENGTH(node);
  int next_ptr = decode_int24(node->next_ptr);
  while (true)
  {
    uchar c = *word++;
    if (c != *(prefix++)) return; // Prefix doesn't match
    if (prefix == prefix_end) // Prefix matches
      return cdict_find_node(node, next_ptr, word, end, index, result);
    if (word == end) // Query ends
      return find_ends(PREFIX_PTR(next_ptr, node), index, false, result);
  }
}

static void cdict_find_node(void const *parent_node, int ptr,
    char const *word, char const *end, int index, cdict_result_t *result)
{
  bool is_final = PTR_IS_FINAL(ptr);
  void const *node = PTR_NODE(ptr, parent_node);
  if (word == end)
    return find_ends(PREFIX_PTR(ptr, parent_node), index, is_final, result);
  if (is_final)
    index++;
  switch (NODE_KIND(node))
  {
    case BRANCHES:
      return cdict_find_branches(node, word, end, index, result);
    case PREFIX: return cdict_find_prefix(node, word, end, index, result);
    default: return;
  }
}

#define RESULT_T_INIT ((cdict_result_t){ \
    .found = false, .index = 0, .prefix_ptr = 0, .original_index = 0, \
    .owner = NULL })

void cdict_find(cdict_t const *dict, char const *word, int word_size,
    cdict_result_t *result)
{
  *result = RESULT_T_INIT;
  result->owner = dict;
  cdict_find_node(dict->root_node, 0, word, word + word_size, 0,
      result);
  if (result->found)
  {
    int alias = resolve_alias(dict, result->index);
    if (alias >= 0)
      result->index = alias;
  }
}

bool cdict_result_belongs_to(cdict_t const *dict,
    cdict_result_t const *result)
{
  if (dict == NULL || result == NULL || result->owner != dict
      || result->original_index < 0)
    return false;
  if (result->prefix_ptr == 0)
    return true;
  uintptr_t parent = (uintptr_t)PREFIX_PTR_PARENT(result->prefix_ptr);
  return parent >= (uintptr_t)dict->data_begin
    && parent < (uintptr_t)dict->data_end;
}


/** ************************************************************************
    cdict_freq
    ************************************************************************ */

int cdict_freq(cdict_t const *dict, int index)
{
  if (dict == NULL || index < 0 || dict->freq == NULL ||
      dict->data_begin == NULL || dict->data_end == NULL)
    return 0;
  uintptr_t begin = (uintptr_t)dict->data_begin;
  uintptr_t end = (uintptr_t)dict->data_end;
  uintptr_t frequency = (uintptr_t)dict->freq;
  if (end < begin || frequency < begin || frequency >= end ||
      (size_t)(index / 2) >= (size_t)(end - frequency))
    return 0;
  uint8_t f = dict->freq[index / 2];
  if (index & 1) f = f >> 4;
  return f & 0xF;
}

/** ************************************************************************
    cdict_word
    ************************************************************************ */

static int cdict_word_node(void const *parent_node, int ptr, int index,
    char *dst, int dsti, int max_len);

static int cdict_word_branches(branches_t const *b, int index,
    char *dst, int dsti, int max_len)
{
  // The 'number' field of each transition is in the same order as the labels.
  int len = b->length;
  int next = 0;
  int next_number = 0;
  for (int i = 0; i < len;)
  {
    int ni = branch_number(b, i);
    int bi = branch(b, i);
    if (ni > index)
      i = i * 2 + 1;
    else
    {
      next = bi;
      next_number = ni;
      dst[dsti] = b->labels[i];
      i = i * 2 + 2;
    }
  }
  if (next == 0)
    return dsti;
  return cdict_word_node(b, next, index - next_number, dst, dsti + 1,
      max_len);
}

static int cdict_word_prefix(prefix_t const *p, int index,
    char *dst, int dsti, int max_len)
{
  int end = dsti + PREFIX_LENGTH(p);
  uchar const *prefix = p->prefix;
  if (end > max_len)
    end = max_len;
  while (dsti < end)
    dst[dsti++] = *(prefix++);
  return cdict_word_node(p, decode_int24(p->next_ptr), index, dst, end, max_len);
}

static int cdict_word_node(void const *parent_node, int ptr, int index,
    char *dst, int dsti, int max_len)
{
  if (dsti >= max_len)
    return dsti;
  void const *node = PTR_NODE(ptr, parent_node);
  if (PTR_IS_FINAL(ptr))
  {
    if (index == 0)
      return dsti;
    index--;
  }
  switch (NODE_KIND(node))
  {
    case BRANCHES: return cdict_word_branches(node, index, dst, dsti, max_len);
    case PREFIX: return cdict_word_prefix(node, index, dst, dsti, max_len);
  }
  return dsti;
}

int cdict_word(cdict_t const *dict, int index, char *dst, int max_length)
{
  return cdict_word_node(dict->root_node, 0, index, dst, 0, max_length);
}

/** ************************************************************************
    Priority max-queue storing the N most frequent words.
    ************************************************************************ */

typedef struct
{
  unsigned int freq:4;
  unsigned int index:28;
} word_freq_t;

/** Orders by decreasing frequencies. Elements with the same frequency are
    ordered by increasing [index] value, which corresponds to the alphabetical
    order. */
static int word_freq_compare(void const *a_, void const *b_)
{
  word_freq_t const *a = a_;
  word_freq_t const *b = b_;
  int d = b->freq - a->freq;
  if (d != 0) return d;
  return a->index - b->index;
}

typedef struct
{
  word_freq_t *q;
  int ends; // Cannot be bigger than [max_length].
  int max_length;
} priority_t;

static void priority_init(priority_t *p, word_freq_t *q, int q_len)
{
  *p = (priority_t){ .q = q, .ends = 0, .max_length = q_len };
}

/** This modifies the structure [p] inplace and then empties it. Returns the
    number of elements written to [dst]. */
static int priority_to_sorted_array(priority_t *p, int *dst, int count)
{
  qsort(p->q, p->ends, sizeof(word_freq_t), &word_freq_compare);
  count = min(count, p->ends);
  for (int i = 0; i < count; i++)
    dst[i] = p->q[i].index;
  p->ends = 0;
  return count;
}

/** Push an element and remove the lowest ranking element at the same time.
    The removed element can be the same as the element being added. This
    function works even when the queue is full. */
static void priority_pushpop(priority_t *p, word_freq_t new)
{
  word_freq_t *q = p->q;
  if (p->ends == 0 || word_freq_compare(&q[0], &new) <= 0)
    return;
  int i = 0, biggest = 0, ends = p->ends;
  while (true)
  {
    int left = i * 2 + 1, right = i * 2 + 2;
    if (left >= ends)
      break;
    biggest = (right < ends && word_freq_compare(&q[right], &q[left]) > 0) ?
      right : left;
    if (word_freq_compare(&q[biggest], &new) <= 0)
      break;
    q[i] = q[biggest];
    i = biggest;
  }
  q[i] = new;
}

/** Add a word to the priority queue, possibly removing an other word from it.
    Do nothing if the queue is full and the new word ranks higher than any
    other word already stored. Words are ordered with [word_freq_compare()]. */
static void priority_add(priority_t *p, int freq, int index)
{
  word_freq_t new = (word_freq_t){ .freq = freq, .index = index };
  if (p->ends == p->max_length)
  {
    priority_pushpop(p, new);
    return;
  }
  word_freq_t *q = p->q;
  int i = p->ends;
  p->ends++;
  while (i > 0)
  {
    int parent = (i - 1) / 2;
    if (word_freq_compare(&q[parent], &new) >= 0)
      break;
    q[i] = q[parent];
    i = parent;
  }
  q[i] = new;
}

/** Resole aliases and call [priority_add]. */
static void priority_add_resolved(cdict_t const *dict, priority_t *dst,
    int index)
{
  int alias = resolve_alias(dict, index);
  if (alias >= 0)
    index = alias;
  priority_add(dst, cdict_freq(dict, index), index);
}

/** ************************************************************************
    cdict_suffixes
    ************************************************************************ */

static void suffixes(cdict_t const *dict, void const *parent_node, int ptr,
    int index, priority_t *dst);

static void suffixes_branches(cdict_t const *dict, branches_t const *b,
    int index, priority_t *dst)
{
  int len = b->length;
  for (int i = 0; i < len; i++)
    suffixes(dict, b, branch(b, i), index + branch_number(b, i), dst);
}

static void suffixes(cdict_t const *dict, void const *parent_node, int ptr,
    int index, priority_t *dst)
{
  void const *node = PTR_NODE(ptr, parent_node);
  if (PTR_IS_FINAL(ptr))
  {
    priority_add_resolved(dict, dst, index);
    index++;
  }
  switch (NODE_KIND(node))
  {
    case BRANCHES:
      suffixes_branches(dict, node, index, dst);
      break;
    case PREFIX:
      suffixes(dict, node, decode_int24(((prefix_t const*)node)->next_ptr),
          index, dst);
      break;
  }
}

int cdict_suffixes(cdict_t const *dict, cdict_result_t const *r, int *dst,
    int count)
{
  if (!cdict_result_belongs_to(dict, r) || dst == NULL || count < 1
      || count > CDICT_SPATIAL_MAX_RESULTS)
    return 0;
  if (r->prefix_ptr == 0)
    return 0;
  word_freq_t words[count];
  priority_t queue;
  priority_init(&queue, words, count);
  suffixes(dict, PREFIX_PTR_PARENT(r->prefix_ptr),
      PREFIX_PTR_PTR(r->prefix_ptr), r->original_index, &queue);
  return priority_to_sorted_array(&queue, dst, count);
}


/** ************************************************************************
    cdict_spatial_search
    ************************************************************************ */

#define SPATIAL_VISITED_CAPACITY 1024
#define SPATIAL_SCALAR_PARTIAL_CAPACITY 512
#define SPATIAL_BYTE_EDGE_CAPACITY 256

typedef struct
{
  uint32_t node_offset;
  int32_t index;
  uint16_t prefix_pos;
  uint8_t final;
  uint8_t reserved;
} spatial_cursor_t;

typedef struct
{
  spatial_cursor_t cursor;
  uint32_t cost_q8;
  uint16_t tap_index;
  uint16_t word_codepoints;
  uint8_t edit_count;
  uint8_t edit_mask;
  uint16_t reserved;
} spatial_state_t;

typedef struct
{
  spatial_cursor_t cursor;
  uint32_t cost_q8;
  uint16_t tap_index;
  uint16_t word_codepoints;
  uint8_t edit_count;
  uint8_t used;
  uint16_t reserved;
} spatial_visited_t;

typedef struct
{
  spatial_cursor_t cursor;
  uint32_t codepoint;
  uint32_t minimum;
  uint8_t remaining;
  uint8_t reserved[3];
} spatial_scalar_partial_t;

typedef struct
{
  spatial_cursor_t cursor;
  uint8_t byte;
  uint8_t reserved[3];
} spatial_byte_edge_t;

typedef struct
{
  spatial_state_t queue[CDICT_SPATIAL_BEAM_WIDTH];
  spatial_visited_t visited[SPATIAL_VISITED_CAPACITY];
  spatial_scalar_partial_t scalar_partials[SPATIAL_SCALAR_PARTIAL_CAPACITY];
  spatial_byte_edge_t byte_edges[SPATIAL_BYTE_EDGE_CAPACITY];
  int queue_length;
  uint32_t generated_count;
  uint32_t best_completed_cost;
  bool has_completed;
  bool truncated;
} spatial_private_workspace_t;

_Static_assert(sizeof(spatial_private_workspace_t) <=
    CDICT_SPATIAL_WORKSPACE_BYTES, "spatial workspace is too small");

static bool spatial_scalar_valid(uint32_t codepoint)
{
  return codepoint <= UINT32_C(0x10ffff) &&
    !(codepoint >= UINT32_C(0xd800) && codepoint <= UINT32_C(0xdfff));
}

static bool spatial_dict_range(cdict_t const *dict, uint32_t offset,
    size_t length)
{
  if (dict == NULL || dict->data_begin == NULL || dict->data_end == NULL)
    return false;
  uintptr_t begin = (uintptr_t)dict->data_begin;
  uintptr_t end = (uintptr_t)dict->data_end;
  if (end < begin)
    return false;
  size_t size = (size_t)(end - begin);
  return (size_t)offset <= size && length <= size - (size_t)offset;
}

static bool spatial_pointer_offset(cdict_t const *dict, void const *pointer,
    uint32_t *offset)
{
  if (dict == NULL || pointer == NULL || dict->data_begin == NULL ||
      dict->data_end == NULL)
    return false;
  uintptr_t begin = (uintptr_t)dict->data_begin;
  uintptr_t end = (uintptr_t)dict->data_end;
  uintptr_t value = (uintptr_t)pointer;
  if (end < begin || value < begin || value >= end ||
      value - begin > UINT32_MAX)
    return false;
  *offset = (uint32_t)(value - begin);
  return true;
}

static bool spatial_format_valid(int format)
{
  return format == FORMAT_4_BITS || format == FORMAT_8_BITS ||
    format == FORMAT_16_BITS || format == FORMAT_24_BITS;
}

static bool spatial_array_range(cdict_t const *dict, uint8_t const *array,
    int format, int length)
{
  uint32_t offset;
  return length >= 0 && spatial_format_valid(format) &&
    spatial_pointer_offset(dict, array, &offset) &&
    spatial_dict_range(dict, offset, FORMAT_ARRAY_SIZE(format, length));
}

static bool spatial_dictionary_valid(cdict_t const *dict)
{
  uint32_t name_offset;
  uint32_t ignored;
  if (dict == NULL ||
      !spatial_pointer_offset(dict, dict->name, &name_offset) ||
      memchr(dict->name, '\0', (size_t)((uintptr_t)dict->data_end -
          (uintptr_t)dict->name)) == NULL ||
      !spatial_pointer_offset(dict, dict->root_node, &ignored) ||
      !spatial_pointer_offset(dict, dict->freq, &ignored) ||
      dict->aliases_length < 0)
    return false;
  if (dict->aliases_length == 0)
    return true;
  return spatial_array_range(dict, dict->aliases_keys,
      ALIASES_KEY_FORMAT(dict), dict->aliases_length) &&
    spatial_array_range(dict, dict->aliases_values,
      ALIASES_VALUES_FORMAT(dict), dict->aliases_length);
}

static int spatial_branch_slot(branches_t const *branches, uint8_t label)
{
  for (int i = 0; i < branches->length;)
  {
    uint8_t current = branches->labels[i];
    if (current == label)
      return i;
    i = i * 2 + ((label < current) ? 1 : 2);
  }
  return -1;
}

static cdict_spatial_status_t spatial_validate_node(cdict_t const *dict,
    spatial_cursor_t const *cursor, void const **node_out)
{
  if (!spatial_dict_range(dict, cursor->node_offset, 1))
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  uint8_t const *node = dict->data_begin + cursor->node_offset;
  if (NODE_KIND(node) == BRANCHES)
  {
    if (cursor->prefix_pos != 0 ||
        !spatial_dict_range(dict, cursor->node_offset, sizeof(branches_t)))
      return CDICT_SPATIAL_CORRUPT_DICTIONARY;
    branches_t const *branches = (void const*)node;
    int branches_format = BRANCHES_BRANCHES_FORMAT(branches);
    int numbers_format = BRANCHES_NUMBERS_FORMAT(branches);
    if (!spatial_format_valid(branches_format) ||
        !spatial_format_valid(numbers_format))
      return CDICT_SPATIAL_CORRUPT_DICTIONARY;
    size_t length = sizeof(branches_t) + branches->length +
      FORMAT_ARRAY_SIZE(branches_format, branches->length) +
      FORMAT_ARRAY_SIZE(numbers_format, branches->length);
    if (!spatial_dict_range(dict, cursor->node_offset, length))
      return CDICT_SPATIAL_CORRUPT_DICTIONARY;
    for (int i = 0; i < branches->length; i++)
      if (spatial_branch_slot(branches, branches->labels[i]) != i)
        return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  }
  else if (NODE_KIND(node) == PREFIX)
  {
    if (!spatial_dict_range(dict, cursor->node_offset, sizeof(prefix_t)))
      return CDICT_SPATIAL_CORRUPT_DICTIONARY;
    prefix_t const *prefix = (void const*)node;
    int length = PREFIX_LENGTH(prefix);
    if (length <= 0 || length > PREFIX_MAX_LENGTH ||
        cursor->prefix_pos >= length ||
        !spatial_dict_range(dict, cursor->node_offset,
          sizeof(prefix_t) + (size_t)length))
      return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  }
  else
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  *node_out = node;
  return CDICT_SPATIAL_OK;
}

static cdict_spatial_status_t spatial_transition(cdict_t const *dict,
    uint32_t parent_offset, int pointer, int64_t index,
    spatial_cursor_t *cursor)
{
  int64_t relative = (int64_t)(pointer & PTR_OFFSET_MASK);
  int64_t target = (int64_t)parent_offset + relative;
  if (index < 0 || index > INT32_MAX || target < 0 ||
      target > UINT32_MAX ||
      !spatial_dict_range(dict, (uint32_t)target, 1))
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  *cursor = (spatial_cursor_t){
    .node_offset = (uint32_t)target,
    .index = (int32_t)index,
    .prefix_pos = 0,
    .final = PTR_IS_FINAL(pointer),
    .reserved = 0,
  };
  return CDICT_SPATIAL_OK;
}

static int spatial_byte_edge_compare(spatial_byte_edge_t const *a,
    spatial_byte_edge_t const *b)
{
  if (a->byte != b->byte)
    return (a->byte < b->byte) ? -1 : 1;
  if (a->cursor.index != b->cursor.index)
    return (a->cursor.index < b->cursor.index) ? -1 : 1;
  if (a->cursor.node_offset != b->cursor.node_offset)
    return (a->cursor.node_offset < b->cursor.node_offset) ? -1 : 1;
  return 0;
}

static cdict_spatial_status_t spatial_byte_edges(cdict_t const *dict,
    spatial_cursor_t const *cursor, spatial_private_workspace_t *workspace,
    int *edge_count)
{
  *edge_count = 0;
  void const *node;
  cdict_spatial_status_t status = spatial_validate_node(dict, cursor, &node);
  if (status != CDICT_SPATIAL_OK)
    return status;
  int64_t base_index = (int64_t)cursor->index + (cursor->final ? 1 : 0);
  if (base_index > INT32_MAX)
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;

  if (NODE_KIND(node) == PREFIX)
  {
    prefix_t const *prefix = node;
    int length = PREFIX_LENGTH(prefix);
    spatial_byte_edge_t edge = {
      .cursor = *cursor,
      .byte = prefix->prefix[cursor->prefix_pos],
      .reserved = { 0, 0, 0 },
    };
    edge.cursor.index = (int32_t)base_index;
    edge.cursor.final = 0;
    edge.cursor.prefix_pos++;
    if (edge.cursor.prefix_pos == length)
    {
      status = spatial_transition(dict, cursor->node_offset,
          decode_int24(prefix->next_ptr), base_index, &edge.cursor);
      if (status != CDICT_SPATIAL_OK)
        return status;
    }
    workspace->byte_edges[0] = edge;
    *edge_count = 1;
    return CDICT_SPATIAL_OK;
  }

  branches_t const *branches = node;
  int length = branches->length;
  for (int i = 0; i < length; i++)
  {
    int64_t index = base_index + branch_number(branches, i);
    spatial_byte_edge_t edge = {
      .byte = branches->labels[i],
      .reserved = { 0, 0, 0 },
    };
    status = spatial_transition(dict, cursor->node_offset,
        branch(branches, i), index, &edge.cursor);
    if (status != CDICT_SPATIAL_OK)
      return status;
    int position = i;
    while (position > 0 && spatial_byte_edge_compare(&edge,
          &workspace->byte_edges[position - 1]) < 0)
    {
      workspace->byte_edges[position] = workspace->byte_edges[position - 1];
      position--;
    }
    workspace->byte_edges[position] = edge;
  }
  for (int i = 1; i < length; i++)
    if (workspace->byte_edges[i - 1].byte == workspace->byte_edges[i].byte)
      return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  *edge_count = length;
  return CDICT_SPATIAL_OK;
}

static cdict_spatial_status_t spatial_follow_byte(cdict_t const *dict,
    spatial_cursor_t const *cursor, uint8_t byte, spatial_cursor_t *next,
    bool *found)
{
  *found = false;
  void const *node;
  cdict_spatial_status_t status = spatial_validate_node(dict, cursor, &node);
  if (status != CDICT_SPATIAL_OK)
    return status;
  int64_t base_index = (int64_t)cursor->index + (cursor->final ? 1 : 0);
  if (base_index > INT32_MAX)
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;

  if (NODE_KIND(node) == PREFIX)
  {
    prefix_t const *prefix = node;
    int length = PREFIX_LENGTH(prefix);
    if (prefix->prefix[cursor->prefix_pos] != byte)
      return CDICT_SPATIAL_OK;
    *next = *cursor;
    next->index = (int32_t)base_index;
    next->final = 0;
    next->prefix_pos++;
    if (next->prefix_pos == length)
    {
      status = spatial_transition(dict, cursor->node_offset,
          decode_int24(prefix->next_ptr), base_index, next);
      if (status != CDICT_SPATIAL_OK)
        return status;
    }
    *found = true;
    return CDICT_SPATIAL_OK;
  }

  branches_t const *branches = node;
  int slot = spatial_branch_slot(branches, byte);
  if (slot < 0)
    return CDICT_SPATIAL_OK;
  status = spatial_transition(dict, cursor->node_offset,
      branch(branches, slot), base_index + branch_number(branches, slot), next);
  if (status == CDICT_SPATIAL_OK)
    *found = true;
  return status;
}

static int spatial_encode_utf8(uint32_t codepoint, uint8_t bytes[4])
{
  if (!spatial_scalar_valid(codepoint))
    return 0;
  if (codepoint <= UINT32_C(0x7f))
  {
    bytes[0] = (uint8_t)codepoint;
    return 1;
  }
  if (codepoint <= UINT32_C(0x7ff))
  {
    bytes[0] = (uint8_t)(UINT32_C(0xc0) | (codepoint >> 6));
    bytes[1] = (uint8_t)(UINT32_C(0x80) | (codepoint & UINT32_C(0x3f)));
    return 2;
  }
  if (codepoint <= UINT32_C(0xffff))
  {
    bytes[0] = (uint8_t)(UINT32_C(0xe0) | (codepoint >> 12));
    bytes[1] = (uint8_t)(UINT32_C(0x80) |
        ((codepoint >> 6) & UINT32_C(0x3f)));
    bytes[2] = (uint8_t)(UINT32_C(0x80) | (codepoint & UINT32_C(0x3f)));
    return 3;
  }
  bytes[0] = (uint8_t)(UINT32_C(0xf0) | (codepoint >> 18));
  bytes[1] = (uint8_t)(UINT32_C(0x80) |
      ((codepoint >> 12) & UINT32_C(0x3f)));
  bytes[2] = (uint8_t)(UINT32_C(0x80) |
      ((codepoint >> 6) & UINT32_C(0x3f)));
  bytes[3] = (uint8_t)(UINT32_C(0x80) | (codepoint & UINT32_C(0x3f)));
  return 4;
}

static cdict_spatial_status_t spatial_follow_scalar(cdict_t const *dict,
    spatial_cursor_t const *cursor, uint32_t codepoint,
    spatial_cursor_t *next, bool *found)
{
  uint8_t bytes[4];
  int length = spatial_encode_utf8(codepoint, bytes);
  if (length == 0)
    return CDICT_SPATIAL_INVALID_ARGUMENT;
  spatial_cursor_t current = *cursor;
  for (int i = 0; i < length; i++)
  {
    cdict_spatial_status_t status = spatial_follow_byte(dict, &current,
        bytes[i], next, found);
    if (status != CDICT_SPATIAL_OK || !*found)
      return status;
    if (i + 1 < length && next->final)
      return CDICT_SPATIAL_INVALID_UTF8;
    current = *next;
  }
  return CDICT_SPATIAL_OK;
}

static int spatial_state_compare(spatial_state_t const *a,
    spatial_state_t const *b)
{
  if (a->cost_q8 != b->cost_q8)
    return (a->cost_q8 < b->cost_q8) ? -1 : 1;
  if (a->tap_index != b->tap_index)
    return (a->tap_index > b->tap_index) ? -1 : 1;
  if (a->edit_count != b->edit_count)
    return (a->edit_count < b->edit_count) ? -1 : 1;
  if (a->cursor.index != b->cursor.index)
    return (a->cursor.index < b->cursor.index) ? -1 : 1;
  if (a->cursor.node_offset != b->cursor.node_offset)
    return (a->cursor.node_offset < b->cursor.node_offset) ? -1 : 1;
  if (a->cursor.prefix_pos != b->cursor.prefix_pos)
    return (a->cursor.prefix_pos < b->cursor.prefix_pos) ? -1 : 1;
  if (a->cursor.final != b->cursor.final)
    return (a->cursor.final < b->cursor.final) ? -1 : 1;
  if (a->edit_mask != b->edit_mask)
    return (a->edit_mask < b->edit_mask) ? -1 : 1;
  if (a->word_codepoints != b->word_codepoints)
    return (a->word_codepoints < b->word_codepoints) ? -1 : 1;
  return 0;
}

static uint32_t spatial_hash_state(spatial_state_t const *state)
{
  uint32_t hash = state->cursor.node_offset * UINT32_C(0x9e3779b1);
  hash ^= (uint32_t)state->cursor.index * UINT32_C(0x85ebca6b);
  hash ^= ((uint32_t)state->cursor.prefix_pos << 16) |
    ((uint32_t)state->tap_index << 1) | state->cursor.final;
  hash ^= ((uint32_t)state->edit_count << 24) | state->word_codepoints;
  hash ^= hash >> 16;
  return hash;
}

static bool spatial_same_visited(spatial_visited_t const *visited,
    spatial_state_t const *state)
{
  return visited->cursor.node_offset == state->cursor.node_offset &&
    visited->cursor.index == state->cursor.index &&
    visited->cursor.prefix_pos == state->cursor.prefix_pos &&
    visited->cursor.final == state->cursor.final &&
    visited->tap_index == state->tap_index &&
    visited->word_codepoints == state->word_codepoints &&
    visited->edit_count == state->edit_count;
}

static bool spatial_visit(spatial_private_workspace_t *workspace,
    spatial_state_t const *state)
{
  uint32_t slot = spatial_hash_state(state) % SPATIAL_VISITED_CAPACITY;
  for (int probe = 0; probe < SPATIAL_VISITED_CAPACITY; probe++)
  {
    spatial_visited_t *visited = &workspace->visited[slot];
    if (!visited->used)
    {
      *visited = (spatial_visited_t){
        .cursor = state->cursor,
        .cost_q8 = state->cost_q8,
        .tap_index = state->tap_index,
        .word_codepoints = state->word_codepoints,
        .edit_count = state->edit_count,
        .used = 1,
        .reserved = 0,
      };
      return true;
    }
    if (spatial_same_visited(visited, state))
    {
      if (visited->cost_q8 <= state->cost_q8)
        return false;
      visited->cost_q8 = state->cost_q8;
      return true;
    }
    slot = (slot + 1) % SPATIAL_VISITED_CAPACITY;
  }
  workspace->truncated = true;
  return true;
}

static void spatial_queue_add(spatial_private_workspace_t *workspace,
    cdict_spatial_query_t const *query, spatial_state_t const *state)
{
  if (state->cost_q8 >= CDICT_SPATIAL_COST_INF)
    return;
  if (workspace->has_completed &&
      state->cost_q8 > workspace->best_completed_cost &&
      state->cost_q8 - workspace->best_completed_cost > query->beam_cost_q8)
  {
    workspace->truncated = true;
    return;
  }
  if (!spatial_visit(workspace, state))
    return;
  int length = workspace->queue_length;
  if (length == CDICT_SPATIAL_BEAM_WIDTH &&
      spatial_state_compare(state, &workspace->queue[length - 1]) >= 0)
  {
    workspace->truncated = true;
    return;
  }
  if (length < CDICT_SPATIAL_BEAM_WIDTH)
    workspace->queue_length++;
  else
  {
    length--;
    workspace->truncated = true;
  }
  int position = length;
  while (position > 0 && spatial_state_compare(state,
        &workspace->queue[position - 1]) < 0)
  {
    workspace->queue[position] = workspace->queue[position - 1];
    position--;
  }
  workspace->queue[position] = *state;
}

static bool spatial_queue_pop(spatial_private_workspace_t *workspace,
    spatial_state_t *state)
{
  if (workspace->queue_length == 0)
    return false;
  *state = workspace->queue[0];
  workspace->queue_length--;
  memmove(&workspace->queue[0], &workspace->queue[1],
      (size_t)workspace->queue_length * sizeof(workspace->queue[0]));
  return true;
}

static uint32_t spatial_cost_add(uint32_t a, uint32_t b)
{
  if (a >= CDICT_SPATIAL_COST_INF || b >= CDICT_SPATIAL_COST_INF ||
      a > CDICT_SPATIAL_COST_INF - b)
    return CDICT_SPATIAL_COST_INF;
  return a + b;
}

static uint32_t spatial_substitution_cost(cdict_spatial_query_t const *query,
    int tap_index, uint32_t codepoint)
{
  if (query->literal_codepoints[tap_index] == codepoint)
    return 0;
  for (int i = 0; i < query->symbol_count; i++)
    if (query->symbol_codepoints[i] == codepoint)
      return query->substitution_costs_q8[
        (size_t)tap_index * query->symbol_count + i];
  return query->unknown_substitution_cost_q8;
}

static bool spatial_frequency(cdict_t const *dict, int index,
    uint8_t *frequency)
{
  uint32_t offset;
  if (index < 0 || !spatial_pointer_offset(dict, dict->freq, &offset) ||
      (uint32_t)(index / 2) > UINT32_MAX - offset ||
      !spatial_dict_range(dict, offset + (uint32_t)(index / 2), 1))
    return false;
  *frequency = (uint8_t)cdict_freq(dict, index);
  return true;
}

static int spatial_candidate_compare(cdict_spatial_candidate_t const *a,
    cdict_spatial_candidate_t const *b)
{
  int64_t a_key = (int64_t)a->spatial_cost_q8 - 128 * (int64_t)a->frequency;
  int64_t b_key = (int64_t)b->spatial_cost_q8 - 128 * (int64_t)b->frequency;
  if (a_key != b_key)
    return (a_key < b_key) ? -1 : 1;
  if (a->spatial_cost_q8 != b->spatial_cost_q8)
    return (a->spatial_cost_q8 < b->spatial_cost_q8) ? -1 : 1;
  if (a->edit_count != b->edit_count)
    return (a->edit_count < b->edit_count) ? -1 : 1;
  if (a->frequency != b->frequency)
    return (a->frequency > b->frequency) ? -1 : 1;
  if (a->index != b->index)
    return (a->index < b->index) ? -1 : 1;
  return 0;
}

static cdict_spatial_status_t spatial_add_candidate(cdict_t const *dict,
    spatial_state_t const *state, cdict_spatial_candidate_t *dst,
    int capacity, int *length)
{
  int index = state->cursor.index;
  int alias = resolve_alias(dict, index);
  if (alias >= 0)
    index = alias;
  uint8_t frequency;
  if (!spatial_frequency(dict, index, &frequency))
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  cdict_spatial_candidate_t candidate = {
    .index = index,
    .spatial_cost_q8 = state->cost_q8,
    .edit_count = state->edit_count,
    .edit_mask = state->edit_mask,
    .frequency = frequency,
    .reserved = 0,
  };
  for (int i = 0; i < *length; i++)
  {
    if (dst[i].index != index)
      continue;
    if (spatial_candidate_compare(&candidate, &dst[i]) < 0)
      dst[i] = candidate;
    return CDICT_SPATIAL_OK;
  }
  if (*length < capacity)
  {
    dst[*length] = candidate;
    (*length)++;
    return CDICT_SPATIAL_OK;
  }
  int worst = 0;
  for (int i = 1; i < *length; i++)
    if (spatial_candidate_compare(&dst[worst], &dst[i]) < 0)
      worst = i;
  if (spatial_candidate_compare(&candidate, &dst[worst]) < 0)
    dst[worst] = candidate;
  return CDICT_SPATIAL_OK;
}

typedef bool (*spatial_scalar_visitor_t)(uint32_t codepoint,
    spatial_cursor_t const *cursor, void *context);

static cdict_spatial_status_t spatial_emit_scalar(
    spatial_private_workspace_t *workspace,
    cdict_spatial_query_t const *query, uint32_t codepoint,
    spatial_cursor_t const *cursor, spatial_scalar_visitor_t visitor,
    void *context, bool *keep_going)
{
  if (workspace->generated_count >= query->expansion_budget)
  {
    workspace->truncated = true;
    *keep_going = false;
    return CDICT_SPATIAL_OK;
  }
  workspace->generated_count++;
  *keep_going = visitor(codepoint, cursor, context);
  return CDICT_SPATIAL_OK;
}

static cdict_spatial_status_t spatial_enumerate_scalars(cdict_t const *dict,
    spatial_cursor_t const *cursor, spatial_private_workspace_t *workspace,
    cdict_spatial_query_t const *query, spatial_scalar_visitor_t visitor,
    void *context)
{
  int edge_count;
  cdict_spatial_status_t status = spatial_byte_edges(dict, cursor, workspace,
      &edge_count);
  if (status != CDICT_SPATIAL_OK)
    return status;
  int partial_count = 0;
  for (int i = 0; i < edge_count; i++)
  {
    spatial_byte_edge_t edge = workspace->byte_edges[i];
    if (edge.byte <= UINT8_C(0x7f))
    {
      bool keep_going;
      status = spatial_emit_scalar(workspace, query, edge.byte, &edge.cursor,
          visitor, context, &keep_going);
      if (status != CDICT_SPATIAL_OK || !keep_going)
        return status;
      continue;
    }
    spatial_scalar_partial_t partial = { .cursor = edge.cursor };
    if (edge.byte >= UINT8_C(0xc2) && edge.byte <= UINT8_C(0xdf))
    {
      partial.codepoint = edge.byte & UINT8_C(0x1f);
      partial.minimum = UINT32_C(0x80);
      partial.remaining = 1;
    }
    else if (edge.byte >= UINT8_C(0xe0) && edge.byte <= UINT8_C(0xef))
    {
      partial.codepoint = edge.byte & UINT8_C(0x0f);
      partial.minimum = UINT32_C(0x800);
      partial.remaining = 2;
    }
    else if (edge.byte >= UINT8_C(0xf0) && edge.byte <= UINT8_C(0xf4))
    {
      partial.codepoint = edge.byte & UINT8_C(0x07);
      partial.minimum = UINT32_C(0x10000);
      partial.remaining = 3;
    }
    else
      return CDICT_SPATIAL_INVALID_UTF8;
    if (partial.cursor.final)
      return CDICT_SPATIAL_INVALID_UTF8;
    if (partial_count == SPATIAL_SCALAR_PARTIAL_CAPACITY)
    {
      workspace->truncated = true;
      continue;
    }
    workspace->scalar_partials[partial_count++] = partial;
  }

  for (int partial_index = 0; partial_index < partial_count; partial_index++)
  {
    spatial_scalar_partial_t partial = workspace->scalar_partials[partial_index];
    status = spatial_byte_edges(dict, &partial.cursor, workspace, &edge_count);
    if (status != CDICT_SPATIAL_OK)
      return status;
    for (int i = 0; i < edge_count; i++)
    {
      spatial_byte_edge_t edge = workspace->byte_edges[i];
      if (edge.byte < UINT8_C(0x80) || edge.byte > UINT8_C(0xbf))
        return CDICT_SPATIAL_INVALID_UTF8;
      spatial_scalar_partial_t next = partial;
      next.cursor = edge.cursor;
      next.codepoint = (next.codepoint << 6) |
        (edge.byte & UINT8_C(0x3f));
      next.remaining--;
      if (next.remaining == 0)
      {
        if (next.codepoint < next.minimum ||
            !spatial_scalar_valid(next.codepoint))
          return CDICT_SPATIAL_INVALID_UTF8;
        bool keep_going;
        status = spatial_emit_scalar(workspace, query, next.codepoint,
            &next.cursor, visitor, context, &keep_going);
        if (status != CDICT_SPATIAL_OK || !keep_going)
          return status;
      }
      else
      {
        if (next.cursor.final)
          return CDICT_SPATIAL_INVALID_UTF8;
        if (partial_count == SPATIAL_SCALAR_PARTIAL_CAPACITY)
          workspace->truncated = true;
        else
          workspace->scalar_partials[partial_count++] = next;
      }
    }
  }
  return CDICT_SPATIAL_OK;
}

typedef struct
{
  cdict_spatial_query_t const *query;
  spatial_private_workspace_t *workspace;
  spatial_state_t const *state;
  int word_limit;
} spatial_expand_context_t;

static bool spatial_expand_scalar(uint32_t codepoint,
    spatial_cursor_t const *cursor, void *context_value)
{
  spatial_expand_context_t *context = context_value;
  cdict_spatial_query_t const *query = context->query;
  spatial_state_t const *state = context->state;
  if (state->word_codepoints >= context->word_limit)
    return true;

  if (state->tap_index < query->input_count)
  {
    uint32_t substitution = spatial_substitution_cost(query,
        state->tap_index, codepoint);
    bool exact = query->literal_codepoints[state->tap_index] == codepoint;
    if (exact || (state->edit_count < query->max_edits &&
          substitution != UINT16_MAX))
    {
      spatial_state_t next = *state;
      next.cursor = *cursor;
      next.tap_index++;
      next.word_codepoints++;
      next.cost_q8 = spatial_cost_add(next.cost_q8, substitution);
      if (!exact)
      {
        next.edit_count++;
        next.edit_mask |= CDICT_EDIT_SUBSTITUTION;
      }
      spatial_queue_add(context->workspace, query, &next);
    }
  }

  if (state->edit_count < query->max_edits &&
      query->omission_cost_q8 != UINT16_MAX)
  {
    spatial_state_t next = *state;
    next.cursor = *cursor;
    next.word_codepoints++;
    next.edit_count++;
    next.edit_mask |= CDICT_EDIT_OMISSION;
    next.cost_q8 = spatial_cost_add(next.cost_q8,
        query->omission_cost_q8);
    spatial_queue_add(context->workspace, query, &next);
  }
  return true;
}

static cdict_spatial_status_t spatial_validate_arguments(
    cdict_t const *dict, cdict_spatial_query_t const *query,
    cdict_spatial_workspace_t *workspace, cdict_spatial_candidate_t *dst,
    int dst_capacity, int *dst_length)
{
  if (dst_length == NULL)
    return CDICT_SPATIAL_INVALID_ARGUMENT;
  *dst_length = 0;
  if (dict == NULL || query == NULL || workspace == NULL || dst == NULL ||
      query->input_count > CDICT_SPATIAL_MAX_INPUT ||
      query->symbol_count > CDICT_SPATIAL_MAX_SYMBOLS ||
      (query->input_count != 0 && query->symbol_count == 0) ||
      query->max_edits > CDICT_SPATIAL_MAX_EDITS ||
      query->max_results == 0 ||
      query->max_results > CDICT_SPATIAL_MAX_RESULTS ||
      dst_capacity < query->max_results ||
      dst_capacity > CDICT_SPATIAL_MAX_RESULTS ||
      query->expansion_budget == 0 ||
      query->expansion_budget > CDICT_SPATIAL_MAX_EXPANSIONS ||
      query->beam_cost_q8 >= CDICT_SPATIAL_COST_INF ||
      (query->input_count != 0 && query->literal_codepoints == NULL) ||
      (query->symbol_count != 0 && query->symbol_codepoints == NULL) ||
      ((size_t)query->input_count * query->symbol_count != 0 &&
        query->substitution_costs_q8 == NULL))
    return CDICT_SPATIAL_INVALID_ARGUMENT;
  for (int i = 0; i < query->input_count; i++)
    if (!spatial_scalar_valid(query->literal_codepoints[i]))
      return CDICT_SPATIAL_INVALID_ARGUMENT;
  for (int i = 0; i < query->symbol_count; i++)
  {
    if (!spatial_scalar_valid(query->symbol_codepoints[i]))
      return CDICT_SPATIAL_INVALID_ARGUMENT;
    for (int j = 0; j < i; j++)
      if (query->symbol_codepoints[i] == query->symbol_codepoints[j])
        return CDICT_SPATIAL_INVALID_ARGUMENT;
  }
  if (!spatial_dictionary_valid(dict))
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  return CDICT_SPATIAL_OK;
}

cdict_spatial_status_t cdict_spatial_search(cdict_t const *dict,
    cdict_spatial_query_t const *query, cdict_spatial_workspace_t *workspace,
    cdict_spatial_candidate_t *dst, int dst_capacity, int *dst_length)
{
  cdict_spatial_status_t status = spatial_validate_arguments(dict, query,
      workspace, dst, dst_capacity, dst_length);
  if (status != CDICT_SPATIAL_OK)
    return status;

  spatial_private_workspace_t *private_workspace =
    (void*)workspace->bytes;
  memset(private_workspace, 0, sizeof(*private_workspace));
  private_workspace->best_completed_cost = CDICT_SPATIAL_COST_INF;

  uint32_t root_offset;
  if (!spatial_pointer_offset(dict, dict->root_node, &root_offset))
    return CDICT_SPATIAL_CORRUPT_DICTIONARY;
  spatial_state_t initial = {
    .cursor = {
      .node_offset = root_offset,
      .index = 0,
      .prefix_pos = 0,
      .final = 0,
      .reserved = 0,
    },
    .cost_q8 = 0,
    .tap_index = 0,
    .word_codepoints = 0,
    .edit_count = 0,
    .edit_mask = 0,
    .reserved = 0,
  };
  spatial_queue_add(private_workspace, query, &initial);

  int result_capacity = query->max_results;
  int word_limit = min(CDICT_SPATIAL_MAX_WORD_CODEPOINTS,
      query->input_count + query->max_edits);
  uint32_t expansions = 0;
  spatial_state_t state;
  while (expansions < query->expansion_budget &&
      spatial_queue_pop(private_workspace, &state))
  {
    expansions++;
    if (state.cursor.final && state.tap_index == query->input_count)
    {
      status = spatial_add_candidate(dict, &state, dst, result_capacity,
          dst_length);
      if (status != CDICT_SPATIAL_OK)
        break;
      if (!private_workspace->has_completed ||
          state.cost_q8 < private_workspace->best_completed_cost)
      {
        private_workspace->has_completed = true;
        private_workspace->best_completed_cost = state.cost_q8;
      }
    }

    if (state.tap_index < query->input_count &&
        state.edit_count < query->max_edits &&
        query->extra_tap_cost_q8 != UINT16_MAX)
    {
      spatial_state_t extra_tap = state;
      extra_tap.tap_index++;
      extra_tap.edit_count++;
      extra_tap.edit_mask |= CDICT_EDIT_EXTRA_TAP;
      extra_tap.cost_q8 = spatial_cost_add(extra_tap.cost_q8,
          query->extra_tap_cost_q8);
      spatial_queue_add(private_workspace, query, &extra_tap);
    }

    if (state.tap_index + 1 < query->input_count &&
        state.edit_count < query->max_edits &&
        query->transposition_cost_q8 != UINT16_MAX &&
        state.word_codepoints + 2 <= word_limit)
    {
      spatial_cursor_t first_cursor;
      spatial_cursor_t second_cursor;
      bool first_found;
      bool second_found;
      status = spatial_follow_scalar(dict, &state.cursor,
          query->literal_codepoints[state.tap_index + 1], &first_cursor,
          &first_found);
      if (status != CDICT_SPATIAL_OK)
        break;
      if (first_found)
      {
        status = spatial_follow_scalar(dict, &first_cursor,
            query->literal_codepoints[state.tap_index], &second_cursor,
            &second_found);
        if (status != CDICT_SPATIAL_OK)
          break;
        if (second_found)
        {
          spatial_state_t transposition = state;
          transposition.cursor = second_cursor;
          transposition.tap_index += 2;
          transposition.word_codepoints += 2;
          transposition.edit_count++;
          transposition.edit_mask |= CDICT_EDIT_TRANSPOSITION;
          uint32_t cost = spatial_substitution_cost(query,
              state.tap_index, query->literal_codepoints[state.tap_index]);
          cost = spatial_cost_add(cost, spatial_substitution_cost(query,
              state.tap_index + 1,
              query->literal_codepoints[state.tap_index + 1]));
          cost = spatial_cost_add(cost, query->transposition_cost_q8);
          transposition.cost_q8 = spatial_cost_add(transposition.cost_q8,
              cost);
          spatial_queue_add(private_workspace, query, &transposition);
        }
      }
    }

    if (state.word_codepoints < word_limit)
    {
      spatial_expand_context_t context = {
        .query = query,
        .workspace = private_workspace,
        .state = &state,
        .word_limit = word_limit,
      };
      status = spatial_enumerate_scalars(dict, &state.cursor,
          private_workspace, query, spatial_expand_scalar, &context);
      if (status != CDICT_SPATIAL_OK)
        break;
    }
  }

  if (status != CDICT_SPATIAL_OK)
  {
    *dst_length = 0;
    return status;
  }
  if (private_workspace->queue_length != 0)
    private_workspace->truncated = true;

  for (int i = 1; i < *dst_length; i++)
  {
    cdict_spatial_candidate_t candidate = dst[i];
    int position = i;
    while (position > 0 && spatial_candidate_compare(&candidate,
          &dst[position - 1]) < 0)
    {
      dst[position] = dst[position - 1];
      position--;
    }
    dst[position] = candidate;
  }
  return private_workspace->truncated ? CDICT_SPATIAL_TRUNCATED :
    CDICT_SPATIAL_OK;
}
