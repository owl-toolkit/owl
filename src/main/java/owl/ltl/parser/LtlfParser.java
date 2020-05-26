/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import owl.grammar.LTLLexer;
import owl.grammar.LTLParser;
import owl.ltl.LabelledFormula;

public final class LtlfParser {
  private LtlfParser() {}

  public static LabelledFormula parse(String string) {
    return parse(string, null);
  }

  public static LabelledFormula parse(String string, @Nullable List<String> atomicPropositions) {
    // Tokenize the stream
    var lexer = new LTLLexer(CharStreams.fromString(string));
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
    var treeVisitor = atomicPropositions == null || atomicPropositions.isEmpty()
      ? new LtlfParseTreeVisitor()
      : new LtlfParseTreeVisitor(atomicPropositions);

    return LabelledFormula.of(
      treeVisitor.visit(parser.formula()),
      treeVisitor.atomicPropositions());
  }

  public static LabelledFormula parseAndTranslateToLtl(String string) {
    return parseAndTranslateToLtl(string, null);
  }

  public static LabelledFormula parseAndTranslateToLtl(String string,
    @Nullable List<String> atomicPropositions) {
    var ltlf = parse(string, atomicPropositions);
    var ltl = LtlfToLtlTranslator.translate(ltlf.formula(), ltlf.atomicPropositions().size());

    var randomID = new Random();
    var atomicPropositionsWithTail = new ArrayList<>(ltlf.atomicPropositions());

    if (!atomicPropositionsWithTail.contains("tail")) {
      atomicPropositionsWithTail.add("tail");
    }

    while (ltlf.atomicPropositions().size() == atomicPropositionsWithTail.size()) {
      String tail = "tail_" + randomID.nextInt();

      if (!atomicPropositionsWithTail.contains(tail)) {
        atomicPropositionsWithTail.add(tail);
      }
    }

    return LabelledFormula.of(ltl, atomicPropositionsWithTail);
  }
}
