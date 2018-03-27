package owl.automaton;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.iterableWithSize;

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.SimpleTranslations;

public class AutomatonFactoryTest {

  @SuppressWarnings("unchecked")
  @Test
  public void testSingleton() {
    ValuationSetFactory factory = DefaultEnvironment.annotated().factorySupplier()
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
    ValuationSetFactory factory = DefaultEnvironment.annotated().factorySupplier()
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
    assertThat(singleton.getLabelledEdges(singletonState), iterableWithSize(1));
    LabelledEdge<Object> selfLoop = LabelledEdge.of(singletonState,
      factory.universe());
    assertThat(singleton.getLabelledEdges(singletonState), hasItem(selfLoop));
  }

  @Test
  public void create() {
    var automaton = SimpleTranslations.buildSafety(
      LtlParser.parse("G a | b R c"),
      DefaultEnvironment.annotated());

    var initialState = automaton.getInitialState();
    var labelledEdges = automaton.getLabelledEdges(automaton.getInitialState());

    for (BitSet valuation : BitSets.powerSet(automaton.getFactory().alphabetSize())) {
      var edge = automaton.getEdge(initialState, valuation);
      var matchingEdges = labelledEdges.stream()
        .filter(x -> x.valuations.contains(valuation))
        .map(x -> x.edge)
        .collect(Collectors.toUnmodifiableSet());

      if (edge == null) {
        assertThat(matchingEdges, empty());
      } else {
        assertThat(matchingEdges, contains(edge));
      }
    }
  }
}
