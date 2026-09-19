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
  private ReaderAiService.Article article;
  private final ReaderAiUi ui;
  private final ReaderAiSettings settings;
  private final ReaderAiOpenRouter client = new ReaderAiOpenRouter();
  private final ReaderAiCache cache;
  private final ReaderAiStore store;
  private final ReaderAiService service;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final List<ReaderAiService.ChatTurn> turns = new ArrayList<>();
  private final ReaderAiAction autoAction;
  private final SourceReloader sourceReloader;
  private final Runnable onDismiss;

  private Dialog dialog;
  private TextView status;
  private TextView output;
  private LinearLayout conversation;
  private ScrollView outputScroll;
  private LinearLayout chatRow;
  private EditText chatInput;
  private TextView sourceLabel;
  private TextView sourcePreview;
  private ImageButton summaryOne;
  private ImageButton summaryTwo;
  private ImageButton directChat;
  private ImageButton quiz;
  private ImageButton copy;
  private ImageButton save;
  private ImageButton read;
  private ImageButton share;
  private ImageButton send;
  private float textSizeSp = MIN_TEXT_SP;
  private boolean busy;
  private ReaderAiOpenRouter.Model selectedModel;
  private ReaderAiStore.Type currentType;
  private String currentPrompt = "";
  private String currentMarkdown = "";
  private String currentCacheKey = "";

  static void show(Activity activity, ReaderAiService.Article article)
  {
    new ReaderAiDialog(activity, article, null, null, null).show();
  }

  static void show(Activity activity, ReaderAiService.Article article,
      ReaderAiAction autoAction, SourceReloader sourceReloader,
      Runnable onDismiss)
  {
    new ReaderAiDialog(activity, article, autoAction, sourceReloader,
        onDismiss).show();
  }

  private ReaderAiDialog(Activity activity, ReaderAiService.Article article,
      ReaderAiAction autoAction, SourceReloader sourceReloader,
      Runnable onDismiss)
  {
    this.activity = activity;
    this.article = article;
    this.autoAction = autoAction;
    this.sourceReloader = sourceReloader;
    this.onDismiss = onDismiss;
    ui = new ReaderAiUi(activity);
    settings = new ReaderAiSettings(activity);
    cache = new ReaderAiCache(activity);
    store = new ReaderAiStore(activity);
    service = new ReaderAiService(client, cache, store);
    service.setProgressListener(this::showProgress);
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
    ImageButton savedItems = ui.iconButton(R.drawable.snippet_icon_bookmark,
        "Saved Reader AI results");
    savedItems.setOnClickListener(ignored -> activity.startActivity(
          new Intent(activity, ReaderAiLibraryActivity.class)));
    LinearLayout.LayoutParams savedParams = new LinearLayout.LayoutParams(
        ui.dp(42), ui.dp(42));
    savedParams.setMarginStart(ui.dp(8));
    header.addView(savedItems, savedParams);
    ImageButton settingsButton = ui.iconButton(R.drawable.cog_outline,
        "Open Reader AI settings");
    settingsButton.setOnClickListener(ignored -> ReaderAiSettingsDialog.show(
          activity, this::settingsChanged));
    LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
        ui.dp(42), ui.dp(42));
    settingsParams.setMarginStart(ui.dp(8));
    header.addView(settingsButton, settingsParams);
    ImageButton close = ui.iconButton(R.drawable.ic_reader_ai_close,
        "Close Reader AI and cancel active work");
    close.setOnClickListener(ignored -> dialog.dismiss());
    LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
        ui.dp(42), ui.dp(42));
    closeParams.setMarginStart(ui.dp(8));
    header.addView(close, closeParams);
    root.addView(header);

    LinearLayout modes = ui.row();
    summaryOne = modeButton(R.drawable.ic_reader_ai_summary_one,
        "Summary One", () -> summary(true));
    summaryTwo = modeButton(R.drawable.ic_reader_ai_summary_two,
        "Summary Two", () -> summary(false));
    directChat = modeButton(R.drawable.snippet_icon_message_circle,
        "Chat", this::startDirectChat);
    quiz = modeButton(R.drawable.ic_reader_ai_quiz,
        sourceTitle() + " Quiz", this::chooseQuiz);
    ui.addWeighted(modes, summaryOne, 1f, 0);
    ui.addWeighted(modes, summaryTwo, 1f, ui.dp(8));
    ui.addWeighted(modes, directChat, 1f, ui.dp(8));
    ui.addWeighted(modes, quiz, 1f, ui.dp(8));
    LinearLayout.LayoutParams modeParams = matchWrap();
    modeParams.topMargin = ui.dp(8);
    root.addView(modes, modeParams);

    if (article.sourceType == ReaderAiService.Article.SourceType.CLIPBOARD
        || sourceReloader != null)
    {
      LinearLayout sourceRow = ui.row();
      sourceLabel = ui.text(sourceLabelText(), 12, ui.muted);
      sourceLabel.setSingleLine(true);
      sourceLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
      sourceRow.addView(sourceLabel, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      ImageButton loadClipboard = ui.iconButton(R.drawable.ic_clipboard_paste,
          activity.getString(R.string.reader_ai_load_clipboard));
      loadClipboard.setOnClickListener(ignored -> reloadSource());
      LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(
          ui.dp(42), ui.dp(42));
      loadParams.setMarginStart(ui.dp(8));
      sourceRow.addView(loadClipboard, loadParams);
      LinearLayout.LayoutParams sourceParams = matchWrap();
      sourceParams.topMargin = ui.dp(8);
      root.addView(sourceRow, sourceParams);
    }
    sourcePreview = ui.text("", 12, ui.muted);
    sourcePreview.setTextIsSelectable(true);
    ScrollView sourceScroll = new ScrollView(activity);
    sourceScroll.setBackground(ui.panel(ui.surface, ui.border, 8));
    sourceScroll.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
    sourceScroll.addView(sourcePreview);
    LinearLayout.LayoutParams sourceScrollParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(96));
    sourceScrollParams.topMargin = ui.dp(8);
    root.addView(sourceScroll, sourceScrollParams);
    refreshSourcePreview();


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
    chatInput.setHint(hasSource()
        ? "Ask about this " + sourceLower() + "…" : "Ask anything…");
    chatInput.setTextColor(ui.text);
    chatInput.setHintTextColor(ui.muted);
    chatInput.setTextSize(14);
    chatInput.setSingleLine(true);
    chatInput.setInputType(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    chatInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
    chatInput.setPadding(ui.dp(10), 0, ui.dp(10), 0);
    chatInput.setBackground(ui.panel(ui.surface, ui.border, 8));
    send = ui.iconButton(R.drawable.snippet_icon_send,
        "Send Reader AI question");
    send.setOnClickListener(ignored -> ask());
    chatRow.addView(chatInput, new LinearLayout.LayoutParams(0, ui.dp(44), 1f));
    LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
        ui.dp(44), ui.dp(44));
    sendParams.setMarginStart(ui.dp(8));
    chatRow.addView(send, sendParams);
    chatRow.setVisibility(View.GONE);
    LinearLayout.LayoutParams chatParams = matchWrap();
    chatParams.topMargin = ui.dp(8);
    root.addView(chatRow, chatParams);

    LinearLayout actions = ui.row();
    copy = actionButton(R.drawable.ic_reader_ai_copy,
        "Copy Reader AI result", this::copyCurrent);
    save = actionButton(R.drawable.snippet_icon_bookmark,
        "Save Reader AI result", this::chooseSave);
    read = actionButton(R.drawable.ic_reader_play,
        "Speed-read summary or chat in the plain-text Reader",
        this::readCurrent);
    share = actionButton(R.drawable.ic_reader_ai_share,
        "Share Reader AI result", this::chooseShare);
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
      if (onDismiss != null)
        onDismiss.run();
    });
    dialog.show();
    if (window != null)
    {
      window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels
            - ui.dp(16), ui.dp(760)), ViewGroup.LayoutParams.MATCH_PARENT);
      window.setGravity(Gravity.CENTER);
    }
    root.requestApplyInsets();
    dispatchAutoAction();
  }

  private void dispatchAutoAction()
  {
    if (autoAction == null)
      return;
    switch (autoAction)
    {
      case NONE: return;
      case SUMMARY_ONE: summary(true); return;
      case SUMMARY_TWO: summary(false); return;
      case QUIZ: chooseQuiz(); return;
      case LOAD_CLIPBOARD: reloadSource(); return;
      default: startDirectChat(); return;
    }
  }

  private void summary(boolean first)
  {
    if (!hasSource())
    {
      needSource();
      return;
    }
    runAfterDisclosure(() -> withModel(model -> {
      String prompt = first ? settings.getSummaryOnePrompt()
        : settings.getSummaryTwoPrompt();
      String label = first ? "Summary One" : "Summary Two";
      if (service.needsMultipleCalls(article, prompt, model))
      {
        new AlertDialog.Builder(activity)
          .setTitle("Long " + sourceLower())
          .setMessage("This " + sourceLower()
              + " needs multiple billable OpenRouter calls. Continue?")
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
      currentPrompt = hasSource() ? ReaderAiRequest.DIRECT_CHAT_PROMPT
        : ReaderAiRequest.GENERAL_CHAT_PROMPT;
      currentMarkdown = "";
      currentCacheKey = "";
      turns.clear();
      selectedModel = model;
      renderConversation();
      output.setText(hasSource()
          ? "Ask a question to start a grounded " + sourceLower() + " chat."
          : "Ask anything to start a chat.");
      chatRow.setVisibility(View.VISIBLE);
      setActionsVisible(false);
      status.setText((hasSource() ? sourceTitle() + " Chat" : "Chat")
          + " | " + model.id);
      selectMode(directChat);
      chatInput.requestFocus();
    }));
  }

  private void chooseQuiz()
  {
    if (!hasSource())
    {
      needSource();
      return;
    }
    runAfterDisclosure(() -> withModel(model -> {
      String suffix = article.isBook() ? " per chapter" : " questions";
      String[] choices = {"6" + suffix, "10" + suffix, "12" + suffix,
          "20" + suffix};
      int[] counts = {6, 10, 12, 20};
      new AlertDialog.Builder(activity).setTitle(sourceTitle() + " Quiz")
        .setItems(choices, (dialog, which) -> {
          if (!article.isBook())
          {
            executeQuiz(model, counts[which]);
            return;
          }
          int chapters =
            ReaderBookAiPlanner.readableChapters(article.bookChapters).size();
          new AlertDialog.Builder(activity)
            .setTitle("Generate Book Quiz?")
            .setMessage(counts[which] + " questions will be generated for "
                + "each of " + chapters + " chapters. This can require "
                + "multiple billable OpenRouter calls. Completed evidence is "
                + "kept for safe resume and reuse.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Generate", (notice, ignored) ->
                executeQuiz(model, counts[which]))
            .show();
        }).show();
    }));
  }

  private void executeQuiz(ReaderAiOpenRouter.Model model, int count)
  {
    begin(sourceTitle() + " Quiz | " + model.id);
    executor.execute(() -> {
      try
      {
        String result = service.quiz(settings.getApiKey(), model, article,
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
          finish(sourceTitle() + " Quiz | " + count
              + (article.isBook() ? " per chapter" : " questions")
              + " | " + model.id);
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
    if (selectedModel == null)
    {
      withModel(model -> {
        selectedModel = model;
        ask();
      });
      return;
    }
    begin((currentType == ReaderAiStore.Type.ARTICLE_CHAT
          ? sourceTitle() + " Chat" : currentType.label + " chat")
        + " | " + modelId());
    executor.execute(() -> {
      try
      {
        String answer = currentType == ReaderAiStore.Type.ARTICLE_CHAT
          ? service.directChat(settings.getApiKey(), selectedModel, article,
              turns, question)
          : service.followUp(settings.getApiKey(), selectedModel, article,
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
    output.setOnClickListener(ignored -> copyText(currentMarkdown));
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
      ai.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
      ai.setBackground(ui.panel(ui.highlight, ui.border, 8));
      ai.setText(ReaderAiMarkdown.render("**AI**\n\n" + turn.answer,
            density()));
      ai.setOnClickListener(ignored -> copyText(turn.answer));
      LinearLayout.LayoutParams aiParams = matchWrap();
      aiParams.topMargin = ui.dp(4);
      conversation.addView(ai, aiParams);
    }
    outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
  }

  private void copyText(String text)
  {
    if (text == null || text.isEmpty())
      return;
    ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(
        Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("FrankenKey Reader AI",
          text));
    Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show();
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
    ReaderAiStore.SourceType sourceType = ReaderAiStore.SourceType.valueOf(
        article.sourceType.name());
    String provenance = article.isBook()
      ? "Book: " + article.title
        + (article.author.isEmpty() ? "" : " — " + article.author)
      : article.sourceUrl;
    store.save(article.readerItemId, article.title, currentType, content, chat,
        article.sourceUrl, article.sourceHost, article.author, modelId(),
        promptIdentity(), sourceType, article.isBook() ? article.contentHash : "",
        provenance, false);
    Toast.makeText(activity, "Reader AI result saved", Toast.LENGTH_SHORT).show();
  }

  private void readCurrent()
  {
    if (!isReadableResult())
      return;
    String plainText = ReaderAiMarkdown.plainText(effectiveContent(true)).trim();
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
    String sourceDescription;
    if (article.sourceType == ReaderAiService.Article.SourceType.BOOK)
      sourceDescription = "selected excerpts from this book";
    else if (article.sourceType == ReaderAiService.Article.SourceType.PAGE)
      sourceDescription = "this captured page text";
    else if (article.sourceType == ReaderAiService.Article.SourceType.CLIPBOARD)
      sourceDescription = "this clipboard text";
    else
      sourceDescription = "this saved article text";
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

  private void showProgress(String message)
  {
    post(() -> {
      status.setText(message);
      output.setText("Book AI is working\n\n" + message);
      output.setVisibility(View.VISIBLE);
      while (conversation.getChildCount() > 1)
        conversation.removeViewAt(1);
      chatRow.setVisibility(View.GONE);
      setActionsVisible(false);
    });
  }

  private void begin(String message)
  {
    busy = true;
    status.setText(message);
    setEnabled(false);
    if (article.isBook())
    {
      output.setText("Book AI is working…");
      output.setVisibility(View.VISIBLE);
      while (conversation.getChildCount() > 1)
        conversation.removeViewAt(1);
      chatRow.setVisibility(View.GONE);
      setActionsVisible(false);
    }
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
      String message = error.getMessage() == null
        ? "Reader AI request failed" : error.getMessage();
      status.setText(message);
      output.setText(article.isBook() ? "Book AI stopped\n\n" + message
          : message);
      output.setVisibility(View.VISIBLE);
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
    read.setVisibility(visible && isReadableResult() ? View.VISIBLE : View.GONE);
    share.setVisibility(value);
  }

  private boolean isReadableResult()
  {
    return isSpeedReadEligible(currentType);
  }

  static boolean isSpeedReadEligible(ReaderAiStore.Type type)
  {
    return type != null && type != ReaderAiStore.Type.ARTICLE_QUIZ;
  }

  private void selectMode(View selected)
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

  private ImageButton modeButton(int icon, String description, Runnable action)
  {
    ImageButton button = ui.iconButton(icon, description);
    button.setOnClickListener(ignored -> action.run());
    return button;
  }

  private ImageButton actionButton(int icon, String description,
      Runnable action)
  {
    ImageButton button = ui.iconButton(icon, description);
    button.setOnClickListener(ignored -> action.run());
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

  private String sourceTitle()
  {
    if (article.isBook())
      return "Book";
    if (article.sourceType == ReaderAiService.Article.SourceType.PAGE)
      return "Page";
    return article.sourceType == ReaderAiService.Article.SourceType.CLIPBOARD
      ? "Clipboard" : "Article";
  }

  private String sourceLower()
  {
    if (article.isBook())
      return "book";
    if (article.sourceType == ReaderAiService.Article.SourceType.PAGE)
      return "page";
    return article.sourceType == ReaderAiService.Article.SourceType.CLIPBOARD
      ? "clipboard text" : "article";
  }

  private boolean hasSource()
  {
    return !article.text.trim().isEmpty();
  }

  private void needSource()
  {
    String message = activity.getString(R.string.reader_ai_need_source);
    status.setText(message);
    output.setText(message);
    output.setVisibility(View.VISIBLE);
  }

  private void reloadSource()
  {
    if (sourceReloader == null)
      return;
    ReaderAiService.Article reloaded = sourceReloader.reload();
    if (reloaded != null)
      article = reloaded;
    turns.clear();
    currentMarkdown = "";
    currentCacheKey = "";
    currentType = null;
    selectMode(null);
    renderConversation();
    chatRow.setVisibility(View.GONE);
    setActionsVisible(false);
    String message = hasSource()
      ? sourceTitle() + " loaded"
      : activity.getString(R.string.reader_ai_clipboard_empty);
    status.setText(message);
    output.setText(message);
    output.setVisibility(View.VISIBLE);
    if (sourceLabel != null)
      sourceLabel.setText(sourceLabelText());
    refreshSourcePreview();
  }

  private void refreshSourcePreview()
  {
    if (sourcePreview == null)
      return;
    if (!hasSource())
    {
      sourcePreview.setText(R.string.reader_ai_need_source);
      return;
    }
    String text = article.text;
    if (text.length() > 8000)
      text = text.substring(0, 8000) + "\n…";
    sourcePreview.setText(text);
  }
  private String sourceLabelText()
  {
    String title = article.title.isEmpty()
      ? activity.getString(R.string.reader_title_clipboard) : article.title;
    return title + " · " + article.text.length() + " chars";
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

  interface SourceReloader
  {
    ReaderAiService.Article reload();
  }
}
