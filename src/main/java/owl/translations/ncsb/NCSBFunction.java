package owl.translations.ncsb;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.run.modules.OwlModuleParser.TransformerParser;

import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.collections.ValuationSet;
import owl.run.modules.ImmutableTransformerParser;

public class NCSBFunction<S> implements
  Function<LimitDeterministicAutomaton<S, S, BuchiAcceptance, Object>,
    Automaton<State<S>, BuchiAcceptance>> {
  @SuppressWarnings("unchecked")
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("ldba-complement")
    .description("Complement a limit-deterministic Büchi automaton")
    .parser(settings -> environment -> {
      NCSBFunction<Object> function = new NCSBFunction<>();
      return (input, context) -> {
        checkArgument(input instanceof LimitDeterministicAutomaton);
        checkArgument(((LimitDeterministicAutomaton<?, ?, ?, ?>) input)
          .acceptingComponent().acceptance() instanceof BuchiAcceptance);
        return function.apply(
          (LimitDeterministicAutomaton<Object, Object, BuchiAcceptance, Object>) input);
      };
    })
    .build();


  @Override
  public Automaton<State<S>, BuchiAcceptance> apply(
    LimitDeterministicAutomaton<S, S, BuchiAcceptance, Object> ldba) {
    MutableAutomaton<State<S>, BuchiAcceptance> automaton =
      MutableAutomatonFactory.create(BuchiAcceptance.INSTANCE, ldba.initialComponent().factory());

    Set<State<S>> initialStates = new HashSet<>();
    Set<S> ldbaInitialStates = ldba.initialComponent().initialStates();
    Set<S> ldbaAcceptingInitialStates = ldba.acceptingComponent().initialStates();
    for (Set<S> cStates : Sets.powerSet(ldbaAcceptingInitialStates)) {
      Sets.SetView<S> sStates = Sets.difference(ldbaAcceptingInitialStates, cStates);
      initialStates.add(State.of(ldbaInitialStates, cStates, sStates, cStates));
    }

    AutomatonUtil.explore(automaton, initialStates, (State<S> state, BitSet valuation) -> {
      Set<S> cNonAcceptingSuccessors = new HashSet<>();
      Set<S> cAcceptingSuccessors = new HashSet<>();

      for (S cState : state.c()) {
        Edge<S> edge = ldba.acceptingComponent().edge(cState, valuation);
        if (edge == null) {
          // C state without successor - block
          return Set.of();
        } else if (edge.inSet(0)) {
          cAcceptingSuccessors.add(edge.successor());
        } else {
          cNonAcceptingSuccessors.add(edge.successor());
        }
      }
      // Resolve conflicts
      cAcceptingSuccessors.removeAll(cNonAcceptingSuccessors);

      Set<S> sSuccessors = new HashSet<>();
      for (S sState : state.s()) {
        Edge<S> edge = ldba.acceptingComponent().edge(sState, valuation);
        if (edge == null) {
          continue;
        }
        if (edge.inSet(0)) {
          // S set has seen an accepting edge - block
          return Set.of();
        }
        sSuccessors.add(edge.successor());
      }
      cAcceptingSuccessors.removeAll(sSuccessors);
      cNonAcceptingSuccessors.removeAll(sSuccessors);

      Set<S> nSuccessors = state.n().stream()
        .map((S s) -> ldba.initialComponent().successors(s, valuation))
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());

      Set<S> nToAcceptingSuccessors = state.n().stream()
        .flatMap((S s) -> ldba.valuationSetJumps(s).entrySet().stream())
        .filter((Map.Entry<ValuationSet, Set<S>> entry) -> entry.getKey().contains(valuation))
        .flatMap(entry -> entry.getValue().stream())
        .filter(s -> !cNonAcceptingSuccessors.contains(s) && !sSuccessors.contains(s))
        .collect(Collectors.toSet());

      assert state.n().stream().allMatch(s -> ldba.epsilonJumps(s).isEmpty());

      Set<S> nondeterministicSuccessors = Sets.union(nToAcceptingSuccessors, cAcceptingSuccessors);

      Set<Edge<State<S>>> edges = new HashSet<>();
      for (Set<S> split : Sets.powerSet(nondeterministicSuccessors)) {
        Set<S> splitRemainder = Sets.difference(nondeterministicSuccessors, split);

        Set<S> cSuccessors = Sets.union(cNonAcceptingSuccessors, split);
        Set<S> sSplitSuccessors = Sets.union(sSuccessors, splitRemainder);

        Set<S> bSuccessors = state.b().stream()
          .map(s -> ldba.acceptingComponent().successor(s, valuation))
          .filter(cSuccessors::contains)
          .collect(Collectors.toSet());
        if (bSuccessors.isEmpty()) {
          State<S> successor = State.of(nSuccessors, cSuccessors, sSplitSuccessors, cSuccessors);
          edges.add(Edge.of(successor, 0));
        } else {
          State<S> successor = State.of(nSuccessors, cSuccessors, sSplitSuccessors, bSuccessors);
          edges.add(Edge.of(successor));
        }
      }

      return edges;
    });
    automaton.initialStates(initialStates);
    return automaton;
  }
}
