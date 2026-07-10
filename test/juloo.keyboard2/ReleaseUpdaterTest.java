package juloo.keyboard2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import java.util.Arrays;
import java.util.HashSet;
import juloo.keyboard2.snippets.SnippetStore;
import juloo.keyboard2.suggestions.PersonalizationStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReleaseUpdaterTest
{
  private static final String DIGEST =
    "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private SharedPreferences _preferences;

  @Before
  public void setUp()
  {
    Context context = RuntimeEnvironment.getApplication();
    _preferences = PreferenceManager.getDefaultSharedPreferences(context);
    _preferences.edit().clear().commit();
  }

  @Test
  public void parses_the_exact_release_contract() throws Exception
  {
    ReleaseUpdater.Release release = ReleaseUpdater.parseRelease(
        releaseJson("v2.0.27-vc78", 6017732L,
          "https://github.com/demetre19/FrankenKey/releases/download/v2.0.27-vc78/FrankenKey-installable-release.apk",
          DIGEST));

    assertEquals("The tag version name must drive the announcement label.",
        "2.0.27", release.versionName);
    assertEquals("The integer tag version code must drive update precedence.",
        78, release.versionCode);
    assertEquals("The release body must remain the user-visible changelog.",
        "Fixed important things.", release.changelog);
    assertEquals("The exact release asset size must be retained for bounded streaming.",
        6017732L, release.assetSize);
    assertEquals("The sha256: prefix must be removed only after validating lowercase hex.",
        DIGEST.substring("sha256:".length()), release.sha256);
  }

  @Test
  public void version_code_alone_controls_update_precedence() throws Exception
  {
    ReleaseUpdater.Release lowerNameHigherCode = ReleaseUpdater.parseRelease(
        releaseJson("v1.0.0-vc78", 42L,
          "https://example.com/update.apk", DIGEST));
    ReleaseUpdater.Release higherNameSameCode = ReleaseUpdater.parseRelease(
        releaseJson("v99.0.0-vc77", 42L,
          "https://example.com/update.apk", DIGEST));
    ReleaseUpdater.Release prereleaseName = ReleaseUpdater.parseRelease(
        releaseJson("v2.0.27-beta.1-vc79", 42L,
          "https://example.com/update.apk", DIGEST));

    assertTrue("A higher integer version code must update even if versionName appears lower.",
        ReleaseUpdater.isNewer(lowerNameHigherCode, 77));
    assertFalse("A cosmetic versionName change must never override an equal version code.",
        ReleaseUpdater.isNewer(higherNameSameCode, 77));
    assertEquals("The tag parser must preserve a valid Android versionName without interpreting it for precedence.",
        "2.0.27-beta.1", prereleaseName.versionName);
  }

  @Test
  public void rejects_malformed_tags_urls_digests_sizes_and_assets()
      throws Exception
  {
    assertRejected("Tags must exactly follow v<versionName>-vc<versionCode>.",
        releaseJson("2.0.27-78", 42L,
          "https://example.com/update.apk", DIGEST));
    assertRejected("Version codes must fit a positive Java integer.",
        releaseJson("v2.0.27-vc999999999999", 42L,
          "https://example.com/update.apk", DIGEST));
    assertRejected("APK download URLs must use HTTPS.",
        releaseJson("v2.0.27-vc78", 42L,
          "http://example.com/update.apk", DIGEST));
    assertRejected("Digests must use exact lowercase sha256 syntax.",
        releaseJson("v2.0.27-vc78", 42L,
          "https://example.com/update.apk",
          "sha256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
    assertRejected("Declared assets must stay within the strict 32 MiB cap.",
        releaseJson("v2.0.27-vc78", ReleaseUpdater.MAX_APK_BYTES + 1L,
          "https://example.com/update.apk", DIGEST));
    assertRejected("Zero-byte APK declarations must be rejected.",
        releaseJson("v2.0.27-vc78", 0L,
          "https://example.com/update.apk", DIGEST));
    assertRejected("The browser page must remain the matching official GitHub release.",
        releaseJsonWithHtml("v2.0.27-vc78", 42L,
          "https://example.com/update.apk", DIGEST,
          "https://github.com/attacker/FrankenKey/releases/tag/v2.0.27-vc78"));

    JSONObject missing = new JSONObject(releaseJson("v2.0.27-vc78", 42L,
          "https://example.com/update.apk", DIGEST));
    missing.put("assets", new JSONArray());
    assertRejected("The exact installable asset name is mandatory.", missing.toString());

    JSONObject duplicate = new JSONObject(releaseJson("v2.0.27-vc78", 42L,
          "https://example.com/update.apk", DIGEST));
    duplicate.getJSONArray("assets").put(
        new JSONObject(duplicate.getJSONArray("assets").getJSONObject(0).toString()));
    assertRejected("Duplicate exact-name assets are ambiguous and must be rejected.",
        duplicate.toString());
  }

  @Test
  public void automatic_checks_default_on_and_throttle_after_each_attempt()
  {
    long start = 1000000L;
    assertTrue("Automatic update checks must default on when no preference exists.",
        ReleaseUpdater.isAutomaticCheckDue(_preferences, start));

    ReleaseUpdater.recordAutomaticCheckAttempt(_preferences, start);
    assertFalse("An automatic attempt must prevent repeated network requests before 24 hours.",
        ReleaseUpdater.isAutomaticCheckDue(_preferences,
          start + ReleaseUpdater.AUTO_CHECK_INTERVAL_MS - 1L));
    assertTrue("The automatic check must become due exactly 24 hours after an attempt.",
        ReleaseUpdater.isAutomaticCheckDue(_preferences,
          start + ReleaseUpdater.AUTO_CHECK_INTERVAL_MS));

    _preferences.edit()
      .putBoolean(ReleaseUpdater.PREF_AUTOMATIC_CHECKS, false)
      .commit();
    assertFalse("The explicit automatic-check opt-out must always win.",
        ReleaseUpdater.isAutomaticCheckDue(_preferences,
          start + 2L * ReleaseUpdater.AUTO_CHECK_INTERVAL_MS));
  }

  @Test
  public void rejected_version_is_suppressed_automatically_but_manual_check_revisits()
      throws Exception
  {
    ReleaseUpdater.Release release = ReleaseUpdater.parseRelease(
        releaseJson("v2.0.27-vc78", 42L,
          "https://example.com/update.apk", DIGEST));
    ReleaseUpdater.rejectRelease(_preferences, release);

    assertFalse("A dismissed version must not interrupt the user on a later automatic check.",
        ReleaseUpdater.shouldAnnounce(_preferences, release, 77, false));
    assertTrue("A manual check must revisit a dismissed higher release.",
        ReleaseUpdater.shouldAnnounce(_preferences, release, 77, true));
    assertFalse("Neither automatic nor manual checks may announce a non-higher APK.",
        ReleaseUpdater.shouldAnnounce(_preferences, release, 78, true));
  }

  @Test
  public void delivery_defaults_to_in_app_but_no_action_exists_before_acceptance()
  {
    assertEquals("In-app authenticated delivery must be the default.",
        ReleaseUpdater.DELIVERY_IN_APP,
        ReleaseUpdater.deliveryMode(_preferences));
    assertEquals("No download, browser, or installer action may exist before explicit acceptance.",
        ReleaseUpdater.AcceptedAction.NONE,
        ReleaseUpdater.acceptedAction(false, ReleaseUpdater.DELIVERY_IN_APP));
    assertEquals("Accepted in-app delivery may proceed to authenticated download.",
        ReleaseUpdater.AcceptedAction.DOWNLOAD,
        ReleaseUpdater.acceptedAction(true, ReleaseUpdater.DELIVERY_IN_APP));
    assertEquals("Accepted GitHub delivery must open the release page instead of downloading.",
        ReleaseUpdater.AcceptedAction.OPEN_GITHUB,
        ReleaseUpdater.acceptedAction(true, ReleaseUpdater.DELIVERY_GITHUB));

    _preferences.edit().putString(ReleaseUpdater.PREF_DELIVERY, "unknown")
      .commit();
    assertEquals("Unknown persisted modes must fail closed to authenticated in-app delivery.",
        ReleaseUpdater.DELIVERY_IN_APP,
        ReleaseUpdater.deliveryMode(_preferences));
  }

  @Test
  public void installer_intent_is_read_only_user_ui_for_an_in_place_package()
  {
    Uri uri = Uri.parse(
        "content://dev.frankenkey.keyboard.updates/updates/FrankenKey-installable-release.apk");
    Intent intent = ReleaseUpdater.installerIntent(uri);

    assertEquals("Installation must go through Android's user-controlled VIEW flow.",
        Intent.ACTION_VIEW, intent.getAction());
    assertEquals("The verified APK must be passed as a content URI.",
        "content", intent.getData().getScheme());
    assertEquals("The installer must receive the package-archive MIME type.",
        "application/vnd.android.package-archive", intent.getType());
    assertTrue("The installer gets only a temporary read grant.",
        (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    assertEquals("The updater must not request uninstall, delete, or data-clearing behavior.",
        0, intent.getFlags() & (Intent.FLAG_ACTIVITY_CLEAR_TASK
          | Intent.FLAG_ACTIVITY_CLEAR_TOP));
  }

  @Test
  public void updater_state_writes_preserve_keyboard_user_data() throws Exception
  {
    HashSet<String> learned = new HashSet<String>(Arrays.asList("hello", "world"));
    _preferences.edit()
      .putString(SnippetStore.PREF_SLOTS, "private snippet payload")
      .putStringSet(PersonalizationStore.PREF_WORDS, learned)
      .putStringSet(PersonalizationStore.PREF_BIGRAMS,
          new HashSet<String>(Arrays.asList("hello world")))
      .putStringSet(PersonalizationStore.PREF_CORRECTIONS,
          new HashSet<String>(Arrays.asList("wprld\u0000world\u00004")))
      .putBoolean("clipboard_save_screenshots", true)
      .putString("unrelated_preference", "keep me")
      .commit();
    ReleaseUpdater.Release release = ReleaseUpdater.parseRelease(
        releaseJson("v2.0.27-vc78", 42L,
          "https://example.com/update.apk", DIGEST));

    ReleaseUpdater.recordAutomaticCheckAttempt(_preferences, 12345L);
    ReleaseUpdater.rejectRelease(_preferences, release);
    ReleaseUpdater.setTerminalStatus(_preferences, "available",
        release.versionName);

    assertEquals("Updater metadata must never rewrite snippets.",
        "private snippet payload",
        _preferences.getString(SnippetStore.PREF_SLOTS, null));
    assertEquals("Updater metadata must never rewrite learned words.", learned,
        _preferences.getStringSet(PersonalizationStore.PREF_WORDS, null));
    assertTrue("Updater metadata must never remove next-word learning.",
        _preferences.contains(PersonalizationStore.PREF_BIGRAMS));
    assertTrue("Updater metadata must never remove correction learning.",
        _preferences.contains(PersonalizationStore.PREF_CORRECTIONS));
    assertTrue("Updater metadata must preserve clipboard settings.",
        _preferences.getBoolean("clipboard_save_screenshots", false));
    assertEquals("Updater metadata must preserve unrelated keyboard preferences.",
        "keep me", _preferences.getString("unrelated_preference", null));
  }

  @Test
  public void trust_anchor_and_bounds_are_fixed_release_contracts()
  {
    assertEquals("The updater must pin FrankenKey's persistent release signer.",
        "9fdb36334eb40c87d174a2dca1f5efa26e7e7cf52b0f63aac2ac1d507d4376d9",
        ReleaseUpdater.TRUSTED_SIGNER_SHA256);
    assertEquals("The APK stream cap must remain exactly 32 MiB.",
        32L * 1024L * 1024L, ReleaseUpdater.MAX_APK_BYTES);
    assertEquals("Only the public unauthenticated GitHub latest-release API is allowed.",
        "https://api.github.com/repos/demetre19/FrankenKey/releases/latest",
        ReleaseUpdater.LATEST_RELEASE_URL);
  }

  private static String releaseJson(String tag, long size, String downloadUrl,
      String digest) throws Exception
  {
    return releaseJsonWithHtml(tag, size, downloadUrl, digest,
        "https://github.com/demetre19/FrankenKey/releases/tag/" + tag);
  }

  private static String releaseJsonWithHtml(String tag, long size,
      String downloadUrl, String digest, String htmlUrl) throws Exception
  {
    JSONObject asset = new JSONObject()
      .put("name", ReleaseUpdater.ASSET_NAME)
      .put("size", size)
      .put("browser_download_url", downloadUrl)
      .put("digest", digest);
    return new JSONObject()
      .put("tag_name", tag)
      .put("body", "Fixed important things.")
      .put("html_url", htmlUrl)
      .put("assets", new JSONArray().put(asset))
      .toString();
  }

  private static void assertRejected(String message, String json)
      throws Exception
  {
    try
    {
      ReleaseUpdater.parseRelease(json);
      fail(message);
    }
    catch (ReleaseUpdater.UpdateException expected) {}
  }
}
