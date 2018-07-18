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

package owl.ltl.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import owl.grammar.TLSFLexer;
import owl.grammar.TLSFParser;
import owl.grammar.TLSFParser.SemanticsContext;
import owl.grammar.TLSFParser.SpecificationContext;
import owl.grammar.TLSFParser.TargetContext;
import owl.grammar.TLSFParser.TlsfContext;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.tlsf.ImmutableTlsf;
import owl.ltl.tlsf.ImmutableTlsf.Builder;
import owl.ltl.tlsf.Tlsf;
import owl.ltl.tlsf.Tlsf.Semantics;
import owl.util.annotation.CEntryPoint;

public final class TlsfParser {
  private TlsfParser() {}

  @CEntryPoint
  public static Tlsf parse(String input) {
    return parse(CharStreams.fromString(input));
  }

  private static Tlsf parse(CharStream stream) {
    TLSFLexer lexer = new TLSFLexer(stream);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    TLSFParser parser = new TLSFParser(tokens);
    parser.setErrorHandler(new BailErrorStrategy());

    TlsfContext tree = parser.tlsf();

    Builder builder = ImmutableTlsf.builder();

    // Info
    builder.title(tree.title.getText());
    builder.description(tree.description.getText());
    SemanticsContext semantics = tree.semantics();

    if (semantics.MEALY() != null) {
      builder.semantics(Semantics.MEALY);
    } else if (semantics.MOORE() != null) {
      builder.semantics(Semantics.MOORE);
    } else if (semantics.MEALY_STRICT() != null) {
      builder.semantics(Semantics.MEALY_STRICT);
    } else if (semantics.MOORE_STRICT() != null) {
      builder.semantics(Semantics.MOORE_STRICT);
    } else {
      throw new ParseCancellationException("Unknown semantics");
    }

    TargetContext target = tree.target();

    if (target.MEALY() != null) {
      builder.target(Semantics.MEALY);
    } else if (target.MOORE() != null) {
      builder.target(Semantics.MOORE);
    } else {
      throw new ParseCancellationException("Unknown semantics");
    }

    // Input / output
    List<String> variables = new ArrayList<>();
    List<String> lowercaseVariables = new ArrayList<>();

    BitSet inputs = new BitSet();
    for (TerminalNode variableNode : tree.input().VAR_ID()) {
      String variableName = variableNode.getText();

      if (lowercaseVariables.contains(variableName.toLowerCase())) {
        throw new ParseCancellationException("Duplicate variable definition: " + variableName);
      }

      inputs.set(variables.size());
      variables.add(variableName);
      lowercaseVariables.add(variableName.toLowerCase());
    }

    BitSet outputs = new BitSet();
    for (TerminalNode variableNode : tree.output().VAR_ID()) {
      String variableName = variableNode.getText();

      if (lowercaseVariables.contains(variableName.toLowerCase())) {
        throw new ParseCancellationException("Duplicate variable definition: " + variableName);
      }

      outputs.set(variables.size());
      variables.add(variableName);
      lowercaseVariables.add(variableName.toLowerCase());
    }

    builder.inputs(inputs);
    builder.outputs(outputs);
    builder.variables(List.copyOf(variables));

    // Specifications
    List<Formula> initial = new ArrayList<>();
    List<Formula> preset = new ArrayList<>();
    List<Formula> require = new ArrayList<>();
    List<Formula> assert_ = new ArrayList<>();
    List<Formula> assume = new ArrayList<>();
    List<Formula> guarantee = new ArrayList<>();

    for (SpecificationContext specificationContext : tree.specification()) {
      int children = specificationContext.children.size();

      for (ParseTree child : specificationContext.children.subList(2, children - 1)) {
        String formulaString = child.getText();
        assert !formulaString.isEmpty();
        // Strip trailing ;
        String sanitizedFormula = formulaString.substring(0, formulaString.length() - 1).trim();

        // Map arbitrary variable names to lowercase variables
        for (int i = 0; i < variables.size(); i++) {
          String variableName = variables.get(i);

          for (int j = 0; j < i; j++) {
            variableName = variableName.replace(variables.get(j), lowercaseVariables.get(j));
          }

          sanitizedFormula = sanitizedFormula.replace(variableName, lowercaseVariables.get(i));
        }

        Formula formula = LtlParser.syntax(sanitizedFormula, lowercaseVariables);

        if (specificationContext.INITIALLY() != null) {
          initial.add(formula);
        } else if (specificationContext.PRESET() != null) {
          preset.add(formula);
        } else if (specificationContext.REQUIRE() != null) {
          require.add(formula);
        } else if (specificationContext.ASSERT() != null) {
          assert_.add(formula);
        } else if (specificationContext.ASSUME() != null) {
          assume.add(formula);
        } else if (specificationContext.GUARANTEE() != null) {
          guarantee.add(formula);
        } else {
          throw new ParseCancellationException("Unknown specification type");
        }
      }
    }

    builder.initially(Conjunction.of(initial));
    builder.preset(Conjunction.of(preset));
    builder.require(Conjunction.of(require));
    builder.assume(Conjunction.of(assume));
    builder.assert_(List.copyOf(assert_));
    builder.guarantee(List.copyOf(guarantee));
    return builder.build();
  }

  public static Tlsf parse(InputStream input, Charset charset) throws IOException {
    return parse(CharStreams.fromStream(input, charset));
  }

  public static Tlsf parse(Reader input) throws IOException {
    return parse(CharStreams.fromReader(input));
  }
}
