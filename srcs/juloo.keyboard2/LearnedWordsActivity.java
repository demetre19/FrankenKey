package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import juloo.keyboard2.suggestions.PersonalizationStore;

/** Private settings surface for learned literals and correction rules. */
public final class LearnedWordsActivity extends Activity
{
  public static final String EXTRA_REPLACEMENT_SOURCE =
    "juloo.keyboard2.extra.REPLACEMENT_SOURCE";

  private static final int COLOR_ACCENT = 0xff74d6c9;
  private static final int COLOR_PRIMARY = 0xfff4f7fa;
  private static final int COLOR_SECONDARY = 0xffa8b2be;
  private static final int COLOR_SURFACE = 0xff20252b;

  private static final int REQUEST_EXPORT = 1;
  private static final int REQUEST_DELETE_LIST = 2;
  /* A delete list is user-authored plain text; bound provider input so a
     hostile or broken content provider cannot exhaust memory. */
  private static final int MAX_DELETE_LIST_BYTES = 1024 * 1024;
  private static final String EXPORT_FILENAME =
    "frankenkey-learned-words.txt";
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

  private final List<RowItem> _allRows = new ArrayList<RowItem>();
  private final List<RowItem> _rows = new ArrayList<RowItem>();
  private SharedPreferences _prefs;
  private EditText _addWord;
  private EditText _search;
  private TextView _scopeExplanation;
  private TextView _message;
  private ListView _list;
  private RowAdapter _adapter;
  private Button _primaryAction;
  private Button _taughtTab;
  private Button _correctionsTab;
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
    _scopeExplanation = (TextView)findViewById(
        R.id.learned_words_scope_explanation);
    _message = (TextView)findViewById(R.id.learned_words_message);
    _list = (ListView)findViewById(R.id.learned_words_list);
    _primaryAction = (Button)findViewById(R.id.learned_words_primary_action);
    _taughtTab = (Button)findViewById(R.id.learned_words_taught_tab);
    _correctionsTab = (Button)findViewById(
        R.id.learned_words_corrections_tab);
    _adapter = new RowAdapter();
    _list.setAdapter(_adapter);
    findViewById(R.id.learned_words_back).setOnClickListener(
        _view -> finish());
    _primaryAction.setOnClickListener(_view -> performPrimaryAction());
    _taughtTab.setOnClickListener(_view -> setMode(true));
    _correctionsTab.setOnClickListener(_view -> setMode(false));
    findViewById(R.id.learned_words_export).setOnClickListener(
        _view -> startExport());
    findViewById(R.id.learned_words_delete_list).setOnClickListener(
        _view -> startDeleteList());
    setupLengthFilters();
    updateModeControls();
    _addWord.setOnEditorActionListener((_view, actionId, _event) -> {
        if (actionId != EditorInfo.IME_ACTION_DONE)
          return false;
        performPrimaryAction();
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
    refreshRows();

    String replacementSource = getIntent().getStringExtra(
        EXTRA_REPLACEMENT_SOURCE);
    if (PersonalizationStore.is_learnable(replacementSource))
    {
      setMode(false);
      _addWord.setText(replacementSource);
      _addWord.setSelection(_addWord.length());
      showReplacementEditor(replacementSource, null);
    }
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    if (_adapter != null)
      refreshRows();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode,
      Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    Uri uri = data == null ? null : data.getData();
    if (resultCode != RESULT_OK || uri == null)
      return;
    if (requestCode == REQUEST_EXPORT)
      exportTo(uri);
    else if (requestCode == REQUEST_DELETE_LIST)
      deleteListed(uri);
  }

  private void startExport()
  {
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TITLE, EXPORT_FILENAME);
    startActivityForResult(intent, REQUEST_EXPORT);
  }

  private void startDeleteList()
  {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_DELETE_LIST);
  }

  private void exportTo(Uri uri)
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    List<String> taught = store.taught_words();
    List<String> adaptive = store.learned_words();
    StringBuilder text = new StringBuilder();
    text.append("# FrankenKey learned words\n");
    for (String word : taught)
      text.append(word).append('\n');
    if (!adaptive.isEmpty())
    {
      text.append("\n# Adaptive (correction-backed)\n");
      for (String word : adaptive)
        text.append(word).append('\n');
    }
    List<PersonalizationStore.CorrectionEntry> corrections =
      store.correction_entries();
    if (!corrections.isEmpty())
    {
      text.append("\n# Corrections (source -> target)\n");
      for (PersonalizationStore.CorrectionEntry correction : corrections)
        text.append(correction.source).append(" -> ")
          .append(correction.target == null ? "" : correction.target)
          .append('\n');
    }
    try (OutputStream stream = getContentResolver().openOutputStream(uri);
         Writer out = new OutputStreamWriter(stream,
             StandardCharsets.UTF_8))
    {
      out.write(text.toString());
    }
    catch (IOException | NullPointerException error)
    {
      Toast.makeText(this, R.string.learned_words_export_failed,
          Toast.LENGTH_LONG).show();
      return;
    }
    Toast.makeText(this, getString(R.string.learned_words_export_done,
          taught.size() + adaptive.size()), Toast.LENGTH_SHORT).show();
  }

  private void deleteListed(Uri uri)
  {
    Set<String> listed = new LinkedHashSet<String>();
    try (InputStream stream = getContentResolver().openInputStream(uri))
    {
      if (stream == null)
        throw new IOException("missing content stream");
      byte[] bytes = new byte[MAX_DELETE_LIST_BYTES];
      int length = 0;
      while (length < bytes.length)
      {
        int read = stream.read(bytes, length, bytes.length - length);
        if (read < 0)
          break;
        length += read;
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(
          new java.io.ByteArrayInputStream(bytes, 0, length),
          StandardCharsets.UTF_8));
      for (String line = reader.readLine(); line != null;
          line = reader.readLine())
      {
        String word = line.trim();
        while (word.length() > 0 && word.charAt(0) == '\uFEFF')
          word = word.substring(1).trim();
        if (word.length() == 0 || word.startsWith("#"))
          continue;
        int arrow = word.indexOf("->");
        if (arrow > 0)
          word = word.substring(0, arrow).trim();
        listed.add(word);
      }
    }
    catch (IOException | NullPointerException error)
    {
      Toast.makeText(this, R.string.learned_words_delete_list_failed,
          Toast.LENGTH_LONG).show();
      return;
    }
    PersonalizationStore store = new PersonalizationStore(_prefs);
    final List<String> matched = new ArrayList<String>();
    for (String word : listed)
      if (store.is_taught(word) || store.is_learned(word))
        matched.add(word);
    if (matched.isEmpty())
    {
      Toast.makeText(this, R.string.learned_words_delete_list_none,
          Toast.LENGTH_SHORT).show();
      return;
    }
    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.learned_words_delete_list_title,
            matched.size()))
      .setMessage(R.string.learned_words_delete_list_message)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.learned_words_delete_list_positive,
          (_dialog, _which) -> applyDeleteList(matched))
      .show();
  }

  private void applyDeleteList(List<String> matched)
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    int deleted = 0;
    for (String word : matched)
      if (store.unlearn_word(word))
        deleted++;
    if (deleted == 0)
      return;
    PersonalizationStore.notify_external_change(_prefs);
    refreshRows();
    Toast.makeText(this, getString(R.string.learned_words_delete_list_done,
          deleted), Toast.LENGTH_SHORT).show();
  }

  private void performPrimaryAction()
  {
    if (_showTaught)
      teachWord();
    else
      beginReplacement();
  }

  private void teachWord()
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
    setMode(true);
    refreshRows();
    Toast.makeText(this, getString(R.string.learned_words_learned, word),
        Toast.LENGTH_SHORT).show();
  }

  private void beginReplacement()
  {
    String source = _addWord.getText().toString().trim();
    if (!PersonalizationStore.is_learnable(source))
    {
      _addWord.setError(getString(R.string.learned_words_invalid));
      return;
    }
    PersonalizationStore.ReplacementRule existing =
      new PersonalizationStore(_prefs).replacement_rule(source);
    showReplacementEditor(source, existing == null ? null : existing.target);
  }

  private void showReplacementEditor(final String source,
      String currentTarget)
  {
    final EditText target = new EditText(this);
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
    target.setPadding(dp(12), 0, dp(12), 0);
    target.setMinHeight(dp(48));
    if (currentTarget != null)
    {
      target.setText(currentTarget);
      target.setSelection(target.length());
    }
    LinearLayout targetContainer = new LinearLayout(this);
    targetContainer.setPadding(dp(20), dp(8), dp(20), 0);
    targetContainer.addView(target, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

    final AlertDialog dialog = new AlertDialog.Builder(this)
      .setTitle(getString(R.string.learned_words_replacement_title, source))
      .setMessage(R.string.learned_words_replacement_message)
      .setView(targetContainer)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.learned_words_replacement_save, null)
      .create();
    dialog.setOnShowListener(_dialog -> {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
          .setOnClickListener(_view -> saveReplacement(dialog, source, target));
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
    String target = targetField.getText().toString().trim();
    if (target.length() != 0 && (!PersonalizationStore.is_learnable(target)
          || source.trim().equalsIgnoreCase(target)))
    {
      targetField.setError(getString(
            R.string.learned_words_replacement_invalid));
      return;
    }
    PersonalizationStore store = new PersonalizationStore(_prefs);
    PersonalizationStore.ReplacementRule previous =
      store.replacement_rule(source);
    boolean alreadySaved = previous != null
      && (previous.target == null ? target.length() == 0
        : previous.target.equalsIgnoreCase(target));
    if (!alreadySaved && !store.set_replacement(source,
          target.length() == 0 ? null : target))
    {
      targetField.setError(getString(
            R.string.learned_words_replacement_not_saved));
      return;
    }
    PersonalizationStore.notify_external_change(_prefs);
    _addWord.setText("");
    setMode(false);
    refreshRows();
    String destination = target.length() == 0
      ? getString(R.string.learned_words_best_suggestion)
      : target;
    Toast.makeText(this, getString(
          R.string.learned_words_replacement_saved, source, destination),
        Toast.LENGTH_SHORT).show();
    dialog.dismiss();
  }

  private void refreshRows()
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
    _allRows.clear();
    if (_showTaught)
      for (String word : store.taught_words())
        _allRows.add(RowItem.taught(word));
    else
      for (PersonalizationStore.CorrectionEntry correction
          : store.correction_entries())
        _allRows.add(RowItem.correction(correction));
    filter(_search == null ? "" : _search.getText().toString());
  }

  private void filter(String query)
  {
    String normalized = query == null ? ""
      : query.trim().toLowerCase(Locale.ROOT);
    _rows.clear();
    for (RowItem row : _allRows)
    {
      int length = row.word.codePointCount(0, row.word.length());
      boolean lengthMatches = _lengthFilter == 0
        || (_lengthFilter == 10 ? length >= 10 : length == _lengthFilter);
      String target = row.correction == null || row.correction.target == null
        ? "" : row.correction.target.toLowerCase(Locale.ROOT);
      if (lengthMatches && (normalized.isEmpty()
            || row.word.toLowerCase(Locale.ROOT).contains(normalized)
            || target.contains(normalized)))
        _rows.add(row);
    }
    _adapter.notifyDataSetChanged();
    boolean noRows = _allRows.isEmpty();
    boolean noMatches = !noRows && _rows.isEmpty();
    _message.setText(noRows
        ? (_showTaught ? R.string.learned_words_taught_empty
          : R.string.learned_words_corrections_empty)
        : R.string.learned_words_no_matches);
    _message.setVisibility(noRows || noMatches ? View.VISIBLE : View.GONE);
    _list.setVisibility(noRows || noMatches ? View.GONE : View.VISIBLE);
  }

  private void setMode(boolean showTaught)
  {
    if (_showTaught == showTaught)
    {
      updateModeControls();
      return;
    }
    _showTaught = showTaught;
    _addWord.setText("");
    _addWord.setError(null);
    updateModeControls();
    refreshRows();
  }

  private void updateModeControls()
  {
    styleFilterButton(_taughtTab, _showTaught);
    styleFilterButton(_correctionsTab, !_showTaught);
    _scopeExplanation.setText(_showTaught
        ? R.string.learned_words_taught_explanation
        : R.string.learned_words_corrections_explanation);
    _addWord.setHint(_showTaught
        ? R.string.learned_words_add_hint
        : R.string.learned_words_replacement_source_hint);
    _primaryAction.setText(_showTaught
        ? R.string.learned_words_add_action
        : R.string.learned_words_replacement_add_action);
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

  private void editRow(RowItem row)
  {
    String target = row.correction == null ? null : row.correction.target;
    showReplacementEditor(row.word, target);
  }

  private void confirmDelete(final RowItem row)
  {
    if (row.isTaught())
    {
      new AlertDialog.Builder(this)
        .setTitle(getString(R.string.adaptive_unlearn_confirm_title, row.word))
        .setMessage(R.string.adaptive_unlearn_confirm_message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.adaptive_unlearn_confirm_positive,
            (_dialog, _which) -> deleteRow(row))
        .show();
      return;
    }
    String target = row.correction.target == null
      ? getString(R.string.learned_words_best_suggestion)
      : row.correction.target;
    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.learned_words_delete_correction_title,
            row.word, target))
      .setMessage(R.string.learned_words_delete_correction_message)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.learned_words_delete_correction_positive,
          (_dialog, _which) -> deleteRow(row))
      .show();
  }

  private void deleteRow(RowItem row)
  {
    PersonalizationStore store = new PersonalizationStore(_prefs);
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
    PersonalizationStore.notify_external_change(_prefs);
    refreshRows();
    Toast.makeText(this, row.isTaught()
        ? getString(R.string.learned_words_forgot, row.word)
        : getString(R.string.learned_words_correction_deleted),
        Toast.LENGTH_SHORT).show();
  }

  private final class RowAdapter extends BaseAdapter
  {
    @Override public int getCount() { return _rows.size(); }
    @Override public RowItem getItem(int position) { return _rows.get(position); }
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
      edit.setOnClickListener(_view -> editRow(row));
      delete.setContentDescription(row.isTaught()
          ? getString(R.string.learned_words_forget_accessibility, row.word)
          : getString(R.string.learned_words_delete_correction_accessibility,
            row.word));
      delete.setOnClickListener(_view -> confirmDelete(row));
      return rowView;
    }
  }

}
