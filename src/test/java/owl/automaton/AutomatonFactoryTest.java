package owl.automaton;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;
import owl.run.TestEnvironment;

public class AutomatonFactoryTest {

  @Test
  public void testSingleton() {
    ValuationSetFactory factory = TestEnvironment.INSTANCE.factorySupplier()
      .getValuationSetFactory(List.of("a"));
    Object singletonState = new Object();
    Automaton<Object, NoneAcceptance> singleton =
      AutomatonFactory.singleton(singletonState, factory, NoneAcceptance.INSTANCE, Set.of());

    assertThat(singleton.getStates(), contains(singletonState));
    assertThat(singleton.getAcceptance(), is(NoneAcceptance.INSTANCE));
    assertThat(singleton.getEdges(singletonState), contains(Edge.of(singletonState)));
    assertThat(AutomatonUtil.getIncompleteStates(singleton), is(Map.of()));
    assertThat(AutomatonUtil.getReachableStates(singleton), contains(singletonState));
  }

  @Test
  public void testUniverse() {
    ValuationSetFactory factory = TestEnvironment.INSTANCE.factorySupplier()
      .getValuationSetFactory(List.of("a"));
    Object singletonState = new Object();
    Automaton<Object, AllAcceptance> singleton =
      AutomatonFactory.singleton(singletonState, factory, AllAcceptance.INSTANCE, Set.of());

    assertThat(singleton.getStates(), contains(singletonState));
    assertThat(singleton.getAcceptance(), is(AllAcceptance.INSTANCE));
    //noinspection unchecked
    assertThat(singleton.getEdges(singletonState), contains(Edge.of(singletonState)));

    assertThat(AutomatonUtil.getIncompleteStates(singleton).entrySet(), empty());
    assertThat(AutomatonUtil.getReachableStates(singleton), contains(singletonState));
    assertThat(singleton.getSuccessorMap(singletonState).entrySet(), iterableWithSize(1));
    LabelledEdge<Object> selfLoop = LabelledEdge.of(singletonState,
      factory.createUniverseValuationSet());
    assertThat(singleton.getLabelledEdges(singletonState), hasItem(selfLoop));
  }
}
