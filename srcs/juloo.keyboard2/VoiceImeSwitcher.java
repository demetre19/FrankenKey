package juloo.keyboard2;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import java.util.ArrayList;
import java.util.List;

class VoiceImeSwitcher
{
  static final String PREF_LAST_USED = "voice_ime_last_used";
  static final String PREF_KNOWN_IMES = "voice_ime_known";

  /** Switch to the voice IME immediately. Preferences remember the last selected
      voice IME, but voice keys must not stop on a chooser. */
  public static boolean switch_to_voice_ime(InputMethodService ims,
      InputMethodManager imm, SharedPreferences prefs)
  {
    List<IME> imes = get_voice_ime_list(imm);
    if (imes.size() == 0)
      return false;
    String last_used = prefs.getString(PREF_LAST_USED, null);
    String last_known_imes = prefs.getString(PREF_KNOWN_IMES, null);
    String known_imes = serialize_ime_ids(imes);
    IME selected = null;
    if (known_imes.equals(last_known_imes))
      selected = get_ime_by_id(imes, last_used);
    if (selected == null)
      selected = imes.get(0);
    prefs.edit()
      .putString(PREF_LAST_USED, selected.get_id())
      .putString(PREF_KNOWN_IMES, known_imes)
      .apply();
    switch_input_method(ims, selected);
    return true;
  }


  static void switch_input_method(InputMethodService ims, IME ime)
  {
    if (VERSION.SDK_INT < 28)
      ims.switchInputMethod(ime.get_id());
    else
      ims.switchInputMethod(ime.get_id(), ime.subtype);
  }

  static IME get_ime_by_id(List<IME> imes, String id)
  {
    if (id != null)
      for (IME ime : imes)
        if (ime.get_id().equals(id))
          return ime;
    return null;
  }


  static List<IME> get_voice_ime_list(InputMethodManager imm)
  {
    List<IME> imes = new ArrayList<IME>();
    for (InputMethodInfo im : imm.getEnabledInputMethodList())
      for (InputMethodSubtype imst : imm.getEnabledInputMethodSubtypeList(im, true))
        if (imst.getMode().equals("voice"))
          imes.add(new IME(im, imst));
    return imes;
  }

  /** The enabled voice IME set used when deciding whether the saved IME is still valid. */
  static String serialize_ime_ids(List<IME> imes)
  {
    StringBuilder b = new StringBuilder();
    for (IME ime : imes)
    {
      b.append(ime.get_id());
      b.append(',');
    }
    return b.toString();
  }

  static class IME
  {
    public final InputMethodInfo im;
    public final InputMethodSubtype subtype;

    IME(InputMethodInfo im_, InputMethodSubtype st)
    {
      im = im_;
      subtype = st;
    }

    String get_id() { return im.getId(); }

  }
}
