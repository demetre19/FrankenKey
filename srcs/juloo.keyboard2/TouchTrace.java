package juloo.keyboard2;

import java.util.ArrayList;
import java.util.List;

/** Touch locations for the current typed word, one entry per typed character. */
public final class TouchTrace
{
  private final ArrayList<Entry> _entries = new ArrayList<Entry>();

  public int size()
  {
    return _entries.size();
  }

  public Entry get(int index)
  {
    return _entries.get(index);
  }

  public List<Entry> entries()
  {
    return _entries;
  }

  public void clear()
  {
    _entries.clear();
  }

  public void add(Entry entry)
  {
    if (entry != null)
      _entries.add(entry);
  }

  public void removeFrom(int index)
  {
    if (index < 0)
      index = 0;
    while (_entries.size() > index)
      _entries.remove(_entries.size() - 1);
  }

  public TouchTrace copy()
  {
    TouchTrace out = new TouchTrace();
    out._entries.addAll(_entries);
    return out;
  }

  public static Entry entry(float touchX, float touchY, float keyCenterX,
      float keyCenterY, float keyWidth, float keyHeight)
  {
    return new Entry(touchX, touchY, keyCenterX, keyCenterY, keyWidth,
        keyHeight);
  }

  public static final class Entry
  {
    public final float touchX;
    public final float touchY;
    public final float keyCenterX;
    public final float keyCenterY;
    public final float keyWidth;
    public final float keyHeight;

    Entry(float touchX_, float touchY_, float keyCenterX_, float keyCenterY_,
        float keyWidth_, float keyHeight_)
    {
      touchX = touchX_;
      touchY = touchY_;
      keyCenterX = keyCenterX_;
      keyCenterY = keyCenterY_;
      keyWidth = keyWidth_;
      keyHeight = keyHeight_;
    }
  }
}
