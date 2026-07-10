#include <jni.h>
#include <libcdict.h>
#include <limits.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "juloo_cdict.h"

#pragma GCC diagnostic ignored "-Wunused-parameter"

#define CDICT_JNI_MAX_RESULTS CDICT_SPATIAL_MAX_RESULTS
#define CDICT_JNI_WORD_UTF8_MAX 1024

/** Structure pointed to by the [header_ptr] field in [Cdict.Header]. */
typedef struct
{
  cdict_header_t header;
  cdict_t *dicts;
  char data[];
} header_value;

static struct
{
  jclass class;
  jfieldID found;
  jfieldID index;
  jfieldID prefix_ptr;
  jfieldID original_index;
  jfieldID owner;
} Result;

static struct
{
  jclass class;
  jfieldID sequence;
  jfieldID literal_codepoints;
  jfieldID symbol_codepoints;
  jfieldID substitution_costs_q8;
  jfieldID max_edits;
  jfieldID max_results;
  jfieldID omission_cost_q8;
  jfieldID extra_tap_cost_q8;
  jfieldID transposition_cost_q8;
  jfieldID unknown_substitution_cost_q8;
  jfieldID beam_cost_q8;
  jfieldID expansion_budget;
} SpatialQuery;

static struct
{
  jclass class;
  jfieldID index;
  jfieldID spatial_cost_q8;
  jfieldID edit_count;
  jfieldID edit_mask;
  jfieldID frequency;
} SpatialCandidate;

static struct
{
  jclass class;
  jfieldID sequence;
  jfieldID status;
  jfieldID candidates;
} SpatialResult;

static void throw_new(JNIEnv *env, char const *class_name,
    char const *message)
{
  jclass exception_class = (*env)->FindClass(env, class_name);
  if (exception_class != NULL)
  {
    (*env)->ThrowNew(env, exception_class, message);
    (*env)->DeleteLocalRef(env, exception_class);
  }
}

static void throw_illegal_argument(JNIEnv *env, char const *message)
{
  throw_new(env, "java/lang/IllegalArgumentException", message);
}

static void throw_illegal_state(JNIEnv *env, char const *message)
{
  throw_new(env, "java/lang/IllegalStateException", message);
}

static void throw_out_of_memory(JNIEnv *env)
{
  throw_new(env, "java/lang/OutOfMemoryError", "cdict native allocation failed");
}

static jclass global_class(JNIEnv *env, char const *name)
{
  jclass local = (*env)->FindClass(env, name);
  if (local == NULL)
    return NULL;
  jclass global = (*env)->NewGlobalRef(env, local);
  (*env)->DeleteLocalRef(env, local);
  return global;
}

static cdict_t const *dict_from_long(JNIEnv *env, jlong value)
{
  if (value == 0)
  {
    throw_illegal_state(env, "dictionary is not available");
    return NULL;
  }
  return (cdict_t const*)(intptr_t)value;
}

static jobject result_to_java(JNIEnv *env, cdict_result_t const *result)
{
  jobject jresult = (*env)->AllocObject(env, Result.class);
  if (jresult == NULL)
    return NULL;
  (*env)->SetBooleanField(env, jresult, Result.found, result->found);
  (*env)->SetIntField(env, jresult, Result.index, result->index);
  (*env)->SetLongField(env, jresult, Result.prefix_ptr,
      (jlong)result->prefix_ptr);
  (*env)->SetIntField(env, jresult, Result.original_index,
      result->original_index);
  (*env)->SetLongField(env, jresult, Result.owner,
      (jlong)(intptr_t)result->owner);
  return jresult;
}

static bool result_of_java(JNIEnv *env, jobject jresult, cdict_result_t *dst)
{
  if (jresult == NULL)
  {
    throw_illegal_argument(env, "result must not be null");
    return false;
  }
  dst->found = (*env)->GetBooleanField(env, jresult, Result.found);
  dst->index = (*env)->GetIntField(env, jresult, Result.index);
  dst->prefix_ptr = (intptr_t)(*env)->GetLongField(env, jresult,
      Result.prefix_ptr);
  dst->original_index = (*env)->GetIntField(env, jresult,
      Result.original_index);
  dst->owner = (void const*)(intptr_t)(*env)->GetLongField(env, jresult,
      Result.owner);
  return true;
}

static jintArray jarray_of_int_array(JNIEnv *env, int const *src, int length)
{
  jintArray array = (*env)->NewIntArray(env, length);
  if (array != NULL && length != 0)
    (*env)->SetIntArrayRegion(env, array, 0, length, (jint const*)src);
  return array;
}

static bool unicode_scalar_valid(jint codepoint)
{
  return codepoint >= 0 && codepoint <= 0x10ffff &&
    !(codepoint >= 0xd800 && codepoint <= 0xdfff);
}

static bool q8_16_valid(jint value)
{
  return value >= 0 && value <= UINT16_MAX;
}

static bool decode_utf8_scalar(uint8_t const *bytes, int length, int *offset,
    uint32_t *codepoint)
{
  int i = *offset;
  if (i >= length)
    return false;
  uint8_t first = bytes[i++];
  uint32_t value;
  int remaining;
  uint32_t minimum;
  if (first <= UINT8_C(0x7f))
  {
    value = first;
    remaining = 0;
    minimum = 0;
  }
  else if (first >= UINT8_C(0xc2) && first <= UINT8_C(0xdf))
  {
    value = first & UINT8_C(0x1f);
    remaining = 1;
    minimum = UINT32_C(0x80);
  }
  else if (first >= UINT8_C(0xe0) && first <= UINT8_C(0xef))
  {
    value = first & UINT8_C(0x0f);
    remaining = 2;
    minimum = UINT32_C(0x800);
  }
  else if (first >= UINT8_C(0xf0) && first <= UINT8_C(0xf4))
  {
    value = first & UINT8_C(0x07);
    remaining = 3;
    minimum = UINT32_C(0x10000);
  }
  else
    return false;

  if (remaining > length - i)
    return false;
  for (int n = 0; n < remaining; n++)
  {
    uint8_t continuation = bytes[i++];
    if (continuation < UINT8_C(0x80) || continuation > UINT8_C(0xbf))
      return false;
    value = (value << 6) | (continuation & UINT8_C(0x3f));
  }
  if (value < minimum || value > UINT32_C(0x10ffff) ||
      (value >= UINT32_C(0xd800) && value <= UINT32_C(0xdfff)))
    return false;
  *offset = i;
  *codepoint = value;
  return true;
}

static jstring canonical_utf8_to_java(JNIEnv *env, char const *bytes,
    int length)
{
  jchar utf16[CDICT_JNI_WORD_UTF8_MAX];
  int input = 0;
  int output = 0;
  while (input < length)
  {
    uint32_t codepoint;
    if (!decode_utf8_scalar((uint8_t const*)bytes, length, &input,
          &codepoint))
    {
      throw_illegal_state(env, "dictionary word is not canonical UTF-8");
      return NULL;
    }
    if (codepoint <= UINT32_C(0xffff))
    {
      if (output == CDICT_JNI_WORD_UTF8_MAX)
      {
        throw_illegal_state(env, "dictionary word exceeds JNI bound");
        return NULL;
      }
      utf16[output++] = (jchar)codepoint;
    }
    else
    {
      if (output > CDICT_JNI_WORD_UTF8_MAX - 2)
      {
        throw_illegal_state(env, "dictionary word exceeds JNI bound");
        return NULL;
      }
      uint32_t supplementary = codepoint - UINT32_C(0x10000);
      utf16[output++] = (jchar)(UINT32_C(0xd800) | (supplementary >> 10));
      utf16[output++] = (jchar)(UINT32_C(0xdc00) |
          (supplementary & UINT32_C(0x3ff)));
    }
  }
  return (*env)->NewString(env, utf16, output);
}

static jobject spatial_result_to_java(JNIEnv *env, jlong sequence,
    cdict_spatial_status_t status, cdict_spatial_candidate_t const *candidates,
    int length)
{
  jobjectArray array = (*env)->NewObjectArray(env, length,
      SpatialCandidate.class, NULL);
  if (array == NULL)
    return NULL;
  for (int i = 0; i < length; i++)
  {
    jobject candidate = (*env)->AllocObject(env, SpatialCandidate.class);
    if (candidate == NULL)
    {
      (*env)->DeleteLocalRef(env, array);
      return NULL;
    }
    (*env)->SetIntField(env, candidate, SpatialCandidate.index,
        candidates[i].index);
    (*env)->SetIntField(env, candidate, SpatialCandidate.spatial_cost_q8,
        candidates[i].spatial_cost_q8);
    (*env)->SetIntField(env, candidate, SpatialCandidate.edit_count,
        candidates[i].edit_count);
    (*env)->SetIntField(env, candidate, SpatialCandidate.edit_mask,
        candidates[i].edit_mask);
    (*env)->SetIntField(env, candidate, SpatialCandidate.frequency,
        candidates[i].frequency);
    (*env)->SetObjectArrayElement(env, array, i, candidate);
    (*env)->DeleteLocalRef(env, candidate);
    if ((*env)->ExceptionCheck(env))
    {
      (*env)->DeleteLocalRef(env, array);
      return NULL;
    }
  }

  jobject result = (*env)->AllocObject(env, SpatialResult.class);
  if (result == NULL)
  {
    (*env)->DeleteLocalRef(env, array);
    return NULL;
  }
  (*env)->SetLongField(env, result, SpatialResult.sequence, sequence);
  (*env)->SetIntField(env, result, SpatialResult.status, status);
  (*env)->SetObjectField(env, result, SpatialResult.candidates, array);
  (*env)->DeleteLocalRef(env, array);
  return result;
}

JNIEXPORT jint JNICALL Java_juloo_cdict_Cdict_format_1version(JNIEnv *env,
    jclass jcls)
{
  return cdict_format_version();
}

JNIEXPORT void JNICALL Java_juloo_cdict_Cdict_init(JNIEnv *env, jclass jcls)
{
  Result.class = global_class(env, "juloo/cdict/Cdict$Result");
  if (Result.class == NULL)
    return;
  Result.found = (*env)->GetFieldID(env, Result.class, "found", "Z");
  Result.index = (*env)->GetFieldID(env, Result.class, "index", "I");
  Result.prefix_ptr = (*env)->GetFieldID(env, Result.class, "prefix_ptr", "J");
  Result.original_index = (*env)->GetFieldID(env, Result.class,
      "original_index", "I");
  Result.owner = (*env)->GetFieldID(env, Result.class, "owner", "J");

  SpatialQuery.class = global_class(env, "juloo/cdict/Cdict$SpatialQuery");
  if (SpatialQuery.class == NULL)
    return;
  SpatialQuery.sequence = (*env)->GetFieldID(env, SpatialQuery.class,
      "sequence", "J");
  SpatialQuery.literal_codepoints = (*env)->GetFieldID(env,
      SpatialQuery.class, "literalCodePoints", "[I");
  SpatialQuery.symbol_codepoints = (*env)->GetFieldID(env,
      SpatialQuery.class, "symbolCodePoints", "[I");
  SpatialQuery.substitution_costs_q8 = (*env)->GetFieldID(env,
      SpatialQuery.class, "substitutionCostsQ8", "[I");
  SpatialQuery.max_edits = (*env)->GetFieldID(env, SpatialQuery.class,
      "maxEdits", "I");
  SpatialQuery.max_results = (*env)->GetFieldID(env, SpatialQuery.class,
      "maxResults", "I");
  SpatialQuery.omission_cost_q8 = (*env)->GetFieldID(env, SpatialQuery.class,
      "omissionCostQ8", "I");
  SpatialQuery.extra_tap_cost_q8 = (*env)->GetFieldID(env,
      SpatialQuery.class, "extraTapCostQ8", "I");
  SpatialQuery.transposition_cost_q8 = (*env)->GetFieldID(env,
      SpatialQuery.class, "transpositionCostQ8", "I");
  SpatialQuery.unknown_substitution_cost_q8 = (*env)->GetFieldID(env,
      SpatialQuery.class, "unknownSubstitutionCostQ8", "I");
  SpatialQuery.beam_cost_q8 = (*env)->GetFieldID(env, SpatialQuery.class,
      "beamCostQ8", "I");
  SpatialQuery.expansion_budget = (*env)->GetFieldID(env, SpatialQuery.class,
      "expansionBudget", "I");

  SpatialCandidate.class = global_class(env,
      "juloo/cdict/Cdict$SpatialCandidate");
  if (SpatialCandidate.class == NULL)
    return;
  SpatialCandidate.index = (*env)->GetFieldID(env, SpatialCandidate.class,
      "index", "I");
  SpatialCandidate.spatial_cost_q8 = (*env)->GetFieldID(env,
      SpatialCandidate.class, "spatialCostQ8", "I");
  SpatialCandidate.edit_count = (*env)->GetFieldID(env,
      SpatialCandidate.class, "editCount", "I");
  SpatialCandidate.edit_mask = (*env)->GetFieldID(env,
      SpatialCandidate.class, "editMask", "I");
  SpatialCandidate.frequency = (*env)->GetFieldID(env,
      SpatialCandidate.class, "frequency", "I");

  SpatialResult.class = global_class(env, "juloo/cdict/Cdict$SpatialResult");
  if (SpatialResult.class == NULL)
    return;
  SpatialResult.sequence = (*env)->GetFieldID(env, SpatialResult.class,
      "sequence", "J");
  SpatialResult.status = (*env)->GetFieldID(env, SpatialResult.class,
      "status", "I");
  SpatialResult.candidates = (*env)->GetFieldID(env, SpatialResult.class,
      "candidates", "[Ljuloo/cdict/Cdict$SpatialCandidate;");
}

JNIEXPORT jlong JNICALL Java_juloo_cdict_Cdict_of_1bytes_1native
  (JNIEnv *env, jclass cls, jbyteArray data)
{
  if (data == NULL)
  {
    throw_illegal_argument(env, "dictionary bytes must not be null");
    return 0;
  }
  jsize length = (*env)->GetArrayLength(env, data);
  if ((size_t)length > SIZE_MAX - sizeof(header_value))
  {
    throw_out_of_memory(env);
    return 0;
  }
  header_value *header = malloc(sizeof(header_value) + (size_t)length);
  if (header == NULL)
  {
    throw_out_of_memory(env);
    return 0;
  }
  header->dicts = NULL;
  (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte*)header->data);
  if ((*env)->ExceptionCheck(env))
  {
    free(header);
    return 0;
  }
  cdict_cnstr_result_t construction = cdict_of_string(header->data, length,
      &header->header);
  if (construction != CDICT_OK)
  {
    char const *message = cdict_cnstr_result_to_string(construction);
    free(header);
    throw_new(env, "juloo/cdict/Cdict$ConstructionError", message);
    return 0;
  }

  int dictionary_count = header->header.n_dicts;
  if (dictionary_count != 0)
  {
    if ((size_t)dictionary_count > SIZE_MAX / sizeof(cdict_t))
    {
      free(header);
      throw_out_of_memory(env);
      return 0;
    }
    header->dicts = malloc((size_t)dictionary_count * sizeof(cdict_t));
    if (header->dicts == NULL)
    {
      free(header);
      throw_out_of_memory(env);
      return 0;
    }
    for (int i = 0; i < dictionary_count; i++)
      cdict_get_dict(&header->header, i, &header->dicts[i]);
  }
  return (jlong)(intptr_t)header;
}

JNIEXPORT jobjectArray JNICALL Java_juloo_cdict_Cdict_00024Header_get_1dicts_1native
  (JNIEnv *env, jobject owner, jlong header_ptr)
{
  if (header_ptr == 0)
  {
    throw_illegal_state(env, "dictionary header is not available");
    return NULL;
  }
  header_value const *header = (void const*)(intptr_t)header_ptr;
  jclass cdict_class = (*env)->FindClass(env, "juloo/cdict/Cdict");
  if (cdict_class == NULL)
    return NULL;
  jmethodID constructor = (*env)->GetMethodID(env, cdict_class, "<init>",
      "(Ljava/lang/String;JLjuloo/cdict/Cdict$Header;)V");
  if (constructor == NULL)
  {
    (*env)->DeleteLocalRef(env, cdict_class);
    return NULL;
  }
  int dictionary_count = header->header.n_dicts;
  jobjectArray dictionaries = (*env)->NewObjectArray(env, dictionary_count,
      cdict_class, NULL);
  if (dictionaries == NULL)
  {
    (*env)->DeleteLocalRef(env, cdict_class);
    return NULL;
  }
  for (int i = 0; i < dictionary_count; i++)
  {
    jstring name = (*env)->NewStringUTF(env, header->dicts[i].name);
    if (name == NULL)
      break;
    jobject dictionary = (*env)->NewObject(env, cdict_class, constructor, name,
        (jlong)(intptr_t)&header->dicts[i], owner);
    (*env)->DeleteLocalRef(env, name);
    if (dictionary == NULL)
      break;
    (*env)->SetObjectArrayElement(env, dictionaries, i, dictionary);
    (*env)->DeleteLocalRef(env, dictionary);
    if ((*env)->ExceptionCheck(env))
      break;
  }
  (*env)->DeleteLocalRef(env, cdict_class);
  if ((*env)->ExceptionCheck(env))
  {
    (*env)->DeleteLocalRef(env, dictionaries);
    return NULL;
  }
  return dictionaries;
}

JNIEXPORT void JNICALL Java_juloo_cdict_Cdict_finalize_1header
  (JNIEnv *env, jclass cls, jlong header_ptr)
{
  if (header_ptr == 0)
    return;
  header_value *header = (void*)(intptr_t)header_ptr;
  free(header->dicts);
  header->dicts = NULL;
  free(header);
}

JNIEXPORT jobject JNICALL Java_juloo_cdict_Cdict_find_1native
  (JNIEnv *env, jclass cls, jlong dictionary_ptr, jstring word)
{
  cdict_t const *dictionary = dict_from_long(env, dictionary_ptr);
  if (dictionary == NULL)
    return NULL;
  if (word == NULL)
  {
    throw_illegal_argument(env, "word must not be null");
    return NULL;
  }
  char const *utf8 = (*env)->GetStringUTFChars(env, word, NULL);
  if (utf8 == NULL)
    return NULL;
  jsize length = (*env)->GetStringUTFLength(env, word);
  cdict_result_t result;
  cdict_find(dictionary, utf8, length, &result);
  (*env)->ReleaseStringUTFChars(env, word, utf8);
  return result_to_java(env, &result);
}

JNIEXPORT jint JNICALL Java_juloo_cdict_Cdict_freq_1native
  (JNIEnv *env, jclass cls, jlong dictionary_ptr, jint index)
{
  cdict_t const *dictionary = dict_from_long(env, dictionary_ptr);
  if (dictionary == NULL)
    return 0;
  return cdict_freq(dictionary, index);
}

JNIEXPORT jstring JNICALL Java_juloo_cdict_Cdict_word_1native
  (JNIEnv *env, jclass cls, jlong dictionary_ptr, jint index)
{
  cdict_t const *dictionary = dict_from_long(env, dictionary_ptr);
  if (dictionary == NULL)
    return NULL;
  if (index < 0)
  {
    throw_illegal_argument(env, "word index must not be negative");
    return NULL;
  }
  char bytes[CDICT_JNI_WORD_UTF8_MAX + 1];
  int length = cdict_word(dictionary, index, bytes, CDICT_JNI_WORD_UTF8_MAX);
  if (length < 0 || length >= CDICT_JNI_WORD_UTF8_MAX)
  {
    throw_illegal_state(env, "dictionary word exceeds JNI bound");
    return NULL;
  }
  return canonical_utf8_to_java(env, bytes, length);
}

JNIEXPORT jintArray JNICALL Java_juloo_cdict_Cdict_suffixes_1native
  (JNIEnv *env, jclass cls, jlong dictionary_ptr, jobject jresult, jint count)
{
  cdict_t const *dictionary = dict_from_long(env, dictionary_ptr);
  if (dictionary == NULL)
    return NULL;
  if (count < 1 || count > CDICT_JNI_MAX_RESULTS)
  {
    throw_illegal_argument(env, "suffix count must be in 1..16");
    return NULL;
  }
  cdict_result_t result;
  if (!result_of_java(env, jresult, &result))
    return NULL;
  if (!cdict_result_belongs_to(dictionary, &result))
  {
    throw_illegal_argument(env, "suffix result does not belong to dictionary");
    return NULL;
  }
  int indexes[CDICT_JNI_MAX_RESULTS];
  int final_length = cdict_suffixes(dictionary, &result, indexes, count);
  return jarray_of_int_array(env, indexes, final_length);
}

JNIEXPORT jobject JNICALL Java_juloo_cdict_Cdict_spatial_1native
  (JNIEnv *env, jclass cls, jlong dictionary_ptr, jobject jquery)
{
  cdict_t const *dictionary = dict_from_long(env, dictionary_ptr);
  if (dictionary == NULL)
    return NULL;
  if (jquery == NULL)
  {
    throw_illegal_argument(env, "spatial query must not be null");
    return NULL;
  }

  jintArray literal_array = (jintArray)(*env)->GetObjectField(env, jquery,
      SpatialQuery.literal_codepoints);
  jintArray symbol_array = (jintArray)(*env)->GetObjectField(env, jquery,
      SpatialQuery.symbol_codepoints);
  jintArray cost_array = (jintArray)(*env)->GetObjectField(env, jquery,
      SpatialQuery.substitution_costs_q8);
  if (literal_array == NULL || symbol_array == NULL || cost_array == NULL)
  {
    throw_illegal_argument(env, "spatial arrays must not be null");
    goto cleanup_arrays;
  }

  jsize input_count = (*env)->GetArrayLength(env, literal_array);
  jsize symbol_count = (*env)->GetArrayLength(env, symbol_array);
  jsize cost_count = (*env)->GetArrayLength(env, cost_array);
  if (input_count < 0 || input_count > CDICT_SPATIAL_MAX_INPUT ||
      symbol_count < 0 || symbol_count > CDICT_SPATIAL_MAX_SYMBOLS ||
      (input_count != 0 && symbol_count == 0) ||
      (int64_t)input_count * symbol_count != cost_count)
  {
    throw_illegal_argument(env, "invalid spatial array dimensions");
    goto cleanup_arrays;
  }

  jint max_edits = (*env)->GetIntField(env, jquery, SpatialQuery.max_edits);
  jint max_results = (*env)->GetIntField(env, jquery, SpatialQuery.max_results);
  jint omission_cost = (*env)->GetIntField(env, jquery,
      SpatialQuery.omission_cost_q8);
  jint extra_tap_cost = (*env)->GetIntField(env, jquery,
      SpatialQuery.extra_tap_cost_q8);
  jint transposition_cost = (*env)->GetIntField(env, jquery,
      SpatialQuery.transposition_cost_q8);
  jint unknown_cost = (*env)->GetIntField(env, jquery,
      SpatialQuery.unknown_substitution_cost_q8);
  jint beam_cost = (*env)->GetIntField(env, jquery,
      SpatialQuery.beam_cost_q8);
  jint expansion_budget = (*env)->GetIntField(env, jquery,
      SpatialQuery.expansion_budget);
  if (max_edits < 0 || max_edits > CDICT_SPATIAL_MAX_EDITS ||
      max_results < 1 || max_results > CDICT_SPATIAL_MAX_RESULTS ||
      !q8_16_valid(omission_cost) || !q8_16_valid(extra_tap_cost) ||
      !q8_16_valid(transposition_cost) || !q8_16_valid(unknown_cost) ||
      beam_cost < 0 || beam_cost >= CDICT_SPATIAL_COST_INF ||
      expansion_budget < 1 ||
      expansion_budget > CDICT_SPATIAL_MAX_EXPANSIONS)
  {
    throw_illegal_argument(env, "invalid spatial scalar fields");
    goto cleanup_arrays;
  }

  jint literal_values[CDICT_SPATIAL_MAX_INPUT];
  jint symbol_values[CDICT_SPATIAL_MAX_SYMBOLS];
  uint32_t literals[CDICT_SPATIAL_MAX_INPUT];
  uint32_t symbols[CDICT_SPATIAL_MAX_SYMBOLS];
  uint16_t costs[CDICT_SPATIAL_MAX_INPUT * CDICT_SPATIAL_MAX_SYMBOLS];
  if (input_count != 0)
    (*env)->GetIntArrayRegion(env, literal_array, 0, input_count,
        literal_values);
  if (symbol_count != 0)
    (*env)->GetIntArrayRegion(env, symbol_array, 0, symbol_count,
        symbol_values);
  if ((*env)->ExceptionCheck(env))
    goto cleanup_arrays;
  for (int i = 0; i < input_count; i++)
  {
    if (!unicode_scalar_valid(literal_values[i]))
    {
      throw_illegal_argument(env, "invalid literal Unicode scalar");
      goto cleanup_arrays;
    }
    literals[i] = (uint32_t)literal_values[i];
  }
  for (int i = 0; i < symbol_count; i++)
  {
    if (!unicode_scalar_valid(symbol_values[i]))
    {
      throw_illegal_argument(env, "invalid symbol Unicode scalar");
      goto cleanup_arrays;
    }
    for (int j = 0; j < i; j++)
      if (symbol_values[i] == symbol_values[j])
      {
        throw_illegal_argument(env, "spatial symbols must be unique");
        goto cleanup_arrays;
      }
    symbols[i] = (uint32_t)symbol_values[i];
  }
  for (int row = 0; row < input_count; row++)
  {
    jint row_values[CDICT_SPATIAL_MAX_SYMBOLS];
    (*env)->GetIntArrayRegion(env, cost_array, row * symbol_count,
        symbol_count, row_values);
    if ((*env)->ExceptionCheck(env))
      goto cleanup_arrays;
    for (int column = 0; column < symbol_count; column++)
    {
      jint value = row_values[column];
      if (!q8_16_valid(value))
      {
        throw_illegal_argument(env, "spatial substitution cost out of range");
        goto cleanup_arrays;
      }
      costs[row * symbol_count + column] = (uint16_t)value;
    }
  }

  cdict_spatial_query_t query = {
    .literal_codepoints = literals,
    .input_count = (uint16_t)input_count,
    .symbol_codepoints = symbols,
    .symbol_count = (uint16_t)symbol_count,
    .substitution_costs_q8 = costs,
    .max_edits = (uint8_t)max_edits,
    .max_results = (uint8_t)max_results,
    .omission_cost_q8 = (uint16_t)omission_cost,
    .extra_tap_cost_q8 = (uint16_t)extra_tap_cost,
    .transposition_cost_q8 = (uint16_t)transposition_cost,
    .unknown_substitution_cost_q8 = (uint16_t)unknown_cost,
    .beam_cost_q8 = (uint32_t)beam_cost,
    .expansion_budget = (uint32_t)expansion_budget,
  };
  cdict_spatial_workspace_t workspace;
  cdict_spatial_candidate_t candidates[CDICT_SPATIAL_MAX_RESULTS];
  int result_length = 0;
  cdict_spatial_status_t status = cdict_spatial_search(dictionary, &query,
      &workspace, candidates, CDICT_SPATIAL_MAX_RESULTS, &result_length);
  if (status == CDICT_SPATIAL_INVALID_ARGUMENT)
  {
    throw_illegal_argument(env, "native spatial query rejected");
    goto cleanup_arrays;
  }
  if (status == CDICT_SPATIAL_CORRUPT_DICTIONARY)
  {
    throw_illegal_state(env, "corrupt dictionary during spatial search");
    goto cleanup_arrays;
  }
  if ((status != CDICT_SPATIAL_OK &&
        status != CDICT_SPATIAL_TRUNCATED &&
        status != CDICT_SPATIAL_INVALID_UTF8) ||
      result_length < 0 || result_length > max_results)
  {
    throw_illegal_state(env, "invalid native spatial result");
    goto cleanup_arrays;
  }
  jlong sequence = (*env)->GetLongField(env, jquery, SpatialQuery.sequence);
  jobject result = spatial_result_to_java(env, sequence, status, candidates,
      result_length);
  (*env)->DeleteLocalRef(env, literal_array);
  (*env)->DeleteLocalRef(env, symbol_array);
  (*env)->DeleteLocalRef(env, cost_array);
  return result;

cleanup_arrays:
  if (literal_array != NULL)
    (*env)->DeleteLocalRef(env, literal_array);
  if (symbol_array != NULL)
    (*env)->DeleteLocalRef(env, symbol_array);
  if (cost_array != NULL)
    (*env)->DeleteLocalRef(env, cost_array);
  return NULL;
}

