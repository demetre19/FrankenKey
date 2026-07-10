/** libdict

    This library implements a compact dictionary as a Radix Tree. Words are byte
    strings of arbitrary encoding, the alphabet size is 256.
    Several techniques are used to make the dictionary as small as possible.
*/

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define CDICT_SPATIAL_MAX_INPUT 48
#define CDICT_SPATIAL_MAX_SYMBOLS 96
#define CDICT_SPATIAL_MAX_RESULTS 16
#define CDICT_SPATIAL_MAX_EDITS 2
#define CDICT_SPATIAL_MAX_WORD_CODEPOINTS 50
#define CDICT_SPATIAL_BEAM_WIDTH 128
#define CDICT_SPATIAL_MAX_EXPANSIONS 32768
#define CDICT_SPATIAL_COST_INF UINT32_C(0x3fffffff)
#define CDICT_SPATIAL_WORKSPACE_BYTES 65536

#define CDICT_EDIT_SUBSTITUTION UINT8_C(0x01)
#define CDICT_EDIT_OMISSION UINT8_C(0x02)
#define CDICT_EDIT_EXTRA_TAP UINT8_C(0x04)
#define CDICT_EDIT_TRANSPOSITION UINT8_C(0x08)

typedef struct
{
  char const *name;
  void const *root_node;
  uint8_t const *freq;
  uint8_t aliases_header;
  int aliases_length;
  uint8_t const *aliases_keys;
  uint8_t const *aliases_values;
  uint8_t const *data_begin;
  uint8_t const *data_end;
} cdict_t;

typedef struct
{
  char const *data;
  int n_dicts; /** Number of dictionaries in the file. */
  int total_size; /** Total size in bytes. */
} cdict_header_t;

typedef enum
{
  CDICT_OK, // The dictionary was succesfully loaded
  CDICT_NOT_A_DICTIONARY, // File is not a dictionary
  CDICT_UNSUPPORTED_FORMAT,
  // Dictionary was created for an incompatible version of the library
} cdict_cnstr_result_t;

/** Create an in-memory dictionary from a string of a given size. The string is
    not copied and must remain valid until the dictionary is no longer used. No
    memory allocation is done by this function or any other function in the
    library. Returns [CDICT_OK] on success or an error code if the dictionary
    seems corrupted. */
cdict_cnstr_result_t cdict_of_string(char const *data, int size,
    cdict_header_t *dst);

/** Obtain the dictionaries at index [i] in the dictionary file and write it to
    [dst]. [i] is in the range [0,header->n_dicts). */
void cdict_get_dict(cdict_header_t const *header, int i, cdict_t *dst);

/** Text description of an error for use in exceptions and logs. */
char const* cdict_cnstr_result_to_string(cdict_cnstr_result_t r);

/** Return value of [cdict_find]. */
typedef struct
{
  bool found; /** Whether the query is recognized. */
  int index;
  /** Unique index of the recognized word or [-1] if the query is not
      recognized. Find the corresponding freq at [dict->freq[index]]. */
  intptr_t prefix_ptr;
  /** Internal node where the search stopped. Use [cdict_suffixes] to list
      the words starting with this prefix. Might be [0], in which case the
      queried is not the prefix of any word in the dictionary. */
  /** Index of the recognized word before alias resolution. Used internally. */
  int original_index;
  /** Runtime owner identity; results are valid only for this dictionary. */
  void const *owner;
} cdict_result_t;

/** Lookup the given word of the given size in the dictionary.
    Write its result to [result]. */
void cdict_find(cdict_t const *dict, char const *word, int word_size,
    cdict_result_t *result);

/** Return whether a find result can be safely passed back to this dictionary. */
bool cdict_result_belongs_to(cdict_t const *dict,
    cdict_result_t const *result);


/** Frequency associated to a word. [index] is the corresponding field in
    [cdict_result_t]. */
int cdict_freq(cdict_t const *dict, int index);

/** Retrieve the word at the given index. Returns the number of chars written
    to [dst]. Do not write a NUL byte at the end of [dst]. */
int cdict_word(cdict_t const *dict, int index, char *dst, int max_length);

/** List the words starting with the word first queried with [cdict_find].
    This can be used even if [result->found] is false. Write up to [count] word
    indexes to [dst]. Return the number of word indexes written to [dst]. */
int cdict_suffixes(cdict_t const *dict, cdict_result_t const *r, int *dst,
    int count);


typedef enum
{
  CDICT_SPATIAL_OK = 0,
  CDICT_SPATIAL_TRUNCATED,
  CDICT_SPATIAL_INVALID_ARGUMENT,
  CDICT_SPATIAL_INVALID_UTF8,
  CDICT_SPATIAL_CORRUPT_DICTIONARY
} cdict_spatial_status_t;

typedef struct
{
  /** One Unicode scalar for each observed tap. */
  uint32_t const *literal_codepoints;
  uint16_t input_count;
  /** Unique active-layout Unicode scalars. */
  uint32_t const *symbol_codepoints;
  uint16_t symbol_count;
  /** Row-major [input_count * symbol_count] Q8 substitution costs. */
  uint16_t const *substitution_costs_q8;
  uint8_t max_edits;
  uint8_t max_results;
  uint16_t omission_cost_q8;
  uint16_t extra_tap_cost_q8;
  uint16_t transposition_cost_q8;
  uint16_t unknown_substitution_cost_q8;
  uint32_t beam_cost_q8;
  uint32_t expansion_budget;
} cdict_spatial_query_t;

typedef struct
{
  /** Alias-resolved dictionary word index. */
  int32_t index;
  uint32_t spatial_cost_q8;
  uint8_t edit_count;
  uint8_t edit_mask;
  uint8_t frequency;
  uint8_t reserved;
} cdict_spatial_candidate_t;

/** Caller-owned, naturally aligned fixed search workspace. */
typedef union
{
  max_align_t align;
  uint8_t bytes[CDICT_SPATIAL_WORKSPACE_BYTES];
} cdict_spatial_workspace_t;

/** Coordinate-first bounded search over canonical UTF-8 dictionary words.
    All storage is caller-owned and fixed-size. No allocation or recursion is
    performed. Results are deterministically ordered and limited to 16. */
cdict_spatial_status_t cdict_spatial_search(
    cdict_t const *dict,
    cdict_spatial_query_t const *query,
    cdict_spatial_workspace_t *workspace,
    cdict_spatial_candidate_t *dst,
    int dst_capacity,
    int *dst_length);

/** Version of the dictionary's format. Dictionaries built for a different
    version are not compatible. */
int cdict_format_version();
