package juloo.keyboard2;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderActivityTest
{
  private ActivityController<ReaderActivity> _activityController;
  private ServiceController<ReaderPlaybackService> _controller;

  @After
  public void tearDown()
  {
    if (_activityController != null)
      _activityController.destroy();
    if (_controller != null)
      _controller.destroy();
    ShadowTextToSpeech.reset();
  }

  @Test
  public void installed_offline_voices_are_default_and_network_voices_require_opt_in()
  {
    Voice offline = voice("offline-en-au", Locale.forLanguageTag("en-AU"),
        false, Collections.<String>emptySet());
    Voice network = voice("network-en-us", Locale.US, true,
        Collections.<String>emptySet());
    Voice missing = voice("missing-en-gb", Locale.UK, false,
        Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    ShadowTextToSpeech.addVoice(network);
    ShadowTextToSpeech.addVoice(missing);
    ShadowTextToSpeech.addVoice(offline);

    ReaderPlaybackService service = service();
    List<ReaderPlaybackService.VoiceOption> defaults =
      service.availableVoices();

    assertEquals("Only installed offline voices are shown by default.",
        1, defaults.size());
    assertEquals("offline-en-au", defaults.get(0).name);
    assertFalse(defaults.get(0).networkRequired);
    assertFalse("A network-required voice cannot be selected before opt-in.",
        service.setVoice("network-en-us"));

    service.setAllowNetworkVoices(true);
    List<ReaderPlaybackService.VoiceOption> optedIn =
      service.availableVoices();
    assertEquals("Opt-in reveals installed network voices but not uninstalled voices.",
        2, optedIn.size());
    assertFalse("Offline voices remain first in the system voice list.",
        optedIn.get(0).networkRequired);
    assertTrue(optedIn.get(1).networkRequired);
    assertTrue(service.setVoice("network-en-us"));

    service.setAllowNetworkVoices(false);
    assertFalse(service.allowNetworkVoices());
    assertEquals("Disabling network voices immediately returns to an offline voice.",
        "offline-en-au", service.snapshot().voiceName);
  }

  @Test
  public void installed_offline_voice_previews_without_connectivity()
  {
    Voice offline = voice("offline-preview", Locale.US, false,
        Collections.<String>emptySet());
    ShadowTextToSpeech.addVoice(offline);
    ReaderPlaybackService service = service();

    assertTrue(service.previewVoice("offline-preview", "Local preview text"));
    assertEquals("Local preview text", shadowOf(
        service.textToSpeechForTest()).getLastSpokenText());
  }

  @Test
  public void playback_fails_closed_when_only_network_or_uninstalled_voices_exist()
  {
    ShadowTextToSpeech.addVoice(voice("network-only", Locale.US, true,
        Collections.<String>emptySet()));
    ShadowTextToSpeech.addVoice(voice("not-installed", Locale.US, false,
        Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)));
    ReaderPlaybackService service = service();
    service.load("private", "Private", "Never send this implicitly", false);

    service.play();

    assertEquals(ReaderPlaybackService.Status.ERROR,
        service.snapshot().status);
    assertFalse("Network voice consent remains off after a failed play.",
        service.allowNetworkVoices());
    assertTrue(service.availableVoices().isEmpty());
  }

  @Test
  public void reader_controls_have_accessible_labels_and_48dp_touch_targets()
  {
    Context context = RuntimeEnvironment.getApplication();
    View root = LayoutInflater.from(context).inflate(
        R.layout.reader_activity, null, false);
    int minimum = Math.round(48f *
        context.getResources().getDisplayMetrics().density);
    int[] touchTargets = {
      R.id.reader_back, R.id.reader_previous, R.id.reader_play_pause,
      R.id.reader_next, R.id.reader_stop, R.id.reader_preview_voice,
      R.id.reader_progress, R.id.reader_speed, R.id.reader_voice,
      R.id.reader_network_voices
    };
    for (int id : touchTargets)
    {
      View control = root.findViewById(id);
      int declaredHeight = control.getLayoutParams().height;
      assertTrue("Every Reader control must expose at least a 48dp touch target: " + id,
          control.getMinimumHeight() >= minimum || declaredHeight >= minimum);
    }

    assertFalse(root.findViewById(R.id.reader_back).getContentDescription()
        .toString().isEmpty());
    assertFalse(root.findViewById(R.id.reader_progress).getContentDescription()
        .toString().isEmpty());
    assertFalse(root.findViewById(R.id.reader_speed).getContentDescription()
        .toString().isEmpty());
    assertFalse(root.findViewById(R.id.reader_voice).getContentDescription()
        .toString().isEmpty());
    assertFalse("Network voices must be visibly and functionally off by default.",
        ((Switch)root.findViewById(R.id.reader_network_voices)).isChecked());
    assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
        root.findViewById(R.id.reader_status).getAccessibilityLiveRegion());
  }

  @Test
  public void launcher_opens_private_reader_activity()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    ActivityInfo info = context.getPackageManager().getActivityInfo(
        new ComponentName(context, ReaderActivity.class), 0);
    assertFalse("Reader activity accepts only explicit in-app launches in Release 1.",
        info.exported);

    LauncherActivity launcher = Robolectric.buildActivity(
        LauncherActivity.class).create().get();
    launcher.findViewById(R.id.launcher_reader).performClick();
    Intent launched = shadowOf((Activity)launcher).getNextStartedActivity();
    assertNotNull(launched);
    assertEquals(ReaderActivity.class.getName(),
        launched.getComponent().getClassName());
  }

  @Test
  public void ime_quick_read_uses_only_an_opaque_activity_token_until_permission()
  {
    Application application = RuntimeEnvironment.getApplication();
    shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    String itemId = "quick-read:test";
    String title = "Clipboard";
    String privateText = "Private clipboard text";

    Keyboard2.startReaderText(application, itemId, title, privateText);

    Intent launched = shadowOf(application).getNextStartedActivity();
    assertNotNull(launched);
    assertEquals(ReaderActivity.class.getName(),
        launched.getComponent().getClassName());
    assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    assertNull("No playback service may receive text before notification permission.",
        shadowOf(application).getNextStartedService());
    Bundle extras = launched.getExtras();
    assertNotNull(extras);
    assertEquals("The private activity receives one opaque token only.",
        1, extras.size());
    for (String key : extras.keySet())
    {
      Object value = extras.get(key);
      assertNotEquals(itemId, value);
      assertNotEquals(title, value);
      assertNotEquals(privateText, value);
    }
  }

  @Test
  public void ime_quick_read_rejects_unbounded_text_before_permission_handoff()
  {
    Application application = RuntimeEnvironment.getApplication();
    shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);

    Keyboard2.startReaderText(application, "quick-read:test", "Clipboard",
        repeat('x', ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH + 1));

    assertNull("Oversized Reader text must not enter the pending Activity handoff.",
        shadowOf(application).getNextStartedActivity());
    assertNull("Oversized Reader text must not reach the playback service.",
        shadowOf(application).getNextStartedService());
  }

  @Test
  public void ime_quick_read_starts_shared_service_after_permission()
  {
    Application application = RuntimeEnvironment.getApplication();
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

    Keyboard2.startReaderText(application,
        repeat('i', ReaderPlaybackService.MAX_ITEM_ID_LENGTH + 20),
        repeat('t', ReaderPlaybackService.MAX_TITLE_LENGTH + 20),
        "Readable clipboard text");

    Intent service = shadowOf(application).getNextStartedService();
    assertNotNull(service);
    assertEquals(ReaderPlaybackService.class.getName(),
        service.getComponent().getClassName());
    assertEquals(ReaderPlaybackService.ACTION_LOAD_AND_PLAY,
        service.getAction());
    assertEquals(ReaderPlaybackService.MAX_ITEM_ID_LENGTH,
        service.getStringExtra(ReaderPlaybackService.EXTRA_ITEM_ID).length());
    assertEquals(ReaderPlaybackService.MAX_TITLE_LENGTH,
        service.getStringExtra(ReaderPlaybackService.EXTRA_TITLE).length());
    assertNull(shadowOf(application).getNextStartedActivity());
  }

  @Test
  public void quick_read_permission_grant_preserves_pending_text_and_starts_playback()
  {
    Application application = RuntimeEnvironment.getApplication();
    ShadowApplication shadowApplication = shadowOf(application);
    shadowApplication.denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    ReaderPlaybackService playback = service();
    playback.load("old-item", "Old title", "Old persisted text", false);
    String privateText = "Fresh private clipboard text";

    Keyboard2.startReaderText(application, "quick-read:fresh", "Clipboard",
        privateText);
    Intent launched = shadowApplication.getNextStartedActivity();
    ReaderActivity activity = launchQuickRead(application, playback, launched);
    ShadowActivity.PermissionsRequest request =
      shadowOf(activity).getLastRequestedPermission();

    assertNotNull(request);
    assertArrayEquals(new String[]{Manifest.permission.POST_NOTIFICATIONS},
        request.requestedPermissions);
    assertEquals(privateText, ((EditText)activity.findViewById(
        R.id.reader_text)).getText().toString());
    playback.setSpeechRate(1.1f);
    assertEquals("A restored session update must not clobber the pending handoff.",
        privateText, ((EditText)activity.findViewById(
            R.id.reader_text)).getText().toString());
    assertEquals(1, shadowApplication.getBoundServiceConnections().size());
    shadowApplication.getNextStartedService();

    activity.onRequestPermissionsResult(request.requestCode,
        request.requestedPermissions,
        new int[]{PackageManager.PERMISSION_GRANTED});

    Intent started = shadowApplication.getNextStartedService();
    assertNotNull(started);
    assertEquals(ReaderPlaybackService.ACTION_LOAD_AND_PLAY,
        started.getAction());
    playback.onStartCommand(started, 0, 1);
    assertEquals(privateText, playback.activeText());
  }

  @Test
  public void quick_read_permission_denial_discards_playable_private_text()
  {
    Application application = RuntimeEnvironment.getApplication();
    ShadowApplication shadowApplication = shadowOf(application);
    shadowApplication.denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    ReaderPlaybackService playback = service();

    Keyboard2.startReaderText(application, "quick-read:denied", "Clipboard",
        "Denied private clipboard text");
    Intent launched = shadowApplication.getNextStartedActivity();
    ReaderActivity activity = launchQuickRead(application, playback, launched);
    ShadowActivity.PermissionsRequest request =
      shadowOf(activity).getLastRequestedPermission();
    assertNotNull(request);
    assertEquals(1, shadowApplication.getBoundServiceConnections().size());
    shadowApplication.getNextStartedService();

    activity.onRequestPermissionsResult(request.requestCode,
        request.requestedPermissions,
        new int[]{PackageManager.PERMISSION_DENIED});

    assertEquals("", ((EditText)activity.findViewById(
        R.id.reader_title)).getText().toString());
    assertEquals("", ((EditText)activity.findViewById(
        R.id.reader_text)).getText().toString());
    assertNull(shadowApplication.getNextStartedService());

    activity.findViewById(R.id.reader_play_pause).performClick();
    ShadowActivity.PermissionsRequest retry =
      shadowOf(activity).getLastRequestedPermission();
    activity.onRequestPermissionsResult(retry.requestCode,
        retry.requestedPermissions,
        new int[]{PackageManager.PERMISSION_GRANTED});
    assertNull("Denied captured text cannot later bypass the one-shot holder.",
        shadowApplication.getNextStartedService());
  }

  private ReaderActivity launchQuickRead(Application application,
      ReaderPlaybackService playback, Intent launched)
  {
    ShadowApplication shadowApplication = shadowOf(application);
    shadowApplication.setComponentNameAndServiceForBindService(
        new ComponentName(application, ReaderPlaybackService.class),
        playback.onBind(new Intent(application, ReaderPlaybackService.class)));
    shadowApplication.setBindServiceCallsOnServiceConnectedDirectly(true);
    _activityController = Robolectric.buildActivity(
        ReaderActivity.class, launched).create().start().resume().visible();
    return _activityController.get();
  }

  private ReaderPlaybackService service()
  {
    _controller = Robolectric.buildService(ReaderPlaybackService.class)
      .create();
    ReaderPlaybackService service = _controller.get();
    service.onTtsInitialized(TextToSpeech.SUCCESS);
    return service;
  }

  private static Voice voice(String name, Locale locale, boolean network,
      Set<String> features)
  {
    return new Voice(name, locale, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL,
        network, new HashSet<String>(features));
  }

  private static String repeat(char value, int count)
  {
    StringBuilder out = new StringBuilder(count);
    for (int i = 0; i < count; i++)
      out.append(value);
    return out.toString();
  }
}
