package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Touch locations for the current typed word, one nullable slot per code
    point. */
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

  /** Return a read-only copy of the current slots. */
  public List<Entry> entries()
  {
    return Collections.unmodifiableList(new ArrayList<Entry>(_entries));
  }

  public void clear()
  {
    _entries.clear();
  }

  /** Append one slot. A null entry means that no touch was captured. */
  public void add(Entry entry)
  {
    _entries.add(entry);
  }

  void addNulls(int count)
  {
    while (count-- > 0)
      _entries.add(null);
  }

  void set(int index, Entry entry)
  {
    _entries.set(index, entry);
  }

  public void removeFrom(int index)
  {
    if (index < 0)
      index = 0;
    while (_entries.size() > index)
      _entries.remove(_entries.size() - 1);
  }

  /** Capture immutable touch slots for asynchronous decoding. */
  public Snapshot snapshot()
  {
    return new Snapshot(_entries);
  }

  public static final class Snapshot
  {
    private final Entry[] _entries;

    private Snapshot(List<Entry> entries)
    {
      _entries = entries.toArray(new Entry[entries.size()]);
    }

    public int size()
    {
      return _entries.length;
    }

    /** Return the nullable touch slot at [index]. */
    public Entry get(int index)
    {
      return _entries[index];
    }
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
