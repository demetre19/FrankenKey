package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.suggestions.Decoder;
import juloo.keyboard2.suggestions.PersonalizationStore;
import juloo.keyboard2.suggestions.SharedDecoder;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class KeyEventHandlerAutocorrectContractTest
{
  private final List<SharedDecoder> _decoders = new ArrayList<SharedDecoder>();

  @After
  public void tearDown()
  {
    for (SharedDecoder decoder : _decoders)
      decoder.close();
  }

  @Test
  public void space_punctuation_and_enter_commit_the_decoder_correction_once()
      throws Exception
  {
    for (Boundary boundary : new Boundary[] {
        new Boundary("space", KeyValue.getSpecialKeyByName("space"), " "),
        new Boundary("period", KeyValue.getKeyByName("."), "."),
        new Boundary("enter", KeyValue.getSpecialKeyByName("enter"), "\n") })
    {
      Harness harness = harness("teh", true, true);
      installCorrection(harness.decoder, harness.key, "teh", "the");

      harness.handler.key_up(boundary.key, Pointers.Modifiers.EMPTY, null);

      assertEquals(boundary.name
          + " must commit the READY decoder correction and its exact separator as one visible replacement.",
          "the" + boundary.separator, harness.receiver.input.text.toString());
      assertEquals(boundary.name
          + " correction must perform exactly one replacement commit.",
          1, harness.receiver.input.commitTextCalls);
      assertEquals(boundary.name
          + " correction must remove the original typed word once.",
          1, harness.receiver.input.deleteSurroundingCalls);
      assertFalse(boundary.name
          + " commit must invalidate its request key so a second tap cannot reuse stale correction data.",
          harness.decoder.is_current(harness.key));
    }
  }

  @Test
  public void automatic_correction_reports_editor_metadata_for_platform_flash()
      throws Exception
  {
    Harness harness = harness("prefix teh", true, true);
    harness.receiver.input.extractedStartOffset = 100;
    installCorrection(harness.decoder, harness.key, "teh", "the");

    harness.handler.handle_space_bar();

    assertEquals("Autocorrection must still replace only the active word and append its separator.",
        "prefix the ", harness.receiver.input.text.toString());
    assertEquals("A changed automatic correction must notify the editor exactly once so Android can render its standard corrected-range fade.",
        1, harness.receiver.input.commitCorrectionCalls);
    CorrectionInfo info = harness.receiver.input.correctionInfo;
    assertNotNull("The editor notification must carry correction metadata.", info);
    assertEquals("The correction offset must be absolute, including ExtractedText.startOffset, so the editor highlights the replaced word.",
        107, info.getOffset());
    assertEquals("The correction metadata must identify the exact text replaced by autocorrect.",
        "teh", info.getOldText().toString());
    assertEquals("The correction metadata must exclude the separator and identify only the corrected word.",
        "the", info.getNewText().toString());
  }

  @Test
  public void pending_separator_correction_applies_only_when_exact_result_arrives()
      throws Exception
  {
    Harness harness = harness("prefix teh", true, true);
    harness.receiver.input.extractedStartOffset = 100;
    clearResult(harness.decoder);

    harness.handler.handle_space_bar();

    assertEquals("A slow decoder must never block the separator; the literal word and space must appear immediately.",
        "prefix teh ", harness.receiver.input.text.toString());
    assertTrue("The exact completed-word request must remain current while its nonblocking boundary correction is pending.",
        harness.decoder.is_current(harness.key));

    Decoder.Result result = correctionResult(harness.key, "teh", "the");
    installResult(harness.decoder, result);
    harness.handler.decoder_result_ready(result);

    assertEquals("The exact late READY result must safely replace the unchanged word boundary.",
        "prefix the ", harness.receiver.input.text.toString());
    assertEquals("A late automatic correction must emit one platform correction notification for the editor fade.",
        1, harness.receiver.input.commitCorrectionCalls);
    assertEquals("Late correction metadata must preserve the absolute pre-separator word offset.",
        107, harness.receiver.input.correctionInfo.getOffset());
    assertFalse("Resolving the boundary must invalidate its completed-word request before the next word.",
        harness.decoder.is_current(harness.key));

    harness.handler.handle_backspace();

    assertEquals("Immediate Backspace after a late correction must retain the normal correction-undo contract.",
        "prefix teh ", harness.receiver.input.text.toString());
  }

  @Test
  public void stale_replacement_selection_callbacks_preserve_live_correction_undo()
      throws Exception
  {
    Harness harness = harness("prefix teh", true, true);
    clearResult(harness.decoder);
    harness.handler.handle_space_bar();
    Decoder.Result result = correctionResult(harness.key, "teh", "the");
    installResult(harness.decoder, result);
    harness.handler.decoder_result_ready(result);

    harness.handler.selection_updated(11, 0, 0);
    harness.handler.selection_updated(0, 11, 11);
    harness.handler.handle_backspace();

    assertEquals("Delayed selection callbacks from the editor's internal replacement must not settle an otherwise exact correction before immediate Backspace can restore the literal.",
        "prefix teh ", harness.receiver.input.text.toString());
  }

  @Test
  public void pointer_up_modifier_refresh_preserves_live_correction_undo()
      throws Exception
  {
    Harness harness = harness("prefix teh", true, true);
    clearResult(harness.decoder);
    harness.handler.handle_space_bar();
    Decoder.Result result = correctionResult(harness.key, "teh", "the");
    installResult(harness.decoder, result);
    harness.handler.decoder_result_ready(result);

    harness.handler.mods_changed(Pointers.Modifiers.EMPTY);
    harness.handler.handle_backspace();

    assertEquals("The neutral pointer-up modifier refresh is not a new editing action and must not settle correction undo.",
        "prefix teh ", harness.receiver.input.text.toString());
  }

  @Test
  public void verified_stale_pre_separator_selection_callback_keeps_late_result_safe()
      throws Exception
  {
    Harness harness = harness("prefix teh", true, true);
    clearResult(harness.decoder);
    harness.handler.handle_space_bar();
    harness.handler.mods_changed(Pointers.Modifiers.EMPTY);

    harness.handler.selection_updated(9, 0, 0);
    harness.handler.selection_updated(10, 11, 11);
    harness.handler.currently_typed_word(snapshot(999, "", false));
    Decoder.Result result = correctionResult(harness.key, "teh", "the");
    installResult(harness.decoder, result);
    harness.handler.decoder_result_ready(result);

    assertEquals("A delayed pre-separator selection notification must not cancel correction when the live editor still proves the exact post-separator cursor and suffix.",
        "prefix the ", harness.receiver.input.text.toString());
  }

  @Test
  public void next_key_cancels_pending_separator_correction_before_editor_mutation()
      throws Exception
  {
    Harness harness = harness("teh", true, true);
    clearResult(harness.decoder);
    harness.handler.handle_space_bar();
    Decoder.Result late = correctionResult(harness.key, "teh", "the");

    harness.handler.key_down(KeyValue.getKeyByName("x"), false);
    installResult(harness.decoder, late);
    harness.handler.decoder_result_ready(late);

    assertEquals("Any later key action must freeze the already-visible literal boundary rather than rewrite text behind the cursor.",
        "teh ", harness.receiver.input.text.toString());
    assertEquals("A cancelled late result must not report correction metadata.",
        0, harness.receiver.input.commitCorrectionCalls);
  }

  @Test
  public void editor_change_rejects_pending_separator_correction()
      throws Exception
  {
    Harness moved = harness("teh", true, true);
    clearResult(moved.decoder);
    moved.handler.handle_space_bar();
    Decoder.Result movedLate = correctionResult(moved.key, "teh", "the");
    moved.receiver.input.setSelection(0, 0);
    installResult(moved.decoder, movedLate);
    moved.handler.decoder_result_ready(movedLate);

    assertEquals("A moved cursor must prevent a late decoder result from rewriting the old boundary.",
        "teh ", moved.receiver.input.text.toString());

    Harness changed = harness("teh", true, true);
    clearResult(changed.decoder);
    changed.handler.handle_space_bar();
    Decoder.Result changedLate = correctionResult(changed.key, "teh", "the");
    changed.receiver.input.commitText("x", 1);
    installResult(changed.decoder, changedLate);
    changed.handler.decoder_result_ready(changedLate);

    assertEquals("A changed editor suffix must prevent stale late correction even when the captured request itself completed.",
        "teh x", changed.receiver.input.text.toString());
    assertEquals("Rejected late results must not emit misleading editor correction metadata.",
        0, changed.receiver.input.commitCorrectionCalls);
  }

  @Test
  public void termux_never_stages_late_separator_correction()
      throws Exception
  {
    Harness harness = harness("teh", true, true, 0, true);
    clearResult(harness.decoder);

    harness.handler.handle_space_bar();
    Decoder.Result late = correctionResult(harness.key, "teh", "the");
    installResult(harness.decoder, late);
    harness.handler.decoder_result_ready(late);

    assertEquals("TYPE_NULL terminal input must keep its immediate literal boundary when no result was READY at separator time.",
        "teh ", harness.receiver.input.text.toString());
    assertEquals("Termux must never receive late editor correction metadata.",
        0, harness.receiver.input.commitCorrectionCalls);
  }

  @Test
  public void explicit_candidate_tap_does_not_report_automatic_correction()
      throws Exception
  {
    Harness harness = harness("teh", true, true);
    installCorrection(harness.decoder, harness.key, "teh", "the");

    harness.handler.suggestion_entered(harness.key, "the");

    assertEquals("A deliberate candidate selection must not masquerade as automatic editor feedback.",
        0, harness.receiver.input.commitCorrectionCalls);
  }

  @Test
  public void termux_autocorrect_and_candidate_tap_use_raw_del_events()
      throws Exception
  {
    for (boolean candidateTap : new boolean[] { false, true })
    {
      Harness harness = harness("teh", true, true, 0, true);
      installCorrection(harness.decoder, harness.key, "teh", "the");
      harness.receiver.input.rejectSurroundingReplacement = true;

      if (candidateTap)
        harness.handler.suggestion_entered(harness.key, "the");
      else
        harness.handler.handle_space_bar();

      String origin = candidateTap ? "candidate tap" : "autocorrect";
      assertEquals("Termux " + origin
          + " must replace the tracked typo and commit its separator.",
          "the ", harness.receiver.input.text.toString());
      assertEquals("Termux " + origin
          + " must not use unsupported surrounding-text deletion.",
          0, harness.receiver.input.deleteSurroundingCalls);
      assertEquals("Replacing teh through " + origin
          + " must send one raw DEL key-down per code point.",
          3, harness.receiver.input.delKeyDowns);
      assertEquals("The corrected word and separator must be one text commit.",
          1, harness.receiver.input.commitTextCalls);
      assertEquals("Termux raw-event replacement must not invoke editor correction metadata that its terminal InputConnection cannot honor.",
          0, harness.receiver.input.commitCorrectionCalls);
      assertCountsRemain(harness.prefs, "the", "teh", 0, 0);
    }
  }

  @Test
  public void termux_pending_undo_requires_unchanged_local_word_state()
      throws Exception
  {
    Harness harness = harness("teh", true, true, 0, true);
    installCorrection(harness.decoder, harness.key, "teh", "the");
    harness.handler.handle_space_bar();
    harness.receiver.input.commitText("x", 1);
    harness.handler._typedword.typed("x");

    harness.handler.handle_backspace();

    assertEquals("A later Termux Backspace must delete only the current character, not blindly rewrite an older correction.",
        "the ", harness.receiver.input.text.toString());
  }

  @Test
  public void changed_candidate_and_autocorrect_undo_before_learning()
      throws Exception
  {
    for (boolean candidateClick : new boolean[] { false, true })
    {
      Harness harness = harness("teh", true, true);
      commitChangedReplacement(harness, candidateClick);

      harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
          Pointers.Modifiers.EMPTY, null);

      String origin = candidateClick ? "candidate click" : "separator autocorrect";
      assertEquals("Immediate Backspace after a changed " + origin
          + " must restore the exact source and separator.",
          "teh ", harness.receiver.input.text.toString());
      assertEquals("The changed " + origin
          + " and its verified undo must each be one surrounding-text replacement.",
          2, harness.receiver.input.commitTextCalls);
      assertCountsRemain(harness.prefs, "the", "teh", 0, 0);
    }
  }

  @Test
  public void rejected_thys_to_thus_then_manual_this_keeps_original_source()
      throws Exception
  {
    Harness harness = harness("thys", true, true);
    installCorrection(harness.decoder, harness.key, "thys", "thus");

    harness.handler.handle_space_bar();
    assertEquals("The fixture must first reproduce the unwanted thys-to-thus autocorrection.",
        "thus ", harness.receiver.input.text.toString());
    harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
        Pointers.Modifiers.EMPTY, null);
    assertEquals("Immediate Backspace must restore the exact original typed word before manual repair.",
        "thys ", harness.receiver.input.text.toString());

    for (int i = 0; i < 3; i++)
      harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
          Pointers.Modifiers.EMPTY, null);
    harness.handler.send_text("is");
    assertEquals("Removing the separator, s, and y then typing is must visibly produce this.",
        "this", harness.receiver.input.text.toString());
    harness.handler.handle_space_bar();

    assertEquals("The final user-approved correction and boundary must remain in the editor.",
        "this ", harness.receiver.input.text.toString());
    awaitCounts(harness.prefs, "this", "thys", 0, 1);
    assertCountsRemain(harness.prefs, "this", "thys", 0, 1);
    assertEquals("Rejecting the intermediate autocorrection must not teach thys-to-thus.",
        0, correctionCount(harness.prefs, "thys", "thus"));
    assertEquals("The intermediate thus spelling must not replace the original thys provenance.",
        0, correctionCount(harness.prefs, "thus", "this"));
  }

  @Test
  public void secondary_replacement_undo_preserves_prior_manual_correction()
      throws Exception
  {
    for (boolean candidateClick : new boolean[] { false, true })
    {
      Harness harness = harness("thus", true, true);
      harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
          Pointers.Modifiers.EMPTY, null);
      harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
          Pointers.Modifiers.EMPTY, null);
      harness.handler.send_text("is");
      assertEquals("The fixture must first create the editor-verified manual correction.",
          "this", harness.receiver.input.text.toString());

      Decoder.RequestKey correctedKey = harness.handler._current_request_key;
      assertNotNull(correctedKey);
      awaitResult(harness.decoder, correctedKey);
      installCorrection(harness.decoder, correctedKey, "this", "thit");
      if (candidateClick)
        harness.handler.suggestion_entered(correctedKey, "thit");
      else
        harness.handler.handle_space_bar();
      assertEquals("The secondary replacement must be visible before its undo window.",
          "thit ", harness.receiver.input.text.toString());

      harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
          Pointers.Modifiers.EMPTY, null);

      String origin = candidateClick ? "candidate click" : "separator autocorrect";
      assertEquals("Immediate Backspace after the secondary " + origin
          + " must restore the manually corrected word.",
          "this ", harness.receiver.input.text.toString());
      awaitCounts(harness.prefs, "this", "thus", 0, 1);
      assertCountsRemain(harness.prefs, "this", "thus", 0, 1);
      assertEquals("Undoing the secondary " + origin
          + " must reject its positive typo-pair evidence.",
          0, correctionCount(harness.prefs, "this", "thit"));
      assertEquals("Undoing the secondary " + origin
          + " must not teach its rejected target.",
          0, wordCount(harness.prefs, "thit"));
    }
  }

  @Test
  public void disabled_or_unsafe_context_commits_literal_separator_not_available_correction()
      throws Exception
  {
    for (Gate gate : new Gate[] {
        new Gate("disabled autocorrect", false, true),
        new Gate("unsafe editor", true, false) })
    {
      Harness harness = harness("teh", gate.autocorrect, gate.safeEditor);
      installCorrection(harness.decoder, harness.key, "teh", "the");

      harness.handler.handle_space_bar();

      assertEquals(gate.name
          + " must preserve the literal token even if a correction object is otherwise available.",
          "teh ", harness.receiver.input.text.toString());
      assertEquals(gate.name
          + " must insert only the separator, not run a surrounding-word replacement.",
          0, harness.receiver.input.deleteSurroundingCalls);
    }
  }

  @Test
  public void stale_or_wrong_request_result_is_never_used_at_commit_boundary()
      throws Exception
  {
    Harness harness = harness("teh", true, true);
    Decoder.RequestKey stale = harness.key;
    Decoder.RequestKey current = harness.decoder.request(harness.session,
        snapshot(99, "tehx", false));
    awaitResult(harness.decoder, current);
    harness.handler._current_request_key = current;
    installCorrection(harness.decoder, stale, "teh", "the");

    harness.handler.handle_space_bar();

    assertEquals("A correction produced for an older request must not replace the current typed text.",
        "teh ", harness.receiver.input.text.toString());
  }

  @Test
  public void standalone_lower_i_is_promoted_once_at_whitespace_or_sentence_boundaries()
      throws Exception
  {
    String[] separators = new String[] { " ", "." };
    String[] boundaryNames = new String[] {
      "whitespace boundary", "sentence punctuation boundary"
    };

    for (int i = 0; i < separators.length; ++i)
    {
      Harness harness = harness("i", false, true);

      harness.handler.handle_word_separator(separators[i]);

      assertEquals("A standalone lower i must become I only when "
          + boundaryNames[i] + " commits the active word.",
          "I" + separators[i], harness.receiver.input.text.toString());
      assertEquals("The standalone-i rewrite must leave the cursor after its committed boundary.",
          2, harness.receiver.input.cursor);
      assertEquals("A standalone-i rewrite must remove exactly the active one-letter word.",
          1, harness.receiver.input.deleteSurroundingCalls);
      assertEquals("A standalone-i rewrite must commit one atomic replacement with its boundary.",
          1, harness.receiver.input.commitTextCalls);
    }
  }

  @Test
  public void standalone_i_field_policy_rewrites_only_exact_typed_token_at_boundary()
      throws Exception
  {
    Harness allowed = harness("word ", false, true);
    allowed.config.editor_config.autocapitalise_standalone_i = true;

    allowed.handler.send_text("i");
    allowed.handler.handle_space_bar();

    assertEquals("A non-sentence-caps field that enables standalone-I must visibly promote a typed boundary token.",
        "word I ", allowed.receiver.input.text.toString());
    assertEquals("The standalone-I replacement must leave the cursor after the user's committed space.",
        7, allowed.receiver.input.cursor);
    assertEquals("Typing i then committing its boundary must perform one atomic replacement beyond the initial typed-i commit.",
        2, allowed.receiver.input.commitTextCalls);
    assertEquals("The enabled field policy must replace exactly the typed standalone i.",
        1, allowed.receiver.input.deleteSurroundingCalls);

    Harness blocked = harness("word ", false, true);
    blocked.config.editor_config.autocapitalise_standalone_i = false;

    blocked.handler.send_text("i");
    blocked.handler.handle_space_bar();

    assertEquals("A password-style field policy must preserve the user's literal typed i and space.",
        "word i ", blocked.receiver.input.text.toString());
    assertEquals("A disabled standalone-I policy must leave the cursor after normal separator insertion.",
        7, blocked.receiver.input.cursor);
    assertEquals("A disabled standalone-I policy must not delete any surrounding user text.",
        0, blocked.receiver.input.deleteSurroundingCalls);
    assertEquals("A disabled standalone-I policy must commit the typed i and separator normally.",
        2, blocked.receiver.input.commitTextCalls);

    Harness suffix = harness("h", false, true);
    suffix.config.editor_config.autocapitalise_standalone_i = true;

    suffix.handler.send_text("i");
    suffix.handler.handle_space_bar();

    assertEquals("The editor guard must preserve hi rather than rewriting its final i as a standalone word.",
        "hi ", suffix.receiver.input.text.toString());
    assertEquals("A suffix i must keep the cursor after its normal committed space.",
        3, suffix.receiver.input.cursor);
    assertEquals("A suffix i must not delete surrounding word text for a standalone-I rewrite.",
        0, suffix.receiver.input.deleteSurroundingCalls);
    assertEquals("A suffix i must use normal typed text and separator commits.",
        2, suffix.receiver.input.commitTextCalls);
  }

  @Test
  public void nonstandalone_or_already_capital_i_is_not_rewritten_at_a_boundary()
      throws Exception
  {
    String[] words = new String[] { "in", "i'", "I" };

    for (String word : words)
    {
      Harness harness = harness(word, false, true);

      harness.handler.handle_space_bar();

      assertEquals("Only an exact active word i may be promoted; " + word
          + " must remain the user's original text.",
          word + " ", harness.receiver.input.text.toString());
      assertEquals("An unchanged word must keep the cursor after its normal separator.",
          word.length() + 1, harness.receiver.input.cursor);
      assertEquals("An unchanged word must not be deleted for a standalone-i rewrite.",
          0, harness.receiver.input.deleteSurroundingCalls);
      assertEquals("An unchanged word must use only its normal separator commit.",
          1, harness.receiver.input.commitTextCalls);
    }
  }

  @Test
  public void correction_followed_by_period_refreshes_sentence_caps_after_replacement()
      throws Exception
  {
    Harness harness = harness("teh", true, true,
        TextUtils.CAP_MODE_SENTENCES);
    harness.receiver.input.cursorCapsMode = TextUtils.CAP_MODE_SENTENCES;
    installCorrection(harness.decoder, harness.key, "teh", "the");

    harness.handler.key_up(KeyValue.getKeyByName("."),
        Pointers.Modifiers.EMPTY, null);
    harness.receiver.resetShiftStateUpdates();
    harness.handler._autocap.delayed_callback.run();

    assertEquals("A correction followed by a period must leave the corrected sentence in the editor.",
        "the.", harness.receiver.input.text.toString());
    assertEquals("A correction followed by a period must leave the cursor after that new sentence boundary.",
        4, harness.receiver.input.cursor);
    assertEquals("Correcting a word at a period must still delete the old word exactly once.",
        1, harness.receiver.input.deleteSurroundingCalls);
    assertEquals("Correcting a word at a period must still commit the corrected word and period together once.",
        1, harness.receiver.input.commitTextCalls);
    assertTrue("The period inserted by a correction must schedule fresh sentence caps for the next letter.",
        harness.receiver.shiftState);
    assertEquals("The corrected period must produce one post-replacement caps update.",
        1, harness.receiver.shiftStateCalls);
  }

  @Test
  public void rejected_correction_replacement_commits_literal_separator_without_undo_claim()
      throws Exception
  {
    Harness harness = harness("teh", true, true);
    installCorrection(harness.decoder, harness.key, "teh", "the");
    harness.receiver.input.rejectSurroundingReplacement = true;

    harness.handler.key_up(KeyValue.getSpecialKeyByName("space"),
        Pointers.Modifiers.EMPTY, null);

    assertEquals("If the editor rejects correction replacement, the user's original word and literal separator must remain.",
        "teh ", harness.receiver.input.text.toString());
    assertEquals("Fallback separator insertion must leave the cursor after the user's original word and space.",
        4, harness.receiver.input.cursor);
    assertEquals("A rejected correction must make one failed surrounding-text replacement attempt.",
        1, harness.receiver.input.deleteSurroundingCalls);
    assertEquals("A rejected correction must commit only the literal separator.",
        1, harness.receiver.input.commitTextCalls);

    harness.receiver.input.rejectSurroundingReplacement = false;
    harness.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
        Pointers.Modifiers.EMPTY, null);

    assertEquals("Backspace after a rejected correction must normally remove the separator, not claim an autocorrect undo.",
        "teh", harness.receiver.input.text.toString());
    assertEquals("Normal backspace after fallback must leave the cursor at the literal word end.",
        3, harness.receiver.input.cursor);
    assertEquals("A rejected correction followed by normal backspace must not make an undo replacement commit.",
        1, harness.receiver.input.commitTextCalls);
  }

  @Test
  public void literal_boundary_records_once_after_empty_word_key_rollover()
      throws Exception
  {
    Harness harness = harness("cazoo", false, true);
    installLiteral(harness.decoder, harness.key, "cazoo", true, false);

    harness.handler.handle_space_bar();

    assertEquals("An ordinary literal boundary must remain visible while its typed-word request rolls to the empty word.",
        "cazoo ", harness.receiver.input.text.toString());
    assertFalse("The boundary must revoke the completed word key before its prepared learning record is consumed.",
        harness.decoder.is_current(harness.key));
    awaitCounts(harness.prefs, "cazoo", null, 1, 0);
    assertCountsRemain(harness.prefs, "cazoo", null, 1, 0);
  }

  @Test
  public void changed_candidate_and_autocorrect_commit_once_on_next_action_or_finish()
      throws Exception
  {
    for (boolean candidateClick : new boolean[] { false, true })
      for (boolean finish : new boolean[] { false, true })
      {
        Harness harness = harness("teh", true, true);
        commitChangedReplacement(harness, candidateClick);

        if (finish)
          harness.handler.finished();
        else
        {
          KeyValue next = KeyValue.getKeyByName("x");
          harness.handler.key_down(next, false);
          harness.handler.key_up(next, Pointers.Modifiers.EMPTY, null);
        }

        String origin = candidateClick ? "candidate click" : "separator autocorrect";
        String acceptance = finish ? "an accepted editor finish"
          : "the next non-Backspace key-down/key-up pair";
        awaitCounts(harness.prefs, "the", "teh", 1, 1);
        assertCountsRemain(harness.prefs, "the", "teh", 1, 1);
        assertEquals("A changed " + origin + " followed by " + acceptance
            + " must record the accepted target exactly once.",
            1, wordCount(harness.prefs, "the"));
        assertEquals("A changed " + origin + " followed by " + acceptance
            + " must record the typo pair exactly once.",
            1, correctionCount(harness.prefs, "teh", "the"));
      }
  }

  @Test
  public void pending_replacement_never_blindly_undoes_after_cursor_or_suffix_change()
      throws Exception
  {
    Harness moved = harness("teh", true, true);
    commitChangedReplacement(moved, false);
    moved.receiver.input.setSelection(0, 0);

    moved.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
        Pointers.Modifiers.EMPTY, null);

    assertEquals("Backspace at a moved cursor must not rewrite the corrected suffix at its old insertion point.",
        "the ", moved.receiver.input.text.toString());
    awaitCounts(moved.prefs, "the", "teh", 1, 1);

    Harness changed = harness("teh", true, true);
    commitChangedReplacement(changed, true);
    changed.receiver.input.text.replace(0, changed.receiver.input.text.length(),
        "xyz ");
    changed.receiver.input.cursor = 4;
    changed.receiver.input.selectionStart = 4;
    changed.receiver.input.selectionEnd = 4;

    changed.handler.key_up(KeyValue.getSpecialKeyByName("backspace"),
        Pointers.Modifiers.EMPTY, null);

    assertEquals("A same-position suffix mismatch must fail closed and perform only normal Backspace on the unrelated editor text.",
        "xyz", changed.receiver.input.text.toString());
    awaitCounts(changed.prefs, "the", "teh", 1, 1);
  }

  private static void commitChangedReplacement(Harness harness,
      boolean candidateClick)
      throws Exception
  {
    installCorrection(harness.decoder, harness.key, "teh", "the");
    if (candidateClick)
      harness.handler.suggestion_entered(harness.key, "the");
    else
      harness.handler.handle_space_bar();
  }

  private Harness harness(String text, boolean autocorrect,
      boolean safeEditor)
      throws Exception
  {
    return harness(text, autocorrect, safeEditor, 0);
  }

  private Harness harness(String text, boolean autocorrect,
      boolean safeEditor, int capsMode)
      throws Exception
  {
    return harness(text, autocorrect, safeEditor, capsMode, false);
  }

  private Harness harness(String text, boolean autocorrect,
      boolean safeEditor, int capsMode, boolean termux)
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences(
        "key_event_autocorrect_" + _decoders.size(), Context.MODE_PRIVATE);
    prefs.edit().clear().commit();
    Constructor<Config> constructor = Config.class.getDeclaredConstructor(
        SharedPreferences.class, Resources.class, Boolean.class,
        juloo.keyboard2.dict.Dictionaries.class);
    constructor.setAccessible(true);
    Config config = constructor.newInstance(prefs,
        new TestResources(context.getResources()), Boolean.FALSE, null);
    config.suggestions_enabled = true;
    config.autocorrect_enabled = autocorrect;
    config.autocapitalisation = true;
    config.editor_config.caps_mode = capsMode;
    config.editor_config.caps_initially_enabled = false;
    config.editor_config.caps_initially_updated = false;
    config.editor_config.should_show_candidates_view = true;
    config.editor_config.should_use_typing_assistance = safeEditor;
    config.editor_config.should_use_personalization = !termux;
    config.editor_config.initial_text_before_cursor = text;
    config.editor_config.initial_text_after_cursor = "";
    config.editor_config.initial_sel_start = text.length();
    config.editor_config.initial_sel_end = text.length();

    RecordingReceiver receiver = new RecordingReceiver(text);
    if (termux)
    {
      receiver.editorInfo.inputType = android.text.InputType.TYPE_NULL;
      receiver.editorInfo.packageName = "com.termux";
    }
    final SharedDecoder[] decoder_ref = new SharedDecoder[1];
    final KeyEventHandler[] handler_ref = new KeyEventHandler[1];
    SharedDecoder decoder = new SharedDecoder(receiver.handler,
        new SharedDecoder.Callback()
        {
          @Override
          public void decoder_state_changed(SharedDecoder.Presentation state)
          {
            SharedDecoder active_decoder = decoder_ref[0];
            KeyEventHandler active_handler = handler_ref[0];
            Decoder.Result completed = active_decoder == null
              || state.key == null
              ? null : active_decoder.current_result(state.key);
            if (completed != null && active_handler != null)
              active_handler.decoder_result_ready(completed);
          }
        });
    decoder_ref[0] = decoder;
    _decoders.add(decoder);
    long session = decoder.start_session(
        new Decoder.DecoderConfig(true, autocorrect, true, safeEditor),
        SharedDecoder.ResourceSpec.empty("empty"), null,
        new SharedDecoder.PersonalizationSpec(
          "test-" + _decoders.size(), prefs));
    KeyEventHandler handler = new KeyEventHandler(receiver, decoder);
    handler_ref[0] = handler;
    config.handler = handler;
    handler.started(config, session);

    Decoder.RequestKey key = decoder.current_key();
    if (key == null)
    {
      key = decoder.request(session, snapshot(1, text, false));
      handler._current_request_key = key;
    }
    if (safeEditor)
      awaitResult(decoder, key);
    return new Harness(config, receiver, prefs, decoder, handler, session, key);
  }

  private static Decoder.Result correctionResult(Decoder.RequestKey key,
      String queried, String corrected)
      throws Exception
  {
    Decoder.Candidate literal = candidate(Decoder.normalize(queried), queried,
        Decoder.SOURCE_LITERAL, 8192, 0, 0, false, false,
        Decoder.Role.ENTERED_LITERAL);
    Decoder.Candidate correction = candidate(Decoder.normalize(corrected),
        corrected, Decoder.SOURCE_CDICT_SPATIAL, 0, 1,
        Decoder.EDIT_TRANSPOSITION, true, false, Decoder.Role.WORD);
    return result(key, queried,
        new Decoder.Candidate[] { correction, literal }, literal, correction);
  }

  private static void installCorrection(SharedDecoder decoder,
      Decoder.RequestKey key, String queried, String corrected)
      throws Exception
  {
    installResult(decoder, correctionResult(key, queried, corrected));
  }

  private static void installLiteral(SharedDecoder decoder,
      Decoder.RequestKey key, String word, boolean recognized, boolean learned)
      throws Exception
  {
    Decoder.Candidate literal = candidate(Decoder.normalize(word), word,
        Decoder.SOURCE_LITERAL, 0, 0, 0, recognized, learned,
        Decoder.Role.ENTERED_LITERAL);
    installResult(decoder, result(key, word,
          new Decoder.Candidate[] { literal }, literal, null));
  }

  private static Decoder.Result result(Decoder.RequestKey key, String queried,
      Decoder.Candidate[] words, Decoder.Candidate literal,
      Decoder.Candidate correction)
      throws Exception
  {
    Constructor<Decoder.Result> constructor = Decoder.Result.class
      .getDeclaredConstructor(Decoder.RequestKey.class, String.class,
          Decoder.Candidate[].class, String.class, Decoder.Candidate.class,
          Decoder.Candidate.class, boolean.class, Decoder.Failure.class);
    constructor.setAccessible(true);
    return constructor.newInstance(key, queried, words, null, literal,
        correction, true, Decoder.Failure.NONE);
  }

  private static void installResult(SharedDecoder decoder,
      Decoder.Result result)
      throws Exception
  {
    for (String fieldName : new String[] {
        "_acceptedResult", "_lastCompletedResult" })
    {
      Field field = SharedDecoder.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(decoder, result);
    }
  }
  private static void clearResult(SharedDecoder decoder)
      throws Exception
  {
    for (String fieldName : new String[] {
        "_acceptedResult", "_lastCompletedResult" })
    {
      Field field = SharedDecoder.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(decoder, null);
    }
  }


  private static Decoder.Candidate candidate(String canonical, String surface,
      int sourceMask, int totalQ8, int editCount, int editMask,
      boolean recognized, boolean learned, Decoder.Role role)
      throws Exception
  {
    Constructor<Decoder.Candidate> constructor = Decoder.Candidate.class
      .getDeclaredConstructor(String.class, String.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class, int.class,
          int.class, int.class, int.class, int.class, int.class,
          Decoder.Role.class, boolean.class, boolean.class, boolean.class);
    constructor.setAccessible(true);
    return constructor.newInstance(canonical, surface, sourceMask, -1, 0, 0,
        learned ? 1 : 0, 0, 0, 0, 0, 0, editCount, editMask, totalQ8, role,
        recognized, learned, true);
  }

  private static CurrentlyTypedWord.Snapshot snapshot(long revision,
      String word, boolean selection)
      throws Exception
  {
    Constructor<CurrentlyTypedWord.Snapshot> constructor =
      CurrentlyTypedWord.Snapshot.class.getDeclaredConstructor(long.class,
          String.class, int.class, boolean.class, TouchTrace.Snapshot.class);
    constructor.setAccessible(true);
    return constructor.newInstance(revision, word, 0, selection,
        new TouchTrace().snapshot());
  }

  private static Decoder.Result awaitResult(SharedDecoder decoder,
      Decoder.RequestKey key)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Decoder.Result result = decoder.current_result(key);
      if (result != null)
        return result;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for decoder fixture result");
    return null;
  }

  private static void awaitCounts(SharedPreferences prefs, String target,
      String source, int expectedWordCount, int expectedCorrectionCount)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      if (wordCount(prefs, target) == expectedWordCount
          && correctionCount(prefs, source, target) == expectedCorrectionCount)
        return;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for personalization counts target=" + target
        + " source=" + source + " expected=" + expectedWordCount + "/"
        + expectedCorrectionCount + " actual=" + wordCount(prefs, target)
        + "/" + correctionCount(prefs, source, target));
  }

  private static void assertCountsRemain(SharedPreferences prefs,
      String target, String source, int expectedWordCount,
      int expectedCorrectionCount)
      throws Exception
  {
    long deadline = System.nanoTime() + 100_000_000L;
    do
    {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals("The accepted target count must remain stable.",
          expectedWordCount, wordCount(prefs, target));
      assertEquals("The typo-pair count must remain stable.",
          expectedCorrectionCount, correctionCount(prefs, source, target));
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
  }

  private static int wordCount(SharedPreferences prefs, String word)
      throws Exception
  {
    PersonalizationStore store = new PersonalizationStore(prefs);
    Method method = PersonalizationStore.class.getDeclaredMethod(
        "word_count", String.class);
    method.setAccessible(true);
    return ((Integer)method.invoke(store, word)).intValue();
  }

  private static int correctionCount(SharedPreferences prefs, String source,
      String target)
      throws Exception
  {
    if (source == null)
      return 0;
    PersonalizationStore store = new PersonalizationStore(prefs);
    Method method = PersonalizationStore.class.getDeclaredMethod(
        "correction_count", String.class, String.class);
    method.setAccessible(true);
    return ((Integer)method.invoke(store, source, target)).intValue();
  }

  private static final class Harness
  {
    final Config config;
    final RecordingReceiver receiver;
    final SharedPreferences prefs;
    final SharedDecoder decoder;
    final KeyEventHandler handler;
    final long session;
    final Decoder.RequestKey key;

    Harness(Config config_, RecordingReceiver receiver_, SharedPreferences prefs_,
        SharedDecoder decoder_, KeyEventHandler handler_, long session_,
        Decoder.RequestKey key_)
    {
      config = config_;
      receiver = receiver_;
      prefs = prefs_;
      decoder = decoder_;
      handler = handler_;
      session = session_;
      key = key_;
    }
  }

  private static final class Gate
  {
    final String name;
    final boolean autocorrect;
    final boolean safeEditor;

    Gate(String name_, boolean autocorrect_, boolean safeEditor_)
    {
      name = name_;
      autocorrect = autocorrect_;
      safeEditor = safeEditor_;
    }
  }

  private static final class Boundary
  {
    final String name;
    final KeyValue key;
    final String separator;

    Boundary(String name_, KeyValue key_, String separator_)
    {
      name = name_;
      key = key_;
      separator = separator_;
    }
  }

  private static final class RecordingReceiver
      implements KeyEventHandler.IReceiver
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final RecordingInputConnection input;
    final EditorInfo editorInfo = new EditorInfo();
    boolean shiftState;
    int shiftStateCalls;

    RecordingReceiver(String text)
    {
      input = new RecordingInputConnection(text);
    }

    void resetShiftStateUpdates()
    {
      shiftStateCalls = 0;
    }

    @Override public void handle_event_key(KeyValue.Event event) {}
    @Override public void set_shift_state(boolean state, boolean lock)
    {
      shiftState = state;
      ++shiftStateCalls;
    }
    @Override public void set_compose_pending(boolean pending) {}
    @Override public void selection_state_changed(boolean ongoing) {}
    @Override public RecordingInputConnection getCurrentInputConnection()
    {
      return input;
    }
    @Override public EditorInfo getCurrentInputEditorInfo()
    {
      return editorInfo;
    }
    @Override public Handler getHandler()
    {
      return handler;
    }
  }

  private static final class RecordingInputConnection
      extends BaseInputConnection
  {
    final StringBuilder text = new StringBuilder();
    int cursor;
    int selectionStart;
    int selectionEnd;
    int extractedStartOffset;
    int cursorCapsMode;
    int commitTextCalls;
    int deleteSurroundingCalls;
    int commitCorrectionCalls;
    CorrectionInfo correctionInfo;
    boolean rejectSurroundingReplacement;
    int delKeyDowns;

    RecordingInputConnection(String initial)
    {
      super(new View(RuntimeEnvironment.getApplication()), false);
      text.append(initial);
      cursor = text.length();
      selectionStart = cursor;
      selectionEnd = cursor;
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request,
        int flags)
    {
      ExtractedText out = new ExtractedText();
      out.text = text.toString();
      out.startOffset = extractedStartOffset;
      out.selectionStart = selectionStart;
      out.selectionEnd = selectionEnd;
      return out;
    }

    @Override
    public int getCursorCapsMode(int reqModes)
    {
      return cursorCapsMode & reqModes;
    }

    @Override public CharSequence getTextBeforeCursor(int length, int flags)
    {
      return text.substring(Math.max(0, cursor - length), cursor);
    }

    @Override public CharSequence getTextAfterCursor(int length, int flags)
    {
      return text.substring(cursor, Math.min(text.length(), cursor + length));
    }

    @Override public CharSequence getSelectedText(int flags)
    {
      return text.substring(Math.min(selectionStart, selectionEnd),
          Math.max(selectionStart, selectionEnd));
    }

    @Override public boolean setSelection(int start, int end)
    {
      if (start < 0 || end < 0 || start > text.length() || end > text.length())
        return false;
      selectionStart = start;
      selectionEnd = end;
      cursor = end;
      return true;
    }

    @Override
    public boolean deleteSurroundingText(int before, int after)
    {
      deleteSurroundingCalls++;
      if (rejectSurroundingReplacement)
        return false;
      int start = Math.max(0, cursor - before);
      int end = Math.min(text.length(), cursor + after);
      text.delete(start, end);
      cursor = start;
      selectionStart = cursor;
      selectionEnd = cursor;
      return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int before, int after)
    {
      return deleteSurroundingText(before, after);
    }

    @Override
    public boolean commitText(CharSequence value, int newCursorPosition)
    {
      commitTextCalls++;
      int start = Math.min(selectionStart, selectionEnd);
      int end = Math.max(selectionStart, selectionEnd);
      text.replace(start, end, value.toString());
      cursor = start + value.length();
      selectionStart = cursor;
      selectionEnd = cursor;
      return true;
    }

    @Override
    public boolean commitCorrection(CorrectionInfo info)
    {
      commitCorrectionCalls++;
      correctionInfo = info;
      return true;
    }

    @Override public boolean beginBatchEdit() { return true; }
    @Override public boolean endBatchEdit() { return true; }
    @Override public boolean finishComposingText() { return true; }
    @Override public boolean sendKeyEvent(KeyEvent event)
    {
      if (event.getKeyCode() == KeyEvent.KEYCODE_DEL
          && event.getAction() == KeyEvent.ACTION_DOWN && cursor > 0)
      {
        delKeyDowns++;
        int start = text.offsetByCodePoints(cursor, -1);
        text.delete(start, cursor);
        cursor = start;
        selectionStart = cursor;
        selectionEnd = cursor;
      }
      return true;
    }
  }

  private static final class TestResources extends Resources
  {
    TestResources(Resources base)
    {
      super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
    }

    @Override public float getDimension(int id) { return 1f; }
  }
}
