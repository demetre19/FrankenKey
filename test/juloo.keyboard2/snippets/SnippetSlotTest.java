package juloo.keyboard2.snippets;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SnippetSlotTest
{
  public SnippetSlotTest() {}

  @Test
  public void empty_slot_label_is_one_based_index()
  {
    assertEquals("1", SnippetSlot.of(0, "", "").getDisplayLabel());
    assertEquals("7", SnippetSlot.of(6, "", "").getDisplayLabel());
  }

  @Test
  public void phrase_label_uses_first_one_or_two_code_points()
  {
    Utils.label("a", "a");
    Utils.label("ab", "ab");
    Utils.label("abc", "ab");
    Utils.label("\uD83D\uDE00", "\uD83D\uDE00");
    Utils.label("\uD83D\uDE00x", "\uD83D\uDE00x");
    Utils.label("\uD83D\uDE00\uD83D\uDE0E!", "\uD83D\uDE00\uD83D\uDE0E");
  }

  @Test
  public void custom_label_overrides_generated_label()
  {
    assertEquals("mail", SnippetSlot.of(0, "email@example.com", "mail").getDisplayLabel());
    assertEquals("★", SnippetSlot.of(4, "", "★").getDisplayLabel());
  }

  @Test
  public void configured_state_follows_insertable_phrase()
  {
    assertFalse(SnippetSlot.of(0, "", "").isConfigured());
    assertFalse(SnippetSlot.of(1, "", "★").isConfigured());
    assertTrue(SnippetSlot.of(2, "ok", "").isConfigured());
    assertTrue(SnippetSlot.of(3, "\uD83D\uDE00", "face").isConfigured());
  }

  @Test
  public void page_count_uses_seven_slot_pages()
  {
    assertEquals(7, SnippetPages.PAGE_SIZE);
    Utils.pageCount(0, 0);
    Utils.pageCount(1, 1);
    Utils.pageCount(7, 1);
    Utils.pageCount(8, 2);
    Utils.pageCount(14, 2);
    Utils.pageCount(15, 3);
  }

  @Test
  public void page_slices_use_seven_slot_boundaries()
  {
    Utils.page(1, 0, 0);
    Utils.page(7, 0, 0, 1, 2, 3, 4, 5, 6);
    Utils.page(8, 0, 0, 1, 2, 3, 4, 5, 6);
    Utils.page(8, 1, 7);
    Utils.page(14, 0, 0, 1, 2, 3, 4, 5, 6);
    Utils.page(14, 1, 7, 8, 9, 10, 11, 12, 13);
    Utils.page(15, 0, 0, 1, 2, 3, 4, 5, 6);
    Utils.page(15, 1, 7, 8, 9, 10, 11, 12, 13);
    Utils.page(15, 2, 14);
  }

  static class Utils
  {
    static void label(String phrase, String expected)
    {
      assertEquals(phrase, expected, SnippetSlot.of(2, phrase, "").getDisplayLabel());
    }

    static void pageCount(int slotCount, int expected)
    {
      assertEquals("slot count " + slotCount, expected, SnippetPages.pageCount(slotCount));
    }

    static void page(int slotCount, int pageIndex, int... expectedIndexes)
    {
      List<SnippetSlot> page = SnippetPages.pageOf(slots(slotCount), pageIndex);
      assertEquals("page size for slot count " + slotCount + " page " + pageIndex,
          expectedIndexes.length, page.size());
      for (int i = 0; i < expectedIndexes.length; ++i)
        assertEquals("slot index at page offset " + i + " for slot count " + slotCount
            + " page " + pageIndex, expectedIndexes[i], page.get(i).getIndex());
    }

    static List<SnippetSlot> slots(int count)
    {
      ArrayList<SnippetSlot> slots = new ArrayList<>();
      for (int i = 0; i < count; ++i)
        slots.add(SnippetSlot.of(i, "slot" + i, ""));
      return slots;
    }
  }
}
