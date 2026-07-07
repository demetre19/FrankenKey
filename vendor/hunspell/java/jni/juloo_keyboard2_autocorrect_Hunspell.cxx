#include <jni.h>
#include <algorithm>
#include <exception>
#include <string>
#include <vector>

#include "hunspell.hxx"

#pragma GCC diagnostic ignored "-Wunused-parameter"

static void throw_construction_error(JNIEnv *env, char const *msg)
{
  env->ThrowNew(env->FindClass(
        "juloo/keyboard2/autocorrect/Hunspell$ConstructionError"), msg);
}

static std::string string_of_java(JNIEnv *env, jstring str)
{
  char const *chars = env->GetStringUTFChars(str, nullptr);
  std::string out(chars == nullptr ? "" : chars);
  if (chars != nullptr)
    env->ReleaseStringUTFChars(str, chars);
  return out;
}

extern "C" JNIEXPORT jlong JNICALL
Java_juloo_keyboard2_autocorrect_Hunspell_construct_1native(JNIEnv *env,
    jclass cls, jstring jaff_path, jstring jdic_path)
{
  try
  {
    std::string aff_path = string_of_java(env, jaff_path);
    std::string dic_path = string_of_java(env, jdic_path);
    Hunspell *speller = new Hunspell(aff_path.c_str(), dic_path.c_str());
    return reinterpret_cast<jlong>(speller);
  }
  catch (std::exception const &e)
  {
    throw_construction_error(env, e.what());
    return 0;
  }
  catch (...)
  {
    throw_construction_error(env, "Hunspell construction failed");
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_juloo_keyboard2_autocorrect_Hunspell_destroy_1native(JNIEnv *env,
    jclass cls, jlong ptr)
{
  delete reinterpret_cast<Hunspell*>(ptr);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_juloo_keyboard2_autocorrect_Hunspell_spell_1native(JNIEnv *env,
    jclass cls, jlong ptr, jstring jword)
{
  if (ptr == 0)
    return false;
  Hunspell *speller = reinterpret_cast<Hunspell*>(ptr);
  return speller->spell(string_of_java(env, jword)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_juloo_keyboard2_autocorrect_Hunspell_suggest_1native(JNIEnv *env,
    jclass cls, jlong ptr, jstring jword, jint max_count)
{
  jclass string_cls = env->FindClass("java/lang/String");
  if (ptr == 0 || max_count <= 0)
    return env->NewObjectArray(0, string_cls, nullptr);
  Hunspell *speller = reinterpret_cast<Hunspell*>(ptr);
  std::vector<std::string> suggestions = speller->suggest(string_of_java(env,
        jword));
  int len = std::min<int>(suggestions.size(), max_count);
  jobjectArray out = env->NewObjectArray(len, string_cls, nullptr);
  for (int i = 0; i < len; i++)
    env->SetObjectArrayElement(out, i,
        env->NewStringUTF(suggestions[i].c_str()));
  return out;
}
