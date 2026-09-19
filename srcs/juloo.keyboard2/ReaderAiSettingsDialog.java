package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** OpenRouter key, live model catalog, and editable Reader AI prompts. */
final class ReaderAiSettingsDialog
{
  private final Activity activity;
  private final ReaderAiUi ui;
  private final ReaderAiSettings settings;
  private final ReaderAiOpenRouter client = new ReaderAiOpenRouter();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Runnable onSaved;
  private final Runnable onDismiss;
  private final android.os.Handler autosaveHandler =
    new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable autosavePrompts = this::savePrompts;
  private final Runnable autosaveKey = this::saveKey;
  private EditText key;
  private Button model;
  private EditText summaryOne;
  private EditText summaryTwo;
  private EditText quiz;
  private TextView status;
  private String selectedModelId;
  private List<ReaderAiOpenRouter.Model> models = new ArrayList<>();

  static void show(Activity activity, Runnable onSaved)
  {
    new ReaderAiSettingsDialog(activity, onSaved, null).show();
  }

  static void show(Activity activity, Runnable onSaved, Runnable onDismiss)
  {
    new ReaderAiSettingsDialog(activity, onSaved, onDismiss).show();
  }

  private ReaderAiSettingsDialog(Activity activity, Runnable onSaved,
      Runnable onDismiss)
  {
    this.activity = activity;
    this.ui = new ReaderAiUi(activity);
    this.settings = new ReaderAiSettings(activity);
    this.onSaved = onSaved;
    this.onDismiss = onDismiss;
    selectedModelId = settings.getModelId();
  }

  private void show()
  {
    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(16));

    TextView title = ui.text("Reader AI settings", 20, ui.text);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    content.addView(title);
    status = ui.text("Reader text is sent only when you request AI.", 12,
        ui.muted);
    status.setPadding(0, ui.dp(4), 0, ui.dp(12));
    content.addView(status);

    LinearLayout savedRow = ui.row();
    TextView savedLabel = ui.text("Saved Reader AI results", 14, ui.text);
    savedRow.addView(savedLabel, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    android.widget.ImageButton savedResults = ui.iconButton(
        R.drawable.snippet_icon_bookmark, "Open saved Reader AI results");
    savedResults.setOnClickListener(ignored -> activity.startActivity(
          new Intent(activity, ReaderAiLibraryActivity.class)));
    savedRow.addView(savedResults, new LinearLayout.LayoutParams(
          ui.dp(42), ui.dp(42)));
    LinearLayout.LayoutParams savedResultsParams = matchWrap();
    savedResultsParams.bottomMargin = ui.dp(8);
    content.addView(savedRow, savedResultsParams);

    content.addView(label("OpenRouter API key"));
    key = input("sk-or-...", false, 1000);
    key.setTransformationMethod(PasswordTransformationMethod.getInstance());
    try
    {
      key.setText(settings.getApiKey());
    }
    catch (GeneralSecurityException error)
    {
      status.setText(error.getMessage());
    }
    key.addTextChangedListener(new SimpleTextWatcher(
          () -> schedule(autosaveKey)));
    content.addView(key, matchWrap());

    CheckBox reveal = new CheckBox(activity);
    reveal.setText("Show API key");
    reveal.setTextColor(ui.text);
    reveal.setMinHeight(ui.dp(48));
    reveal.setOnCheckedChangeListener((button, checked) -> {
      int selection = key.getSelectionStart();
      key.setTransformationMethod(checked ? null
          : PasswordTransformationMethod.getInstance());
      key.setSelection(Math.max(0, Math.min(selection, key.length())));
    });
    content.addView(reveal);

    content.addView(label("OpenRouter model"));
    LinearLayout modelRow = ui.row();
    model = ui.button(modelLabel());
    model.setOnClickListener(ignored -> {
      if (models.isEmpty())
        refreshModels(true);
      else
        showModelPicker();
    });
    android.widget.ImageButton refresh = ui.iconButton(
        R.drawable.ic_reader_ai_refresh, "Refresh OpenRouter models");
    refresh.setOnClickListener(ignored -> refreshModels(false));
    ui.addWeighted(modelRow, model, 1f, 0);
    LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
        ui.dp(42), ui.dp(42));
    refreshParams.setMarginStart(ui.dp(8));
    modelRow.addView(refresh, refreshParams);
    content.addView(modelRow, matchWrap());

    content.addView(label("Summary One prompt"));
    summaryOne = promptInput(settings.getSummaryOnePrompt());
    summaryOne.addTextChangedListener(new SimpleTextWatcher(
          () -> schedule(autosavePrompts)));
    content.addView(summaryOne, promptParams());
    content.addView(label("Summary Two prompt"));
    summaryTwo = promptInput(settings.getSummaryTwoPrompt());
    summaryTwo.addTextChangedListener(new SimpleTextWatcher(
          () -> schedule(autosavePrompts)));
    content.addView(summaryTwo, promptParams());
    content.addView(label("Quiz prompt"));
    quiz = promptInput(settings.getQuizPrompt());
    quiz.addTextChangedListener(new SimpleTextWatcher(
          () -> schedule(autosavePrompts)));
    content.addView(quiz, promptParams());

    LinearLayout restoreRow = ui.row();
    TextView restoreLabel = ui.text("Restore default prompts", 14, ui.text);
    restoreRow.addView(restoreLabel, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    android.widget.ImageButton restore = ui.iconButton(
        R.drawable.ic_reader_ai_restore, "Restore default Reader AI prompts");
    restore.setOnClickListener(ignored -> {
      summaryOne.setText(ReaderAiRequest.SUMMARY_ONE_PROMPT);
      summaryTwo.setText(ReaderAiRequest.SUMMARY_TWO_PROMPT);
      quiz.setText(ReaderAiRequest.QUIZ_PROMPT);
    });
    restoreRow.addView(restore, new LinearLayout.LayoutParams(
          ui.dp(42), ui.dp(42)));
    LinearLayout.LayoutParams restoreParams = matchWrap();
    restoreParams.topMargin = ui.dp(10);
    content.addView(restoreRow, restoreParams);

    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content);
    AlertDialog dialog = new AlertDialog.Builder(activity)
      .setView(scroll)
      .setPositiveButton("Close", null)
      .create();
    dialog.setOnDismissListener(ignored -> {
      flushAutosave();
      client.cancel();
      executor.shutdownNow();
      if (onDismiss != null)
        onDismiss.run();
    });
    dialog.show();
  }

  private void schedule(Runnable work)
  {
    autosaveHandler.removeCallbacks(work);
    autosaveHandler.postDelayed(work, 600);
  }

  private void flushAutosave()
  {
    autosaveHandler.removeCallbacks(autosaveKey);
    autosaveHandler.removeCallbacks(autosavePrompts);
    saveKey();
    savePrompts();
  }

  private void saveKey()
  {
    try
    {
      settings.setApiKey(key.getText().toString());
      notifySaved();
    }
    catch (GeneralSecurityException | IllegalArgumentException error)
    {
      status.setText(error.getMessage());
    }
  }

  private void savePrompts()
  {
    try
    {
      settings.setPrompts(summaryOne.getText().toString(),
          summaryTwo.getText().toString(), quiz.getText().toString());
      notifySaved();
    }
    catch (IllegalArgumentException error)
    {
      status.setText(error.getMessage());
    }
  }

  private void notifySaved()
  {
    if (onSaved != null)
      onSaved.run();
  }


  private void refreshModels(boolean showPickerAfter)
  {
    final String apiKey = key.getText().toString().trim();
    status.setText("Loading OpenRouter models…");
    model.setEnabled(false);
    executor.execute(() -> {
      try
      {
        List<ReaderAiOpenRouter.Model> loaded = client.fetchModels(apiKey);
        activity.runOnUiThread(() -> {
          models = loaded;
          model.setEnabled(true);
          ReaderAiOpenRouter.Model selected = findModel(selectedModelId);
          if (selected == null)
          {
            ReaderAiOpenRouter.Model preferred = findModel(
                ReaderAiOpenRouter.PREFERRED_MODEL_ID);
            if (preferred != null)
            {
              selectedModelId = preferred.id;
              settings.setModelId(selectedModelId);
              notifySaved();
            }
          }
          model.setText(modelLabel());
          status.setText(loaded.isEmpty() ? "No text models returned"
              : loaded.size() + " text models loaded");
          if (showPickerAfter && !loaded.isEmpty())
            showModelPicker();
        });
      }
      catch (Exception error)
      {
        activity.runOnUiThread(() -> {
          model.setEnabled(true);
          status.setText(error.getMessage());
        });
      }
    });
  }

  private void showModelPicker()
  {
    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
    EditText search = input("Search models", true, 300);
    content.addView(search, matchWrap());
    LinearLayout filters = ui.row();
    CheckBox free = check("Free only");
    CheckBox longContext = check("100k+ context");
    ui.addWeighted(filters, free, 1f, 0);
    ui.addWeighted(filters, longContext, 1f, ui.dp(8));
    content.addView(filters);
    ListView list = new ListView(activity);
    content.addView(list, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(420)));

    final List<ReaderAiOpenRouter.Model> visible = new ArrayList<>();
    final android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
        activity, android.R.layout.simple_list_item_1, new ArrayList<>());
    list.setAdapter(adapter);
    Runnable apply = () -> {
      String query = search.getText().toString().trim().toLowerCase(Locale.US);
      visible.clear();
      adapter.clear();
      for (ReaderAiOpenRouter.Model candidate : models)
      {
        if (free.isChecked() && !candidate.isFree())
          continue;
        if (longContext.isChecked() && !candidate.hasLongContext())
          continue;
        if (!query.isEmpty() && !candidate.searchText().contains(query))
          continue;
        visible.add(candidate);
        adapter.add(candidate.name + "\n" + candidate.id + "\n"
            + candidate.guidance());
      }
      adapter.notifyDataSetChanged();
    };
    search.addTextChangedListener(new SimpleTextWatcher(apply));
    free.setOnCheckedChangeListener((button, checked) -> apply.run());
    longContext.setOnCheckedChangeListener((button, checked) -> apply.run());
    apply.run();

    AlertDialog picker = new AlertDialog.Builder(activity)
      .setTitle("Choose OpenRouter model")
      .setView(content)
      .setNegativeButton("Close", null)
      .create();
    list.setOnItemClickListener((parent, view, position, id) -> {
      if (position >= 0 && position < visible.size())
      {
        selectedModelId = visible.get(position).id;
        settings.setModelId(selectedModelId);
        model.setText(modelLabel());
        status.setText("Model saved: " + selectedModelId);
        notifySaved();
        picker.dismiss();
      }
    });
    picker.show();
  }

  private ReaderAiOpenRouter.Model findModel(String id)
  {
    for (ReaderAiOpenRouter.Model candidate : models)
      if (candidate.id.equals(id))
        return candidate;
    return null;
  }

  private String modelLabel()
  {
    ReaderAiOpenRouter.Model selected = findModel(selectedModelId);
    return selected == null ? (selectedModelId.isEmpty() ? "Choose model"
        : selectedModelId) : selected.name + "\n" + selected.guidance();
  }

  private TextView label(String text)
  {
    TextView label = ui.text(text, 14, ui.text);
    label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    label.setPadding(0, ui.dp(12), 0, ui.dp(6));
    return label;
  }

  private EditText input(String hint, boolean singleLine, int maxLength)
  {
    EditText input = new EditText(activity);
    input.setHint(hint);
    input.setTextColor(ui.text);
    input.setHintTextColor(ui.muted);
    input.setTextSize(14);
    input.setSingleLine(singleLine);
    input.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
    input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
    input.setBackground(ui.panel(ui.surface, ui.border, 8));
    return input;
  }

  private EditText promptInput(String value)
  {
    EditText input = input("Prompt", false, 20_000);
    input.setText(value);
    input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
    input.setInputType(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    return input;
  }

  private CheckBox check(String label)
  {
    CheckBox check = new CheckBox(activity);
    check.setText(label);
    check.setTextColor(ui.text);
    check.setMinHeight(ui.dp(48));
    return check;
  }

  private LinearLayout.LayoutParams matchWrap()
  {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private LinearLayout.LayoutParams promptParams()
  {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ui.dp(150));
  }

  private static final class SimpleTextWatcher implements TextWatcher
  {
    private final Runnable changed;
    SimpleTextWatcher(Runnable changed) { this.changed = changed; }
    @Override public void beforeTextChanged(CharSequence s, int start, int count,
        int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before,
        int count) { changed.run(); }
    @Override public void afterTextChanged(Editable s) {}
  }
}
