package juloo.keyboard2.snippets;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import juloo.keyboard2.R;

public class SnippetSlotsPreference extends PreferenceCategory
{
  private static final String PREF_EXPANDED =
      "frankenkey_snippets_settings_expanded";
  private boolean _attached = false;
  private List<SnippetSlot> _slots;

  public SnippetSlotsPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setTitle(R.string.pref_snippets_slots_title);
    setOrderingAsAdded(true);
  }

  @Override
  protected void onAttachedToActivity()
  {
    super.onAttachedToActivity();
    if (_attached)
      return;
    _attached = true;
    load_and_reattach();
  }

  private void load_and_reattach()
  {
    _slots = SnippetStore.loadSlots(getContext());
    reattach();
  }

  private void reattach()
  {
    if (!_attached)
      return;
    removeAll();
    addPreference(new TogglePreference(getContext(), is_expanded()));
    if (!is_expanded())
      return;
    for (SnippetSlot slot : _slots)
      addPreference(new SlotPreference(getContext(), slot));
    addPreference(new AddPagePreference(getContext()));
  }

  private boolean is_expanded()
  {
    return getPreferenceManager().getSharedPreferences()
      .getBoolean(PREF_EXPANDED, false);
  }

  private void set_expanded(boolean expanded)
  {
    getPreferenceManager().getSharedPreferences()
      .edit()
      .putBoolean(PREF_EXPANDED, expanded)
      .apply();
  }

  private class TogglePreference extends Preference
  {
    private final boolean _expanded;

    TogglePreference(Context context, boolean expanded)
    {
      super(context);
      _expanded = expanded;
      setPersistent(false);
      setTitle(expanded ? R.string.pref_snippets_slots_hide :
          R.string.pref_snippets_slots_show);
      setSummary(expanded ? R.string.pref_snippets_slots_hide_summary :
          R.string.pref_snippets_slots_show_summary);
      setIcon(expanded ? android.R.drawable.arrow_up_float :
          android.R.drawable.arrow_down_float);
    }

    @Override
    protected void onClick()
    {
      set_expanded(!_expanded);
      reattach();
    }
  }

  private void change_slot(SnippetSlot slot)
  {
    persist_slots(SnippetStore.replaceSlot(_slots, slot));
  }

  private void clear_slot(SnippetSlot slot)
  {
    change_slot(SnippetSlot.of(slot.getIndex(), "", ""));
  }

  private void add_page()
  {
    persist_slots(SnippetStore.withMinimumSlots(_slots,
          _slots.size() + SnippetPages.PAGE_SIZE));
  }

  private void persist_slots(List<SnippetSlot> slots)
  {
    _slots = SnippetStore.withMinimumSlots(slots, SnippetStore.DEFAULT_SLOT_COUNT);
    SnippetStore.saveSlots(getContext(), _slots);
    reattach();
  }

  private class SlotPreference extends Preference
  {
    private final SnippetSlot _slot;

    SlotPreference(Context context, SnippetSlot slot)
    {
      super(context);
      _slot = slot;
      setPersistent(false);
      setTitle(slot_title(slot));
      setSummary(slot.isConfigured() ? slot.getPhrase() :
          getContext().getString(R.string.pref_snippets_slot_empty));
    }

    @Override
    protected void onClick()
    {
      show_editor(_slot);
    }

    @Override
    protected void onBindView(View view)
    {
      super.onBindView(view);
      TextView title = (TextView)view.findViewById(android.R.id.title);
      TextView summary = (TextView)view.findViewById(android.R.id.summary);
      if (title != null)
      {
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setHorizontallyScrolling(false);
      }
      if (summary != null)
      {
        summary.setSingleLine(false);
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setHorizontallyScrolling(false);
      }
    }
  }

  private class AddPagePreference extends Preference
  {
    AddPagePreference(Context context)
    {
      super(context);
      setPersistent(false);
      setTitle(R.string.pref_snippets_add_page);
      setSummary(R.string.pref_snippets_add_page_summary);
    }

    @Override
    protected void onClick()
    {
      add_page();
    }
  }

  private String slot_title(SnippetSlot slot)
  {
    return getContext().getString(R.string.pref_snippets_slot_title,
        slot.getIndex() + 1, slot.getDisplayLabel());
  }


  private void show_editor(final SnippetSlot slot)
  {
    final LinearLayout content = new LinearLayout(getContext());
    content.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(20);
    content.setPadding(pad, pad / 2, pad, 0);

    final TextView preview = new TextView(getContext());
    preview.setTextSize(20);
    content.addView(preview, match_wrap());

    final EditText phrase = new EditText(getContext());
    phrase.setHint(R.string.pref_snippets_phrase_hint);
    phrase.setText(slot.getPhrase());
    phrase.setSingleLine(false);
    content.addView(phrase, match_wrap());

    final EditText customLabel = new EditText(getContext());
    customLabel.setHint(R.string.pref_snippets_label_hint);
    customLabel.setText(slot.getCustomLabel());
    customLabel.setSingleLine(true);
    content.addView(customLabel, match_wrap());

    TextWatcher watcher = new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count)
      {
        refresh_preview(preview, slot.getIndex(), phrase, customLabel);
      }
      public void afterTextChanged(Editable s) {}
    };
    phrase.addTextChangedListener(watcher);
    customLabel.addTextChangedListener(watcher);
    refresh_preview(preview, slot.getIndex(), phrase, customLabel);

    new AlertDialog.Builder(getContext())
      .setTitle(getContext().getString(R.string.pref_snippets_edit_title,
            slot.getIndex() + 1))
      .setView(content)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which)
        {
          change_slot(SnippetSlot.of(slot.getIndex(),
                phrase.getText().toString(),
                customLabel.getText().toString()));
        }
      })
      .setNeutralButton(R.string.pref_snippets_clear, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which)
        {
          clear_slot(slot);
        }
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void refresh_preview(TextView preview, int index, EditText phrase,
      EditText customLabel)
  {
    SnippetSlot draft = SnippetSlot.of(index,
        phrase.getText().toString(), customLabel.getText().toString());
    preview.setText(getContext().getString(R.string.pref_snippets_preview,
          draft.getDisplayLabel()));
  }

  private LinearLayout.LayoutParams match_wrap()
  {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private int dp(int value)
  {
    return (int)(value * getContext().getResources().getDisplayMetrics().density + 0.5f);
  }
}
