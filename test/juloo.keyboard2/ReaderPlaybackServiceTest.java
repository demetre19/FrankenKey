package juloo.keyboard2;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowService;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReaderPlaybackServiceTest
{
  private Context _context;

  @Before
  public void clearReaderState()
  {
    _context = RuntimeEnvironment.getApplication();
    _context.getSharedPreferences("reader_playback", Context.MODE_PRIVATE)
      .edit().clear().commit();
    ShadowTextToSpeech.reset();
    ShadowTextToSpeech.addVoice(new Voice("reader-test-offline", Locale.US,
        Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false,
        Collections.<String>emptySet()));
  }

  @Test
  public void hundred_thousand_characters_play_once_in_order_through_bounded_queue()
  {
    String text = largeText(100000);
    ReaderPlaybackService.ReaderChunkQueue queue =
      new ReaderPlaybackService.ReaderChunkQueue(text, 3800, 2, 0);
    StringBuilder rebuilt = new StringBuilder(text.length());
    ReaderPlaybackService.ReaderChunkQueue.Span span;
    while ((span = queue.current()) != null)
    {
      assertTrue("The speech queue must never retain more than two pending units.",
          queue.queuedCount() <= 2);
      assertTrue("Every speech request must stay below the engine-safe UTF-16 limit.",
          span.end - span.start <= 3800);
      assertFalse("A speech request must not end with half of a surrogate pair.",
          Character.isHighSurrogate(text.charAt(span.end - 1)));
      assertFalse("A speech request must not start with half of a surrogate pair.",
          Character.isLowSurrogate(text.charAt(span.start)));
      rebuilt.append(text, span.start, span.end);
      queue.advance();
    }
    assertEquals("Bounded chunking must preserve all 100,000 UTF-16 characters in order.",
        text, rebuilt.toString());

    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    service.onTtsInitialized(TextToSpeech.SUCCESS);
    service.load("large-fixture", "Large fixture", text, true);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

    ShadowTextToSpeech shadowTts = Shadows.shadowOf(service.textToSpeechForTest());
    StringBuilder spoken = new StringBuilder(text.length());
    for (String chunk : shadowTts.getSpokenTextList())
      spoken.append(chunk);
    assertEquals("The actual Android TTS integration must receive every chunk once and in order.",
        text, spoken.toString());
    assertEquals("Completing the final unit must persist end-of-item progress.",
        text.length(), service.snapshot().characterOffset);
    controller.destroy();
  }

  @Test
  public void stable_unit_progress_survives_service_recreation_and_resumes_there()
  {
    String text = largeText(12000);
    ServiceController<ReaderPlaybackService> firstController =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService first = firstController.get();
    first.load("resume-fixture", "Resume fixture", text, false);
    first.next();
    int savedOffset = first.snapshot().characterOffset;
    assertTrue(savedOffset > 0);
    firstController.destroy();

    ServiceController<ReaderPlaybackService> secondController =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService restored = secondController.get();
    restored.onTtsInitialized(TextToSpeech.SUCCESS);
    assertEquals("A recreated service must restore the same private Reader item.",
        "resume-fixture", restored.snapshot().itemId);
    assertEquals("A recreated service must return to the last stable spoken unit.",
        savedOffset, restored.snapshot().characterOffset);
    assertEquals("Process recreation must not start speech without a fresh user command.",
        ReaderPlaybackService.Status.PAUSED, restored.snapshot().status);

    restored.play();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    List<String> resumedSpeech =
      Shadows.shadowOf(restored.textToSpeechForTest()).getSpokenTextList();
    assertFalse(resumedSpeech.isEmpty());
    assertTrue("Resume must begin at the saved unit rather than restarting the document.",
        text.substring(savedOffset).startsWith(resumedSpeech.get(0)));
    secondController.destroy();
  }

  @Test
  public void playback_settings_are_bounded_and_survive_service_recreation()
  {
    ServiceController<ReaderPlaybackService> firstController =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService first = firstController.get();
    first.onTtsInitialized(TextToSpeech.SUCCESS);
    first.setSpeechRate(8f);
    first.setPitch(0.1f);
    assertEquals("Speech speed must stay inside the configured 800 WPM range.",
        800f / 180f, first.snapshot().speechRate, 0.001f);
    assertEquals("Voice pitch must stay inside the TTS-safe range.",
        0.5f, first.snapshot().pitch, 0.001f);
    first.setSpeechRate(1.35f);
    first.setPitch(1.2f);
    first.setAllowNetworkVoices(true);
    assertTrue("A selected installed voice must be accepted before persistence.",
        first.setVoice("reader-test-offline"));
    firstController.destroy();

    ServiceController<ReaderPlaybackService> secondController =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService restored = secondController.get();
    assertEquals("A user's WPM setting must survive normal service recreation.",
        1.35f, restored.snapshot().speechRate, 0.001f);
    assertEquals("A user's voice pitch must survive normal service recreation.",
        1.2f, restored.snapshot().pitch, 0.001f);
    assertTrue("The network-voice opt-in must survive normal service recreation.",
        restored.allowNetworkVoices());
    assertEquals("The selected voice must survive normal service recreation.",
        "reader-test-offline", restored.snapshot().voiceName);
    secondController.destroy();
  }

  @Test
  public void keyboard_reader_and_notification_commands_share_one_service_state()
  {
    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    List<ReaderPlaybackService.Snapshot> keyboardStates = new ArrayList<>();
    List<ReaderPlaybackService.Snapshot> readerStates = new ArrayList<>();
    service.addListener(keyboardStates::add);
    service.addListener(readerStates::add);
    service.load("shared", "Shared session", largeText(9000), false);

    ReaderPlaybackService.LocalBinder binder =
      (ReaderPlaybackService.LocalBinder)service.onBind(new Intent());
    assertSame("IME and Reader binders must resolve the one service-owned session.",
        service, binder.service());
    assertTrue("Dismissing the keyboard must not stop the service-owned item.",
        service.onUnbind(new Intent()));
    assertEquals(ReaderPlaybackService.Status.PAUSED, service.snapshot().status);

    service.handleAction(ReaderPlaybackService.ACTION_PLAY);
    ShadowService shadow = Shadows.shadowOf(service);
    assertEquals("Active speech must use the one Reader foreground notification.",
        ReaderPlaybackService.NOTIFICATION_ID, shadow.getLastForegroundNotificationId());
    assertNotNull(shadow.getLastForegroundNotification());
    assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        service.getForegroundServiceType());
    assertNotNull("The foreground owner must also own the framework MediaSession token.",
        service.mediaSessionForTest().getSessionToken());

    service.handleAction(ReaderPlaybackService.ACTION_PAUSE);
    assertEquals(ReaderPlaybackService.Status.PAUSED, service.snapshot().status);
    int beforeNext = service.snapshot().characterOffset;
    service.handleAction(ReaderPlaybackService.ACTION_NEXT);
    assertTrue(service.snapshot().characterOffset > beforeNext);
    service.handleAction(ReaderPlaybackService.ACTION_PREVIOUS);
    assertEquals(beforeNext, service.snapshot().characterOffset);
    assertEquals("Keyboard and Reader listeners must observe the same state transitions.",
        keyboardStates.get(keyboardStates.size() - 1).characterOffset,
        readerStates.get(readerStates.size() - 1).characterOffset);
    controller.destroy();
  }

  @Test
  public void range_callbacks_publish_follow_along_and_live_speed_restarts_in_place()
  {
    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    service.onTtsInitialized(TextToSpeech.SUCCESS);
    String text = "Alpha bravo charlie delta echo.";
    service.load("follow", "Follow", text, true);
    int generation = service.utteranceGenerationForTest();

    service.onUtteranceRange(generation + ":0:" + text.length(), 6, 11);
    assertEquals("TTS word ranges drive the visible follow-along start.",
        6, service.snapshot().highlightStart);
    assertEquals("TTS word ranges drive the visible follow-along end.",
        11, service.snapshot().highlightEnd);
    assertEquals("Follow-along progress tracks the spoken word.",
        6, service.snapshot().characterOffset);

    service.setSpeechRate(1.5f);
    assertTrue("Changing speed during playback restarts the active utterance.",
        service.utteranceGenerationForTest() > generation);
    assertEquals("A live speed change keeps playback active.",
        ReaderPlaybackService.Status.PLAYING, service.snapshot().status);
    assertEquals("A live speed change resumes at the tracked word.",
        6, service.snapshot().characterOffset);
    controller.destroy();
  }

  @Test
  public void relative_seek_uses_five_to_two_percent_and_never_splits_surrogates()
  {
    String shortText = largeText(1000);
    String longText = largeText(100000);
    int shortTarget = ReaderPlaybackService.adaptiveSeekCharacter(
        shortText, 0, 1);
    int longTarget = ReaderPlaybackService.adaptiveSeekCharacter(
        longText, 0, 1);
    assertTrue("Short Reader items seek approximately five percent.",
        shortTarget >= 45 && shortTarget <= 60);
    assertTrue("Long Reader items seek approximately two percent.",
        longTarget >= 1950 && longTarget <= 2050);

    String emoji = "word ".repeat(10) + "\uD83D\uDE00" + " tail ".repeat(10);
    int target = ReaderPlaybackService.adaptiveSeekCharacter(
        emoji, 49, 1);
    assertFalse("Relative seeking must never land between UTF-16 surrogates.",
        target > 0 && target < emoji.length() &&
        Character.isHighSurrogate(emoji.charAt(target - 1)) &&
        Character.isLowSurrogate(emoji.charAt(target)));
  }

  @Test
  public void disabling_an_active_network_only_voice_stops_and_fails_closed()
  {
    ShadowTextToSpeech.reset();
    ShadowTextToSpeech.addVoice(new Voice("network-only", Locale.US,
        Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, true,
        Collections.<String>emptySet()));
    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    service.onTtsInitialized(TextToSpeech.SUCCESS);
    service.setAllowNetworkVoices(true);
    assertTrue(service.setVoice("network-only"));
    service.load("network", "Network", "Private text stays local.", true);
    int activeGeneration = service.utteranceGenerationForTest();

    service.setAllowNetworkVoices(false);

    assertFalse(service.allowNetworkVoices());
    assertEquals("Opting out mid-utterance must stop rather than continue a network voice.",
        ReaderPlaybackService.Status.ERROR, service.snapshot().status);
    assertTrue("Stopping the active utterance must invalidate every queued callback.",
        service.utteranceGenerationForTest() > activeGeneration);
    controller.destroy();
  }

  @Test
  public void stale_error_from_replaced_item_cannot_corrupt_current_playback()
  {
    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    service.onTtsInitialized(TextToSpeech.SUCCESS);
    service.load("old", "Old", "Old private text.", true);
    int staleGeneration = service.utteranceGenerationForTest();
    service.load("replacement", "Replacement", "Current private text.", true);

    service.onUtteranceError(staleGeneration + ":0:17");

    assertEquals("replacement", service.snapshot().itemId);
    assertEquals("A delayed callback must not fail the replacement session.",
        ReaderPlaybackService.Status.PLAYING, service.snapshot().status);
    controller.destroy();
  }

  @Test
  public void stale_error_after_destroy_is_ignored()
  {
    ServiceController<ReaderPlaybackService> controller =
      Robolectric.buildService(ReaderPlaybackService.class).create();
    ReaderPlaybackService service = controller.get();
    service.onTtsInitialized(TextToSpeech.SUCCESS);
    service.load("destroyed", "Destroyed", "Private text.", true);
    int staleGeneration = service.utteranceGenerationForTest();

    controller.destroy();
    service.onUtteranceError(staleGeneration + ":0:13");

    assertNotEquals("A callback posted before teardown must not resurrect an error state.",
        ReaderPlaybackService.Status.ERROR, service.snapshot().status);
    assertNull(service.textToSpeechForTest());
  }

  @Test
  public void manifest_declares_modern_media_foreground_service_contract()
      throws Exception
  {
    PackageManager packageManager = _context.getPackageManager();
    ServiceInfo service = packageManager.getServiceInfo(
        new ComponentName(_context, ReaderPlaybackService.class),
        PackageManager.GET_META_DATA);
    assertFalse("Reader playback is private to FrankenKey app surfaces.", service.exported);
    assertEquals("Android 14+ must recognize the service as media playback.",
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        service.getForegroundServiceType());

    PackageInfo application = packageManager.getPackageInfo(_context.getPackageName(),
        PackageManager.GET_PERMISSIONS);
    List<String> permissions = Arrays.asList(application.requestedPermissions);
    assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE));
    assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK));
    assertTrue("Android 13+ notification visibility is requested before Reader playback.",
        permissions.contains(Manifest.permission.POST_NOTIFICATIONS));
  }

  private static String largeText(int length)
  {
    String unit = "First sentence. Second sentence with emoji \ud83d\ude03.\n\n";
    StringBuilder text = new StringBuilder(length);
    while (text.length() < length)
      text.append(unit);
    text.setLength(length);
    if (Character.isHighSurrogate(text.charAt(text.length() - 1)))
      text.setCharAt(text.length() - 1, '.');
    return text.toString();
  }
}
