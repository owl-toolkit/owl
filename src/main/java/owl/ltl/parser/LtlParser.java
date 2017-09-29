package owl.ltl.parser;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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

public final class LtlParser {
  private final CharStream input;

  private LtlParser(CharStream input) {
    this.input = input;
  }

  public static LtlParser create(String input) {
    return new LtlParser(CharStreams.fromString(input));
  }

  public static LtlParser create(InputStream input) throws IOException {
    return new LtlParser(CharStreams.fromStream(input));
  }

  public static Formula formula(String input) {
    return parse(input).getFormula();
  }

  public static LtlParseResult parse(String input) {
    return create(input).parse();
  }

  public static LtlParseResult parse(InputStream input) throws IOException {
    return create(input).parse();
  }

  private LtlParseResult doParse(List<String> literals) {
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
    LtlParseTreeVisitor treeVisitor = literals.isEmpty()
      ? new LtlParseTreeVisitor()
      : new LtlParseTreeVisitor(literals);
    Formula formula = treeVisitor.visit(parser.formula());
    return new LtlParseResult(formula, treeVisitor.variables());
  }

  public Formula formula() {
    return parse().getFormula();
  }

  public LtlParseResult parse() {
    return doParse(ImmutableList.of());
  }

  public LtlParseResult parse(List<String> literals) {
    checkArgument(!literals.isEmpty(), "Supplied literal list is empty");
    return doParse(literals);
  }

  public LtlParseResult parse(String... literals) {
    return parse(ImmutableList.copyOf(literals));
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
