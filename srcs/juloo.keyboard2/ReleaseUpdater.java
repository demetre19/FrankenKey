package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ref.WeakReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Consent-based updater for official FrankenKey GitHub releases. */
final class ReleaseUpdater
{
  static final String LATEST_RELEASE_URL =
    "https://api.github.com/repos/demetre19/FrankenKey/releases/latest";
  static final String ASSET_NAME = "FrankenKey-installable-release.apk";
  static final long MAX_APK_BYTES = 32L * 1024L * 1024L;
  static final long AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
  static final String TRUSTED_SIGNER_SHA256 =
    "9fdb36334eb40c87d174a2dca1f5efa26e7e7cf52b0f63aac2ac1d507d4376d9";

  static final String PREF_AUTOMATIC_CHECKS = "update_automatic_checks";
  static final String PREF_DELIVERY = "update_delivery";
  static final String DELIVERY_IN_APP = "in_app";
  static final String DELIVERY_GITHUB = "github";
  static final String PREF_LAST_AUTOMATIC_CHECK =
    "update_last_automatic_check_ms";
  static final String PREF_REJECTED_VERSION_CODE =
    "update_rejected_version_code";
  static final String PREF_LAST_STATUS = "update_last_status";
  static final String PREF_LAST_STATUS_VERSION = "update_last_status_version";

  private static final String STATUS_NEVER = "never";
  private static final String STATUS_UP_TO_DATE = "up_to_date";
  private static final String STATUS_AVAILABLE = "available";
  private static final String STATUS_REJECTED = "rejected";
  private static final String STATUS_FAILED = "failed";
  private static final int MAX_RELEASE_BYTES = 1024 * 1024;
  private static final int MAX_REDIRECTS = 5;
  private static final int CONNECT_TIMEOUT_MS = 10000;
  private static final int READ_TIMEOUT_MS = 20000;
  private static final int REQUEST_UNKNOWN_APP_SOURCES = 77;
  private static final String APK_MIME =
    "application/vnd.android.package-archive";
  private static final String UPDATE_DIRECTORY = "updates";
  private static final String UPDATE_FILENAME = ASSET_NAME;
  private static final String PARTIAL_FILENAME = ASSET_NAME + ".part";
  private static final AtomicBoolean OPERATION_IN_FLIGHT =
    new AtomicBoolean(false);
  private static final Object STATE_LOCK = new Object();
  private static WeakReference<ReleaseUpdater> ACTIVE_UPDATER =
    new WeakReference<ReleaseUpdater>(null);
  private static CheckResult PENDING_CHECK;
  private static DownloadResult PENDING_DOWNLOAD;
  private static Release PENDING_CONSENT;
  private static WeakReference<ReleaseUpdater> CONSENT_OWNER =
    new WeakReference<ReleaseUpdater>(null);
  private static File PENDING_INSTALL;
  private static boolean WAITING_FOR_INSTALL_PERMISSION;
  private static boolean LEFT_FOR_INSTALL_PERMISSION;
  private static final ExecutorService EXECUTOR =
    Executors.newSingleThreadExecutor(new ThreadFactory()
      {
        @Override
        public Thread newThread(Runnable runnable)
        {
          Thread thread = new Thread(runnable, "FrankenKey updater");
          thread.setDaemon(true);
          return thread;
        }
      });

  interface StatusListener
  {
    void onUpdateStatusChanged();
  }

  enum AcceptedAction
  {
    NONE,
    DOWNLOAD,
    OPEN_GITHUB
  }

  static final class Release
  {
    final String versionName;
    final int versionCode;
    final String changelog;
    final String htmlUrl;
    final String downloadUrl;
    final long assetSize;
    final String sha256;

    Release(String versionName, int versionCode, String changelog,
        String htmlUrl, String downloadUrl, long assetSize, String sha256)
    {
      this.versionName = versionName;
      this.versionCode = versionCode;
      this.changelog = changelog;
      this.htmlUrl = htmlUrl;
      this.downloadUrl = downloadUrl;
      this.assetSize = assetSize;
      this.sha256 = sha256;
    }
  }

  static final class UpdateException extends Exception
  {
    UpdateException(String message)
    {
      super(message);
    }

    UpdateException(String message, Throwable cause)
    {
      super(message, cause);
    }
  }
  private static final class CheckResult
  {
    final Release release;
    final Exception failure;
    final boolean manual;

    CheckResult(Release release, Exception failure, boolean manual)
    {
      this.release = release;
      this.failure = failure;
      this.manual = manual;
    }
  }

  private static final class DownloadResult
  {
    final File verified;
    final Exception failure;

    DownloadResult(File verified, Exception failure)
    {
      this.verified = verified;
      this.failure = failure;
    }
  }


  private final WeakReference<Activity> _activity;
  private final SharedPreferences _preferences;
  private final StatusListener _statusListener;
  private final Handler _mainHandler;
  private volatile boolean _destroyed;
  private boolean _resumed;
  private String _transientStatus;
  private AlertDialog _consentDialog;

  ReleaseUpdater(Activity activity, SharedPreferences preferences,
      StatusListener statusListener)
  {
    _activity = new WeakReference<Activity>(activity);
    _preferences = preferences;
    _statusListener = statusListener;
    _mainHandler = new Handler(Looper.getMainLooper());
    synchronized (STATE_LOCK)
    {
      ACTIVE_UPDATER = new WeakReference<ReleaseUpdater>(this);
    }
  }

  void checkAutomatically()
  {
    if (!isAutomaticCheckDue(_preferences, System.currentTimeMillis()))
      return;
    startCheck(false);
  }

  void checkManually()
  {
    startCheck(true);
  }

  void onPause()
  {
    _resumed = false;
    synchronized (STATE_LOCK)
    {
      if (WAITING_FOR_INSTALL_PERMISSION)
        LEFT_FOR_INSTALL_PERMISSION = true;
    }
  }

  void onResume()
  {
    _resumed = true;
    synchronized (STATE_LOCK)
    {
      ACTIVE_UPDATER = new WeakReference<ReleaseUpdater>(this);
    }
    resumePendingState();
    Activity activity = usableActivity();
    if (activity == null)
      return;
    File pending;
    synchronized (STATE_LOCK)
    {
      if (!WAITING_FOR_INSTALL_PERMISSION || !LEFT_FOR_INSTALL_PERMISSION)
        return;
      LEFT_FOR_INSTALL_PERMISSION = false;
      if (Build.VERSION.SDK_INT >= 26
          && !activity.getPackageManager().canRequestPackageInstalls())
      {
        WAITING_FOR_INSTALL_PERMISSION = false;
        PENDING_INSTALL = null;
        setTransientStatus(null);
        Toast.makeText(activity, R.string.update_install_permission_denied,
            Toast.LENGTH_LONG).show();
        return;
      }
      pending = PENDING_INSTALL;
      WAITING_FOR_INSTALL_PERMISSION = false;
      PENDING_INSTALL = null;
    }
    if (pending != null)
      launchInstaller(activity, pending);
  }

  void destroy()
  {
    _destroyed = true;
    if (_consentDialog != null)
      _consentDialog.dismiss();
    synchronized (STATE_LOCK)
    {
      if (ACTIVE_UPDATER.get() == this)
        ACTIVE_UPDATER.clear();
      if (CONSENT_OWNER.get() == this)
        CONSENT_OWNER.clear();
    }
    _activity.clear();
  }

  CharSequence statusSummary()
  {
    Activity activity = usableActivity();
    if (activity == null)
      return "";
    if (_transientStatus != null)
      return _transientStatus;
    String status = _preferences.getString(PREF_LAST_STATUS, STATUS_NEVER);
    String version = _preferences.getString(PREF_LAST_STATUS_VERSION, "");
    if (STATUS_UP_TO_DATE.equals(status))
      return activity.getString(R.string.update_status_up_to_date);
    if (STATUS_AVAILABLE.equals(status))
      return activity.getString(R.string.update_status_available, version);
    if (STATUS_REJECTED.equals(status))
      return activity.getString(R.string.update_status_rejected, version);
    if (STATUS_FAILED.equals(status))
      return activity.getString(R.string.update_status_failed);
    return activity.getString(R.string.update_status_never_checked);
  }

  private void startCheck(final boolean manual)
  {
    final Activity activity = usableActivity();
    if (activity == null || !isUserUnlocked(activity))
      return;
    synchronized (STATE_LOCK)
    {
      if (PENDING_CONSENT != null)
      {
        if (manual)
          Toast.makeText(activity, R.string.update_status_busy,
              Toast.LENGTH_SHORT).show();
        return;
      }
    }
    if (!OPERATION_IN_FLIGHT.compareAndSet(false, true))
    {
      if (manual)
        Toast.makeText(activity, R.string.update_status_busy,
            Toast.LENGTH_SHORT).show();
      return;
    }
    if (!manual)
      recordAutomaticCheckAttempt(_preferences, System.currentTimeMillis());
    setTransientStatus(activity.getString(R.string.update_status_checking));
    EXECUTOR.execute(() -> {
      Release release = null;
      Exception failure = null;
      try
      {
        release = fetchLatestRelease();
      }
      catch (Exception e)
      {
        failure = e;
      }
      OPERATION_IN_FLIGHT.set(false);
      deliverCheckResult(new CheckResult(release, failure, manual));
    });
  }

  private void finishCheck(Release release, Exception failure, boolean manual)
  {
    if (failure != null)
    {
      setTransientStatus(null);
      if (manual)
      {
        setTerminalStatus(_preferences, STATUS_FAILED, "");
        notifyStatusChanged();
        Activity activity = usableActivity();
        if (activity != null)
          Toast.makeText(activity, R.string.update_check_failed,
              Toast.LENGTH_LONG).show();
      }
      return;
    }

    if (!isNewer(release, BuildConfig.VERSION_CODE))
    {
      setTerminalStatus(_preferences, STATUS_UP_TO_DATE, "");
      setTransientStatus(null);
      if (manual)
      {
        Activity activity = usableActivity();
        if (activity != null)
          Toast.makeText(activity, R.string.update_status_up_to_date,
              Toast.LENGTH_SHORT).show();
      }
      return;
    }

    if (!shouldAnnounce(_preferences, release, BuildConfig.VERSION_CODE,
          manual))
    {
      setTerminalStatus(_preferences, STATUS_REJECTED, release.versionName);
      setTransientStatus(null);
      return;
    }

    setTerminalStatus(_preferences, STATUS_AVAILABLE, release.versionName);
    setTransientStatus(null);
    showConsentDialog(release);
  }

  private void showConsentDialog(final Release release)
  {
    final Activity activity = usableActivity();
    if (activity == null)
      return;
    synchronized (STATE_LOCK)
    {
      PENDING_CONSENT = release;
      ReleaseUpdater owner = CONSENT_OWNER.get();
      if (owner != null && owner != this && owner.usableActivity() != null)
        return;
      if (owner == this && _consentDialog != null
          && _consentDialog.isShowing())
        return;
      CONSENT_OWNER = new WeakReference<ReleaseUpdater>(this);
    }
    int positive = DELIVERY_GITHUB.equals(deliveryMode(_preferences))
      ? R.string.update_open_github
      : R.string.update_download_install;
    String changelog = release.changelog.length() == 0
      ? activity.getString(R.string.update_no_changelog)
      : release.changelog;
    _consentDialog = new AlertDialog.Builder(activity)
      .setTitle(activity.getString(R.string.update_available_title,
            release.versionName, release.versionCode))
      .setMessage(changelog)
      .setNegativeButton(R.string.update_not_now,
          (_dialog, _which) -> {
            clearPendingConsent();
            rejectRelease(_preferences, release);
            setTerminalStatus(_preferences, STATUS_REJECTED,
                release.versionName);
            notifyStatusChanged();
          })
      .setPositiveButton(positive,
          (_dialog, _which) -> {
            clearPendingConsent();
            performAcceptedAction(release,
                acceptedAction(true, deliveryMode(_preferences)));
          })
      .create();
    _consentDialog.setOnDismissListener(_dialog -> {
      synchronized (STATE_LOCK)
      {
        if (CONSENT_OWNER.get() == this)
          CONSENT_OWNER.clear();
      }
      _consentDialog = null;
    });
    _consentDialog.setCanceledOnTouchOutside(false);
    _consentDialog.setCancelable(false);
    _consentDialog.show();
  }

  private void performAcceptedAction(Release release, AcceptedAction action)
  {
    if (action == AcceptedAction.OPEN_GITHUB)
    {
      openGithubRelease(release);
      return;
    }
    if (action == AcceptedAction.DOWNLOAD)
      startDownload(release);
  }

  private void openGithubRelease(Release release)
  {
    Activity activity = usableActivity();
    if (activity == null)
      return;
    try
    {
      activity.startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(release.htmlUrl)));
    }
    catch (Exception e)
    {
      setTerminalStatus(_preferences, STATUS_FAILED, "");
      notifyStatusChanged();
      Toast.makeText(activity, R.string.update_open_failed,
          Toast.LENGTH_LONG).show();
    }
  }

  private void startDownload(final Release release)
  {
    final Activity activity = usableActivity();
    if (activity == null || !isUserUnlocked(activity))
      return;
    if (!OPERATION_IN_FLIGHT.compareAndSet(false, true))
    {
      Toast.makeText(activity, R.string.update_status_busy,
          Toast.LENGTH_SHORT).show();
      return;
    }
    setTransientStatus(activity.getString(R.string.update_status_downloading));
    final Context appContext = activity.getApplicationContext();
    EXECUTOR.execute(() -> {
      File verified = null;
      Exception failure = null;
      try
      {
        verified = downloadAndVerify(appContext, release);
      }
      catch (Exception e)
      {
        failure = e;
      }
      OPERATION_IN_FLIGHT.set(false);
      deliverDownloadResult(new DownloadResult(verified, failure));
    });
  }

  private void finishDownload(File verified, Exception failure)
  {
    if (failure != null || verified == null)
    {
      setTransientStatus(null);
      setTerminalStatus(_preferences, STATUS_FAILED, "");
      notifyStatusChanged();
      Activity activity = usableActivity();
      if (activity != null)
        Toast.makeText(activity, R.string.update_download_failed,
            Toast.LENGTH_LONG).show();
      return;
    }
    setTransientStatus(null);
    requestInstaller(verified);
  }

  private void requestInstaller(File verified)
  {
    Activity activity = usableActivity();
    if (activity == null)
      return;
    if (Build.VERSION.SDK_INT >= 26
        && !activity.getPackageManager().canRequestPackageInstalls())
    {
      synchronized (STATE_LOCK)
      {
        PENDING_INSTALL = verified;
        WAITING_FOR_INSTALL_PERMISSION = true;
        LEFT_FOR_INSTALL_PERMISSION = false;
      }
      setTransientStatus(activity.getString(
            R.string.update_status_install_permission));
      Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          Uri.parse("package:" + activity.getPackageName()));
      try
      {
        activity.startActivityForResult(permission,
            REQUEST_UNKNOWN_APP_SOURCES);
      }
      catch (Exception e)
      {
        synchronized (STATE_LOCK)
        {
          PENDING_INSTALL = null;
          WAITING_FOR_INSTALL_PERMISSION = false;
        }
        setTransientStatus(null);
        setTerminalStatus(_preferences, STATUS_FAILED, "");
        notifyStatusChanged();
        Toast.makeText(activity, R.string.update_install_permission_failed,
            Toast.LENGTH_LONG).show();
      }
      return;
    }
    launchInstaller(activity, verified);
  }

  private void launchInstaller(Activity activity, File verified)
  {
    try
    {
      Uri uri = FileProvider.getUriForFile(activity,
          activity.getPackageName() + ".updates", verified);
      Intent intent = installerIntent(uri);
      intent.setClipData(ClipData.newRawUri("FrankenKey update", uri));
      activity.startActivity(intent);
      setTransientStatus(activity.getString(R.string.update_status_installer));
    }
    catch (Exception e)
    {
      setTransientStatus(null);
      setTerminalStatus(_preferences, STATUS_FAILED, "");
      notifyStatusChanged();
      Toast.makeText(activity, R.string.update_install_failed,
          Toast.LENGTH_LONG).show();
    }
  }

  private void setTransientStatus(String status)
  {
    _transientStatus = status;
    notifyStatusChanged();
  }

  private void notifyStatusChanged()
  {
    if (_statusListener != null && usableActivity() != null)
      _statusListener.onUpdateStatusChanged();
  }

  private void deliverCheckResult(CheckResult result)
  {
    _mainHandler.post(() -> {
      ReleaseUpdater target;
      synchronized (STATE_LOCK)
      {
        target = ACTIVE_UPDATER.get();
        if (target == null || target.resumedActivity() == null)
        {
          PENDING_CHECK = result;
          return;
        }
      }
      target.finishCheck(result.release, result.failure, result.manual);
    });
  }

  private void deliverDownloadResult(DownloadResult result)
  {
    _mainHandler.post(() -> {
      ReleaseUpdater target;
      synchronized (STATE_LOCK)
      {
        target = ACTIVE_UPDATER.get();
        if (target == null || target.resumedActivity() == null)
        {
          PENDING_DOWNLOAD = result;
          return;
        }
      }
      target.finishDownload(result.verified, result.failure);
    });
  }

  private void resumePendingState()
  {
    if (resumedActivity() == null)
      return;
    CheckResult check = null;
    DownloadResult download = null;
    Release consent = null;
    synchronized (STATE_LOCK)
    {
      if (ACTIVE_UPDATER.get() != this)
        return;
      if (PENDING_CHECK != null)
      {
        check = PENDING_CHECK;
        PENDING_CHECK = null;
      }
      else if (PENDING_DOWNLOAD != null)
      {
        download = PENDING_DOWNLOAD;
        PENDING_DOWNLOAD = null;
      }
      else
        consent = PENDING_CONSENT;
    }
    if (check != null)
    {
      finishCheck(check.release, check.failure, check.manual);
      return;
    }
    if (download != null)
    {
      finishDownload(download.verified, download.failure);
      return;
    }
    if (consent != null)
      showConsentDialog(consent);
  }

  private void clearPendingConsent()
  {
    synchronized (STATE_LOCK)
    {
      PENDING_CONSENT = null;
      if (CONSENT_OWNER.get() == this)
        CONSENT_OWNER.clear();
    }
  }

  private Activity usableActivity()
  {
    if (_destroyed)
      return null;
    Activity activity = _activity.get();
    if (activity == null || activity.isFinishing())
      return null;
    if (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())
      return null;
    return activity;
  }

  private Activity resumedActivity()
  {
    if (!_resumed)
      return null;
    return usableActivity();
  }

  static boolean isUserUnlocked(Context context)
  {
    if (Build.VERSION.SDK_INT < 24)
      return true;
    UserManager manager = (UserManager)context.getSystemService(
        Context.USER_SERVICE);
    return manager != null && manager.isUserUnlocked();
  }

  static boolean isAutomaticCheckDue(SharedPreferences preferences, long now)
  {
    if (!preferences.getBoolean(PREF_AUTOMATIC_CHECKS, true))
      return false;
    long last = preferences.getLong(PREF_LAST_AUTOMATIC_CHECK, 0L);
    return last <= 0L || (now >= last
        && now - last >= AUTO_CHECK_INTERVAL_MS);
  }

  static boolean isNewer(Release release, int installedVersionCode)
  {
    return release.versionCode > installedVersionCode;
  }

  static boolean shouldAnnounce(SharedPreferences preferences, Release release,
      int installedVersionCode, boolean manual)
  {
    if (!isNewer(release, installedVersionCode))
      return false;
    return manual || preferences.getInt(PREF_REJECTED_VERSION_CODE, -1)
      != release.versionCode;
  }

  static String deliveryMode(SharedPreferences preferences)
  {
    String mode = preferences.getString(PREF_DELIVERY, DELIVERY_IN_APP);
    return DELIVERY_GITHUB.equals(mode) ? DELIVERY_GITHUB : DELIVERY_IN_APP;
  }

  static AcceptedAction acceptedAction(boolean accepted, String deliveryMode)
  {
    if (!accepted)
      return AcceptedAction.NONE;
    return DELIVERY_GITHUB.equals(deliveryMode)
      ? AcceptedAction.OPEN_GITHUB
      : AcceptedAction.DOWNLOAD;
  }

  static void recordAutomaticCheckAttempt(SharedPreferences preferences,
      long now)
  {
    preferences.edit().putLong(PREF_LAST_AUTOMATIC_CHECK, now).apply();
  }

  static void rejectRelease(SharedPreferences preferences, Release release)
  {
    preferences.edit()
      .putInt(PREF_REJECTED_VERSION_CODE, release.versionCode)
      .apply();
  }

  static void setTerminalStatus(SharedPreferences preferences, String status,
      String versionName)
  {
    preferences.edit()
      .putString(PREF_LAST_STATUS, status)
      .putString(PREF_LAST_STATUS_VERSION, versionName)
      .apply();
  }

  static Intent installerIntent(Uri uri)
  {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, APK_MIME);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return intent;
  }

  static Release parseRelease(String json) throws UpdateException
  {
    try
    {
      JSONObject root = new JSONObject(json);
      String tag = requiredString(root, "tag_name");
      java.util.regex.Matcher tagMatch = java.util.regex.Pattern
        .compile("^v([A-Za-z0-9][A-Za-z0-9._+~-]{0,63})-vc([1-9][0-9]*)$")
        .matcher(tag);
      if (!tagMatch.matches())
        throw new UpdateException("Invalid release tag");
      int versionCode;
      try
      {
        versionCode = Integer.parseInt(tagMatch.group(2));
      }
      catch (NumberFormatException e)
      {
        throw new UpdateException("Invalid version code", e);
      }
      String versionName = tagMatch.group(1);
      String htmlUrl = requiredString(root, "html_url");
      validateOfficialReleaseUrl(htmlUrl, tag);
      String changelog = root.optString("body", "");
      if (changelog.length() > 200000)
        throw new UpdateException("Changelog is too large");

      JSONArray assets = root.getJSONArray("assets");
      JSONObject selected = null;
      for (int i = 0; i < assets.length(); ++i)
      {
        JSONObject asset = assets.getJSONObject(i);
        if (!ASSET_NAME.equals(asset.optString("name", "")))
          continue;
        if (selected != null)
          throw new UpdateException("Duplicate update asset");
        selected = asset;
      }
      if (selected == null)
        throw new UpdateException("Missing update asset");

      long size = selected.getLong("size");
      if (size <= 0L || size > MAX_APK_BYTES)
        throw new UpdateException("Invalid update size");
      String downloadUrl = requiredString(selected, "browser_download_url");
      validateHttpsUrl(downloadUrl);
      String digest = requiredString(selected, "digest");
      if (!digest.matches("sha256:[0-9a-f]{64}"))
        throw new UpdateException("Invalid update digest");
      return new Release(versionName, versionCode, changelog, htmlUrl,
          downloadUrl, size, digest.substring("sha256:".length()));
    }
    catch (JSONException e)
    {
      throw new UpdateException("Invalid release response", e);
    }
  }

  private static String requiredString(JSONObject object, String key)
      throws JSONException, UpdateException
  {
    String value = object.getString(key);
    if (value.length() == 0 || value.length() > 4096)
      throw new UpdateException("Invalid " + key);
    return value;
  }

  private static void validateOfficialReleaseUrl(String value, String tag)
      throws UpdateException
  {
    validateHttpsUrl(value);
    try
    {
      URI uri = new URI(value);
      String expected = "/demetre19/FrankenKey/releases/tag/" + tag;
      if (!"github.com".equalsIgnoreCase(uri.getHost())
          || !expected.equals(uri.getRawPath())
          || uri.getRawUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null)
        throw new UpdateException("Invalid release page");
    }
    catch (java.net.URISyntaxException e)
    {
      throw new UpdateException("Invalid release page", e);
    }
  }

  private static void validateHttpsUrl(String value) throws UpdateException
  {
    try
    {
      URI uri = new URI(value);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null || uri.getHost().length() == 0
          || uri.getRawUserInfo() != null)
        throw new UpdateException("HTTPS URL required");
    }
    catch (java.net.URISyntaxException e)
    {
      throw new UpdateException("Invalid URL", e);
    }
  }

  private static Release fetchLatestRelease() throws Exception
  {
    HttpURLConnection connection = openHttpsConnection(
        new URL(LATEST_RELEASE_URL), "application/vnd.github+json");
    try
    {
      byte[] bytes = readBounded(connection.getInputStream(),
          MAX_RELEASE_BYTES);
      return parseRelease(new String(bytes, StandardCharsets.UTF_8));
    }
    finally
    {
      connection.disconnect();
    }
  }

  private static File downloadAndVerify(Context context, Release release)
      throws Exception
  {
    File directory = new File(context.getCacheDir(), UPDATE_DIRECTORY);
    if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory())
      throw new UpdateException("Unable to create update directory");
    File partial = new File(directory, PARTIAL_FILENAME);
    File verified = new File(directory, UPDATE_FILENAME);
    deleteQuietly(partial);
    deleteQuietly(verified);

    HttpURLConnection connection = openHttpsConnection(
        new URL(release.downloadUrl), APK_MIME);
    try
    {
      long contentLength = connection.getContentLength();
      if (contentLength >= 0L && contentLength != release.assetSize)
        throw new UpdateException("Download size does not match release");
      MessageDigest digest = sha256Digest();
      long count = 0L;
      try (InputStream input = connection.getInputStream();
           FileOutputStream output = new FileOutputStream(partial))
      {
        byte[] buffer = new byte[32768];
        int read;
        while ((read = input.read(buffer)) != -1)
        {
          count += read;
          if (count > release.assetSize || count > MAX_APK_BYTES)
            throw new UpdateException("Update exceeds declared size");
          output.write(buffer, 0, read);
          digest.update(buffer, 0, read);
        }
        output.getFD().sync();
      }
      if (count != release.assetSize)
        throw new UpdateException("Incomplete update download");
      if (!release.sha256.equals(toHex(digest.digest())))
        throw new UpdateException("Update digest mismatch");
      verifyArchive(context, partial, release);
      if (!partial.renameTo(verified))
        throw new UpdateException("Unable to finalize update");
      return verified;
    }
    catch (Exception e)
    {
      deleteQuietly(partial);
      deleteQuietly(verified);
      throw e;
    }
    finally
    {
      connection.disconnect();
    }
  }

  private static void verifyArchive(Context context, File archive,
      Release release) throws UpdateException
  {
    PackageManager manager = context.getPackageManager();
    int flags = Build.VERSION.SDK_INT >= 28
      ? PackageManager.GET_SIGNING_CERTIFICATES
      : PackageManager.GET_SIGNATURES;
    PackageInfo info = manager.getPackageArchiveInfo(archive.getAbsolutePath(),
        flags);
    if (info == null)
      throw new UpdateException("Invalid APK archive");
    if (!BuildConfig.APPLICATION_ID.equals(info.packageName))
      throw new UpdateException("Unexpected APK package");
    long archiveVersion = Build.VERSION.SDK_INT >= 28
      ? info.getLongVersionCode()
      : info.versionCode;
    if (archiveVersion <= BuildConfig.VERSION_CODE
        || archiveVersion != release.versionCode)
      throw new UpdateException("Unexpected APK version");
    if (info.versionName == null
        || !release.versionName.equals(info.versionName))
      throw new UpdateException("Unexpected APK version name");

    Signature[] signatures;
    if (Build.VERSION.SDK_INT >= 28)
    {
      if (info.signingInfo == null)
        throw new UpdateException("Missing APK signer");
      signatures = info.signingInfo.getApkContentsSigners();
    }
    else
      signatures = info.signatures;
    if (signatures == null || signatures.length != 1)
      throw new UpdateException("Unexpected APK signer count");
    String signer = toHex(sha256Digest().digest(signatures[0].toByteArray()));
    if (!TRUSTED_SIGNER_SHA256.equals(signer))
      throw new UpdateException("Untrusted APK signer");
  }

  private static HttpURLConnection openHttpsConnection(URL initial,
      String accept) throws Exception
  {
    URL current = initial;
    for (int redirects = 0; redirects <= MAX_REDIRECTS; ++redirects)
    {
      if (!"https".equalsIgnoreCase(current.getProtocol()))
        throw new UpdateException("HTTPS connection required");
      HttpURLConnection connection = (HttpURLConnection)current.openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setUseCaches(false);
      connection.setRequestProperty("Accept", accept);
      connection.setRequestProperty("User-Agent",
          "FrankenKey/" + BuildConfig.VERSION_NAME);
      if (LATEST_RELEASE_URL.equals(current.toString()))
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
      int response = connection.getResponseCode();
      if (response == HttpURLConnection.HTTP_OK)
        return connection;
      if (response != HttpURLConnection.HTTP_MOVED_PERM
          && response != HttpURLConnection.HTTP_MOVED_TEMP
          && response != HttpURLConnection.HTTP_SEE_OTHER
          && response != 307 && response != 308)
      {
        connection.disconnect();
        throw new UpdateException("Unexpected HTTP response");
      }
      String location = connection.getHeaderField("Location");
      connection.disconnect();
      if (location == null || redirects == MAX_REDIRECTS)
        throw new UpdateException("Invalid download redirect");
      current = new URL(current, location);
    }
    throw new UpdateException("Too many redirects");
  }

  private static byte[] readBounded(InputStream input, int maximum)
      throws IOException, UpdateException
  {
    try (InputStream stream = input;
         ByteArrayOutputStream output = new ByteArrayOutputStream())
    {
      byte[] buffer = new byte[8192];
      int count = 0;
      int read;
      while ((read = stream.read(buffer)) != -1)
      {
        count += read;
        if (count > maximum)
          throw new UpdateException("Response exceeds limit");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static MessageDigest sha256Digest() throws UpdateException
  {
    try
    {
      return MessageDigest.getInstance("SHA-256");
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new UpdateException("SHA-256 is unavailable", e);
    }
  }

  private static String toHex(byte[] bytes)
  {
    final char[] digits = "0123456789abcdef".toCharArray();
    char[] value = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; ++i)
    {
      int item = bytes[i] & 0xff;
      value[i * 2] = digits[item >>> 4];
      value[i * 2 + 1] = digits[item & 0x0f];
    }
    return new String(value);
  }

  private static void deleteQuietly(File file)
  {
    if (file.exists())
      file.delete();
  }
}
