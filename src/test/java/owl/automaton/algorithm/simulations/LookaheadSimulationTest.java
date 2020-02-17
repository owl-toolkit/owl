package owl.automaton.algorithm.simulations;

import static owl.automaton.algorithm.simulations.CommonAutomata.buildAutomatonOne;

import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.game.algorithms.OinkGameSolver;

public class LookaheadSimulationTest {
  @Test
  void simpleDirectTest() {
    if (!OinkGameSolver.checkOinkExecutable()) {
      return;
    }
    var aut = buildAutomatonOne();

    var game = new SimulationGame<>(
      ForwardDirectLookaheadSimulation.of(
        aut, aut, 1, 5, 1, Set.of()
      )
    );

    assert new OinkGameSolver().solve(game)
      .playerEven()
      .contains(game.onlyInitialState());
  }
}
