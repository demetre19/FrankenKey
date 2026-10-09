package juloo.keyboard2.suggestions.email;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EmailLearningObserverTest
{
  private static final String ADDR = "de@example.com";
  private static final long NOW = 365L * EmailMemoryStore.DAY_MS;

  private EmailMemoryStore _store;
  private EmailLearningObserver _observer;
  private boolean _settingOn = true;

  @Before
  public void setUp()
  {
    final SharedPreferences prefs = RuntimeEnvironment.getApplication()
      .getSharedPreferences(EmailMemoryStore.PREF_NAME, Context.MODE_PRIVATE);
    prefs.edit().clear().apply();
    _store = new EmailMemoryStore(new EmailMemoryStore.PrefsSource()
    {
      public SharedPreferences get() { return prefs; }
    }, new Handler(Looper.getMainLooper()));
    _observer = new EmailLearningObserver(_store,
        new Handler(Looper.getMainLooper()),
        new EmailLearningObserver.MemoryEnabled()
        {
          public boolean get() { return _settingOn; }
        },
        new EmailLearningObserver.Clock()
        {
          public long now() { return NOW; }
        });
  }

  private void flush()
  {
    Robolectric.flushForegroundThreadScheduler();
  }

  private static EditorInfo info(int inputType)
  {
    EditorInfo info = new EditorInfo();
    info.inputType = inputType;
    return info;
  }

  private static EditorInfo emailField()
  {
    return info(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  }

  private static EditorInfo proseField()
  {
    return info(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_NORMAL);
  }

  private List<EmailMemoryStore.Entry> entries()
  {
    return _store.entries(NOW);
  }

  @Test
  public void field_finish_learns_changed_address_plus_two()
  {
    _observer.onFieldFinished(emailField(), "", ADDR);
    flush();
    List<EmailMemoryStore.Entry> entries = entries();
    assertEquals(1, entries.size());
    assertEquals(ADDR, entries.get(0).address);
    assertEquals(2, entries.get(0).count);
    assertEquals(EmailMemoryStore.SOURCE_FIELD,
        entries.get(0).sources);
  }

  @Test
  public void field_finish_skips_unchanged_prefilled_text()
  {
    _observer.onFieldFinished(emailField(), ADDR, ADDR);
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void field_finish_skips_non_address_text()
  {
    _observer.onFieldFinished(emailField(), "", "just words");
    _observer.onFieldFinished(emailField(), "", "a@b");
    _observer.onFieldFinished(emailField(), "", ADDR + " extra");
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void field_finish_learns_in_soft_hint_field()
  {
    EditorInfo info = proseField();
    info.hintText = "Email";
    _observer.onFieldFinished(info, "", ADDR);
    flush();
    assertEquals(1, entries().size());
    assertEquals(EmailMemoryStore.SOURCE_FIELD,
        entries().get(0).sources);
  }

  @Test
  public void field_finish_skips_email_subject_hint()
  {
    EditorInfo info = proseField();
    info.hintText = "Email subject";
    _observer.onFieldFinished(info, "", ADDR);
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void field_finish_skips_guarded_editors()
  {
    EditorInfo password = info(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    EditorInfo number = info(InputType.TYPE_CLASS_NUMBER);
    EditorInfo phone = info(InputType.TYPE_CLASS_PHONE);
    EditorInfo unknown = info(0 /* TYPE_NULL */);
    EditorInfo flagged = emailField();
    flagged.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    _observer.onFieldFinished(password, "", ADDR);
    _observer.onFieldFinished(number, "", ADDR);
    _observer.onFieldFinished(phone, "", ADDR);
    _observer.onFieldFinished(unknown, "", ADDR);
    _observer.onFieldFinished(flagged, "", ADDR);
    _observer.onFieldFinished(null, "", ADDR);
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void prose_separator_learns_plus_one()
  {
    _observer.onProseSeparator(proseField(), ADDR);
    flush();
    List<EmailMemoryStore.Entry> entries = entries();
    assertEquals(1, entries.size());
    assertEquals(1, entries.get(0).count);
    assertEquals(EmailMemoryStore.SOURCE_PROSE,
        entries.get(0).sources);
  }

  @Test
  public void prose_separator_skips_structured_and_secret_editors()
  {
    EditorInfo uri = info(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_URI);
    EditorInfo password = info(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    EditorInfo flagged = proseField();
    flagged.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    _observer.onProseSeparator(emailField(), ADDR);
    _observer.onProseSeparator(uri, ADDR);
    _observer.onProseSeparator(password, ADDR);
    _observer.onProseSeparator(flagged, ADDR);
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void prose_separator_skips_invalid_tokens()
  {
    _observer.onProseSeparator(proseField(), "word");
    _observer.onProseSeparator(proseField(), "@handle");
    _observer.onProseSeparator(proseField(), "a@b@c.d");
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void selected_learns_plus_two()
  {
    _observer.onAddressSelected(proseField(), ADDR);
    flush();
    List<EmailMemoryStore.Entry> entries = entries();
    assertEquals(1, entries.size());
    assertEquals(2, entries.get(0).count);
    assertEquals(EmailMemoryStore.SOURCE_SELECTED,
        entries.get(0).sources);
  }

  @Test
  public void selected_skips_flagged_and_secret_editors()
  {
    EditorInfo flagged = proseField();
    flagged.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    EditorInfo password = info(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    _observer.onAddressSelected(flagged, ADDR);
    _observer.onAddressSelected(password, ADDR);
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void setting_off_learns_nothing()
  {
    _settingOn = false;
    _observer.onFieldFinished(emailField(), "", ADDR);
    _observer.onProseSeparator(proseField(), ADDR);
    _observer.onAddressSelected(proseField(), ADDR);
    flush();
    assertTrue(entries().isEmpty());
  }

  @Test
  public void events_accumulate_sources_and_count()
  {
    _observer.onProseSeparator(proseField(), ADDR);
    _observer.onAddressSelected(proseField(), ADDR);
    _observer.onFieldFinished(emailField(), "", ADDR);
    flush();
    List<EmailMemoryStore.Entry> entries = entries();
    assertEquals(1, entries.size());
    assertEquals(1 + 2 + 2, entries.get(0).count);
    assertEquals(EmailMemoryStore.SOURCE_PROSE
        | EmailMemoryStore.SOURCE_SELECTED
        | EmailMemoryStore.SOURCE_FIELD,
        entries.get(0).sources);
  }

  @Test
  public void store_unavailable_never_throws()
  {
    EmailMemoryStore dead = new EmailMemoryStore(
        new EmailMemoryStore.PrefsSource()
        {
          public SharedPreferences get() { return null; }
        }, new Handler(Looper.getMainLooper()));
    EmailLearningObserver observer = new EmailLearningObserver(dead,
        new Handler(Looper.getMainLooper()),
        new EmailLearningObserver.MemoryEnabled()
        {
          public boolean get() { return true; }
        },
        new EmailLearningObserver.Clock()
        {
          public long now() { return NOW; }
        });
    observer.onFieldFinished(emailField(), "", ADDR);
    observer.onProseSeparator(proseField(), ADDR);
    observer.onAddressSelected(proseField(), ADDR);
    flush();
    assertTrue(dead.entries(NOW).isEmpty());
  }
}
