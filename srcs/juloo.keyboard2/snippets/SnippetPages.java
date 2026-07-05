package juloo.keyboard2.snippets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SnippetPages
{
  public static final int PAGE_SIZE = SnippetSlot.PAGE_SIZE;

  private SnippetPages() {}

  public static int pageCount(int slotCount)
  {
    if (slotCount <= 0)
      return 0;
    return (slotCount + PAGE_SIZE - 1) / PAGE_SIZE;
  }

  public static List<SnippetSlot> pageOf(List<SnippetSlot> slots, int pageIndex)
  {
    if (slots == null || pageIndex < 0)
      return Collections.emptyList();
    int start = pageIndex * PAGE_SIZE;
    if (start >= slots.size())
      return Collections.emptyList();
    int end = Math.min(start + PAGE_SIZE, slots.size());
    return Collections.unmodifiableList(new ArrayList<>(slots.subList(start, end)));
  }
}
