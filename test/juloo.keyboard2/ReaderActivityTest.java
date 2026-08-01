package juloo.keyboard2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowAlertDialog;
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
    Voice british = voice("offline-en-gb", Locale.UK,
        false, Collections.<String>emptySet());
    Voice network = voice("network-en-us", Locale.US, true,
        Collections.<String>emptySet());
    Voice missing = voice("missing-en-gb", Locale.UK, false,
        Collections.singleton(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    ShadowTextToSpeech.addVoice(network);
    ShadowTextToSpeech.addVoice(missing);
    ShadowTextToSpeech.addVoice(british);
    ShadowTextToSpeech.addVoice(offline);

    ReaderPlaybackService service = service();
    List<ReaderPlaybackService.VoiceOption> defaults =
      service.availableVoices();

    assertEquals("Installed Australian and UK offline voices are shown by default.",
        2, defaults.size());
    assertEquals("offline-en-au", defaults.get(0).name);
    assertEquals("offline-en-gb", defaults.get(1).name);
    assertFalse(defaults.get(0).networkRequired);
    assertFalse(defaults.get(1).networkRequired);
    ReaderActivity.VoiceIdentity known = ReaderActivity.voiceIdentity(
        "en-au-x-aub-local", Locale.forLanguageTag("en-AU"));
    assertEquals("A documented exact voice ID uses its stable human label.",
        "William", known.name);
    assertEquals("A documented exact voice ID uses its documented gender.",
        "Male", known.gender);
    ReaderActivity.VoiceIdentity unknown = ReaderActivity.voiceIdentity(
        "offline-en-gb", Locale.UK);
    assertEquals("Unknown voice IDs keep a truthful regional label.",
        "British voice", unknown.name);
    assertEquals("Unknown voice IDs never receive a guessed gender.",
        "", unknown.gender);
    ReaderActivity.VoiceIdentity networkIdentity =
        ReaderActivity.voiceIdentity(
          "en-us-x-tpf-network", Locale.US);
    assertEquals("A network voice keeps the matching human label.",
        "Harper", networkIdentity.name);
    assertEquals("A network voice keeps the matching gender.",
        "Female", networkIdentity.gender);
    Context context = RuntimeEnvironment.getApplication();
    assertEquals("Offline availability remains explicit without relying on its icon.",
        "Harper · Female · Available offline",
        context.getString(R.string.reader_voice_offline_label,
          "Harper", "Female"));
    assertEquals("Network availability stays explicit beside name and gender.",
        "Harper · Female · Uses network",
        context.getString(R.string.reader_voice_network_label,
          "Harper", "Female"));
    assertFalse("A network-required voice cannot be selected before opt-in.",
        service.setVoice("network-en-us"));

    service.setAllowNetworkVoices(true);
    List<ReaderPlaybackService.VoiceOption> optedIn =
      service.availableVoices();
    assertEquals("Opt-in reveals installed network voices but not uninstalled voices.",
        3, optedIn.size());
    assertFalse("Offline voices remain first in the system voice list.",
        optedIn.get(0).networkRequired);
    assertFalse(optedIn.get(1).networkRequired);
    assertTrue(optedIn.get(2).networkRequired);
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
  public void reader_library_activity_and_rows_inflate_with_manifest_theme()
      throws Exception
  {
    Context application = RuntimeEnvironment.getApplication();
    ActivityInfo info = application.getPackageManager().getActivityInfo(
        new ComponentName(application, ReaderLibraryActivity.class), 0);
    Context context = new ContextThemeWrapper(application, info.theme);
    View activity = LayoutInflater.from(context).inflate(
        R.layout.reader_library_activity, null, false);
    View row = LayoutInflater.from(context).inflate(
        R.layout.reader_library_row, null, false);

    assertNotNull("Library must inflate its complete activity layout.",
        activity.findViewById(R.id.reader_library_list));
    assertNotNull("A stored item row must inflate under the Library activity theme.",
        row.findViewById(R.id.reader_library_row_open));
  }

  @Test
  public void reader_library_item_actions_are_compact_content_sized_and_separated()
  {
    Context context = new ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), R.style.readerThemeDark);
    View row = LayoutInflater.from(context).inflate(
        R.layout.reader_library_row, null, false);
    int compactHeight = Math.round(36f *
        context.getResources().getDisplayMetrics().density);
    int eightDp = Math.round(8f *
        context.getResources().getDisplayMetrics().density);
    int horizontalPadding = Math.round(20f *
        context.getResources().getDisplayMetrics().density);
    View original = row.findViewById(R.id.reader_library_row_original);
    View delete = row.findViewById(R.id.reader_library_row_delete);
    View open = row.findViewById(R.id.reader_library_row_open);

    for (View action : new View[] { original, delete, open })
    {
      assertEquals("Library item actions use the smaller requested height.",
          compactHeight, action.getLayoutParams().height);
      assertEquals("Library item actions size to their own labels.",
          ViewGroup.LayoutParams.WRAP_CONTENT, action.getLayoutParams().width);
      assertEquals("Library item actions keep stronger horizontal padding.",
          horizontalPadding, action.getPaddingLeft());
      assertEquals(horizontalPadding, action.getPaddingRight());
    }
    assertTrue("Original keeps 8dp from Delete when visible.",
        ((ViewGroup.MarginLayoutParams)original.getLayoutParams()).rightMargin
          >= eightDp);
    assertTrue("Delete keeps 8dp from Open.",
        ((ViewGroup.MarginLayoutParams)delete.getLayoutParams()).rightMargin
          >= eightDp);
  }

  @Test
  public void reader_controls_have_accessible_labels_and_48dp_touch_targets()
      throws Exception
  {
    Context context = new ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), R.style.readerThemeDark);
    View root = LayoutInflater.from(context).inflate(
        R.layout.reader_activity, null, false);
    int minimum = Math.round(48f *
        context.getResources().getDisplayMetrics().density);
    int[] touchTargets = {
      R.id.reader_back, R.id.reader_theme, R.id.reader_unit_previous,
      R.id.reader_unit_next, R.id.reader_previous, R.id.reader_play_pause,
      R.id.reader_next, R.id.reader_stop, R.id.reader_jump_bottom,
      R.id.reader_preview_voice, R.id.reader_speed, R.id.reader_pitch,
      R.id.reader_follow_mode, R.id.reader_voice, R.id.reader_network_voices
    };
    for (int id : touchTargets)
    {
      View control = root.findViewById(id);
      int declaredHeight = control.getLayoutParams().height;
      assertTrue("Every Reader control must expose at least a 48dp touch target: " + id,
          control.getMinimumHeight() >= minimum || declaredHeight >= minimum);
    }

    assertEquals("Reader keeps one text editor and derives its title from the source.",
        1, countViewsOfType(root, EditText.class));
    assertTrue("Reading speed is shown as words per minute, not only an opaque multiplier.",
        ((TextView)root.findViewById(R.id.reader_speed_label)).getText()
          .toString().contains("wpm"));
    assertFalse(root.findViewById(R.id.reader_back).getContentDescription()
        .toString().isEmpty());
    assertFalse(root.findViewById(R.id.reader_jump_bottom)
        .getContentDescription().toString().isEmpty());
    assertFalse(root.findViewById(R.id.reader_speed).getContentDescription()
        .toString().isEmpty());
    assertFalse(root.findViewById(R.id.reader_pitch).getContentDescription()
        .toString().isEmpty());
    assertFalse(root.findViewById(R.id.reader_voice).getContentDescription()
        .toString().isEmpty());
    int[] iconControls = {
      R.id.reader_back, R.id.reader_theme, R.id.reader_previous,
      R.id.reader_play_pause, R.id.reader_next, R.id.reader_stop,
      R.id.reader_jump_bottom, R.id.reader_preview_voice
    };
    for (int id : iconControls)
    {
      View control = root.findViewById(id);
      assertTrue("Familiar Reader actions use compact icon controls: " + id,
          control instanceof ImageButton);
      assertNotNull("Every Reader icon control has a visible icon: " + id,
          ((ImageButton)control).getDrawable());
      assertNotNull("Every Reader icon control has a rounded surface: " + id,
          control.getBackground());
      assertEquals("Reader icons stay exactly centered inside their touch targets: " + id,
          ImageView.ScaleType.CENTER, ((ImageButton)control).getScaleType());
      assertFalse("Every Reader icon keeps an accessible label: " + id,
          control.getContentDescription().toString().isEmpty());
    }
    int twelveDp = Math.round(12f *
        context.getResources().getDisplayMetrics().density);
    int eightDp = Math.round(8f *
        context.getResources().getDisplayMetrics().density);
    EditText readerText = root.findViewById(R.id.reader_text);
    assertEquals("Reader prose keeps 12dp of breathing room from the left edge.",
        twelveDp, readerText.getPaddingLeft());
    assertEquals("Reader prose keeps 12dp of breathing room from the right edge.",
        twelveDp, readerText.getPaddingRight());
    assertEquals("Playback controls must not touch the top of their dock.",
        eightDp, root.findViewById(R.id.reader_control_dock).getPaddingTop());
    View original = root.findViewById(R.id.reader_open_original);
    View clipboard = root.findViewById(R.id.reader_clipboard);
    ViewGroup sourceActions = (ViewGroup)original.getParent();
    assertTrue("Original must sit to the left of Read Clipboard in the source actions.",
        sourceActions.indexOfChild(original) < sourceActions.indexOfChild(clipboard));
    assertEquals("Original and Reader actions share the rounded themed surface.",
        R.drawable.reader_themed_icon_button,
        layoutAttributeResource(context, R.layout.reader_activity,
          R.id.reader_open_original, "background"));
    int compactHeight = Math.round(36f *
        context.getResources().getDisplayMetrics().density);
    int horizontalPadding = Math.round(20f *
        context.getResources().getDisplayMetrics().density);
    View library = root.findViewById(R.id.reader_library);
    assertEquals("Reader source actions use the requested smaller height.",
        compactHeight, clipboard.getLayoutParams().height);
    assertEquals(compactHeight, library.getLayoutParams().height);
    assertEquals("Reader source actions use content-sized widths.",
        ViewGroup.LayoutParams.WRAP_CONTENT, clipboard.getLayoutParams().width);
    assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
        library.getLayoutParams().width);
    assertEquals("Reader source actions keep stronger horizontal padding.",
        horizontalPadding, clipboard.getPaddingLeft());
    assertEquals(horizontalPadding, clipboard.getPaddingRight());
    assertEquals(horizontalPadding, library.getPaddingLeft());
    assertEquals(horizontalPadding, library.getPaddingRight());
    assertTrue("The source-action row stays at least 8dp below the header.",
        ((ViewGroup.MarginLayoutParams)sourceActions.getLayoutParams()).topMargin
          >= eightDp);
    assertTrue("The theme toggle is an icon control, not a word button.",
        root.findViewById(R.id.reader_theme) instanceof ImageButton);
    assertEquals("Dark mode shows the dark-state icon.",
        R.drawable.ic_reader_dark_mode,
        ReaderActivity.themeIconResource(true));
    assertEquals("Light mode shows the light-state icon.",
        R.drawable.ic_reader_light_mode,
        ReaderActivity.themeIconResource(false));
    assertTrue("The theme icon stays at least 8dp from the title.",
        ((ViewGroup.MarginLayoutParams)root.findViewById(
            R.id.reader_theme).getLayoutParams()).leftMargin >= eightDp);
    assertTrue("Read Clipboard stays at least 8dp from the preceding action.",
        ((ViewGroup.MarginLayoutParams)clipboard.getLayoutParams()).leftMargin
          >= eightDp);
    assertTrue("Library stays at least 8dp from Read Clipboard.",
        ((ViewGroup.MarginLayoutParams)library.getLayoutParams()).leftMargin
          >= eightDp);
    assertFalse("Network voices must be visibly and functionally off by default.",
        ((Switch)root.findViewById(R.id.reader_network_voices)).isChecked());
    assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
        root.findViewById(R.id.reader_status).getAccessibilityLiveRegion());
  }

  @Test
  public void spoken_highlight_stays_in_a_stable_reading_band()
  {
    assertEquals("A passage already in the middle reading band must not jitter.",
        300, ReaderActivity.readingScrollTarget(300, 650, 690, 900, 2400));
    assertEquals("A passage below the viewport moves into the upper reading zone.",
        900, ReaderActivity.readingScrollTarget(300, 1200, 1240, 900, 2400));
    assertEquals("Following the final passage clamps to the page bottom.",
        1500, ReaderActivity.readingScrollTarget(
          900, 2100, 2140, 900, 2400));
    assertEquals("Short pages never produce a negative or unnecessary scroll.",
        0, ReaderActivity.readingScrollTarget(0, 100, 140, 900, 700));
  }

  @Test
  public void keyboard_reader_transport_requires_explicit_read_activation()
      throws Exception
  {
    Context context = new ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), R.style.Light);
    assertTrue("Everyday keyboard mode owns the compact Reader transport.",
        layoutIncludes(context, R.layout.keyboard,
          R.layout.reader_transport_strip));
    assertTrue("Clipboard mode owns the same compact Reader transport.",
        layoutIncludes(context, R.layout.clipboard_pane,
          R.layout.reader_transport_strip));

    View transport = LayoutInflater.from(context).inflate(
        R.layout.reader_transport_strip, null, false);
    assertEquals("The transport must not consume keyboard height before state is known.",
        View.GONE, transport.getVisibility());
    assertFalse("Active playback stays collapsed until a Reader action is pressed.",
        Keyboard2.reader_transport_visible(true, true, false));
    assertTrue("An explicit Reader action reveals active playback controls.",
        Keyboard2.reader_transport_visible(true, true, true));
    assertFalse("Reader actions cannot expose empty playback controls.",
        Keyboard2.reader_transport_visible(true, false, true));
    assertFalse("The default-off setting suppresses playback controls.",
        Keyboard2.reader_transport_visible(false, true, true));
    assertFalse("The empty candidates bar is removed above Reader actions.",
        Keyboard2.candidate_strip_visible(true, true));
    assertFalse("The empty candidates bar is removed above playback controls.",
        Keyboard2.candidate_strip_visible(true, true));
    assertTrue("The candidates bar returns when the Reader strip hides.",
        Keyboard2.candidate_strip_visible(true, false));
    assertFalse("The Reader strip never forces a disabled candidates bar on.",
        Keyboard2.candidate_strip_visible(false, false));
    View clipboard = transport.findViewById(R.id.reader_transport_clipboard);
    View library = transport.findViewById(R.id.reader_transport_library);
    int compactHeight = Math.round(36f *
        context.getResources().getDisplayMetrics().density);
    int verticalPadding = Math.round(4f *
        context.getResources().getDisplayMetrics().density);
    int horizontalPadding = Math.round(16f *
        context.getResources().getDisplayMetrics().density);
    int actionGap = Math.round(8f *
        context.getResources().getDisplayMetrics().density);
    assertEquals("Read Clipboard uses the requested compact 36dp height.",
        compactHeight, clipboard.getLayoutParams().height);
    assertEquals("Library uses the requested compact 36dp height.",
        compactHeight, library.getLayoutParams().height);
    assertEquals("The Reader strip keeps an 8dp breathing space above its controls.",
        actionGap, transport.getPaddingTop());
    assertEquals("The Reader strip keeps an 8dp breathing space below its controls.",
        actionGap, transport.getPaddingBottom());
    assertEquals("Read Clipboard uses the keyboard action surface.",
        R.drawable.reader_keyboard_action_button,
        layoutAttributeResource(context, R.layout.reader_transport_strip,
          R.id.reader_transport_clipboard, "background"));
    assertEquals("Library uses the keyboard action surface.",
        R.drawable.reader_keyboard_action_button,
        layoutAttributeResource(context, R.layout.reader_transport_strip,
          R.id.reader_transport_library, "background"));
    android.util.TypedValue labelColor = new android.util.TypedValue();
    assertTrue(context.getTheme().resolveAttribute(
        R.attr.colorLabel, labelColor, true));
    assertEquals("Read Clipboard uses the keyboard label color.",
        labelColor.data, ((TextView)clipboard).getCurrentTextColor());
    assertEquals("Library uses the keyboard label color.",
        labelColor.data, ((TextView)library).getCurrentTextColor());
    assertEquals("Compact actions keep 4dp top and bottom padding.",
        verticalPadding, clipboard.getPaddingTop());
    assertEquals("Read Clipboard keeps professional horizontal padding.",
        horizontalPadding, clipboard.getPaddingLeft());
    assertEquals("Read Clipboard text stays clear of its right edge.",
        horizontalPadding, clipboard.getPaddingRight());
    assertEquals("Library keeps professional horizontal padding.",
        horizontalPadding, library.getPaddingLeft());
    assertEquals("Library text stays clear of its right edge.",
        horizontalPadding, library.getPaddingRight());
    assertEquals("Compact actions keep a visible 8dp gap.",
        actionGap,
        ((ViewGroup.MarginLayoutParams)library.getLayoutParams())
          .getMarginStart());
    int minimum = Math.round(48f *
        context.getResources().getDisplayMetrics().density);
    int[] controls = {
      R.id.reader_transport_previous, R.id.reader_transport_play_pause,
      R.id.reader_transport_next, R.id.reader_transport_stop
    };
    for (int id : controls)
    {
      View control = transport.findViewById(id);
      int declaredHeight = control.getLayoutParams().height;
      assertTrue("Compact Reader controls retain a 48dp touch target: " + id,
          control.getMinimumHeight() >= minimum || declaredHeight >= minimum);
      assertNotNull("Compact Reader controls use a rounded surface: " + id,
          control.getBackground());
      assertFalse("Compact Reader controls require accessible labels: " + id,
          control.getContentDescription().toString().isEmpty());
    }
    int[] iconControls = {
      R.id.reader_transport_previous, R.id.reader_transport_play_pause,
      R.id.reader_transport_next, R.id.reader_transport_stop
    };
    for (int id : iconControls)
    {
      View control = transport.findViewById(id);
      assertTrue("Keyboard transport uses centered icon controls: " + id,
          control instanceof ImageButton);
      assertNotNull("Keyboard transport icons must be visible: " + id,
          ((ImageButton)control).getDrawable());
      assertEquals("Keyboard transport icons remain exactly centered: " + id,
          ImageView.ScaleType.CENTER, ((ImageButton)control).getScaleType());
    }
  }

  @Test
  public void keyboard_reader_actions_appear_only_for_an_empty_readable_editor()
  {
    assertTrue("An enabled Reader exposes actions in an empty readable editor.",
        Keyboard2.reader_entry_visible(
          true, true, true, false, false, false));
    assertFalse("Reader actions stay absent until the user enables them.",
        Keyboard2.reader_entry_visible(
          false, true, true, false, false, false));
    assertFalse("The first editor text immediately hides both Reader actions.",
        Keyboard2.reader_entry_visible(
          true, true, false, false, false, false));
    assertFalse("Composing text hides both Reader actions.",
        Keyboard2.reader_entry_visible(
          true, true, true, true, false, false));
    assertFalse("Visible candidates take precedence over Reader actions.",
        Keyboard2.reader_entry_visible(
          true, true, true, false, true, false));
    assertTrue("Collapsed playback leaves the empty-editor actions available.",
        Keyboard2.reader_entry_visible(
          true, true, true, false, false, false));
    assertFalse("Expanded playback controls replace the compact action row.",
        Keyboard2.reader_entry_visible(
          true, true, true, false, false, true));
    assertFalse("Private or unsupported editors never expose Reader text access.",
        Keyboard2.reader_entry_visible(
          true, false, true, false, false, false));
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
  public void share_and_process_text_use_an_exported_validator_and_opaque_handoff()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    ActivityInfo shareInfo = context.getPackageManager().getActivityInfo(
        new ComponentName(context, ReaderShareActivity.class),
        PackageManager.GET_META_DATA);
    assertTrue("Android must be able to discover the dedicated Reader share target.",
        shareInfo.exported);
    assertTrue("Highlighted-text hosts must know Reader never mutates the selection.",
        shareInfo.metaData.getBoolean(Intent.EXTRA_PROCESS_TEXT_READONLY));

    Intent share = new Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, "Shared private article text");
    ComponentName shareTarget = share.resolveActivity(context.getPackageManager());
    assertNotNull("Reader appears in Android's text share targets.", shareTarget);
    assertEquals(ReaderShareActivity.class.getName(), shareTarget.getClassName());
    ReaderShareActivity shareActivity = Robolectric.buildActivity(
        ReaderShareActivity.class, share).create().get();
    assertNull("Shared text must wait for explicit import confirmation.",
        shadowOf((Activity)shareActivity).getNextStartedActivity());
    assertOpaqueReaderLaunch(confirmImportAndAwait(shareActivity),
        "Shared private article text");

    Intent processText = new Intent(Intent.ACTION_PROCESS_TEXT)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT, "Highlighted private text");
    ComponentName processTarget =
      processText.resolveActivity(context.getPackageManager());
    assertNotNull("Reader appears in Android's highlighted-text actions.",
        processTarget);
    assertEquals(ReaderShareActivity.class.getName(),
        processTarget.getClassName());
    ReaderShareActivity processActivity = Robolectric.buildActivity(
        ReaderShareActivity.class, processText).create().get();
    assertOpaqueReaderLaunch(confirmImportAndAwait(processActivity),
        "Highlighted private text");
  }

  @Test
  public void external_reader_entry_rejects_missing_oversized_and_url_only_text()
  {
    Intent missing = new Intent(Intent.ACTION_SEND).setType("text/plain");
    ReaderShareActivity missingActivity = Robolectric.buildActivity(
        ReaderShareActivity.class, missing).create().get();
    assertNull("Missing shared text never reaches the private Reader activity.",
        shadowOf((Activity)missingActivity).getNextStartedActivity());

    Intent oversized = new Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT,
          repeat('x', ReaderPlaybackService.MAX_ACTIVE_TEXT_LENGTH + 1));
    ReaderShareActivity oversizedActivity = Robolectric.buildActivity(
        ReaderShareActivity.class, oversized).create().get();
    assertNull("Unbounded shared text never enters the in-memory Reader holder.",
        shadowOf((Activity)oversizedActivity).getNextStartedActivity());

    Intent urlOnly = new Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, "https://example.com/private-article");
    ReaderShareActivity urlActivity = Robolectric.buildActivity(
        ReaderShareActivity.class, urlOnly).create().get();
    assertNull("A URL-only share must not be spoken as raw text before hardened extraction exists.",
        shadowOf((Activity)urlActivity).getNextStartedActivity());
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
  public void keyboard_transport_play_requests_notification_permission_then_resumes()
  {
    Application application = RuntimeEnvironment.getApplication();
    ShadowApplication shadowApplication = shadowOf(application);
    shadowApplication.denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    ReaderPlaybackService playback = service();
    playback.load("restored-item", "Restored title",
        "Restored private text", false);

    ReaderActivity.startPlaybackRequest(application);
    Intent launched = shadowApplication.getNextStartedActivity();
    ReaderActivity activity = launchQuickRead(application, playback, launched);
    ShadowActivity.PermissionsRequest request =
      shadowOf(activity).getLastRequestedPermission();

    assertNotNull("Transport Play must immediately enter the permission flow.",
        request);
    assertArrayEquals(new String[]{Manifest.permission.POST_NOTIFICATIONS},
        request.requestedPermissions);
    assertNull("The permission Activity handoff contains no Reader content.",
        launched.getStringExtra(ReaderPlaybackService.EXTRA_TEXT));
    shadowApplication.getNextStartedService();

    activity.onRequestPermissionsResult(request.requestCode,
        request.requestedPermissions,
        new int[]{PackageManager.PERMISSION_GRANTED});

    Intent started = shadowApplication.getNextStartedService();
    assertNotNull(started);
    assertEquals(ReaderPlaybackService.ACTION_PLAY, started.getAction());
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

  private static Intent confirmImportAndAwait(ReaderShareActivity activity)
      throws Exception
  {
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    assertNotNull("External Reader text must require explicit import confirmation.",
        dialog);
    assertTrue("The import confirmation must remain visible for a user decision.",
        dialog.isShowing());
    assertTrue("The confirmation must expose an affirmative import action.",
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick());

    long deadline = System.nanoTime() + 3_000_000_000L;
    do
    {
      shadowOf(Looper.getMainLooper()).idle();
      Intent launched =
        shadowOf((Activity)activity).getNextStartedActivity();
      if (launched != null)
        return launched;
      Thread.sleep(2L);
    }
    while (System.nanoTime() < deadline);
    fail("Timed out waiting for confirmed Reader import to launch.");
    return null;
  }

  private static void assertOpaqueReaderLaunch(Intent launched,
      String privateText)
  {
    assertNotNull(launched);
    assertEquals(ReaderActivity.class.getName(),
        launched.getComponent().getClassName());
    Bundle extras = launched.getExtras();
    assertNotNull(extras);
    assertEquals("External text crosses into Reader as one opaque token only.",
        1, extras.size());
    for (String key : extras.keySet())
      assertNotEquals("Private shared text must not appear in activity extras.",
          privateText, extras.get(key));
  }

  private static int countViewsOfType(View root, Class<?> type)
  {
    int count = type.isInstance(root) ? 1 : 0;
    if (!(root instanceof ViewGroup))
      return count;
    ViewGroup group = (ViewGroup)root;
    for (int i = 0; i < group.getChildCount(); i++)
      count += countViewsOfType(group.getChildAt(i), type);
    return count;
  }

  private static boolean layoutContainsId(Context context, int layout,
      int viewId)
      throws Exception
  {
    XmlResourceParser parser = context.getResources().getLayout(layout);
    try
    {
      while (parser.next() != XmlPullParser.END_DOCUMENT)
      {
        if (parser.getEventType() == XmlPullParser.START_TAG &&
            parser.getAttributeResourceValue(
              "http://schemas.android.com/apk/res/android", "id", 0) ==
              viewId)
          return true;
      }
      return false;
    }
    finally
    {
      parser.close();
    }
  }

  private static int layoutAttributeResource(Context context, int layout,
      int viewId, String attribute)
      throws Exception
  {
    XmlResourceParser parser = context.getResources().getLayout(layout);
    try
    {
      while (parser.next() != XmlPullParser.END_DOCUMENT)
      {
        if (parser.getEventType() == XmlPullParser.START_TAG &&
            parser.getAttributeResourceValue(
              "http://schemas.android.com/apk/res/android", "id", 0) ==
              viewId)
          return parser.getAttributeResourceValue(
              "http://schemas.android.com/apk/res/android", attribute, 0);
      }
      return 0;
    }
    finally
    {
      parser.close();
    }
  }


  private static boolean layoutIncludes(Context context, int layout,
      int includedLayout)
      throws Exception
  {
    XmlResourceParser parser = context.getResources().getLayout(layout);
    try
    {
      while (parser.next() != XmlPullParser.END_DOCUMENT)
      {
        if (parser.getEventType() == XmlPullParser.START_TAG &&
            "include".equals(parser.getName()) &&
            parser.getAttributeResourceValue(null, "layout", 0) ==
              includedLayout)
          return true;
      }
      return false;
    }
    finally
    {
      parser.close();
    }
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
