/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.ltl.parser;

import java.util.List;
import javax.annotation.Nullable;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import owl.grammar.LTLLexer;
import owl.grammar.LTLParser;
import owl.ltl.LabelledFormula;

public final class LtlParser {
  private LtlParser() {}

  /**
   * Parses the LTL formula of the given string as an LTL formula on infinite words.
   *
   * @param formula the string containing the formula.
   * @return the syntax tree of the formula annotated with a list of atomic propositions.
   */
  public static LabelledFormula parse(String formula) {
    return parse(formula, null);
  }

  /**
   * Parses the LTL formula of the given string as an LTL formula on infinite words.
   *
   * @param formula the string containing the formula.
   * @param atomicPropositions the list of atomic propositions. If null is passed, then the list
   *     of atomic propositions is extracted from the formula string.
   * @return the syntax tree of the formula annotated with a list of atomic propositions.
   */
  public static LabelledFormula parse(String formula, @Nullable List<String> atomicPropositions) {
    // Tokenize the stream
    var lexer = new LTLLexer(CharStreams.fromString(formula));
    // Don't print long error messages on the console
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    // Add a fail-fast behaviour for token errors
    lexer.addErrorListener(new TokenErrorListener());
    var tokens = new CommonTokenStream(lexer);

    // Parse the tokens
    var parser = new LTLParser(tokens);
    // Set fail-fast behaviour for grammar errors
    parser.setErrorHandler(new BailErrorStrategy());

    // Convert the AST into a proper object
    var treeVisitor = atomicPropositions == null
      ? new LtlParseTreeVisitor()
      : new LtlParseTreeVisitor(atomicPropositions);

    return LabelledFormula.of(
      treeVisitor.visit(parser.formula()),
      treeVisitor.atomicPropositions());
  }
}
