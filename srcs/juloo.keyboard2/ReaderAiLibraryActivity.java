package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Searchable, sortable, favorite-aware library of explicitly saved AI outputs. */
public final class ReaderAiLibraryActivity extends Activity
{
  private ReaderAiUi ui;
  private ReaderAiStore store;
  private ReaderLibrary library;
  private ExecutorService executor;
  private LinearLayout rows;
  private EditText search;
  private CheckBox favorites;
  private Button sort;
  private Button sourceFilterButton;
  private Button outputFilterButton;
  private ReaderAiStore.SourceFilter sourceFilter =
    ReaderAiStore.SourceFilter.ALL;
  private ReaderAiStore.OutputFilter outputFilter =
    ReaderAiStore.OutputFilter.ALL;
  private boolean oldestFirst;
  private long loadGeneration;

  @Override protected void onCreate(Bundle state)
  {
    setTheme(ReaderActivity.themeResource(this));
    super.onCreate(state);
    ui = new ReaderAiUi(this);
    store = new ReaderAiStore(this);
    library = new ReaderLibrary(this);
    executor = Executors.newSingleThreadExecutor();

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));

    LinearLayout header = ui.row();
    Button back = ui.button("Back");
    back.setContentDescription("Close saved Reader AI results");
    back.setOnClickListener(ignored -> finish());
    TextView title = ui.text("Saved Reader AI", 21, ui.text);
    title.setTypeface(android.graphics.Typeface.DEFAULT,
        android.graphics.Typeface.BOLD);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    titleParams.setMarginStart(ui.dp(10));
    header.addView(back);
    header.addView(title, titleParams);
    root.addView(header);

    search = new EditText(this);
    search.setHint("Search titles, results, chat, or source…");
    search.setTextColor(ui.text);
    search.setHintTextColor(ui.muted);
    search.setSingleLine(true);
    search.setPadding(ui.dp(12), 0, ui.dp(12), 0);
    search.setMinHeight(ui.dp(48));
    search.setBackground(ui.panel(ui.surface, ui.border, 8));
    LinearLayout.LayoutParams searchParams = matchWrap();
    searchParams.topMargin = ui.dp(10);
    root.addView(search, searchParams);

    LinearLayout controls = ui.row();
    favorites = new CheckBox(this);
    favorites.setText("Favorites only");
    favorites.setTextColor(ui.text);
    favorites.setMinHeight(ui.dp(48));
    sort = ui.button("Newest first");
    sort.setOnClickListener(ignored -> {
      oldestFirst = !oldestFirst;
      sort.setText(oldestFirst ? "Oldest first" : "Newest first");
      reload();
    });
    controls.addView(favorites, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    controls.addView(sort);
    root.addView(controls);

    LinearLayout filters = ui.row();
    sourceFilterButton = ui.button("Source: All");
    sourceFilterButton.setContentDescription("Filter saved results by source");
    sourceFilterButton.setOnClickListener(ignored -> cycleSourceFilter());
    outputFilterButton = ui.button("Type: All");
    outputFilterButton.setContentDescription("Filter saved results by output type");
    outputFilterButton.setOnClickListener(ignored -> cycleOutputFilter());
    filters.addView(sourceFilterButton, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    LinearLayout.LayoutParams outputFilterParams = new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    outputFilterParams.setMarginStart(ui.dp(8));
    filters.addView(outputFilterButton, outputFilterParams);
    root.addView(filters);

    rows = new LinearLayout(this);
    rows.setOrientation(LinearLayout.VERTICAL);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(rows);
    root.addView(scroll, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    setContentView(root);

    search.addTextChangedListener(new TextWatcher()
    {
      @Override public void beforeTextChanged(CharSequence s, int start,
          int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before,
          int count) { reload(); }
      @Override public void afterTextChanged(Editable s) {}
    });
    favorites.setOnCheckedChangeListener((button, checked) -> reload());
    reload();
  }

  @Override protected void onDestroy()
  {
    if (executor != null)
      executor.shutdownNow();
    if (store != null)
      store.close();
    if (library != null)
      library.close();
    super.onDestroy();
  }

  private void reload()
  {
    final long generation = ++loadGeneration;
    final String query = search.getText().toString();
    final boolean favoritesOnly = favorites.isChecked();
    final boolean oldest = oldestFirst;
    executor.execute(() -> {
      List<ReaderAiStore.Entry> loaded = store.search(query, favoritesOnly,
          oldest, sourceFilter, outputFilter);
      runOnUiThread(() -> {
        if (generation != loadGeneration || isFinishing())
          return;
        rows.removeAllViews();
        if (loaded.isEmpty())
        {
          TextView empty = ui.text("No saved Reader AI results.", 15, ui.muted);
          empty.setGravity(Gravity.CENTER);
          empty.setPadding(ui.dp(16), ui.dp(40), ui.dp(16), ui.dp(40));
          rows.addView(empty, matchWrap());
          return;
        }
        ReaderAiStore.Entry previous = null;
        for (ReaderAiStore.Entry entry : loaded)
        {
          if (previous == null || !sameSavedDay(previous.createdAt,
                entry.createdAt, TimeZone.getDefault()))
            rows.addView(dateMarker(entry.createdAt), matchWrap());
          rows.addView(row(entry), rowParams());
          previous = entry;
        }
      });
    });
  }

  private View dateMarker(long timestamp)
  {
    LinearLayout marker = ui.row();
    marker.setGravity(Gravity.CENTER_VERTICAL);
    marker.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(4));
    View leading = new View(this);
    leading.setBackgroundColor(ui.border);
    marker.addView(leading, new LinearLayout.LayoutParams(0, ui.dp(1), 1f));
    TextView label = ui.text(savedDayLabel(timestamp, System.currentTimeMillis(),
          Locale.getDefault(), TimeZone.getDefault()), 10, ui.muted);
    label.setPadding(ui.dp(8), 0, ui.dp(8), 0);
    marker.addView(label);
    View trailing = new View(this);
    trailing.setBackgroundColor(ui.border);
    marker.addView(trailing, new LinearLayout.LayoutParams(0, ui.dp(1), 1f));
    return marker;
  }

  static boolean sameSavedDay(long left, long right, TimeZone zone)
  {
    Calendar leftDate = Calendar.getInstance(zone);
    leftDate.setTimeInMillis(left);
    Calendar rightDate = Calendar.getInstance(zone);
    rightDate.setTimeInMillis(right);
    return leftDate.get(Calendar.ERA) == rightDate.get(Calendar.ERA) &&
      leftDate.get(Calendar.YEAR) == rightDate.get(Calendar.YEAR) &&
      leftDate.get(Calendar.DAY_OF_YEAR) ==
        rightDate.get(Calendar.DAY_OF_YEAR);
  }

  static String savedDayLabel(long timestamp, long now, Locale locale,
      TimeZone zone)
  {
    Calendar saved = Calendar.getInstance(zone, locale);
    saved.setTimeInMillis(timestamp);
    Calendar current = Calendar.getInstance(zone, locale);
    current.setTimeInMillis(now);
    String pattern = saved.get(Calendar.YEAR) == current.get(Calendar.YEAR)
      ? "d MMM" : "d MMM yyyy";
    SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
    formatter.setTimeZone(zone);
    return formatter.format(new Date(timestamp));
  }

  private View row(ReaderAiStore.Entry entry)
  {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
    row.setBackground(ui.panel(ui.surface, entry.favorite ? ui.accent : ui.border,
          8));
    TextView title = ui.text((entry.favorite ? "★ " : "") + entry.articleTitle,
        16, ui.text);
    title.setTypeface(android.graphics.Typeface.DEFAULT,
        android.graphics.Typeface.BOLD);
    title.setMaxLines(2);
    row.addView(title);
    String meta = entry.type.label + " • "
      + (entry.sourceType == ReaderAiStore.SourceType.BOOK ? "Book" : "Article")
      + " • " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
          DateFormat.SHORT).format(new Date(entry.createdAt));
    if (!entry.author.isEmpty())
      meta += " • " + entry.author;
    else if (!entry.sourceHost.isEmpty())
      meta += " • " + entry.sourceHost;
    String preview = entry.contentMarkdown.replace('\n', ' ').trim();
    if (preview.length() > 180)
      preview = preview.substring(0, 180) + "…";
    TextView body = ui.text(preview, 14, ui.text);
    body.setMaxLines(3);
    body.setPadding(0, ui.dp(6), 0, 0);
    row.addView(body);
    row.setContentDescription((entry.favorite ? "Favorite. " : "")
        + entry.type.label + ". " + entry.articleTitle);
    row.setOnClickListener(ignored -> showDetail(entry.id));
    return row;
  }

  private void showDetail(long id)
  {
    executor.execute(() -> {
      ReaderAiStore.Entry entry = store.load(id);
      ReaderLibrary.Item source = entry == null ? null : resolveSource(entry);
      runOnUiThread(() -> {
        if (entry != null && !isFinishing())
          showDetail(entry, source);
      });
    });
  }

  private void showDetail(ReaderAiStore.Entry entry, ReaderLibrary.Item source)
  {
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
    TextView meta = ui.text(entry.type.label + " • " + entry.modelId, 12,
        ui.muted);
    content.addView(meta);
    if (!entry.author.isEmpty())
      content.addView(ui.text(entry.author, 13, ui.muted));
    if (!entry.provenance.isEmpty() && entry.sourceUrl.isEmpty())
      content.addView(ui.text(entry.provenance, 13, ui.muted));
    if (entry.sourceType == ReaderAiStore.SourceType.BOOK && source == null)
      content.addView(ui.text("Source unavailable. The saved AI output remains usable.",
            13, ui.muted));
    TextView rendered = ui.text("", 15, ui.text);
    rendered.setTextIsSelectable(true);
    rendered.setMovementMethod(LinkMovementMethod.getInstance());
    rendered.setLinkTextColor(ui.accent);
    rendered.setLineSpacing(0, 1.18f);
    rendered.setText(ReaderAiMarkdown.render(entry.contentMarkdown
          + (entry.chatMarkdown.isEmpty() ? "" : "\n\n" + entry.chatMarkdown),
          getResources().getDisplayMetrics().density));
    content.addView(rendered, matchWrap());
    if (!entry.sourceUrl.isEmpty())
    {
      TextView sourceLink = ui.text("Original URL\n" + entry.sourceUrl, 13,
          ui.accent);
      sourceLink.setPadding(0, ui.dp(14), 0, ui.dp(8));
      sourceLink.setOnClickListener(ignored -> startActivity(new Intent(
              Intent.ACTION_VIEW, Uri.parse(entry.sourceUrl))));
      content.addView(sourceLink);
    }
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);

    AlertDialog detail = new AlertDialog.Builder(this)
      .setTitle((entry.favorite ? "★ " : "") + entry.articleTitle)
      .setView(scroll)
      .setNegativeButton("Close", null)
      .setPositiveButton("Actions", null)
      .create();
    detail.setOnShowListener(ignored -> detail.getButton(
          AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
            showActions(detail, entry, source)));
    detail.show();
  }

  private void showActions(AlertDialog detail, ReaderAiStore.Entry entry,
      ReaderLibrary.Item source)
  {
    List<String> labels = new java.util.ArrayList<>();
    labels.add("Copy");
    labels.add("Share");
    if (entry.type != ReaderAiStore.Type.ARTICLE_QUIZ)
      labels.add("Speed Read");
    labels.add(entry.favorite ? "Remove favorite" : "Add favorite");
    if (source != null)
      labels.add(entry.sourceType == ReaderAiStore.SourceType.BOOK
          ? "Open Book" : "Open Article");
    labels.add("Delete");
    new AlertDialog.Builder(this).setTitle("Saved result actions")
      .setItems(labels.toArray(new String[0]), (dialog, which) -> {
        String action = labels.get(which);
        if ("Copy".equals(action))
        {
          ClipboardManager clipboard = (ClipboardManager)getSystemService(
              Context.CLIPBOARD_SERVICE);
          clipboard.setPrimaryClip(ClipData.newPlainText("Reader AI",
                ReaderAiTextShare.format(entry.articleTitle, entry.type.label,
                  entry.contentMarkdown, entry.chatMarkdown, entry.sourceUrl)));
        }
        else if ("Share".equals(action))
          ReaderAiTextShare.share(this, entry.articleTitle, entry.type.label,
              entry.contentMarkdown, entry.chatMarkdown, entry.sourceUrl);
        else if ("Speed Read".equals(action))
          speedRead(detail, entry);
        else if (action.contains("favorite"))
        {
          executor.execute(() -> {
            store.setFavorite(entry.id, !entry.favorite);
            runOnUiThread(() -> { detail.dismiss(); reload(); });
          });
        }
        else if ("Open Book".equals(action) || "Open Article".equals(action))
          ReaderActivity.startLibraryItem(this, source.id);
        else if ("Delete".equals(action))
          confirmDelete(detail, entry);
      }).show();
  }

  private ReaderLibrary.Item resolveSource(ReaderAiStore.Entry entry)
  {
    try
    {
      ReaderLibrary.Item source = entry.readerItemId == null ? null
        : library.get(entry.readerItemId);
      if (source == null && entry.sourceType == ReaderAiStore.SourceType.BOOK
          && entry.bookFingerprint != null)
        source = library.getByContentHash(entry.bookFingerprint);
      return source != null &&
        source.sourceState == ReaderLibrary.SourceState.AVAILABLE ? source : null;
    }
    catch (ReaderLibrary.LibraryException error)
    {
      return null;
    }
  }

  private void speedRead(AlertDialog detail, ReaderAiStore.Entry entry)
  {
    String plainText = ReaderAiMarkdown.plainText(entry.contentMarkdown
        + (entry.chatMarkdown.isEmpty() ? "" : "\n\n" + entry.chatMarkdown))
      .trim();
    if (plainText.isEmpty())
      return;
    ReaderActivity.startQuickRead(this, "saved-reader-ai:" + entry.id,
        entry.articleTitle + " — " + entry.type.label, plainText);
    detail.dismiss();
  }

  private void cycleSourceFilter()
  {
    if (sourceFilter == ReaderAiStore.SourceFilter.ALL)
      sourceFilter = ReaderAiStore.SourceFilter.ARTICLES;
    else if (sourceFilter == ReaderAiStore.SourceFilter.ARTICLES)
      sourceFilter = ReaderAiStore.SourceFilter.BOOKS;
    else
      sourceFilter = ReaderAiStore.SourceFilter.ALL;
    sourceFilterButton.setText(sourceFilter == ReaderAiStore.SourceFilter.ALL
        ? "Source: All" : sourceFilter == ReaderAiStore.SourceFilter.ARTICLES
        ? "Source: Articles" : "Source: Books");
    reload();
  }

  private void cycleOutputFilter()
  {
    if (outputFilter == ReaderAiStore.OutputFilter.ALL)
      outputFilter = ReaderAiStore.OutputFilter.SUMMARY;
    else if (outputFilter == ReaderAiStore.OutputFilter.SUMMARY)
      outputFilter = ReaderAiStore.OutputFilter.QUIZ;
    else if (outputFilter == ReaderAiStore.OutputFilter.QUIZ)
      outputFilter = ReaderAiStore.OutputFilter.CHAT;
    else
      outputFilter = ReaderAiStore.OutputFilter.ALL;
    outputFilterButton.setText(outputFilter == ReaderAiStore.OutputFilter.ALL
        ? "Type: All" : outputFilter == ReaderAiStore.OutputFilter.SUMMARY
        ? "Type: Summary" : outputFilter == ReaderAiStore.OutputFilter.QUIZ
        ? "Type: Quiz" : "Type: Chat");
    reload();
  }

  private void confirmDelete(AlertDialog detail, ReaderAiStore.Entry entry)
  {
    new AlertDialog.Builder(this).setTitle("Delete saved result?")
      .setMessage("This removes only the saved AI output. The source is not deleted.")
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Delete", (dialog, which) -> executor.execute(() -> {
        store.delete(entry.id);
        runOnUiThread(() -> { detail.dismiss(); reload(); });
      })).show();
  }

  private LinearLayout.LayoutParams matchWrap()
  {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private LinearLayout.LayoutParams rowParams()
  {
    LinearLayout.LayoutParams params = matchWrap();
    params.topMargin = ui.dp(8);
    return params;
  }
}
