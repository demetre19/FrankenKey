package juloo.keyboard2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import juloo.keyboard2.prefs.CustomThemesPreference;

public class Theme
{
  // Key colors
  public final int colorKey;
  public final int colorKeyActivated;
  public final int colorKeyAction;
  public final int colorKeySpaceBar;
  public final int colorKeyAlternate;
  public final int colorKeyActionAlternate;
  public final int colorKeySpaceBarAlternate;
  public final int colorKeyboard;

  // Label colors
  public final int lockedColor;
  public final int activatedColor;
  public final int pressedColor;
  public final int labelColor;
  public final int subLabelColor;
  public final int secondaryLabelColor;
  public final int greyedLabelColor;

  // Key borders
  public final float keyBorderRadius;
  public final float keyBorderWidth;
  public final float keyBorderWidthActivated;
  public final float keyBorderWidthAction;
  public final float keyBorderWidthSpaceBar;
  public final int keyBorderColorLeft;
  public final int keyBorderColorTop;
  public final int keyBorderColorRight;
  public final int keyBorderColorBottom;

  public final int colorNavBar;
  public final boolean isLightNavBar;


  public Theme(Context context, AttributeSet attrs)
  {
    getKeyFont(context); // _key_font will be accessed
    TypedArray s = context.getTheme().obtainStyledAttributes(attrs, R.styleable.keyboard, 0, 0);
    int colorKey_ = s.getColor(R.styleable.keyboard_colorKey, 0);
    int colorKeyAction_ = s.getColor(R.styleable.keyboard_colorKeyAction, colorKey_);
    int colorKeySpaceBar_ = s.getColor(R.styleable.keyboard_colorKeySpaceBar, colorKey_);
    int colorKeyAlternate_ = s.getColor(R.styleable.keyboard_colorKeyAlternate, colorKey_);
    int colorKeyActionAlternate_ = s.getColor(R.styleable.keyboard_colorKeyActionAlternate, colorKeyAction_);
    int colorKeySpaceBarAlternate_ = s.getColor(R.styleable.keyboard_colorKeySpaceBarAlternate, colorKeySpaceBar_);
    int colorKeyActivated_ = s.getColor(R.styleable.keyboard_colorKeyActivated, 0);
    int colorKeyboard_ = s.getColor(R.styleable.keyboard_colorKeyboard, 0);
    int colorNavBar_ = s.getColor(R.styleable.keyboard_navigationBarColor, 0);
    boolean isLightNavBar_ = s.getBoolean(R.styleable.keyboard_windowLightNavigationBar, false);
    int labelColor_ = s.getColor(R.styleable.keyboard_colorLabel, 0);
    int activatedColor_ = s.getColor(R.styleable.keyboard_colorLabelActivated, 0);
    int pressedColor_ = s.getColor(R.styleable.keyboard_colorLabelPressed, labelColor_);
    int lockedColor_ = s.getColor(R.styleable.keyboard_colorLabelLocked, 0);
    int subLabelColor_ = s.getColor(R.styleable.keyboard_colorSubLabel, 0);
    float secondaryDimming = s.getFloat(R.styleable.keyboard_secondaryDimming, 0.25f);
    float greyedDimming = s.getFloat(R.styleable.keyboard_greyedDimming, 0.5f);
    float keyBorderRadius_ = s.getDimension(R.styleable.keyboard_keyBorderRadius, 0);
    float keyBorderWidth_ = s.getDimension(R.styleable.keyboard_keyBorderWidth, 0);
    float keyBorderWidthActivated_ = s.getDimension(R.styleable.keyboard_keyBorderWidthActivated, 0);
    float keyBorderWidthAction_ = s.getDimension(R.styleable.keyboard_keyBorderWidthAction, 0);
    float keyBorderWidthSpaceBar_ = s.getDimension(R.styleable.keyboard_keyBorderWidthSpaceBar, 0);
    int keyBorderColorLeft_ = s.getColor(R.styleable.keyboard_keyBorderColorLeft, colorKey_);
    int keyBorderColorTop_ = s.getColor(R.styleable.keyboard_keyBorderColorTop, colorKey_);
    int keyBorderColorRight_ = s.getColor(R.styleable.keyboard_keyBorderColorRight, colorKey_);
    int keyBorderColorBottom_ = s.getColor(R.styleable.keyboard_keyBorderColorBottom, colorKey_);
    s.recycle();

    if (isCustomTheme(context))
    {
      CustomThemesPreference.CustomTheme custom =
        CustomThemesPreference.load_selected(
            DirectBootAwarePreferences.get_shared_preferences(context));
      colorKeyboard_ = custom.colorKeyboard;
      colorKey_ = custom.colorKey;
      colorKeyAlternate_ = custom.colorKeyAlternate;
      colorKeyAction_ = custom.colorKeyAction;
      colorKeyActionAlternate_ = custom.colorKeyActionAlternate;
      colorKeySpaceBar_ = custom.colorKeySpaceBar;
      colorKeySpaceBarAlternate_ = custom.colorKeySpaceBarAlternate;
      colorKeyActivated_ = adjustLight(custom.colorKey, 0.35f);
      colorNavBar_ = custom.colorKeyboard;
      isLightNavBar_ = contrastTextColor(custom.colorKeyboard) == 0xff000000;
      labelColor_ = custom.colorLabel;
      activatedColor_ = adjustLight(custom.colorLabel, 0.35f);
      pressedColor_ = custom.colorLabel;
      lockedColor_ = adjustLight(custom.colorLabel, 0.45f);
      subLabelColor_ = custom.colorSubLabel;
      keyBorderColorLeft_ = custom.colorKeyAlternate;
      keyBorderColorTop_ = custom.colorKey;
      keyBorderColorRight_ = custom.colorKeyAlternate;
      keyBorderColorBottom_ = custom.colorKeyAlternate;
    }

    colorKey = colorKey_;
    colorKeyAction = colorKeyAction_;
    colorKeySpaceBar = colorKeySpaceBar_;
    colorKeyAlternate = colorKeyAlternate_;
    colorKeyActionAlternate = colorKeyActionAlternate_;
    colorKeySpaceBarAlternate = colorKeySpaceBarAlternate_;
    colorKeyActivated = colorKeyActivated_;
    colorKeyboard = colorKeyboard_;
    colorNavBar = colorNavBar_;
    isLightNavBar = isLightNavBar_;
    labelColor = labelColor_;
    activatedColor = activatedColor_;
    pressedColor = pressedColor_;
    lockedColor = lockedColor_;
    subLabelColor = subLabelColor_;
    secondaryLabelColor = adjustLight(labelColor_, secondaryDimming);
    greyedLabelColor = adjustLight(labelColor_, greyedDimming);
    keyBorderRadius = keyBorderRadius_;
    keyBorderWidth = keyBorderWidth_;
    keyBorderWidthActivated = keyBorderWidthActivated_;
    keyBorderWidthAction = keyBorderWidthAction_;
    keyBorderWidthSpaceBar = keyBorderWidthSpaceBar_;
    keyBorderColorLeft = keyBorderColorLeft_;
    keyBorderColorTop = keyBorderColorTop_;
    keyBorderColorRight = keyBorderColorRight_;
    keyBorderColorBottom = keyBorderColorBottom_;
  }

  private static boolean isCustomTheme(Context context)
  {
    try
    {
      return CustomThemesPreference.THEME_VALUE.equals(
          DirectBootAwarePreferences.get_shared_preferences(context)
            .getString("theme", "system"));
    }
    catch (Exception _e)
    {
      return false;
    }
  }

  /** Interpolate the 'value' component toward its opposite by 'alpha'. */
  static int adjustLight(int color, float alpha)
  {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    float v = hsv[2];
    hsv[2] = alpha - (2 * alpha - 1) * v;
    return Color.HSVToColor(hsv);
  }

  static int ensureTextContrast(int color, int background)
  {
    return contrastRatio(color, background) >= 4.5
      ? color : contrastTextColor(background);
  }

  static int contrastTextColor(int background)
  {
    return contrastRatio(0xffffffff, background) >=
      contrastRatio(0xff000000, background) ? 0xffffffff : 0xff000000;
  }

  static double contrastRatio(int foreground, int background)
  {
    double foregroundLuminance = relativeLuminance(foreground);
    double backgroundLuminance = relativeLuminance(background);
    double lighter = Math.max(foregroundLuminance, backgroundLuminance);
    double darker = Math.min(foregroundLuminance, backgroundLuminance);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static double relativeLuminance(int color)
  {
    double red = linearColor((color >> 16) & 0xff);
    double green = linearColor((color >> 8) & 0xff);
    double blue = linearColor(color & 0xff);
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  }

  private static double linearColor(int channel)
  {
    double value = channel / 255.0;
    if (value <= 0.03928)
      return value / 12.92;
    return Math.pow((value + 0.055) / 1.055, 2.4);
  }

  Paint initIndicationPaint(Paint.Align align, Typeface font)
  {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setTextAlign(align);
    if (font != null)
      paint.setTypeface(font);
    return (paint);
  }

  static Typeface _key_font = null;

  static public Typeface getKeyFont(Context context)
  {
    if (_key_font == null)
      _key_font = Typeface.createFromAsset(context.getAssets(), "special_font.ttf");
    return _key_font;
  }

  public static final class Computed
  {
    public final float vertical_margin;
    public final float horizontal_margin;
    public final float margin_top;
    public final float margin_left;
    public final float row_height;
    public final Paint indication_paint;

    public final Key key;
    public final Key key_activated;
    public final Key key_action;
    public final Key key_space_bar;
    public final Key key_alternate;
    public final Key key_action_alternate;
    public final Key key_space_bar_alternate;
    public final Key key_suggestion;

    public Computed(Theme theme, Config config, float keyWidth, KeyboardData layout)
    {
      // Make sure that the layout isn't higher than the screen. Take the
      // height of the candidates view into account.
      row_height = Math.min(config.keyboard_rows_height_pixels,
          (config.screenHeightPixels - config.keyboard_rows_height_pixels) / layout.keysHeight);
      vertical_margin = config.key_vertical_margin * row_height;
      horizontal_margin = config.key_horizontal_margin * keyWidth;
      // Add half of the key margin on the left and on the top as it's also
      // added on the right and on the bottom of every keys.
      margin_top = config.marginTop + vertical_margin / 2;
      margin_left = horizontal_margin / 2;
      key = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Normal, false);
      key_alternate = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Normal, true);
      key_action = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Action, false);
      key_action_alternate = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Action, true);
      key_space_bar = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Space_bar, false);
      key_space_bar_alternate = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Space_bar, true);
      key_activated = new Key(theme, config, keyWidth, true, KeyboardData.Key.Role.Normal, false);
      key_suggestion = new Key(theme, config, keyWidth, false, KeyboardData.Key.Role.Suggestion, false);
      indication_paint = init_label_paint(config, null);
      indication_paint.setColor(theme.subLabelColor);
    }

    public static final class Key
    {
      public final Paint bg_paint = new Paint();
      public final Paint border_left_paint;
      public final Paint border_top_paint;
      public final Paint border_right_paint;
      public final Paint border_bottom_paint;
      public final float border_width;
      public final float border_radius;
      final Paint _label_paint;
      final Paint _special_label_paint;
      final Paint _sublabel_paint;
      final Paint _special_sublabel_paint;
      final int _label_alpha_bits;
      public final int labelColor;
      public final int subLabelColor;
      public final int secondaryLabelColor;
      public final int greyedLabelColor;
      public final int pressedColor;
      public final int activatedColor;
      public final int lockedColor;


      public Key(Theme theme, Config config, float keyWidth, boolean activated,
          KeyboardData.Key.Role role, boolean alternate)
      {
        border_radius = config.borderConfig ? config.customBorderRadius * keyWidth : theme.keyBorderRadius;
        int bg_color;
        if (activated)
        {
          bg_color = theme.colorKeyActivated;
          border_width = theme.keyBorderWidthActivated;
          bg_paint.setAlpha(config.keyActivatedOpacity);
        }
        else
        {
          switch (role)
          {
            case Action:
              bg_color = alternate ? theme.colorKeyActionAlternate : theme.colorKeyAction;
              border_width = theme.keyBorderWidthAction;
              break;
            case Space_bar:
              bg_color = alternate ? theme.colorKeySpaceBarAlternate : theme.colorKeySpaceBar;
              border_width = theme.keyBorderWidthSpaceBar;
              break;
            case Suggestion:
              bg_color = 0;
              border_width = 0;
              break;
            default:
            case Normal:
              bg_color = alternate ? theme.colorKeyAlternate : theme.colorKey;
              border_width = config.borderConfig ? config.customBorderLineWidth : theme.keyBorderWidth;
              break;
          }
          bg_paint.setAlpha(config.keyOpacity);
        }
        labelColor = Theme.ensureTextContrast(theme.labelColor, bg_color);
        subLabelColor = Theme.ensureTextContrast(theme.subLabelColor, bg_color);
        secondaryLabelColor = Theme.ensureTextContrast(theme.secondaryLabelColor, bg_color);
        greyedLabelColor = Theme.ensureTextContrast(theme.greyedLabelColor, bg_color);
        pressedColor = Theme.ensureTextContrast(theme.pressedColor, bg_color);
        activatedColor = Theme.ensureTextContrast(theme.activatedColor, bg_color);
        lockedColor = Theme.ensureTextContrast(theme.lockedColor, bg_color);
        bg_paint.setColor(bg_color);
        border_left_paint = init_border_paint(config, border_width, theme.keyBorderColorLeft);
        border_top_paint = init_border_paint(config, border_width, theme.keyBorderColorTop);
        border_right_paint = init_border_paint(config, border_width, theme.keyBorderColorRight);
        border_bottom_paint = init_border_paint(config, border_width, theme.keyBorderColorBottom);
        _label_paint = init_label_paint(config, null);
        _special_label_paint = init_label_paint(config, _key_font);
        _sublabel_paint = init_label_paint(config, null);
        _special_sublabel_paint = init_label_paint(config, _key_font);
        _label_alpha_bits = (config.labelBrightness & 0xFF) << 24;
      }

      public Paint label_paint(boolean special_font, int color, float text_size)
      {
        Paint p = special_font ? _special_label_paint : _label_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        return p;
      }

      public Paint sublabel_paint(boolean special_font, int color, float text_size, Paint.Align align)
      {
        Paint p = special_font ? _special_sublabel_paint : _sublabel_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        p.setTextAlign(align);
        return p;
      }
    }

    static Paint init_border_paint(Config config, float border_width, int color)
    {
      Paint p = new Paint();
      p.setAlpha(config.keyOpacity);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(border_width);
      p.setColor(color);
      return p;
    }

    static Paint init_label_paint(Config config, Typeface font)
    {
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setTextAlign(Paint.Align.CENTER);
      if (font != null)
        p.setTypeface(font);
      return p;
    }
  }
}
