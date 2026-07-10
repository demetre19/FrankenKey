package juloo.keyboard2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Demonstrates the four authored clean-layout Spacebar corner actions. */
public final class SpacebarGestureView extends View implements Runnable
{
  static final String[] ICONS = { "\uE017", "⚙", "\uE001", "gif" };
  static final String[] RESULTS = { "Clipboard", "Coding mode", "Emoji", "GIF" };
  static final int[] X_DIRECTIONS = { -1, 1, -1, 1 };
  static final int[] Y_DIRECTIONS = { -1, -1, 1, 1 };

  private final Paint _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF _key = new RectF();
  private long _animation_start;

  public SpacebarGestureView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _paint.setTypeface(Theme.getKeyFont(context));
    setContentDescription(context.getString(R.string.launcher_spacebar_accessibility));
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    _animation_start = SystemClock.uptimeMillis();
    post(this);
  }

  @Override
  protected void onDetachedFromWindow()
  {
    removeCallbacks(this);
    super.onDetachedFromWindow();
  }

  @Override
  public void run()
  {
    invalidate();
    if (animationsEnabled())
      postDelayed(this, 16);
  }

  private boolean animationsEnabled()
  {
    return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);
    float density = getResources().getDisplayMetrics().density;
    float centerX = getWidth() / 2f;
    float keyWidth = Math.min(getWidth() - 48f * density, 360f * density);
    float keyHeight = 118f * density;
    float keyTop = 28f * density;
    _key.set(centerX - keyWidth / 2f, keyTop,
        centerX + keyWidth / 2f, keyTop + keyHeight);

    _paint.setStyle(Paint.Style.FILL);
    _paint.setColor(Color.rgb(47, 47, 47));
    canvas.drawRoundRect(_key, 14f * density, 14f * density, _paint);
    _paint.setStyle(Paint.Style.STROKE);
    _paint.setStrokeWidth(1f * density);
    _paint.setColor(Color.rgb(91, 101, 112));
    canvas.drawRoundRect(_key, 14f * density, 14f * density, _paint);

    float insetX = 27f * density;
    float insetY = 29f * density;
    drawIcon(canvas, ICONS[0], _key.left + insetX, _key.top + insetY,
        22f * density, true);
    drawIcon(canvas, ICONS[1], _key.right - insetX, _key.top + insetY,
        19f * density, false);
    drawIcon(canvas, ICONS[2], _key.left + insetX, _key.bottom - insetY,
        22f * density, true);
    drawIcon(canvas, ICONS[3], _key.right - insetX, _key.bottom - insetY,
        15f * density, false);

    long elapsed = animationsEnabled()
      ? (SystemClock.uptimeMillis() - _animation_start) % 4800L : 0;
    int action = (int)(elapsed / 1200L);
    float actionTime = elapsed % 1200L;
    float progress = Math.min(1f, actionTime / 300f);
    float startX = centerX;
    float startY = _key.centerY();
    float endX = centerX + X_DIRECTIONS[action] * (keyWidth * .38f);
    float endY = startY + Y_DIRECTIONS[action] * (keyHeight * .36f);

    _paint.setTypeface(null);
    _paint.setStyle(Paint.Style.STROKE);
    _paint.setStrokeCap(Paint.Cap.ROUND);
    _paint.setStrokeWidth(4f * density);
    _paint.setColor(Color.rgb(85, 214, 190));
    canvas.drawLine(startX, startY,
        startX + (endX - startX) * progress,
        startY + (endY - startY) * progress, _paint);
    _paint.setStyle(Paint.Style.FILL);
    float fingerX = startX + (endX - startX) * progress;
    float fingerY = startY + (endY - startY) * progress;
    canvas.drawCircle(fingerX, fingerY, 9f * density, _paint);

    _paint.setTextAlign(Paint.Align.CENTER);
    _paint.setTextSize(16f * density);
    _paint.setTypeface(null);
    _paint.setColor(actionTime >= 300f ? Color.rgb(140, 233, 215) : Color.LTGRAY);
    canvas.drawText(RESULTS[action], centerX, _key.bottom + 39f * density, _paint);
  }

  private void drawIcon(Canvas canvas, String icon, float x, float y,
      float size, boolean useKeyFont)
  {
    _paint.setStyle(Paint.Style.FILL);
    _paint.setTypeface(useKeyFont ? Theme.getKeyFont(getContext()) : null);
    _paint.setTextAlign(Paint.Align.CENTER);
    _paint.setTextSize(size);
    _paint.setColor(Color.WHITE);
    Paint.FontMetrics fm = _paint.getFontMetrics();
    canvas.drawText(icon, x, y - (fm.ascent + fm.descent) / 2f, _paint);
  }
}
