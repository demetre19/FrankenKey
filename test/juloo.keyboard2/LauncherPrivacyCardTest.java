package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowAlertDialog;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class LauncherPrivacyCardTest
{
  @Test
  public void private_by_design_is_the_first_content_card()
  {
    Context context = RuntimeEnvironment.getApplication();
    ScrollView screen = (ScrollView)LayoutInflater.from(context)
      .inflate(R.layout.launcher_activity, null);
    ViewGroup content = (ViewGroup)screen.getChildAt(0);
    View first = content.getChildAt(0);

    assertTrue("The privacy promise must be the first visible content block.",
        first instanceof ViewGroup);
    TextView title = (TextView)((ViewGroup)first).getChildAt(0);
    assertEquals(context.getString(R.string.launcher_privacy_title),
        title.getText().toString());
  }

  @Test
  public void adaptive_learning_cards_keep_an_eight_dp_gap()
  {
    Context context = RuntimeEnvironment.getApplication();
    ScrollView screen = (ScrollView)LayoutInflater.from(context)
      .inflate(R.layout.launcher_activity, null);
    TextView teachForget = findText(screen,
        context.getString(R.string.launcher_teach_forget_summary));
    int expected = Math.round(8f *
        context.getResources().getDisplayMetrics().density);

    assertNotNull(teachForget);
    assertEquals("Adjacent adaptive-learning cards must not visually merge.",
        expected, ((ViewGroup.MarginLayoutParams)
          teachForget.getLayoutParams()).topMargin);
  }

  @Test
  public void shortcut_map_button_opens_the_complete_compact_guide()
  {
    LauncherActivity activity = Robolectric.buildActivity(
        LauncherActivity.class).create().get();

    assertTrue("The first-launch shortcut-map button must be reachable.",
        activity.findViewById(R.id.launcher_shortcut_map).performClick());
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();

    assertNotNull("The shortcut map must open in a modal.", dialog);
    assertTrue(dialog.isShowing());
    assertTrue("The modal must document reversed Enter behavior.",
        ((TextView)dialog.findViewById(R.id.launcher_shortcuts_typing))
          .getText().toString().contains("Shift + Enter — New line"));
    String editing = ((TextView)dialog.findViewById(
        R.id.launcher_shortcuts_editing)).getText().toString();
    assertTrue("The modal must document Shift+G selection.", editing.contains(
          "Shift + G + swipe"));
    assertTrue("The modal must document Z northwest Select all.", editing.contains(
          "Z ↖ — Select all"));
    assertTrue("The modal must document Backspace left-swipe deletion.",
        editing.contains("Backspace ←"));
    assertTrue("The modal must document four exact corrections.",
        ((TextView)dialog.findViewById(R.id.launcher_shortcuts_learning))
          .getText().toString().contains("four times"));
  }

  private static TextView findText(View view, String text)
  {
    if (view instanceof TextView &&
        text.contentEquals(((TextView)view).getText()))
      return (TextView)view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++)
      {
        TextView result = findText(((ViewGroup)view).getChildAt(i), text);
        if (result != null)
          return result;
      }
    return null;
  }
}
