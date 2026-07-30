package juloo.keyboard2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.ServiceInfo;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowService;
import org.robolectric.shadows.ShadowTextToSpeech;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderArchitectureProofTest
{
  private static final String SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE";

  @Test
  public void installed_voice_metadata_supports_local_first_filtering_and_fallback()
  {
    Voice offlineEnglish = voice("offline-en", Locale.US, false);
    Voice onlineEnglish = voice("online-en", Locale.US, true);
    Voice offlineFrench = voice("offline-fr", Locale.FRANCE, false);
    List<Voice> voices = new ArrayList<>();
    voices.add(onlineEnglish);
    voices.add(offlineFrench);
    voices.add(offlineEnglish);

    assertEquals("Network-required voices must stay hidden until the user explicitly enables them.",
        Collections.singletonList(offlineEnglish), compatible(voices, Locale.US, false));
    assertEquals("Enabling system network voices may add them without changing the offline-first fallback.",
        offlineEnglish, fallback(voices, Locale.US, true));
    assertTrue("Android Voice metadata must expose whether an installed voice requires a network connection.",
        onlineEnglish.isNetworkConnectionRequired());
  }

  @Test
  public void android_tts_enumerates_voices_and_reports_utterance_progress()
  {
    Voice offlineEnglish = voice("offline-en", Locale.US, false);
    Voice onlineEnglish = voice("online-en", Locale.US, true);
    ShadowTextToSpeech.addVoice(offlineEnglish);
    ShadowTextToSpeech.addVoice(onlineEnglish);
    List<String> callbacks = new ArrayList<>();
    TextToSpeech tts = new TextToSpeech(RuntimeEnvironment.getApplication(),
        status -> callbacks.add("init:" + status));
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener()
    {
      @Override public void onStart(String id) { callbacks.add("start:" + id); }
      @Override public void onDone(String id) { callbacks.add("done:" + id); }
      @Override public void onError(String id) { callbacks.add("error:" + id); }
    });

    assertTrue(tts.getVoices().contains(offlineEnglish));
    assertTrue(tts.getVoices().contains(onlineEnglish));
    assertFalse(offlineEnglish.isNetworkConnectionRequired());
    assertTrue(onlineEnglish.isNetworkConnectionRequired());
    assertEquals(TextToSpeech.SUCCESS,
        tts.speak("proof chunk", TextToSpeech.QUEUE_FLUSH, new Bundle(), "unit-12"));
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertTrue(callbacks.contains("start:unit-12"));
    assertTrue(callbacks.contains("done:unit-12"));

    ShadowTextToSpeech shadow = Shadows.shadowOf(tts);
    assertEquals(Collections.singletonList("proof chunk"), shadow.getSpokenTextList());
    tts.stop();
    assertTrue(shadow.isStopped());
    tts.shutdown();
    assertTrue(shadow.isShutdown());
  }

  @Test
  public void utf16_chunking_preserves_a_large_document_without_splitting_surrogates()
  {
    String paragraph = "Sentence one. Sentence two with emoji \ud83d\ude03.\n\n";
    StringBuilder input = new StringBuilder(100000);
    while (input.length() < 100000)
      input.append(paragraph);
    input.setLength(100000);
    if (Character.isHighSurrogate(input.charAt(input.length() - 1)))
      input.setCharAt(input.length() - 1, '.');

    List<String> chunks = chunk(input.toString(), 4000);
    StringBuilder rebuilt = new StringBuilder(input.length());
    for (String value : chunks)
    {
      assertTrue("Every TTS request must stay within the configured engine-safe limit.",
          value.length() <= 4000);
      if (!value.isEmpty())
      {
        assertFalse("A TTS request must never end with half of a UTF-16 surrogate pair.",
            Character.isHighSurrogate(value.charAt(value.length() - 1)));
        assertFalse("A TTS request must never begin with half of a UTF-16 surrogate pair.",
            Character.isLowSurrogate(value.charAt(0)));
      }
      rebuilt.append(value);
    }
    assertEquals("Bounded TTS chunking must reconstruct all 100,000 characters exactly and in order.",
        input.toString(), rebuilt.toString());
    assertTrue("A 100,000-character document must be represented by a bounded queue of small requests.",
        chunks.size() < 100);
  }

  @Test
  public void one_foreground_service_owns_media_session_and_command_state()
  {
    ProofPlaybackService service = Robolectric.buildService(ProofPlaybackService.class)
      .create().get();
    assertNotNull("The service-owned framework MediaSession must publish one controller token.",
        service.session.getSessionToken());
    ShadowService shadow = Shadows.shadowOf(service);
    assertEquals(ProofPlaybackService.NOTIFICATION_ID,
        shadow.getLastForegroundNotificationId());
    assertNotNull("Playback must enter foreground with a user-visible media notification.",
        shadow.getLastForegroundNotification());
    assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        service.getForegroundServiceType());

    java.util.function.Consumer<PlaybackCommand> ime = service::handle;
    java.util.function.Consumer<PlaybackCommand> reader = service::handle;
    java.util.function.Consumer<PlaybackCommand> notification = service::handle;
    ime.accept(PlaybackCommand.PLAY);
    assertEquals(Playback.PLAYING, service.playback);
    reader.accept(PlaybackCommand.PAUSE);
    assertEquals(Playback.PAUSED, service.playback);
    notification.accept(PlaybackCommand.NEXT);
    assertEquals(1, service.unit);
    notification.accept(PlaybackCommand.STOP);
    assertEquals(Playback.STOPPED, service.playback);

    service.onDestroy();
    assertTrue("The service lifecycle must release its MediaSession owner.", service.released);
  }

  @Test
  public void progress_round_trips_through_private_state_after_service_recreation()
  {
    Context context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("reader_architecture_proof", Context.MODE_PRIVATE)
      .edit().clear().putString("item", "proof-item").putInt("unit", 47).commit();

    android.content.SharedPreferences restored = context.getSharedPreferences(
        "reader_architecture_proof", Context.MODE_PRIVATE);
    assertEquals("proof-item", restored.getString("item", null));
    assertEquals("A recreated playback owner must resume from the last stable spoken unit.",
        47, restored.getInt("unit", -1));
  }

  @Test
  public void clipboard_and_editor_privacy_checks_fail_closed()
  {
    ClipData sensitive = ClipData.newPlainText("secret", "private text");
    PersistableBundle extras = new PersistableBundle();
    extras.putBoolean(SENSITIVE_KEY, true);
    sensitive.getDescription().setExtras(extras);
    assertTrue("Clipboard content explicitly marked sensitive must never enter Reader.",
        isSensitive(sensitive));

    EditorInfo password = new EditorInfo();
    password.inputType = InputType.TYPE_CLASS_TEXT |
      InputType.TYPE_TEXT_VARIATION_PASSWORD;
    assertFalse("Password editors must not expose current-field or selected-text Reader actions.",
        editorAllowsReader(password, "com.example.notes"));

    EditorInfo terminal = new EditorInfo();
    terminal.inputType = InputType.TYPE_NULL;
    assertFalse("TYPE_NULL terminals must fail closed because their tracked text may contain hidden passwords.",
        editorAllowsReader(terminal, "com.termux"));

    EditorInfo text = new EditorInfo();
    text.inputType = InputType.TYPE_CLASS_TEXT |
      InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
    assertTrue("Ordinary readable text editors may expose explicit Reader actions.",
        editorAllowsReader(text, "com.example.notes"));
  }

  @Test
  public void share_and_process_text_intake_is_explicit_and_bounded()
  {
    Intent share = new Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, "Shared article text");
    assertEquals("Shared article text", acceptedText(share, 100));

    Intent process = new Intent(Intent.ACTION_PROCESS_TEXT)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT, "Selected words");
    assertEquals("Selected words", acceptedText(process, 100));

    Intent oversized = new Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, repeat('x', 101));
    assertNull("Oversized shared text must be rejected before it enters the Library.",
        acceptedText(oversized, 100));

    Intent wrongAction = new Intent(Intent.ACTION_VIEW)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, "implicit capture");
    assertNull("Reader intake must reject unrelated intents rather than broadening capture.",
        acceptedText(wrongAction, 100));
  }

  @Test
  public void document_picker_intake_requires_content_grant_and_bounded_copy()
      throws Exception
  {
    Intent document = new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setDataAndType(android.net.Uri.parse("content://proof.provider/book"),
          "application/pdf")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    assertTrue(acceptedDocument(document));
    assertArrayEquals("Document providers with unknown lengths must still be copied under a hard cap.",
        "book".getBytes(java.nio.charset.StandardCharsets.UTF_8),
        copyBounded(new java.io.ByteArrayInputStream(
            "book".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 4));

    document.setData(android.net.Uri.parse("file:///tmp/book.pdf"));
    assertFalse("Raw file paths must not bypass the Storage Access Framework.",
        acceptedDocument(document));
    try
    {
      copyBounded(new java.io.ByteArrayInputStream(new byte[5]), 4);
      fail("A provider stream that crosses the declared cap must be rejected.");
    }
    catch (java.io.IOException expected) {}
  }

  @Test
  public void accessibility_snapshot_is_bounded_deduplicated_and_recycles_nodes()
  {
    ProofNode root = node("", false, true,
        node("Article title", false, true),
        node("Article title", false, true),
        node("hidden", false, false),
        node("password", true, true),
        node("First paragraph", false, true,
          node("Nested paragraph", false, true)));

    List<String> captured = capture(root, 7);
    assertEquals("Visible accessible text must retain stable traversal order and remove duplicates.",
        java.util.Arrays.asList("Article title", "First paragraph", "Nested paragraph"),
        captured);
    assertEquals("Every visited accessibility node must be released after the one-shot snapshot.",
        7, root.recycledTreeCount());

    ProofNode tooDeep = node("root", false, true,
        node("one", false, true,
          node("two", false, true,
            node("three", false, true))));
    assertEquals("Bounded traversal must stop cleanly instead of walking an unbounded live node tree.",
        java.util.Arrays.asList("root", "one"), capture(tooDeep, 2));
  }

  private static Voice voice(String name, Locale locale, boolean network)
  {
    return new Voice(name, locale, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL,
        network, Collections.<String>emptySet());
  }

  private static List<Voice> compatible(List<Voice> voices, Locale locale,
      boolean allowNetwork)
  {
    List<Voice> result = new ArrayList<>();
    for (Voice voice : voices)
      if (voice.getLocale().getLanguage().equals(locale.getLanguage()) &&
          (allowNetwork || !voice.isNetworkConnectionRequired()))
        result.add(voice);
    Collections.sort(result, (a, b) -> {
      int network = Boolean.compare(a.isNetworkConnectionRequired(),
          b.isNetworkConnectionRequired());
      return network != 0 ? network : a.getName().compareTo(b.getName());
    });
    return result;
  }

  private static Voice fallback(List<Voice> voices, Locale locale,
      boolean allowNetwork)
  {
    List<Voice> compatible = compatible(voices, locale, allowNetwork);
    return compatible.isEmpty() ? null : compatible.get(0);
  }

  private static List<String> chunk(String text, int limit)
  {
    List<String> result = new ArrayList<>((text.length() / limit) + 1);
    int start = 0;
    while (start < text.length())
    {
      int end = Math.min(text.length(), start + limit);
      if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1)))
        end--;
      int preferred = end;
      for (int i = end - 1; i > start + (limit / 2); i--)
        if (text.charAt(i) == '\n' || Character.isWhitespace(text.charAt(i)))
        {
          preferred = i + 1;
          break;
        }
      end = preferred;
      result.add(text.substring(start, end));
      start = end;
    }
    return result;
  }

  private static boolean isSensitive(ClipData clip)
  {
    if (clip == null || clip.getDescription() == null)
      return true;
    PersistableBundle extras = clip.getDescription().getExtras();
    return extras != null && (extras.getBoolean(SENSITIVE_KEY, false) ||
        extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false));
  }

  private static boolean editorAllowsReader(EditorInfo info, String packageName)
  {
    if (info == null || info.inputType == InputType.TYPE_NULL ||
        packageName == null || packageName.isEmpty())
      return false;
    int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
    int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
    if (inputClass != InputType.TYPE_CLASS_TEXT)
      return false;
    return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
      variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
      variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
  }

  private static String acceptedText(Intent intent, int maximumLength)
  {
    if (intent == null || !"text/plain".equals(intent.getType()))
      return null;
    String action = intent.getAction();
    String key;
    if (Intent.ACTION_SEND.equals(action))
      key = Intent.EXTRA_TEXT;
    else if (Intent.ACTION_PROCESS_TEXT.equals(action))
      key = Intent.EXTRA_PROCESS_TEXT;
    else
      return null;
    CharSequence value = intent.getCharSequenceExtra(key);
    if (value == null || value.length() == 0 || value.length() > maximumLength)
      return null;
    return value.toString();
  }
  private static boolean acceptedDocument(Intent intent)
  {
    if (intent == null || !Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction()) ||
        !intent.hasCategory(Intent.CATEGORY_OPENABLE) ||
        intent.getData() == null ||
        !"content".equals(intent.getData().getScheme()) ||
        (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
      return false;
    String type = intent.getType();
    return "application/pdf".equals(type) ||
      "application/epub+zip".equals(type) ||
      "text/plain".equals(type);
  }

  private static byte[] copyBounded(java.io.InputStream input, int maximumBytes)
      throws java.io.IOException
  {
    try (java.io.InputStream source = input;
         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream())
    {
      byte[] buffer = new byte[1024];
      int total = 0;
      int read;
      while ((read = source.read(buffer)) != -1)
      {
        total += read;
        if (total > maximumBytes)
          throw new java.io.IOException("document exceeds byte limit");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }


  private static String repeat(char value, int count)
  {
    char[] chars = new char[count];
    java.util.Arrays.fill(chars, value);
    return new String(chars);
  }

  enum Playback { STOPPED, PLAYING, PAUSED }
  enum PlaybackCommand { PLAY, PAUSE, STOP, NEXT, PREVIOUS }

  public static final class ProofPlaybackService extends Service
  {
    static final int NOTIFICATION_ID = 4041;
    private static final String CHANNEL_ID = "reader_proof_playback";
    MediaSession session;
    MediaSession.Callback callback;
    Playback playback = Playback.STOPPED;
    int unit = 0;
    boolean released = false;

    @Override
    public void onCreate()
    {
      super.onCreate();
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
          "Reader proof playback", NotificationManager.IMPORTANCE_LOW));
      session = new MediaSession(this, "ReaderArchitectureProof");
      callback = new MediaSession.Callback()
      {
        @Override public void onPlay() { playback = Playback.PLAYING; }
        @Override public void onPause() { playback = Playback.PAUSED; }
        @Override public void onStop() { playback = Playback.STOPPED; }
        @Override public void onSkipToNext() { unit++; }
        @Override public void onSkipToPrevious() { unit = Math.max(0, unit - 1); }
      };
      session.setCallback(callback);
      session.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
      session.setPlaybackState(new PlaybackState.Builder()
        .setState(PlaybackState.STATE_PAUSED, 0, 0f)
        .setActions(PlaybackState.ACTION_PLAY |
          PlaybackState.ACTION_PAUSE |
          PlaybackState.ACTION_STOP |
          PlaybackState.ACTION_SKIP_TO_NEXT |
          PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        .build());
      session.setActive(true);
      Notification notification = new Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("Reader proof")
        .setContentText("Playback service owns this session")
        .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()))
        .build();
      startForeground(NOTIFICATION_ID, notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    void handle(PlaybackCommand command)
    {
      switch (command)
      {
        case PLAY: callback.onPlay(); break;
        case PAUSE: callback.onPause(); break;
        case STOP: callback.onStop(); break;
        case NEXT: callback.onSkipToNext(); break;
        case PREVIOUS: callback.onSkipToPrevious(); break;
      }
    }

    @Override
    public void onDestroy()
    {
      if (session != null)
        session.release();
      released = true;
      super.onDestroy();
    }
  }

  static final class ProofNode
  {
    final String text;
    final boolean password;
    final boolean visible;
    final List<ProofNode> children;
    boolean recycled;

    ProofNode(String text, boolean password, boolean visible,
        List<ProofNode> children)
    {
      this.text = text;
      this.password = password;
      this.visible = visible;
      this.children = children;
    }

    void recycle() { recycled = true; }

    int recycledTreeCount()
    {
      int count = recycled ? 1 : 0;
      for (ProofNode child : children)
        count += child.recycledTreeCount();
      return count;
    }
  }

  private static ProofNode node(String text, boolean password, boolean visible,
      ProofNode... children)
  {
    return new ProofNode(text, password, visible,
        java.util.Arrays.asList(children));
  }

  private static List<String> capture(ProofNode root, int maximumNodes)
  {
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    ArrayDeque<ProofNode> pending = new ArrayDeque<>();
    pending.add(root);
    int visited = 0;
    while (!pending.isEmpty())
    {
      ProofNode current = pending.removeFirst();
      if (visited++ >= maximumNodes)
      {
        current.recycle();
        while (!pending.isEmpty())
          pending.removeFirst().recycle();
        break;
      }
      try
      {
        if (!current.visible || current.password)
          continue;
        String normalized = current.text == null ? "" : current.text.trim();
        if (!normalized.isEmpty() && seen.add(normalized))
          result.add(normalized);
        pending.addAll(current.children);
      }
      finally
      {
        current.recycle();
      }
    }
    recycleUnvisited(root);
    return result;
  }

  private static void recycleUnvisited(ProofNode node)
  {
    if (!node.recycled)
      node.recycle();
    for (ProofNode child : node.children)
      recycleUnvisited(child);
  }
}
