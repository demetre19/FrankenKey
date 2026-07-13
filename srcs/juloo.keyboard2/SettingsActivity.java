package juloo.keyboard2;

import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.TwoStatePreference;
import android.preference.PreferenceActivity;
import android.content.SharedPreferences;
import android.preference.PreferenceCategory;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import juloo.keyboard2.suggestions.PersonalizationStore;

public class SettingsActivity extends PreferenceActivity
{
  private static final int REQUEST_SCREENSHOT_MEDIA_PERMISSION = 54;
  private static final int REQUEST_EXPORT_SETTINGS_BACKUP = 55;
  private static final int REQUEST_IMPORT_SETTINGS_BACKUP = 56;
  private static final int REQUEST_RECORD_AUDIO_PERMISSION = 57;
  private static final String SETTINGS_BACKUP_FILENAME =
    "frankenkey-settings-backup.json";
  static final String EXTRA_REQUEST_SCREENSHOT_PERMISSION =
    "juloo.keyboard2.REQUEST_SCREENSHOT_PERMISSION";
  static final String EXTRA_REQUEST_VOICE_PERMISSION =
    "juloo.keyboard2.REQUEST_VOICE_PERMISSION";
  private ReleaseUpdater _releaseUpdater;
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    // The preferences can't be read when in direct-boot mode. Avoid crashing
    // and don't allow changing the settings.
    // Run the config migration on this prefs as it might be different from the
    // one used by the keyboard, which have been migrated.
    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);
    setupUpdaterPreferences();
    Preference dashboard = findPreference("giphy_api_dashboard");
    if (dashboard != null)
      dashboard.setIntent(new Intent(Intent.ACTION_VIEW,
            Uri.parse(GiphyClient.DASHBOARD_URL)));
    setupTypingAssistancePreferences();
    setupVoiceTypingPreference();
    setupClipboardPreferences();
    setupBackupPreferences();
    requestScreenshotPermissionFromIntent();
    requestVoicePermissionFromIntent();

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    findPreference("margin_bottom_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("margin_bottom_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_landscape_unfolded").setEnabled(foldableDevice);
    styleSettingsList();
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    if (_releaseUpdater != null)
      _releaseUpdater.onResume();
    refreshUpdateStatus();
    getListView().post(() -> styleSettingsList());
  }

  @Override
  protected void onPause()
  {
    if (_releaseUpdater != null)
      _releaseUpdater.onPause();
    super.onPause();
  }

  @Override
  protected void onDestroy()
  {
    if (_releaseUpdater != null)
      _releaseUpdater.destroy();
    super.onDestroy();
  }


  void fallbackEncrypted()
  {
    // Can't communicate with the user here.
    finish();
  }

  protected void onStop()
  {
    DirectBootAwarePreferences
      .copy_preferences_to_protected_storage(this,
          getPreferenceManager().getSharedPreferences());
    super.onStop();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults)
  {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION)
    {
      boolean granted = grantResults.length > 0
        && grantResults[0] == PackageManager.PERMISSION_GRANTED;
      setMultimodalVoiceTypingEnabled(granted);
      if (!granted)
        Toast.makeText(this, R.string.voice_typing_permission_denied,
            Toast.LENGTH_SHORT).show();
      return;
    }
    if (requestCode != REQUEST_SCREENSHOT_MEDIA_PERMISSION)
      return;
    if (ClipboardHistoryService.hasScreenshotReadPermission(this))
    {
      ClipboardHistoryService.refresh_screenshot_observer();
      return;
    }
    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
    prefs.edit().putBoolean("clipboard_save_screenshots", false).apply();
    Preference pref = findPreference("clipboard_save_screenshots");
    if (pref instanceof TwoStatePreference)
      ((TwoStatePreference)pref).setChecked(false);
    Toast.makeText(this, R.string.pref_clipboard_screenshots_permission_denied,
        Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != RESULT_OK || data == null || data.getData() == null)
      return;
    if (requestCode == REQUEST_EXPORT_SETTINGS_BACKUP)
    {
      exportSettingsBackup(data.getData());
      return;
    }
    if (requestCode == REQUEST_IMPORT_SETTINGS_BACKUP)
      importSettingsBackup(data.getData());
  }

  private void setupUpdaterPreferences()
  {
    SharedPreferences preferences =
      getPreferenceManager().getSharedPreferences();
    _releaseUpdater = new ReleaseUpdater(this, preferences,
        () -> refreshUpdateStatus());
    Preference current = findPreference("update_current_version");
    if (current != null)
      current.setSummary(getString(R.string.pref_update_current_version_summary,
            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
    refreshUpdateStatus();
    _releaseUpdater.checkAutomatically();
    Preference manual = findPreference("check_for_updates");
    if (manual != null)
      manual.setOnPreferenceClickListener(preference -> {
        _releaseUpdater.checkManually();
        return true;
      });
  }

  private void refreshUpdateStatus()
  {
    Preference status = findPreference("update_status");
    if (status != null && _releaseUpdater != null)
      status.setSummary(_releaseUpdater.statusSummary());
  }

  private void setupBackupPreferences()
  {
    Preference export = findPreference("export_settings_backup");
    if (export != null)
      export.setOnPreferenceClickListener(preference -> {
        startExportSettingsBackup();
        return true;
      });
    Preference import_ = findPreference("import_settings_backup");
    if (import_ != null)
      import_.setOnPreferenceClickListener(preference -> {
        startImportSettingsBackup();
        return true;
      });
  }

  private void startExportSettingsBackup()
  {
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/json");
    intent.putExtra(Intent.EXTRA_TITLE, SETTINGS_BACKUP_FILENAME);
    startActivityForResult(intent, REQUEST_EXPORT_SETTINGS_BACKUP);
  }

  private void startImportSettingsBackup()
  {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_IMPORT_SETTINGS_BACKUP);
  }

  private void exportSettingsBackup(Uri uri)
  {
    try
    {
      SettingsBackup.exportToUri(this, uri,
          getPreferenceManager().getSharedPreferences());
      Toast.makeText(this, R.string.pref_export_settings_done,
          Toast.LENGTH_SHORT).show();
    }
    catch (Exception _e)
    {
      Toast.makeText(this, R.string.pref_export_settings_failed,
          Toast.LENGTH_LONG).show();
    }
  }

  private void importSettingsBackup(Uri uri)
  {
    try
    {
      SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
      SettingsBackup.importFromUri(this, uri, prefs);
      refreshTypingAssistanceStatus();
      Toast.makeText(this, R.string.pref_import_settings_done,
          Toast.LENGTH_SHORT).show();
      recreate();
    }
    catch (Exception _e)
    {
      Toast.makeText(this, R.string.pref_import_settings_failed,
          Toast.LENGTH_LONG).show();
    }
  }


  private void setupTypingAssistancePreferences()
  {
    refreshTypingAssistanceStatus();
    Preference clear = findPreference("clear_typing_assistance_data");
    if (clear != null)
      clear.setOnPreferenceClickListener(preference -> {
        showClearTypingAssistanceDialog();
        return true;
      });
  }

  private void setupVoiceTypingPreference()
  {
    Preference preference = findPreference("multimodal_voice_typing");
    if (preference == null)
      return;
    preference.setOnPreferenceChangeListener((_preference, value) -> {
        if (!Boolean.TRUE.equals(value) || hasRecordAudioPermission())
          return true;
        showVoicePermissionDisclosure();
        return false;
      });
  }

  private void requestVoicePermissionFromIntent()
  {
    Intent intent = getIntent();
    if (intent != null
        && intent.getBooleanExtra(EXTRA_REQUEST_VOICE_PERMISSION, false))
      getListView().post(() -> requestVoicePermissionIfNeeded());
  }

  private void requestVoicePermissionIfNeeded()
  {
    if (hasRecordAudioPermission())
    {
      setMultimodalVoiceTypingEnabled(true);
      return;
    }
    showVoicePermissionDisclosure();
  }

  private void showVoicePermissionDisclosure()
  {
    new AlertDialog.Builder(this)
      .setTitle(R.string.voice_typing_permission_title)
      .setMessage(R.string.voice_typing_permission_message)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.voice_typing_permission_continue,
          (_dialog, _which) -> requestRecordAudioPermission())
      .show();
  }

  private boolean hasRecordAudioPermission()
  {
    return VERSION.SDK_INT < 23
      || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
  }

  private void requestRecordAudioPermission()
  {
    if (VERSION.SDK_INT >= 23)
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
          REQUEST_RECORD_AUDIO_PERMISSION);
    else
      setMultimodalVoiceTypingEnabled(true);
  }

  private void setMultimodalVoiceTypingEnabled(boolean enabled)
  {
    getPreferenceManager().getSharedPreferences().edit()
      .putBoolean("multimodal_voice_typing", enabled).apply();
    Preference preference = findPreference("multimodal_voice_typing");
    if (preference instanceof TwoStatePreference)
      ((TwoStatePreference)preference).setChecked(enabled);
  }

  private void setupClipboardPreferences()
  {
    Preference screenshots = findPreference("clipboard_save_screenshots");
    if (screenshots == null)
      return;
    screenshots.setOnPreferenceClickListener(preference -> {
      requestScreenshotPermissionIfNeeded();
      return false;
    });
  }

  private void requestScreenshotPermissionFromIntent()
  {
    Intent intent = getIntent();
    if (intent != null
        && intent.getBooleanExtra(EXTRA_REQUEST_SCREENSHOT_PERMISSION, false))
      getListView().post(() -> requestScreenshotPermissionIfNeeded());
  }

  private void requestScreenshotPermissionIfNeeded()
  {
    boolean enabled = getPreferenceManager().getSharedPreferences()
      .getBoolean("clipboard_save_screenshots", true);
    if (!enabled)
      return;
    if (ClipboardHistoryService.hasScreenshotReadPermission(this))
    {
      ClipboardHistoryService.refresh_screenshot_observer();
      return;
    }
    if (VERSION.SDK_INT >= 23)
      requestPermissions(screenshotMediaPermissions(),
          REQUEST_SCREENSHOT_MEDIA_PERMISSION);
  }

  private String[] screenshotMediaPermissions()
  {
    if (VERSION.SDK_INT >= 34)
      return new String[]{
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
      };
    return new String[]{ VERSION.SDK_INT >= 33
      ? Manifest.permission.READ_MEDIA_IMAGES
      : Manifest.permission.READ_EXTERNAL_STORAGE };
  }

  private void refreshTypingAssistanceStatus()
  {
    Preference status = findPreference("typing_assistance_status");
    if (status == null)
      return;
    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
    boolean has_data = PersonalizationStore.has_data(prefs);
    status.setSummary(getString(has_data
          ? R.string.pref_typing_assistance_status_with_learning
          : R.string.pref_typing_assistance_status_empty));
  }

  private void showClearTypingAssistanceDialog()
  {
    new AlertDialog.Builder(this)
      .setTitle(R.string.pref_clear_typing_assistance_title)
      .setMessage(R.string.pref_clear_typing_assistance_summary)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.pref_clear_typing_assistance_title,
          (_dialog, _which) -> clearTypingAssistanceData())
      .show();
  }

  private void clearTypingAssistanceData()
  {
    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
    PersonalizationStore.clear(prefs);
    try
    {
      SharedPreferences protected_prefs =
        DirectBootAwarePreferences.get_shared_preferences(this);
      if (protected_prefs != prefs)
        PersonalizationStore.clear(protected_prefs);
    }
    catch (Exception _e) {}
    try
    {
      if (Config.globalConfig() != null && Config.globalConfig().handler != null)
        Config.globalConfig().handler.typing_assistance_data_cleared();
    }
    catch (Exception _e) {}
    refreshTypingAssistanceStatus();
    Toast.makeText(this, R.string.pref_clear_typing_assistance_done,
        Toast.LENGTH_SHORT).show();
  }

  private void styleSettingsList()
  {
    ListView list = getListView();
    boolean lightTheme = isLightTheme();
    int horizontal = settingsSidePadding();
    list.setPadding(0, list.getPaddingTop(), 0,
        list.getPaddingBottom());
    list.setClipToPadding(false);
    list.setDivider(null);
    list.setDividerHeight(0);
    list.setBackgroundColor(lightTheme ? 0xfff4f5f7 : 0xff0b0d10);

    ListAdapter adapter = getPreferenceScreen().getRootAdapter();
    list.setAdapter(new SettingsListAdapter(adapter, lightTheme, horizontal,
          getResources().getDisplayMetrics().density));
  }

  private boolean isLightTheme()
  {
    TypedValue value = new TypedValue();
    if (getTheme().resolveAttribute(android.R.attr.isLightTheme, value, true))
      return value.data != 0;
    return (getResources().getConfiguration().uiMode
        & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
  }

  private int settingsSidePadding()
  {
    return dp(20);
  }

  private int dp(int value)
  {
    return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
  }

  private static class SettingsListAdapter extends BaseAdapter
  {
    private static final int LIGHT_PAGE = 0xfff4f5f7;
    private static final int LIGHT_SURFACE = 0xffffffff;
    private static final int DARK_PAGE = 0xff0b0d10;
    private static final int DARK_SURFACE = 0xff15181c;

    private final ListAdapter _inner;
    private final boolean _lightTheme;
    private final int _sidePadding;
    private final float _density;

    SettingsListAdapter(ListAdapter inner, boolean lightTheme, int sidePadding,
        float density)
    {
      _inner = inner;
      _lightTheme = lightTheme;
      _sidePadding = sidePadding;
      _density = density;
    }

    @Override
    public int getCount()
    {
      return _inner.getCount();
    }

    @Override
    public Object getItem(int position)
    {
      return _inner.getItem(position);
    }

    @Override
    public long getItemId(int position)
    {
      return _inner.getItemId(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
      FrameLayout wrapper = rowWrapper(convertView, parent);
      View innerConvert = wrapper == convertView ? (View)wrapper.getTag() : null;
      FrameLayout card = ((SettingsRowWrapper)wrapper).card();
      wrapper.removeAllViews();
      card.removeAllViews();

      View view = _inner.getView(position, innerConvert, parent);
      card.setBackgroundColor(surfaceColor());
      card.setPadding(_sidePadding, 0, _sidePadding, 0);
      card.setTag(null);
      card.addView(view, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT));

      wrapper.setBackgroundColor(pageColor());
      wrapper.setPadding(0, topPaddingFor(position), 0,
          bottomPaddingFor(position));
      wrapper.setTag(view);
      wrapper.addView(card, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT));
      return wrapper;
    }

    @Override
    public boolean areAllItemsEnabled()
    {
      return _inner.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position)
    {
      return _inner.isEnabled(position);
    }

    @Override
    public int getItemViewType(int position)
    {
      return _inner.getItemViewType(position);
    }

    @Override
    public int getViewTypeCount()
    {
      return _inner.getViewTypeCount();
    }

    @Override
    public boolean hasStableIds()
    {
      return _inner.hasStableIds();
    }

    @Override
    public boolean isEmpty()
    {
      return _inner.isEmpty();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer)
    {
      _inner.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer)
    {
      _inner.unregisterDataSetObserver(observer);
    }

    private FrameLayout rowWrapper(View convertView, ViewGroup parent)
    {
      if (convertView instanceof SettingsRowWrapper)
        return (FrameLayout)convertView;
      return new SettingsRowWrapper(parent.getContext());
    }


    private int topPaddingFor(int position)
    {
      if (isSectionStart(position))
        return dp(12);
      return 0;
    }

    private int bottomPaddingFor(int position)
    {
      if (isSectionEnd(position))
        return dp(12);
      return 0;
    }

    private boolean isSectionStart(int position)
    {
      return position == 0 || getItem(position) instanceof PreferenceCategory;
    }

    private boolean isSectionEnd(int position)
    {
      return position == getCount() - 1
          || getItem(position + 1) instanceof PreferenceCategory;
    }

    private int pageColor()
    {
      return _lightTheme ? LIGHT_PAGE : DARK_PAGE;
    }

    private int surfaceColor()
    {
      return _lightTheme ? LIGHT_SURFACE : DARK_SURFACE;
    }


    private int dp(int value)
    {
      return (int)(value * _density + 0.5f);
    }

    private static class SettingsRowWrapper extends FrameLayout
    {
      private final FrameLayout _card;

      SettingsRowWrapper(Context context)
      {
        super(context);
        _card = new FrameLayout(context);
      }

      FrameLayout card()
      {
        return _card;
      }
    }

  }

}
