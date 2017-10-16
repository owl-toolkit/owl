package owl.automaton;

import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.automaton.ArenaFactory.Node;
import owl.automaton.acceptance.ParityAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.Optimisation;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public class ArenaFactoryTest {
  private static final LTL2DPAFunction TRANSLATION;

  static {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    TRANSLATION = new LTL2DPAFunction(optimisations, false);
  }

  @Test
  public void transform() throws Exception {
    LabelledFormula formula = LtlParser.parse("G (a <-> X b) & G F (!a | b | c)");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.castAutomaton(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);
    Arena<Node<Object>, ParityAcceptance> arena =
      ArenaFactory.transform(automaton, ImmutableList.of("a","c"));

    for (Node<Object> state : arena.getStates()) {
      for (Node<Object> predecessor : arena.getPredecessors(state)) {
        assertThat(state, Matchers.isIn(arena.getSuccessors(predecessor)));
      }

      for (Node<Object> successors : arena.getSuccessors(state)) {
        assertThat(state, Matchers.isIn(arena.getPredecessors(successors)));
      }
    }

    // System.out.print(AutomatonUtil.toHoa(arena));
  }

}