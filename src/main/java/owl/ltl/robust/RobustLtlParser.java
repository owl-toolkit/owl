package owl.ltl.robust;

import java.io.IOException;
import java.io.InputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import owl.grammar.LTLLexer;
import owl.grammar.LTLParser;
import owl.ltl.parser.TokenErrorListener;

public final class RobustLtlParser {
  private RobustLtlParser() {}

  public static LabelledSplit parse(String input) {
    return parse(CharStreams.fromString(input));
  }

  public static LabelledSplit parse(InputStream input)
    throws IOException {
    return parse(CharStreams.fromStream(input));
  }

  private static LabelledSplit parse(CharStream input) {
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
    RobustLtlVisitor visitor = new RobustLtlVisitor();
    Split split = visitor.visit(parser.formula());
    return LabelledSplit.of(split, visitor.variables());
  }
}
