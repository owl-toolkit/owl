package owl.translations;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.BitSet;
import java.util.EnumSet;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;

public class LTL2DAModuleFunctionTest {
  static final String HUGE_ALPHABET = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)"
      + "& X G (x1 | x2 | x3)";

  static final LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(),
    true, EnumSet.of(
      LTL2DAFunction.Constructions.SAFETY,
      LTL2DAFunction.Constructions.CO_SAFETY,
      LTL2DAFunction.Constructions.BUCHI,
      LTL2DAFunction.Constructions.CO_BUCHI,
      LTL2DAFunction.Constructions.PARITY));

  @Test
  public void construct() {
    var formula = LtlParser.parse("a | b R X c");

    var automaton = translator.apply(formula);
    var complementAutomaton = translator.apply(formula.not());

    assertThat(automaton.size(), is(complementAutomaton.size()));
    assertThat(EmptinessCheck.isEmpty(automaton), is(false));
    assertThat(EmptinessCheck.isEmpty(complementAutomaton), is(false));
  }

  @Test
  public void performanceSafety() {
    var formula = LtlParser.parse(HUGE_ALPHABET);
    assertThat(formula.variables().size(), is(29));

    var automaton = (Automaton<Object, ?>) translator.apply(formula);
    var state = automaton.initialState();

    // Check null successor.
    BitSet empty = new BitSet();
    assertThat(automaton.edge(state, empty), is(nullValue()));
    assertTrue(automaton.labelledEdges(state).stream()
      .noneMatch(x -> x.valuations.contains(empty)));
  }

  @Test
  public void performanceCosafety() {
    var formula = LtlParser.parse(HUGE_ALPHABET).not();
    assertThat(formula.variables().size(), is(29));

    var automaton = (Automaton<EquivalenceClass, ?>) translator.apply(formula);
    var state = automaton.initialState().factory().getTrue();
    var edge = Edge.of(state, 0);

    // Check true sink.
    BitSet empty = new BitSet();

    assertThat(automaton.edge(state, empty), is(edge));
    assertThat(automaton.labelledEdges(state),
      contains(LabelledEdge.of(edge, automaton.factory().universe())));
  }
}