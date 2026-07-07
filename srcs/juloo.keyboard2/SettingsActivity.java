package juloo.keyboard2;

import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.view.View;
import android.view.ViewGroup;

public class SettingsActivity extends PreferenceActivity
{
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
    Preference dashboard = findPreference("giphy_api_dashboard");
    if (dashboard != null)
      dashboard.setIntent(new Intent(Intent.ACTION_VIEW,
            Uri.parse(GiphyClient.DASHBOARD_URL)));

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
    getListView().post(() -> styleSettingsList());
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

  private void styleSettingsList()
  {
    ListView list = getListView();
    int horizontal = settingsSidePadding();
    list.setPadding(0, list.getPaddingTop(), 0,
        list.getPaddingBottom());
    list.setClipToPadding(false);
    list.setDivider(null);
    list.setDividerHeight(0);

    ListAdapter adapter = getPreferenceScreen().getRootAdapter();
    list.setAdapter(new SettingsListAdapter(adapter, isNightMode(), horizontal,
          getResources().getDisplayMetrics().density));
  }

  private boolean isNightMode()
  {
    return (getResources().getConfiguration().uiMode
        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
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
    private static final int LIGHT_SECTION = 0xffffffff;
    private static final int LIGHT_SECTION_ALT = 0xfff4f5f7;
    private static final int DARK_SECTION = 0xff121212;
    private static final int DARK_SECTION_ALT = 0xff1f1f1f;

    private final ListAdapter _inner;
    private final boolean _nightMode;
    private final int _sidePadding;
    private final float _density;

    SettingsListAdapter(ListAdapter inner, boolean nightMode, int sidePadding,
        float density)
    {
      _inner = inner;
      _nightMode = nightMode;
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
      card.setBackground(sectionBackground(position));
      card.setPadding(_sidePadding, topPaddingFor(position),
          _sidePadding, bottomPaddingFor(position));
      card.setTag(null);
      card.addView(view, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT));

      wrapper.setPadding(0, 0, 0, 0);
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
        return _sidePadding;
      return 0;
    }

    private int bottomPaddingFor(int position)
    {
      if (isSectionEnd(position))
        return _sidePadding;
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

    private GradientDrawable sectionBackground(int position)
    {
      GradientDrawable background = new GradientDrawable();
      background.setColor(sectionColor(sectionFor(position)));
      return background;
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

    private int sectionFor(int position)
    {
      int section = -1;
      for (int i = 0; i <= position; ++i)
        if (getItem(i) instanceof PreferenceCategory)
          ++section;
      return Math.max(0, section);
    }

    private int sectionColor(int section)
    {
      boolean alternate = (section & 1) == 1;
      if (_nightMode)
        return alternate ? DARK_SECTION_ALT : DARK_SECTION;
      return alternate ? LIGHT_SECTION_ALT : LIGHT_SECTION;
    }
  }

}
