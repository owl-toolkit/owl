package owl.ltl.parser;

public final class ParserException extends Exception {
  ParserException(String message) {
    super(message);
  }

  ParserException(Throwable cause) {
    super(cause);
  }
}
