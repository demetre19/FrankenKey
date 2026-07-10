package juloo.keyboard2.suggestions;

import android.content.Context;
import android.os.Build.VERSION;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import juloo.keyboard2.Config;
import juloo.keyboard2.R;

public class CandidatesView extends LinearLayout
{
  static final int NUM_CANDIDATES = 4;
  static final int LONG_CANDIDATE_LENGTH = 10;
  static final float LONG_CANDIDATE_TEXT_SCALE = 0.78f;
  float _candidate_text_size_px = 0f;


  /** Candidates currently visible. Entries can be [null] when there are less
      than [NUM_CANDIDATES] suggestions.
      - Entries at indexes [0] to [2] are word suggestions.
      - Entry at index [3] is the emoji suggestion. */
  String[] _items = new String[NUM_CANDIDATES];
  DisplayRole[] _roles = new DisplayRole[NUM_CANDIDATES];
  Decoder.RequestKey _request_key = null;


  /** Text views showing the candidates in [_items]. Text views visibility is
      set to [GONE] when there are less than [NUM_CANDIDATES] suggestions. */
  TextView[] _item_views = new TextView[NUM_CANDIDATES];
  View[] _separators = new View[2];

  /** Message when no dictionary is installed. Visible when no candidates are
      shown. Might be [null]. */
  View _status_no_dict = null;
  private static enum DisplayRole
  {
    NONE,
    WORD,
    ENTERED_TEXT,
    LEARN_ACTION,
    UNLEARN_ACTION,
    LEARNED_FEEDBACK,
    UNLEARNED_FEEDBACK,
    EMOJI
  }

  public CandidatesView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    setup_item_view(0, R.id.candidates_middle);
    setup_item_view(1, R.id.candidates_right);
    setup_item_view(2, R.id.candidates_left);
    setup_item_view(3, R.id.candidates_emoji);
    setup_separator_view(0, R.id.candidates_separator_left);
    setup_separator_view(1, R.id.candidates_separator_right);
  }

  public void set_decoder_state(SharedDecoder.Presentation state)
  {
    clear_candidates();
    if (state == null || state.state != SharedDecoder.Presentation.State.READY
        || state.result == null || state.key == null)
      return;
    Decoder.Candidate[] words = state.result.words();
    int count = Math.min(words.length, 3);
    for (int i = 0; i < count; i++)
    {
      _items[i] = words[i].surface;
      _roles[i] = words[i].role == Decoder.Role.ENTERED_LITERAL
        ? DisplayRole.ENTERED_TEXT : DisplayRole.WORD;
    }
    _items[3] = state.result.emoji;
    _roles[3] = state.result.emoji == null ? DisplayRole.NONE : DisplayRole.EMOJI;
    _request_key = state.key;
    expose_learn_action(words);
    expose_learn_feedback(state);
    if (count != 0 && _status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
    update_separators();
    for (int i = 0; i < _item_views.length; i++)
    {
      TextView v = _item_views[i];
      if (_items[i] != null)
      {
        set_candidate_text(v, _items[i], _roles[i]);
        v.setContentDescription(description_for(_items[i], _roles[i]));
        v.setVisibility(View.VISIBLE);
      }
    }
  }
  void update_separators()
  {
    update_separator(0, _items[2] != null && _items[0] != null);
    update_separator(1, _items[0] != null && _items[1] != null);
  }

  void update_separator(int index, boolean visible)
  {
    View separator = _separators[index];
    if (separator != null)
      separator.setVisibility(visible ? View.VISIBLE : View.GONE);
  }


  void clear_candidates()
  {
    _request_key = null;
    for (int i = 0; i < _item_views.length; i++)
    {
      _items[i] = null;
      _roles[i] = DisplayRole.NONE;
      _item_views[i].setText("");
      _item_views[i].setContentDescription(null);
      _item_views[i].setVisibility(View.GONE);
    }
    for (int i = 0; i < _separators.length; i++)
      update_separator(i, false);
  }

  public void refresh_config(Config config, boolean dictionary_available)
  {
    clear_candidates();
    if (!dictionary_available)
      inflate_status_no_dict(config);
    else if (_status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
    set_sizes(config);
  }

  void set_candidate_text(TextView v, String text, DisplayRole role)
  {
    String label = label_for(text, role);
    v.setText(label);
    apply_candidate_text_size(v, label);
  }

  /** Set the height of the suggestion row and the text size. */
  void set_sizes(Config config)
  {
    // Make the candidates view about as high as a keyboard row.
    float row_height = config.keyboard_rows_height_pixels * (1 - config.key_vertical_margin);
    ViewGroup.MarginLayoutParams p =
      (ViewGroup.MarginLayoutParams)getLayoutParams();
    p.height = (int)row_height;
    setLayoutParams(p);
    // Match the size of labels on the keyboard.
    _candidate_text_size_px = row_height * config.characterSize * config.labelTextSize;
    for (int i = 0; i < NUM_CANDIDATES; i++)
    {
      TextView v = _item_views[i];
      apply_candidate_text_size(v, null);
    }
  }

  void apply_candidate_text_size(TextView v, String label)
  {
    float text_size = _candidate_text_size_px;
    if (text_size <= 0f)
      return;
    float max_size = candidate_max_text_size(text_size, label);
    if (VERSION.SDK_INT < 26)
      v.setTextSize(TypedValue.COMPLEX_UNIT_PX, max_size);
    else
      v.setAutoSizeTextTypeUniformWithConfiguration(
          Math.max(1, (int)(max_size / 2.)),
          Math.max(1, (int)max_size),
          1, TypedValue.COMPLEX_UNIT_PX);
  }

  float candidate_max_text_size(float text_size, String label)
  {
    if (label != null && label.codePointCount(0, label.length())
        > LONG_CANDIDATE_LENGTH)
      return text_size * LONG_CANDIDATE_TEXT_SCALE;
    return text_size;
  }

  /** Show or hide a status view and inflate it if needed. */
  View inflate_and_show(View v, boolean show, int layout_id)
  {
    if (!show)
    {
      if (v != null)
        v.setVisibility(View.GONE);
    }
    else
    {
      if (v == null)
      {
        v = View.inflate(getContext(), layout_id, null);
        addView(v);
      }
      v.setVisibility(View.VISIBLE);
    }
    return v;
  }

  void inflate_status_no_dict(Config config)
  {
    if (_status_no_dict == null)
    {
      _status_no_dict = View.inflate(getContext(),
          R.layout.candidates_status_no_dict, null);
      addView(_status_no_dict);
    }
    Locale current_locale = (config.device_locales.default_ != null) ?
      Locale.forLanguageTag(config.device_locales.default_.lang_tag) : null;
    TextView tv = _status_no_dict.findViewById(android.R.id.text1);
    if (tv != null && current_locale != null)
      tv.setText(getResources().getString(
            R.string.candidates_status_click_to_install,
            current_locale.getDisplayName()));
    _status_no_dict.setVisibility(View.VISIBLE);
  }

  void expose_learn_action(Decoder.Candidate[] words)
  {
    Decoder.Candidate entered = null;
    for (Decoder.Candidate candidate : words)
      if (candidate.role == Decoder.Role.ENTERED_LITERAL)
      {
        entered = candidate;
        break;
      }
    if (entered == null)
      return;
    _items[2] = entered.surface;
    _roles[2] = entered.learned
      ? DisplayRole.UNLEARN_ACTION : DisplayRole.LEARN_ACTION;
  }

  void expose_learn_feedback(SharedDecoder.Presentation state)
  {
    if (state.feedback == SharedDecoder.Presentation.Feedback.NONE
        || state.feedbackWord == null)
      return;
    _items[2] = state.feedbackWord;
    _roles[2] = state.feedback == SharedDecoder.Presentation.Feedback.LEARNED
      ? DisplayRole.LEARNED_FEEDBACK : DisplayRole.UNLEARNED_FEEDBACK;
  }


  String label_for(String text, DisplayRole role)
  {
    if (role == DisplayRole.LEARN_ACTION)
      return "📖+";
    if (role == DisplayRole.UNLEARN_ACTION)
      return "📖−";
    if (role == DisplayRole.LEARNED_FEEDBACK)
      return "📖✓";
    if (role == DisplayRole.UNLEARNED_FEEDBACK)
      return "📖−";
    return text;
  }

  String description_for(String text, DisplayRole role)
  {
    if (role == DisplayRole.LEARN_ACTION)
      return "Learn " + text;
    if (role == DisplayRole.UNLEARN_ACTION)
      return "Forget " + text;
    if (role == DisplayRole.LEARNED_FEEDBACK)
      return "Learned " + text;
    if (role == DisplayRole.UNLEARNED_FEEDBACK)
      return "Forgot " + text;
    return text;
  }

  private void setup_separator_view(int index, int item_id)
  {
    _separators[index] = findViewById(item_id);
    update_separator(index, false);
  }

  private void setup_item_view(final int item_index, int item_id)
  {
    TextView v = (TextView)findViewById(item_id);
    v.setSingleLine(true);
    v.setMaxLines(1);
    v.setOnClickListener(new View.OnClickListener()
        {
          @Override
          public void onClick(View _v)
          {
            String it = _items[item_index];
            Decoder.RequestKey key = _request_key;
            if (it == null || key == null)
              return;
            if (_roles[item_index] == DisplayRole.LEARN_ACTION
                || _roles[item_index] == DisplayRole.UNLEARN_ACTION)
              Config.globalConfig().handler.suggestion_swiped_up(key, it);
            else if (_roles[item_index] != DisplayRole.LEARNED_FEEDBACK
                && _roles[item_index] != DisplayRole.UNLEARNED_FEEDBACK)
              Config.globalConfig().handler.suggestion_entered(key, it);
          }
        });
    v.setOnTouchListener(new View.OnTouchListener()
        {
          float _down_y;

          @Override
          public boolean onTouch(View _v, MotionEvent event)
          {
            String it = _items[item_index];
            Decoder.RequestKey key = _request_key;
            if (it == null || key == null)
              return false;
            switch (event.getActionMasked())
            {
              case MotionEvent.ACTION_DOWN:
                _down_y = event.getY();
                return false;
              case MotionEvent.ACTION_UP:
                float dy = event.getY() - _down_y;
                if (Math.abs(dy) < swipe_threshold_px())
                  return false;
                if (_roles[item_index] == DisplayRole.LEARNED_FEEDBACK
                    || _roles[item_index] == DisplayRole.UNLEARNED_FEEDBACK)
                  return true;
                if (dy < 0 || _roles[item_index] == DisplayRole.LEARN_ACTION
                    || _roles[item_index] == DisplayRole.UNLEARN_ACTION)
                  Config.globalConfig().handler.suggestion_swiped_up(key, it);
                else
                  Config.globalConfig().handler.suggestion_entered(key, it);
                return true;
              default:
                return false;
            }
          }
        });
    v.setVisibility(View.GONE);
    _item_views[item_index] = v;
  }

  float swipe_threshold_px()
  {
    return 24.f * getResources().getDisplayMetrics().density;
  }

  /** Whether the candidates view should be shown for a given editor. */
  public static boolean should_show(EditorInfo info)
  {
    int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
    int flags = info.inputType & InputType.TYPE_MASK_FLAGS;
    switch (info.inputType & InputType.TYPE_MASK_CLASS)
    {
      case InputType.TYPE_CLASS_TEXT:
        switch (variation)
        {
          case InputType.TYPE_TEXT_VARIATION_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
            return false;
          default:
            /* Editor requested that we don't show suggestions. Enable
               suggestions anyway when the flags [NO_SUGGESTIONS] and
               [AUTO_CORRECT] are present at the same time. This happens with
               Google Keep. */
            if ((flags &
                  (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                   | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
                == InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
              return false;
            return true;
        }
      case InputType.TYPE_CLASS_NUMBER:
        // Beware of TYPE_NUMBER_VARIATION_PASSWORD
        return false;
      default: return false;
    }
  }
}
