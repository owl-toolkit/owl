package owl.ltl.parser;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
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
import org.antlr.v4.runtime.tree.TerminalNode;
import owl.grammar.TLSFLexer;
import owl.grammar.TLSFParser;
import owl.grammar.TLSFParser.SemanticsContext;
import owl.grammar.TLSFParser.SpecificationContext;
import owl.grammar.TLSFParser.TargetContext;
import owl.grammar.TLSFParser.TlsfContext;
import owl.ltl.Conjunction;
import owl.ltl.LabelledFormula;
import owl.ltl.tlsf.ImmutableTlsf;
import owl.ltl.tlsf.ImmutableTlsf.Builder;
import owl.ltl.tlsf.Tlsf;
import owl.ltl.tlsf.Tlsf.Semantics;

public final class TlsfParser {
  private TlsfParser() {}

  public static Tlsf parse(String input) {
    return parse(CharStreams.fromString(input));
  }

  @SuppressWarnings("PMD.ConfusingTernary")
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
    BitSet inputs = new BitSet();
    BitSet outputs = new BitSet();
    BiMap<String, Integer> variables = HashBiMap.create();

    for (TerminalNode variableNode : tree.input().VAR_ID()) {
      String variableName = variableNode.getText();
      int index = variables.computeIfAbsent(variableName, key -> variables.size());
      inputs.set(index);
    }

    for (TerminalNode variableNode : tree.output().VAR_ID()) {
      String variableName = variableNode.getText();
      int index = variables.computeIfAbsent(variableName, key -> variables.size());
      outputs.set(index);
    }

    builder.inputs(inputs);
    builder.outputs(outputs);
    builder.mapping(variables);

    // Specifications
    List<LabelledFormula> initial = new ArrayList<>();
    List<LabelledFormula> preset = new ArrayList<>();
    List<LabelledFormula> require = new ArrayList<>();
    List<LabelledFormula> assert_ = new ArrayList<>();
    List<LabelledFormula> assume = new ArrayList<>();
    List<LabelledFormula> guarantee = new ArrayList<>();

    for (SpecificationContext specificationContext : tree.specification()) {
      String formulaString = specificationContext.formula.getText();
      assert !formulaString.isEmpty();
      // Strip trailing ;
      String sanitizedFormula = formulaString
        .substring(0, formulaString.length() - 1)
        .trim();
      LabelledFormula formula = LtlParser.parse(sanitizedFormula);

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
      builder.initially(Conjunction.of(initial.stream().map(LabelledFormula::getFormula)));
    }
    if (!preset.isEmpty()) {
      //noinspection ConstantConditions
      builder.preset(Conjunction.of(preset.stream().map(LabelledFormula::getFormula)));
    }
    if (!require.isEmpty()) {
      //noinspection ConstantConditions
      builder.preset(Conjunction.of(require.stream().map(LabelledFormula::getFormula)));
    }
    if (!assert_.isEmpty()) {
      //noinspection ConstantConditions
      builder.assert_(Conjunction.of(assert_.stream().map(LabelledFormula::getFormula)));
    }
    if (!assume.isEmpty()) {
      //noinspection ConstantConditions
      builder.assume(Conjunction.of(assume.stream().map(LabelledFormula::getFormula)));
    }
    if (!guarantee.isEmpty()) {
      //noinspection ConstantConditions
      builder.guarantee(Conjunction.of(guarantee.stream().map(LabelledFormula::getFormula)));
    }

    return builder.build();
  }

  public static Tlsf parse(InputStream input, Charset charset) throws IOException {
    return parse(CharStreams.fromStream(input, charset));
  }

  public static Tlsf parse(Reader input) throws IOException {
    return parse(CharStreams.fromReader(input));
  }
}
