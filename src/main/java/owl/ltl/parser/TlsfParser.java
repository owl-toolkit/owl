package owl.ltl.parser;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.function.Function;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.misc.ParseCancellationException;
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

public final class TlsfParser {
  private TlsfParser() {
  }

  public static Tlsf parse(String input) {
    return parse(new ANTLRInputStream(input));
  }

  @SuppressWarnings("PMD.ConfusingTernary")
  private static Tlsf parse(ANTLRInputStream stream) {
    final TLSFLexer lexer = new TLSFLexer(stream);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final TLSFParser parser = new TLSFParser(tokens);
    parser.setErrorHandler(new BailErrorStrategy());

    final TlsfContext tree;
    tree = parser.tlsf();

    final Builder builder = ImmutableTlsf.builder();

    // Info
    builder.title(tree.title.getText());
    builder.description(tree.description.getText());
    final SemanticsContext semantics = tree.semantics();
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
    final TargetContext target = tree.target();
    if (target.MEALY() != null) {
      builder.target(Semantics.MEALY);
    } else if (target.MOORE() != null) {
      builder.target(Semantics.MOORE);
    } else {
      throw new ParseCancellationException("Unknown semantics");
    }

    // Input / output
    final BitSet inputs = new BitSet();
    final BitSet outputs = new BitSet();
    final BiMap<String, Integer> variables = HashBiMap.create();
    final Function<String, Integer> valueFunction = key -> variables.size();
    for (final TerminalNode variableNode : tree.input().VAR_ID()) {
      final String variableName = variableNode.getText();
      int index = variables.computeIfAbsent(variableName, valueFunction);
      inputs.set(index);
    }
    for (final TerminalNode variableNode : tree.output().VAR_ID()) {
      final String variableName = variableNode.getText();
      int index = variables.computeIfAbsent(variableName, valueFunction);
      outputs.set(index);
    }
    builder.inputs(inputs);
    builder.outputs(outputs);
    builder.mapping(variables);

    // Specifications
    Collection<Formula> initial = new ArrayList<>();
    Collection<Formula> preset = new ArrayList<>();
    Collection<Formula> require = new ArrayList<>();
    Collection<Formula> assert_ = new ArrayList<>();
    Collection<Formula> assume = new ArrayList<>();
    Collection<Formula> guarantee = new ArrayList<>();
    for (final SpecificationContext specificationContext : tree.specification()) {
      final String formulaString = specificationContext.formula.getText();
      assert !formulaString.isEmpty();
      // Strip trailing ;
      final String sanitizedFormula = formulaString
        .substring(0, formulaString.length() - 1)
        .trim();
      final Formula formula = LtlParser.parse(sanitizedFormula).getFormula();

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
    if (!initial.isEmpty()) {
      //noinspection ConstantConditions
      builder.initially(Conjunction.create(initial.stream()));
    }
    if (!preset.isEmpty()) {
      //noinspection ConstantConditions
      builder.preset(Conjunction.create(preset.stream()));
    }
    if (!require.isEmpty()) {
      //noinspection ConstantConditions
      builder.preset(Conjunction.create(require.stream()));
    }
    if (!assert_.isEmpty()) {
      //noinspection ConstantConditions
      builder.assert_(Conjunction.create(assert_.stream()));
    }
    if (!assume.isEmpty()) {
      //noinspection ConstantConditions
      builder.assume(Conjunction.create(assume.stream()));
    }
    if (!guarantee.isEmpty()) {
      //noinspection ConstantConditions
      builder.guarantee(Conjunction.create(guarantee.stream()));
    }

    return builder.build();
  }

  public static Tlsf parse(InputStream input) throws IOException {
    return parse(new ANTLRInputStream(input));
  }
}
