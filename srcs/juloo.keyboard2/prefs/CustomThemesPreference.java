package juloo.keyboard2.prefs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.SeekBar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import juloo.keyboard2.R;
import org.json.JSONException;
import org.json.JSONObject;

public class CustomThemesPreference extends ListGroupPreference<CustomThemesPreference.CustomTheme>
{
  public static final String KEY = "custom_themes";
  public static final String SELECTED_INDEX_KEY = "custom_theme_index";
  public static final String THEME_VALUE = "custom";

  static final List<CustomTheme> DEFAULT = Collections.singletonList(
      new CustomTheme("My Custom Theme", 0xff14201a, 0xff243a2e,
        0xff1d3026, 0xff1a2b22, 0xff15241c, 0xff203529,
        0xff18291f, 0xfff4fff7, 0xffc4d8ca));
  static final ListGroupPreference.Serializer<CustomTheme> SERIALIZER =
    new Serializer();

  public CustomThemesPreference(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setKey(KEY);
    setTitle(R.string.pref_custom_themes_title);
  }

  public static CustomTheme load_selected(SharedPreferences prefs)
  {
    List<CustomTheme> themes = load_from_preferences(KEY, prefs, DEFAULT, SERIALIZER);
    if (themes == null || themes.size() == 0)
      themes = DEFAULT;
    int index = prefs.getInt(SELECTED_INDEX_KEY, 0);
    if (index < 0 || index >= themes.size())
      index = 0;
    return themes.get(index);
  }

  @Override
  protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
  {
    super.onSetInitialValue(restoreValue, defaultValue);
    if (_values.size() == 0)
      set_values(new ArrayList<CustomTheme>(DEFAULT), false);
  }

  @Override
  String label_of_value(CustomTheme value, int i)
  {
    return getContext().getString(R.string.pref_custom_themes_item, i + 1,
        value.name);
  }

  @Override
  AddButton on_attach_add_button(AddButton prev_btn)
  {
    if (prev_btn == null)
      return new CustomThemesAddButton(getContext());
    return prev_btn;
  }

  @Override
  boolean should_allow_remove_item(CustomTheme _value)
  {
    return _values.size() > 1;
  }

  @Override
  void add_item(CustomTheme value)
  {
    super.add_item(value);
    select_index(_values.size() - 1);
  }

  @Override
  void change_item(int i, CustomTheme value)
  {
    super.change_item(i, value);
    select_index(i);
  }

  @Override
  void remove_item(int i)
  {
    super.remove_item(i);
    int selected = getSharedPreferences().getInt(SELECTED_INDEX_KEY, 0);
    if (selected >= _values.size())
      selected = Math.max(0, _values.size() - 1);
    select_index(selected);
  }

  @Override
  ListGroupPreference.Serializer<CustomTheme> get_serializer() { return SERIALIZER; }

  @Override
  void select(final SelectionCallback<CustomTheme> callback, CustomTheme old_value)
  {
    final CustomTheme initial = old_value == null
      ? new CustomTheme("Custom Theme " + (_values.size() + 1), 0xff181a1f,
          0xff30343b, 0xff272b31, 0xff242830, 0xff1f232a,
          0xff2d3138, 0xff23272e, 0xfff7f8fa, 0xffcbd0d8)
      : old_value;
    final ThemeFields fields = new ThemeFields(getContext(), initial);
    new AlertDialog.Builder(getContext())
      .setTitle(old_value == null
          ? R.string.pref_custom_themes_add
          : R.string.pref_custom_themes_edit)
      .setView(fields.view)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface _dialog, int _which)
        {
          callback.select(fields.theme());
        }
      })
      .show();
  }

  void select_index(int index)
  {
    if (index < 0)
      index = 0;
    SharedPreferences.Editor editor = getSharedPreferences().edit();
    editor.putString("theme", THEME_VALUE);
    editor.putInt(SELECTED_INDEX_KEY, index);
    editor.apply();
  }

  static int parseColor(String value, int fallback)
  {
    if (value == null)
      return fallback;
    String v = value.trim();
    if (v.length() == 0)
      return fallback;
    if (!v.startsWith("#"))
      v = "#" + v;
    if (!v.matches("#[0-9a-fA-F]{6}"))
      return fallback;
    return (int)(0xff000000L | Long.parseLong(v.substring(1), 16));
  }

  public static String toHex(int color)
  {
    return String.format("#%06X", color & 0x00ffffff);
  }

  static class ThemeFields
  {
    final ScrollView view;
    final EditText name;
    final ColorField keyboard;
    final ColorField key;
    final ColorField keyAlternate;
    final ColorField action;
    final ColorField actionAlternate;
    final ColorField space;
    final ColorField spaceAlternate;
    final ColorField label;
    final ColorField subLabel;

    ThemeFields(Context context, CustomTheme initial)
    {
      LinearLayout list = new LinearLayout(context);
      list.setOrientation(LinearLayout.VERTICAL);
      int pad = dp(context, 20);
      list.setPadding(pad, dp(context, 12), pad, dp(context, 8));
      name = addField(context, list, R.string.pref_custom_theme_name, initial.name);
      keyboard = addColorField(context, list, R.string.pref_custom_theme_keyboard,
          initial.colorKeyboard);
      key = addColorField(context, list, R.string.pref_custom_theme_key,
          initial.colorKey);
      keyAlternate = addColorField(context, list, R.string.pref_custom_theme_key_alternate,
          initial.colorKeyAlternate);
      action = addColorField(context, list, R.string.pref_custom_theme_action,
          initial.colorKeyAction);
      actionAlternate = addColorField(context, list,
          R.string.pref_custom_theme_action_alternate, initial.colorKeyActionAlternate);
      space = addColorField(context, list, R.string.pref_custom_theme_space,
          initial.colorKeySpaceBar);
      spaceAlternate = addColorField(context, list,
          R.string.pref_custom_theme_space_alternate, initial.colorKeySpaceBarAlternate);
      label = addColorField(context, list, R.string.pref_custom_theme_label,
          initial.colorLabel);
      subLabel = addColorField(context, list, R.string.pref_custom_theme_sub_label,
          initial.colorSubLabel);
      view = new ScrollView(context);
      view.addView(list);
    }

    CustomTheme theme()
    {
      String title = name.getText().toString().trim();
      if (title.length() == 0)
        title = "Custom Theme";
      return new CustomTheme(title,
          keyboard.color(0xff181a1f),
          key.color(0xff30343b),
          keyAlternate.color(0xff272b31),
          action.color(0xff242830),
          actionAlternate.color(0xff1f232a),
          space.color(0xff2d3138),
          spaceAlternate.color(0xff23272e),
          label.color(0xfff7f8fa),
          subLabel.color(0xffcbd0d8));
    }

    static EditText addField(Context context, LinearLayout list, int titleId,
        String value)
    {
      TextView title = new TextView(context);
      title.setText(titleId);
      title.setPadding(0, dp(context, 10), 0, 0);
      list.addView(title);
      EditText input = new EditText(context);
      input.setSingleLine(true);
      input.setText(value);
      input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
      list.addView(input);
      return input;
    }

    static ColorField addColorField(Context context, LinearLayout list,
        int titleId, int value)
    {
      ColorField field = new ColorField(context, titleId, value);
      list.addView(field.view);
      return field;
    }

    static int dp(Context context, int value)
    {
      return (int)(value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class ColorField implements SeekBar.OnSeekBarChangeListener,
      TextWatcher
    {
      final LinearLayout view;
      final View swatch;
      final EditText hex;
      final SeekBar[] bars = new SeekBar[3];
      final TextView[] labels = new TextView[3];
      private int _color;
      private boolean _updating;

      ColorField(Context context, int titleId, int color)
      {
        _color = color;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(0, dp(context, 12), 0, dp(context, 8));

        LinearLayout preview = new LinearLayout(context);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setGravity(Gravity.CENTER_VERTICAL);

        swatch = new View(context);
        LinearLayout.LayoutParams swatchParams =
          new LinearLayout.LayoutParams(dp(context, 64), dp(context, 64));
        swatchParams.setMargins(0, 0, dp(context, 16), 0);
        preview.addView(swatch, swatchParams);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(titleId);
        text.addView(title);

        hex = new EditText(context);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[] { new InputFilter.LengthFilter(7) });
        hex.setSelectAllOnFocus(true);
        hex.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        hex.addTextChangedListener(this);
        text.addView(hex, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT));

        preview.addView(text, new LinearLayout.LayoutParams(0,
              LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        view.addView(preview);

        addSliderRow(context, "Red", 0);
        addSliderRow(context, "Green", 1);
        addSliderRow(context, "Blue", 2);
        syncControlsFromColor();
        updatePreview();
      }

      public void onProgressChanged(SeekBar seekBar, int progress,
          boolean fromUser)
      {
        if (_updating)
          return;
        _color = 0xff000000
          | (bars[0].getProgress() << 16)
          | (bars[1].getProgress() << 8)
          | bars[2].getProgress();
        syncHexFromColor();
        updatePreview();
      }

      public void onStartTrackingTouch(SeekBar seekBar) {}

      public void onStopTrackingTouch(SeekBar seekBar) {}

      public void beforeTextChanged(CharSequence s, int start, int count,
          int after) {}

      public void onTextChanged(CharSequence s, int start, int before,
          int count) {}

      public void afterTextChanged(Editable editable)
      {
        if (_updating)
          return;
        int parsed = parseColor(editable.toString(), _color);
        if (parsed == _color)
          return;
        _color = parsed;
        syncSlidersFromColor();
        updatePreview();
      }

      int color(int fallback)
      {
        return parseColor(hex.getText().toString(), fallback);
      }

      private void addSliderRow(Context context, String label, int index)
      {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 8), 0, 0);

        labels[index] = new TextView(context);
        row.addView(labels[index], new LinearLayout.LayoutParams(
              dp(context, 88), LinearLayout.LayoutParams.WRAP_CONTENT));

        bars[index] = new SeekBar(context);
        bars[index].setMax(255);
        bars[index].setOnSeekBarChangeListener(this);
        row.addView(bars[index], new LinearLayout.LayoutParams(0,
              LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        view.addView(row);
        labels[index].setText(label);
      }

      private void syncControlsFromColor()
      {
        syncHexFromColor();
        syncSlidersFromColor();
      }

      private void syncHexFromColor()
      {
        _updating = true;
        hex.setText(toHex(_color));
        hex.setSelection(hex.getText().length());
        _updating = false;
      }

      private void syncSlidersFromColor()
      {
        _updating = true;
        bars[0].setProgress((_color >> 16) & 0xff);
        bars[1].setProgress((_color >> 8) & 0xff);
        bars[2].setProgress(_color & 0xff);
        _updating = false;
      }

      private void updatePreview()
      {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(_color);
        background.setStroke(dp(swatch.getContext(), 2), 0xff777777);
        swatch.setBackground(background);

        labels[0].setText("Red " + ((_color >> 16) & 0xff));
        labels[1].setText("Green " + ((_color >> 8) & 0xff));
        labels[2].setText("Blue " + (_color & 0xff));
      }
    }
  }

  class CustomThemesAddButton extends AddButton
  {
    public CustomThemesAddButton(Context ctx)
    {
      super(ctx);
      setLayoutResource(R.layout.pref_custom_themes_add_btn);
    }
  }

  public static class CustomTheme
  {
    public final String name;
    public final int colorKeyboard;
    public final int colorKey;
    public final int colorKeyAlternate;
    public final int colorKeyAction;
    public final int colorKeyActionAlternate;
    public final int colorKeySpaceBar;
    public final int colorKeySpaceBarAlternate;
    public final int colorLabel;
    public final int colorSubLabel;

    public CustomTheme(String name, int colorKeyboard, int colorKey,
        int colorKeyAlternate, int colorKeyAction, int colorKeyActionAlternate,
        int colorKeySpaceBar, int colorKeySpaceBarAlternate, int colorLabel,
        int colorSubLabel)
    {
      this.name = name;
      this.colorKeyboard = colorKeyboard;
      this.colorKey = colorKey;
      this.colorKeyAlternate = colorKeyAlternate;
      this.colorKeyAction = colorKeyAction;
      this.colorKeyActionAlternate = colorKeyActionAlternate;
      this.colorKeySpaceBar = colorKeySpaceBar;
      this.colorKeySpaceBarAlternate = colorKeySpaceBarAlternate;
      this.colorLabel = colorLabel;
      this.colorSubLabel = colorSubLabel;
    }
  }

  static class Serializer implements ListGroupPreference.Serializer<CustomTheme>
  {
    public CustomTheme load_item(Object obj) throws JSONException
    {
      JSONObject json = (JSONObject)obj;
      return new CustomTheme(
          json.optString("name", "Custom Theme"),
          parseColor(json.optString("keyboard", null), 0xff181a1f),
          parseColor(json.optString("key", null), 0xff30343b),
          parseColor(json.optString("keyAlternate", null), 0xff272b31),
          parseColor(json.optString("action", null), 0xff242830),
          parseColor(json.optString("actionAlternate", null), 0xff1f232a),
          parseColor(json.optString("space", null), 0xff2d3138),
          parseColor(json.optString("spaceAlternate", null), 0xff23272e),
          parseColor(json.optString("label", null), 0xfff7f8fa),
          parseColor(json.optString("subLabel", null), 0xffcbd0d8));
    }

    public Object save_item(CustomTheme theme) throws JSONException
    {
      JSONObject json = new JSONObject();
      json.put("name", theme.name);
      json.put("keyboard", toHex(theme.colorKeyboard));
      json.put("key", toHex(theme.colorKey));
      json.put("keyAlternate", toHex(theme.colorKeyAlternate));
      json.put("action", toHex(theme.colorKeyAction));
      json.put("actionAlternate", toHex(theme.colorKeyActionAlternate));
      json.put("space", toHex(theme.colorKeySpaceBar));
      json.put("spaceAlternate", toHex(theme.colorKeySpaceBarAlternate));
      json.put("label", toHex(theme.colorLabel));
      json.put("subLabel", toHex(theme.colorSubLabel));
      return json;
    }
  }
}
