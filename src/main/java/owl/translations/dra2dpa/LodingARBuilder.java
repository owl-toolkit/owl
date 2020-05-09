package owl.translations.dra2dpa;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.cli.Options;
import owl.automaton.AbstractImmutableAutomaton.NonDeterministicEdgeMapAutomaton;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.SingletonAutomaton;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.ParityAcceptanceOptimizations;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.run.modules.OwlModule;

public class LodingARBuilder<R> {
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "loding",
    "Converts a Rabin automaton into a parity automaton using Löding's construction",
    new Options(),
    (commandLine, environment) -> OwlModule.AutomatonTransformer.of(
      automaton -> new LodingARBuilder<>(automaton).build(), RabinAcceptance.class));

  private final Automaton<R, RabinAcceptance> rabinAutomaton;

  public LodingARBuilder(Automaton<R, RabinAcceptance> rabinAutomaton) {
    this.rabinAutomaton = rabinAutomaton;
  }

  public Automaton<LIARState<R>, ParityAcceptance> build() {
    if (rabinAutomaton.initialStates().isEmpty()) {
      return EmptyAutomaton.of(rabinAutomaton.factory(), new ParityAcceptance(1, Parity.MIN_ODD));
    }

    RabinAcceptance rabinAcceptance = rabinAutomaton.acceptance();
    Set<RabinPair> rabinPairs = Set.copyOf(rabinAcceptance.pairs());
    if (rabinPairs.isEmpty()) {
      R rabinState = rabinAutomaton.initialStates().iterator().next();
      return SingletonAutomaton.of(rabinAutomaton.factory(),
        LIARState.of(rabinState, new int[] {1}, 0, 0),
        new ParityAcceptance(1, Parity.MIN_ODD), Set.of(0));
    }

    int[] record = new int[rabinPairs.size()];
    Arrays.setAll(record, i -> i);
    Set<LIARState<R>> initialStates = rabinAutomaton.initialStates().stream()
      .map(initialRabinState -> LIARState.of(initialRabinState, record, 0, 0))
      .collect(Collectors.toSet());
    LIARExplorer<R> explorer =
      new LIARExplorer<>(rabinAutomaton, initialStates, rabinPairs);
    var resultAutomaton = MutableAutomatonUtil.asMutable(explorer);
    resultAutomaton.trim();
    ParityAcceptanceOptimizations.setAcceptingSets(resultAutomaton);
    return resultAutomaton;
  }

  private static final class LIARExplorer<S> extends
    NonDeterministicEdgeMapAutomaton<LIARState<S>, ParityAcceptance> {
    private final Automaton<S, RabinAcceptance> rabinAutomaton;
    private final RabinPair[] indexToPair;

    public LIARExplorer(Automaton<S, RabinAcceptance> rabinAutomaton,
      Set<LIARState<S>> initialStates, Set<RabinPair> trackedPairs) {
      super(rabinAutomaton.factory(), initialStates, new ParityAcceptance(0, Parity.MIN_ODD));
      this.rabinAutomaton = rabinAutomaton;
      indexToPair = trackedPairs.toArray(RabinPair[]::new);
    }

    @Override
    public Map<Edge<LIARState<S>>, ValuationSet> edgeMap(LIARState<S> state) {
      return Collections3.transformMap(rabinAutomaton.edgeMap(requireNonNull(state.state())),
        rabinState -> computeSuccessorEdge(state.record(), rabinState));
    }

    private Edge<LIARState<S>> computeSuccessorEdge(int[] record, Edge<S> rabinEdge) {
      int r = record.length - 1;
      int k = IntStream.range(0, r + 1)
        .filter(i -> rabinEdge.inSet(indexToPair[record[i]].finSet()))
        .min().orElse(r + 1);
      int l = IntStream.range(0, r + 1)
        .filter(i -> rabinEdge.inSet(indexToPair[record[i]].infSet()))
        .min().orElse(r + 1);

      int[] successorRecord;
      if (k == r + 1) {
        successorRecord = record;
      } else {
        successorRecord = new int[record.length];
        System.arraycopy(record, 0, successorRecord, 0, k);
        successorRecord[r] = record[k];
        System.arraycopy(record, k + 1, successorRecord, k, r - k);
      }
      // f' = l, e' = k
      LIARState<S> successorState = LIARState.of(rabinEdge.successor(), successorRecord, l, k);

      // Magic
      int priority = k > l ? l * 2 + 1 : k * 2;
      return Edge.of(successorState, priority);
    }
  }
}
