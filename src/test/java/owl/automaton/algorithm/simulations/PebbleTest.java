package owl.automaton.algorithm.simulations;

import static owl.automaton.algorithm.simulations.CommonAutomata.predecessorAutomaton;

import java.util.BitSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import owl.collections.ValuationSet;

public class PebbleTest {
  @Test
  void predecessorsTest() {
    var expectedForA = Set.of(
      Pebble.of(2, false), Pebble.of(3, false), Pebble.of(4, true)
    );
    var expectedForB = Set.of(
      Pebble.of(2, false)
    );

    var aut = predecessorAutomaton();
    BitSet a = aut.factory().iterator(aut.factory().of(0)).next();
    BitSet b = aut.factory().iterator(aut.factory().of(1)).next();
    ValuationSet ab = aut.factory().of(a).union(aut.factory().of(b));

    assert Pebble.of(4, false).predecessors(aut, a).equals(expectedForA);
    assert Pebble.of(4, false).predecessors(aut, b).isEmpty();
    assert Pebble.of(1, false).predecessors(aut, a).isEmpty();

    var combined = Pebble.of(4, false).predecessors(aut, ab);
    assert combined.containsAll(expectedForA);
    assert combined.containsAll(expectedForB);
  }
}
