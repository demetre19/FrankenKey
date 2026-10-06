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
import juloo.keyboard2.EditorConfig;
import juloo.keyboard2.Config;
import juloo.keyboard2.R;

public class CandidatesView extends LinearLayout
{
  static final int NUM_CANDIDATES = 4;
  static final int WORDS_PER_PAGE = 3;
  static final int WORD_PAGES = 2;
  static final int LONG_CANDIDATE_LENGTH = 10;
  static final float LONG_CANDIDATE_TEXT_SCALE = 0.78f;
  static final long PAGE_ANIMATION_MS = 180L;
  float _candidate_text_size_px = 0f;


  /** Candidates currently visible. Entries can be [null] when there are less
      than [NUM_CANDIDATES] suggestions.
      - Entries at indexes [0] to [2] are word suggestions.
      - Entry at index [3] is the emoji suggestion. */
  String[] _items = new String[NUM_CANDIDATES];
  CandidateRole[] _roles = new CandidateRole[NUM_CANDIDATES];
  String[][] _page_items = new String[WORD_PAGES][WORDS_PER_PAGE];
  CandidateRole[][] _page_roles =
    new CandidateRole[WORD_PAGES][WORDS_PER_PAGE];
  /** One [Decoder.CandidateTicket] per visible candidate, captured when a
      READY presentation renders and bound to a finger at ACTION_DOWN. */
  Decoder.CandidateTicket[][] _page_tickets =
    new Decoder.CandidateTicket[WORD_PAGES][WORDS_PER_PAGE];
  Decoder.CandidateTicket _emoji_ticket = null;
  /** Ticket captured at ACTION_DOWN for the slot under the finger. */
  Decoder.CandidateTicket _down_ticket = null;
  int _page = 0;
  Decoder.RequestKey _request_key = null;

  /** Text views showing the candidates in [_items]. Text views visibility is
      set to [GONE] when there are less than [NUM_CANDIDATES] suggestions. */
  TextView[] _item_views = new TextView[NUM_CANDIDATES];
  View[] _separators = new View[2];

  /** Message when no dictionary is installed. Visible when no candidates are
      shown. Might be [null]. */
  View _status_no_dict = null;

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
    // Pinned [CandidateSource] candidates, when present, were already merged
    // ahead of the ranked words by [SharedDecoder].
    Decoder.Candidate[] words = state.ranked != null
      ? state.ranked : state.result.words();
    int count = 0;
    if (state.pinned != null)
      for (CandidateSource.Candidate pinned : state.pinned)
      {
        if (count >= WORDS_PER_PAGE * WORD_PAGES)
          break;
        _page_items[count / WORDS_PER_PAGE][count % WORDS_PER_PAGE] =
          pinned.surface;
        _page_roles[count / WORDS_PER_PAGE][count % WORDS_PER_PAGE] =
          pinned.role;
        _page_tickets[count / WORDS_PER_PAGE][count % WORDS_PER_PAGE] =
          make_ticket(state, pinned.surface, pinned.role);
        count++;
      }
    for (int i = 0; i < words.length && count < WORDS_PER_PAGE * WORD_PAGES;
        i++)
    {
      _page_items[count / WORDS_PER_PAGE][count % WORDS_PER_PAGE] =
        words[i].surface;
      _page_roles[count / WORDS_PER_PAGE][count % WORDS_PER_PAGE] =
        display_role(words[i].role);
      _page_tickets[count / WORDS_PER_PAGE][count % WORDS_PER_PAGE] =
        make_ticket(state, words[i].surface, _page_roles[count / WORDS_PER_PAGE][count % WORDS_PER_PAGE]);
      count++;
    }
    expose_learn_action(words);
    expose_learn_feedback(state);
    for (int slot = count; slot < WORDS_PER_PAGE * WORD_PAGES; slot++)
      _page_tickets[slot / WORDS_PER_PAGE][slot % WORDS_PER_PAGE] = null;
    _page_tickets[0][2] = _page_items[0][2] == null ? null
      : make_ticket(state, _page_items[0][2], _page_roles[0][2]);
    _items[3] = state.result.emoji;
    _roles[3] = state.result.emoji == null
      ? CandidateRole.NONE : CandidateRole.EMOJI;
    _emoji_ticket = _items[3] == null ? null
      : make_ticket(state, _items[3], _roles[3]);
    _request_key = state.key;
    render_page(0, false);
    if (count != 0 && _status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
  }

  static CandidateRole display_role(Decoder.Role role)
  {
    switch (role)
    {
      case ENTERED_LITERAL: return CandidateRole.ENTERED_TEXT;
      case NEXT_WORD: return CandidateRole.NEXT_WORD;
      default: return CandidateRole.WORD;
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

  /** Immutable acceptance ticket for one rendered candidate. Returns null
      when the presentation carries no word snapshot (legacy READY) so the
      dispatch can fall back to the bare request key. */
  static Decoder.CandidateTicket make_ticket(
      SharedDecoder.Presentation state, String surface, CandidateRole role)
  {
    return state.word == null ? null
      : new Decoder.CandidateTicket(state.key, state.connectionId,
          state.word, state.absoluteWordStart, surface, role,
          android.os.SystemClock.uptimeMillis());
  }

  /** The live ticket for an item index on the current page. */
  Decoder.CandidateTicket ticket_for(int item_index)
  {
    if (item_index == 3)
      return _emoji_ticket;
    return _page_tickets[_page][item_index];
  }



  void clear_candidates()
  {
    _emoji_ticket = null;
    _request_key = null;
    _page = 0;
    for (int page = 0; page < WORD_PAGES; page++)
      for (int slot = 0; slot < WORDS_PER_PAGE; slot++)
      {
        _page_items[page][slot] = null;
        _page_roles[page][slot] = CandidateRole.NONE;
        _page_tickets[page][slot] = null;
      }
    for (int i = 0; i < _item_views.length; i++)
    {
      _items[i] = null;
      _roles[i] = CandidateRole.NONE;
      _item_views[i].animate().cancel();
      _item_views[i].setTranslationX(0f);
      _item_views[i].setText("");
      _item_views[i].setContentDescription(null);
      _item_views[i].setVisibility(View.GONE);
    }
    for (int i = 0; i < _separators.length; i++)
      update_separator(i, false);
  }
  void render_page(int page, boolean animate)
  {
    int previous = _page;
    _page = page;
    for (int i = 0; i < WORDS_PER_PAGE; i++)
    {
      _items[i] = _page_items[page][i];
      _roles[i] = _page_roles[page][i];
    }
    update_separators();
    for (int i = 0; i < _item_views.length; i++)
    {
      TextView v = _item_views[i];
      v.animate().cancel();
      v.setTranslationX(0f);
      if (_items[i] == null)
      {
        v.setText("");
        v.setContentDescription(null);
        v.setVisibility(View.GONE);
        continue;
      }
      set_candidate_text(v, _items[i], _roles[i]);
      v.setContentDescription(description_for(_items[i], _roles[i]));
      v.setVisibility(View.VISIBLE);
    }
    if (animate && animations_enabled())
    {
      float offset = Math.max(1, getWidth());
      if (page < previous)
        offset = -offset;
      for (int i = 0; i < WORDS_PER_PAGE; i++)
        if (_item_views[i].getVisibility() == View.VISIBLE)
        {
          _item_views[i].setTranslationX(offset);
          _item_views[i].animate().translationX(0f)
            .setDuration(PAGE_ANIMATION_MS).start();
        }
    }
  }
  boolean show_page_for_swipe(float dx)
  {
    int target = _page + (dx < 0f ? 1 : -1);
    if (target >= 0 && target < WORD_PAGES
        && _page_items[target][0] != null)
      render_page(target, true);
    return true;
  }
  boolean animations_enabled()
  {
    return VERSION.SDK_INT < 26
      || android.animation.ValueAnimator.areAnimatorsEnabled();
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

  void set_candidate_text(TextView v, String text, CandidateRole role)
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
    _page_items[0][2] = entered.surface;
    _page_roles[0][2] = entered.learned
      ? CandidateRole.UNLEARN_ACTION : CandidateRole.LEARN_ACTION;
  }

  void expose_learn_feedback(SharedDecoder.Presentation state)
  {
    if (state.feedback == SharedDecoder.Presentation.Feedback.NONE
        || state.feedbackWord == null)
      return;
    _page_items[0][2] = state.feedbackWord;
    _page_roles[0][2] =
      state.feedback == SharedDecoder.Presentation.Feedback.LEARNED
      ? CandidateRole.LEARNED_FEEDBACK : CandidateRole.UNLEARNED_FEEDBACK;
  }


  String label_for(String text, CandidateRole role)
  {
    if (role == CandidateRole.LEARN_ACTION)
      return "📖+";
    if (role == CandidateRole.UNLEARN_ACTION)
      return "📖−";
    if (role == CandidateRole.LEARNED_FEEDBACK)
      return "📖✓";
    if (role == CandidateRole.UNLEARNED_FEEDBACK)
      return "📖−";
    return text;
  }

  String description_for(String text, CandidateRole role)
  {
    if (role == CandidateRole.LEARN_ACTION)
      return "Learn " + text;
    if (role == CandidateRole.UNLEARN_ACTION)
      return "Forget " + text;
    if (role == CandidateRole.LEARNED_FEEDBACK)
      return "Learned " + text;
    if (role == CandidateRole.UNLEARNED_FEEDBACK)
      return "Forgot " + text;
    return text;
  }

  private void setup_separator_view(int index, int item_id)
  {
    _separators[index] = findViewById(item_id);
    update_separator(index, false);
  }

  /** Route every strip gesture through the single candidate dispatch seam.
      The accepted ticket is the one captured at ACTION_DOWN for this slot,
      falling back to the slot's live ticket for events without a down record
      (accessibility clicks, direct dispatch). The ticket carries the word the
      user saw at press time even if the strip re-rendered mid-press, and
      [Decoder.CandidateTicket.consume] makes a repeated dispatch a no-op, so
      onTouch+onClick, double taps, and two-finger taps insert exactly once.
      Presentations without ticket data dispatch the bare request key, the
      pre-ticket behavior. */
  void dispatch_accept(int item_index, CandidateRole role)
  {
    Decoder.CandidateTicket ticket = _down_ticket != null
      ? _down_ticket : ticket_for(item_index);
    _down_ticket = null;
    if (ticket != null)
    {
      if (role == null || role == CandidateRole.NONE || !ticket.consume())
        return;
      Config.globalConfig().handler.candidate_accepted(ticket, role,
          ticket.candidate);
      return;
    }
    String it = _items[item_index];
    Decoder.RequestKey key = _request_key;
    if (it == null || key == null || role == null
        || role == CandidateRole.NONE)
      return;
    Config.globalConfig().handler.candidate_accepted(key, role, it);
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
            dispatch_accept(item_index, _roles[item_index]);
          }
        });
    v.setOnLongClickListener(new View.OnLongClickListener()
        {
          @Override
          public boolean onLongClick(View _v)
          {
            // Seam for role-specific long-press behavior. Not consumed: the
            // release still performs the default accept so a plain long tap
            // keeps its pre-seam outcome unless a lane registers a handler.
            Decoder.CandidateTicket ticket = _down_ticket != null
              ? _down_ticket : ticket_for(item_index);
            CandidateRole role = _roles[item_index];
            if (ticket != null)
            {
              Config.globalConfig().handler.candidate_long_pressed(
                  ticket, role, ticket.candidate);
              return false;
            }
            String it = _items[item_index];
            Decoder.RequestKey key = _request_key;
            if (it == null || key == null || role == CandidateRole.NONE)
              return false;
            Config.globalConfig().handler.candidate_long_pressed(
                key, role, it);
            return false;
          }
        });
    v.setOnTouchListener(new View.OnTouchListener()
        {
          float _down_x;
          float _down_y;

          @Override
          public boolean onTouch(View _v, MotionEvent event)
          {
            Decoder.RequestKey key = _request_key;
            if (key == null)
              return false;
            switch (event.getActionMasked())
            {
              case MotionEvent.ACTION_DOWN:
                _down_x = event.getX();
                _down_y = event.getY();
                _down_ticket = ticket_for(item_index);
                return false;
              case MotionEvent.ACTION_UP:
                float dx = event.getX() - _down_x;
                float dy = event.getY() - _down_y;
                if (Math.abs(dx) >= swipe_threshold_px()
                    && Math.abs(dx) > Math.abs(dy))
                  return show_page_for_swipe(dx);
                String it = _items[item_index];
                if (it == null || Math.abs(dy) < swipe_threshold_px())
                  return false;
                CandidateRole role = _roles[item_index];
                if (role == CandidateRole.LEARNED_FEEDBACK
                    || role == CandidateRole.UNLEARNED_FEEDBACK)
                  return true;
                // An upward swipe on any candidate is the learn gesture.
                CandidateRole dispatch = dy < 0
                  ? CandidateRole.LEARN_ACTION : role;
                dispatch_accept(item_index, dispatch);
                return true;
              case MotionEvent.ACTION_CANCEL:
                _down_ticket = null;
                return false;
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

  /** Whether typing assistance should be shown for a given editor. */
  public static boolean should_show(EditorInfo info)
  {
    return EditorConfig.should_use_typing_assistance(info);
  }
}
