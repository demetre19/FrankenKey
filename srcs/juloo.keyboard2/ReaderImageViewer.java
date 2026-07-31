package juloo.keyboard2;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Fullscreen, app-private viewer for retained Reader article images. */
final class ReaderImageViewer
{
  private static final long MAX_DECODED_PIXELS = 12_000_000L;
  private static final int MAX_DECODED_DIMENSION = 4096;

  private ReaderImageViewer() {}

  static void show(Activity activity, File file)
  {
    Dialog dialog = new Dialog(activity,
        android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    FrameLayout root = new FrameLayout(activity);
    root.setBackgroundColor(Color.BLACK);
    root.setFitsSystemWindows(true);

    ZoomImageView image = new ZoomImageView(activity);
    image.setContentDescription(
        activity.getString(R.string.reader_image_content_description));
    root.addView(image, new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT));

    ProgressBar progress = new ProgressBar(activity);
    FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
        dp(activity, 48), dp(activity, 48), Gravity.CENTER);
    root.addView(progress, progressParams);

    ImageButton close = new ImageButton(activity);
    close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
    close.setColorFilter(Color.WHITE);
    close.setBackgroundColor(0x99000000);
    close.setContentDescription(activity.getString(R.string.reader_image_close));
    close.setPadding(dp(activity, 12), dp(activity, 12),
        dp(activity, 12), dp(activity, 12));
    close.setOnClickListener(_view -> dialog.dismiss());
    FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
        dp(activity, 48), dp(activity, 48), Gravity.TOP | Gravity.START);
    closeParams.setMargins(dp(activity, 8), dp(activity, 8),
        dp(activity, 8), dp(activity, 8));
    root.addView(close, closeParams);

    TextView hint = new TextView(activity);
    hint.setText(R.string.reader_image_zoom_hint);
    hint.setTextColor(Color.WHITE);
    hint.setTextSize(14);
    hint.setGravity(Gravity.CENTER);
    hint.setBackgroundColor(0x99000000);
    hint.setPadding(dp(activity, 12), dp(activity, 8),
        dp(activity, 12), dp(activity, 8));
    hint.setVisibility(View.GONE);
    FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    hintParams.setMargins(dp(activity, 12), dp(activity, 12),
        dp(activity, 12), dp(activity, 20));
    root.addView(hint, hintParams);

    AtomicBoolean dismissed = new AtomicBoolean();
    AtomicReference<Bitmap> loadedBitmap = new AtomicReference<>();
    dialog.setOnDismissListener(_dialog ->
    {
      dismissed.set(true);
      image.setImageDrawable(null);
      Bitmap bitmap = loadedBitmap.getAndSet(null);
      if (bitmap != null && !bitmap.isRecycled())
        bitmap.recycle();
    });
    dialog.setContentView(root);
    dialog.show();
    Window window = dialog.getWindow();
    if (window != null)
    {
      window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
      window.setStatusBarColor(Color.BLACK);
      window.setNavigationBarColor(Color.BLACK);
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT);
    }

    new Thread(() ->
    {
      Bitmap bitmap = decode(file);
      activity.runOnUiThread(() ->
      {
        if (dismissed.get() || activity.isFinishing() ||
            activity.isDestroyed() || !dialog.isShowing())
        {
          if (bitmap != null && !bitmap.isRecycled())
            bitmap.recycle();
          return;
        }
        if (bitmap == null)
        {
          dialog.dismiss();
          Toast.makeText(activity, R.string.reader_image_unavailable,
              Toast.LENGTH_SHORT).show();
          return;
        }
        loadedBitmap.set(bitmap);
        image.setImageBitmap(bitmap);
        progress.setVisibility(View.GONE);
        hint.setVisibility(View.VISIBLE);
      });
    }, "FrankenKey Reader image").start();
  }

  static Bitmap decode(File file)
  {
    if (file == null || !file.isFile())
      return null;
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getPath(), bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
      return null;
    int sample = 1;
    while (bounds.outWidth / sample > MAX_DECODED_DIMENSION ||
        bounds.outHeight / sample > MAX_DECODED_DIMENSION ||
        (long)(bounds.outWidth / sample) *
          (bounds.outHeight / sample) > MAX_DECODED_PIXELS)
      sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    return BitmapFactory.decodeFile(file.getPath(), options);
  }

  private static int dp(Context context, int value)
  {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  static final class ZoomImageView extends ImageView
  {
    private final Matrix _displayMatrix = new Matrix();
    private final ScaleGestureDetector _scaleDetector;
    private final GestureDetector _gestureDetector;
    private float _minimumScale = 1f;
    private float _maximumScale = 5f;
    private float _currentScale = 1f;
    private float _lastX;
    private float _lastY;
    private boolean _dragged;

    ZoomImageView(Context context)
    {
      super(context);
      setScaleType(ScaleType.MATRIX);
      _scaleDetector = new ScaleGestureDetector(context,
          new ScaleGestureDetector.SimpleOnScaleGestureListener()
          {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector)
            {
              return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector)
            {
              zoomBy(detector.getScaleFactor(), detector.getFocusX(),
                  detector.getFocusY());
              return true;
            }
          });
      _gestureDetector = new GestureDetector(context,
          new GestureDetector.SimpleOnGestureListener()
          {
            @Override public boolean onDown(MotionEvent event)
            {
              return true;
            }

            @Override public boolean onDoubleTap(MotionEvent event)
            {
              if (_currentScale > _minimumScale * 1.05f)
                resetToFit();
              else
                zoomTo(Math.min(_maximumScale,
                      Math.max(1f, _minimumScale * 2.5f)),
                    event.getX(), event.getY());
              return true;
            }
          });
      setFocusable(true);
      setClickable(true);
    }

    @Override public void setImageDrawable(Drawable drawable)
    {
      super.setImageDrawable(drawable);
      if (getWidth() > 0 && getHeight() > 0)
        resetToFit();
    }

    @Override protected void onSizeChanged(int width, int height,
        int oldWidth, int oldHeight)
    {
      super.onSizeChanged(width, height, oldWidth, oldHeight);
      resetToFit();
    }

    @Override public boolean onTouchEvent(MotionEvent event)
    {
      _gestureDetector.onTouchEvent(event);
      _scaleDetector.onTouchEvent(event);
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          _lastX = event.getX();
          _lastY = event.getY();
          _dragged = false;
          if (getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(true);
          break;
        case MotionEvent.ACTION_MOVE:
          if (!_scaleDetector.isInProgress() && event.getPointerCount() == 1)
          {
            float dx = event.getX() - _lastX;
            float dy = event.getY() - _lastY;
            if (Math.abs(dx) > 1f || Math.abs(dy) > 1f)
              _dragged = true;
            _displayMatrix.postTranslate(dx, dy);
            constrain();
            applyMatrix();
          }
          _lastX = event.getX();
          _lastY = event.getY();
          break;
        case MotionEvent.ACTION_POINTER_UP:
          int remaining = event.getActionIndex() == 0 ? 1 : 0;
          if (remaining < event.getPointerCount())
          {
            _lastX = event.getX(remaining);
            _lastY = event.getY(remaining);
          }
          break;
        case MotionEvent.ACTION_UP:
          if (!_dragged)
            performClick();
          break;
        case MotionEvent.ACTION_CANCEL:
          _dragged = false;
          break;
        default:
          break;
      }
      return true;
    }

    @Override public boolean performClick()
    {
      super.performClick();
      return true;
    }

    private void resetToFit()
    {
      Drawable drawable = getDrawable();
      int width = getWidth() - getPaddingLeft() - getPaddingRight();
      int height = getHeight() - getPaddingTop() - getPaddingBottom();
      if (drawable == null || width <= 0 || height <= 0 ||
          drawable.getIntrinsicWidth() <= 0 ||
          drawable.getIntrinsicHeight() <= 0)
        return;
      float widthScale = width / (float)drawable.getIntrinsicWidth();
      float heightScale = height / (float)drawable.getIntrinsicHeight();
      _minimumScale = Math.min(widthScale, heightScale);
      _maximumScale = Math.max(1f, _minimumScale * 5f);
      _currentScale = _minimumScale;
      _displayMatrix.reset();
      _displayMatrix.postScale(_minimumScale, _minimumScale);
      _displayMatrix.postTranslate(
          getPaddingLeft() +
            (width - drawable.getIntrinsicWidth() * _minimumScale) / 2f,
          getPaddingTop() +
            (height - drawable.getIntrinsicHeight() * _minimumScale) / 2f);
      applyMatrix();
    }

    private void zoomBy(float factor, float focusX, float focusY)
    {
      zoomTo(_currentScale * factor, focusX, focusY);
    }

    private void zoomTo(float targetScale, float focusX, float focusY)
    {
      float bounded = Math.max(_minimumScale,
          Math.min(_maximumScale, targetScale));
      float factor = bounded / _currentScale;
      _displayMatrix.postScale(factor, factor, focusX, focusY);
      _currentScale = bounded;
      constrain();
      applyMatrix();
    }

    private void constrain()
    {
      Drawable drawable = getDrawable();
      if (drawable == null)
        return;
      RectF bounds = new RectF(0, 0, drawable.getIntrinsicWidth(),
          drawable.getIntrinsicHeight());
      _displayMatrix.mapRect(bounds);
      float left = getPaddingLeft();
      float top = getPaddingTop();
      float right = getWidth() - getPaddingRight();
      float bottom = getHeight() - getPaddingBottom();
      float dx = 0f;
      float dy = 0f;
      if (bounds.width() <= right - left)
        dx = (left + right) / 2f - bounds.centerX();
      else if (bounds.left > left)
        dx = left - bounds.left;
      else if (bounds.right < right)
        dx = right - bounds.right;
      if (bounds.height() <= bottom - top)
        dy = (top + bottom) / 2f - bounds.centerY();
      else if (bounds.top > top)
        dy = top - bounds.top;
      else if (bounds.bottom < bottom)
        dy = bottom - bounds.bottom;
      _displayMatrix.postTranslate(dx, dy);
    }

    private void applyMatrix()
    {
      setImageMatrix(_displayMatrix);
      invalidate();
    }
  }
}
