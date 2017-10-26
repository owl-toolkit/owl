package owl.automaton;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.Collections;
import org.junit.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.factories.Registry;
import owl.factories.ValuationSetFactory;

public class AutomatonFactoryTest {

  @Test
  public void testSingleton() {
    ValuationSetFactory factory = Registry.getFactories(1).valuationSetFactory;
    Object singletonState = new Object();
    Automaton<Object, NoneAcceptance> singleton =
      AutomatonFactory.singleton(singletonState, factory);


    assertThat(singleton.getStates(), contains(singletonState));
    assertThat(singleton.getAcceptance(), is(NoneAcceptance.INSTANCE));
    assertThat(singleton.getEdges(singletonState), empty());
    assertThat(singleton.getIncompleteStates(),
      is(Collections.singletonMap(singletonState, factory.createUniverseValuationSet())));
    assertThat(singleton.getReachableStates(), contains(singletonState));
    assertThat(singleton.getSuccessorMap(singletonState).entrySet(), empty());
    assertThat(singleton.getLabelledEdges(singletonState), empty());
  }

  @Test
  public void testUniverse() {
    ValuationSetFactory factory = Registry.getFactories(1).valuationSetFactory;
    Object singletonState = new Object();
    Automaton<Object, AllAcceptance> singleton =
      AutomatonFactory.universe(singletonState, factory);

    assertThat(singleton.getStates(), contains(singletonState));
    assertThat(singleton.getAcceptance(), is(AllAcceptance.INSTANCE));
    //noinspection unchecked
    assertThat(singleton.getEdges(singletonState), contains(Edges.create(singletonState)));

    assertThat(singleton.getIncompleteStates().entrySet(), empty());
    assertThat(singleton.getReachableStates(), contains(singletonState));
    assertThat(singleton.getSuccessorMap(singletonState).entrySet(), iterableWithSize(1));
    LabelledEdge<Object> selfLoop = new LabelledEdge<>(Edges.create(singletonState),
      factory.createUniverseValuationSet());
    assertThat(singleton.getLabelledEdges(singletonState), hasItem(selfLoop));
  }
}
