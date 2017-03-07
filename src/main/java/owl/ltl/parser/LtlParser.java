package owl.ltl.parser;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.grammar.LTLLexer;
import owl.grammar.LTLParser;
import owl.ltl.Formula;

public final class LtlParser {
  private final LtlParseTreeVisitor treeVisitor;

  public LtlParser() {
    this.treeVisitor = new LtlParseTreeVisitor();
  }

  public static Formula formula(String input) {
    try {
      return new LtlParser().parseLtl(input);
    } catch (ParserException e) {
      throw new AssertionError(e);
    }
  }

  private static LtlParseResult parse(ANTLRInputStream stream) throws ParserException {
    final LtlParser parser = new LtlParser();
    final Formula formula = parser.parseLtl(stream);
    return new LtlParseResult(formula, parser.variables());
  }

  public static LtlParseResult parse(String input) throws ParserException {
    return parse(new ANTLRInputStream(input));
  }

  public static LtlParseResult parse(InputStream input) throws IOException, ParserException {
    return parse(new ANTLRInputStream(input));
  }

  public Formula parseLtl(String input) throws ParserException {
    return parseLtl(new ANTLRInputStream(input));
  }

  public Formula parseLtl(InputStream input) throws IOException, ParserException {
    return parseLtl(new ANTLRInputStream(input));
  }

  private Formula parseLtl(ANTLRInputStream stream) throws ParserException {
    final LTLLexer lexer = new LTLLexer(stream);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final LTLParser parser = new LTLParser(tokens);
    parser.setErrorHandler(new BailErrorStrategy());

    try {
      return treeVisitor.visit(parser.formula());
    } catch (ParseCancellationException e) {
      throw new ParserException(e);
    }
  }

  public ImmutableList<String> variables() {
    return treeVisitor.variables();
  }
}
