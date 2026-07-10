package juloo.keyboard2;

import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;

public final class ClipboardHistoryView extends NonScrollGridView
  implements ClipboardHistoryService.OnClipboardHistoryChange
{
  List<ClipboardHistoryService.ClipboardEntry> _history;
  ClipboardHistoryService _service;
  ClipboardEntriesAdapter _adapter;

  public ClipboardHistoryView(android.content.Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    _history = Collections.EMPTY_LIST;
    _adapter = this.new ClipboardEntriesAdapter();
    _service = ClipboardHistoryService.get_service(ctx);
    if (_service != null)
    {
      _service.set_on_clipboard_history_change(this);
      _history = _service.clear_expired_and_get_entries();
    }
    setAdapter(_adapter);
  }

  /** The history entry at index [pos] is removed from the history and added to
      the list of pinned clipboards. */
  public void pin_entry(int pos)
  {
    ClipboardPinView v = find_pin_view();
    if (v == null)
      return;
    ClipboardHistoryService.ClipboardEntry clip = _history.get(pos);
    v.add_entry(clip);
    _service.remove_history_entry(clip);
  }

  /** Send the specified entry to the editor. */
  public void paste_entry(int pos)
  {
    ClipboardHistoryService.paste(_history.get(pos));
  }

  /** Open text clips for editing; image clips paste directly. */
  public void open_entry(final int pos, View source)
  {
    final ClipboardHistoryService.ClipboardEntry clip = _history.get(pos);
    if (clip.isImage())
    {
      paste_entry(pos);
      return;
    }
    ClipboardItemEditorView editor = ClipboardItemEditorView.findFrom(source);
    if (editor == null)
    {
      paste_entry(pos);
      return;
    }
    editor.open(clip.text, new ClipboardItemEditorView.Listener()
    {
      @Override
      public void onPasteEdited(String editedText)
      {
        ClipboardHistoryService.ClipboardEntry edited =
          ClipboardHistoryService.ClipboardEntry.text(editedText);
        _service.replace_history_entry(clip, edited);
        ClipboardHistoryService.paste(editedText);
      }
    });
  }

  @Override
  public void on_clipboard_history_change()
  {
    update_data();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility)
  {
    if (visibility == View.VISIBLE)
      update_data();
  }

  void update_data()
  {
    _history = _service.clear_expired_and_get_entries();
    _adapter.notifyDataSetChanged();
    invalidate();
  }

  private ClipboardPinView find_pin_view()
  {
    View parent = this;
    while (parent != null)
    {
      View pins = parent.findViewById(R.id.clipboard_pin_view);
      if (pins instanceof ClipboardPinView)
        return (ClipboardPinView)pins;
      if (!(parent.getParent() instanceof View))
        return null;
      parent = (View)parent.getParent();
    }
    return null;
  }

  class ClipboardEntriesAdapter extends BaseAdapter
  {
    public ClipboardEntriesAdapter() {}

    @Override
    public int getCount() { return _history.size(); }
    @Override
    public Object getItem(int pos) { return _history.get(pos); }
    @Override
    public long getItemId(int pos) { return _history.get(pos).displayText().hashCode(); }

    @Override
    public View getView(final int pos, View v, ViewGroup _parent)
    {
      if (v == null)
        v = View.inflate(getContext(), R.layout.clipboard_history_entry, null);
      enforceFixedCard(v, R.id.clipboard_entry_text);
      bind_entry(v, _history.get(pos));
      v.setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { open_entry(pos, v); }
          });
      v.findViewById(R.id.clipboard_entry_addpin).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { pin_entry(pos); }
          });
      v.findViewById(R.id.clipboard_entry_paste).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { paste_entry(pos); }
          });
      return v;
    }

    private void bind_entry(View v, ClipboardHistoryService.ClipboardEntry entry)
    {
      TextView text = (TextView)v.findViewById(R.id.clipboard_entry_text);
      ImageView image = (ImageView)v.findViewById(R.id.clipboard_entry_image);
      text.setText(entry.displayText());
      if (!entry.isImage())
      {
        if (image != null)
          image.setVisibility(View.GONE);
        text.setVisibility(View.VISIBLE);
        return;
      }
      if (image != null)
      {
        image.setVisibility(View.VISIBLE);
        try { image.setImageURI(Uri.parse(entry.uri)); }
        catch (Exception _e) { image.setVisibility(View.GONE); }
      }
      text.setVisibility(View.VISIBLE);
    }
  }
}
