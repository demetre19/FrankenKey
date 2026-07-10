package juloo.keyboard2;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class ReleaseUpdaterResourcesTest
{
  private static final String ANDROID_NS =
    "http://schemas.android.com/apk/res/android";

  @Test
  public void release_metadata_is_2_0_26_version_code_77() throws Exception
  {
    String gradle = read("build.gradle.kts");
    assertTrue("The updater baseline release must be versionName 2.0.26.",
        gradle.contains("versionName = \"2.0.26\""));
    assertTrue("The updater baseline release must be versionCode 77.",
        gradle.contains("versionCode = 77"));
  }

  @Test
  public void settings_expose_default_on_checks_delivery_status_and_manual_action()
      throws Exception
  {
    Document settings = parse("res/xml/settings.xml");
    Element automatic = preference(settings, "update_automatic_checks");
    Element delivery = preference(settings, "update_delivery");
    Element current = preference(settings, "update_current_version");
    Element status = preference(settings, "update_status");
    Element manual = preference(settings, "check_for_updates");

    assertNotNull("Settings must expose automatic update checks.", automatic);
    assertEquals("Automatic checks must use an explicit consent-preserving checkbox.",
        "CheckBoxPreference", automatic.getTagName());
    assertEquals("Automatic checks must default on.", "true",
        automatic.getAttributeNS(ANDROID_NS, "defaultValue"));
    assertEquals("Automatic-check copy must promise no unapproved download or install.",
        "@string/pref_update_automatic_checks_summary",
        automatic.getAttributeNS(ANDROID_NS, "summary"));

    assertNotNull("Settings must expose delivery choice.", delivery);
    assertEquals("Delivery mode must default to verified in-app delivery.", "in_app",
        delivery.getAttributeNS(ANDROID_NS, "defaultValue"));
    assertEquals(Arrays.asList("in_app", "github"),
        stringArray("res/values/arrays.xml", "pref_update_delivery_values"));

    assertNotNull("Settings must display the installed version.", current);
    assertEquals("Version display must not act like a button.", "false",
        current.getAttributeNS(ANDROID_NS, "selectable"));
    assertNotNull("Settings must display updater status.", status);
    assertEquals("Status display must not act like a button.", "false",
        status.getAttributeNS(ANDROID_NS, "selectable"));
    assertNotNull("Settings must expose a manual check action.", manual);
  }

  @Test
  public void manifest_provider_exposes_only_private_cache_updates_directory()
      throws Exception
  {
    Document manifest = parse("AndroidManifest.xml");
    Element provider = provider(manifest, "androidx.core.content.FileProvider");

    assertNotNull("The authenticated APK handoff requires AndroidX FileProvider.",
        provider);
    assertEquals("The update authority must remain app-specific and narrowly named.",
        "${applicationId}.updates",
        provider.getAttributeNS(ANDROID_NS, "authorities"));
    assertEquals("The update provider must never be exported.", "false",
        provider.getAttributeNS(ANDROID_NS, "exported"));
    assertEquals("Installer access must use temporary URI grants.", "true",
        provider.getAttributeNS(ANDROID_NS, "grantUriPermissions"));

    NodeList metadata = provider.getElementsByTagName("meta-data");
    assertEquals("FileProvider must have exactly one paths declaration.",
        1, metadata.getLength());
    assertEquals("FileProvider must reference only the updater paths XML.",
        "@xml/update_file_paths",
        ((Element)metadata.item(0)).getAttributeNS(ANDROID_NS, "resource"));

    Document paths = parse("res/xml/update_file_paths.xml");
    Element root = paths.getDocumentElement();
    NodeList children = root.getChildNodes();
    List<Element> pathElements = new ArrayList<Element>();
    for (int i = 0; i < children.getLength(); ++i)
      if (children.item(i) instanceof Element)
        pathElements.add((Element)children.item(i));
    assertEquals("No broad files, root, or external path may be shared.",
        1, pathElements.size());
    Element cache = pathElements.get(0);
    assertEquals("Only a cache-path is allowed.", "cache-path",
        cache.getTagName());
    assertEquals("The grant must be rooted at cache/updates only.", "updates/",
        cache.getAttribute("path"));
  }

  @Test
  public void manifest_requests_install_permission_without_exporting_an_installer()
      throws Exception
  {
    Document manifest = parse("AndroidManifest.xml");
    NodeList permissions = manifest.getElementsByTagName("uses-permission");
    boolean found = false;
    for (int i = 0; i < permissions.getLength(); ++i)
      found |= "android.permission.REQUEST_INSTALL_PACKAGES".equals(
          ((Element)permissions.item(i)).getAttributeNS(ANDROID_NS, "name"));
    assertTrue("Android 8+ in-app delivery must declare package-install request capability.",
        found);
  }

  @Test
  public void activity_wiring_checks_from_launcher_and_settings_and_continues_permission_flow()
      throws Exception
  {
    String launcher = read("srcs/juloo.keyboard2/LauncherActivity.java");
    String settings = read("srcs/juloo.keyboard2/SettingsActivity.java");
    String updater = read("srcs/juloo.keyboard2/ReleaseUpdater.java");

    assertTrue("LauncherActivity must guard automatic checks behind user-unlocked state.",
        launcher.contains("ReleaseUpdater.isUserUnlocked(this)")
        && launcher.contains("_releaseUpdater.checkAutomatically()"));
    assertTrue("Opening Settings directly must also trigger the default-on check through the shared throttle.",
        settings.contains("_releaseUpdater.checkAutomatically()"));
    assertTrue("Settings manual action must bypass automatic throttle through the manual API.",
        settings.contains("_releaseUpdater.checkManually()"));
    assertTrue("Both activities must forward pause/resume so unknown-sources permission can continue only after the user returns.",
        launcher.contains("_releaseUpdater.onPause()")
        && launcher.contains("_releaseUpdater.onResume()")
        && settings.contains("_releaseUpdater.onPause()")
        && settings.contains("_releaseUpdater.onResume()"));
    assertTrue("Unknown-sources routing must use the package-scoped Android settings page.",
        updater.contains("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES")
        && updater.contains("Uri.parse(\"package:\" + activity.getPackageName())"));
    assertTrue("Async results and verified pending installs must survive Activity recreation without retaining a destroyed Activity.",
        updater.contains("static WeakReference<ReleaseUpdater> ACTIVE_UPDATER")
        && updater.contains("static CheckResult PENDING_CHECK")
        && updater.contains("static DownloadResult PENDING_DOWNLOAD")
        && updater.contains("static File PENDING_INSTALL"));
    assertTrue("A resumed Activity must reattach before draining lifecycle-retained results.",
        updater.contains("ACTIVE_UPDATER = new WeakReference<ReleaseUpdater>(this);")
        && updater.indexOf("ACTIVE_UPDATER = new WeakReference<ReleaseUpdater>(this);",
            updater.indexOf("void onResume()"))
          < updater.indexOf("resumePendingState();",
            updater.indexOf("void onResume()")));
    assertTrue("Archive authentication must bind package, code, versionName and signer before installer handoff.",
        updater.contains("BuildConfig.APPLICATION_ID.equals(info.packageName)")
        && updater.contains("archiveVersion <= BuildConfig.VERSION_CODE")
        && updater.contains("release.versionName.equals(info.versionName)")
        && updater.contains("TRUSTED_SIGNER_SHA256.equals(signer)"));
  }

  private static Document parse(String path) throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder().parse(new File(path));
  }

  private static String read(String path) throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)),
        StandardCharsets.UTF_8);
  }

  private static Element preference(Document document, String key)
  {
    NodeList all = document.getElementsByTagName("*");
    for (int i = 0; i < all.getLength(); ++i)
    {
      Element element = (Element)all.item(i);
      if (key.equals(element.getAttributeNS(ANDROID_NS, "key")))
        return element;
    }
    return null;
  }

  private static Element provider(Document document, String name)
  {
    NodeList providers = document.getElementsByTagName("provider");
    for (int i = 0; i < providers.getLength(); ++i)
    {
      Element provider = (Element)providers.item(i);
      if (name.equals(provider.getAttributeNS(ANDROID_NS, "name")))
        return provider;
    }
    return null;
  }

  private static List<String> stringArray(String path, String name)
      throws Exception
  {
    NodeList arrays = parse(path).getElementsByTagName("string-array");
    for (int i = 0; i < arrays.getLength(); ++i)
    {
      Element array = (Element)arrays.item(i);
      if (!name.equals(array.getAttribute("name")))
        continue;
      List<String> values = new ArrayList<String>();
      NodeList items = array.getElementsByTagName("item");
      for (int j = 0; j < items.getLength(); ++j)
        values.add(items.item(j).getTextContent());
      return values;
    }
    fail("Missing string-array: " + name);
    return null;
  }
}
