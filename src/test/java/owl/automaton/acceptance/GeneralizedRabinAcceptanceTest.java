package owl.automaton.acceptance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeneralizedRabinAcceptanceTest {
  @Test
  public void testGetFiniteSet() {
    GeneralizedRabinAcceptance acceptance = new GeneralizedRabinAcceptance();
    GeneralizedRabinAcceptance.GeneralizedRabinPair pair = acceptance.createPair();
    int finiteIndex = pair.getOrCreateFiniteIndex();
    assertThat(pair.getFiniteIndex(), is(finiteIndex));
  }

  @Test
  public void testGetInfiniteSet() {
    GeneralizedRabinAcceptance acceptance = new GeneralizedRabinAcceptance();
    GeneralizedRabinAcceptance.GeneralizedRabinPair pair = acceptance.createPair();
    int infiniteIndex = pair.createInfiniteSet();
    assertTrue(pair.getInfiniteIndices().contains(infiniteIndex));
    assertThat(pair.getInfiniteSetCount(), is(1));
  }
}
