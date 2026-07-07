package juloo.keyboard2.suggestions;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.TouchTrace;

/** Rerank candidates with keyboard geometry and, when available, tap locations. */
public final class TouchGeometry
{
  private static final double INSERT_DELETE_COST = 24.0;
  private static final double UNKNOWN_SUBSTITUTION_COST = 12.0;
  private static final double TOUCH_COST_SCALE = 10.0;

  public static void rerank(String typed, List<String> candidates)
  {
    rerank(typed, candidates, null, null);
  }

  public static void rerank(String typed, List<String> candidates,
      KeyboardData layout)
  {
    rerank(typed, candidates, null, layout);
  }

  public static void rerank(String typed, List<String> candidates,
      final TouchTrace touchTrace, KeyboardData layout)
  {
    final String normalized = typed.toLowerCase(Locale.ROOT);
    final Map<Character, Pos> positions = positions_for(layout);
    Collections.sort(candidates, new Comparator<String>()
        {
          public int compare(String a, String b)
          {
            double score_a = score(normalized, a.toLowerCase(Locale.ROOT),
                touchTrace, positions);
            double score_b = score(normalized, b.toLowerCase(Locale.ROOT),
                touchTrace, positions);
            if (score_a < score_b)
              return -1;
            if (score_a > score_b)
              return 1;
            return a.compareTo(b);
          }
        });
  }

  public static int score(String typed, String candidate)
  {
    return score(typed, candidate, QWERTY_POSITIONS);
  }

  static int score(String typed, String candidate,
      Map<Character, Pos> positions)
  {
    return (int)Math.round(score(typed, candidate, null, positions));
  }

  public static double score(String typed, String candidate, TouchTrace touchTrace,
      KeyboardData layout)
  {
    return score(typed.toLowerCase(Locale.ROOT),
        candidate.toLowerCase(Locale.ROOT), touchTrace, positions_for(layout));
  }

  static double score(String typed, String candidate, TouchTrace touchTrace,
      Map<Character, Pos> positions)
  {
    int n = typed.length();
    int m = candidate.length();
    double[][] d = new double[n + 1][m + 1];
    for (int i = 1; i <= n; i++)
      d[i][0] = d[i - 1][0] + INSERT_DELETE_COST;
    for (int j = 1; j <= m; j++)
      d[0][j] = d[0][j - 1] + INSERT_DELETE_COST;
    for (int i = 1; i <= n; i++)
    {
      for (int j = 1; j <= m; j++)
      {
        double sub = d[i - 1][j - 1] + substitution_cost(typed, i - 1,
            candidate.charAt(j - 1), touchTrace, positions);
        double del = d[i - 1][j] + INSERT_DELETE_COST;
        double ins = d[i][j - 1] + INSERT_DELETE_COST;
        double best = Math.min(sub, Math.min(del, ins));
        if (i > 1 && j > 1 && typed.charAt(i - 1) == candidate.charAt(j - 2)
            && typed.charAt(i - 2) == candidate.charAt(j - 1))
        {
          double trans = d[i - 2][j - 2]
            + substitution_cost(typed, i - 2, candidate.charAt(j - 2),
                touchTrace, positions)
            + substitution_cost(typed, i - 1, candidate.charAt(j - 1),
                touchTrace, positions)
            + 2.0;
          best = Math.min(best, trans);
        }
        d[i][j] = best;
      }
    }
    return d[n][m];
  }

  private static double substitution_cost(String typed, int typedIndex,
      char candidateChar, TouchTrace touchTrace, Map<Character, Pos> positions)
  {
    char typedChar = typed.charAt(typedIndex);
    if (typedChar == candidateChar)
      return 0.0;
    if (touchTrace == null || typedIndex >= touchTrace.size())
      return key_distance(typedChar, candidateChar, positions);
    TouchTrace.Entry touch = touchTrace.get(typedIndex);
    Pos typedPos = positions.get(Character.valueOf(typedChar));
    Pos candidatePos = positions.get(Character.valueOf(candidateChar));
    if (touch == null || typedPos == null || candidatePos == null)
      return key_distance(typedChar, candidateChar, positions);
    float unitX = Math.max(touch.keyWidth / 2f, 1f);
    float unitY = Math.max(touch.keyHeight / 2f, 1f);
    float candidateCenterX = touch.keyCenterX + (candidatePos.x - typedPos.x) * unitX;
    float candidateCenterY = touch.keyCenterY + (candidatePos.y - typedPos.y) * unitY;
    float sigmaX = Math.max(touch.keyWidth * 0.55f, 1f);
    float sigmaY = Math.max(touch.keyHeight * 0.55f, 1f);
    float dx = (touch.touchX - candidateCenterX) / sigmaX;
    float dy = (touch.touchY - candidateCenterY) / sigmaY;
    return (dx * dx + dy * dy) * TOUCH_COST_SCALE;
  }

  private static int key_distance(char a, char b, Map<Character, Pos> positions)
  {
    Pos pa = positions.get(Character.valueOf(a));
    Pos pb = positions.get(Character.valueOf(b));
    if (pa == null || pb == null)
      return a == b ? 0 : (int)UNKNOWN_SUBSTITUTION_COST;
    float dx = pa.x - pb.x;
    float dy = pa.y - pb.y;
    return Math.round(dx * dx + dy * dy);
  }

  private static Map<Character, Pos> positions_for(KeyboardData layout)
  {
    if (layout == null)
      return QWERTY_POSITIONS;
    Map<Character, Pos> out = new HashMap<Character, Pos>();
    float y = 0f;
    for (KeyboardData.Row row : layout.rows)
    {
      y += row.shift;
      float x = Math.max(0f, (layout.keysWidth - row.keysWidth) / 2f);
      for (KeyboardData.Key key : row.keys)
      {
        x += key.shift;
        float center_x = (x + key.width / 2f) * 2f;
        float center_y = (y + row.height / 2f) * 2f;
        for (int i = 0; i < key.keys.length; i++)
          add_key(out, key.getKeyValue(i), center_x, center_y);
        x += key.width;
      }
      y += row.height;
    }
    return out.isEmpty() ? QWERTY_POSITIONS : out;
  }

  private static void add_key(Map<Character, Pos> positions, KeyValue value,
      float x, float y)
  {
    if (value == null || value.getKind() != KeyValue.Kind.Char)
      return;
    char c = Character.toLowerCase(value.getChar());
    if (!Character.isLetter(c) || positions.containsKey(Character.valueOf(c)))
      return;
    positions.put(Character.valueOf(c), new Pos(x, y));
  }

  private static void add_row(String chars, int y, int x_offset)
  {
    for (int i = 0; i < chars.length(); i++)
      QWERTY_POSITIONS.put(Character.valueOf(chars.charAt(i)),
          new Pos(x_offset + i * 2, y));
  }

  private static final class Pos
  {
    final float x;
    final float y;

    Pos(float x_, float y_)
    {
      x = x_;
      y = y_;
    }
  }

  private static final Map<Character, Pos> QWERTY_POSITIONS =
    new HashMap<Character, Pos>();

  static
  {
    add_row("qwertyuiop", 0, 0);
    add_row("asdfghjkl", 2, 1);
    add_row("zxcvbnm", 4, 3);
  }
}
