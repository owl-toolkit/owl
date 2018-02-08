package owl.util;

import java.util.StringJoiner;

public final class StringUtil {
  private StringUtil() {}

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
