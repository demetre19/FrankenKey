package juloo.keyboard2.suggestions;

/**
 * Semantic role of a candidate shown in the suggestion strip.
 *
 * [NONE] marks an empty slot and is never rendered or dispatched. [WORD],
 * [ENTERED_TEXT], [NEXT_WORD] and [EMOJI] are produced by [Decoder]. The
 * remaining roles are extension seams: they render as plain words and carry
 * no debug or source labels. Pinned candidates supplied by
 * [CandidateSource]s may use any of them.
 */
public enum CandidateRole
{
  NONE,
  WORD,
  ENTERED_TEXT,
  NEXT_WORD,
  EMOJI,
  LEARN_ACTION,
  UNLEARN_ACTION,
  LEARNED_FEEDBACK,
  UNLEARNED_FEEDBACK,
  EMAIL_ADDRESS,
  EMAIL_DOMAIN,
  KEEP_TYPED,
  SAVE_TYPED,
  REMOVE_CONFIRM,
  SHORTCUT,
  COMPLETION
}
