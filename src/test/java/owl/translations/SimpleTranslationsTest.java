package owl.translations;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.BitSet;
import org.junit.Test;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;

public class SimpleTranslationsTest {
  public static final String HUGE_ALPHABET = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)"
      + "& X G (x1 | x2 | x3)";

  @Test
  public void construct() {
    var formula = LtlParser.parse("a | b R X c");

    var automaton = SimpleTranslations.buildSafety(formula, DefaultEnvironment.annotated());
    var complementAutomaton =
      SimpleTranslations.buildCoSafety(formula.not(), DefaultEnvironment.annotated());

    assertThat(automaton.size(), is(complementAutomaton.size()));
    assertThat(EmptinessCheck.isEmpty(automaton), is(false));
    assertThat(EmptinessCheck.isEmpty(complementAutomaton), is(false));
  }

  @Test
  public void performanceSafety() {
    var formula = LtlParser.parse(HUGE_ALPHABET);
    assertThat(formula.variables().size(), is(29));

    var automaton = SimpleTranslations.buildSafety(formula, DefaultEnvironment.annotated());
    var state = automaton.getInitialState();

    // Check null successor.
    BitSet empty = new BitSet();
    assertThat(automaton.getEdge(state, empty), is(nullValue()));
    assertTrue(automaton.getLabelledEdges(state).stream()
      .noneMatch(x -> x.valuations.contains(empty)));
  }

  @Test
  public void performanceCosafety() {
    var formula = LtlParser.parse(HUGE_ALPHABET).not();
    assertThat(formula.variables().size(), is(29));

    var automaton = SimpleTranslations.buildCoSafety(formula, DefaultEnvironment.annotated());
    var state = automaton.getInitialState().factory().getTrue();
    var edge = Edge.of(state, 0);

    // Check true sink.
    BitSet empty = new BitSet();

    assertThat(automaton.getEdge(state, empty), is(edge));
    assertThat(automaton.getLabelledEdges(state),
      contains(LabelledEdge.of(edge, automaton.getFactory().universe())));
  }
}