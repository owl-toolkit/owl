package owl.translations.ltl2ldba;

import java.util.StringJoiner;

public class StringUtil {
  public static String join(String... pieces) {
    StringJoiner joiner = new StringJoiner(", ", " [", "]");

    for (String piece : pieces) {
      if (piece != null) {
        joiner.add(piece);
      }
    }

    return joiner.toString();
  }
}
