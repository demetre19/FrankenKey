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
  private ExecutorService executor;
  private LinearLayout rows;
  private EditText search;
  private CheckBox favorites;
  private Button sort;
  private boolean oldestFirst;
  private long loadGeneration;

  @Override protected void onCreate(Bundle state)
  {
    setTheme(ReaderActivity.themeResource(this));
    super.onCreate(state);
    ui = new ReaderAiUi(this);
    store = new ReaderAiStore(this);
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
          oldest);
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
      + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(new Date(entry.createdAt));
    if (!entry.sourceHost.isEmpty())
      meta += " • " + entry.sourceHost;
    row.addView(ui.text(meta, 12, ui.muted));
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
      runOnUiThread(() -> {
        if (entry != null && !isFinishing())
          showDetail(entry);
      });
    });
  }

  private void showDetail(ReaderAiStore.Entry entry)
  {
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
    TextView meta = ui.text(entry.type.label + " • " + entry.modelId, 12,
        ui.muted);
    content.addView(meta);
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
      TextView source = ui.text("Original URL\n" + entry.sourceUrl, 13,
          ui.accent);
      source.setPadding(0, ui.dp(14), 0, ui.dp(8));
      source.setOnClickListener(ignored -> startActivity(new Intent(
              Intent.ACTION_VIEW, Uri.parse(entry.sourceUrl))));
      content.addView(source);
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
            showActions(detail, entry)));
    detail.show();
  }

  private void showActions(AlertDialog detail, ReaderAiStore.Entry entry)
  {
    List<String> labels = new java.util.ArrayList<>();
    labels.add("Copy");
    labels.add("Share");
    labels.add(entry.favorite ? "Remove favorite" : "Add favorite");
    if (entry.readerItemId != null)
      labels.add("Open Reader item");
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
        else if (action.contains("favorite"))
        {
          executor.execute(() -> {
            store.setFavorite(entry.id, !entry.favorite);
            runOnUiThread(() -> { detail.dismiss(); reload(); });
          });
        }
        else if ("Open Reader item".equals(action))
          ReaderActivity.startLibraryItem(this, entry.readerItemId);
        else if ("Delete".equals(action))
          confirmDelete(detail, entry);
      }).show();
  }

  private void confirmDelete(AlertDialog detail, ReaderAiStore.Entry entry)
  {
    new AlertDialog.Builder(this).setTitle("Delete saved result?")
      .setMessage("This removes the saved AI output. The Reader article is not deleted.")
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
