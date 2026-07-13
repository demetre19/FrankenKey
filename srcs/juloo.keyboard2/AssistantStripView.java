package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A compact, candidate-row-height surface for grammar and voice actions. */
public final class AssistantStripView extends LinearLayout
{
  private TextView _message;
  private TextView _primary;
  private TextView _dismiss;
  private Runnable _primaryAction;
  private Runnable _dismissAction;

  public AssistantStripView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _message = findViewById(R.id.assistant_message);
    _primary = findViewById(R.id.assistant_primary);
    _dismiss = findViewById(R.id.assistant_dismiss);
    _primary.setOnClickListener(_view -> run(_primaryAction));
    _dismiss.setOnClickListener(_view -> run(_dismissAction));
    clear();
  }

  public void refresh_config(Config config)
  {
    float height = config.keyboard_rows_height_pixels
      * (1 - config.key_vertical_margin);
    ViewGroup.LayoutParams params = getLayoutParams();
    params.height = (int)height;
    setLayoutParams(params);
  }

  public void show(String message, String primaryLabel,
      Runnable primaryAction, Runnable dismissAction)
  {
    _message.setText(message);
    _primary.setText(primaryLabel);
    _primaryAction = primaryAction;
    _dismissAction = dismissAction;
    _primary.setVisibility(primaryAction == null ? View.GONE : View.VISIBLE);
    _dismiss.setVisibility(dismissAction == null ? View.GONE : View.VISIBLE);
    setVisibility(View.VISIBLE);
  }

  public void update_message(String message)
  {
    if (is_showing())
      _message.setText(message);
  }

  public void clear()
  {
    _primaryAction = null;
    _dismissAction = null;
    if (_message != null)
      _message.setText("");
    if (_primary != null)
      _primary.setVisibility(View.GONE);
    if (_dismiss != null)
      _dismiss.setVisibility(View.GONE);
    setVisibility(View.GONE);
  }

  public boolean is_showing()
  {
    return getVisibility() == View.VISIBLE;
  }

  private static void run(Runnable action)
  {
    if (action != null)
      action.run();
  }
}
