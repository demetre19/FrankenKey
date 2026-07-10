package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Inline editor for long text clips selected from the clipboard grid. */
public final class ClipboardItemEditorView extends LinearLayout
{
  private EditText _text;
  private View _paste;
  private View _close;
  private Listener _listener;

  public ClipboardItemEditorView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _text = (EditText)findViewById(R.id.clipboard_editor_text);
    _paste = findViewById(R.id.clipboard_editor_paste);
    _close = findViewById(R.id.clipboard_editor_close);
    if (_paste != null)
      _paste.setOnClickListener(new View.OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          pasteEditedText();
        }
      });
    if (_close != null)
      _close.setOnClickListener(new View.OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          close();
        }
      });
  }

  public void open(String text, Listener listener)
  {
    _listener = listener;
    if (_text != null)
    {
      _text.setText(text);
      _text.setSelection(_text.length());
      _text.requestFocus();
    }
    setVisibility(View.VISIBLE);
  }

  public void close()
  {
    setVisibility(View.GONE);
    _listener = null;
  }

  private void pasteEditedText()
  {
    String edited = _text == null ? "" : _text.getText().toString();
    Listener listener = _listener;
    close();
    if (listener != null)
      listener.onPasteEdited(edited);
    else
      ClipboardHistoryService.paste(edited);
  }

  public static ClipboardItemEditorView findFrom(View child)
  {
    View current = child;
    while (current != null)
    {
      View editor = current.findViewById(R.id.clipboard_item_editor);
      if (editor instanceof ClipboardItemEditorView)
        return (ClipboardItemEditorView)editor;
      ViewParent parent = current.getParent();
      if (!(parent instanceof View))
        return null;
      current = (View)parent;
    }
    return null;
  }

  public interface Listener
  {
    public void onPasteEdited(String editedText);
  }
}
