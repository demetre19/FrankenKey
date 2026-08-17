package juloo.keyboard2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class Reader3dActivityTest
{
  @Test
  public void temporary_article_uses_private_handoff_and_locked_local_webview()
  {
    ActivityController<Activity> hostController =
      Robolectric.buildActivity(Activity.class).create().start().resume();
    Activity host = hostController.get();
    String privateText = "Private article text that must not cross Binder.";

    assertTrue(Reader3dActivity.start(host, null, "Private article",
          privateText));
    Intent intent = shadowOf(host).getNextStartedActivity();
    assertNotNull(intent);
    Bundle extras = intent.getExtras();
    assertNotNull(extras);
    for (String key : extras.keySet())
      assertNotEquals("Article text stays in an app-private cache handoff.",
          privateText, String.valueOf(extras.get(key)));

    ActivityController<Reader3dActivity> controller =
      Robolectric.buildActivity(Reader3dActivity.class, intent)
      .create().start().resume().visible();
    Reader3dActivity activity = controller.get();
    ViewGroup content = activity.findViewById(android.R.id.content);
    assertEquals(1, content.getChildCount());
    assertTrue(content.getChildAt(0) instanceof WebView);
    WebSettings settings = ((WebView)content.getChildAt(0)).getSettings();
    assertTrue(settings.getJavaScriptEnabled());
    assertTrue(settings.getDomStorageEnabled());
    assertFalse(settings.getAllowFileAccess());
    assertFalse(settings.getAllowContentAccess());
    assertFalse(settings.getJavaScriptCanOpenWindowsAutomatically());
    assertFalse(settings.supportMultipleWindows());
    assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,
        settings.getMixedContentMode());

    controller.pause().stop().destroy();
    hostController.pause().stop().destroy();
  }

  @Test
  public void document_preserves_raw_word_and_epub_chapter_coordinates()
  {
    ReaderLibrary.Item item = new ReaderLibrary.Item(
        "book", "Book", ReaderLibrary.SourceType.EPUB, null,
        "application/epub+zip", "Author", "en", 1L, 1L, 1L,
        "unit:1:0", 0.4f, false, "hash",
        ReaderLibrary.ImportState.READY, null, Arrays.asList(
          new ReaderLibrary.ContentUnit(0, "chapter", "one two", "en", "c1"),
          new ReaderLibrary.ContentUnit(1, "chapter", "three four five", "en", "c2")));

    Reader3dActivity.ReaderDocument document =
      Reader3dActivity.ReaderDocument.fromItem(item);

    assertEquals("one two\n\nthree four five", document.text);
    assertEquals(5, document.rawWordStarts.length);
    assertEquals(2, document.progressRawWordIndex);
    assertEquals("[{\"start\":0,\"end\":2},{\"start\":2,\"end\":5}]",
        document.chapterRangesJson);
    assertEquals(document.text.indexOf("three"),
        document.characterOffsetForRawWord(2));
    assertEquals(1, document.unitForDocumentOffset(
          document.characterOffsetForRawWord(2)).index);
  }

  @Test
  public void packaged_surface_keeps_private_drive_mobile_settings_exact()
      throws Exception
  {
    String html = readAsset("reader_3d.html");
    assertTrue(html.contains("default-src 'none'"));
    assertTrue(html.contains("connect-src 'none'"));
    assertTrue(html.contains("for(var start=0;start<length;start+=32768)"));
    assertTrue(html.contains("id=\"dm-reader-settings\""));
    assertTrue(html.contains("function bindButton(button,action)"));
    assertTrue(html.contains("bindButton(root.querySelector('#dm-reader-close'),closeReader)"));
    assertTrue(html.contains("bindButton(root.querySelector('#dm-reader-settings'),showSettings)"));
    assertTrue(html.contains("bindButton(root.querySelector('#dm-reader-bookmarks'),showBookmarks)"));
    assertTrue(html.contains("id=\"dm-reader-ai\" aria-label=\"Article AI\""));
    assertTrue(html.contains("ai.hidden=!Native.canOpenReaderAi()"));
    assertTrue(html.contains("if(!ai.hidden)bindButton(ai,function(){Native.openReaderAi()})"));
    assertTrue(html.contains("dm-reader-settings--previewing .dm-confirm__box{opacity:.01}"));
    assertTrue(html.contains(".dm-confirm{position:fixed;inset:0;background:rgba(0,0,0,.7);z-index:500"));
    assertTrue(html.contains(".dm-reader-settings{z-index:400}"));
    assertTrue(html.contains("function startPreview(key){if(['backgroundIntensity','backgroundHue','backgroundSize'].indexOf(key)!==-1)"));
    assertTrue(html.contains("input.addEventListener('pointerdown',function(){startPreview(key)});"));
    assertTrue(html.contains("swatch=el('button',{class:'dm-reader-settings__swatch',type:'button'"));
    assertTrue(html.contains("swatch.addEventListener('click',function(){input.click()})"));
    assertTrue(html.contains("--dm-surface:#111;--dm-surface-2:#1a1a1a;--dm-text:#d1d5db"));
    assertTrue(html.contains(".dm-reader{height:100%;display:flex;flex-direction:column;background:#000"));
    assertTrue(html.contains("@media(orientation:portrait){.dm-reader{padding-top:max(60px,var(--dm-safe-top))}.dm-reader__bg{top:max(60px,var(--dm-safe-top))}}"));
    assertTrue(html.contains("padding-right:max(50px,var(--dm-safe-right))"));
    assertTrue(html.contains(".dm-reader__bg{top:max(28px,var(--dm-safe-top))}"));
    assertTrue(html.contains(".dm-reader__chapter-cue-top{position:absolute;z-index:100"));
    assertTrue(html.contains(".dm-reader .dm-reader__rail--right{right:max(50px,var(--dm-safe-right))}"));
    assertTrue(html.contains(".dm-reader__rail--right{right:0;width:120px}"));
    assertTrue(html.contains(".dm-reader.dm-reader--playing .dm-reader__stage::before,.dm-reader.dm-reader--playing .dm-reader__stage::after"));
    assertTrue(html.contains("height:24%;z-index:1;pointer-events:none"));
    assertTrue(html.contains("linear-gradient(to bottom,rgba(0,0,0,.82),transparent)"));
    assertTrue(html.contains("linear-gradient(to top,rgba(0,0,0,.82),transparent)"));
    assertFalse(html.contains(".dm-reader.dm-reader--playing .dm-reader__bg{opacity:0"));
    assertTrue(html.contains(".dm-reader.dm-reader--playing .dm-reader__bar>*{opacity:0;visibility:hidden;pointer-events:none}"));
    assertTrue(html.contains(".dm-reader.dm-reader--playing .dm-reader__status span:last-child{opacity:0;visibility:hidden}"));
    assertFalse(html.contains("brainwave"));
    assertFalse(html.contains("Brainwave"));
    assertFalse(html.contains("window.reader3dOpenSettings"));
    assertTrue(html.contains("if(e.touches.length!==2)return"));
    assertTrue(html.contains("if(pinched)state.ignoreTap=Date.now()+450"));
    assertFalse(html.contains("touchend',function(){pinching=false;state.ignoreTap"));
    assertTrue(html.contains("addEventListener('pointercancel'"));
    assertTrue(html.contains("addEventListener('lostpointercapture'"));
    assertTrue(html.contains("settings.mode='3d'"));
    assertTrue(html.contains("var defaults={settingsVersion:4,mode:'plain',theme:'dark',wpm:300,wordsAtTime:1,adaptiveShortWords:false,shortWordMinLength:4,shortWordMaxCombinedLength:10,chapterCues:false,chapterCueFontSize:16,chapterCueFontColor:'#d1d5db',chapterCueFontOpacity:55,chapterCueSemantic:true,chapterCueLineOpacity:35,chapterCueLineThickness:2,chapterCuePosition:'top',fontSize:54,fontFamily:'system',letterSpacing:2,wordColor:'#f3f4f6',focusLetter:true,focusColor:'#ffffff',focusPosition:'middle',pinMode:true,focusLines:false,focusLineWidth:2,focusLineGap:12,crosshairMode:false,microPauses:true,longWordPause:true,punctuationFactor:1,sentenceEndFactor:1.5,longWordThreshold:8,longWordFactor:.5,textVignette:false,textVignetteWidth:300,textVignetteIntensity:50,background:'none',backgroundIntensity:55,backgroundHue:0,backgroundSize:30,stereoSpacing:120,swapRailControls:false}"));

    assertTrue(html.contains(
          ".dm-reader-settings__section:nth-child(odd){background:#15191e}"));
    assertTrue(html.contains(
          ".dm-reader-settings__section:nth-child(even){background:#0b0d10}"));
    String[] semanticGroups = {
      "[\"Reader setup\",['mode','theme','swapRailControls']]",
      "[\"Pace & grouping\",['wpm','wordsAtTime','adaptiveShortWords','shortWordMinLength','shortWordMaxCombinedLength']]",
      "[\"Chapter progress\",['chapterCues','chapterCuePosition','chapterCueFontSize','chapterCueFontColor','chapterCueFontOpacity','chapterCueSemantic','chapterCueLineOpacity','chapterCueLineThickness']]",
      "[\"Text style\",['fontSize','fontFamily','letterSpacing','wordColor']]",
      "[\"Focus guide\",['focusLetter','focusColor','focusPosition','pinMode','focusLines','crosshairMode','focusLineWidth','focusLineGap']]",
      "[\"Reading rhythm\",['microPauses','longWordPause','punctuationFactor','sentenceEndFactor','longWordThreshold','longWordFactor']]",
      "[\"Background & depth\",['textVignette','textVignetteWidth','textVignetteIntensity','background','backgroundIntensity','backgroundHue','backgroundSize','stereoSpacing']]"
    };
    for (String group : semanticGroups)
      assertTrue("Missing semantic settings group: " + group,
          html.contains(group));

    String[] exactControls = {
      "row('Mode',select('mode'", "row('Theme',select('theme'",
      "row('Swap side controls',check('swapRailControls')",
      "row('Reading speed',range('wpm',50,1500,25)",
      "row('Words at a time',range('wordsAtTime',1,5,1)",
      "row('Group short words',check('adaptiveShortWords')",
      "row('Group words shorter than',range('shortWordMinLength',1,20,1)",
      "row('Combined character limit',range('shortWordMaxCombinedLength',2,40,1)",
      "row('Chapter cues',check('chapterCues')",
      "row('Cue position',select('chapterCuePosition'",
      "row('Cue text size',range('chapterCueFontSize',0,48,1)",
      "row('Cue text colour',color('chapterCueFontColor')",
      "row('Cue text opacity',range('chapterCueFontOpacity',0,100,5)",
      "row('Colour by progress',check('chapterCueSemantic')",
      "row('Progress line opacity',range('chapterCueLineOpacity',0,100,5)",
      "row('Progress line thickness',range('chapterCueLineThickness',1,12,1)",
      "row('Font size',range('fontSize',16,220,4)",
      "row('Font family',select('fontFamily'",
      "row('Letter spacing',range('letterSpacing',0,20,1)",
      "row('Word colour',color('wordColor')", "row('Focus letter',check('focusLetter')",
      "row('Focus colour',color('focusColor')", "row('Focus position',select('focusPosition'",
      "row('Pin focus letter',check('pinMode')", "row('Focus lines',check('focusLines')",
      "row('Crosshair',check('crosshairMode')",
      "row('Focus line width',range('focusLineWidth',1,6,1)",
      "row('Focus line gap',range('focusLineGap',4,30,2)",
      "row('Micro-pauses',check('microPauses')", "row('Long-word pause',check('longWordPause')",
      "row('Punctuation pause',range('punctuationFactor',0,3,.1)",
      "row('Sentence-end pause',range('sentenceEndFactor',0,4,.1)",
      "row('Long-word threshold',range('longWordThreshold',4,18,1)",
      "row('Long-word pause strength',range('longWordFactor',0,3,.1)",
      "row('Text vignette',check('textVignette')",
      "row('Vignette width',range('textVignetteWidth',100,600,10)",
      "row('Vignette intensity',range('textVignetteIntensity',0,100,5)",
      "row('Background',select('background'",
      "row('Background intensity',range('backgroundIntensity',0,100,5)",
      "row('Background hue',range('backgroundHue',0,360,10)",
      "row('Background size',range('backgroundSize',10,160,5)",
      "row('Stereo spacing',range('stereoSpacing',20,1200,10)"
    };
    for (String control : exactControls)
      assertTrue("Missing exact mobile setting: " + control,
          html.contains(control));
  }

  private static String readAsset(String name) throws Exception
  {
    try (InputStream input = RuntimeEnvironment.getApplication()
        .getAssets().open(name);
        InputStreamReader reader = new InputStreamReader(input,
          StandardCharsets.UTF_8))
    {
      char[] buffer = new char[8192];
      StringBuilder result = new StringBuilder();
      int count;
      while ((count = reader.read(buffer)) != -1)
        result.append(buffer, 0, count);
      return result.toString();
    }
  }
}
