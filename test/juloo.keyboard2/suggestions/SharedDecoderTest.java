package juloo.keyboard2.suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.CurrentlyTypedWord;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.TouchTrace;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class SharedDecoderTest
{
  private final List<SharedDecoder> _decoders = new ArrayList<SharedDecoder>();

  @After
  public void tearDown()
  {
    for (SharedDecoder decoder : _decoders)
      decoder.close();
  }

  @Test
  public void latest_request_wins_and_only_latest_ready_state_is_delivered()
      throws Exception
  {
    QueuedHandler handler = new QueuedHandler();
    RecordingCallback callback = new RecordingCallback();
    SharedDecoder decoder = decoder(handler, callback);
    long session = start(decoder, enabledConfig());

    Decoder.RequestKey first = decoder.request(session, snapshot(1, "ca", false));
    Decoder.RequestKey second = decoder.request(session, snapshot(2, "cab", false));
    Decoder.RequestKey latest = decoder.request(session, snapshot(3, "cabin", false));

    assertFalse("A superseded keystroke must stop being a valid click or correction target immediately.",
        decoder.is_current(first));
    assertFalse("Only one pending request may survive the latest-wins mailbox.",
        decoder.is_current(second));
    assertTrue(decoder.is_current(latest));
    SharedDecoder.Presentation ready = awaitReady(decoder, latest);
    assertEquals("The accepted result must belong to the exact latest immutable word snapshot.",
        "cabin", ready.result.queriedWord);
    assertNull("A stale result must never be returned through its old request key.",
        decoder.current_result(first));
    assertNull("A replaced pending result must never be returned through its old request key.",
        decoder.current_result(second));

    handler.drain();
    assertFalse("Coalesced main-thread delivery must still publish the latest state.",
        callback.states.isEmpty());
    for (SharedDecoder.Presentation delivered : callback.states)
      if (delivered.state == SharedDecoder.Presentation.State.READY)
        assertEquals("Out-of-order worker/main delivery must reject every stale READY result.",
            latest, delivered.key);
  }

  @Test
  public void delayed_callback_reads_latest_state_instead_of_replaying_stale_ready()
      throws Exception
  {
    QueuedHandler handler = new QueuedHandler();
    RecordingCallback callback = new RecordingCallback();
    SharedDecoder decoder = decoder(handler, callback);
    long session = start(decoder, enabledConfig());

    Decoder.RequestKey stale = decoder.request(session, snapshot(1, "alpha", false));
    awaitReady(decoder, stale);
    Decoder.RequestKey current = decoder.request(session, snapshot(2, "beta", false));
    awaitReady(decoder, current);

    handler.drain();

    assertFalse("The queued callback must not expose a READY result that became stale before the main thread consumed it.",
        containsReady(callback.states, stale));
    assertTrue("The main thread must receive the replacement READY result after stale delivery is rejected.",
        containsReady(callback.states, current));
  }

  @Test
  public void every_session_resource_layout_config_and_personalization_epoch_invalidates_old_key()
      throws Exception
  {
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig());
    Decoder.RequestKey initial = decoder.request(session, snapshot(1, "epoch", false));

    KeyboardData alternateLayout = KeyboardData.load_string_exn(
        "<keyboard bottom_row=\"false\"><row><key c=\"a\"/><key c=\"z\"/></row></keyboard>");
    decoder.update_layout(session, alternateLayout);
    Decoder.RequestKey layout = decoder.current_key();
    assertInvalidated(decoder, initial, layout,
        "Installing a different visible layout must invalidate geometry-dependent results.");
    assertTrue(layout.layoutEpoch > initial.layoutEpoch);

    decoder.update_resources(session, SharedDecoder.ResourceSpec.empty("r2"));
    Decoder.RequestKey resources = decoder.current_key();
    assertInvalidated(decoder, layout, resources,
        "Changing dictionaries or language resources must invalidate old provider results.");
    assertTrue(resources.resourceEpoch > layout.resourceEpoch);

    decoder.update_config(session,
        new Decoder.DecoderConfig(true, false, true, true));
    Decoder.RequestKey config = decoder.current_key();
    assertInvalidated(decoder, resources, config,
        "Changing suggestion/autocorrect policy must invalidate old click and correction targets.");
    assertTrue(config.configEpoch > resources.configEpoch);

    decoder.update_personalization(session,
        SharedDecoder.PersonalizationSpec.empty("profile-2"));
    Decoder.RequestKey personalization = decoder.current_key();
    assertInvalidated(decoder, config, personalization,
        "Switching personalization domains must invalidate old personalized rankings.");
    assertTrue(personalization.personalizationEpoch
        > config.personalizationEpoch);

    long replacementSession = decoder.start_session(enabledConfig(),
        SharedDecoder.ResourceSpec.empty("r2"), alternateLayout,
        SharedDecoder.PersonalizationSpec.empty("profile-2"));
    assertTrue("A new editor must receive a distinct session epoch.",
        replacementSession > session);
    assertFalse("Candidate targets must never cross editor sessions.",
        decoder.is_current(personalization));

    Decoder.RequestKey replacement = decoder.request(replacementSession,
        snapshot(2, "fresh", false));
    decoder.finish_session(replacementSession);
    assertFalse("Finishing an editor must invalidate its last candidate row immediately.",
        decoder.is_current(replacement));
    assertNull(decoder.current_key());
  }

  @Test
  public void assistance_selection_and_display_gates_never_publish_clickable_candidates()
      throws Exception
  {
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());

    long unsafeSession = start(decoder,
        new Decoder.DecoderConfig(true, true, true, false));
    Decoder.RequestKey unsafe = decoder.request(unsafeSession,
        snapshot(1, "private", false));
    assertEmpty(decoder, unsafe,
        "Unsafe editor contexts must not start decoding or expose clickable candidates.");

    long selectedSession = start(decoder, enabledConfig());
    Decoder.RequestKey selected = decoder.request(selectedSession,
        snapshot(2, "selected", true));
    assertEmpty(decoder, selected,
        "A non-empty selection must suppress candidates and autocorrect until text tracking is unambiguous.");

    long disabledSession = start(decoder,
        new Decoder.DecoderConfig(false, false, false, true));
    Decoder.RequestKey disabled = decoder.request(disabledSession,
        snapshot(3, "disabled", false));
    assertEmpty(decoder, disabled,
        "Disabling both suggestions and autocorrect must leave no stale result behind.");

    long hiddenSession = start(decoder,
        new Decoder.DecoderConfig(false, true, false, true));
    Decoder.RequestKey hidden = decoder.request(hiddenSession,
        snapshot(4, "autocorrect", false));
    awaitResult(decoder, hidden);
    assertEquals("Autocorrect may decode in the background while the candidate strip is disabled, but the presentation must stay non-clickable.",
        SharedDecoder.Presentation.State.EMPTY,
        decoder.current_presentation().state);
  }

  @Test
  public void explicit_invalidation_rejects_clicks_and_completed_results_immediately()
      throws Exception
  {
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig());
    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "invalidate", false));
    awaitReady(decoder, key);

    decoder.invalidate(session);

    assertFalse("Cursor moves and unsafe transitions must revoke the old request key synchronously.",
        decoder.is_current(key));
    assertNull("Invalidation must also revoke an already completed correction result.",
        decoder.current_result(key));
    assertEquals(SharedDecoder.Presentation.State.EMPTY,
        decoder.current_presentation().state);
  }

  @Test
  public void passive_prewarm_reuses_resources_until_the_resource_key_changes()
      throws Exception
  {
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    SharedPreferences preferences = RuntimeEnvironment.getApplication()
      .getSharedPreferences("shared_decoder_prewarm_test",
          Context.MODE_PRIVATE);
    preferences.edit().clear().commit();
    SharedDecoder.ResourceSpec warmedResources = resources("en:1");
    SharedDecoder.PersonalizationSpec warmedPersonalization =
      new SharedDecoder.PersonalizationSpec("profile:1", preferences);

    decoder.prewarm(warmedResources, warmedPersonalization);

    assertNull("Passive prewarm must prepare worker resources without inventing an editor request key.",
        decoder.current_key());
    assertEquals("Passive prewarm must not expose a candidate presentation before an editor starts.",
        SharedDecoder.Presentation.State.EMPTY,
        decoder.current_presentation().state);

    long firstSession = decoder.start_session(enabledConfig(),
        warmedResources, null, warmedPersonalization);
    Decoder.RequestKey firstWord = decoder.request(firstSession,
        snapshot(1, "first", false));

    assertEquals("The first editor must reuse the resource epoch installed by prewarm instead of reinstalling the same language.",
        1L, firstWord.resourceEpoch);
    SharedDecoder.Presentation firstReady = awaitReady(decoder, firstWord);
    assertEquals("Cold resource installation must not strand the first typed word in PENDING.",
        SharedDecoder.Presentation.State.READY, firstReady.state);
    assertEquals("The first accepted result must still belong to the word typed while prewarm and session startup raced.",
        "first", firstReady.result.queriedWord);

    SharedDecoder.ResourceSpec sameKeyReplacement = resources("en:1");
    SharedDecoder.PersonalizationSpec sameProfileReplacement =
      new SharedDecoder.PersonalizationSpec("profile:1", preferences);
    long replacementSession = decoder.start_session(enabledConfig(),
        sameKeyReplacement, null, sameProfileReplacement);
    Decoder.RequestKey replacementWord = decoder.request(replacementSession,
        snapshot(2, "second", false));

    assertEquals("Replacing an editor with the same language key must preserve the warmed resource epoch.",
        firstWord.resourceEpoch, replacementWord.resourceEpoch);
    awaitReady(decoder, replacementWord);

    long changedSession = decoder.start_session(enabledConfig(),
        resources("fr:1"), null, sameProfileReplacement);
    Decoder.RequestKey changedWord = decoder.request(changedSession,
        snapshot(3, "third", false));

    assertTrue("Selecting a different resource key must advance the epoch so old dictionary results cannot cross languages.",
        changedWord.resourceEpoch > replacementWord.resourceEpoch);
    awaitReady(decoder, changedWord);
  }

  @Test
  public void prepare_commit_requires_the_exact_current_request_key()
      throws Exception
  {
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig());

    Decoder.RequestKey superseded = decoder.request(session,
        snapshot(1, "alpha", false));
    Decoder.RequestKey current = decoder.request(session,
        snapshot(2, "beta", false));

    assertNull("A superseded request must never authorize a later editor commit.",
        decoder.prepare_commit(session, superseded, "alpha", null));
    assertNull("A request key from the active editor must still be rejected when paired with a different session epoch.",
        decoder.prepare_commit(session + 1, current, "beta", null));
    assertNotNull("The exact current request may be captured before editor mutation.",
        decoder.prepare_commit(session, current, "beta", null));

    decoder.invalidate(session);
    assertNull("Explicit invalidation must revoke prepare-time authority immediately.",
        decoder.prepare_commit(session, current, "beta", null));
  }

  @Test
  public void prepared_word_survives_empty_word_rollover_and_is_consumed_once()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_commit_rollover_test");
    PersonalizationStore seeded = new PersonalizationStore(preferences);
    seeded.record_word("hello");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-rollover");

    Decoder.RequestKey wordKey = decoder.request(session,
        snapshot(1, "hello", false));
    awaitReady(decoder, wordKey);
    SharedDecoder.CommitToken token = decoder.prepare_commit(session, wordKey,
        "hello", null);
    assertNotNull(token);

    Decoder.RequestKey boundaryKey = decoder.request(session,
        snapshot(2, "", false));
    assertTrue("The empty-word boundary request must become current without revoking an already prepared word token.",
        decoder.is_current(boundaryKey));

    decoder.commit_prepared(token);
    decoder.commit_prepared(token);

    Decoder.RequestKey verificationKey = decoder.request(session,
        snapshot(3, "hello", false));
    Decoder.Result verification = awaitReady(decoder, verificationKey).result;
    assertNotNull(verification.literal);
    assertEquals("The prepared word must be recorded exactly once even when duplicate commit delivery follows a boundary rollover.",
        2, verification.literal.unigramCount);
    assertEquals("One-shot token consumption must also be durable in the backing store.",
        2, new PersonalizationStore(preferences).word_count("hello"));
  }

  @Test
  public void prepared_commit_rejects_replaced_sessions_and_personalization_domains()
      throws Exception
  {
    SharedPreferences firstPreferences = preferences(
        "shared_decoder_commit_domain_first_test");
    new PersonalizationStore(firstPreferences).record_word("alpha");
    SharedPreferences secondPreferences = preferences(
        "shared_decoder_commit_domain_second_test");
    new PersonalizationStore(secondPreferences).record_word("alpha");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long firstSession = start(decoder, enabledConfig(), firstPreferences,
        "profile-first");

    Decoder.RequestKey firstKey = decoder.request(firstSession,
        snapshot(1, "alpha", false));
    awaitReady(decoder, firstKey);
    SharedDecoder.CommitToken replacedSessionToken = decoder.prepare_commit(
        firstSession, firstKey, "alpha", null);
    assertNotNull(replacedSessionToken);

    long replacementSession = start(decoder, enabledConfig(), firstPreferences,
        "profile-first");
    decoder.commit_prepared(replacedSessionToken);
    Decoder.RequestKey replacementKey = decoder.request(replacementSession,
        snapshot(2, "alpha", false));
    awaitReady(decoder, replacementKey);
    assertEquals("A token captured by an earlier editor session must not mutate the reused personalization store.",
        1, new PersonalizationStore(firstPreferences).word_count("alpha"));

    SharedDecoder.CommitToken replacedDomainToken = decoder.prepare_commit(
        replacementSession, replacementKey, "alpha", null);
    assertNotNull(replacedDomainToken);
    decoder.update_personalization(replacementSession,
        new SharedDecoder.PersonalizationSpec("profile-second",
          secondPreferences));
    Decoder.RequestKey switchedDomainKey = decoder.current_key();
    assertNotNull(switchedDomainKey);
    assertFalse(replacementKey.equals(switchedDomainKey));

    decoder.commit_prepared(replacedDomainToken);
    awaitReady(decoder, switchedDomainKey);
    assertEquals("Changing domains must reject the old token rather than writing back into the retired profile.",
        1, new PersonalizationStore(firstPreferences).word_count("alpha"));
    assertEquals("Changing domains must also prevent the old token from leaking into the replacement profile.",
        1, new PersonalizationStore(secondPreferences).word_count("alpha"));
  }

  @Test
  public void candidate_evidence_requires_the_exact_result_and_record_precedes_rerank()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_candidate_provenance_test");
    PersonalizationStore seeded = new PersonalizationStore(preferences);
    seeded.record_commit("the", "teh");
    seeded.record_word("ten");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-candidate");

    Decoder.RequestKey initialKey = decoder.request(session,
        snapshot(1, "teh", false));
    Decoder.Result initial = awaitReady(decoder, initialKey).result;
    Decoder.Candidate accepted = candidate(initial, "the");
    assertNotNull("The fixture must expose the learned correction target in the exact accepted result.",
        accepted);
    assertNull("A learned word that was not returned by this result is not candidate provenance.",
        candidate(initial, "ten"));
    assertEquals(1, accepted.exactCorrectionCount);

    SharedDecoder.CommitToken forgedTarget = decoder.prepare_commit(session,
        initialKey, "ten", "teh");
    assertNotNull("Invalid correction provenance must suppress only the pair evidence, not the otherwise valid word token.",
        forgedTarget);
    decoder.commit_prepared(forgedTarget);
    Decoder.RequestKey afterForgedKey = decoder.current_key();
    assertFalse(initialKey.equals(afterForgedKey));
    Decoder.Result afterForged = awaitReady(decoder, afterForgedKey).result;
    assertEquals("A target absent from the exact accepted result must not create correction-pair evidence.",
        0, new PersonalizationStore(preferences)
          .correction_count("teh", "ten"));
    assertEquals("Rejected provenance must leave the real correction evidence unchanged.",
        1, candidate(afterForged, "the").exactCorrectionCount);

    SharedDecoder.CommitToken provenTarget = decoder.prepare_commit(session,
        afterForgedKey, "the", "teh");
    assertNotNull(provenTarget);
    decoder.commit_prepared(provenTarget);
    Decoder.RequestKey personalizedKey = decoder.current_key();
    assertFalse(afterForgedKey.equals(personalizedKey));
    Decoder.Candidate reranked = candidate(
        awaitReady(decoder, personalizedKey).result, "the");

    assertNotNull(reranked);
    assertEquals("The worker must serialize RECORD before decoding the resubmitted personalized request.",
        2, reranked.exactCorrectionCount);
    assertTrue("The next accepted result must expose the stronger correction weight produced by the preceding RECORD.",
        reranked.correctionWeight > accepted.correctionWeight);
    assertEquals(2, new PersonalizationStore(preferences)
        .correction_count("teh", "the"));
  }

  @Test
  public void correction_only_candidate_acceptance_strengthens_pair_without_teaching_word()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_correction_only_candidate_test");
    PersonalizationStore seeded = new PersonalizationStore(preferences);
    for (int i = 0; i < 4; i++)
      seeded.record_correction("thus", "this");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-correction-only-candidate");

    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "thus", false));
    Decoder.Candidate accepted =
      candidate(awaitReady(decoder, key).result, "this");
    assertNotNull("Four editor-verified corrections must recall the target without dictionary or unigram evidence.",
        accepted);
    assertFalse(accepted.recognized);
    assertEquals(0, accepted.unigramCount);

    SharedDecoder.CommitToken token = decoder.prepare_commit(session, key,
        "this", "thus");
    assertNotNull(token);
    decoder.commit_prepared(token);
    awaitReady(decoder, decoder.current_key());

    PersonalizationStore stored = new PersonalizationStore(preferences);
    assertEquals("Accepting a correction-only candidate must strengthen that exact pair.",
        5, stored.correction_count("thus", "this"));
    assertEquals("Correction-only candidate presentation must not promote an unknown target into ordinary word history.",
        0, stored.word_count("this"));
  }

  @Test
  public void accepted_two_edit_candidate_strengthens_only_its_exact_pair()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_two_edit_candidate_test");
    PersonalizationStore seeded = new PersonalizationStore(preferences);
    for (int i = 0; i < 4; i++)
      seeded.record_correction("thxz", "this");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-two-edit-candidate");

    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "thxz", false));
    Decoder.Candidate accepted =
      candidate(awaitReady(decoder, key).result, "this");
    assertNotNull(accepted);
    assertTrue("Exact candidate provenance must use the bounded textual pair rather than geometry-priced decoder edit count.",
        PersonalizationStore.is_plausible_correction("thxz", "this"));

    SharedDecoder.CommitToken token = decoder.prepare_commit(session, key,
        "this", "thxz");
    assertNotNull(token);
    decoder.commit_prepared(token);
    awaitReady(decoder, decoder.current_key());

    PersonalizationStore stored = new PersonalizationStore(preferences);
    assertEquals("Accepting the exact two-edit candidate must strengthen its pair after provenance validation.",
        5, stored.correction_count("thxz", "this"));
    assertEquals(0, stored.word_count("this"));
  }

  @Test
  public void fold_equivalent_accent_autocorrection_keeps_commit_provenance()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_accent_correction_test");
    PersonalizationStore seeded = new PersonalizationStore(preferences);
    for (int i = 0; i < 4; i++)
      seeded.record_correction("resume", "re\u0301sume\u0301");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-accent-correction");

    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "resume", false));
    Decoder.Result result = awaitReady(decoder, key).result;
    assertNotNull("The exact accent-bearing target must remain an autocorrection even though it shares the literal's folded lookup key.",
        result.autocorrection);
    assertEquals("résumé", result.autocorrection.surface);
    assertEquals("resume", result.autocorrection.canonical);

    SharedDecoder.CommitToken forged = decoder.prepare_commit(session, key,
        "resumé", "resume");
    assertNotNull(forged);
    decoder.commit_prepared(forged);
    key = decoder.current_key();
    awaitReady(decoder, key);
    PersonalizationStore stored = new PersonalizationStore(preferences);
    assertEquals("A fold-equivalent surface that was not emitted by the accepted result must not gain correction evidence.",
        0, stored.correction_count("resume", "resumé"));
    assertEquals(4, stored.correction_count("resume", "résumé"));

    SharedDecoder.CommitToken token = decoder.prepare_commit(session, key,
        "résumé", "resume");
    assertNotNull("Fold-equivalent correction output must retain accepted-result provenance through prepare_commit.",
        token);
    decoder.commit_prepared(token);
    awaitReady(decoder, decoder.current_key());

    stored = new PersonalizationStore(preferences);
    assertEquals("Committing the emitted accent correction must strengthen the exact surface pair.",
        5, stored.correction_count("resume", "résumé"));
  }

  @Test
  public void unrecognized_correction_resets_phrase_context_before_next_word()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_unknown_context_reset_test");
    PersonalizationStore seeded = new PersonalizationStore(preferences);
    seeded.record_word("alpha");
    seeded.reset_context();
    seeded.record_word("beta");
    seeded.reset_context();
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-unknown-context-reset");

    Decoder.RequestKey alphaKey = decoder.request(session,
        snapshot(1, "alpha", false));
    awaitReady(decoder, alphaKey);
    decoder.commit_prepared(decoder.prepare_commit(session, alphaKey,
          "alpha", null));
    awaitReady(decoder, decoder.current_key());

    Decoder.RequestKey unknownKey = decoder.request(session,
        snapshot(2, "this", false));
    awaitReady(decoder, unknownKey);
    decoder.commit_prepared(decoder.prepare_commit(session, unknownKey,
          "this", "thus"));
    awaitReady(decoder, decoder.current_key());

    Decoder.RequestKey betaKey = decoder.request(session,
        snapshot(3, "beta", false));
    awaitReady(decoder, betaKey);
    decoder.commit_prepared(decoder.prepare_commit(session, betaKey,
          "beta", null));
    awaitReady(decoder, decoder.current_key());

    PersonalizationStore stored = new PersonalizationStore(preferences);
    assertEquals(1, stored.correction_count("thus", "this"));
    assertEquals(0, stored.word_count("this"));
    assertEquals("An unknown corrected token must break phrase context rather than creating a false alpha-to-beta next-word association.",
        0, stored.bigram_count("alpha", "beta"));
    assertEquals(0, stored.bigram_count("alpha", "this"));
    assertEquals(0, stored.bigram_count("this", "beta"));
  }

  @Test
  public void manual_correction_requires_decodable_request_not_dictionary_recognition()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_manual_provenance_test");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-manual");

    Decoder.RequestKey suppressedKey = decoder.request(session,
        snapshot(1, "the", true));
    assertNull("A selected word intentionally has no accepted decoder result.",
        decoder.current_result(suppressedKey));
    SharedDecoder.CommitToken unproven = decoder.prepare_commit(session,
        suppressedKey, "the", "tge");
    assertNotNull(unproven);
    decoder.commit_prepared(unproven);

    Decoder.RequestKey provenKey = decoder.request(session,
        snapshot(2, "the", false));
    Decoder.Result provenResult = awaitReady(decoder, provenKey).result;
    assertNotNull(provenResult.literal);
    assertFalse("The fixture must prove correction-only learning without a dictionary or pre-learned target.",
        provenResult.literal.recognized || provenResult.literal.learned);
    assertEquals("A non-decodable selected request must not create correction-pair evidence.",
        0, new PersonalizationStore(preferences)
          .correction_count("tge", "the"));

    SharedDecoder.CommitToken proven = decoder.prepare_commit(session,
        provenKey, "the", "tge");
    assertNotNull(proven);
    decoder.commit_prepared(proven);
    awaitReady(decoder, decoder.current_key());

    PersonalizationStore stored = new PersonalizationStore(preferences);
    assertEquals("The decodable editor-verified manual correction must persist without dictionary recognition.",
        1, stored.correction_count("tge", "the"));
    assertEquals("Correction-only evidence must not turn arbitrary unrecognized commits into ordinary learned words.",
        0, stored.word_count("the"));
  }

  @Test
  public void manual_correction_token_does_not_wait_for_completed_result()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_manual_race_test");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder, enabledConfig(), preferences,
        "profile-manual-race");
    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "this", false));
    awaitReady(decoder, key);
    for (String fieldName : new String[] {
        "_acceptedResult", "_lastCompletedResult" })
    {
      Field field = SharedDecoder.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(decoder, null);
    }

    SharedDecoder.CommitToken token = decoder.prepare_commit(session, key,
        "this", "thus");
    assertNotNull("An exact current decodable request must prepare before its result is available.",
        token);
    decoder.commit_prepared(token);
    awaitReady(decoder, decoder.current_key());

    PersonalizationStore stored = new PersonalizationStore(preferences);
    assertEquals("Immediate Space after the final corrected letter must not lose the typo pair.",
        1, stored.correction_count("thus", "this"));
    assertEquals(0, stored.word_count("this"));
  }

  @Test
  public void hidden_suggestions_do_not_disable_autocorrect_learning()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_hidden_suggestions_learning_test");
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    Decoder.DecoderConfig autocorrectOnly =
      new Decoder.DecoderConfig(false, true, true, true);
    long session = start(decoder, autocorrectOnly, preferences,
        "profile-autocorrect-only");
    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "this", false));
    awaitResult(decoder, key);

    SharedDecoder.CommitToken token = decoder.prepare_commit(session, key,
        "this", "thus");
    assertNotNull("Hiding candidate display must not disable learning while Autocorrect remains active.",
        token);
    decoder.commit_prepared(token);
    awaitResult(decoder, decoder.current_key());

    assertEquals(1, new PersonalizationStore(preferences)
        .correction_count("thus", "this"));
  }

  @Test
  public void inactive_clear_converges_worker_and_store_while_stale_session_is_rejected()
      throws Exception
  {
    SharedPreferences preferences = preferences(
        "shared_decoder_inactive_clear_test");
    PersonalizationStore seeded = new PersonalizationStore(preferences);
    seeded.record_commit("alpha", "alpga");
    seeded.record_commit("beta", null);
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    SharedDecoder.ResourceSpec warmedResources = resources("en:clear");
    SharedDecoder.PersonalizationSpec warmedPersonalization =
      new SharedDecoder.PersonalizationSpec("profile:clear", preferences);
    decoder.prewarm(warmedResources, warmedPersonalization);

    long firstSession = decoder.start_session(enabledConfig(), warmedResources,
        null, warmedPersonalization);
    Decoder.Result first = awaitReady(decoder, decoder.request(firstSession,
          snapshot(1, "alpha", false))).result;
    assertTrue(first.literal.learned);
    decoder.finish_session(firstSession);

    decoder.clear_personalization(firstSession);
    long staleProofSession = decoder.start_session(enabledConfig(),
        warmedResources, null, warmedPersonalization);
    Decoder.Result staleProof = awaitReady(decoder,
        decoder.request(staleProofSession, snapshot(2, "alpha", false))).result;
    assertTrue("A stale nonzero editor epoch must not be accepted as an inactive management call.",
        staleProof.literal.learned);
    PersonalizationStore afterRejectedClear =
      new PersonalizationStore(preferences);
    assertEquals(1, afterRejectedClear.word_count("alpha"));
    assertEquals(1, afterRejectedClear.word_count("beta"));
    assertEquals(1, afterRejectedClear.bigram_count("alpha", "beta"));
    assertEquals(1, afterRejectedClear.correction_count("alpga", "alpha"));
    decoder.finish_session(staleProofSession);

    decoder.clear_personalization(0);
    long clearedSession = decoder.start_session(enabledConfig(),
        warmedResources, null, warmedPersonalization);
    Decoder.Result cleared = awaitReady(decoder,
        decoder.request(clearedSession, snapshot(3, "alpha", false))).result;
    assertFalse("The next decode must observe cleared inactive worker memory, not stale prewarmed counts.",
        cleared.literal.learned);
    assertEquals(0, cleared.literal.unigramCount);
    PersonalizationStore clearedStore = new PersonalizationStore(preferences);
    assertEquals("Inactive clear must converge the persisted unigram store.",
        0, clearedStore.word_count("alpha"));
    assertEquals(0, clearedStore.word_count("beta"));
    assertEquals("Inactive clear must remove persisted context pairs.",
        0, clearedStore.bigram_count("alpha", "beta"));
    assertEquals("Inactive clear must remove persisted correction pairs.",
        0, clearedStore.correction_count("alpga", "alpha"));
  }

  @Test
  public void keyboard_lifecycle_prewarms_once_and_reuses_same_language_on_editor_start()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/Keyboard2.java");
    String onCreate = methodBody(source, "public void onCreate()");
    String onStartInputView = methodBody(source,
        "public void onStartInputView(EditorInfo info, boolean restarting)");
    String refreshConfig = methodBody(source, "private void refresh_config()");
    String subtypeChanged = methodBody(source,
        "public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype)");

    int loadResources = onCreate.indexOf("refresh_current_dictionary(true);");
    int loadPersonalization = onCreate.indexOf(
        "_personalization_spec = create_personalization_spec();");
    int prewarm = onCreate.indexOf(
        "_decoder.prewarm(_resource_spec, _personalization_spec);");
    assertTrue("IME creation must resolve resources and personalization before passively prewarming the shared decoder.",
        loadResources >= 0 && loadPersonalization > loadResources
          && prewarm > loadPersonalization);

    int refresh = onStartInputView.indexOf("refresh_config();");
    int startSession = onStartInputView.indexOf("_decoder.start_session(");
    assertTrue("Editor startup must refresh normal config before starting a session from the already prepared resource descriptors.",
        refresh >= 0 && startSession > refresh);
    assertFalse("Starting an editor must not force a same-language dictionary generation or reload.",
        onStartInputView.contains("refresh_current_dictionary(true)")
          || onStartInputView.contains("_resource_generation++"));
    assertTrue("The editor-start refresh path must compare the selected language instead of force-generating a new resource key.",
        refreshConfig.contains("refresh_current_dictionary(false)"));
    assertTrue("The first editor session must reuse the prewarmed resource descriptor while selecting the privacy-safe personalization descriptor for that editor.",
        onStartInputView.contains(
          "_resource_spec, layout, session_personalization_spec()"));
    assertTrue("A subtype change must update an active editor, but passively prewarm the replacement resources when no editor session exists.",
        subtypeChanged.replaceAll("\\s+", "").contains(
          "if(_decoder_session!=0)_decoder.update_resources(_decoder_session,_resource_spec);else_decoder.prewarm(_resource_spec,_personalization_spec);"));
  }

  private static SharedPreferences preferences(String name)
  {
    SharedPreferences preferences = RuntimeEnvironment.getApplication()
      .getSharedPreferences(name, Context.MODE_PRIVATE);
    preferences.edit().clear().commit();
    return preferences;
  }

  private static long start(SharedDecoder decoder,
      Decoder.DecoderConfig config, SharedPreferences preferences,
      String profile)
  {
    return decoder.start_session(config, SharedDecoder.ResourceSpec.empty("r1"),
        null, new SharedDecoder.PersonalizationSpec(profile, preferences));
  }

  private static Decoder.Candidate candidate(Decoder.Result result,
      String canonical)
  {
    if (result.autocorrection != null
        && canonical.equals(result.autocorrection.canonical))
      return result.autocorrection;
    for (Decoder.Candidate candidate : result.words())
      if (canonical.equals(candidate.canonical))
        return candidate;
    return null;
  }

  private static SharedDecoder.ResourceSpec resources(String key)
  {
    return new SharedDecoder.ResourceSpec(key, new juloo.cdict.Cdict[0],
        null, null, null);
  }

  private static String readSource(String path)
      throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)),
        StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String methodSignature)
  {
    int methodIndex = source.indexOf(methodSignature);
    assertTrue("Expected method in source: " + methodSignature,
        methodIndex >= 0);
    int openBrace = source.indexOf('{', methodIndex);
    assertTrue("Expected method body for: " + methodSignature,
        openBrace >= 0);

    int depth = 0;
    for (int i = openBrace; i < source.length(); i++)
    {
      char c = source.charAt(i);
      if (c == '{')
        depth++;
      else if (c == '}')
      {
        depth--;
        if (depth == 0)
          return source.substring(openBrace + 1, i);
      }
    }
    fail("Expected closing brace for: " + methodSignature);
    return "";
  }

  private SharedDecoder decoder(Handler handler, SharedDecoder.Callback callback)
  {
    SharedDecoder decoder = new SharedDecoder(handler, callback);
    _decoders.add(decoder);
    return decoder;
  }

  private static long start(SharedDecoder decoder,
      Decoder.DecoderConfig config)
  {
    return decoder.start_session(config, SharedDecoder.ResourceSpec.empty("r1"),
        null, SharedDecoder.PersonalizationSpec.empty("profile-1"));
  }

  private static Decoder.DecoderConfig enabledConfig()
  {
    return new Decoder.DecoderConfig(true, true, true, true);
  }

  private static CurrentlyTypedWord.Snapshot snapshot(long revision,
      String word, boolean selected)
      throws Exception
  {
    Constructor<CurrentlyTypedWord.Snapshot> constructor =
      CurrentlyTypedWord.Snapshot.class.getDeclaredConstructor(long.class,
          String.class, int.class, boolean.class, TouchTrace.Snapshot.class);
    constructor.setAccessible(true);
    return constructor.newInstance(revision, word, 0, selected,
        new TouchTrace().snapshot());
  }

  private static SharedDecoder.Presentation awaitReady(SharedDecoder decoder,
      Decoder.RequestKey key)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      SharedDecoder.Presentation state = decoder.current_presentation();
      if (state.state == SharedDecoder.Presentation.State.READY
          && key.equals(state.key))
        return state;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for READY state for generation "
        + key.requestGeneration);
    return null;
  }

  private static Decoder.Result awaitResult(SharedDecoder decoder,
      Decoder.RequestKey key)
      throws Exception
  {
    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      Decoder.Result result = decoder.current_result(key);
      if (result != null)
        return result;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for hidden decoder result");
    return null;
  }

  private static void assertEmpty(SharedDecoder decoder,
      Decoder.RequestKey key, String message)
      throws Exception
  {
    Thread.sleep(20L);
    assertEquals(message, SharedDecoder.Presentation.State.EMPTY,
        decoder.current_presentation().state);
    assertNull(message, decoder.current_result(key));
  }

  private static void assertInvalidated(SharedDecoder decoder,
      Decoder.RequestKey stale, Decoder.RequestKey current, String message)
  {
    assertNotNull(message, current);
    assertFalse(message, decoder.is_current(stale));
    assertTrue(message, decoder.is_current(current));
    assertNull(message, decoder.current_result(stale));
  }

  private static boolean containsReady(
      List<SharedDecoder.Presentation> states, Decoder.RequestKey key)
  {
    for (SharedDecoder.Presentation state : states)
      if (state.state == SharedDecoder.Presentation.State.READY
          && key.equals(state.key))
        return true;
    return false;
  }

  private static final class RecordingCallback
      implements SharedDecoder.Callback
  {
    final List<SharedDecoder.Presentation> states =
      new ArrayList<SharedDecoder.Presentation>();

    @Override
    public void decoder_state_changed(SharedDecoder.Presentation state)
    {
      states.add(state);
    }
  }

  private static final class QueuedHandler extends Handler
  {
    private final List<Runnable> _queued = new ArrayList<Runnable>();

    QueuedHandler()
    {
      super(Looper.getMainLooper());
    }

    @Override
    public boolean sendMessageAtTime(Message message, long uptimeMillis)
    {
      Runnable runnable = message.getCallback();
      if (runnable == null)
        return super.sendMessageAtTime(message, uptimeMillis);
      synchronized (_queued)
      {
        _queued.add(runnable);
      }
      return true;
    }

    void drain()
    {
      while (true)
      {
        Runnable runnable;
        synchronized (_queued)
        {
          if (_queued.isEmpty())
            return;
          runnable = _queued.remove(0);
        }
        runnable.run();
      }
    }
  }
}
