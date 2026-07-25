package juloo.keyboard2.autocorrect;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RealImeThroughputInstrumentedTest
{
  private static final String RELEASE_IME_PACKAGE = "dev.frankenkey.keyboard";
  private String _imePackage;
  private String _imeId;
  private String _imeLauncher;
  private static final String HOST_PACKAGE = "dev.frankenkey.imehost";
  private static final String HOST_ACTIVITY =
    "dev.frankenkey.imehost/.MainActivity";
  private static final int BASE_WIDTH = 1080;
  private static final int BASE_HEIGHT = 2400;
  private static final Pattern TOKEN = Pattern.compile(
      "[A-Za-z]+(?:'[A-Za-z]+)?");
  private static final Map<Character, PointF> KEYS = keys();
  private static final PointF SHIFT = point(84, 1960);
  private static final PointF SPACE = point(475, 2160);
  private static final PointF PERIOD = point(837, 2160);
  private static final PointF HOST_FOCUS = point(540, 700);

  private Instrumentation _instrumentation;
  private UiAutomation _automation;
  private Context _target;
  private float _scaleX;
  private float _scaleY;
  private long _keyIntervalMs;

  @Before
  public void setUp() throws Exception
  {
    _instrumentation = InstrumentationRegistry.getInstrumentation();
    _automation = _instrumentation.getUiAutomation();
    _target = _instrumentation.getTargetContext();
    _imePackage = InstrumentationRegistry.getArguments().getString(
        "ime_package", RELEASE_IME_PACKAGE);
    _imeId = _imePackage + "/juloo.keyboard2.Keyboard2";
    _imeLauncher = _imePackage + "/juloo.keyboard2.LauncherActivity";
    DisplayMetrics metrics = new DisplayMetrics();
    WindowManager window = (WindowManager)_target.getSystemService(
        Context.WINDOW_SERVICE);
    window.getDefaultDisplay().getRealMetrics(metrics);
    _scaleX = metrics.widthPixels / (float)BASE_WIDTH;
    _keyIntervalMs = Long.parseLong(InstrumentationRegistry.getArguments()
        .getString("key_interval_ms", "0"));
    _scaleY = metrics.heightPixels / (float)BASE_HEIGHT;

    assertTrue("The benchmark host must be installed before running the real-IME gate.",
        shell("pm list packages " + HOST_PACKAGE).contains(HOST_PACKAGE));
    if (_imePackage.equals(_target.getPackageName()))
    {
      android.preference.PreferenceManager.getDefaultSharedPreferences(_target)
        .edit().clear().commit();
      if (android.os.Build.VERSION.SDK_INT >= 24)
        android.preference.PreferenceManager.getDefaultSharedPreferences(
            _target.createDeviceProtectedStorageContext())
          .edit().clear().commit();
    }
    else
      shell("pm clear " + _imePackage);
    shell("am start -W -n " + _imeLauncher);
    shell("ime enable " + _imeId);
    shell("ime set " + _imeId);
    SystemClock.sleep(1000);
  }

  @Test
  public void maximumThroughputTargetedGate() throws Exception
  {
    JSONObject corpus = new JSONObject(readAsset(
          "keyboard_chapter_typo_corpus.json"));
    JSONArray cases = corpus.getJSONArray("targeted_sequences");
    JSONArray results = new JSONArray();
    StringBuilder failures = new StringBuilder();
    int harmful = 0;
    int gatedProtected = 0;
    int retainedProtected = 0;

    for (int i = 0; i < cases.length(); i++)
    {
      JSONObject spec = cases.getJSONObject(i);
      CaseResult result = runCase(spec);
      results.put(result.json);
      if (spec.optBoolean("diagnostic_only", false))
        continue;
      harmful += result.harmful;
      gatedProtected += result.protectedCount;
      retainedProtected += result.protectedRetained;
      if ("reported-regressions-fast".equals(spec.getString("id"))
          && result.corrected != result.typoCount)
        failures.append("reported-regressions-fast corrected ")
          .append(result.corrected).append('/').append(result.typoCount)
          .append('\n');
      if ("uppercase-protection-fast".equals(spec.getString("id"))
          && result.protectedRetained != result.protectedCount)
        failures.append("uppercase protection retained ")
          .append(result.protectedRetained).append('/')
          .append(result.protectedCount).append('\n');
    }

    if (harmful > corpus.getJSONObject("gates")
        .getInt("maximum_harmful_corrections"))
      failures.append("harmful corrections: ").append(harmful).append('\n');
    if (retainedProtected != gatedProtected)
      failures.append("protected retention: ").append(retainedProtected)
        .append('/').append(gatedProtected).append('\n');

    JSONObject report = report("targeted", results, failures);
    writeReport("frankenkey-real-ime-fast-targeted.json", report);
    assertEquals("Maximum-throughput real-IME failures; report="
        + reportPath("frankenkey-real-ime-fast-targeted.json") + "\n"
        + failures, 0, failures.length());
  }

  @Test
  public void reportedShortWordContextGate() throws Exception
  {
    JSONObject corpus = new JSONObject(readAsset(
          "keyboard_chapter_typo_corpus.json"));
    JSONArray cases = corpus.getJSONArray("targeted_sequences");
    JSONObject spec = null;
    for (int i = 0; i < cases.length(); ++i)
      if ("live-report-short-latent-failure".equals(
            cases.getJSONObject(i).getString("id")))
      {
        spec = cases.getJSONObject(i);
        break;
      }
    assertNotNull("The reported short-word case must remain in the real-IME corpus.",
        spec);
    CaseResult result = runCase(spec);
    assertEquals("The reported sentence must repair decisive unknown words while preserving recognized context-dependent literals until editor-verified evidence exists.",
        spec.getString("safe_expected"), result.json.getString("actual"));
  }

  @Test
  public void shortUnknownWordCompletesWithinLatentWindow() throws Exception
  {
    JSONObject spec = new JSONObject()
      .put("id", "short-latent-window")
      .put("typed", "twi and three.")
      .put("expected", "Two and three.");
    CaseResult result = runCase(spec);
    assertEquals("A decisive short repair must complete while its exact editor boundary remains rewritable.",
        spec.getString("expected"), result.json.getString("actual"));
  }


  @Test
  public void maximumThroughputChapterGate() throws Exception
  {
    JSONObject corpus = new JSONObject(readAsset(
          "keyboard_chapter_typo_corpus.json"));
    JSONArray sentences = corpus.getJSONArray("sentences");
    StringBuilder typed = new StringBuilder();
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < sentences.length(); i++)
    {
      if (i > 0)
      {
        typed.append(' ');
        expected.append(' ');
      }
      typed.append(sentences.getJSONObject(i).getString("typed"));
      expected.append(sentences.getJSONObject(i).getString("expected"));
    }
    JSONObject spec = new JSONObject()
      .put("id", corpus.getString("id"))
      .put("typed", typed.toString())
      .put("expected", expected.toString());
    CaseResult result = runCase(spec);
    StringBuilder failures = new StringBuilder();
    JSONObject gates = corpus.getJSONObject("gates");
    if (result.corrected < gates.getInt("chapter_corrections_min"))
      failures.append("chapter corrections: ").append(result.corrected)
        .append('/').append(result.typoCount).append('\n');
    if (result.harmful > gates.getInt("maximum_harmful_corrections"))
      failures.append("chapter harmful corrections: ")
        .append(result.harmful).append('\n');
    if (result.protectedRetained != result.protectedCount)
      failures.append("chapter protected retention: ")
        .append(result.protectedRetained).append('/')
        .append(result.protectedCount).append('\n');

    JSONArray results = new JSONArray().put(result.json);
    JSONObject report = report("chapter", results, failures);
    writeReport("frankenkey-real-ime-fast-chapter.json", report);
    assertEquals("Maximum-throughput chapter failures; report="
        + reportPath("frankenkey-real-ime-fast-chapter.json") + "\n"
        + failures, 0, failures.length());
  }

  private CaseResult runCase(JSONObject spec) throws Exception
  {
    shell("am force-stop " + HOST_PACKAGE);
    shell("am start -W -n " + HOST_ACTIVITY);
    waitForEditor();
    waitForKeyboard();

    String typed = spec.getString("typed");
    long started = SystemClock.elapsedRealtimeNanos();
    type(typed);
    long elapsed = SystemClock.elapsedRealtimeNanos() - started;
    SystemClock.sleep(1000);
    String actual = editorText();
    return score(spec, actual, elapsed);
  }


  private void waitForEditor()
  {
    long deadline = SystemClock.uptimeMillis() + 10_000L;
    do
    {
      AccessibilityNodeInfo root = _automation.getRootInActiveWindow();
      if (root != null)
      {
        AccessibilityNodeInfo editor = findEditor(root);
        if (editor != null && editor.isVisibleToUser())
          return;
      }
      SystemClock.sleep(100L);
    }
    while (SystemClock.uptimeMillis() < deadline);
    fail("Timed out waiting for benchmark editor.");
  }


  private void waitForKeyboard() throws Exception
  {
    long deadline = SystemClock.uptimeMillis() + 10_000L;
    String lastState = "";
    do
    {
      tap(HOST_FOCUS);
      SystemClock.sleep(250L);
      String state = lastState = shell("dumpsys input_method");
      if (state.contains("mCurImeId=" + _imeId)
          && state.contains("packageName=" + HOST_PACKAGE)
          && state.contains("mInputStarted=true mInputViewStarted=true")
          && state.contains("mIsInputViewShown=true"))
      {
        SystemClock.sleep(250L);
        return;
      }
    }
    while (SystemClock.uptimeMillis() < deadline);
    fail("Timed out waiting for input method window " + _imeId
        + " [current=" + lastState.contains("mCurImeId=" + _imeId)
        + ", host=" + lastState.contains("packageName=" + HOST_PACKAGE)
        + ", started=" + lastState.contains(
          "mInputStarted=true mInputViewStarted=true")
        + ", shown=" + lastState.contains("mIsInputViewShown=true") + "]");
  }

  private void type(String text)
  {
    for (int offset = 0; offset < text.length(); offset++)
    {
      char c = text.charAt(offset);
      PointF key = KEYS.get(Character.toLowerCase(c));
      if (key != null)
      {
        if (Character.isUpperCase(c))
          tap(SHIFT);
        tap(key);
      }
      else if (c == ' ')
        tap(SPACE);
      else if (c == '.')
        tap(PERIOD);
      else
        throw new IllegalArgumentException(
            "Unsupported benchmark character: " + c);
      if (_keyIntervalMs > 0)
        SystemClock.sleep(_keyIntervalMs);
    }
  }

  private void tap(PointF point)
  {
    float x = point.x * _scaleX;
    float y = point.y * _scaleY;
    long downTime = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(downTime, downTime,
        MotionEvent.ACTION_DOWN, x, y, 0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    MotionEvent up = MotionEvent.obtain(downTime, downTime + 1,
        MotionEvent.ACTION_UP, x, y, 0);
    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    try
    {
      assertTrue("Touch down injection failed.",
          _automation.injectInputEvent(down, true));
      assertTrue("Touch up injection failed.",
          _automation.injectInputEvent(up, true));
    }
    finally
    {
      down.recycle();
      up.recycle();
    }
  }

  private String editorText()
  {
    AccessibilityNodeInfo root = _automation.getRootInActiveWindow();
    assertNotNull("Benchmark window must expose an accessibility root.", root);
    AccessibilityNodeInfo field = findEditor(root);
    assertNotNull("Benchmark host must expose one EditText.", field);
    CharSequence text = field.getText();
    return text == null ? "" : text.toString();
  }

  private static AccessibilityNodeInfo findEditor(AccessibilityNodeInfo node)
  {
    if ("android.widget.EditText".contentEquals(node.getClassName()))
      return node;
    for (int i = 0; i < node.getChildCount(); i++)
    {
      AccessibilityNodeInfo child = node.getChild(i);
      if (child == null)
        continue;
      AccessibilityNodeInfo found = findEditor(child);
      if (found != null)
        return found;
    }
    return null;
  }

  private static CaseResult score(JSONObject spec, String actual, long elapsed)
      throws Exception
  {
    List<String> typed = tokens(spec.getString("typed"));
    List<String> expected = tokens(spec.getString("expected"));
    List<String> observed = tokens(actual);
    int count = Math.max(typed.size(), Math.max(expected.size(), observed.size()));
    int typos = 0;
    int corrected = 0;
    int unchanged = 0;
    int harmful = 0;
    int protectedCount = 0;
    int protectedRetained = 0;
    JSONArray outcomes = new JSONArray();
    for (int i = 0; i < count; i++)
    {
      String source = i < typed.size() ? typed.get(i) : "";
      String target = i < expected.size() ? expected.get(i) : "";
      String seen = i < observed.size() ? observed.get(i) : "";
      String outcome;
      if (source.equalsIgnoreCase(target))
      {
        protectedCount++;
        boolean caseSensitive = source.length() > 1
          && source.equals(source.toUpperCase(Locale.ROOT));
        boolean retained = caseSensitive
          ? source.equals(seen) : source.equalsIgnoreCase(seen);
        if (retained)
        {
          protectedRetained++;
          outcome = "protected-retained";
        }
        else
        {
          harmful++;
          outcome = "protected-changed";
        }
      }
      else
      {
        typos++;
        if (target.equalsIgnoreCase(seen))
        {
          corrected++;
          outcome = "corrected";
        }
        else if (source.equalsIgnoreCase(seen))
        {
          unchanged++;
          outcome = "unchanged";
        }
        else
        {
          harmful++;
          outcome = "wrong";
        }
      }
      outcomes.put(new JSONObject()
          .put("typed", source)
          .put("expected", target)
          .put("actual", seen)
          .put("outcome", outcome));
    }
    JSONObject json = new JSONObject()
      .put("id", spec.getString("id"))
      .put("diagnostic_only", spec.optBoolean("diagnostic_only", false))
      .put("typed", spec.getString("typed"))
      .put("expected", spec.getString("expected"))
      .put("actual", actual)
      .put("elapsed_ms", elapsed / 1000000.0)
      .put("characters_per_second",
          spec.getString("typed").length() / (elapsed / 1000000000.0))
      .put("typos", typos)
      .put("corrected", corrected)
      .put("unchanged", unchanged)
      .put("harmful", harmful)
      .put("protected", protectedCount)
      .put("protected_retained", protectedRetained)
      .put("outcomes", outcomes);
    return new CaseResult(json, typos, corrected, unchanged, harmful,
        protectedCount, protectedRetained);
  }

  private JSONObject report(String lane, JSONArray results,
      StringBuilder failures) throws Exception
  {
    return new JSONObject()
      .put("benchmark", "chapter-general-autocorrection-v1")
      .put("lane", lane)
      .put("input", "synchronous UiAutomation MotionEvent injection")
      .put("artificial_delay_ms", 0)
      .put("key_interval_ms", _keyIntervalMs)
      .put("installed_apk", installedIdentity())
      .put("results", results)
      .put("passed", failures.length() == 0)
      .put("failures", failures.toString());
  }

  private JSONObject installedIdentity() throws Exception
  {
    String pathOutput = shell("pm path " + _imePackage).trim();
    assertTrue(pathOutput, pathOutput.startsWith("package:"));
    String path = pathOutput.substring("package:".length());
    String dump = shell("dumpsys package " + _imePackage);
    Matcher code = Pattern.compile("versionCode=(\\d+)").matcher(dump);
    Matcher name = Pattern.compile("versionName=(\\S+)").matcher(dump);
    return new JSONObject()
      .put("package", _imePackage)
      .put("version_code", code.find() ? Integer.parseInt(code.group(1)) : -1)
      .put("version_name", name.find() ? name.group(1) : "unknown")
      .put("sha256", shell("sha256sum " + path).trim().split("\\s+")[0]);
  }

  private void writeReport(String name, JSONObject report) throws Exception
  {
    File file = new File(_target.getExternalFilesDir(null), name);
    FileOutputStream output = new FileOutputStream(file);
    try
    {
      output.write((report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
    }
    finally
    {
      output.close();
    }
    shell("cp " + file.getAbsolutePath() + " " + reportPath(name));
  }

  private String reportPath(String name)
  {
    return "/storage/emulated/0/Download/" + name;
  }

  private String shell(String command) throws Exception
  {
    ParcelFileDescriptor descriptor = _automation.executeShellCommand(command);
    InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
    try
    {
      return new String(readAll(input), StandardCharsets.UTF_8);
    }
    finally
    {
      input.close();
    }
  }

  private String readAsset(String name) throws Exception
  {
    InputStream input = _instrumentation.getContext().getAssets().open(name);
    try
    {
      return new String(readAll(input), StandardCharsets.UTF_8);
    }
    finally
    {
      input.close();
    }
  }

  private static byte[] readAll(InputStream input) throws Exception
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = input.read(buffer)) != -1)
      output.write(buffer, 0, read);
    return output.toByteArray();
  }

  private static List<String> tokens(String text)
  {
    ArrayList<String> tokens = new ArrayList<String>();
    Matcher matcher = TOKEN.matcher(text);
    while (matcher.find())
      tokens.add(matcher.group());
    return tokens;
  }

  private static Map<Character, PointF> keys()
  {
    HashMap<Character, PointF> keys = new HashMap<Character, PointF>();
    addRow(keys, "qwertyuiop", new int[] {
        60, 168, 275, 382, 489, 596, 703, 810, 917, 1024 }, 1525);
    addRow(keys, "asdfghjkl", new int[] {
        114, 221, 328, 435, 542, 649, 756, 863, 970 }, 1740);
    addRow(keys, "zxcvbnm", new int[] {
        221, 328, 435, 542, 649, 756, 863 }, 1960);
    return keys;
  }

  private static void addRow(Map<Character, PointF> keys, String letters,
      int[] xs, int y)
  {
    for (int i = 0; i < letters.length(); i++)
      keys.put(letters.charAt(i), point(xs[i], y));
  }

  private static PointF point(int x, int y)
  {
    return new PointF(x, y);
  }

  private static final class CaseResult
  {
    final JSONObject json;
    final int typoCount;
    final int corrected;
    final int unchanged;
    final int harmful;
    final int protectedCount;
    final int protectedRetained;

    CaseResult(JSONObject json_, int typoCount_, int corrected_, int unchanged_,
        int harmful_, int protectedCount_, int protectedRetained_)
    {
      json = json_;
      typoCount = typoCount_;
      corrected = corrected_;
      unchanged = unchanged_;
      harmful = harmful_;
      protectedCount = protectedCount_;
      protectedRetained = protectedRetained_;
    }
  }
}
