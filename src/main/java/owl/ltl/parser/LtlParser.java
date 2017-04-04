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
    return new LtlParser().parseLtl(input);
  }

  private static LtlParseResult parse(ANTLRInputStream stream) {
    final LtlParser parser = new LtlParser();
    final Formula formula = parser.parseLtl(stream);
    return new LtlParseResult(formula, parser.variables());
  }

  public static LtlParseResult parse(String input) {
    try {
      return parse(new ANTLRInputStream(input));
    } catch (ParseCancellationException e) {
      System.err.println("Failed for formula " + input);
      throw e;
    }
  }

  public static LtlParseResult parse(InputStream input) throws IOException {
    return parse(new ANTLRInputStream(input));
  }

  public Formula parseLtl(String input) {
    return parseLtl(new ANTLRInputStream(input));
  }

  public Formula parseLtl(InputStream input) throws IOException {
    return parseLtl(new ANTLRInputStream(input));
  }

  private Formula parseLtl(ANTLRInputStream stream) {
    LTLLexer lexer = new LTLLexer(stream);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    LTLParser parser = new LTLParser(tokens);
    parser.setErrorHandler(new BailErrorStrategy());
    return treeVisitor.visit(parser.formula());
  }

  public ImmutableList<String> variables() {
    return treeVisitor.variables();
  }
}
