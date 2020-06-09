package owl.automaton.algorithm.simulations;

import static owl.automaton.algorithm.simulations.CommonAutomata.anotherRefinementAutomaton;
import static owl.automaton.algorithm.simulations.CommonAutomata.simpleColorRefinementAutomaton;

import org.junit.jupiter.api.Test;
import owl.game.algorithms.OinkGameSolver;

public class DirectColorRefinementTest {
  @Test
  void simpleAutomatonTest() {
    if (!OinkGameSolver.checkOinkExecutable()) {
      return;
    }
    var aut = simpleColorRefinementAutomaton();
    var otherAut = anotherRefinementAutomaton();

    var refinementRel = ColorRefinement.of(aut);
    var gameRel = new BuchiSimulation().directSimulation(aut, aut, 1);
    assert refinementRel.containsAll(gameRel);
    assert gameRel.containsAll(refinementRel);

    var otherRefinementRel = ColorRefinement.of(otherAut);
    var otherGameRel = new BuchiSimulation().directSimulation(otherAut, otherAut, 1);
    assert otherRefinementRel.containsAll(otherGameRel);
    assert otherGameRel.containsAll(otherRefinementRel);
  }
}
