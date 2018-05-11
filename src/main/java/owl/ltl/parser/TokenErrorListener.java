package owl.ltl.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public final class TokenErrorListener extends BaseErrorListener {
  @Override
  public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
    int charPositionInLine, String msg, RecognitionException e) {
    if (e == null) {
      // TODO Check this case?
      return;
    }
    throw e;
  }
}
