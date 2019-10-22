/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.ltl.ltlf;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.annotation.Nullable;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import owl.grammar.LTLLexer;
import owl.grammar.LTLParser;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.parser.TokenErrorListener;
import owl.ltl.rewriter.SimplifierFactory;

import owl.util.annotation.CEntryPoint;

public final class LtlfParser {
  private LtlfParser() {}

  public static LabelledFormula parse(String input) {
    return parse(input, null);
  }

  public static LabelledFormula parse(InputStream input) throws IOException {
    return parse(CharStreams.fromStream(input), null);
  }

  public static LabelledFormula parse(String input, @Nullable List<String> literals) {
    return parse(CharStreams.fromString(input), literals);
  }

  public static Formula syntax(String input) {
    return parse(input).formula();
  }

  public static Formula syntax(String input, @Nullable List<String> literals) {
    return parse(input, literals).formula();
  }

  @CEntryPoint
  public static Formula syntaxToLtl(String input, List<String> literals) {
    Formula ltlf = parse(input, literals).formula();
    Formula ltl = LtlfToLtlTranslator.translate(ltlf, Literal.of(literals.size()));

    return SimplifierFactory.apply(ltl,
      SimplifierFactory.Mode.SYNTACTIC_FIXPOINT);
  }

  private static LabelledFormula parse(CharStream input, @Nullable List<String> literals) {
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
    LtlfParseTreeVisitor treeVisitor = literals == null || literals.isEmpty()
      ? new LtlfParseTreeVisitor()
      : new LtlfParseTreeVisitor(literals);
    return LabelledFormula.of(treeVisitor.visit(parser.formula()), treeVisitor.variables());
  }

}
