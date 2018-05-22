package owl.automaton;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.EnumSet;
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
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

public class AutomatonFactoryTest {

  @SuppressWarnings("unchecked")
  @Test
  public void testSingleton() {
    ValuationSetFactory factory = DefaultEnvironment.annotated().factorySupplier()
      .getValuationSetFactory(List.of("a"));
    Object singletonState = new Object();
    Automaton<Object, NoneAcceptance> singleton =
      AutomatonFactory.singleton(factory, singletonState, NoneAcceptance.INSTANCE, Set.of());

    assertThat(singleton.states(), contains(singletonState));
    assertThat(singleton.acceptance(), is(NoneAcceptance.INSTANCE));
    assertThat(singleton.edges(singletonState), contains(Edge.of(singletonState)));
    assertThat(AutomatonUtil.getIncompleteStates(singleton), is(Map.of()));
    assertThat(AutomatonUtil.getReachableStates(singleton), contains(singletonState));
  }

  @Test
  public void testUniverse() {
    ValuationSetFactory factory = DefaultEnvironment.annotated().factorySupplier()
      .getValuationSetFactory(List.of("a"));
    Object singletonState = new Object();
    Automaton<Object, AllAcceptance> singleton =
      AutomatonFactory.singleton(factory, singletonState, AllAcceptance.INSTANCE, Set.of());

    assertThat(singleton.states(), contains(singletonState));
    assertThat(singleton.acceptance(), is(AllAcceptance.INSTANCE));
    //noinspection unchecked
    assertThat(singleton.edges(singletonState), contains(Edge.of(singletonState)));

    assertThat(AutomatonUtil.getIncompleteStates(singleton).entrySet(), empty());
    assertThat(AutomatonUtil.getReachableStates(singleton), contains(singletonState));
    assertThat(singleton.labelledEdges(singletonState), iterableWithSize(1));
    LabelledEdge<Object> selfLoop = LabelledEdge.of(singletonState,
      factory.universe());
    assertThat(singleton.labelledEdges(singletonState), hasItem(selfLoop));
  }

  @Test
  public void create() {
    var automaton = AutomatonUtil.cast((new LTL2DAFunction(DefaultEnvironment.annotated(), true,
      EnumSet.of(LTL2DAFunction.Constructions.SAFETY))).apply(LtlParser.parse("G a | b R c")),
      EquivalenceClass.class, AllAcceptance.class);

    var initialState = automaton.onlyInitialState();
    var labelledEdges = automaton.labelledEdges(automaton.onlyInitialState());

    automaton.factory().forEach(valuation -> {
      var edge = automaton.edge(initialState, valuation);
      var matchingEdges = labelledEdges.stream()
        .filter(x -> x.valuations.contains(valuation))
        .map(x -> x.edge)
        .collect(Collectors.toUnmodifiableSet());

      if (edge == null) {
        assertThat(matchingEdges, empty());
      } else {
        assertThat(matchingEdges, contains(edge));
      }
    });
  }
}
