package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor for the centered Reader AI omnibutton: the tap and each of the
 * eight swipe sectors map to any catalog action (including No action).
 * Everything persists immediately (autosave).
 */
final class ReaderAiButtonSettings
{
  private ReaderAiButtonSettings() {}

  static void show(Activity activity, SharedPreferences prefs)
  {
    LinearLayout list = new LinearLayout(activity);
    list.setOrientation(LinearLayout.VERTICAL);
    int pad = (int)(16 * activity.getResources().getDisplayMetrics().density);
    list.setPadding(pad, pad, pad, 0);

    List<TextView> values = new ArrayList<>();
    for (ReaderAiAction.Sector sector : ReaderAiAction.Sector.values())
      list.addView(sectorRow(activity, prefs, sector, values));

    CheckBox swap = new CheckBox(activity);
    swap.setText(R.string.pref_reader_ai_button_swap);
    swap.setChecked(ReaderAiAction.aiOnRight(prefs));
    swap.setOnCheckedChangeListener((_view, checked) ->
        ReaderAiAction.setAiOnRight(prefs, checked));
    list.addView(swap);

    overlaySection(activity, list);

    android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
    scroll.addView(list);
    new AlertDialog.Builder(activity)
      .setTitle(R.string.pref_reader_ai_button_editor_title)
      .setView(scroll)
      .setPositiveButton(android.R.string.ok, null)
      .setNeutralButton(R.string.pref_reader_ai_button_reset,
          (dialog, which) -> {
            ReaderAiAction.Sector[] sectors = ReaderAiAction.Sector.values();
            for (int i = 0; i < sectors.length; i++)
            {
              ReaderAiAction.setAction(prefs, sectors[i],
                  sectors[i].defaultAction);
              values.get(i).setText(sectors[i].defaultAction.labelRes);
            }
            ReaderAiAction.setAiOnRight(prefs, true);
            swap.setChecked(true);
          })
      .show();
  }

  private static void overlaySection(Activity activity, LinearLayout list)
  {
    SharedPreferences overlay = activity.getSharedPreferences(
        "reader_ai_overlay", android.content.Context.MODE_PRIVATE);
    float density = activity.getResources().getDisplayMetrics().density;

    TextView header = new TextView(activity);
    header.setText(R.string.pref_reader_ai_overlay_title);
    header.setTextSize(16);
    header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
    header.setPadding(0, (int)(16 * density), 0, (int)(4 * density));
    list.addView(header);

    CheckBox enable = new CheckBox(activity);
    enable.setText(R.string.pref_reader_ai_overlay_enable);
    enable.setChecked(overlay.getBoolean("reader_ai_button_overlay", true));
    enable.setOnCheckedChangeListener((_view, checked) ->
        overlay.edit().putBoolean("reader_ai_button_overlay", checked).apply());
    list.addView(enable);

    // Live preview mirroring the floating button.
    android.widget.ImageView preview = new android.widget.ImageView(activity);
    int psize = (int)(56 * density);
    LinearLayout.LayoutParams pparams =
      new LinearLayout.LayoutParams(psize, psize);
    pparams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
    pparams.topMargin = (int)(8 * density);
    preview.setLayoutParams(pparams);
    preview.setImageResource(R.drawable.ic_reader_ai);
    preview.setPadding(psize / 5, psize / 5, psize / 5, psize / 5);
    list.addView(preview);
    Runnable refreshPreview = () -> applyPreview(preview, overlay, density);
    refreshPreview.run();

    TextView opacityLabel = new TextView(activity);
    opacityLabel.setTextSize(14);
    list.addView(opacityLabel);
    android.widget.SeekBar opacity = new android.widget.SeekBar(activity);
    opacity.setMax(80); // 0.2 .. 1.0
    int current = Math.round(overlay.getFloat("reader_ai_overlay_opacity", 0.5f)
        * 100) - 20;
    opacity.setProgress(Math.max(0, Math.min(80, current)));
    Runnable updateOpacityLabel = () -> opacityLabel.setText(
        activity.getString(R.string.pref_reader_ai_overlay_opacity) + " — " +
        Math.round(overlay.getFloat("reader_ai_overlay_opacity", 0.5f) * 100)
        + "%");
    updateOpacityLabel.run();
    opacity.setOnSeekBarChangeListener(
        new android.widget.SeekBar.OnSeekBarChangeListener()
        {
          @Override public void onProgressChanged(android.widget.SeekBar bar,
              int progress, boolean fromUser)
          {
            if (fromUser)
            {
              overlay.edit().putFloat("reader_ai_overlay_opacity",
                  0.2f + progress / 100f).apply();
              updateOpacityLabel.run();
              refreshPreview.run();
            }
          }
          @Override public void onStartTrackingTouch(android.widget.SeekBar b) {}
          @Override public void onStopTrackingTouch(android.widget.SeekBar b) {}
        });
    list.addView(opacity);

    CheckBox round = new CheckBox(activity);
    round.setText(R.string.pref_reader_ai_overlay_round);
    round.setChecked(overlay.getBoolean("reader_ai_overlay_shape", true));
    round.setOnCheckedChangeListener((_view, checked) -> {
      overlay.edit().putBoolean("reader_ai_overlay_shape", checked).apply();
      refreshPreview.run();
    });
    list.addView(round);

    list.addView(colorRow(activity, overlay, "reader_ai_overlay_color",
          R.string.pref_reader_ai_overlay_button_color, 0xFF303030,
          refreshPreview));
    list.addView(colorRow(activity, overlay, "reader_ai_overlay_icon_color",
          R.string.pref_reader_ai_overlay_icon_color, 0xFFFFFFFF,
          refreshPreview));
  }

  private static void applyPreview(android.widget.ImageView preview,
      SharedPreferences overlay, float density)
  {
    android.graphics.drawable.GradientDrawable bg =
      new android.graphics.drawable.GradientDrawable();
    boolean round = overlay.getBoolean("reader_ai_overlay_shape", true);
    bg.setShape(round ? android.graphics.drawable.GradientDrawable.OVAL
        : android.graphics.drawable.GradientDrawable.RECTANGLE);
    if (!round)
      bg.setCornerRadius(12 * density);
    bg.setColor(overlay.getInt("reader_ai_overlay_color", 0xFF303030));
    preview.setBackground(bg);
    preview.setColorFilter(
        overlay.getInt("reader_ai_overlay_icon_color", 0xFFFFFFFF));
    preview.setAlpha(Math.max(0.2f, Math.min(1.0f,
          overlay.getFloat("reader_ai_overlay_opacity", 0.5f))));
  }

  private static View colorRow(Activity activity, SharedPreferences overlay,
      String key, int labelRes, int defaultColor, Runnable refreshPreview)
  {
    float density = activity.getResources().getDisplayMetrics().density;
    LinearLayout col = new LinearLayout(activity);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(0, (int)(6 * density), 0, (int)(6 * density));
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    TextView label = new TextView(activity);
    label.setText(labelRes);
    label.setTextSize(14);
    row.addView(label, new LinearLayout.LayoutParams(0,
          LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    android.widget.EditText hex = new android.widget.EditText(activity);
    int[] colors = {0xFF303030, 0xFF1A73E8, 0xFF00897B, 0xFF6A1B9A,
        0xFFC62828, 0xFFFFFFFF, 0xFF000000};
    for (int color : colors)
    {
      View swatch = new View(activity);
      android.graphics.drawable.GradientDrawable dot =
        new android.graphics.drawable.GradientDrawable();
      dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
      dot.setColor(color);
      dot.setStroke((int)(1 * density), 0xFF888888);
      swatch.setBackground(dot);
      int size = (int)(28 * density);
      LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(size, size);
      params.setMarginStart((int)(6 * density));
      swatch.setLayoutParams(params);
      swatch.setOnClickListener(_view -> {
        overlay.edit().putInt(key, color).apply();
        hex.setText(String.format("#%08X", color));
        refreshPreview.run();
      });
      row.addView(swatch);
    }
    col.addView(row);
    hex.setHint(R.string.pref_reader_ai_overlay_hex_hint);
    hex.setTextSize(14);
    hex.setSingleLine();
    hex.setText(String.format("#%08X", overlay.getInt(key, defaultColor)));
    hex.setOnFocusChangeListener((_view, focused) -> {
      if (focused)
        return;
      try
      {
        overlay.edit().putInt(key,
            (int)Long.parseLong(hex.getText().toString().trim()
              .replaceFirst("^#", ""), 16)).apply();
        refreshPreview.run();
      }
      catch (Exception ignored) {}
    });
    col.addView(hex);
    return col;
  }


  private static View sectorRow(Activity activity, SharedPreferences prefs,
      ReaderAiAction.Sector sector, List<TextView> values)
  {
    float density = activity.getResources().getDisplayMetrics().density;
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setMinimumHeight((int)(52 * density));
    row.setPadding((int)(12 * density), 0, (int)(12 * density), 0);
    android.util.TypedValue ripple = new android.util.TypedValue();
    activity.getTheme().resolveAttribute(
        android.R.attr.selectableItemBackground, ripple, true);
    row.setBackgroundResource(ripple.resourceId);
    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
    rowParams.bottomMargin = (int)(4 * density);
    row.setLayoutParams(rowParams);

    TextView label = new TextView(activity);
    label.setText(sector.labelRes);
    label.setTextSize(16);
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    row.addView(label, labelParams);

    TextView value = new TextView(activity);
    value.setText(ReaderAiAction.actionFor(prefs, sector).labelRes);
    value.setTextSize(16);
    value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
    android.util.TypedValue accent = new android.util.TypedValue();
    activity.getTheme().resolveAttribute(android.R.attr.colorAccent,
        accent, true);
    value.setTextColor(accent.data);
    values.add(value);
    row.addView(value);

    row.setOnClickListener(_view -> pickAction(activity, prefs, sector, value));
    return row;
  }

  private static void pickAction(Activity activity, SharedPreferences prefs,
      ReaderAiAction.Sector sector, TextView value)
  {
    ReaderAiAction[] actions = ReaderAiAction.values();
    String[] labels = new String[actions.length];
    int checked = 0;
    ReaderAiAction current = ReaderAiAction.actionFor(prefs, sector);
    for (int i = 0; i < actions.length; i++)
    {
      labels[i] = activity.getString(actions[i].labelRes);
      if (actions[i] == current)
        checked = i;
    }
    new AlertDialog.Builder(activity)
      .setTitle(sector.labelRes)
      .setSingleChoiceItems(labels, checked, (dialog, which) -> {
        ReaderAiAction.setAction(prefs, sector, actions[which]);
        value.setText(actions[which].labelRes);
        dialog.dismiss();
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }
}
