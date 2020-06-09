package owl.automaton.algorithm.simulations;

import static owl.automaton.algorithm.simulations.CommonAutomata.predecessorAutomaton;

import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TransitionTest {
  @Test
  void validityTest() {
    var aut = predecessorAutomaton();
    var factory = aut.factory();

    BitSet a = factory.iterator(factory.of(0)).next();

    assert Transition.of(a, 2, false).isValid(1, aut);
    assert !Transition.of(a, 3, true).isValid(1, aut);
    assert Transition.of(a, 3, false).isValid(1, aut);

    assert SimulationStates.LookaheadSimulationState.of(
      1, 1,
      List.of(Transition.of(a, 2, false))
    ).isValid(aut);

    assert SimulationStates.LookaheadSimulationState.of(
      1, 1,
      Transition.of(a, 2, false).append(Transition.of(a, 4, false))
    ).isValid(aut);

    var longTransition = SimulationStates.LookaheadSimulationState.of(
      1, 1,
      Transition.of(a, 3, false).append(
        Transition.of(a, 4, false).append(
          Transition.of(a, 4, true)
        )
      )
    );
    assert longTransition.isValid(aut);
    assert longTransition.flag();

    assert !SimulationStates.LookaheadSimulationState.of(
      1, 1,
      List.of(Transition.of(a, 4, false))
    ).isValid(aut);
  }
}
