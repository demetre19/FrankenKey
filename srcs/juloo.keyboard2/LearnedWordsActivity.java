package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import juloo.keyboard2.suggestions.PersonalizationStore;

/**
 * Private settings surface for learned literals and correction rules.
 *
 * This activity is a tab host: tabs are registered by class through
 * [register_tab] so feature lanes add their panes without editing this file.
 * A tab that returns null from [Tab.createContent] drives the shared
 * word-list pane (scope text, add field, search, length filters, and the row
 * list); that is what the built-in Words and Corrections tabs do. A tab that
 * returns a view owns the whole content area below the tab strip.
 * [Tab.onVisible] runs whenever a tab is selected and on every
 * [Activity.onResume].
 */
public final class LearnedWordsActivity extends Activity
{
  public static final String EXTRA_REPLACEMENT_SOURCE =
    "juloo.keyboard2.extra.REPLACEMENT_SOURCE";

  private static final int COLOR_ACCENT = 0xff74d6c9;
  private static final int COLOR_PRIMARY = 0xfff4f7fa;
  private static final int COLOR_SECONDARY = 0xffa8b2be;
  private static final int COLOR_SURFACE = 0xff20252b;

  /**
   * One tab of the host. Implementations must expose a public no-arg
   * constructor.
   */
  public static abstract class Tab
  {
    private LearnedWordsActivity _host;
    View _content;

    void attach(LearnedWordsActivity host)
    {
      _host = host;
    }

    /** Stable id for this tab's strip button; [View.NO_ID] by default. */
    public int buttonId()
    {
      return View.NO_ID;
    }

    /** The label on the tab button. */
    public abstract CharSequence title();

    /**
     * Build the tab's own content view. Called lazily the first time the
     * tab is selected; the returned view is retained and re-attached on
     * later selections. Returning null means the tab drives the shared
     * word-list pane instead — see [WordListTab].
     */
    protected View createContent()
    {
      return null;
    }

    /** Called when this tab becomes visible, including on [onResume]. */
    public void onVisible() {}

    final View content()
    {
      if (_content == null)
        _content = createContent();
      return _content;
    }

    final LearnedWordsActivity host()
    {
      return _host;
    }
  }

  private static final List<Class<? extends Tab>> _tabClasses =
    new ArrayList<Class<? extends Tab>>();

  static
  {
    register_tab(WordsTab.class);
    register_tab(CorrectionsTab.class);
  }

  /**
   * Register a tab class. Registration order is the tab order. Tabs are
   * constructed when the activity is created.
   */
  public static void register_tab(Class<? extends Tab> tabClass)
  {
    if (tabClass == null)
      throw new IllegalArgumentException("tab class must not be null");
    _tabClasses.add(tabClass);
  }

  private final List<Tab> _tabs = new ArrayList<Tab>();
  private final List<Button> _tabButtons = new ArrayList<Button>();
  private LinearLayout _tabStrip;
  private FrameLayout _tabContent;
  private View _sharedPane;
  private int _selectedTab = -1;
  private WordListTab _activeListTab;
  private SharedPreferences _prefs;
  private String _searchText = "";
  private int _lengthFilter = 0;
  private EditText _addWord;
  private EditText _search;
  private TextView _scopeExplanation;
  private TextView _message;
  private ListView _list;
  private Button _primaryAction;
  private Button[] _lengthButtons;
  private RowAdapter _adapter;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    if (getActionBar() != null)
      getActionBar().hide();
    setContentView(R.layout.learned_words_activity);
    _prefs = PreferenceManager.getDefaultSharedPreferences(this);
    _tabStrip = (LinearLayout)findViewById(R.id.learned_words_tabs);
    _tabContent =
      (FrameLayout)findViewById(R.id.learned_words_tab_content);
    _sharedPane = findViewById(R.id.learned_words_shared_pane);
    _addWord = (EditText)findViewById(R.id.learned_words_add);
    _search = (EditText)findViewById(R.id.learned_words_search);
    _scopeExplanation = (TextView)findViewById(
        R.id.learned_words_scope_explanation);
    _message = (TextView)findViewById(R.id.learned_words_message);
    _list = (ListView)findViewById(R.id.learned_words_list);
    _primaryAction = (Button)findViewById(R.id.learned_words_primary_action);
    _adapter = new RowAdapter();
    _list.setAdapter(_adapter);
    findViewById(R.id.learned_words_back).setOnClickListener(
        _view -> finish());
    _primaryAction.setOnClickListener(_view -> {
        if (_activeListTab != null)
          _activeListTab.performPrimaryAction();
      });
    _addWord.setOnEditorActionListener((_view, actionId, _event) -> {
        if (actionId != EditorInfo.IME_ACTION_DONE || _activeListTab == null)
          return false;
        _activeListTab.performPrimaryAction();
        return true;
      });
    _search.addTextChangedListener(new TextWatcher()
    {
      @Override public void beforeTextChanged(CharSequence value, int start,
          int count, int after) {}
      @Override public void onTextChanged(CharSequence value, int start,
          int before, int count)
      {
        _searchText = value == null ? "" : value.toString();
        if (_activeListTab != null)
          _activeListTab.filter(_searchText);
      }
      @Override public void afterTextChanged(Editable value) {}
    });
    setupLengthFilters();
    buildTabs();
    selectTab(0);

    String replacementSource = getIntent().getStringExtra(
        EXTRA_REPLACEMENT_SOURCE);
    if (PersonalizationStore.is_learnable(replacementSource))
    {
      CorrectionsTab corrections = (CorrectionsTab)findTab(
          CorrectionsTab.class);
      if (corrections != null)
      {
        selectTab(_tabs.indexOf(corrections));
        corrections.prefillReplacement(replacementSource);
      }
    }
  }

  private void buildTabs()
  {
    _tabs.clear();
    _tabButtons.clear();
    _tabStrip.removeAllViews();
    for (int i = 0; i < _tabClasses.size(); i++)
    {
      final Tab tab = newTab(_tabClasses.get(i));
      tab.attach(this);
      _tabs.add(tab);
      final int index = i;
      Button button = new Button(this);
      button.setAllCaps(false);
      button.setText(tab.title());
      button.setBackgroundResource(R.drawable.reader_icon_button);
      if (tab.buttonId() != View.NO_ID)
        button.setId(tab.buttonId());
      button.setOnClickListener(_view -> selectTab(index));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          0, dp(48), 1f);
      params.setMarginEnd(dp(4));
      params.setMarginStart(i == 0 ? 0 : dp(4));
      _tabStrip.addView(button, params);
      _tabButtons.add(button);
    }
  }

  private Tab newTab(Class<? extends Tab> tabClass)
  {
    try
    {
      Constructor<? extends Tab> ctor = tabClass.getDeclaredConstructor();
      return ctor.newInstance();
    }
    catch (Exception e)
    {
      throw new IllegalStateException(
          "Learned Words tab must have a public no-arg constructor: "
          + tabClass.getName(), e);
    }
  }

  private Tab findTab(Class<? extends Tab> tabClass)
  {
    for (Tab tab : _tabs)
      if (tabClass.isInstance(tab))
        return tab;
    return null;
  }

  private void selectTab(int index)
  {
    if (index < 0 || index >= _tabs.size())
      return;
    _selectedTab = index;
    Tab selected = _tabs.get(index);
    for (int i = 0; i < _tabs.size(); i++)
      styleFilterButton(_tabButtons.get(i), i == index);
    _tabContent.removeAllViews();
    View content = selected.content();
    if (content != null)
    {
      _tabContent.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
      _tabContent.setVisibility(View.VISIBLE);
      _sharedPane.setVisibility(View.GONE);
      _activeListTab = null;
    }
    else
    {
      _tabContent.setVisibility(View.GONE);
      _sharedPane.setVisibility(View.VISIBLE);
      _activeListTab = (WordListTab)selected;
    }
    selected.onVisible();
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    if (_selectedTab >= 0 && _selectedTab < _tabs.size())
      _tabs.get(_selectedTab).onVisible();
  }

  SharedPreferences prefs()
  {
    return _prefs;
  }

  void styleFilterButton(Button button, boolean selected)
  {
    if (button == null)
      return;
    button.setBackgroundTintList(ColorStateList.valueOf(
          selected ? COLOR_ACCENT : COLOR_SURFACE));
    button.setTextColor(selected ? 0xff07100f : COLOR_PRIMARY);
  }

  int dp(int value)
  {
    return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
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
      params.setMarginEnd(dp(8));
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
    if (_activeListTab != null)
      _activeListTab.filter(_searchText);
  }

  private void updateLengthButtons()
  {
    if (_lengthButtons == null)
      return;
    for (int i = 0; i < _lengthButtons.length; i++)
      styleFilterButton(_lengthButtons[i], i == _lengthFilter);
  }

  /**
   * Row source for the shared word-list pane. The list always renders the
   * rows of whichever [WordListTab] is selected; a tab supplying its own
   * [Tab.createContent] view owns the whole content area instead.
   */
  private final class RowAdapter extends BaseAdapter
  {
    @Override public int getCount()
    { return _activeListTab == null ? 0 : _activeListTab._rows.size(); }
    @Override public RowItem getItem(int position)
    { return _activeListTab._rows.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View recycled, ViewGroup parent)
    {
      View rowView = recycled == null
        ? LayoutInflater.from(LearnedWordsActivity.this).inflate(
            R.layout.learned_words_row, parent, false)
        : recycled;
      RowItem row = getItem(position);
      TextView wordView = (TextView)rowView.findViewById(
          R.id.learned_words_row_word);
      TextView mappingView = (TextView)rowView.findViewById(
          R.id.learned_words_row_mapping);
      Button edit = (Button)rowView.findViewById(
          R.id.learned_words_row_edit);
      Button delete = (Button)rowView.findViewById(
          R.id.learned_words_row_forget);

      wordView.setText(row.word);
      wordView.setScrollX(0);
      wordView.setTextColor(row.isTaught() ? COLOR_ACCENT : COLOR_PRIMARY);
      if (row.isTaught())
      {
        mappingView.setVisibility(View.GONE);
        edit.setText(R.string.learned_words_replace_action);
        edit.setContentDescription(getString(
              R.string.learned_words_replace_accessibility, row.word));
      }
      else
      {
        String target = row.correction.target == null
          ? getString(R.string.learned_words_best_suggestion)
          : row.correction.target;
        mappingView.setText(getString(
              R.string.learned_words_mapping, target));
        mappingView.setVisibility(View.VISIBLE);
        edit.setText(R.string.learned_words_edit_action);
        edit.setContentDescription(getString(
              R.string.learned_words_edit_accessibility, row.word, target));
      }
      edit.setOnClickListener(_view -> _activeListTab.editRow(row));
      delete.setContentDescription(row.isTaught()
          ? getString(R.string.learned_words_forget_accessibility, row.word)
          : getString(
            R.string.learned_words_delete_correction_accessibility,
            row.word));
      delete.setOnClickListener(_view -> _activeListTab.confirmDelete(row));
      return rowView;
    }
  }

  /**
   * Shared list pane used by the Words and Corrections tabs. A tab of this
   * kind supplies only its row source, header copy, and primary action; the
   * host binds the shared views to it each time it becomes visible.
   */
  private static abstract class WordListTab extends Tab
  {
    final List<RowItem> _allRows = new ArrayList<RowItem>();
    final List<RowItem> _rows = new ArrayList<RowItem>();

    abstract boolean isTaughtMode();

    void performPrimaryAction()
    {
      if (isTaughtMode())
        teachWord();
      else
        beginReplacement();
    }

    private void teachWord()
    {
      LearnedWordsActivity activity = host();
      String word = activity._addWord.getText().toString().trim();
      if (!PersonalizationStore.is_learnable(word))
      {
        activity._addWord.setError(activity.getString(
              R.string.learned_words_invalid));
        return;
      }
      PersonalizationStore store =
        new PersonalizationStore(activity.prefs());
      if (!store.learn_word(word))
      {
        activity._addWord.setError(activity.getString(
              R.string.learned_words_already_learned));
        return;
      }
      PersonalizationStore.notify_external_change(activity.prefs());
      activity._addWord.setText("");
      refreshRows();
      Toast.makeText(activity, activity.getString(
            R.string.learned_words_learned, word),
          Toast.LENGTH_SHORT).show();
    }

    private void beginReplacement()
    {
      LearnedWordsActivity activity = host();
      String source = activity._addWord.getText().toString().trim();
      if (!PersonalizationStore.is_learnable(source))
      {
        activity._addWord.setError(activity.getString(
              R.string.learned_words_invalid));
        return;
      }
      PersonalizationStore.ReplacementRule existing =
        new PersonalizationStore(activity.prefs()).replacement_rule(source);
      showReplacementEditor(source,
          existing == null ? null : existing.target);
    }

    void showReplacementEditor(final String source, String currentTarget)
    {
      final LearnedWordsActivity activity = host();
      final EditText target = new EditText(activity);
      target.setSingleLine(true);
      target.setInputType(InputType.TYPE_CLASS_TEXT
          | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      target.setImeOptions(EditorInfo.IME_ACTION_DONE
          | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
      target.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
      target.setHint(R.string.learned_words_replacement_best_hint);
      target.setTextColor(COLOR_PRIMARY);
      target.setHintTextColor(COLOR_SECONDARY);
      target.setBackgroundResource(R.drawable.launcher_input);
      target.setPadding(activity.dp(12), 0, activity.dp(12), 0);
      target.setMinHeight(activity.dp(48));
      if (currentTarget != null)
      {
        target.setText(currentTarget);
        target.setSelection(target.length());
      }
      LinearLayout targetContainer = new LinearLayout(activity);
      targetContainer.setPadding(activity.dp(20), activity.dp(8),
          activity.dp(20), 0);
      targetContainer.addView(target, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(48)));

      final AlertDialog dialog = new AlertDialog.Builder(activity)
        .setTitle(activity.getString(
              R.string.learned_words_replacement_title, source))
        .setMessage(R.string.learned_words_replacement_message)
        .setView(targetContainer)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.learned_words_replacement_save, null)
        .create();
      dialog.setOnShowListener(_dialog -> {
          dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            .setOnClickListener(
                _view -> saveReplacement(dialog, source, target));
        });
      target.setOnEditorActionListener((_view, actionId, _event) -> {
          if (actionId != EditorInfo.IME_ACTION_DONE)
            return false;
          saveReplacement(dialog, source, target);
          return true;
        });
      dialog.show();
    }

    private void saveReplacement(AlertDialog dialog, String source,
        EditText targetField)
    {
      LearnedWordsActivity activity = host();
      String target = targetField.getText().toString().trim();
      if (target.length() != 0 && (!PersonalizationStore.is_learnable(target)
            || source.trim().equalsIgnoreCase(target)))
      {
        targetField.setError(activity.getString(
              R.string.learned_words_replacement_invalid));
        return;
      }
      PersonalizationStore store =
        new PersonalizationStore(activity.prefs());
      PersonalizationStore.ReplacementRule previous =
        store.replacement_rule(source);
      boolean alreadySaved = previous != null
        && (previous.target == null ? target.length() == 0
          : previous.target.equalsIgnoreCase(target));
      if (!alreadySaved && !store.set_replacement(source,
            target.length() == 0 ? null : target))
      {
        targetField.setError(activity.getString(
              R.string.learned_words_replacement_not_saved));
        return;
      }
      PersonalizationStore.notify_external_change(activity.prefs());
      activity._addWord.setText("");
      refreshRows();
      String destination = target.length() == 0
        ? activity.getString(R.string.learned_words_best_suggestion)
        : target;
      Toast.makeText(activity, activity.getString(
            R.string.learned_words_replacement_saved, source, destination),
          Toast.LENGTH_SHORT).show();
      dialog.dismiss();
    }

    private void refreshRows()
    {
      LearnedWordsActivity activity = host();
      _allRows.clear();
      PersonalizationStore store =
        new PersonalizationStore(activity.prefs());
      if (isTaughtMode())
        for (String word : store.taught_words())
          _allRows.add(RowItem.taught(word));
      else
        for (PersonalizationStore.CorrectionEntry correction
            : store.correction_entries())
          _allRows.add(RowItem.correction(correction));
      filter(activity._searchText);
    }

    void filter(String query)
    {
      LearnedWordsActivity activity = host();
      String normalized = query == null ? ""
        : query.trim().toLowerCase(Locale.ROOT);
      _rows.clear();
      for (RowItem row : _allRows)
      {
        int length = row.word.codePointCount(0, row.word.length());
        boolean lengthMatches = activity._lengthFilter == 0
          || (activity._lengthFilter == 10
            ? length >= 10 : length == activity._lengthFilter);
        String target = row.correction == null || row.correction.target == null
          ? "" : row.correction.target.toLowerCase(Locale.ROOT);
        if (lengthMatches && (normalized.isEmpty()
              || row.word.toLowerCase(Locale.ROOT).contains(normalized)
              || target.contains(normalized)))
          _rows.add(row);
      }
      activity._adapter.notifyDataSetChanged();
      boolean noRows = _allRows.isEmpty();
      boolean noMatches = !noRows && _rows.isEmpty();
      activity._message.setText(noRows
          ? (isTaughtMode() ? R.string.learned_words_taught_empty
            : R.string.learned_words_corrections_empty)
          : R.string.learned_words_no_matches);
      activity._message.setVisibility(
          noRows || noMatches ? View.VISIBLE : View.GONE);
      activity._list.setVisibility(
          noRows || noMatches ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onVisible()
    {
      LearnedWordsActivity activity = host();
      activity._addWord.setText("");
      activity._addWord.setError(null);
      activity._scopeExplanation.setText(isTaughtMode()
          ? R.string.learned_words_taught_explanation
          : R.string.learned_words_corrections_explanation);
      activity._addWord.setHint(isTaughtMode()
          ? R.string.learned_words_add_hint
          : R.string.learned_words_replacement_source_hint);
      activity._primaryAction.setText(isTaughtMode()
          ? R.string.learned_words_add_action
          : R.string.learned_words_replacement_add_action);
      refreshRows();
    }

    private void editRow(RowItem row)
    {
      String target = row.correction == null ? null : row.correction.target;
      showReplacementEditor(row.word, target);
    }

    private void confirmDelete(final RowItem row)
    {
      final LearnedWordsActivity activity = host();
      if (row.isTaught())
      {
        new AlertDialog.Builder(activity)
          .setTitle(activity.getString(
                R.string.adaptive_unlearn_confirm_title, row.word))
          .setMessage(R.string.adaptive_unlearn_confirm_message)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(R.string.adaptive_unlearn_confirm_positive,
              (_dialog, _which) -> deleteRow(row))
          .show();
        return;
      }
      String target = row.correction.target == null
        ? activity.getString(R.string.learned_words_best_suggestion)
        : row.correction.target;
      new AlertDialog.Builder(activity)
        .setTitle(activity.getString(
              R.string.learned_words_delete_correction_title,
              row.word, target))
        .setMessage(R.string.learned_words_delete_correction_message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.learned_words_delete_correction_positive,
            (_dialog, _which) -> deleteRow(row))
        .show();
    }

    private void deleteRow(RowItem row)
    {
      LearnedWordsActivity activity = host();
      PersonalizationStore store =
        new PersonalizationStore(activity.prefs());
      boolean changed;
      if (row.isTaught())
        changed = store.unlearn_word(row.word);
      else if (row.correction.explicit)
        changed = store.remove_replacement(row.correction.source);
      else
        changed = store.remove_correction(
            row.correction.source, row.correction.target);
      if (!changed)
        return;
      PersonalizationStore.notify_external_change(activity.prefs());
      refreshRows();
      Toast.makeText(activity, row.isTaught()
          ? activity.getString(R.string.learned_words_forgot, row.word)
          : activity.getString(R.string.learned_words_correction_deleted),
          Toast.LENGTH_SHORT).show();
    }
  }

  /** The taught-words list. */
  public static final class WordsTab extends WordListTab
  {
    @Override
    public CharSequence title()
    {
      return host().getString(R.string.learned_words_taught_tab);
    }

    @Override
    public int buttonId()
    {
      return R.id.learned_words_taught_tab;
    }

    @Override
    boolean isTaughtMode()
    {
      return true;
    }
  }

  /** The correction rules list. */
  public static final class CorrectionsTab extends WordListTab
  {
    @Override
    public CharSequence title()
    {
      return host().getString(R.string.learned_words_corrections_tab);
    }

    @Override
    public int buttonId()
    {
      return R.id.learned_words_corrections_tab;
    }

    @Override
    boolean isTaughtMode()
    {
      return false;
    }

    /** Open the replacement editor prefilled with [source]. */
    void prefillReplacement(String source)
    {
      host()._addWord.setText(source);
      host()._addWord.setSelection(host()._addWord.length());
      showReplacementEditor(source, null);
    }
  }

  private static final class RowItem
  {
    final String word;
    final PersonalizationStore.CorrectionEntry correction;

    private RowItem(String word_,
        PersonalizationStore.CorrectionEntry correction_)
    {
      word = word_;
      correction = correction_;
    }

    static RowItem taught(String word)
    {
      return new RowItem(word, null);
    }

    static RowItem correction(PersonalizationStore.CorrectionEntry correction)
    {
      return new RowItem(correction.source, correction);
    }

    boolean isTaught()
    {
      return correction == null;
    }
  }
}
