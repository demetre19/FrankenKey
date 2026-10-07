package juloo.keyboard2.grammar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stateless offline grammar rules. [check] scans one sentence (at most
 * [MAX_LENGTH] UTF-16 units) and returns ordered [GrammarIssue] entries.
 * No allocation happens beyond tokenization and the result list.
 */
public final class GrammarRules
{
  public static final int MAX_LENGTH = 500;

  public static final String RULE_DOUBLE_WORD = "double_word";
  public static final String RULE_A_AN = "a_an";
  public static final String RULE_COULD_OF = "could_of";
  public static final String RULE_YOUR_YOURE = "your_youre";
  public static final String RULE_ITS_ITS = "its_its";
  public static final String RULE_THEIR_THERE = "their_there";
  public static final String RULE_THEN_THAN = "then_than";
  public static final String RULE_ALOT = "alot";

  public static final String[] ALL_RULE_IDS = {
    RULE_DOUBLE_WORD, RULE_A_AN, RULE_COULD_OF, RULE_YOUR_YOURE,
    RULE_ITS_ITS, RULE_THEIR_THERE, RULE_THEN_THAN, RULE_ALOT
  };

  private static final Set<String> MODALS = new HashSet<>(Arrays.asList(
      "could", "should", "would", "must", "might"));
  /** Repetitions that are valid English and never flagged. */
  private static final Set<String> DOUBLE_WORD_SKIP = new HashSet<>(
      Arrays.asList("had", "that"));
  /** Letters whose spoken name starts with a vowel sound ("an F"). */
  private static final String INITIALISM_VOWEL_LETTERS = "aefhilmnorsx";
  /** Mixed-case words read as initialisms ("an X-ray"). */
  private static final Set<String> INITIALISM_WORDS = new HashSet<>(
      Arrays.asList("x-ray", "x-rays"));
  /** Nouns that make "your welcome/right/wrong/sure + noun" valid. */
  private static final java.util.Map<String, Set<String>>
    YOUR_NOUN_FOLLOWERS = new java.util.HashMap<>();
  static
  {
    YOUR_NOUN_FOLLOWERS.put("welcome", new HashSet<>(Arrays.asList(
        "pack", "packs", "basket", "baskets", "card", "cards", "gift",
        "gifts", "sign", "signs", "party", "parties", "kit", "kits",
        "letter", "letters", "message", "messages", "speech", "speech")));
    YOUR_NOUN_FOLLOWERS.put("right", new HashSet<>(Arrays.asList(
        "hand", "hands", "foot", "feet", "side", "sides", "arm", "arms",
        "eye", "eyes", "ear", "ears", "turn", "lane", "lanes", "wing",
        "answer", "answers", "choice", "choices", "way")));
    YOUR_NOUN_FOLLOWERS.put("wrong", new HashSet<>(Arrays.asList(
        "hand", "hands", "side", "sides", "turn", "turns", "way", "ways",
        "number", "numbers", "answer", "answers", "choice", "choices")));
    YOUR_NOUN_FOLLOWERS.put("sure", new HashSet<>(Arrays.asList(
        "thing", "things", "fire", "winner", "winners", "sign", "signs")));
  }

  private GrammarRules() {}

  static final class Token
  {
    int start;
    int end;
    String word;
    String lower;
    boolean quoted;
  }

  /**
   * [sentence] is at most [MAX_LENGTH] UTF-16 units. Returned issues are
   * ordered by offset; spans never overlap.
   */
  public static List<GrammarIssue> check(String sentence, GrammarData data)
  {
    List<GrammarIssue> issues = new ArrayList<>();
    if (sentence == null || sentence.length() > MAX_LENGTH || data == null)
      return issues;
    List<Token> tokens = tokenize(sentence);
    for (int i = 0; i < tokens.size(); i++)
    {
      Token t = tokens.get(i);
      String w = t.lower;
      Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
      Token next2 = i + 2 < tokens.size() ? tokens.get(i + 2) : null;
      if (next != null)
      {
        if (w.equals(next.lower))
          checkDoubleWord(sentence, tokens, i, issues);
        if ("a".equals(w) || "an".equals(w))
          checkAAn(sentence, t, next, data, issues);
        if (MODALS.contains(w) && "of".equals(next.lower)
            && (next2 == null || !"course".equals(next2.lower)))
          add(issues, RULE_COULD_OF, t.start, next.end,
              t.word + " " + cased("have", next.word),
              sentence.substring(t.start, next.end));
        if ("your".equals(w))
          checkYour(sentence, t, next, next2, data, issues);
        if ("its".equals(w) && data.itsToContraction.contains(next.lower))
          add(issues, RULE_ITS_ITS, t.start, t.end,
              cased("it's", t.word), t.word);
        if (("it's".equals(w) || "it’s".equals(w))
            && data.contractionToIts.contains(next.lower))
          add(issues, RULE_ITS_ITS, t.start, t.end, cased("its", t.word),
              t.word);
        if ("their".equals(w))
          checkTheir(sentence, t, next, next2, data, issues);
      }
      if ("then".equals(w))
        checkThenThan(sentence, tokens, i, next, data, issues);
      if ("alot".equals(w))
        add(issues, RULE_ALOT, t.start, t.end, cased("a lot", t.word), t.word);
    }
    return issues;
  }

  private static void checkDoubleWord(String sentence, List<Token> tokens,
      int i, List<GrammarIssue> issues)
  {
    Token t = tokens.get(i);
    Token next = tokens.get(i + 1);
    if (DOUBLE_WORD_SKIP.contains(t.lower))
      return;
    if (isDigits(t.lower))
      return;
    if ("is".equals(t.lower) && t.quoted)
      return;
    /* Span both occurrences; replace with a single, source-cased word. */
    add(issues, RULE_DOUBLE_WORD, t.start, next.end, t.word,
        sentence.substring(t.start, next.end));
  }

  private static void checkAAn(String sentence, Token article, Token next,
      GrammarData data, List<GrammarIssue> issues)
  {
    String correct = articleFor(next, data);
    if (correct == null || correct.equals(article.lower))
      return;
    add(issues, RULE_A_AN, article.start, article.end,
        cased(correct, article.word), article.word);
  }

  /** Correct article for [word], or null when no confident call exists. */
  static String articleFor(Token word, GrammarData data)
  {
    String w = word.lower;
    if (w.length() == 0)
      return null;
    if (data.anExceptions.contains(w))
      return "an";
    if (data.aExceptions.contains(w))
      return "a";
    if (isAllCaps(word.word))
      return INITIALISM_VOWEL_LETTERS.indexOf(w.charAt(0)) >= 0 ? "an" : "a";
    char first = w.charAt(0);
    if (first == 'u')
    {
      /* "u" words not in the exception list: long-u takes "a" only when the
         exception list names it; otherwise "un-"/"us" words take "an". */
      return "an";
    }
    return isVowel(first) ? "an" : "a";
  }

  private static void checkYour(String sentence, Token t, Token next,
      Token next2, GrammarData data, List<GrammarIssue> issues)
  {
    String kind = data.yourFollowers.get(next.lower);
    if (kind == null)
      return;
    if ("noun".equals(kind) && next2 != null
        && YOUR_NOUN_FOLLOWERS.get(next.lower).contains(next2.lower))
      return; /* "your welcome pack", "your right hand" */
    add(issues, RULE_YOUR_YOURE, t.start, t.end, cased("you're", t.word),
        t.word);
  }

  private static void checkTheir(String sentence, Token t, Token next,
      Token next2, GrammarData data, List<GrammarIssue> issues)
  {
    boolean match = data.theirFollowers.contains(next.lower)
      || ("will".equals(next.lower) && next2 != null
        && "be".equals(next2.lower))
      || ("has".equals(next.lower) && next2 != null
        && "been".equals(next2.lower));
    if (match)
      add(issues, RULE_THEIR_THERE, t.start, t.end, cased("there", t.word),
          t.word);
  }

  private static void checkThenThan(String sentence, List<Token> tokens,
      int i, Token next, GrammarData data, List<GrammarIssue> issues)
  {
    if (i == 0)
      return;
    Token prev = tokens.get(i - 1);
    Token t = tokens.get(i);
    boolean match = data.thenAlways.contains(prev.lower)
      || (data.thenComparatives.contains(prev.lower) && next != null
        && (data.thenComparativeFollowers.contains(next.lower)
          || isDigits(next.lower)));
    if (match)
      add(issues, RULE_THEN_THAN, t.start, t.end, cased("than", t.word),
          t.word);
  }

  private static void add(List<GrammarIssue> issues, String rule, int start,
      int end, String replacement, String matched)
  {
    if (!issues.isEmpty())
    {
      GrammarIssue last = issues.get(issues.size() - 1);
      if (start < last.offset + last.length)
        return;
    }
    issues.add(new GrammarIssue(rule, start, end - start, replacement,
        matched));
  }

  /** Apply the source token's casing to a replacement string. */
  static String cased(String replacement, String source)
  {
    if (source.length() == 0)
      return replacement;
    if (source.length() > 1 && isAllCaps(source)
        && replacement.indexOf(' ') < 0)
      return replacement.toUpperCase(Locale.ROOT);
    if (Character.isUpperCase(source.charAt(0)))
      return Character.toUpperCase(replacement.charAt(0))
        + replacement.substring(1);
    return replacement;
  }

  static List<Token> tokenize(String sentence)
  {
    List<Token> tokens = new ArrayList<>();
    boolean inQuote = false;
    int i = 0;
    int n = sentence.length();
    while (i < n)
    {
      char c = sentence.charAt(i);
      if (c == '"' || c == '\u201c' || c == '\u201d')
      {
        inQuote = !inQuote;
        i++;
        continue;
      }
      if (c == '\'' || c == '\u2019')
      {
        /* Quote toggles only for "is is" style: an apostrophe surrounded by
           spaces or at the boundaries. */
        boolean leftSpace = i == 0
          || Character.isWhitespace(sentence.charAt(i - 1));
        boolean rightSpace = i == n - 1
          || Character.isWhitespace(sentence.charAt(i + 1));
        if (leftSpace || rightSpace)
        {
          inQuote = !inQuote;
          i++;
          continue;
        }
      }
      if (isWordChar(sentence, i))
      {
        int start = i;
        while (i < n && isWordChar(sentence, i))
          i++;
        /* Internal apostrophes stay inside the word ("it's"). */
        while (i + 1 < n && isApostrophe(sentence.charAt(i))
            && isWordChar(sentence, i + 1))
        {
          i++;
          while (i < n && isWordChar(sentence, i))
            i++;
        }
        Token token = new Token();
        token.start = start;
        token.end = i;
        token.word = sentence.substring(start, i);
        token.lower = token.word.toLowerCase(Locale.ROOT)
          .replace('\u2019', '\'');
        token.quoted = inQuote;
        tokens.add(token);
        continue;
      }
      i++;
    }
    return tokens;
  }

  private static boolean isWordChar(String s, int i)
  {
    int cp = s.codePointAt(i);
    return Character.isLetterOrDigit(cp);
  }

  private static boolean isApostrophe(char c)
  {
    return c == '\'' || c == '\u2019';
  }

  private static boolean isVowel(char c)
  {
    return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
  }

  private static boolean isAllCaps(String word)
  {
    for (int i = 0; i < word.length(); i++)
      if (Character.isLowerCase(word.charAt(i)))
        return false;
    return true;
  }

  private static boolean isDigits(String word)
  {
    for (int i = 0; i < word.length(); i++)
      if (!Character.isDigit(word.charAt(i)))
        return false;
    return word.length() > 0;
  }
}
