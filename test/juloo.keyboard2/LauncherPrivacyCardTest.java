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
}
