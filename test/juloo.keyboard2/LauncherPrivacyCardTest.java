package juloo.keyboard2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
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
