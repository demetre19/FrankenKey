package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.UserManager;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputType;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.LogPrinter;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import juloo.cdict.Cdict;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.dict.DictionariesActivity;
import juloo.keyboard2.lang.LanguagePack;
import juloo.keyboard2.lang.LanguagePackManager;
import juloo.keyboard2.prefs.LayoutsPreference;
import juloo.keyboard2.suggestions.CandidatesView;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.SharedDecoder;
import juloo.keyboard2.snippets.SnippetRowView;

public class Keyboard2 extends InputMethodService
  implements SharedPreferences.OnSharedPreferenceChangeListener
{
  /** The view containing the keyboard and candidates view. */
  private ViewGroup _keyboard_container_view;
  private Keyboard2View _keyboard_layout_view;
  private CandidatesView _candidates_view;
  private SnippetRowView _snippet_row_view;
  private SharedDecoder _decoder;
  private KeyEventHandler _keyeventhandler;
  private long _decoder_session = 0;
  private SharedDecoder.ResourceSpec _resource_spec =
    SharedDecoder.ResourceSpec.empty("none:0");
  private SharedDecoder.PersonalizationSpec _personalization_spec =
    SharedDecoder.PersonalizationSpec.empty("locked");
  private String _resource_name = null;
  private long _resource_generation = 0;
  /** If not 'null', the layout to use instead of [_config.current_layout]. */
  private KeyboardData _currentSpecialLayout;
  /** Layout associated with the currently selected locale. Not 'null'. */
  private KeyboardData _localeTextLayout;
  /** Installed and current locales. */
  private Dictionaries _dictionaries;
  private LanguagePackManager _language_pack_manager;
  private ViewGroup _emojiPane = null;
  private ViewGroup _clipboard_pane = null;
  private ViewGroup _gif_pane = null;
  private boolean _requestedScreenshotPermission = false;
  private boolean _emojiPaneCommitting = false;
  private Handler _handler;
  private SharedPreferences _prefs;

  private Config _config;

  private FoldStateTracker _foldStateTracker;

  /** Layout currently visible before it has been modified. */
  KeyboardData current_layout_unmodified()
  {
    if (_currentSpecialLayout != null)
      return _currentSpecialLayout;
    KeyboardData layout = null;
    int layout_i = _config.get_current_layout();
    if (layout_i >= _config.layouts.size())
      layout_i = 0;
    if (layout_i < _config.layouts.size())
      layout = _config.layouts.get(layout_i);
    if (layout == null)
      layout = _config.clean_mode ? loadCleanTextLayout() : _localeTextLayout;
    return layout;
  }

  /** Layout currently visible. */
  KeyboardData current_layout()
  {
    KeyboardData layout = _currentSpecialLayout != null
      ? _currentSpecialLayout
      : LayoutModifier.modify_layout(current_layout_unmodified());
    return layout;
  }

  /** Install the visible typing layout and the decoder's matching geometry. */
  private void apply_layout(KeyboardData layout)
  {
    _keyboard_layout_view.setKeyboard(layout);
    if (_decoder_session != 0)
      _decoder.update_layout(_decoder_session, layout);
  }

  void setTextLayout(int l)
  {
    _config.set_current_layout(l);
    _currentSpecialLayout = null;
    apply_layout(current_layout());
  }

  void incrTextLayout(int delta)
  {
    int s = _config.layouts.size();
    setTextLayout((_config.get_current_layout() + delta + s) % s);
  }

  void setSpecialLayout(KeyboardData l)
  {
    _currentSpecialLayout = l;
    apply_layout(l);
  }

  KeyboardData loadLayout(int layout_id)
  {
    return KeyboardData.load(getResources(), layout_id);
  }

  /** Load a layout that contains a numpad. */
  KeyboardData loadNumpad(int layout_id)
  {
    return LayoutModifier.modify_numpad(KeyboardData.load(getResources(), layout_id),
        current_layout_unmodified());
  }

  KeyboardData loadNumericLayout()
  {
    return loadNumpad(_config.orientation_landscape ?
        R.xml.numeric_landscape : R.xml.numeric);
  }

  KeyboardData loadCleanTextLayout()
  {
    return KeyboardData.load(getResources(), R.xml.clean_text);
  }

  KeyboardData loadCleanNumericLayout()
  {
    return KeyboardData.load(getResources(), R.xml.clean_numeric);
  }

  KeyboardData selectedNumberEntryLayout()
  {
    switch (_config.selected_number_layout)
    {
      case PIN:
        return loadPinentry(_config.orientation_landscape ?
            R.xml.pin_landscape : R.xml.pin);
      case NUMBER:
        return _config.clean_mode ? loadCleanNumericLayout() : loadNumericLayout();
      case NORMAL:
      default:
        return null;
    }
  }

  KeyboardData loadCleanSymbolsLayout()
  {
    return KeyboardData.load(getResources(), R.xml.clean_symbols);
  }

  KeyboardData loadPinentry(int layout_id)
  {
    return LayoutModifier.modify_pinentry(KeyboardData.load(getResources(), layout_id),
        current_layout_unmodified());
  }

  @Override
  public void onCreate()
  {
    super.onCreate();
    _prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _handler = new Handler(getMainLooper());
    _foldStateTracker = new FoldStateTracker(this);
    _dictionaries = Dictionaries.instance(this);
    _language_pack_manager = new LanguagePackManager(this);
    Config.initGlobalConfig(_prefs, getResources(),
        _foldStateTracker.isUnfolded(), _dictionaries);
    _config = Config.globalConfig();
    Receiver recvr = this.new Receiver();
    _decoder = new SharedDecoder(_handler, recvr);
    _keyeventhandler = new KeyEventHandler(recvr, _decoder);
    KeyValue.Stateful._handler = recvr;
    _config.handler = _keyeventhandler;
    _prefs.registerOnSharedPreferenceChangeListener(this);
    Logs.set_debug_logs(getResources().getBoolean(R.bool.debug_logs));
    refreshSubtypeImm();
    refresh_current_dictionary(true);
    _personalization_spec = create_personalization_spec();
    _decoder.prewarm(_resource_spec, _personalization_spec);
    create_keyboard_view();
    ClipboardHistoryService.on_startup(this, _keyeventhandler);
    _foldStateTracker.setChangedCallback(() -> {
        refresh_config();
        apply_layout(current_layout());
      });
  }

  @Override
  public void onDestroy()
  {
    _prefs.unregisterOnSharedPreferenceChangeListener(this);
    _keyeventhandler.finished();
    _decoder_session = 0;
    _decoder.close();
    _foldStateTracker.close();
    super.onDestroy();
  }

  private void create_keyboard_view()
  {
    _keyboard_container_view = (ViewGroup)inflate_view(R.layout.keyboard);
    _keyboard_layout_view = (Keyboard2View)_keyboard_container_view.findViewById(R.id.keyboard_view);
    _candidates_view = (CandidatesView)_keyboard_container_view.findViewById(R.id.candidates_view);
    _snippet_row_view = (SnippetRowView)_keyboard_container_view.findViewById(R.id.snippet_row);
  }

  InputMethodManager get_imm()
  {
    return (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
  }

  private void refreshSubtypeImm()
  {
    _config.shouldOfferVoiceTyping = true;
    KeyboardData default_layout = null;
    _config.device_locales = DeviceLocales.load(this);
    if (_config.device_locales.default_ != null)
    {
      String layout_name = _config.device_locales.default_.default_layout;
      if (layout_name != null)
        default_layout = LayoutsPreference.layout_of_string(getResources(), layout_name);
    }
    _config.extra_keys_subtype = _config.device_locales.extra_keys();
    if (default_layout == null)
      default_layout = loadLayout(R.xml.latn_qwerty_us);
    _localeTextLayout = default_layout;
  }

  private SharedDecoder.PersonalizationSpec create_personalization_spec()
  {
    if (VERSION.SDK_INT >= 24)
    {
      UserManager user = (UserManager)getSystemService(USER_SERVICE);
      if (user != null && !user.isUserUnlocked())
        return SharedDecoder.PersonalizationSpec.empty("locked");
    }
    return new SharedDecoder.PersonalizationSpec("credential",
        PreferenceManager.getDefaultSharedPreferences(this));
  }

  private boolean refresh_current_dictionary(boolean force)
  {
    String current = _config.device_locales.default_ == null
      ? null : _config.device_locales.default_.dictionary;
    if (!force && (current == null ? _resource_name == null
          : current.equals(_resource_name)))
      return false;

    _resource_name = current;
    _resource_generation++;
    if (current == null)
    {
      _resource_spec = SharedDecoder.ResourceSpec.empty(
          "none:" + _resource_generation);
      return true;
    }

    LanguagePack languagePack = _language_pack_manager.find(current);
    Cdict[] dictionaries = _dictionaries.load(current);
    Cdict main = dictionaries == null ? null
      : Dictionaries.find_by_name(dictionaries, "main");
    Cdict emoji = dictionaries == null ? null
      : Dictionaries.find_by_name(dictionaries, "emoji");
    _resource_spec = new SharedDecoder.ResourceSpec(
        current + ":" + _resource_generation, dictionaries, main, emoji,
        languagePack);
    return true;
  }

  private SharedDecoder.PersonalizationSpec session_personalization_spec()
  {
    if (_config.editor_config.should_use_personalization)
      return _personalization_spec;
    return SharedDecoder.PersonalizationSpec.empty("termux-stateless");
  }

  private Decoder.DecoderConfig decoder_config()
  {
    return new Decoder.DecoderConfig(
        _config.suggestions_enabled,
        _config.autocorrect_enabled,
        _config.editor_config.should_show_candidates_view
          && !_config.split_layout,
        _config.editor_config.should_use_typing_assistance);
  }

  private void refresh_candidates_view()
  {
    boolean should_show =
      _config.suggestions_enabled
      && _config.editor_config.should_show_candidates_view
      && !_config.split_layout;
    if (should_show)
      _candidates_view.refresh_config(_config,
          _resource_spec.mainDictionary != null);
    _candidates_view.set_decoder_state(_decoder.current_presentation());
    _candidates_view.setVisibility(should_show ? View.VISIBLE : View.GONE);
  }

  /** Might re-create the keyboard view. [_keyboard_layout_view.setKeyboard()] and
      [setInputView()] must be called soon after. */
  private void refresh_config()
  {
    int prev_theme = _config.theme;
    _config.refresh(getResources(), _foldStateTracker.isUnfolded(), _dictionaries);
    boolean resources_changed = refresh_current_dictionary(false);
    SharedDecoder.PersonalizationSpec personalization =
      create_personalization_spec();
    boolean personalization_changed = !_personalization_spec.key.equals(
        personalization.key);
    _personalization_spec = personalization;
    // Refreshing the theme config requires re-creating the views
    if (prev_theme != _config.theme)
    {
      create_keyboard_view();
      _emojiPane = null;
      _clipboard_pane = null;
      _gif_pane = null;
      setInputView(_keyboard_container_view);
    }
    // Set keyboard background opacity
    Drawable bg = _keyboard_container_view.getBackground().mutate();
    bg.setAlpha(_config.keyboardOpacity);
    _keyboard_container_view.setBackground(bg);
    _keyboard_layout_view.reset();
    _snippet_row_view.refresh_config(_prefs,
        _config.editor_config.should_show_snippet_row,
        slot -> _keyeventhandler.snippet_entered(slot.getPhrase()));
    refresh_candidates_view();
    if (_decoder_session != 0)
    {
      _decoder.update_config(_decoder_session, decoder_config());
      if (resources_changed)
        _decoder.update_resources(_decoder_session, _resource_spec);
      if (personalization_changed)
        _decoder.update_personalization(_decoder_session,
            _personalization_spec);
    }
    else if (resources_changed || personalization_changed)
      _decoder.prewarm(_resource_spec, _personalization_spec);
  }

  private KeyboardData refresh_special_layout()
  {
    if (_config.editor_config.numeric_layout)
    {
      KeyboardData numberEntryLayout = selectedNumberEntryLayout();
      if (numberEntryLayout != null)
        return numberEntryLayout;
    }
    return null;
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting)
  {
    _keyeventhandler.finished();
    _decoder_session = 0;
    _config.editor_config.refresh(info, getResources());
    refresh_config();
    _currentSpecialLayout = refresh_special_layout();
    KeyboardData layout = current_layout();
    apply_layout(layout);
    _decoder_session = _decoder.start_session(decoder_config(),
        _resource_spec, layout, session_personalization_spec());
    _keyeventhandler.started(_config, _decoder_session);
    refresh_candidates_view();
    setInputView(_keyboard_container_view);
    Logs.debug_startup_input_view(info, _config);
  }

  @Override
  public void setInputView(View v)
  {
    ViewParent parent = v.getParent();
    if (parent != null && parent instanceof ViewGroup)
      ((ViewGroup)parent).removeView(v);
    super.setInputView(v);
    updateSoftInputWindowLayoutParams();
    v.requestApplyInsets();
  }

  void showKeyboardView()
  {
    setInputView(_keyboard_container_view);
  }

  private boolean isEmojiPaneOpen()
  {
    return _emojiPane != null && _emojiPane.getParent() != null;
  }

  void insertEmojiFromPane(KeyValue key)
  {
    boolean wasCommitting = _emojiPaneCommitting;
    _emojiPaneCommitting = true;
    try
    {
      _keyeventhandler.key_up(key, Pointers.Modifiers.EMPTY, null);
    }
    finally
    {
      _emojiPaneCommitting = wasCommitting;
    }
  }

  private boolean isGifPaneOpen()
  {
    return _gif_pane != null && _gif_pane.getParent() != null;
  }

  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    updateSoftInputWindowLayoutParams();
  }

  private void updateSoftInputWindowLayoutParams() {
    final Window window = getWindow().getWindow();
    // On API >= 35, Keyboard2View behaves as edge-to-edge
    // APIs 30 to 34 have visual artifact when edge-to-edge is enabled
    if (VERSION.SDK_INT >= 35)
    {
      WindowManager.LayoutParams wattrs = window.getAttributes();
      wattrs.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      // Allow to draw behind system bars
      wattrs.setFitInsetsTypes(0);
      window.setDecorFitsSystemWindows(false);
    }
    updateLayoutHeightOf(window, ViewGroup.LayoutParams.MATCH_PARENT);
    final View inputArea = window.findViewById(android.R.id.inputArea);

    updateLayoutHeightOf(
            (View) inputArea.getParent(),
            isFullscreenMode()
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT);
    updateLayoutGravityOf((View) inputArea.getParent(), Gravity.BOTTOM);

  }

  private static void updateLayoutHeightOf(final Window window, final int layoutHeight) {
    final WindowManager.LayoutParams params = window.getAttributes();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      window.setAttributes(params);
    }
  }

  private static void updateLayoutHeightOf(final View view, final int layoutHeight) {
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      view.setLayoutParams(params);
    }
  }

  private static void updateLayoutGravityOf(final View view, final int layoutGravity) {
    final ViewGroup.LayoutParams lp = view.getLayoutParams();
    if (lp instanceof LinearLayout.LayoutParams) {
      final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    } else if (lp instanceof FrameLayout.LayoutParams) {
      final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    }
  }

  @Override
  public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype)
  {
    refreshSubtypeImm();
    if (refresh_current_dictionary(true))
    {
      if (_decoder_session != 0)
        _decoder.update_resources(_decoder_session, _resource_spec);
      else
        _decoder.prewarm(_resource_spec, _personalization_spec);
    }
    refresh_candidates_view();
    apply_layout(current_layout());
  }

  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd)
  {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    _keyeventhandler.selection_updated(oldSelStart, newSelStart, newSelEnd);
    if ((oldSelStart == oldSelEnd) != (newSelStart == newSelEnd))
      _keyboard_layout_view.set_selection_state(newSelStart != newSelEnd);
  }

  private void finish_input_session()
  {
    _keyeventhandler.finished();
    _decoder_session = 0;
    if (_candidates_view != null)
      _candidates_view.set_decoder_state(_decoder.current_presentation());
  }

  @Override
  public void onFinishInputView(boolean finishingInput)
  {
    super.onFinishInputView(finishingInput);
    finish_input_session();
    _keyboard_layout_view.reset();
  }

  @Override
  public void onFinishInput()
  {
    super.onFinishInput();
    finish_input_session();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key)
  {
    refresh_config();
    apply_layout(current_layout());
  }

  @Override
  public boolean onEvaluateFullscreenMode()
  {
    /* Entirely disable fullscreen mode. */
    return false;
  }

  @Override
  public boolean onEvaluateInputViewShown()
  {
    // Since Android 16, this method returns [false] for unknown reasons.
    if (super.onEvaluateInputViewShown())
      return true;
    if (getResources().getConfiguration().hardKeyboardHidden
        == Configuration.HARDKEYBOARDHIDDEN_NO
        && _config.physical_keyboard_hide)
    {
      Logs.debug("Physical keyboard is present");
      return false;
    }
    return true;
  }

  /** Called from [onClick] attributes. */
  public void launch_dictionaries_activity(View v)
  {
    start_activity(DictionariesActivity.class);
  }

  void start_activity(Class cls)
  {
    Intent intent = new Intent(this, cls);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  void request_screenshot_permission_if_needed()
  {
    if (!Config.globalConfig().clipboard_save_screenshots)
      return;
    if (ClipboardHistoryService.hasScreenshotReadPermission(this))
    {
      ClipboardHistoryService.refresh_screenshot_observer();
      return;
    }
    if (_requestedScreenshotPermission)
      return;
    _requestedScreenshotPermission = true;
    Intent intent = new Intent(this, SettingsActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra(SettingsActivity.EXTRA_REQUEST_SCREENSHOT_PERMISSION, true);
    startActivity(intent);
  }

  /** Not static */
  public class Receiver implements KeyEventHandler.IReceiver,
         KeyValue.Stateful.Symbol_provider, SharedDecoder.Callback
  {
    public void handle_event_key(KeyValue.Event ev)
    {
      switch (ev)
      {
        case CONFIG:
          start_activity(SettingsActivity.class);
          break;

        case SWITCH_TEXT:
          if (isGifPaneOpen())
          {
            ((GifSearchView)_gif_pane).setTypingKeyboard(current_layout());
            break;
          }
          _currentSpecialLayout = null;
          apply_layout(current_layout());
          break;

        case SWITCH_NUMERIC:
          KeyboardData numericLayout = _config.clean_mode ? loadCleanNumericLayout() : loadNumericLayout();
          if (isGifPaneOpen())
          {
            ((GifSearchView)_gif_pane).setTypingKeyboard(numericLayout);
            break;
          }
          setSpecialLayout(numericLayout);
          break;

        case SWITCH_NUMBER_ENTRY:
          KeyboardData numberEntryLayout = selectedNumberEntryLayout();
          if (numberEntryLayout == null)
            numberEntryLayout = _config.clean_mode ? loadCleanNumericLayout() : loadNumericLayout();
          if (isGifPaneOpen())
          {
            ((GifSearchView)_gif_pane).setTypingKeyboard(numberEntryLayout);
            break;
          }
          setSpecialLayout(numberEntryLayout);
          break;

        case TOGGLE_CLEAN_MODE:
          boolean cleanMode = !_config.clean_mode;
          _prefs.edit().putBoolean("clean_mode", cleanMode).commit();
          try
          {
            PreferenceManager.getDefaultSharedPreferences(Keyboard2.this)
              .edit().putBoolean("clean_mode", cleanMode).apply();
          }
          catch (Exception _e) {}
          refresh_config();
          _currentSpecialLayout = null;
          apply_layout(current_layout());
          break;

        case SWITCH_EMOJI:
          if (_emojiPane == null)
            _emojiPane = (ViewGroup)inflate_view(R.layout.emoji_pane);
          ((EmojiSearchView)_emojiPane).setKeyboard(Keyboard2.this,
              current_layout());
          setInputView(_emojiPane);
          break;

        case SWITCH_CLIPBOARD:
          request_screenshot_permission_if_needed();
          if (_clipboard_pane == null)
            _clipboard_pane = (ViewGroup)inflate_view(R.layout.clipboard_pane);
          setInputView(_clipboard_pane);
          break;

        case SWITCH_BACK_EMOJI:
        case SWITCH_BACK_CLIPBOARD:
        case SWITCH_BACK_GIF:
          showKeyboardView();
          break;

        case CHANGE_METHOD_PICKER:
          get_imm().showInputMethodPicker();
          break;

        case CHANGE_METHOD_PREV:
          if (VERSION.SDK_INT < 28)
            get_imm().switchToLastInputMethod(getConnectionToken());
          else
            switchToPreviousInputMethod();
          break;

        case CHANGE_METHOD_NEXT:
          if (VERSION.SDK_INT < 28)
            get_imm().switchToNextInputMethod(getConnectionToken(), false);
          else
            switchToNextInputMethod(false);
          break;

        case ACTION:
          if (isEmojiPaneOpen())
          {
            ((EmojiSearchView)_emojiPane).refreshResults();
            break;
          }
          if (isGifPaneOpen())
          {
            ((GifSearchView)_gif_pane).refreshResults();
            break;
          }
          InputConnection conn = getCurrentInputConnection();
          if (conn != null)
            conn.performEditorAction(_config.editor_config.actionId);
          break;

        case GIF:
          if (_gif_pane == null)
            _gif_pane = (ViewGroup)inflate_view(R.layout.gif_pane);
          ((GifSearchView)_gif_pane).setKeyboard(Keyboard2.this,
              current_layout());
          setInputView(_gif_pane);
          break;

        case SWITCH_FORWARD:
          incrTextLayout(1);
          break;

        case SWITCH_BACKWARD:
          incrTextLayout(-1);
          break;

        case SWITCH_GREEKMATH:
          setSpecialLayout(loadNumpad(R.xml.greekmath));
          break;

        case SWITCH_CLEAN_SYMBOLS:
          KeyboardData symbolsLayout = loadCleanSymbolsLayout();
          if (isGifPaneOpen())
          {
            ((GifSearchView)_gif_pane).setTypingKeyboard(symbolsLayout);
            break;
          }
          setSpecialLayout(symbolsLayout);
          break;

        case CAPS_LOCK:
          set_shift_state(true, true);
          break;

        case SWITCH_VOICE_TYPING:
          if (!VoiceImeSwitcher.switch_to_voice_ime(Keyboard2.this, get_imm(),
                Config.globalPrefs()))
            _config.shouldOfferVoiceTyping = false;
          break;

        case HIDE_SELF:
          Keyboard2.this.requestHideSelf(0);
          break;
      }
    }

    public void set_shift_state(boolean state, boolean lock)
    {
      _keyboard_layout_view.set_shift_state(state, lock);
    }

    public void set_compose_pending(boolean pending)
    {
      _keyboard_layout_view.set_compose_pending(pending);
    }

    public void selection_state_changed(boolean selection_is_ongoing)
    {
      _keyboard_layout_view.set_selection_state(selection_is_ongoing);
    }

    public void confirm_unlearn_word(String word, final Runnable positive_action)
    {
      if (word == null || positive_action == null
          || _keyboard_container_view == null)
        return;
      IBinder token = _keyboard_container_view.getWindowToken();
      if (token == null)
        return;
      AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(
            Keyboard2.this, _config.theme))
        .setTitle(getString(R.string.adaptive_unlearn_confirm_title, word))
        .setMessage(R.string.adaptive_unlearn_confirm_message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.adaptive_unlearn_confirm_positive,
            (_dialog, _which) -> positive_action.run())
        .create();
      dialog.setCanceledOnTouchOutside(true);
      Utils.show_dialog_on_ime(dialog, token);
    }

    public InputConnection getCurrentInputConnection()
    {
      if (isEmojiPaneOpen() && !_emojiPaneCommitting)
        return ((EmojiSearchView)_emojiPane).getSearchInputConnection();
      if (isGifPaneOpen())
        return ((GifSearchView)_gif_pane).getSearchInputConnection();
      return Keyboard2.this.getCurrentInputConnection();
    }

    public EditorInfo getCurrentInputEditorInfo()
    {
      return Keyboard2.this.getCurrentInputEditorInfo();
    }

    public Handler getHandler()
    {
      return _handler;
    }

    public void decoder_state_changed(SharedDecoder.Presentation state)
    {
      if (_candidates_view != null)
        _candidates_view.set_decoder_state(state);
    }

    public String provide_stateful_key_symbol(KeyValue.Stateful q)
    {
      SharedDecoder.Presentation presentation = _decoder.current_presentation();
      if (presentation.state != SharedDecoder.Presentation.State.READY
          || presentation.result == null)
        return "";
      Decoder.Candidate[] words = presentation.result.words();
      switch (q)
      {
        case Complete_first:
          return words.length > 0 ? words[0].surface : "";
        case Complete_second:
          return words.length > 1 ? words[1].surface : "";
        case Complete_third:
          return words.length > 2 ? words[2].surface : "";
        case Complete_emoji:
          return presentation.result.emoji == null
            ? "" : presentation.result.emoji;
      }
      return "";
    }
  }

  private IBinder getConnectionToken()
  {
    return getWindow().getWindow().getAttributes().token;
  }

  private View inflate_view(int layout)
  {
    return View.inflate(new ContextThemeWrapper(this, _config.theme), layout, null);
  }
}
