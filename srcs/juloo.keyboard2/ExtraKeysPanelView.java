package juloo.keyboard2;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.EnumMap;
import java.util.Map;

/** A compact remote-control key panel driven by the normal keyboard event path. */
public final class ExtraKeysPanelView extends LinearLayout
{
  private static final KeyValue.Modifier[] MODIFIERS = {
    KeyValue.Modifier.CTRL,
    KeyValue.Modifier.ALT,
    KeyValue.Modifier.SHIFT,
    KeyValue.Modifier.META,
  };

  private static final String[] MORE_LABELS = {
    "Esc", "Tab", "Home", "End", "Ins", "Del", "PgUp", "PgDn",
  };
  private static final String[] MORE_KEYS = {
    "esc", "tab", "home", "end", "insert", "delete", "page_up", "page_down",
  };
  private static final String[] COMMAND_LABELS = {
    "Enter", "←", "↑", "↓", "→", "Cmd+C", "Cmd+V", "Cmd+S",
  };
  private static final String[] COMMAND_KEYS = {
    "enter", "left", "up", "down", "right", null, null, null,
  };
  private static final String[] FUNCTION_LABELS = {
    "F1", "F2", "F3", "F4", "F5", "F6",
    "F7", "F8", "F9", "F10", "F11", "F12",
  };
  private static final String[] FUNCTION_KEYS = {
    "f1", "f2", "f3", "f4", "f5", "f6",
    "f7", "f8", "f9", "f10", "f11", "f12",
  };

  private static final class ModifierState
  {
    boolean active;
    boolean pressed;
    boolean wasActive;
    boolean used;
    TextView button;
  }

  private final Map<KeyValue.Modifier, ModifierState> _modifierStates =
    new EnumMap<KeyValue.Modifier, ModifierState>(KeyValue.Modifier.class);
  private LinearLayout _content;
  private TextView _functionButton;
  private TextView _pinButton;
  private TextView _moreButton;
  private Keyboard2View _keyboard;
  private Config.IKeyEventHandler _handler;
  private boolean _functionMode;
  private boolean _moreVisible = true;
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
    int padding = dp(4);
    setPadding(padding, padding, padding, padding);
    TypedArray colors = context.obtainStyledAttributes(new int[] {
        R.attr.colorLabel, R.attr.colorKeyboard,
    });
    _labelColor = colors.getColor(0, 0xffffffff);
    setBackgroundColor(colors.getColor(1, 0xff202124));
    colors.recycle();
    buildTopRow();
    _content = new LinearLayout(context);
    _content.setOrientation(VERTICAL);
    addView(_content, new LayoutParams(LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT));
    updateModeButtons();
    rebuildContent();
  }

  public void bind(Keyboard2View keyboard, Config.IKeyEventHandler handler)
  {
    _keyboard = keyboard;
    _handler = handler;
    _keyboard.set_external_key_listeners(
        this::markHeldModifiersUsed, this::consumeLatchedModifiers);
  }

  public void toggle()
  {
    if (getVisibility() == VISIBLE)
      hidePanel();
    else
      setVisibility(VISIBLE);
  }

  public void onStartInput()
  {
    clearModifiers();
    setVisibility(_pinned ? VISIBLE : GONE);
  }

  private void hidePanel()
  {
    clearModifiers();
    setVisibility(GONE);
  }

  private void buildTopRow()
  {
    LinearLayout row = newRow();
    addModifierButton(row, "Ctrl", KeyValue.Modifier.CTRL);
    addModifierButton(row, "Alt", KeyValue.Modifier.ALT);
    addModifierButton(row, "Shift", KeyValue.Modifier.SHIFT);
    addModifierButton(row, "Cmd", KeyValue.Modifier.META);
    _functionButton = addClickButton(row, "Fn", () -> {
      _functionMode = !_functionMode;
      if (_functionMode)
        _moreVisible = false;
      updateModeButtons();
      rebuildContent();
    });
    _pinButton = addClickButton(row, "Pin", () -> {
      _pinned = !_pinned;
      _pinButton.setActivated(_pinned);
    });
    _moreButton = addClickButton(row, "…", () -> {
      _moreVisible = !_moreVisible;
      if (_moreVisible)
        _functionMode = false;
      updateModeButtons();
      rebuildContent();
    });
    addView(inScroll(row), new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
  }

  private void rebuildContent()
  {
    _content.removeAllViews();
    if (_functionMode)
    {
      addKeyRow(FUNCTION_LABELS, FUNCTION_KEYS, 0, 6, dp(56));
      addKeyRow(FUNCTION_LABELS, FUNCTION_KEYS, 6, 12, dp(56));
    }
    else if (_moreVisible)
    {
      addKeyRow(MORE_LABELS, MORE_KEYS, 0, MORE_LABELS.length, dp(48));
      addCommandRow();
    }
  }

  private void addCommandRow()
  {
    LinearLayout row = newRow();
    for (int i = 0; i < COMMAND_LABELS.length; i++)
    {
      final int index = i;
      TextView button = makeButton(COMMAND_LABELS[i], dp(i < 5 ? 48 : 64));
      if (COMMAND_KEYS[i] == null)
        button.setOnClickListener(v -> dispatchShortcut(COMMAND_LABELS[index].charAt(4)));
      else
        button.setOnClickListener(v -> dispatchKey(COMMAND_KEYS[index]));
      row.addView(button);
    }
    _content.addView(inScroll(row), new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
  }

  private void addKeyRow(String[] labels, String[] keys, int from, int to, int width)
  {
    LinearLayout row = newRow();
    for (int i = from; i < to; i++)
    {
      final String key = keys[i];
      TextView button = makeButton(labels[i], width);
      button.setOnClickListener(v -> dispatchKey(key));
      row.addView(button);
    }
    _content.addView(inScroll(row), new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
  }

  private void addModifierButton(LinearLayout row, String label,
      KeyValue.Modifier modifier)
  {
    ModifierState state = new ModifierState();
    state.button = makeButton(label, dp(54));
    state.button.setOnTouchListener((view, event) -> handleModifierTouch(
        modifier, state, event));
    _modifierStates.put(modifier, state);
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
        state.used = false;
        if (!state.active)
          setModifierActive(modifier, state, true);
        return true;
      case MotionEvent.ACTION_UP:
        setModifierActive(modifier, state,
            state.used ? state.wasActive : !state.wasActive);
        state.pressed = false;
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

  private TextView addClickButton(LinearLayout row, String label, Runnable action)
  {
    TextView button = makeButton(label, dp(54));
    button.setOnClickListener(v -> action.run());
    row.addView(button);
    return button;
  }

  private TextView makeButton(String label, int width)
  {
    TextView button = new TextView(getContext());
    LayoutParams params = new LayoutParams(width, dp(46));
    int margin = dp(1);
    params.setMargins(margin, margin, margin, margin);
    button.setLayoutParams(params);
    button.setBackgroundResource(R.drawable.extra_key_button);
    button.setClickable(true);
    button.setFocusable(true);
    button.setGravity(Gravity.CENTER);
    button.setText(label);
    button.setTextColor(_labelColor);
    button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    button.setSingleLine(true);
    button.setContentDescription(label);
    return button;
  }

  private LinearLayout newRow()
  {
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  private HorizontalScrollView inScroll(LinearLayout row)
  {
    HorizontalScrollView scroll = new HorizontalScrollView(getContext());
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setFillViewport(true);
    scroll.addView(row, new HorizontalScrollView.LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    return scroll;
  }

  private void dispatchKey(String keyName)
  {
    if (_keyboard == null || _handler == null)
      return;
    markHeldModifiersUsed();
    Pointers.Modifiers modifiers = currentModifiers();
    KeyValue key = _keyboard.modifyKey(KeyValue.getKeyByName(keyName), modifiers);
    if (key == null)
      return;
    _handler.key_down(key, false);
    _handler.key_up(key, modifiers, null);
    consumeLatchedModifiers();
  }

  private void dispatchShortcut(char character)
  {
    if (_keyboard == null || _handler == null)
      return;
    ModifierState meta = _modifierStates.get(KeyValue.Modifier.META);
    boolean wasActive = meta.active;
    if (!wasActive)
      setModifierActive(KeyValue.Modifier.META, meta, true);
    dispatchKey(String.valueOf(Character.toLowerCase(character)));
    if (!wasActive)
      setModifierActive(KeyValue.Modifier.META, meta, false);
  }

  Pointers.Modifiers currentModifiers()
  {
    Pointers.Modifiers modifiers = Pointers.Modifiers.EMPTY;
    for (KeyValue.Modifier modifier : MODIFIERS)
      if (_modifierStates.get(modifier).active)
        modifiers = modifiers.with_extra_mod(KeyValue.makeInternalModifier(modifier));
    return modifiers;
  }

  void markHeldModifiersUsed()
  {
    for (ModifierState state : _modifierStates.values())
      if (state.pressed)
        state.used = true;
  }

  void consumeLatchedModifiers()
  {
    for (KeyValue.Modifier modifier : MODIFIERS)
    {
      ModifierState state = _modifierStates.get(modifier);
      if (state.active && !state.pressed)
        setModifierActive(modifier, state, false);
    }
  }

  private void clearModifiers()
  {
    for (KeyValue.Modifier modifier : MODIFIERS)
    {
      ModifierState state = _modifierStates.get(modifier);
      state.pressed = false;
      state.used = false;
      setModifierActive(modifier, state, false);
    }
  }

  private void setModifierActive(KeyValue.Modifier modifier,
      ModifierState state, boolean active)
  {
    if (state.active == active)
      return;
    state.active = active;
    state.button.setActivated(active);
    if (_keyboard != null)
      _keyboard.set_external_modifier(modifier, active);
  }

  private void updateModeButtons()
  {
    _functionButton.setActivated(_functionMode);
    _moreButton.setActivated(_moreVisible);
  }


  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
