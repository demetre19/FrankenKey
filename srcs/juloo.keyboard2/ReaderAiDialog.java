package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared native SpeedyWatch-style Reader AI surface for 2D and 3D Reader. */
final class ReaderAiDialog
{
  private static final float MIN_TEXT_SP = 15f;
  private static final float MAX_TEXT_SP = 30f;

  private final Activity activity;
  private final ReaderAiService.Article article;
  private final ReaderAiUi ui;
  private final ReaderAiSettings settings;
  private final ReaderAiOpenRouter client = new ReaderAiOpenRouter();
  private final ReaderAiCache cache;
  private final ReaderAiStore store;
  private final ReaderAiService service;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final List<ReaderAiService.ChatTurn> turns = new ArrayList<>();

  private Dialog dialog;
  private TextView status;
  private TextView output;
  private LinearLayout conversation;
  private ScrollView outputScroll;
  private LinearLayout chatRow;
  private EditText chatInput;
  private Button summaryOne;
  private Button summaryTwo;
  private Button directChat;
  private Button quiz;
  private Button copy;
  private Button save;
  private Button read;
  private Button share;
  private Button send;
  private float textSizeSp = MIN_TEXT_SP;
  private boolean busy;
  private ReaderAiOpenRouter.Model selectedModel;
  private ReaderAiStore.Type currentType;
  private String currentPrompt = "";
  private String currentMarkdown = "";
  private String currentCacheKey = "";

  static void show(Activity activity, ReaderAiService.Article article)
  {
    new ReaderAiDialog(activity, article).show();
  }

  private ReaderAiDialog(Activity activity, ReaderAiService.Article article)
  {
    this.activity = activity;
    this.article = article;
    ui = new ReaderAiUi(activity);
    settings = new ReaderAiSettings(activity);
    cache = new ReaderAiCache(activity);
    store = new ReaderAiStore(activity);
    service = new ReaderAiService(client, cache);
  }

  private void show()
  {
    dialog = new Dialog(activity);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    Window window = dialog.getWindow();
    if (window != null)
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

    int horizontalPadding = ui.dp(14);
    int topPadding = ui.dp(12);
    int bottomPadding = ui.dp(12);
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(horizontalPadding, topPadding, horizontalPadding,
        bottomPadding);
    root.setBackground(ui.panel(backgroundColor(), ui.border, 12));
    root.setOnApplyWindowInsetsListener((view, insets) -> {
      view.setPadding(horizontalPadding, topPadding, horizontalPadding,
          bottomPadding + insets.getSystemWindowInsetBottom());
      return insets;
    });

    LinearLayout header = ui.row();
    status = ui.text("Choose an AI action", 12, ui.muted);
    status.setSingleLine(true);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    header.addView(status, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    ImageButton savedItems = new ImageButton(activity);
    savedItems.setImageResource(R.drawable.snippet_icon_bookmark);
    savedItems.setColorFilter(ui.text);
    savedItems.setContentDescription("Saved Reader AI results");
    savedItems.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
    savedItems.setBackground(ui.panel(ui.surface, ui.border, 8));
    savedItems.setOnClickListener(ignored -> activity.startActivity(
          new Intent(activity, ReaderAiLibraryActivity.class)));
    LinearLayout.LayoutParams savedParams = new LinearLayout.LayoutParams(
        ui.dp(42), ui.dp(42));
    savedParams.setMarginStart(ui.dp(8));
    header.addView(savedItems, savedParams);
    Button settingsButton = ui.button("Settings");
    settingsButton.setContentDescription("Open Reader AI settings");
    settingsButton.setOnClickListener(ignored -> ReaderAiSettingsDialog.show(
          activity, this::settingsChanged));
    LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(42));
    settingsParams.setMarginStart(ui.dp(8));
    header.addView(settingsButton, settingsParams);
    ImageButton close = new ImageButton(activity);
    close.setImageResource(R.drawable.ic_reader_ai_close);
    close.setContentDescription("Close Reader AI");
    close.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
    close.setBackground(ui.panel(ui.surface, ui.border, 8));
    close.setOnClickListener(ignored -> dialog.dismiss());
    LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
        ui.dp(42), ui.dp(42));
    closeParams.setMarginStart(ui.dp(8));
    header.addView(close, closeParams);
    root.addView(header);

    LinearLayout modes = ui.row();
    summaryOne = modeButton("Summary One", () -> summary(true));
    summaryTwo = modeButton("Summary Two", () -> summary(false));
    directChat = modeButton("Chat", this::startDirectChat);
    quiz = modeButton("Quiz", this::chooseQuiz);
    quiz.setContentDescription("Article Quiz");
    ui.addWeighted(modes, summaryOne, 1f, 0);
    ui.addWeighted(modes, summaryTwo, 1f, ui.dp(8));
    ui.addWeighted(modes, directChat, 1f, ui.dp(8));
    ui.addWeighted(modes, quiz, 1f, ui.dp(8));
    LinearLayout.LayoutParams modeParams = matchWrap();
    modeParams.topMargin = ui.dp(8);
    root.addView(modes, modeParams);

    conversation = new LinearLayout(activity);
    conversation.setOrientation(LinearLayout.VERTICAL);
    output = messageText();
    conversation.addView(output, matchWrap());
    outputScroll = new ScrollView(activity);
    outputScroll.setFillViewport(true);
    outputScroll.setBackground(ui.panel(ui.surface, ui.border, 8));
    outputScroll.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
    outputScroll.addView(conversation);
    ScaleGestureDetector scale = new ScaleGestureDetector(activity,
        new ScaleGestureDetector.SimpleOnScaleGestureListener()
        {
          @Override public boolean onScale(ScaleGestureDetector detector)
          {
            textSizeSp = Math.max(MIN_TEXT_SP, Math.min(MAX_TEXT_SP,
                  textSizeSp * detector.getScaleFactor()));
            applyTextSize();
            return true;
          }
        });
    outputScroll.setOnTouchListener((view, event) -> {
      scale.onTouchEvent(event);
      return scale.isInProgress();
    });
    LinearLayout.LayoutParams outputParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    outputParams.topMargin = ui.dp(8);
    root.addView(outputScroll, outputParams);

    chatRow = ui.row();
    chatInput = new EditText(activity);
    chatInput.setHint("Ask about this article…");
    chatInput.setTextColor(ui.text);
    chatInput.setHintTextColor(ui.muted);
    chatInput.setTextSize(14);
    chatInput.setSingleLine(true);
    chatInput.setInputType(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    chatInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
    chatInput.setPadding(ui.dp(10), 0, ui.dp(10), 0);
    chatInput.setBackground(ui.panel(ui.surface, ui.border, 8));
    send = ui.button("Send");
    send.setContentDescription("Send Reader AI question");
    send.setOnClickListener(ignored -> ask());
    chatRow.addView(chatInput, new LinearLayout.LayoutParams(0, ui.dp(44), 1f));
    LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
        ui.dp(78), ui.dp(44));
    sendParams.setMarginStart(ui.dp(8));
    chatRow.addView(send, sendParams);
    chatRow.setVisibility(View.GONE);
    LinearLayout.LayoutParams chatParams = matchWrap();
    chatParams.topMargin = ui.dp(8);
    root.addView(chatRow, chatParams);

    LinearLayout actions = ui.row();
    copy = actionButton("Copy", this::copyCurrent);
    save = actionButton("Save", this::chooseSave);
    read = actionButton("Read", this::readCurrent);
    read.setContentDescription("Speed-read summary in the plain-text Reader");
    share = actionButton("Share", this::chooseShare);
    ui.addWeighted(actions, copy, 1f, 0);
    ui.addWeighted(actions, save, 1f, ui.dp(8));
    ui.addWeighted(actions, read, 1f, ui.dp(8));
    ui.addWeighted(actions, share, 1f, ui.dp(8));
    LinearLayout.LayoutParams actionParams = matchWrap();
    actionParams.topMargin = ui.dp(8);
    root.addView(actions, actionParams);
    setActionsVisible(false);

    dialog.setContentView(root);
    dialog.setOnDismissListener(ignored -> {
      service.cancel();
      executor.shutdownNow();
      cache.close();
      store.close();
    });
    dialog.show();
    if (window != null)
    {
      window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels
            - ui.dp(16), ui.dp(760)), ViewGroup.LayoutParams.MATCH_PARENT);
      window.setGravity(Gravity.CENTER);
    }
    root.requestApplyInsets();
  }

  private void summary(boolean first)
  {
    runAfterDisclosure(() -> withModel(model -> {
      String prompt = first ? settings.getSummaryOnePrompt()
        : settings.getSummaryTwoPrompt();
      String label = first ? "Summary One" : "Summary Two";
      if (service.needsMultipleCalls(article, prompt, model))
      {
        new AlertDialog.Builder(activity)
          .setTitle("Long article")
          .setMessage("This article needs multiple billable OpenRouter calls to summarize and combine. Continue?")
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Continue", (dialog, which) ->
              executeSummary(model, label, prompt, first))
          .show();
      }
      else
        executeSummary(model, label, prompt, first);
    }));
  }

  private void executeSummary(ReaderAiOpenRouter.Model model, String label,
      String prompt, boolean first)
  {
    begin(label + " | " + model.id);
    executor.execute(() -> {
      try
      {
        String apiKey = settings.getApiKey();
        ReaderAiService.Result result = service.summary(apiKey, model, article,
            label, prompt);
        post(() -> {
          currentType = first ? ReaderAiStore.Type.SUMMARY_ONE
            : ReaderAiStore.Type.SUMMARY_TWO;
          currentPrompt = prompt;
          currentMarkdown = result.markdown;
          currentCacheKey = result.cacheKey;
          turns.clear();
          renderConversation();
          chatRow.setVisibility(View.VISIBLE);
          setActionsVisible(true);
          finish(label + " | " + model.id
              + (result.cached ? " | cached" : result.requestCount > 1
                ? " | " + result.requestCount + " calls" : ""));
          selectMode(first ? summaryOne : summaryTwo);
        });
      }
      catch (Exception error)
      {
        fail(error);
      }
    });
  }

  private void startDirectChat()
  {
    runAfterDisclosure(() -> withModel(model -> {
      currentType = ReaderAiStore.Type.ARTICLE_CHAT;
      currentPrompt = ReaderAiRequest.DIRECT_CHAT_PROMPT;
      currentMarkdown = "";
      currentCacheKey = "";
      turns.clear();
      selectedModel = model;
      renderConversation();
      output.setText("Ask a question to start a grounded article chat.");
      chatRow.setVisibility(View.VISIBLE);
      setActionsVisible(false);
      status.setText("Article Chat | " + model.id);
      selectMode(directChat);
      chatInput.requestFocus();
    }));
  }

  private void chooseQuiz()
  {
    runAfterDisclosure(() -> withModel(model -> {
      String[] choices = {"6 questions", "10 questions", "12 questions",
          "20 questions"};
      int[] counts = {6, 10, 12, 20};
      new AlertDialog.Builder(activity).setTitle("Article Quiz")
        .setItems(choices, (dialog, which) -> executeQuiz(model, counts[which]))
        .show();
    }));
  }

  private void executeQuiz(ReaderAiOpenRouter.Model model, int count)
  {
    begin("Article Quiz | " + model.id);
    executor.execute(() -> {
      try
      {
        String result = service.quiz(settings.getApiKey(), model.id, article,
            settings.getQuizPrompt(), count);
        post(() -> {
          currentType = ReaderAiStore.Type.ARTICLE_QUIZ;
          currentPrompt = settings.getQuizPrompt();
          currentMarkdown = result;
          currentCacheKey = "";
          turns.clear();
          renderConversation();
          chatRow.setVisibility(View.GONE);
          setActionsVisible(true);
          selectMode(quiz);
          finish("Article Quiz | " + count + " questions | " + model.id);
        });
      }
      catch (Exception error)
      {
        fail(error);
      }
    });
  }

  private void ask()
  {
    if (busy || currentType == null)
      return;
    String question = chatInput.getText().toString().trim();
    if (question.isEmpty())
      return;
    begin((currentType == ReaderAiStore.Type.ARTICLE_CHAT ? "Article Chat"
          : currentType.label + " chat") + " | " + modelId());
    executor.execute(() -> {
      try
      {
        String answer = currentType == ReaderAiStore.Type.ARTICLE_CHAT
          ? service.directChat(settings.getApiKey(), modelId(), article, turns,
              question)
          : service.followUp(settings.getApiKey(), modelId(), article,
              currentPrompt, currentMarkdown, turns, question);
        post(() -> {
          turns.add(new ReaderAiService.ChatTurn(question, answer));
          chatInput.setText("");
          renderConversation();
          setActionsVisible(true);
          finish(currentType.label + " chat | " + modelId());
        });
      }
      catch (Exception error)
      {
        fail(error);
      }
    });
  }

  private void renderConversation()
  {
    output.setText(currentMarkdown.isEmpty() ? ""
        : ReaderAiMarkdown.render(currentMarkdown, density()));
    output.setVisibility(currentMarkdown.isEmpty() ? View.GONE : View.VISIBLE);
    while (conversation.getChildCount() > 1)
      conversation.removeViewAt(1);
    for (ReaderAiService.ChatTurn turn : turns)
    {
      TextView user = messageText();
      user.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
      user.setBackground(ui.panel(ui.highlight, ui.accent, 8));
      user.setText(ReaderAiMarkdown.render("**You**\n\n" + turn.question,
            density()));
      LinearLayout.LayoutParams userParams = matchWrap();
      userParams.topMargin = ui.dp(10);
      conversation.addView(user, userParams);

      TextView ai = messageText();
      ai.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
      ai.setText(ReaderAiMarkdown.render("**AI**\n\n" + turn.answer,
            density()));
      LinearLayout.LayoutParams aiParams = matchWrap();
      aiParams.topMargin = ui.dp(4);
      conversation.addView(ai, aiParams);
    }
    outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
  }

  private void copyCurrent()
  {
    String text = effectiveContent(true);
    if (text.isEmpty())
      return;
    ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(
        Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("FrankenKey Reader AI", text));
    Toast.makeText(activity, "Reader AI result copied", Toast.LENGTH_SHORT).show();
  }

  private void chooseSave()
  {
    if (turns.isEmpty() || currentType == ReaderAiStore.Type.ARTICLE_CHAT)
    {
      saveCurrent(true);
      return;
    }
    new AlertDialog.Builder(activity).setTitle("Save Reader AI result")
      .setItems(new String[]{"Summary only", "Summary + current chat"},
          (dialog, which) -> saveCurrent(which == 1)).show();
  }

  private void saveCurrent(boolean includeChat)
  {
    String content = currentType == ReaderAiStore.Type.ARTICLE_CHAT
      ? ReaderAiService.chatMarkdown(turns) : currentMarkdown;
    if (content.trim().isEmpty())
      return;
    String chat = includeChat && currentType != ReaderAiStore.Type.ARTICLE_CHAT
      ? ReaderAiService.chatMarkdown(turns) : "";
    store.save(article.readerItemId, article.title, currentType, content, chat,
        article.sourceUrl, article.sourceHost, article.author, modelId(),
        promptIdentity(), false);
    Toast.makeText(activity, "Reader AI result saved", Toast.LENGTH_SHORT).show();
  }

  private void readCurrent()
  {
    if (!isSummary())
      return;
    String plainText = ReaderAiMarkdown.plainText(currentMarkdown).trim();
    if (plainText.isEmpty())
      return;
    String identity = currentCacheKey.isEmpty()
      ? Long.toString(System.currentTimeMillis()) : currentCacheKey;
    ReaderActivity.startQuickRead(activity, "reader-ai:" + identity,
        article.title + " — " + currentType.label, plainText);
    dialog.dismiss();
  }

  private void chooseShare()
  {
    if (turns.isEmpty() || currentType == ReaderAiStore.Type.ARTICLE_CHAT)
    {
      shareCurrent(true);
      return;
    }
    new AlertDialog.Builder(activity).setTitle("Share Reader AI result")
      .setItems(new String[]{"Summary only", "Summary + current chat"},
          (dialog, which) -> shareCurrent(which == 1)).show();
  }

  private void shareCurrent(boolean includeChat)
  {
    String content = currentType == ReaderAiStore.Type.ARTICLE_CHAT
      ? ReaderAiService.chatMarkdown(turns) : currentMarkdown;
    String chat = includeChat && currentType != ReaderAiStore.Type.ARTICLE_CHAT
      ? ReaderAiService.chatMarkdown(turns) : "";
    ReaderAiTextShare.share(activity, article.title,
        currentType == null ? "Reader AI" : currentType.label, content, chat,
        article.sourceUrl);
  }

  private String effectiveContent(boolean includeChat)
  {
    if (currentType == ReaderAiStore.Type.ARTICLE_CHAT)
      return ReaderAiService.chatMarkdown(turns);
    String chat = includeChat ? ReaderAiService.chatMarkdown(turns) : "";
    return currentMarkdown + (chat.isEmpty() ? "" : "\n\n" + chat);
  }

  private void runAfterDisclosure(Runnable action)
  {
    if (settings.isDisclosureAccepted())
    {
      action.run();
      return;
    }
    String sourceDescription = article.sourceUrl.isEmpty()
      ? "this clipboard text"
      : "this saved article text";
    new AlertDialog.Builder(activity).setTitle("Reader AI privacy")
      .setMessage("When you request AI, FrankenKey sends "
          + sourceDescription
          + " and your questions to OpenRouter and the model you select. "
          + "Nothing is sent merely by opening Reader AI.")
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Continue", (dialog, which) -> {
        settings.setDisclosureAccepted(true);
        action.run();
      }).show();
  }

  private void withModel(ModelAction action)
  {
    if (selectedModel != null && selectedModel.id.equals(settings.getModelId()))
    {
      action.run(selectedModel);
      return;
    }
    if (busy)
      return;
    begin("Loading OpenRouter model…");
    executor.execute(() -> {
      ReaderAiOpenRouter.Model resolved = null;
      try
      {
        List<ReaderAiOpenRouter.Model> models = client.fetchModels(
            settings.getApiKey());
        String selected = settings.getModelId();
        for (ReaderAiOpenRouter.Model model : models)
          if (model.id.equals(selected))
            resolved = model;
        if (resolved == null && selected.isEmpty())
          for (ReaderAiOpenRouter.Model model : models)
            if (ReaderAiOpenRouter.PREFERRED_MODEL_ID.equals(model.id))
              resolved = model;
      }
      catch (Exception ignored)
      {
        // Generation can still succeed if catalog retrieval is temporarily unavailable.
      }
      if (resolved == null)
      {
        String id = settings.getModelId();
        if (id.isEmpty())
        {
          fail(new IllegalStateException("Choose an OpenRouter model in Settings"));
          return;
        }
        resolved = new ReaderAiOpenRouter.Model(id, id, 0, Double.NaN,
            Double.NaN);
      }
      ReaderAiOpenRouter.Model finalModel = resolved;
      post(() -> {
        selectedModel = finalModel;
        finish("Ready | " + finalModel.id);
        action.run(finalModel);
      });
    });
  }

  private void begin(String message)
  {
    busy = true;
    status.setText(message);
    setEnabled(false);
  }

  private void finish(String message)
  {
    busy = false;
    status.setText(message);
    setEnabled(true);
  }

  private void fail(Exception error)
  {
    post(() -> {
      busy = false;
      status.setText(error.getMessage() == null ? "Reader AI request failed"
          : error.getMessage());
      setEnabled(true);
    });
  }

  private void setEnabled(boolean enabled)
  {
    summaryOne.setEnabled(enabled);
    summaryTwo.setEnabled(enabled);
    directChat.setEnabled(enabled);
    quiz.setEnabled(enabled);
    send.setEnabled(enabled);
  }

  private void setActionsVisible(boolean visible)
  {
    int value = visible ? View.VISIBLE : View.GONE;
    copy.setVisibility(value);
    save.setVisibility(value);
    read.setVisibility(visible && isSummary() ? View.VISIBLE : View.GONE);
    share.setVisibility(value);
  }
  private boolean isSummary()
  {
    return currentType == ReaderAiStore.Type.SUMMARY_ONE
      || currentType == ReaderAiStore.Type.SUMMARY_TWO;
  }

  private void selectMode(Button selected)
  {
    ui.selected(summaryOne, selected == summaryOne);
    ui.selected(summaryTwo, selected == summaryTwo);
    ui.selected(directChat, selected == directChat);
    ui.selected(quiz, selected == quiz);
  }

  private void applyTextSize()
  {
    for (int index = 0; index < conversation.getChildCount(); index++)
      if (conversation.getChildAt(index) instanceof TextView)
        ((TextView)conversation.getChildAt(index)).setTextSize(textSizeSp);
  }

  private TextView messageText()
  {
    TextView text = ui.text("", textSizeSp, ui.text);
    text.setTextIsSelectable(true);
    text.setMovementMethod(LinkMovementMethod.getInstance());
    text.setLinkTextColor(ui.accent);
    text.setLineSpacing(0, 1.18f);
    return text;
  }

  private Button modeButton(String label, Runnable action)
  {
    Button button = ui.button(label);
    button.setOnClickListener(ignored -> action.run());
    return button;
  }

  private Button actionButton(String label, Runnable action)
  {
    Button button = ui.button(label);
    button.setOnClickListener(ignored -> action.run());
    button.setContentDescription(label + " Reader AI result");
    return button;
  }


  private LinearLayout.LayoutParams matchWrap()
  {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
  }


  private void settingsChanged()
  {
    selectedModel = null;
    status.setText("Reader AI settings updated");
  }

  private String modelId()
  {
    return selectedModel == null ? settings.getModelId() : selectedModel.id;
  }

  private String promptIdentity()
  {
    return ReaderAiRequest.cacheKey(currentType == null ? "" : currentType.name(),
        currentPrompt, modelId(), article.sourceUrl, article.contentHash, "");
  }

  private float density()
  {
    return activity.getResources().getDisplayMetrics().density;
  }

  private int backgroundColor()
  {
    android.util.TypedValue value = new android.util.TypedValue();
    return activity.getTheme().resolveAttribute(android.R.attr.colorBackground,
        value, true) ? value.data : 0xff0b0d10;
  }

  private void post(Runnable action)
  {
    activity.runOnUiThread(() -> {
      if (!activity.isFinishing() && dialog != null && dialog.isShowing())
        action.run();
    });
  }

  private interface ModelAction { void run(ReaderAiOpenRouter.Model model); }
}
