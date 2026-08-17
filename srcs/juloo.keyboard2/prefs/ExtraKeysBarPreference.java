package juloo.keyboard2.prefs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.ExtraKeysShortcutStore;
import juloo.keyboard2.ExtraKeysShortcutStore.Shortcut;
import juloo.keyboard2.R;

/** Opens the Extra Keys shortcut visibility, order, and chord manager. */
public final class ExtraKeysBarPreference extends Preference
{
  private List<Shortcut> _shortcuts;
  private SharedPreferences _preferences;
  private LinearLayout _rows;
  private ScrollView _scroll;
  private AlertDialog _managerDialog;

  public ExtraKeysBarPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setPersistent(false);
  }

  @Override
  protected void onClick()
  {
    showManager();
  }

  public void showManager()
  {
    if (_managerDialog != null && _managerDialog.isShowing())
      return;
    _preferences = getSharedPreferences();
    if (_preferences == null)
      return;
    _shortcuts = new ArrayList<Shortcut>(
        ExtraKeysShortcutStore.load(_preferences));

    LinearLayout content = new LinearLayout(getContext());
    content.setOrientation(LinearLayout.VERTICAL);
    int padding = dp(16);
    content.setPadding(padding, dp(8), padding, dp(8));

    TextView help = new TextView(getContext());
    help.setText(R.string.extra_keys_manage_help);
    help.setTextSize(16);
    help.setPadding(0, 0, 0, dp(12));
    content.addView(help, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    _scroll = new ScrollView(getContext());
    _scroll.setFillViewport(false);
    _rows = new LinearLayout(getContext());
    _rows.setOrientation(LinearLayout.VERTICAL);
    _rows.setOnDragListener(this::handleDrag);
    _scroll.addView(_rows, new ScrollView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    content.addView(_scroll, scrollParams);

    Button add = new Button(getContext());
    add.setText(R.string.extra_keys_add_shortcut);
    add.setOnClickListener(view -> showAddDialog());
    LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    addParams.topMargin = dp(12);
    content.addView(add, addParams);
    rebuildRows();

    final AlertDialog dialog = new AlertDialog.Builder(getContext())
      .setTitle(R.string.extra_keys_manage_title)
      .setView(content)
      .setPositiveButton(android.R.string.ok, null)
      .create();
    _managerDialog = dialog;
    dialog.setOnShowListener(_dialog -> {
      int maxHeight = Math.round(getContext().getResources()
          .getDisplayMetrics().heightPixels * 0.76f);
      content.setMinimumHeight(Math.min(maxHeight, dp(620)));
    });
    dialog.setOnDismissListener(_dialog -> {
      if (_managerDialog == dialog)
        _managerDialog = null;
    });
    dialog.show();
  }

  private void rebuildRows()
  {
    _rows.removeAllViews();
    for (Shortcut shortcut : _shortcuts)
      _rows.addView(makeRow(shortcut));
  }

  private View makeRow(Shortcut shortcut)
  {
    LinearLayout row = new LinearLayout(getContext());
    row.setTag(shortcut.id);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(64));
    row.setPadding(0, dp(4), 0, dp(4));

    TextView preview = new TextView(getContext());
    preview.setText(shortcut.label);
    preview.setTextColor(Color.WHITE);
    preview.setTextSize(14);
    preview.setSingleLine(true);
    preview.setGravity(Gravity.CENTER);
    preview.setPadding(dp(8), dp(6), dp(8), dp(6));
    GradientDrawable previewBackground = new GradientDrawable();
    previewBackground.setColor(0xff303030);
    previewBackground.setCornerRadius(dp(6));
    preview.setBackground(previewBackground);
    LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
        dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
    previewParams.rightMargin = dp(12);
    row.addView(preview, previewParams);

    TextView title = new TextView(getContext());
    title.setText(shortcut.label);
    title.setTextSize(17);
    title.setSingleLine(true);
    row.addView(title, new LinearLayout.LayoutParams(
          0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    if (shortcut.isCustom())
    {
      ImageButton remove = new ImageButton(getContext());
      remove.setImageResource(android.R.drawable.ic_menu_delete);
      remove.setBackgroundColor(Color.TRANSPARENT);
      remove.setContentDescription(getContext().getString(
            R.string.extra_keys_remove, shortcut.label));
      remove.setOnClickListener(view -> confirmRemove(shortcut));
      row.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }

    Switch visible = new Switch(getContext());
    visible.setChecked(shortcut.enabled);
    visible.setContentDescription(shortcut.label);
    visible.setOnCheckedChangeListener((button, checked) -> {
      int index = indexOf(shortcut.id);
      if (index < 0)
        return;
      _shortcuts.set(index, _shortcuts.get(index).withEnabled(checked));
      preview.setAlpha(checked ? 1f : 0.45f);
      title.setAlpha(checked ? 1f : 0.45f);
      persist();
    });
    preview.setAlpha(shortcut.enabled ? 1f : 0.45f);
    title.setAlpha(shortcut.enabled ? 1f : 0.45f);
    row.addView(visible, new LinearLayout.LayoutParams(dp(56), dp(48)));

    TextView grip = new TextView(getContext());
    grip.setText("⠿");
    grip.setTextSize(24);
    grip.setGravity(Gravity.CENTER);
    grip.setContentDescription(getContext().getString(
          R.string.extra_keys_reorder, shortcut.label));
    grip.setOnTouchListener((view, event) -> {
      if (event.getActionMasked() != MotionEvent.ACTION_DOWN)
        return true;
      ClipData data = ClipData.newPlainText("shortcut", shortcut.id);
      if (Build.VERSION.SDK_INT >= 24)
        row.startDragAndDrop(data, new View.DragShadowBuilder(row), row, 0);
      else
        row.startDrag(data, new View.DragShadowBuilder(row), row, 0);
      return true;
    });
    row.addView(grip, new LinearLayout.LayoutParams(dp(48), dp(56)));
    return row;
  }

  private boolean handleDrag(View _view, DragEvent event)
  {
    View dragged = (event.getLocalState() instanceof View)
      ? (View)event.getLocalState() : null;
    if (dragged == null || dragged.getParent() != _rows)
      return false;
    switch (event.getAction())
    {
      case DragEvent.ACTION_DRAG_STARTED:
        dragged.setAlpha(0.45f);
        return true;
      case DragEvent.ACTION_DRAG_LOCATION:
        moveDraggedRow(dragged, event.getY());
        autoScroll(event.getY());
        return true;
      case DragEvent.ACTION_DROP:
        persist();
        return true;
      case DragEvent.ACTION_DRAG_ENDED:
        dragged.setAlpha(1f);
        persist();
        return true;
      default:
        return true;
    }
  }

  private void moveDraggedRow(View dragged, float y)
  {
    int from = _rows.indexOfChild(dragged);
    int target = from;
    if (from > 0)
    {
      View previous = _rows.getChildAt(from - 1);
      if (y < previous.getTop() + previous.getHeight() / 2f)
        target = from - 1;
    }
    if (target == from && from + 1 < _rows.getChildCount())
    {
      View next = _rows.getChildAt(from + 1);
      if (y > next.getTop() + next.getHeight() / 2f)
        target = from + 1;
    }
    if (target == from)
      return;
    Shortcut moved = _shortcuts.remove(from);
    _shortcuts.add(target, moved);
    _rows.removeView(dragged);
    _rows.addView(dragged, target);
  }

  private void autoScroll(float y)
  {
    int top = _scroll.getScrollY();
    int edge = dp(56);
    if (y < top + edge)
      _scroll.smoothScrollBy(0, -dp(32));
    else if (y > top + _scroll.getHeight() - edge)
      _scroll.smoothScrollBy(0, dp(32));
  }

  private void showAddDialog()
  {
    final AlertDialog manager = _managerDialog;
    if (manager != null && manager.isShowing())
      manager.hide();
    LinearLayout content = new LinearLayout(getContext());
    content.setOrientation(LinearLayout.VERTICAL);
    int padding = dp(20);
    content.setPadding(padding, dp(8), padding, 0);

    TextView help = new TextView(getContext());
    help.setText(R.string.extra_keys_add_help);
    help.setTextSize(16);
    content.addView(help);

    TextView modifiersLabel = sectionLabel(R.string.extra_keys_modifiers);
    content.addView(modifiersLabel);
    CheckBox ctrl = checkBox("Ctrl");
    CheckBox alt = checkBox("Alt");
    CheckBox shift = checkBox("Shift");
    CheckBox meta = checkBox("Cmd");
    LinearLayout modifiersTop = checkRow(ctrl, alt);
    LinearLayout modifiersBottom = checkRow(shift, meta);
    content.addView(modifiersTop);
    content.addView(modifiersBottom);

    content.addView(sectionLabel(R.string.extra_keys_key));
    String[] keyLabels = ExtraKeysShortcutStore.customKeyLabels();
    String[] keyNames = ExtraKeysShortcutStore.customKeyNames();
    Spinner key = new Spinner(getContext());
    key.setAdapter(new ArrayAdapter<String>(getContext(),
          android.R.layout.simple_spinner_dropdown_item, keyLabels));
    content.addView(key, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    content.addView(sectionLabel(R.string.extra_keys_custom_key));
    EditText customKey = new EditText(getContext());
    customKey.setSingleLine(true);
    customKey.setHint(R.string.extra_keys_custom_key_hint);
    customKey.setContentDescription(getContext().getString(
          R.string.extra_keys_custom_key));
    content.addView(customKey, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    AlertDialog dialog = new AlertDialog.Builder(getContext())
      .setTitle(R.string.extra_keys_add_title)
      .setView(content)
      .setPositiveButton(R.string.extra_keys_add_shortcut, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create();
    dialog.setOnShowListener(_dialog -> dialog.getButton(
          AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
      int modifiers = 0;
      if (ctrl.isChecked()) modifiers |= ExtraKeysShortcutStore.CTRL;
      if (alt.isChecked()) modifiers |= ExtraKeysShortcutStore.ALT;
      if (shift.isChecked()) modifiers |= ExtraKeysShortcutStore.SHIFT;
      if (meta.isChecked()) modifiers |= ExtraKeysShortcutStore.META;
      if (modifiers == 0)
      {
        Toast.makeText(getContext(), R.string.extra_keys_modifier_required,
            Toast.LENGTH_SHORT).show();
        return;
      }
      int selected = key.getSelectedItemPosition();
      Shortcut candidate;
      try
      {
        String customName = customKey.getText().toString().trim();
        candidate = customName.length() == 0
          ? ExtraKeysShortcutStore.custom(
              keyNames[selected], keyLabels[selected], modifiers)
          : ExtraKeysShortcutStore.custom(customName, null, modifiers);
      }
      catch (IllegalArgumentException _e)
      {
        Toast.makeText(getContext(), R.string.extra_keys_key_invalid,
            Toast.LENGTH_SHORT).show();
        return;
      }
      for (int i = 0; i < _shortcuts.size(); i++)
      {
        Shortcut existing = _shortcuts.get(i);
        if (ExtraKeysShortcutStore.ACTION_KEY.equals(existing.action) &&
            existing.keyName.equals(candidate.keyName) &&
            existing.modifiers == modifiers)
        {
          _shortcuts.set(i, existing.withEnabled(true));
          persist();
          rebuildRows();
          Toast.makeText(getContext(), R.string.extra_keys_duplicate,
              Toast.LENGTH_SHORT).show();
          dialog.dismiss();
          return;
        }
      }
      _shortcuts.add(candidate);
      persist();
      rebuildRows();
      _scroll.post(() -> _scroll.fullScroll(View.FOCUS_DOWN));
      dialog.dismiss();
    }));
    dialog.setOnDismissListener(_dialog -> {
      if (manager != null && _managerDialog == manager
          && !manager.isShowing())
        manager.show();
    });
    dialog.show();
  }

  private void confirmRemove(Shortcut shortcut)
  {
    new AlertDialog.Builder(getContext())
      .setMessage(getContext().getString(
            R.string.extra_keys_remove_question, shortcut.label))
      .setPositiveButton(android.R.string.ok, (_dialog, _which) -> {
        int index = indexOf(shortcut.id);
        if (index >= 0)
        {
          _shortcuts.remove(index);
          persist();
          rebuildRows();
        }
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private TextView sectionLabel(int text)
  {
    TextView label = new TextView(getContext());
    label.setText(text);
    label.setTextSize(13);
    label.setAllCaps(true);
    label.setPadding(0, dp(18), 0, dp(6));
    return label;
  }

  private CheckBox checkBox(String label)
  {
    CheckBox check = new CheckBox(getContext());
    check.setText(label);
    check.setMinHeight(dp(48));
    return check;
  }

  private LinearLayout checkRow(CheckBox first, CheckBox second)
  {
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.addView(first, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(second, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    return row;
  }

  private int indexOf(String id)
  {
    for (int i = 0; i < _shortcuts.size(); i++)
      if (_shortcuts.get(i).id.equals(id))
        return i;
    return -1;
  }

  private void persist()
  {
    ExtraKeysShortcutStore.save(_preferences, _shortcuts);
  }

  private int dp(int value)
  {
    return Math.round(value * getContext().getResources()
        .getDisplayMetrics().density);
  }
}
