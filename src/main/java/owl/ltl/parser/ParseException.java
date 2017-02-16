package owl.ltl.parser;

public final class ParseException extends Exception {
  ParseException(String message) {
    super(message);
  }

  ParseException(Throwable cause) {
    super(cause);
  }
}
