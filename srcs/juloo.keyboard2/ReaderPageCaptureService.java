package juloo.keyboard2;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

/**
 * Opt-in page capture for the Reader AI omnibutton. When enabled by the user
 * in system accessibility settings, it snapshots the visible text of the
 * foreground window (throttled, password nodes skipped, bounded length) so the
 * omnibutton can summarize the page the user is looking at — YouTube, X,
 * articles — when the clipboard is empty. Text stays in memory only; nothing
 * leaves the device unless the user explicitly runs an AI action.
 */
public final class ReaderPageCaptureService extends AccessibilityService
{
  private static final int MAX_TEXT = 32000;
  private static final long MIN_INTERVAL_MS = 1200;
  private static final String OVERLAY_PREF = "reader_ai_button_overlay";
  private static final String OVERLAY_X = "reader_ai_overlay_x";
  private static final String OVERLAY_Y = "reader_ai_overlay_y";
  private static final String OVERLAY_OPACITY = "reader_ai_overlay_opacity";
  private static final String OVERLAY_COLOR = "reader_ai_overlay_color";
  private static final String OVERLAY_ICON_COLOR = "reader_ai_overlay_icon_color";
  private static final String OVERLAY_SHAPE = "reader_ai_overlay_shape";

  private static volatile String latestText = "";
  private static volatile String latestPackage = "";
  private static volatile ReaderPageCaptureService instance;

  private long lastCapture = 0;
  private WindowManager windowManager;
  private ImageView overlayButton;
  private WindowManager.LayoutParams overlayParams;
  private boolean overlayDragging = false;
  private SharedPreferences prefs;
  private boolean keyboardVisible = false;
  private final android.os.Handler handler =
    new android.os.Handler(android.os.Looper.getMainLooper());
  private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
    (shared, key) -> onOverlayPrefChanged(key);

  static boolean isEnabled()
  {
    return instance != null;
  }

  static String latestText()
  {
    return latestText;
  }

  static String latestPackage()
  {
    return latestPackage;
  }

  /** Freshly capture the foreground window on demand; returns captured text. */
  static String captureNow()
  {
    ReaderPageCaptureService service = instance;
    if (service == null)
      return latestText;
    return service.captureActiveWindow();
  }

  @Override
  public void onServiceConnected()
  {
    instance = this;
    prefs = getSharedPreferences("reader_ai_overlay", Context.MODE_PRIVATE);
    prefs.registerOnSharedPreferenceChangeListener(prefListener);
    windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
    showOverlay();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event)
  {
    if (event == null)
      return;
    updateKeyboardVisibility();
    CharSequence pkg = event.getPackageName();
    if (pkg != null && getPackageName().contentEquals(pkg))
      return; // never capture our own dialogs/keyboard
    long now = System.currentTimeMillis();
    if (now - lastCapture < MIN_INTERVAL_MS)
      return;
    lastCapture = now;
    captureActiveWindow();
  }

  private String captureActiveWindow()
  {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null)
      return latestText;
    String pkg = String.valueOf(root.getPackageName());
    if (getPackageName().equals(pkg))
    {
      root.recycle();
      return latestText; // our own dialog/keyboard is foreground — keep last page
    }
    StringBuilder text = new StringBuilder();
    collect(root, text);
    root.recycle();
    String captured = text.toString().trim();
    if (captured.length() >= 40)
    {
      latestText = captured;
      latestPackage = pkg;
    }
    return latestText;
  }

  private void collect(AccessibilityNodeInfo node, StringBuilder out)
  {
    if (node == null || out.length() >= MAX_TEXT)
      return;
    if (!node.isPassword())
    {
      CharSequence text = node.getText();
      if (text != null)
      {
        String value = text.toString().trim();
        if (!value.isEmpty())
        {
          if (out.length() > 0)
            out.append('\n');
          out.append(value);
        }
      }
    }
    int count = node.getChildCount();
    for (int i = 0; i < count && out.length() < MAX_TEXT; i++)
    {
      AccessibilityNodeInfo child = node.getChild(i);
      if (child == null)
        continue;
      collect(child, out);
      child.recycle();
    }
  }

  @Override
  public void onInterrupt() {}

  private static float boundedOpacity(float value)
  {
    if (Float.isNaN(value))
      return 0.5f;
    return Math.max(0.2f, Math.min(1.0f, value));
  }

  private void showOverlay()
  {
    if (!prefs.getBoolean(OVERLAY_PREF, true) || overlayButton != null)
      return;
    int size = (int)(48 * getResources().getDisplayMetrics().density);
    ImageView button = new ImageView(this);
    button.setImageResource(R.drawable.ic_reader_ai);
    button.setPadding(size / 5, size / 5, size / 5, size / 5);
    button.setContentDescription(getString(R.string.reader_open_ai_chat));
    overlayParams = new WindowManager.LayoutParams(size, size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
          | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
          | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT);
    overlayParams.gravity = Gravity.TOP | Gravity.START;
    overlayParams.x = prefs.getInt(OVERLAY_X, 0);
    overlayParams.y = prefs.getInt(OVERLAY_Y,
        (int)(200 * getResources().getDisplayMetrics().density));
    overlayButton = button;
    applyOverlayAppearance();
    button.setOnTouchListener(new View.OnTouchListener()
    {
      private float downX, downY, startX, startY;
      private boolean dragging;
      private final Runnable dragStart = () -> {
        dragging = true;
        overlayDragging = true;
        applyOverlayAppearance();
      };
      @Override public boolean onTouch(View view, MotionEvent event)
      {
        switch (event.getAction())
        {
          case MotionEvent.ACTION_DOWN:
            downX = event.getRawX(); downY = event.getRawY();
            startX = overlayParams.x; startY = overlayParams.y;
            dragging = false;
            handler.postDelayed(dragStart,
                android.view.ViewConfiguration.getLongPressTimeout());
            return true;
          case MotionEvent.ACTION_MOVE:
            if (dragging)
            {
              overlayParams.x = (int)(startX + (event.getRawX() - downX));
              overlayParams.y = (int)(startY + (event.getRawY() - downY));
              windowManager.updateViewLayout(overlayButton, overlayParams);
            }
            return true;
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            handler.removeCallbacks(dragStart);
            if (event.getAction() == MotionEvent.ACTION_UP && !dragging)
            {
              float dx = event.getRawX() - downX;
              float dy = event.getRawY() - downY;
              float slop = android.view.ViewConfiguration.get(
                  ReaderPageCaptureService.this).getScaledTouchSlop();
              if (dx * dx + dy * dy > slop * slop * 4)
                dispatchOverlay(ReaderAiAction.sectorFor(dx, dy));
              else
                dispatchOverlay(ReaderAiAction.Sector.TAP);
            }
            else if (dragging)
              prefs.edit().putInt(OVERLAY_X, overlayParams.x)
                .putInt(OVERLAY_Y, overlayParams.y).apply();
            overlayDragging = false;
            applyOverlayAppearance();
            dragging = false;
            return true;
        }
        return false;
      }
    });
    try
    {
      windowManager.addView(overlayButton, overlayParams);
      updateKeyboardVisibility();
    }
    catch (Exception ignored) {}
  }

  private void applyOverlayAppearance()
  {
    if (overlayButton == null)
      return;
    int alpha = overlayDragging ? 255
        : (int)(boundedOpacity(prefs.getFloat(OVERLAY_OPACITY, 0.5f)) * 255);
    android.graphics.drawable.GradientDrawable bg =
      new android.graphics.drawable.GradientDrawable();
    boolean round = prefs.getBoolean(OVERLAY_SHAPE, true);
    bg.setShape(round ? android.graphics.drawable.GradientDrawable.OVAL
        : android.graphics.drawable.GradientDrawable.RECTANGLE);
    if (!round)
      bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
    bg.setColor(prefs.getInt(OVERLAY_COLOR, 0xFF303030));
    bg.setAlpha(alpha);
    overlayButton.setBackground(bg);
    overlayButton.setColorFilter(prefs.getInt(OVERLAY_ICON_COLOR, 0xFFFFFFFF));
    overlayButton.setImageAlpha(alpha);
    overlayButton.setAlpha(1.0f);
  }

  private void onOverlayPrefChanged(String key)
  {
    if (OVERLAY_PREF.equals(key))
    {
      if (prefs.getBoolean(OVERLAY_PREF, true))
        showOverlay();
      else
        removeOverlay();
      return;
    }
    if (OVERLAY_X.equals(key) || OVERLAY_Y.equals(key))
      return; // position writes come from the drag itself
    applyOverlayAppearance();
  }

  private void removeOverlay()
  {
    if (overlayButton != null && windowManager != null)
    {
      try { windowManager.removeView(overlayButton); }
      catch (Exception ignored) {}
      overlayButton = null;
    }
  }

  private void updateKeyboardVisibility()
  {
    boolean hide = false;
    try
    {
      for (android.view.accessibility.AccessibilityWindowInfo w : getWindows())
        if (w.getType() ==
            android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        {
          hide = true;
          break;
        }
      if (!hide)
      {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null)
        {
          hide = hasPasswordNode(root);
          root.recycle();
        }
      }
    }
    catch (Exception ignored) {}
    if (hide == keyboardVisible)
      return;
    keyboardVisible = hide;
    if (overlayButton != null)
      overlayButton.setVisibility(hide ? View.GONE : View.VISIBLE);
  }

  private boolean hasPasswordNode(AccessibilityNodeInfo node)
  {
    if (node == null)
      return false;
    if (node.isPassword() && node.isVisibleToUser())
      return true;
    for (int i = 0; i < node.getChildCount(); i++)
    {
      AccessibilityNodeInfo child = node.getChild(i);
      boolean found = hasPasswordNode(child);
      if (child != null)
        child.recycle();
      if (found)
        return true;
    }
    return false;
  }

  private void dispatchOverlay(ReaderAiAction.Sector sector)
  {
    captureActiveWindow(); // grab the page behind before our activity opens
    SharedPreferences actions =
      android.preference.PreferenceManager.getDefaultSharedPreferences(this);
    ReaderAiAction action = ReaderAiAction.actionFor(actions, sector);
    try
    {
      startActivity(ReaderAiQuickActivity.intent(this, action));
    }
    catch (Exception ignored) {}
  }


  @Override
  public void onDestroy()
  {
    if (prefs != null)
      prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
    removeOverlay();
    if (instance == this)
      instance = null;
    super.onDestroy();
  }
}
