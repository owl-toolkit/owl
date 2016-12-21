package omega_automaton.acceptance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeneralizedRabinAcceptanceTest {
  @Test
  public void testGetFiniteSet() {
    final GeneralizedRabinAcceptanceLazy acceptance = new GeneralizedRabinAcceptanceLazy();
    final GeneralizedRabinAcceptanceLazy.GeneralizedRabinPair pair = acceptance.createPair();
    final int finiteIndex = pair.getOrCreateFiniteIndex();
    assertThat(pair.getFiniteIndex(), is(finiteIndex));
  }

  @Test
  public void testGetInfiniteSet() {
    final GeneralizedRabinAcceptanceLazy acceptance = new GeneralizedRabinAcceptanceLazy();
    final GeneralizedRabinAcceptanceLazy.GeneralizedRabinPair pair = acceptance.createPair();
    final int infiniteIndex = pair.createInfiniteSet();
    assertTrue(pair.getInfiniteIndices().contains(infiniteIndex));
    assertThat(pair.getInfiniteSetCount(), is(1));
  }
}
