package juloo.keyboard2.snippets;

import android.view.inputmethod.InputConnection;
import org.junit.Test;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class SnippetInsertionTest
{
  public SnippetInsertionTest() {}

  @Test
  public void inserter_commits_exact_phrase_at_cursor_position_one()
  {
    CapturingInputConnection input = CapturingInputConnection.create();
    String phrase = "Ship it, no trailing space";

    assertTrue(SnippetInserter.insert(input.connection, phrase));

    assertEquals("Snippet insertion must make exactly one text commit.",
        1, input.commitTextCalls);
    assertEquals("Snippet insertion must commit the configured phrase unchanged.",
        phrase, input.committedText.toString());
    assertEquals("Snippet insertion must leave the cursor after the inserted phrase.",
        1, input.newCursorPosition);
  }

  @Test
  public void inserter_does_not_append_space_to_phrase()
  {
    CapturingInputConnection input = CapturingInputConnection.create();

    assertTrue(SnippetInserter.insert(input.connection, "addr@example.test"));

    assertEquals("addr@example.test", input.committedText.toString());
  }

  @Test
  public void inserter_returns_false_for_null_input_connection()
  {
    assertFalse(SnippetInserter.insert(null, "safe"));
  }

  @Test
  public void key_event_handler_snippet_entry_uses_normal_text_path_without_space()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/KeyEventHandler.java");
    String body = compact(methodBody(source, "public void snippet_entered(String phrase)"));

    assertTrue("snippet_entered must delegate through send_text(phrase) so snippets use the normal string-key path.",
        body.contains("send_text(phrase);"));
    assertFalse("snippet_entered must not add the suggestion-style trailing space.",
        body.contains("phrase+\" \"") || body.contains("phrase + \" \""));
  }

  @Test
  public void keyboard_refresh_passes_snippet_row_listener_that_inserts_slot_phrase()
      throws Exception
  {
    String source = readSource("srcs/juloo.keyboard2/Keyboard2.java");
    String body = compact(methodBody(source, "private void refresh_config()"));

    assertTrue("refresh_config must wire SnippetRowView taps to KeyEventHandler.snippet_entered(...).",
        body.contains("_snippet_row_view.refresh_config(_prefs,")
        && body.contains("_keyeventhandler.snippet_entered("));
    assertTrue("Snippet row listener must insert the tapped slot phrase, not its label or index.",
        body.contains("slot.getPhrase()"));
    assertFalse("Runtime snippet row wiring must not pass a null listener.",
        body.contains("_snippet_row_view.refresh_config(_prefs,null)"));
  }

  private static String readSource(String path)
      throws Exception
  {
    return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
  }

  private static String methodBody(String source, String methodSignature)
  {
    int methodIndex = source.indexOf(methodSignature);
    assertTrue("Expected method in source: " + methodSignature, methodIndex >= 0);
    int openBrace = source.indexOf('{', methodIndex);
    assertTrue("Expected method body for: " + methodSignature, openBrace >= 0);

    int depth = 0;
    for (int i = openBrace; i < source.length(); i++)
    {
      char c = source.charAt(i);
      if (c == '{')
        depth++;
      else if (c == '}')
      {
        depth--;
        if (depth == 0)
          return source.substring(openBrace + 1, i);
      }
    }
    fail("Expected closing brace for: " + methodSignature);
    return "";
  }

  private static String compact(String source)
  {
    return source.replaceAll("\\s+", "");
  }

  private static final class CapturingInputConnection
  {
    final InputConnection connection;
    int commitTextCalls;
    CharSequence committedText;
    int newCursorPosition;

    private CapturingInputConnection(InputConnection connection)
    {
      this.connection = connection;
    }

    static CapturingInputConnection create()
    {
      final CapturingInputConnection[] holder = new CapturingInputConnection[1];
      InvocationHandler handler = new InvocationHandler()
      {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
        {
          if ("commitText".equals(method.getName()))
          {
            holder[0].commitTextCalls++;
            holder[0].committedText = (CharSequence)args[0];
            holder[0].newCursorPosition = ((Integer)args[1]).intValue();
            return Boolean.TRUE;
          }
          return defaultValue(method.getReturnType());
        }
      };
      InputConnection connection = (InputConnection)Proxy.newProxyInstance(
          InputConnection.class.getClassLoader(),
          new Class<?>[] { InputConnection.class }, handler);
      holder[0] = new CapturingInputConnection(connection);
      return holder[0];
    }

    private static Object defaultValue(Class<?> returnType)
    {
      if (returnType == Void.TYPE)
        return null;
      if (returnType == Boolean.TYPE)
        return Boolean.FALSE;
      if (returnType == Integer.TYPE)
        return Integer.valueOf(0);
      if (returnType == Long.TYPE)
        return Long.valueOf(0L);
      if (returnType == Float.TYPE)
        return Float.valueOf(0.0f);
      if (returnType == Double.TYPE)
        return Double.valueOf(0.0d);
      if (returnType == Short.TYPE)
        return Short.valueOf((short)0);
      if (returnType == Byte.TYPE)
        return Byte.valueOf((byte)0);
      if (returnType == Character.TYPE)
        return Character.valueOf('\0');
      return null;
    }
  }
}
