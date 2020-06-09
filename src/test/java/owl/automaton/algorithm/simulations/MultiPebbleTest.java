package owl.automaton.algorithm.simulations;

import static owl.automaton.algorithm.simulations.CommonAutomata.predecessorAutomaton;

import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MultiPebbleTest {
  @Test
  void predecessorsTest() {
    var aut = predecessorAutomaton();
    BitSet a = aut.factory().iterator(aut.factory().of(0)).next();

    var peb1 = Pebble.of(4, false);
    var peb2 = Pebble.of(3, false);
    var mp = MultiPebble.of(
      List.of(peb1, peb2),
      2
    );

    var predecessors = mp.predecessors(aut, a);
    for (var p : peb1.predecessors(aut, a)) {
      assert predecessors.contains(MultiPebble.of(List.of(p, p), 2));
    }
    for (var p : peb2.predecessors(aut, a)) {
      assert predecessors.contains(MultiPebble.of(List.of(p, p), 2));
    }
  }
}
