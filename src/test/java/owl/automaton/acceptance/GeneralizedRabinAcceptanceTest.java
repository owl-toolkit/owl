package owl.automaton.acceptance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import org.junit.Test;

public class GeneralizedRabinAcceptanceTest {
  @Test
  public void testGetFiniteSet() {
    GeneralizedRabinAcceptance acceptance = new GeneralizedRabinAcceptance();
    int finiteIndex = 1;
    IntList infList = new IntArrayList();
    infList.add(2);
    GeneralizedRabinAcceptance.GeneralizedRabinPair pair =
        acceptance.createPair(finiteIndex, infList);
    assertThat(pair.getFiniteIndex(), is(finiteIndex));
  }

  @Test
  public void testGetInfiniteSet() {
    GeneralizedRabinAcceptance acceptance = new GeneralizedRabinAcceptance();
    IntList infList = new IntArrayList();
    infList.add(2);
    infList.add(3);
    GeneralizedRabinAcceptance.GeneralizedRabinPair pair = acceptance.createPair(1, infList);
    assertTrue(pair.getInfiniteIndices().contains(infList.getInt(0)));
    assertThat(pair.getInfiniteSetCount(), is(2));
  }
}
