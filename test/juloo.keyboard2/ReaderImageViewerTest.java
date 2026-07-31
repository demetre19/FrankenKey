package juloo.keyboard2;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderImageViewerTest
{
  private Bitmap _bitmap;
  private ReaderImageViewer.ZoomImageView _view;

  @Before
  public void setUp()
  {
    _bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
    _view = new ReaderImageViewer.ZoomImageView(
        RuntimeEnvironment.getApplication());
    _view.setImageBitmap(_bitmap);
    int width = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY);
    int height = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
    _view.measure(width, height);
    _view.layout(0, 0, 200, 300);
  }

  @After
  public void tearDown()
  {
    _view.setImageDrawable(null);
    _bitmap.recycle();
  }

  @Test
  public void image_fits_view_and_double_tap_toggles_zoom()
  {
    RectF fit = displayedBounds();
    assertEquals("The full image fills the available width.", 0f, fit.left, 0.01f);
    assertEquals("The full image fills the available width.", 200f, fit.right, 0.01f);
    assertEquals("The fitted image remains vertically centered.", 100f, fit.top, 0.01f);
    assertEquals("The fitted image remains vertically centered.", 200f, fit.bottom, 0.01f);

    doubleTap(100f, 150f);
    assertTrue("Double-tap enlarges the image for closer inspection.",
        displayedBounds().width() > 200f);

    doubleTap(100f, 150f);
    RectF reset = displayedBounds();
    assertEquals("A second double-tap restores the fitted width.",
        200f, reset.width(), 0.01f);
    assertEquals("Reset keeps the image centered.", 150f, reset.centerY(), 0.01f);
  }

  @Test
  public void zoomed_image_allows_bounded_panning()
  {
    doubleTap(100f, 150f);
    RectF zoomed = displayedBounds();
    assertTrue("Zoom enlarges the image.", zoomed.width() > 200f);
    float beforeDrag = zoomed.left;

    long start = SystemClock.uptimeMillis();
    onePointer(start, start, MotionEvent.ACTION_DOWN, 100f, 150f);
    onePointer(start, start + 40, MotionEvent.ACTION_MOVE, 60f, 150f);
    onePointer(start, start + 60, MotionEvent.ACTION_UP, 60f, 150f);

    RectF panned = displayedBounds();
    assertTrue("A zoomed image can be panned horizontally.",
        panned.left < beforeDrag);
    assertTrue("Panning cannot expose empty space at the left edge.",
        panned.left <= 0.01f);
    assertTrue("Panning cannot expose empty space at the right edge.",
        panned.right >= 199.99f);
  }

  private RectF displayedBounds()
  {
    RectF bounds = new RectF(0f, 0f, _bitmap.getWidth(), _bitmap.getHeight());
    _view.getImageMatrix().mapRect(bounds);
    return bounds;
  }

  private void doubleTap(float x, float y)
  {
    long start = SystemClock.uptimeMillis();
    onePointer(start, start, MotionEvent.ACTION_DOWN, x, y);
    onePointer(start, start + 30, MotionEvent.ACTION_UP, x, y);
    onePointer(start + 80, start + 80, MotionEvent.ACTION_DOWN, x, y);
    onePointer(start + 80, start + 110, MotionEvent.ACTION_UP, x, y);
  }

  private void onePointer(long downTime, long eventTime, int action,
      float x, float y)
  {
    MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
        x, y, 0);
    _view.onTouchEvent(event);
    event.recycle();
  }

}
