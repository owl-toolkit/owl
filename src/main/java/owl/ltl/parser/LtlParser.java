package owl.ltl.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.annotation.Nullable;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import owl.grammar.LTLLexer;
import owl.grammar.LTLParser;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;

public final class LtlParser {
  private final CharStream input;

  private LtlParser(CharStream input) {
    this.input = input;
  }

  public static LabelledFormula parse(String input) {
    return parse(input, null);
  }

  public static LabelledFormula parse(InputStream input) throws IOException {
    return new LtlParser(CharStreams.fromStream(input)).doParse(null);
  }

  public static LabelledFormula parse(String input, @Nullable List<String> literals) {
    return new LtlParser(CharStreams.fromString(input)).doParse(literals);
  }

  public static Formula syntax(String input) {
    return parse(input).formula();
  }

  public static Formula syntax(String input, @Nullable List<String> literals) {
    return parse(input, literals).formula();
  }

  private LabelledFormula doParse(@Nullable List<String> literals) {
    // Tokenize the stream
    LTLLexer lexer = new LTLLexer(input);
    // Don't print long error messages on the console
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    // Add a fail-fast behaviour for token errors
    lexer.addErrorListener(new TokenErrorListener());
    CommonTokenStream tokens = new CommonTokenStream(lexer);

    // Parse the tokens
    LTLParser parser = new LTLParser(tokens);
    // Set fail-fast behaviour for grammar errors
    parser.setErrorHandler(new BailErrorStrategy());

    // Convert the AST into a proper object
    LtlParseTreeVisitor treeVisitor = literals == null || literals.isEmpty()
      ? new LtlParseTreeVisitor()
      : new LtlParseTreeVisitor(literals);
    return LabelledFormula.of(treeVisitor.visit(parser.formula()), treeVisitor.variables());
  }

  private static final class TokenErrorListener extends BaseErrorListener {
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
}
