package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import juloo.keyboard2.suggestions.PersonalizationStore;

/** Private settings surface for directly managing on-device learned words. */
public final class LearnedWordsActivity extends Activity
{
  private static final int COLOR_ACCENT = 0xff74d6c9;
  private static final int COLOR_PRIMARY = 0xfff4f7fa;
  private static final int COLOR_SURFACE = 0xff20252b;
  private final List<String> _allWords = new ArrayList<String>();
  private final List<String> _words = new ArrayList<String>();
  private final Set<String> _taughtKeys = new HashSet<String>();
  private SharedPreferences _prefs;
  private EditText _addWord;
  private EditText _search;
  private TextView _message;
  private ListView _list;
  private WordAdapter _adapter;
  private Button _taughtTab;
  private Button _adaptiveTab;
  private Button[] _lengthButtons;
  private boolean _showTaught = true;
  private int _lengthFilter = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    setContentView(R.layout.learned_words_activity);
    _prefs = PreferenceManager.getDefaultSharedPreferences(this);
    _addWord = (EditText)findViewById(R.id.learned_words_add);
    _search = (EditText)findViewById(R.id.learned_words_search);
    _message = (TextView)findViewById(R.id.learned_words_message);
    _list = (ListView)findViewById(R.id.learned_words_list);
    _taughtTab = (Button)findViewById(R.id.learned_words_taught_tab);
    _adaptiveTab = (Button)findViewById(R.id.learned_words_adaptive_tab);
    _adapter = new WordAdapter();
    _list.setAdapter(_adapter);
    findViewById(R.id.learned_words_back).setOnClickListener(
        _view -> finish());
    findViewById(R.id.learned_words_learn).setOnClickListener(
        _view -> learnWord());
    _taughtTab.setOnClickListener(_view -> setMode(true));
    _adaptiveTab.setOnClickListener(_view -> setMode(false));
    setupLengthFilters();
    updateModeButtons();
    _addWord.setOnEditorActionListener((_view, actionId, _event) -> {
        if (actionId != EditorInfo.IME_ACTION_DONE)
          return false;
        learnWord();
        return true;
      });
    _search.addTextChangedListener(new TextWatcher()
    {
      @Override public void beforeTextChanged(CharSequence value, int start,
          int count, int after) {}
      @Override public void onTextChanged(CharSequence value, int start,
          int before, int count)
      {
        filter(value == null ? "" : value.toString());
      }
      @Override public void afterTextChanged(Editable value) {}
    });
    refreshWords();
  }


  @Override
  protected void onResume()
  {
    super.onResume();
    if (_adapter != null)
      refreshWords();
  }

  private void learnWord()
  {
    String word = _addWord.getText().toString().trim();
    if (!PersonalizationStore.is_learnable(word))
    {
      _addWord.setError(getString(R.string.learned_words_invalid));
      return;
    }
    PersonalizationStore store = new PersonalizationStore(_prefs);
    if (!store.learn_word(word))
    {
      _addWord.setError(getString(R.string.learned_words_already_learned));
      return;
    }
    PersonalizationStore.notify_external_change(_prefs);
    _addWord.setText("");
    _showTaught = true;
    updateModeButtons();
    refreshWords();
    Toast.makeText(this, getString(R.string.learned_words_learned, word),
        Toast.LENGTH_SHORT).show();
  }

  private void refreshWords()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    _taughtKeys.clear();
    for (String word : store.taught_words())
      _taughtKeys.add(word.toLowerCase(Locale.ROOT));
    _allWords.clear();
    _allWords.addAll(_showTaught
        ? store.taught_words() : store.learned_words());
    filter(_search == null ? "" : _search.getText().toString());
  }

  private void filter(String query)
  {
    String normalized = query == null ? ""
      : query.trim().toLowerCase(Locale.getDefault());
    _words.clear();
    for (String word : _allWords)
    {
      int length = word.codePointCount(0, word.length());
      boolean lengthMatches = _lengthFilter == 0
        || (_lengthFilter == 10 ? length >= 10 : length == _lengthFilter);
      if (lengthMatches && (normalized.isEmpty()
            || word.toLowerCase(Locale.getDefault()).contains(normalized)))
        _words.add(word);
    }
    _adapter.notifyDataSetChanged();
    boolean noWords = _allWords.isEmpty();
    boolean noMatches = !noWords && _words.isEmpty();
    _message.setText(noWords
        ? (_showTaught ? R.string.learned_words_taught_empty
          : R.string.learned_words_adaptive_empty)
        : R.string.learned_words_no_matches);
    _message.setVisibility(noWords || noMatches ? View.VISIBLE : View.GONE);
    _list.setVisibility(noWords || noMatches ? View.GONE : View.VISIBLE);
  }

  private void setMode(boolean showTaught)
  {
    if (_showTaught == showTaught)
      return;
    _showTaught = showTaught;
    updateModeButtons();
    refreshWords();
  }

  private void updateModeButtons()
  {
    styleFilterButton(_taughtTab, _showTaught);
    styleFilterButton(_adaptiveTab, !_showTaught);
  }

  private void setupLengthFilters()
  {
    LinearLayout row = (LinearLayout)findViewById(
        R.id.learned_words_length_filters);
    _lengthButtons = new Button[11];
    for (int length = 0; length <= 10; length++)
    {
      final int selectedLength = length;
      Button button = new Button(this);
      button.setAllCaps(false);
      button.setMinWidth(dp(length == 0 ? 92 : 48));
      button.setText(length == 0
          ? getString(R.string.learned_words_length_all)
          : length == 10 ? "10+" : Integer.toString(length));
      button.setOnClickListener(_view -> setLengthFilter(selectedLength));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
      params.setMarginEnd(dp(6));
      row.addView(button, params);
      _lengthButtons[length] = button;
    }
    updateLengthButtons();
  }

  private void setLengthFilter(int length)
  {
    if (_lengthFilter == length)
      return;
    _lengthFilter = length;
    updateLengthButtons();
    filter(_search.getText().toString());
  }

  private void updateLengthButtons()
  {
    if (_lengthButtons == null)
      return;
    for (int i = 0; i < _lengthButtons.length; i++)
      styleFilterButton(_lengthButtons[i], i == _lengthFilter);
  }

  private void styleFilterButton(Button button, boolean selected)
  {
    if (button == null)
      return;
    button.setBackgroundTintList(ColorStateList.valueOf(
          selected ? COLOR_ACCENT : COLOR_SURFACE));
    button.setTextColor(selected ? 0xff07100f : COLOR_PRIMARY);
  }

  private int dp(int value)
  {
    return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
  }
  private void confirmForget(String word)
  {
    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.adaptive_unlearn_confirm_title, word))
      .setMessage(R.string.adaptive_unlearn_confirm_message)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.adaptive_unlearn_confirm_positive,
          (_dialog, _which) -> forgetWord(word))
      .show();
  }

  private void forgetWord(String word)
  {
    if (!new PersonalizationStore(_prefs).unlearn_word(word))
      return;
    PersonalizationStore.notify_external_change(_prefs);
    refreshWords();
    Toast.makeText(this, getString(R.string.learned_words_forgot, word),
        Toast.LENGTH_SHORT).show();
  }

  private final class WordAdapter extends BaseAdapter
  {
    @Override public int getCount() { return _words.size(); }
    @Override public String getItem(int position) { return _words.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View recycled, ViewGroup parent)
    {
      View row = recycled == null
        ? LayoutInflater.from(LearnedWordsActivity.this).inflate(
            R.layout.learned_words_row, parent, false)
        : recycled;
      String word = getItem(position);
      TextView wordView =
        (TextView)row.findViewById(R.id.learned_words_row_word);
      wordView.setText(word);
      wordView.setTextColor(_taughtKeys.contains(
            word.toLowerCase(Locale.ROOT)) ? COLOR_ACCENT : COLOR_PRIMARY);
      Button forget =
        (Button)row.findViewById(R.id.learned_words_row_forget);
      forget.setContentDescription(getString(
          R.string.learned_words_forget_accessibility, word));
      forget.setOnClickListener(_view -> confirmForget(word));
      return row;
    }
  }
}
