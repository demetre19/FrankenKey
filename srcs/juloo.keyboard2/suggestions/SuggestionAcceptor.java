package juloo.keyboard2.suggestions;

import java.util.EnumMap;
import juloo.keyboard2.Config;

/**
 * Role-keyed dispatch behind [Config.IKeyEventHandler.candidate_accepted].
 *
 * Every strip candidate enters through one seam; the acceptor routes each
 * [CandidateRole] to the action registered for it. Roles without a
 * registered action are dropped, matching feedback slots today. Extension
 * lanes register their roles instead of editing the view's touch logic.
 */
public final class SuggestionAcceptor
{
  public static interface Action
  {
    public void on_candidate(Decoder.RequestKey ticket, String text);
  }

  private final EnumMap<CandidateRole, Action> _actions =
    new EnumMap<CandidateRole, Action>(CandidateRole.class);

  public void register(CandidateRole role, Action action)
  {
    if (role == null || action == null)
      throw new IllegalArgumentException(
          "acceptor registration requires a role and an action");
    _actions.put(role, action);
  }

  public void accept(Decoder.RequestKey ticket, CandidateRole role,
      String text)
  {
    if (ticket == null || role == null || text == null)
      return;
    Action action = _actions.get(role);
    if (action != null)
      action.on_candidate(ticket, text);
  }

  public static interface LongPressAction
  {
    public void on_candidate_long_pressed(Decoder.RequestKey ticket,
        String text);
  }

  private final EnumMap<CandidateRole, LongPressAction> _longPressActions =
    new EnumMap<CandidateRole, LongPressAction>(CandidateRole.class);

  /** Register the handler for a long press (>=450 ms) on a role. */
  public void register_long_press(CandidateRole role, LongPressAction action)
  {
    if (role == null || action == null)
      throw new IllegalArgumentException(
          "long-press registration requires a role and an action");
    _longPressActions.put(role, action);
  }

  public void long_pressed(Decoder.RequestKey ticket, CandidateRole role,
      String text)
  {
    if (ticket == null || role == null || text == null)
      return;
    LongPressAction action = _longPressActions.get(role);
    if (action != null)
      action.on_candidate_long_pressed(ticket, text);
  }

  /**
   * The pre-seam gesture routing: learn/unlearn actions reach
   * [IKeyEventHandler.suggestion_swiped_up], every other candidate reaches
   * [IKeyEventHandler.suggestion_entered]. Used by the interface default and
   * by [KeyEventHandler]'s acceptor registrations.
   */
  public static void default_dispatch(
      final Config.IKeyEventHandler handler, Decoder.RequestKey ticket,
      CandidateRole role, String text)
  {
    switch (role)
    {
      case LEARN_ACTION:
      case UNLEARN_ACTION:
        handler.suggestion_swiped_up(ticket, text);
        break;
      case LEARNED_FEEDBACK:
      case UNLEARNED_FEEDBACK:
      case NONE:
        break;
      default:
        handler.suggestion_entered(ticket, text);
        break;
    }
  }
}
