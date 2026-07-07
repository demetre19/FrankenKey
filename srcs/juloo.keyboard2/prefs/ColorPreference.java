package juloo.keyboard2.prefs;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/*
 ** ColorPreference
 ** -
 ** Open a compact RGB color picker with a small swatch preview.
 ** -
 ** xml attrs:
 **   android:defaultValue  Persisted #RRGGBB value, or empty for theme default
 **   previewColor          Color shown while using the selected theme default
 ** -
 ** Summary field allows showing the current value using %s.
 */
public class ColorPreference extends DialogPreference
  implements SeekBar.OnSeekBarChangeListener
{
  private final LinearLayout _layout;
  private final TextView _textView;
  private final View _chip;
  private final CheckBox _themeDefault;
  private final SeekBar[] _bars = new SeekBar[3];
  private final TextView[] _labels = new TextView[3];
  private final int _fallbackColor;
  private final String _initialSummary;
  private Integer _value;
  private boolean _updating;

  public ColorPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    CharSequence summary = getSummary();
    _initialSummary = summary == null ? "%s" : summary.toString();
    Integer fallback = parseColor(attrs.getAttributeValue(null, "previewColor"));
    _fallbackColor = fallback == null ? 0xff888888 : fallback;

    _layout = new LinearLayout(context);
    _layout.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(24);
    _layout.setPadding(pad, dp(16), pad, dp(8));

    LinearLayout preview = new LinearLayout(context);
    preview.setOrientation(LinearLayout.HORIZONTAL);
    preview.setGravity(android.view.Gravity.CENTER_VERTICAL);
    _chip = new View(context);
    preview.addView(_chip, new LinearLayout.LayoutParams(dp(22), dp(22)));
    _textView = new TextView(context);
    _textView.setPadding(dp(12), 0, 0, 0);
    preview.addView(_textView, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT));
    _layout.addView(preview);

    _themeDefault = new CheckBox(context);
    _themeDefault.setText("Use selected theme color");
    _themeDefault.setPadding(0, dp(8), 0, dp(4));
    _themeDefault.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(CompoundButton button, boolean checked)
      {
        setBarsEnabled(!checked);
        if (checked)
          _value = null;
        else if (_value == null)
          _value = _fallbackColor;
        syncBarsFromValue();
        updateText();
      }
    });
    _layout.addView(_themeDefault);

    addColorRow(context, "Red", 0);
    addColorRow(context, "Green", 1);
    addColorRow(context, "Blue", 2);
    setValue(null);
  }

  @Override
  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
  {
    if (_updating || _themeDefault.isChecked())
      return;
    _value = 0xff000000
      | (_bars[0].getProgress() << 16)
      | (_bars[1].getProgress() << 8)
      | _bars[2].getProgress();
    updateText();
  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar)
  {
  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar)
  {
  }

  @Override
  protected void onSetInitialValue(boolean restorePersistedValue,
      Object defaultValue)
  {
    String value = restorePersistedValue
      ? getPersistedString(defaultValue == null ? "" : defaultValue.toString())
      : (defaultValue == null ? "" : defaultValue.toString());
    if (!restorePersistedValue)
      persistString(value);
    setValue(parseColor(value));
  }

  @Override
  protected Object onGetDefaultValue(TypedArray a, int index)
  {
    return a.getString(index);
  }

  @Override
  protected void onDialogClosed(boolean positiveResult)
  {
    if (positiveResult)
      persistString(_themeDefault.isChecked() || _value == null ? "" : toHex(_value));
    else
      setValue(parseColor(getPersistedString("")));
    updateText();
  }

  @Override
  protected View onCreateDialogView()
  {
    setValue(parseColor(getPersistedString("")));
    ViewGroup parent = (ViewGroup)_layout.getParent();
    if (parent != null)
      parent.removeView(_layout);
    return _layout;
  }

  private void addColorRow(Context context, String label, int index)
  {
    _labels[index] = new TextView(context);
    _labels[index].setPadding(0, dp(10), 0, 0);
    _layout.addView(_labels[index]);
    _bars[index] = new SeekBar(context);
    _bars[index].setMax(255);
    _bars[index].setOnSeekBarChangeListener(this);
    _layout.addView(_bars[index]);
    _labels[index].setText(label);
  }

  private void setValue(Integer value)
  {
    _value = value;
    _themeDefault.setChecked(value == null);
    setBarsEnabled(value != null);
    syncBarsFromValue();
    updateText();
  }

  private void setBarsEnabled(boolean enabled)
  {
    for (int i = 0; i < _bars.length; i++)
    {
      _bars[i].setEnabled(enabled);
      _labels[i].setEnabled(enabled);
    }
  }

  private void syncBarsFromValue()
  {
    int color = _value == null ? _fallbackColor : _value;
    _updating = true;
    _bars[0].setProgress((color >> 16) & 0xff);
    _bars[1].setProgress((color >> 8) & 0xff);
    _bars[2].setProgress(color & 0xff);
    _updating = false;
  }

  private void updateText()
  {
    String value = _value == null ? "Theme default" : toHex(_value);
    _textView.setText(value);
    setSummary(String.format(_initialSummary, value));

    int color = _value == null ? _fallbackColor : _value;
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(color);
    bg.setStroke(dp(1), 0xff777777);
    _chip.setBackground(bg);

    _labels[0].setText("Red " + ((color >> 16) & 0xff));
    _labels[1].setText("Green " + ((color >> 8) & 0xff));
    _labels[2].setText("Blue " + (color & 0xff));
  }

  private int dp(int value)
  {
    return (int)(value * getContext().getResources().getDisplayMetrics().density + 0.5f);
  }

  private static Integer parseColor(String value)
  {
    if (value == null)
      return null;
    String v = value.trim();
    if (v.length() == 0)
      return null;
    if (!v.startsWith("#"))
      v = "#" + v;
    if (!v.matches("#[0-9a-fA-F]{6}"))
      return null;
    return (int)(0xff000000L | Long.parseLong(v.substring(1), 16));
  }

  private static String toHex(int color)
  {
    return String.format("#%06X", color & 0x00ffffff);
  }
}
