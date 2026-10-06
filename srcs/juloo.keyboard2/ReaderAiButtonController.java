package juloo.keyboard2;

import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;

/**
 * Touch controller for the centered Reader AI omnibutton: a short tap
 * dispatches the action assigned to the tap sector; a swipe past the
 * configured swipe distance dispatches the action assigned to that
 * direction's sector.
 */
final class ReaderAiButtonController implements View.OnTouchListener
{
  interface Dispatcher
  {
    void dispatch(ReaderAiAction action);
  }

  private final SharedPreferences _prefs;
  private final float _thresholdPx;
  private final Dispatcher _dispatcher;
  private float _downX;
  private float _downY;
  private boolean _tracking;

  ReaderAiButtonController(SharedPreferences prefs, float thresholdPx,
      Dispatcher dispatcher)
  {
    _prefs = prefs;
    _thresholdPx = thresholdPx;
    _dispatcher = dispatcher;
  }

  @Override
  public boolean onTouch(View view, MotionEvent event)
  {
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
        _downX = event.getRawX();
        _downY = event.getRawY();
        _tracking = true;
        return true;
      case MotionEvent.ACTION_CANCEL:
        _tracking = false;
        return true;
      case MotionEvent.ACTION_UP:
        if (!_tracking)
          return true;
        _tracking = false;
        float dx = event.getRawX() - _downX;
        float dy = event.getRawY() - _downY;
        if (Math.hypot(dx, dy) < _thresholdPx)
        {
          view.performClick();
          _dispatcher.dispatch(ReaderAiAction.actionFor(_prefs,
              ReaderAiAction.Sector.TAP));
          return true;
        }
        ReaderAiAction.Sector sector = ReaderAiAction.sectorFor(dx, dy);
        _dispatcher.dispatch(ReaderAiAction.actionFor(_prefs, sector));
        return true;
      default:
        return true;
    }
  }
}
