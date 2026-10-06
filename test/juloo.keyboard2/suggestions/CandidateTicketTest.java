package juloo.keyboard2.suggestions;

import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.CurrentlyTypedWord;
import juloo.keyboard2.TouchTrace;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/** A1: ticket capture semantics, content validation, exactly-once, and the
    retained-result match behind prepare_commit_for_ticket. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class CandidateTicketTest
{
  private final List<SharedDecoder> _decoders = new ArrayList<SharedDecoder>();

  @After
  public void tearDown()
  {
    for (SharedDecoder decoder : _decoders)
      decoder.close();
  }

  private SharedDecoder decoder(Handler handler, SharedDecoder.Callback callback)
  {
    SharedDecoder decoder = new SharedDecoder(handler, callback);
    _decoders.add(decoder);
    return decoder;
  }

  private static long start(SharedDecoder decoder)
  {
    return decoder.start_session(
        new Decoder.DecoderConfig(true, true, true, true),
        SharedDecoder.ResourceSpec.empty("r1"), null,
        SharedDecoder.PersonalizationSpec.empty("profile-1"));
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

  private static Decoder.RequestKey key(long generation, long revision)
  {
    return new Decoder.RequestKey(1, generation, revision, 1, 1, 1, 1);
  }

  private static Decoder.CandidateTicket ticket(Decoder.RequestKey source,
      int connectionId, String word, long wordStart, String candidate)
      throws Exception
  {
    return new Decoder.CandidateTicket(source, connectionId,
        snapshot(1, word, false), wordStart, candidate, CandidateRole.WORD, 0);
  }

  @Test
  public void ticket_consumes_exactly_once_across_repeated_dispatches()
      throws Exception
  {
    Decoder.CandidateTicket ticket = ticket(key(1, 1), 7, "the", -1, "their");
    assertTrue("First dispatch consumes the ticket.", ticket.consume());
    assertFalse("onClick after onTouch on the same ticket is a duplicate.",
        ticket.consume());
    assertFalse("A second finger's tap on the same ticket is a duplicate.",
        ticket.consume());
  }

  @Test
  public void ticket_slot_matches_same_or_extended_word()
      throws Exception
  {
    Decoder.CandidateTicket ticket =
        ticket(key(1, 1), 7, "th", -1, "their");
    // Intent wins: typing more letters in the same slot keeps the tap valid.
    assertTrue(ticket.same_word_slot(snapshot(2, "the", false), -1));
    assertTrue(ticket.same_word_slot(snapshot(2, "th", false), -1));
    // A separator or a move to another word breaks the slot.
    assertFalse(ticket.same_word_slot(snapshot(2, "next", false), -1));
    // A selection is never the same slot.
    assertFalse(ticket.same_word_slot(snapshot(2, "the", true), -1));
    assertFalse(ticket.same_word_slot(null, -1));
  }

  @Test
  public void ticket_slot_prefers_absolute_start_when_both_readable()
      throws Exception
  {
    Decoder.CandidateTicket ticket =
        ticket(key(1, 1), 7, "th", 42, "their");
    assertTrue(ticket.same_word_slot(snapshot(2, "the", false), 42));
    assertFalse("A different absolute slot rejects even a matching prefix.",
        ticket.same_word_slot(snapshot(2, "the", false), 77));
  }

  @Test
  public void ticket_is_a_request_key_so_existing_dispatch_carries_it()
      throws Exception
  {
    Decoder.CandidateTicket ticket = ticket(key(1, 1), 7, "the", -1, "their");
    assertTrue(ticket instanceof Decoder.RequestKey);
    assertEquals("the", ticket.word);
    assertEquals("their", ticket.candidate);
    assertEquals(CandidateRole.WORD, ticket.role);
    assertEquals(7, ticket.connectionId);
  }

  @Test
  public void ticket_epochs_current_requires_live_epochs_only()
      throws Exception
  {
    SharedDecoder decoder = decoder(new QueuedHandler(), new RecordingCallback());
    long session = start(decoder);
    Decoder.RequestKey source = decoder.request(session,
        snapshot(1, "th", false));
    Decoder.CandidateTicket ticket = ticket(source, 7, "th", -1, "their");

    assertTrue(decoder.ticket_epochs_current(ticket));
    // A newer request does not invalidate the ticket: request generation,
    // word revision, and the personalization epoch are excluded.
    decoder.request(session, snapshot(2, "the", false));
    assertTrue("A newer request for the same word must not reject the ticket.",
        decoder.ticket_epochs_current(ticket));
    // A resource epoch change rejects.
    decoder.update_resources(session,
        SharedDecoder.ResourceSpec.empty("r2"));
    assertFalse(decoder.ticket_epochs_current(ticket));
  }

  @Test
  public void prepare_commit_for_ticket_matches_retained_result_by_word()
      throws Exception
  {
    QueuedHandler handler = new QueuedHandler();
    SharedDecoder decoder = decoder(handler, new RecordingCallback());
    long session = start(decoder);
    Decoder.RequestKey first = decoder.request(session,
        snapshot(1, "ca", false));
    awaitReady(decoder, first);
    Decoder.RequestKey second = decoder.request(session,
        snapshot(2, "cab", false));
    awaitReady(decoder, second);

    // A ticket rendered from the first READY stays preparable after a newer
    // READY replaced the current key: the retained ring matches the word
    // fingerprint even though the key itself is stale.
    Decoder.CandidateTicket ticket = ticket(first, 7, "ca", -1, "cat");
    assertFalse("The stale source key is not current.",
        decoder.is_current(first));
    SharedDecoder.CommitToken token =
        decoder.prepare_commit_for_ticket(session, ticket, "cat", null);
    assertNotNull("A retained result matching the ticket word yields a token.",
        token);

    // A ticket whose word no retained result answers gets no learning token.
    Decoder.CandidateTicket unmatched =
        ticket(first, 7, "zzz", -1, "zzza");
    assertNull(decoder.prepare_commit_for_ticket(session, unmatched,
          "zzza", null));
  }

  @Test
  public void prepare_commit_for_ticket_rejects_stale_epochs()
      throws Exception
  {
    QueuedHandler handler = new QueuedHandler();
    SharedDecoder decoder = decoder(handler, new RecordingCallback());
    long session = start(decoder);
    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "ca", false));
    awaitReady(decoder, key);
    Decoder.CandidateTicket ticket = ticket(key, 7, "ca", -1, "cat");

    decoder.update_resources(session,
        SharedDecoder.ResourceSpec.empty("r2"));
    assertNull("An epoch-changed ticket prepares no learning token.",
        decoder.prepare_commit_for_ticket(session, ticket, "cat", null));
  }

  @Test
  public void connection_change_drops_retained_results()
      throws Exception
  {
    QueuedHandler handler = new QueuedHandler();
    SharedDecoder decoder = decoder(handler, new RecordingCallback());
    long session = start(decoder);
    decoder.update_editor_context(EditorContext.EMPTY, 11, 0);
    Decoder.RequestKey key = decoder.request(session,
        snapshot(1, "ca", false));
    awaitReady(decoder, key);

    decoder.update_editor_context(EditorContext.EMPTY, 12, 0);
    Decoder.CandidateTicket ticket = ticket(key, 11, "ca", -1, "cat");
    assertNull("A ticket rendered under another connection matches nothing.",
        decoder.prepare_commit_for_ticket(session, ticket, "cat", null));
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
