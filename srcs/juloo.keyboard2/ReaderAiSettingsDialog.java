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

/** OpenRouter key, live model catalog, and editable article prompts. */
final class ReaderAiSettingsDialog
{
  private final Activity activity;
  private final ReaderAiUi ui;
  private final ReaderAiSettings settings;
  private final ReaderAiOpenRouter client = new ReaderAiOpenRouter();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Runnable onSaved;
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
    new ReaderAiSettingsDialog(activity, onSaved).show();
  }

  private ReaderAiSettingsDialog(Activity activity, Runnable onSaved)
  {
    this.activity = activity;
    this.ui = new ReaderAiUi(activity);
    this.settings = new ReaderAiSettings(activity);
    this.onSaved = onSaved;
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

    Button savedResults = ui.button("Open saved Reader AI results");
    savedResults.setContentDescription("Open saved Reader AI results");
    savedResults.setOnClickListener(ignored -> activity.startActivity(
          new Intent(activity, ReaderAiLibraryActivity.class)));
    LinearLayout.LayoutParams savedResultsParams = matchWrap();
    savedResultsParams.bottomMargin = ui.dp(8);
    content.addView(savedResults, savedResultsParams);

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
    Button refresh = ui.button("Refresh models");
    refresh.setOnClickListener(ignored -> refreshModels(false));
    ui.addWeighted(modelRow, model, 1.5f, 0);
    ui.addWeighted(modelRow, refresh, 1f, ui.dp(8));
    content.addView(modelRow, matchWrap());

    content.addView(label("Summary One prompt"));
    summaryOne = promptInput(settings.getSummaryOnePrompt());
    content.addView(summaryOne, promptParams());
    content.addView(label("Summary Two prompt"));
    summaryTwo = promptInput(settings.getSummaryTwoPrompt());
    content.addView(summaryTwo, promptParams());
    content.addView(label("Article Quiz prompt"));
    quiz = promptInput(settings.getQuizPrompt());
    content.addView(quiz, promptParams());

    Button restore = ui.button("Restore default article prompts");
    restore.setOnClickListener(ignored -> {
      summaryOne.setText(ReaderAiRequest.SUMMARY_ONE_PROMPT);
      summaryTwo.setText(ReaderAiRequest.SUMMARY_TWO_PROMPT);
      quiz.setText(ReaderAiRequest.QUIZ_PROMPT);
    });
    LinearLayout.LayoutParams restoreParams = matchWrap();
    restoreParams.topMargin = ui.dp(10);
    content.addView(restore, restoreParams);

    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content);
    AlertDialog dialog = new AlertDialog.Builder(activity)
      .setView(scroll)
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Save", null)
      .create();
    dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(view -> save(dialog)));
    dialog.setOnDismissListener(ignored -> {
      client.cancel();
      executor.shutdownNow();
    });
    dialog.show();
  }

  private void save(AlertDialog dialog)
  {
    try
    {
      settings.setApiKey(key.getText().toString());
      settings.setModelId(selectedModelId);
      settings.setPrompts(summaryOne.getText().toString(),
          summaryTwo.getText().toString(), quiz.getText().toString());
      if (onSaved != null)
        onSaved.run();
      dialog.dismiss();
      Toast.makeText(activity, "Reader AI settings saved", Toast.LENGTH_SHORT)
        .show();
    }
    catch (GeneralSecurityException | IllegalArgumentException error)
    {
      status.setText(error.getMessage());
    }
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
              selectedModelId = preferred.id;
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
    CheckBox longContext = check("Long context");
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
        model.setText(modelLabel());
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
