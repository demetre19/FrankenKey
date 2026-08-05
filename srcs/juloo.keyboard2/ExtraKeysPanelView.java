package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import juloo.keyboard2.ExtraKeysShortcutStore.Shortcut;

/** A configurable remote-control key panel driven by the keyboard event path. */
public final class ExtraKeysPanelView extends LinearLayout
{
  private static final KeyValue.Modifier[] MODIFIERS = {
    KeyValue.Modifier.CTRL,
    KeyValue.Modifier.ALT,
    KeyValue.Modifier.SHIFT,
    KeyValue.Modifier.META,
  };

  private static final class ModifierState
  {
    boolean active;
    boolean pressed;
    boolean wasActive;
    TextView button;
  }

  private final Map<KeyValue.Modifier, ModifierState> _modifierStates =
    new EnumMap<KeyValue.Modifier, ModifierState>(KeyValue.Modifier.class);
  private List<Shortcut> _shortcuts = ExtraKeysShortcutStore.defaults();
  private TextView _pinButton;
  private TextView _expandButton;
  private Keyboard2View _keyboard;
  private Config.IKeyEventHandler _handler;
  private boolean _expanded;
  private boolean _pinned;
  private int _labelColor;

  public ExtraKeysPanelView(Context context)
  {
    this(context, null);
  }

  public ExtraKeysPanelView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(VERTICAL);
    for (KeyValue.Modifier modifier : MODIFIERS)
      _modifierStates.put(modifier, new ModifierState());
    TypedArray colors = context.obtainStyledAttributes(new int[] {
        R.attr.colorLabel, R.attr.colorKeyboard,
    });
    _labelColor = colors.getColor(0, 0xffffffff);
    setBackgroundColor(colors.getColor(1, 0xff202124));
    colors.recycle();
    reloadShortcuts();
    rebuildPanel();
  }

  public void bind(Keyboard2View keyboard, Config.IKeyEventHandler handler)
  {
    _keyboard = keyboard;
    _handler = handler;
  }

  public void toggle()
  {
    if (getVisibility() == VISIBLE)
      hidePanel();
    else
    {
      reloadShortcuts();
      rebuildPanel();
      setVisibility(VISIBLE);
    }
  }

  public void onStartInput()
  {
    clearModifiers();
    reloadShortcuts();
    rebuildPanel();
    setVisibility(_pinned ? VISIBLE : GONE);
  }

  private void hidePanel()
  {
    setVisibility(GONE);
  }

  private void reloadShortcuts()
  {
    try
    {
      if (Config.globalConfig() != null)
        _shortcuts = ExtraKeysShortcutStore.load(Config.globalPrefs());
    }
    catch (RuntimeException _e)
    {
      _shortcuts = ExtraKeysShortcutStore.defaults();
    }
  }

  private void rebuildPanel()
  {
    removeAllViews();
    _pinButton = null;
    _expandButton = null;
    for (ModifierState state : _modifierStates.values())
      state.button = null;

    List<Shortcut> visible = new ArrayList<Shortcut>();
    for (Shortcut shortcut : _shortcuts)
      if (shortcut.enabled)
        visible.add(shortcut);

    if (!_expanded)
    {
      LinearLayout row = newRow();
      addExpandButton(row);
      for (Shortcut shortcut : visible)
        addShortcutButton(row, shortcut);
      addSettingsButton(row);
      addView(inScroll(row), wrapContentRow());
      return;
    }

    int rowCount = Math.min(3, Math.max(1, visible.size()));
    int perRow = Math.max(1, (visible.size() + rowCount - 1) / rowCount);
    for (int rowIndex = 0; rowIndex < rowCount; rowIndex++)
    {
      LinearLayout row = newRow();
      if (rowIndex == 0)
        addExpandButton(row);
      int from = rowIndex * perRow;
      int to = Math.min(visible.size(), from + perRow);
      for (int i = from; i < to; i++)
        addShortcutButton(row, visible.get(i));
      if (rowIndex == rowCount - 1)
        addSettingsButton(row);
      addView(inScroll(row), wrapContentRow());
    }
  }

  private LayoutParams wrapContentRow()
  {
    return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
  }

  private void addShortcutButton(LinearLayout row, Shortcut shortcut)
  {
    switch (shortcut.action)
    {
      case ExtraKeysShortcutStore.ACTION_MODIFIER:
        addModifierButton(row, shortcut.label,
            modifierFromMask(shortcut.modifiers));
        return;
      case ExtraKeysShortcutStore.ACTION_PIN:
        _pinButton = addClickButton(row, shortcut.label, shortcut.label, () -> {
          _pinned = !_pinned;
          if (_pinButton != null)
            _pinButton.setActivated(_pinned);
        });
        _pinButton.setActivated(_pinned);
        return;
      case ExtraKeysShortcutStore.ACTION_RETURN:
        addClickButton(row, shortcut.label, shortcut.label, this::dispatchReturn);
        return;
      default:
        addClickButton(row, shortcut.label, shortcut.label,
            () -> dispatchShortcut(shortcut));
    }
  }

  private void addExpandButton(LinearLayout row)
  {
    _expandButton = addClickButton(row, "…", "Show all Extra Keys", () -> {
      _expanded = !_expanded;
      reloadShortcuts();
      rebuildPanel();
    });
    _expandButton.setActivated(_expanded);
  }

  private void addSettingsButton(LinearLayout row)
  {
    addClickButton(row, "+", getResources().getString(
          R.string.pref_extra_keys_bar_title), this::openShortcutManager);
  }

  private void openShortcutManager()
  {
    Intent intent = new Intent(getContext(), SettingsActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra(SettingsActivity.EXTRA_OPEN_EXTRA_KEYS_BAR, true);
    getContext().startActivity(intent);
  }

  private void addModifierButton(LinearLayout row, String label,
      KeyValue.Modifier modifier)
  {
    ModifierState state = _modifierStates.get(modifier);
    final ModifierState buttonState = state;
    state.button = makeButton(label, label);
    state.button.setActivated(state.active);
    state.button.setOnTouchListener((view, event) -> handleModifierTouch(
        modifier, buttonState, event));
    row.addView(state.button);
  }

  private boolean handleModifierTouch(KeyValue.Modifier modifier,
      ModifierState state, MotionEvent event)
  {
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
        state.pressed = true;
        state.wasActive = state.active;
        if (!state.active)
          setModifierActive(modifier, state, true);
        return true;
      case MotionEvent.ACTION_UP:
        state.pressed = false;
        setModifierActive(modifier, state, !state.wasActive);
        if (state.button != null)
          state.button.performClick();
        return true;
      case MotionEvent.ACTION_CANCEL:
        setModifierActive(modifier, state, state.wasActive);
        state.pressed = false;
        return true;
      default:
        return true;
    }
  }

  private TextView addClickButton(LinearLayout row, String label,
      String contentDescription, Runnable action)
  {
    TextView button = makeButton(label, contentDescription);
    button.setOnClickListener(view -> action.run());
    row.addView(button);
    return button;
  }

  private TextView makeButton(String label, String contentDescription)
  {
    TextView button = new TextView(getContext());
    LayoutParams params = new LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    int horizontalMargin = dp(4);
    int verticalMargin = dp(4);
    params.setMargins(horizontalMargin, verticalMargin,
        horizontalMargin, verticalMargin);
    button.setLayoutParams(params);
    button.setBackgroundResource(R.drawable.extra_key_button);
    button.setClickable(true);
    button.setFocusable(true);
    button.setGravity(Gravity.CENTER);
    int padding = dp(8);
    button.setPadding(padding, padding, padding, padding);
    button.setMinWidth(dp(32));
    button.setText(label);
    button.setTextColor(_labelColor);
    button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    button.setSingleLine(true);
    button.setContentDescription(contentDescription);
    return button;
  }

  private LinearLayout newRow()
  {
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(4), 0, dp(4), 0);
    return row;
  }

  private HorizontalScrollView inScroll(LinearLayout row)
  {
    HorizontalScrollView scroll = new HorizontalScrollView(getContext());
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setFillViewport(false);
    scroll.addView(row, new HorizontalScrollView.LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    return scroll;
  }

  private void dispatchShortcut(Shortcut shortcut)
  {
    if (_keyboard == null || _handler == null)
      return;
    Pointers.Modifiers modifiers = withModifierMask(
        currentModifiers(), shortcut.modifiers);
    dispatchValue(KeyValue.getKeyByName(shortcut.keyName), modifiers);
  }

  private void dispatchReturn()
  {
    if (_keyboard == null || _handler == null)
      return;
    dispatchValue(KeyValue.makeStringKey("\n", 0), Pointers.Modifiers.EMPTY);
  }

  private void dispatchValue(KeyValue key, Pointers.Modifiers modifiers)
  {
    key = _keyboard.modifyKey(key, modifiers);
    if (key == null)
      return;
    _handler.key_down(key, false);
    _handler.key_up(key, modifiers, null);
  }

  Pointers.Modifiers currentModifiers()
  {
    Pointers.Modifiers modifiers = Pointers.Modifiers.EMPTY;
    for (KeyValue.Modifier modifier : MODIFIERS)
      if (_modifierStates.get(modifier).active)
        modifiers = modifiers.with_extra_mod(
            KeyValue.makeInternalModifier(modifier));
    return modifiers;
  }

  private Pointers.Modifiers withModifierMask(Pointers.Modifiers modifiers,
      int mask)
  {
    if ((mask & ExtraKeysShortcutStore.CTRL) != 0)
      modifiers = modifiers.with_extra_mod(
          KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL));
    if ((mask & ExtraKeysShortcutStore.ALT) != 0)
      modifiers = modifiers.with_extra_mod(
          KeyValue.makeInternalModifier(KeyValue.Modifier.ALT));
    if ((mask & ExtraKeysShortcutStore.SHIFT) != 0)
      modifiers = modifiers.with_extra_mod(
          KeyValue.makeInternalModifier(KeyValue.Modifier.SHIFT));
    if ((mask & ExtraKeysShortcutStore.META) != 0)
      modifiers = modifiers.with_extra_mod(
          KeyValue.makeInternalModifier(KeyValue.Modifier.META));
    return modifiers;
  }

  private KeyValue.Modifier modifierFromMask(int mask)
  {
    if (mask == ExtraKeysShortcutStore.CTRL)
      return KeyValue.Modifier.CTRL;
    if (mask == ExtraKeysShortcutStore.ALT)
      return KeyValue.Modifier.ALT;
    if (mask == ExtraKeysShortcutStore.SHIFT)
      return KeyValue.Modifier.SHIFT;
    return KeyValue.Modifier.META;
  }

  private void clearModifiers()
  {
    for (KeyValue.Modifier modifier : MODIFIERS)
    {
      ModifierState state = _modifierStates.get(modifier);
      state.pressed = false;
      setModifierActive(modifier, state, false);
    }
  }

  private void setModifierActive(KeyValue.Modifier modifier,
      ModifierState state, boolean active)
  {
    if (state.active == active)
      return;
    state.active = active;
    if (state.button != null)
      state.button.setActivated(active);
    if (_keyboard != null)
      _keyboard.set_external_modifier(modifier, active);
  }

  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
