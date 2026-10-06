package juloo.keyboard2.grammar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Rule word lists loaded once from assets/grammar/en.json. */
public final class GrammarData
{
  public final Set<String> anExceptions = new HashSet<>();
  public final Set<String> aExceptions = new HashSet<>();
  /** follower word → "noun" (flag only when no noun follows) or "any". */
  public final Map<String, String> yourFollowers = new HashMap<>();
  public final Set<String> itsToContraction = new HashSet<>();
  public final Set<String> contractionToIts = new HashSet<>();
  public final Set<String> theirFollowers = new HashSet<>();
  public final Set<String> thenAlways = new HashSet<>();
  public final Set<String> thenComparatives = new HashSet<>();
  public final Set<String> thenComparativeFollowers = new HashSet<>();

  private GrammarData() {}

  public static GrammarData load(InputStream input) throws IOException
  {
    try
    {
      JSONObject root = new JSONObject(readAll(input));
      GrammarData data = new GrammarData();
      JSONObject aAn = root.getJSONObject("a_an");
      fill(aAn.getJSONArray("an_exceptions"), data.anExceptions);
      fill(aAn.getJSONArray("a_exceptions"), data.aExceptions);
      JSONObject followers = root.getJSONObject("your_youre")
        .getJSONObject("followers");
      java.util.Iterator<String> keys = followers.keys();
      while (keys.hasNext())
      {
        String word = keys.next();
        data.yourFollowers.put(word, followers.getString(word));
      }
      JSONObject its = root.getJSONObject("its_its");
      fill(its.getJSONArray("its_to_contraction"), data.itsToContraction);
      fill(its.getJSONArray("contraction_to_its"), data.contractionToIts);
      fill(root.getJSONObject("their_there").getJSONArray("followers"),
          data.theirFollowers);
      JSONObject then = root.getJSONObject("then_than");
      fill(then.getJSONArray("always"), data.thenAlways);
      fill(then.getJSONArray("comparatives"), data.thenComparatives);
      fill(then.getJSONArray("comparative_followers"),
          data.thenComparativeFollowers);
      return data;
    }
    catch (JSONException error)
    {
      throw new IOException("Invalid grammar rule data", error);
    }
  }

  private static void fill(JSONArray array, Set<String> target)
      throws JSONException
  {
    for (int i = 0; i < array.length(); i++)
      target.add(array.getString(i));
  }

  private static String readAll(InputStream input) throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = input.read(buffer)) >= 0)
      out.write(buffer, 0, n);
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }
}
