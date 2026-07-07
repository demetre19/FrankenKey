package juloo.keyboard2;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class SettingsUiContractsTest
{
  @Test
  public void snippet_slots_settings_default_to_collapsed_accordion()
      throws Exception
  {
    String source = readSource(
        "srcs/juloo.keyboard2/snippets/SnippetSlotsPreference.java");
    String reattach = methodBody(source, "private void reattach()");
    String isExpanded = methodBody(source, "private boolean is_expanded()");
    String toggle = methodBody(source, "protected void onClick()");

    int toggleRow = reattach.indexOf("new TogglePreference");
    int collapsedReturn = reattach.indexOf("if (!is_expanded())");
    int slotRows = reattach.indexOf("new SlotPreference");
    int addPage = reattach.indexOf("new AddPagePreference");

    assertOrdered("The snippet slots preference must add only its accordion row before checking expansion.",
        toggleRow, collapsedReturn, slotRows, addPage);
    assertTrue("Snippet slot settings must be collapsed by default.",
        isExpanded.contains("getBoolean(PREF_EXPANDED, false)"));
    assertTrue("Tapping the accordion row must flip the saved expanded state.",
        toggle.contains("set_expanded(!_expanded)")
        && toggle.contains("reattach()"));
  }

  @Test
  public void expanded_snippet_slot_rows_bound_long_title_and_summary_text()
      throws Exception
  {
    String source = readSource(
        "srcs/juloo.keyboard2/snippets/SnippetSlotsPreference.java");
    String slotPreference = methodBody(source,
        "private class SlotPreference extends Preference");

    assertTrue("Expanded snippet slot rows must still render preview labels through the Preference title.",
        slotPreference.contains("setTitle(slot_title(slot))"));
    assertTrue("Expanded snippet slot rows must still render configured phrases through the Preference summary.",
        slotPreference.contains("setSummary(")
        && slotPreference.contains("slot.getPhrase()"));
    assertTrue("SlotPreference must bind its title TextView with max-lines plus ellipsize, or an explicit width/measurement bound, so a long custom label cannot widen the settings card.",
        boundedPreferenceTextIndex(slotPreference, "android.R.id.title") >= 0);
    assertTrue("SlotPreference must bind its summary TextView with max-lines plus ellipsize, or an explicit width/measurement bound, so a long phrase cannot widen the settings card.",
        boundedPreferenceTextIndex(slotPreference, "android.R.id.summary") >= 0);
  }


  @Test
  public void settings_list_has_side_padding_and_alternating_section_backgrounds()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String styleList = methodBody(source, "private void styleSettingsList()");
    String adapter = methodBody(source,
        "private static class SettingsListAdapter extends BaseAdapter");
    String nightStyles = readSource("res/values-night/styles.xml");
    String compactStyleList = styleList.replaceAll("\\s+", " ");
    String getView = methodBody(adapter, "public View getView");
    String rowWrapper = methodBody(adapter, "private FrameLayout rowWrapper");

    assertTrue("Settings list must leave horizontal padding at zero so adapter-owned card/content containers are the single row inset mechanism.",
        compactStyleList.contains("list.setPadding(0, list.getPaddingTop(), 0, list.getPaddingBottom())"));
    assertTrue("Settings rows must render inside a reusable FrameLayout wrapper owned by the styling adapter.",
        getView.contains("FrameLayout wrapper = rowWrapper(convertView, parent)")
        && getView.contains("return wrapper"));
    assertTrue("Settings rows must pass every row from the inner adapter through an adapter-owned card/content container.",
        getView.contains("_inner.getView(position, innerConvert, parent)")
        && contentContainerIndex(getView) >= 0);
    assertOrdered("The adapter-owned card/content container must apply the 20dp side inset before receiving the inner preference row, then be added to the outer wrapper.",
        contentSidePaddingIndex(getView),
        contentAddViewIndex(getView),
        wrapperAddContentIndex(getView),
        getView.indexOf("return wrapper"));
    assertFalse("The inner preference row itself must not be the side-padding target; custom preference rows can draw their own child content flush.",
        getView.contains("applySidePadding(view)")
        || getView.contains("view.setPadding(Math.max(view.getPaddingLeft(), _sidePadding)"));
    assertFalse("The row wrapper must not paint section backgrounds edge-to-edge.",
        getView.contains("wrapper.setBackgroundColor")
        || getView.contains("wrapper.setBackground(sectionBackground(")
        || getView.contains("wrapper.setBackgroundDrawable(sectionBackground("));
    assertFalse("The inner preference row must not own the rounded section background; custom row children need an adapter-owned padded card between themselves and the card background.",
        getView.contains("view.setBackground(sectionBackground(")
        || getView.contains("view.setBackgroundDrawable(sectionBackground(")
        || getView.contains("applySectionBackground(view, position)")
        || getView.contains("setSectionBackground(view, position)"));
    assertFalse("The inner preference row must be attached inside the card/content container, not directly to the outer wrapper.",
        getView.contains("wrapper.addView(view,"));
    assertTrue("The settings list must render through the section styling adapter.",
        styleList.contains("new SettingsListAdapter"));
    assertTrue("Rows must be grouped by PreferenceCategory so each section shares one background.",
        adapter.contains("instanceof PreferenceCategory")
        && adapter.contains("sectionColor(sectionFor(position))"));
    assertTrue("Light sections must alternate white and light gray.",
        adapter.contains("LIGHT_SECTION = 0xffffffff")
        && adapter.contains("LIGHT_SECTION_ALT = 0xfff4f5f7"));
    assertTrue("Dark sections must alternate two dark backgrounds.",
        adapter.contains("DARK_SECTION = 0xff121212")
        && adapter.contains("DARK_SECTION_ALT = 0xff1f1f1f"));
    assertTrue("Night mode settings must use a dark platform theme so text remains readable.",
        nightStyles.contains("name=\"settingsTheme\"")
        && nightStyles.contains("@android:style/Theme.Material"));
  }

  @Test
  public void settings_sections_use_square_card_backgrounds()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String adapter = methodBody(source,
        "private static class SettingsListAdapter extends BaseAdapter");
    String getView = methodBody(adapter, "public View getView");
    String compactGetView = getView.replaceAll("\\s+", " ");

    assertFalse("Settings sections must use a Drawable/Card background; direct setBackgroundColor paints full-width blocks.",
        compactGetView.contains(".setBackgroundColor(sectionColor("));
    assertFalse("Settings sections must not assign the section background directly to the inner preference row.",
        getView.contains("view.setBackground(sectionBackground(")
        || getView.contains("view.setBackgroundDrawable(sectionBackground(")
        || getView.contains("applySectionBackground(view, position)")
        || getView.contains("setSectionBackground(view, position)"));
    assertTrue("Settings sections must assign the square background to the adapter-owned card/content container.",
        cardBackgroundApplicationIndex(getView) >= 0
        && adapter.contains("GradientDrawable"));
    assertFalse("Settings section backgrounds must be square: no rounded corner radius helper or corner radii assignment.",
        adapter.contains("sectionCornerRadius")
        || adapter.contains("setCornerRadii")
        || adapter.contains("setCornerRadius"));
    assertTrue("Rows must still be grouped by PreferenceCategory so each section shares one color.",
        adapter.contains("instanceof PreferenceCategory")
        && adapter.contains("sectionColor(sectionFor(position))"));
  }

  @Test
  public void square_section_cards_have_no_external_section_gap()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String adapter = methodBody(source,
        "private static class SettingsListAdapter extends BaseAdapter");
    String getView = methodBody(adapter, "public View getView");

    assertTrue("The 20dp horizontal inset must remain on the adapter-owned card/content container, before the inner preference row is attached.",
        contentSidePaddingIndex(getView) >= 0);
    assertFalse("The row wrapper must not take horizontal side padding.",
        wrapperTakesSidePadding(getView));
    assertTrue("Square adjacent section cards must remove the external 10dp section gap; wrappers stay flush and only the card owns internal row padding.",
        getView.contains("wrapper.setPadding(0, 0, 0, 0)"));
    assertFalse("No wrapper padding or card layout margin should create vertical gaps between square sections.",
        externalSectionGapIndex(getView) >= 0);
  }


  @Test
  public void settings_adapter_insets_custom_and_nested_preference_rows_inside_cards()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String adapter = methodBody(source,
        "private static class SettingsListAdapter extends BaseAdapter");
    String getView = methodBody(adapter, "public View getView");

    assertTrue("Settings XML must include custom preference rows and nested PreferenceScreen rows so this contract protects the row types that can draw their own child content.",
        hasCustomPreference(settingsRoot())
        && settingsRoot().getElementsByTagName("PreferenceScreen").getLength() > 0);
    assertOrdered("SettingsListAdapter must structurally inset every inner preference row inside an adapter-owned card/content container before adding that container to the wrapper.",
        contentSidePaddingIndex(getView),
        contentAddViewIndex(getView),
        wrapperAddContentIndex(getView),
        getView.indexOf("return wrapper"));
    assertFalse("Custom and nested PreferenceScreen rows must not be added directly to the outer wrapper, because their own child content can draw flush.",
        getView.contains("wrapper.addView(view,"));
  }

  @Test
  public void settings_side_padding_is_strict_twenty_dp()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String settingsSidePadding = methodBody(source,
        "private int settingsSidePadding()");

    assertEquals("Settings side padding must be exactly 20dp on every launch path, with no screen-width scaling or alternate formula.",
        "return dp(20);",
        settingsSidePadding.replaceAll("\\s+", " ").trim());
  }

  @Test
  public void settings_row_wrappers_add_only_vertical_padding_at_section_boundaries()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String adapter = methodBody(source,
        "private static class SettingsListAdapter extends BaseAdapter");
    String getView = methodBody(adapter, "public View getView");
    String compactGetView = getView.replaceAll("\\s+", " ");

    assertFalse("Settings row wrappers must not apply the 20dp side inset horizontally; the card/content container owns that inset and wrapper-side insets would double-pad regular rows.",
        compactGetView.contains("wrapper.setPadding(_sidePadding,")
        || compactGetView.contains("wrapper.setPadding(Math.max(wrapper.getPaddingLeft(), _sidePadding)"));
    assertTrue("Settings row wrapper vertical padding must be driven by section-boundary helper logic, not inline constants in getView.",
        getView.contains("topPaddingFor(position)")
        && getView.contains("bottomPaddingFor(position)"));
    assertTrue("Settings rows must add 20dp top padding before the first item and before each PreferenceCategory heading.",
        adapter.contains("position == 0")
        && adapter.contains("getItem(position) instanceof PreferenceCategory"));
    assertTrue("Settings rows must add 20dp bottom padding before the next PreferenceCategory and after the final row.",
        adapter.contains("position + 1")
        && adapter.contains("getCount() - 1")
        && adapter.contains("getItem(position + 1) instanceof PreferenceCategory"));
  }

  @Test
  public void settings_row_wrapper_reuses_only_adapter_owned_wrappers()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String adapter = methodBody(source,
        "private static class SettingsListAdapter extends BaseAdapter");
    String rowWrapper = methodBody(adapter, "private FrameLayout rowWrapper");

    boolean hasOwnedWrapperSubclass = adapter.contains(" extends FrameLayout")
        && rowWrapper.contains("convertView instanceof")
        && !rowWrapper.contains("convertView instanceof FrameLayout");
    boolean hasOwnershipSentinel = rowWrapper.contains("getTag(");

    assertFalse("SettingsListAdapter must not recycle an arbitrary FrameLayout convertView; only wrappers created by this adapter may be reused.",
        rowWrapper.contains("convertView instanceof FrameLayout"));
    assertTrue("SettingsListAdapter must still recycle rows, but only after proving convertView is an adapter-owned wrapper via a private wrapper subclass or ownership sentinel.",
        rowWrapper.contains("return (FrameLayout)convertView")
        && (hasOwnedWrapperSubclass || hasOwnershipSentinel));
  }

  @Test
  public void settings_activity_reapplies_wrapped_adapter_after_lifecycle_reentry()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/SettingsActivity.java");
    String styleList = methodBody(source, "private void styleSettingsList()");

    boolean reappliesAfterResume = anyMethodBodyContains(source,
        "styleSettingsList()", "void onResume()");
    boolean reappliesAfterWindowFocus = anyMethodBodyContains(source,
        "styleSettingsList()", "void onWindowFocusChanged");
    boolean guardsExistingAdapter = styleList.contains("getAdapter()")
        && styleList.contains("SettingsListAdapter");

    assertTrue("Settings launched from the IME must restore the padded row-wrapper adapter after lifecycle re-entry, or guard styleSettingsList against losing it.",
        reappliesAfterResume || reappliesAfterWindowFocus || guardsExistingAdapter);
  }

  @Test
  public void snippet_slots_collapsed_toggle_declares_dropdown_affordance()
      throws Exception
  {
    String source = readSource(
        "srcs/juloo.keyboard2/snippets/SnippetSlotsPreference.java");
    String toggleConstructor = methodBody(source,
        "TogglePreference(Context context, boolean expanded)");

    assertTrue("The collapsed snippet slots row must declare a visible dropdown/clickable affordance via a custom row layout, widget layout, or icon instead of relying on plain preference text.",
        toggleConstructor.contains("setLayoutResource(")
        || toggleConstructor.contains("setWidgetLayoutResource(")
        || toggleConstructor.contains("setIcon("));
  }

  @Test
  public void delete_words_interval_setting_matches_configured_millisecond_key()
      throws Exception
  {
    Element preference = preferenceWithKey(settingsRoot(),
        "delete_words_interval");
    assertNotNull("Settings must expose the delete-words pacing control.",
        preference);
    assertEquals("Delete-words pacing must use the integer slider preference so values are stored as milliseconds.",
        "juloo.keyboard2.prefs.IntSlideBarPreference",
        preference.getTagName());
    assertEquals("Delete-words pacing UI must display millisecond units.",
        "%sms", preference.getAttribute("android:summary"));
    assertEquals("Delete-words pacing must keep a nonzero lower bound so swiping cannot emit unlimited word deletes.",
        "50", preference.getAttribute("min"));
    assertEquals("Delete-words pacing must keep an upper bound so the slider remains usable.",
        "1000", preference.getAttribute("max"));

    String config = readSource("srcs/juloo.keyboard2/Config.java");
    String key = "delete_words_interval";
    String configLookup = "_prefs.getInt(\"" + key + "\", ";
    int lookup = config.indexOf(configLookup);
    assertTrue("Config must read the same delete-words pacing preference key that settings writes.",
        lookup >= 0);
    int fallbackStart = lookup + configLookup.length();
    int fallbackEnd = config.indexOf(")", fallbackStart);
    assertTrue("Config delete-words interval lookup must include a fallback value.",
        fallbackEnd > fallbackStart);
    assertEquals("Settings and Config must agree on the stored key's initial millisecond value.",
        config.substring(fallbackStart, fallbackEnd).trim(),
        preference.getAttribute("android:defaultValue"));
  }

  private static String readSource(String path)
      throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)),
        StandardCharsets.UTF_8);
  }

  private static Element settingsRoot()
      throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",
        true);
    return factory.newDocumentBuilder()
      .parse(new File("res/xml/settings.xml"))
      .getDocumentElement();
  }

  private static Element preferenceWithKey(Element root, String key)
  {
    NodeList preferences = root.getElementsByTagName("*");
    for (int i = 0; i < preferences.getLength(); ++i)
    {
      Element preference = (Element)preferences.item(i);
      if (key.equals(preference.getAttribute("android:key")))
        return preference;
    }
    return null;
  }

  private static String methodBody(String source, String methodSignature)
  {
    String body = optionalMethodBody(source, methodSignature);
    assertFalse("Missing method or class: " + methodSignature,
        body.isEmpty());
    return body;
  }

  private static boolean anyMethodBodyContains(String source, String needle,
      String... methodSignatures)
  {
    for (String methodSignature : methodSignatures)
      if (optionalMethodBody(source, methodSignature).contains(needle))
        return true;
    return false;
  }

  private static String optionalMethodBody(String source, String methodSignature)
  {
    int start = source.indexOf(methodSignature);
    if (start < 0)
      return "";
    int open = source.indexOf('{', start);
    assertTrue(open >= 0);
    int depth = 0;
    for (int i = open; i < source.length(); ++i)
    {
      char c = source.charAt(i);
      if (c == '{')
        ++depth;
      else if (c == '}')
      {
        --depth;
        if (depth == 0)
          return source.substring(open + 1, i);
      }
    }
    fail("Unclosed method or class: " + methodSignature);
    return "";
  }

  private static boolean hasCustomPreference(Element root)
  {
    NodeList preferences = root.getElementsByTagName("*");
    for (int i = 0; i < preferences.getLength(); ++i)
      if (((Element)preferences.item(i)).getTagName()
          .startsWith("juloo.keyboard2.prefs."))
        return true;
    return false;
  }

  private static int contentContainerIndex(String getView)
  {
    return firstIndexOf(getView,
        "FrameLayout content",
        "FrameLayout card",
        "ViewGroup content",
        "ViewGroup card",
        "SettingsRowContent",
        "SettingsRowCard",
        "rowContent(",
        "rowCard(",
        "contentContainer(",
        "cardContainer(");
  }

  private static int contentSidePaddingIndex(String getView)
  {
    return firstIndexOf(getView,
        "content.setPadding(_sidePadding",
        "card.setPadding(_sidePadding",
        "content.setPadding(Math.max(content.getPaddingLeft(), _sidePadding)",
        "card.setPadding(Math.max(card.getPaddingLeft(), _sidePadding)");
  }

  private static int contentAddViewIndex(String getView)
  {
    return firstIndexOf(getView,
        "content.addView(view",
        "card.addView(view",
        "rowContent.addView(view",
        "rowCard.addView(view");
  }

  private static int wrapperAddContentIndex(String getView)
  {
    return firstIndexOf(getView,
        "wrapper.addView(content",
        "wrapper.addView(card",
        "wrapper.addView(rowContent",
        "wrapper.addView(rowCard");
  }

  private static int cardBackgroundApplicationIndex(String getView)
  {
    return firstIndexOf(getView,
        "content.setBackground(sectionBackground(",
        "card.setBackground(sectionBackground(",
        "content.setBackgroundDrawable(sectionBackground(",
        "card.setBackgroundDrawable(sectionBackground(",
        "applySectionBackground(content, position)",
        "applySectionBackground(card, position)",
        "setSectionBackground(content, position)",
        "setSectionBackground(card, position)");
  }

  private static int externalSectionGapIndex(String getView)
  {
    return firstIndexOf(getView,
        "wrapper.setPadding(0, topPaddingFor(position)",
        "wrapper.setPadding(0, sectionTopPaddingFor(position)",
        "wrapper.setPadding(0, topGapFor(position)",
        "wrapper.setPadding(0, sectionTopGapFor(position)",
        "wrapper.setPadding(wrapper.getPaddingLeft(), topPaddingFor(position)",
        "wrapper.setPadding(wrapper.getPaddingLeft(), sectionTopGapFor(position)",
        ".topMargin = topPaddingFor(position)",
        ".topMargin = sectionTopGapFor(position)",
        ".topMargin = topGapFor(position)",
        ".bottomMargin = bottomPaddingFor(position)",
        ".bottomMargin = sectionBottomGapFor(position)",
        ".bottomMargin = bottomGapFor(position)",
        "setMargins(0, topPaddingFor(position), 0, bottomPaddingFor(position))",
        "setMargins(0, sectionTopGapFor(position), 0, sectionBottomGapFor(position))",
        "setMargins(0, topGapFor(position), 0, bottomGapFor(position))");
  }


  private static boolean wrapperTakesSidePadding(String getView)
  {
    return firstIndexOf(getView,
        "wrapper.setPadding(_sidePadding,",
        "wrapper.setPadding(Math.max(wrapper.getPaddingLeft(), _sidePadding)") >= 0;
  }

  private static int boundedPreferenceTextIndex(String source, String textId)
  {
    int id = source.indexOf(textId);
    while (id >= 0)
    {
      int lineStart = source.lastIndexOf('\n', id);
      int lineEnd = source.indexOf('\n', id);
      if (lineStart < 0)
        lineStart = 0;
      if (lineEnd < 0)
        lineEnd = source.length();
      String line = source.substring(lineStart, lineEnd);
      String window = source.substring(lineStart,
          Math.min(source.length(), lineEnd + 900));
      String local = textViewLocalName(line);
      if (isBoundedTextWindow(window, local)
          || (callsTextBoundingHelper(window, local)
            && hasTextBoundingOperations(source)))
        return id;
      id = source.indexOf(textId, id + textId.length());
    }
    return -1;
  }

  private static String textViewLocalName(String line)
  {
    Matcher matcher = Pattern.compile(
        "(?:TextView\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*[^;]*findViewById")
      .matcher(line);
    if (matcher.find())
      return matcher.group(1);
    return null;
  }

  private static boolean isBoundedTextWindow(String window, String local)
  {
    String receiver = local == null ? "" : local + ".";
    boolean lineLimit = window.contains(receiver + "setMaxLines(")
        || window.contains(receiver + "setSingleLine(");
    boolean ellipsize = window.contains(receiver + "setEllipsize(");
    boolean explicitWidth = window.contains(receiver + "setMaxWidth(")
        || window.contains(receiver + "setWidth(")
        || window.contains("MeasureSpec.AT_MOST")
        || window.contains(".width = 0");
    return (lineLimit && ellipsize) || explicitWidth;
  }

  private static boolean callsTextBoundingHelper(String window, String local)
  {
    if (local == null)
      return false;
    Matcher matcher = Pattern.compile(
        "(bound|limit|ellipsize|constrain)[A-Za-z0-9_]*\\([^;]*\\b"
        + Pattern.quote(local) + "\\b")
      .matcher(window);
    return matcher.find();
  }

  private static boolean hasTextBoundingOperations(String source)
  {
    boolean lineLimit = source.contains("setMaxLines(")
        || source.contains("setSingleLine(");
    boolean ellipsize = source.contains("setEllipsize(");
    boolean explicitWidth = source.contains("setMaxWidth(")
        || source.contains("setWidth(")
        || source.contains("MeasureSpec.AT_MOST")
        || source.contains(".width = 0");
    return (lineLimit && ellipsize) || explicitWidth;
  }



  private static int firstIndexOf(String source, String... needles)
  {
    int first = -1;
    for (String needle : needles)
    {
      int index = source.indexOf(needle);
      if (index >= 0 && (first < 0 || index < first))
        first = index;
    }
    return first;
  }


  private static void assertOrdered(String message, int... indexes)
  {
    int previous = -1;
    for (int index : indexes)
    {
      assertTrue(message + " missing index", index >= 0);
      assertTrue(message + " out of order", index > previous);
      previous = index;
    }
  }
}
