package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import juloo.keyboard2.suggestions.PersonalizationStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class LearnedWordsActivityTest
{
  private Context _context;
  private SharedPreferences _prefs;
  private ActivityController<LearnedWordsActivity> _controller;

  @Before
  public void setUp()
  {
    _context = RuntimeEnvironment.getApplication();
    _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
    _prefs.edit().clear().commit();
  }

  @After
  public void tearDown()
  {
    if (_controller != null)
      _controller.pause().stop().destroy();
    _prefs.edit().clear().commit();
  }

  @Test
  public void page_separates_taught_words_and_combines_search_with_length()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    store.record_selected_correction("ordimary", "ordinary");
    assertTrue(store.learn_word("rhinoceros"));
    assertTrue(store.learn_word("OMP"));
    assertTrue(store.learn_word("alpha"));
    LearnedWordsActivity activity = launchActivity();
    ListView list = (ListView)activity.findViewById(R.id.learned_words_list);

    assertWords(list, "alpha", "OMP", "rhinoceros");

    LinearLayout lengths = (LinearLayout)activity.findViewById(
        R.id.learned_words_length_filters);
    assertTrue("The 3-letter control must filter the taught tab by code-point length.",
        lengths.getChildAt(3).performClick());
    assertWords(list, "OMP");
    assertTrue("The 10+ control must include words with ten or more letters.",
        lengths.getChildAt(10).performClick());
    assertWords(list, "rhinoceros");
    assertTrue(lengths.getChildAt(0).performClick());

    ((EditText)activity.findViewById(R.id.learned_words_search))
      .setText("mp");
    shadowOf(Looper.getMainLooper()).idle();
    assertWords(list, "OMP");

    ((EditText)activity.findViewById(R.id.learned_words_search)).setText("");
    assertTrue(activity.findViewById(
          R.id.learned_words_adaptive_tab).performClick());
    assertWords(list, "ordinary");

    ((EditText)activity.findViewById(R.id.learned_words_search))
      .setText("missing");
    shadowOf(Looper.getMainLooper()).idle();
    TextView message =
      (TextView)activity.findViewById(R.id.learned_words_message);
    assertEquals("A combined filter with no local matches must explain why the list is empty.",
        activity.getString(R.string.learned_words_no_matches),
        message.getText().toString());
    assertEquals(View.VISIBLE, message.getVisibility());
    assertEquals(View.GONE, list.getVisibility());
  }


  @Test
  public void top_field_teaches_one_valid_word_without_keyboard_learning()
  {
    LearnedWordsActivity activity = launchActivity();
    EditText add =
      (EditText)activity.findViewById(R.id.learned_words_add);
    EditText search =
      (EditText)activity.findViewById(R.id.learned_words_search);
    int minimumPadding = Math.round(12f *
        activity.getResources().getDisplayMetrics().density);
    ViewGroup.MarginLayoutParams addMargins =
      (ViewGroup.MarginLayoutParams)add.getLayoutParams();
    ViewGroup.MarginLayoutParams searchMargins =
      (ViewGroup.MarginLayoutParams)search.getLayoutParams();
    assertTrue("The teaching field must keep text visibly inset after first attachment.",
        addMargins.getMarginStart() >= minimumPadding);
    assertTrue("The search field must keep text visibly inset after first attachment.",
        searchMargins.getMarginStart() >= minimumPadding);
    long revision = PersonalizationStore.external_revision(_prefs);

    assertTrue("Direct-entry text must prohibit the keyboard from learning the management field itself.",
        (add.getImeOptions()
          & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
    add.setText("cazoo");
    assertTrue(activity.findViewById(R.id.learned_words_learn).performClick());
    shadowOf(Looper.getMainLooper()).idle();

    assertTrue("The top field must directly teach the entered word.",
        new PersonalizationStore(_prefs).is_learned("cazoo"));
    assertTrue("Direct teaching must mark the word as explicitly taught.",
        new PersonalizationStore(_prefs).is_taught("cazoo"));
    assertEquals("Successful direct teaching must clear the field for the next word.",
        "", add.getText().toString());
    assertEquals(revision + 1L,
        PersonalizationStore.external_revision(_prefs));
    assertWords((ListView)activity.findViewById(R.id.learned_words_list),
        "cazoo");

    add.setText("a");
    assertTrue(activity.findViewById(R.id.learned_words_learn).performClick());
    assertNotNull("Invalid tokens must produce an inline recovery message.",
        add.getError());
    assertFalse(new PersonalizationStore(_prefs).is_learned("a"));
  }

  @Test
  public void row_delete_requires_confirmation_then_unlearns_the_word()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    store.learn_word("hello");
    LearnedWordsActivity activity = launchActivity();
    ListView list = (ListView)activity.findViewById(R.id.learned_words_list);
    View row = list.getAdapter().getView(0, null, list);
    TextView wordView =
      (TextView)row.findViewById(R.id.learned_words_row_word);
    int minimumPadding = Math.round(12f *
        activity.getResources().getDisplayMetrics().density);
    assertTrue("Every learned-word label must keep a direct internal start inset.",
        wordView.getPaddingStart() >= minimumPadding);
    wordView.setScrollX(minimumPadding);
    row = list.getAdapter().getView(0, row, list);
    assertEquals("Recycled rows must reset stale horizontal text scrolling.",
        0, row.findViewById(R.id.learned_words_row_word).getScrollX());

    assertTrue(row.findViewById(R.id.learned_words_row_forget).performClick());
    AlertDialog cancelled = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull("Per-word Delete must open a destructive confirmation.",
        cancelled);
    assertTrue(cancelled.getButton(DialogInterface.BUTTON_NEGATIVE)
        .performClick());
    assertTrue("Cancel must preserve the learned word.",
        new PersonalizationStore(_prefs).is_learned("hello"));

    row = list.getAdapter().getView(0, null, list);
    assertTrue(row.findViewById(R.id.learned_words_row_forget).performClick());
    AlertDialog confirmed = ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(confirmed.getButton(DialogInterface.BUTTON_POSITIVE)
        .performClick());
    shadowOf(Looper.getMainLooper()).idle();

    assertFalse("Confirmed row deletion must unlearn the word.",
        new PersonalizationStore(_prefs).is_learned("hello"));
    assertEquals(View.VISIBLE,
        activity.findViewById(R.id.learned_words_message).getVisibility());
    assertEquals(View.GONE, list.getVisibility());
  }

  private LearnedWordsActivity launchActivity()
  {
    _controller = Robolectric.buildActivity(LearnedWordsActivity.class).setup();
    return _controller.get();
  }

  private static void assertWords(ListView list, String... expected)
  {
    ListAdapter adapter = list.getAdapter();
    assertEquals("The visible learned-word count must match the filtered result.",
        expected.length, adapter.getCount());
    for (int i = 0; i < expected.length; ++i)
      assertEquals("Learned words must remain in deterministic alphabetical order.",
          expected[i], adapter.getItem(i));
  }
}
