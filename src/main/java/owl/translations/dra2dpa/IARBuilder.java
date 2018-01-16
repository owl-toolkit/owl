package owl.translations.dra2dpa;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance.RabinPair;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;
import owl.run.Environment;
import owl.run.modules.ImmutableTransformerSettings;
import owl.run.modules.InputReaders;
import owl.run.modules.ModuleSettings.TransformerSettings;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class IARBuilder<R> {
  public static final TransformerSettings SETTINGS = ImmutableTransformerSettings.builder()
    .key("dra2dpa")
    .transformerSettingsParser(settings -> Transformers.RABIN_TO_PARITY)
    .build();

  private static final Logger logger = Logger.getLogger(IARBuilder.class.getName());
  private final Environment environment;
  private final Automaton<R, RabinAcceptance> rabinAutomaton;
  private final MutableAutomaton<IARState<R>, ParityAcceptance> resultAutomaton;
  private final ValuationSetFactory vsFactory;

  public IARBuilder(Automaton<R, RabinAcceptance> rabinAutomaton, Environment environment) {
    this.rabinAutomaton = rabinAutomaton;
    vsFactory = rabinAutomaton.getFactory();
    resultAutomaton = MutableAutomatonFactory.create(new ParityAcceptance(0), vsFactory);
    this.environment = environment;
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("dra2dpa")
      .reader(InputReaders.HoaReader.DEFAULT)
      .addTransformer(SETTINGS)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.ToHoa.DEFAULT)
      .build());
  }

  public Automaton<IARState<R>, ParityAcceptance> build() throws ExecutionException {
    logger.log(Level.FINE, "Building IAR automaton with SCC decomposition");
    logger.log(Level.FINEST, () -> "Input automaton is\n" + AutomatonUtil.toHoa(rabinAutomaton));

    Set<RabinPair> rabinPairs = ImmutableSet.copyOf(rabinAutomaton.getAcceptance().getPairs());

    // Start analysis
    List<Set<R>> rabinSccs = SccDecomposition.computeSccs(rabinAutomaton);
    logger.log(Level.FINER, "Found {0} SCCs", rabinSccs.size());

    // TODO Threading once we have a thread safe BDD library
    CompletionService<SccProcessingResult<R>> completionService =
      new ExecutorCompletionService<>(MoreExecutors.directExecutor());

    // Start possibly parallel execution
    for (Set<R> rabinScc : rabinSccs) {
      completionService.submit(() -> processScc(rabinScc, rabinPairs));
    }

    // Wait till all executions are finished

    ImmutableMultimap.Builder<R, LabelledEdge<R>> interSccConnectionsBuilder =
      ImmutableMultimap.builder();
    int completedSccs = 0;
    int maximalSubAutomatonPriority = 0;
    while (completedSccs < rabinSccs.size()) {
      try {
        logger.log(Level.FINE, "Waiting for completion");
        Future<SccProcessingResult<R>> currentResultFuture = completionService.take();
        assert currentResultFuture.isDone();

        SccProcessingResult<R> result = currentResultFuture.get();

        Automaton<IARState<R>, ?> subAutomaton = result.subAutomaton;
        OmegaAcceptance subAutomatonAcceptance = subAutomaton.getAcceptance();
        if (subAutomatonAcceptance instanceof ParityAcceptance) {
          maximalSubAutomatonPriority = Math.max(maximalSubAutomatonPriority,
            subAutomatonAcceptance.getAcceptanceSets() - 1);
        }
        interSccConnectionsBuilder.putAll(result.interSccConnections);
        resultAutomaton.addAll(subAutomaton);
        subAutomaton.free();
        completedSccs += 1;
      } catch (InterruptedException e) {
        // TODO Stop if environment.isStopped()
        logger.log(Level.FINE, "Interrupted", e);
      }
    }

    ImmutableMultimap<R, LabelledEdge<R>> interSccConnections = interSccConnectionsBuilder.build();

    // Arbitrary correspondence map
    Map<R, IARState<R>> rabinToIarStateMap =
      Maps.newHashMapWithExpectedSize(rabinAutomaton.getStates().size());
    resultAutomaton.getStates().forEach(iarState ->
      rabinToIarStateMap.put(iarState.getOriginalState(), iarState));
    assert Objects.equals(rabinToIarStateMap.keySet(), rabinAutomaton.getStates());

    logger.log(Level.FINE, "Connecting the SCCs");

    // Connect all SCCs back together
    for (IARState<R> iarState : resultAutomaton.getStates()) {
      R rabinState = iarState.getOriginalState();

      for (LabelledEdge<R> labelledEdge : interSccConnections.get(rabinState)) {
        R successorState = labelledEdge.edge.getSuccessor();
        assert rabinToIarStateMap.containsKey(successorState) :
          String.format("No successor present for %s", successorState);
        // TODO instead of 0 we should use any which is actually used
        Edge<IARState<R>> iarEdge = Edge.of(rabinToIarStateMap.get(successorState), 0);
        resultAutomaton.addEdge(iarState, labelledEdge.valuations, iarEdge);
      }
    }

    ImmutableSet.Builder<IARState<R>> initialStateBuilder = ImmutableSet.builder();

    for (R initialRabinState : rabinAutomaton.getInitialStates()) {
      initialStateBuilder.add(rabinToIarStateMap.get(initialRabinState));
    }

    resultAutomaton.setInitialStates(initialStateBuilder.build());
    resultAutomaton.getAcceptance().setAcceptanceSets(maximalSubAutomatonPriority + 1);
    assert rabinSccs.size() == SccDecomposition.computeSccs(resultAutomaton).size();

    return resultAutomaton;
  }

  private SccProcessingResult<R> getTrivialSccResult(Set<R> simpleScc,
    Multimap<R, LabelledEdge<R>> interSccConnections) {
    // TODO If it is bottom, we can just replace it by single rejecting state
    Map<R, IARState<R>> mapping = new HashMap<>(simpleScc.size());
    simpleScc.forEach(rabinState -> mapping.put(rabinState, IARState.trivial(rabinState)));

    MutableAutomaton<IARState<R>, ParityAcceptance> resultTransitionSystem =
      MutableAutomatonFactory.create(new ParityAcceptance(1), vsFactory);

    for (Map.Entry<R, IARState<R>> iarStateEntry : mapping.entrySet()) {
      R rabinState = iarStateEntry.getKey();
      IARState<R> iarState = iarStateEntry.getValue();
      Collection<LabelledEdge<R>> successors = rabinAutomaton.getLabelledEdges(rabinState);
      for (LabelledEdge<R> labelledEdge : successors) {
        R successor = labelledEdge.edge.getSuccessor();
        IARState<R> successorIARState = mapping.get(successor);
        if (successorIARState == null) {
          // The transition leads outside of the SCC
          continue;
        }
        Edge<IARState<R>> iarEdge = Edge.of(successorIARState, 0);
        resultTransitionSystem.addEdge(iarState, labelledEdge.valuations, iarEdge);
      }
    }
    // Arbitrary initial state to have nice logging
    resultTransitionSystem.setInitialState(resultTransitionSystem.getStates().iterator().next());

    return new SccProcessingResult<>(interSccConnections, resultTransitionSystem);
  }

  private SccProcessingResult<R> processScc(Set<R> scc, Set<RabinPair> rabinPairs) {
    Set<RabinPair> remainingPairsToCheck = new HashSet<>(rabinPairs);
    ImmutableMultimap.Builder<R, LabelledEdge<R>> interSccConnectionsBuilder =
      ImmutableMultimap.builder();

    boolean sccHasALoop = false;
    boolean seenAnyInfSet = false;
    boolean seenAllInfSets = false;

    // Analyse the SCC
    // TODO This could be done while doing Tarjan
    for (R state : scc) {
      for (LabelledEdge<R> labelledEdge : rabinAutomaton.getLabelledEdges(state)) {
        Edge<R> edge = labelledEdge.edge;
        if (scc.contains(edge.getSuccessor())) {
          // This transition is inside this scc
          sccHasALoop = true;
          if (seenAllInfSets) {
            continue;
          }
          // Check which of the Inf sets is active here
          // N.B. |= does not short-circuit
          seenAnyInfSet |= remainingPairsToCheck.removeIf(pair -> pair.containsInfinite(edge));
          // When remaining is create, have seen all sets
          seenAllInfSets = remainingPairsToCheck.isEmpty();
        } else {
          // This transition leads outside the SCC
          interSccConnectionsBuilder.put(state, labelledEdge);
        }
      }
    }

    ImmutableMultimap<R, LabelledEdge<R>> interSccConnections = interSccConnectionsBuilder.build();

    if (!sccHasALoop) {
      // SCC has no transition inside, it's a transient one
      checkState(scc.size() == 1);
      R transientSccState = scc.iterator().next();
      IARState<R> iarState = IARState.trivial(transientSccState);
      return new SccProcessingResult<>(interSccConnections,
        AutomatonFactory.singleton(iarState, vsFactory, NoneAcceptance.INSTANCE));
    }

    if (!seenAnyInfSet) {
      // The SCC has some internal structure, but no transitions are relevant for acceptance
      return getTrivialSccResult(scc, interSccConnections);
    }

    Set<RabinPair> activeRabinPairs;

    if (seenAllInfSets) {
      activeRabinPairs = ImmutableSet.copyOf(rabinPairs);
    } else {
      activeRabinPairs = ImmutableSet.copyOf(Sets.difference(rabinPairs, remainingPairsToCheck));
    }

    // TODO This might access the factory in parallel... Maybe we can return a lazy-explore type
    // of automaton that can be evaluated by the main thread?
    // TODO Filtered automaton?
    Automaton<IARState<R>, ParityAcceptance> subAutomaton = SccIARBuilder.from(rabinAutomaton,
      Set.of(scc.iterator().next()), scc, activeRabinPairs).build();
    return new SccProcessingResult<>(interSccConnections, subAutomaton);
  }

  static final class SccProcessingResult<R> {
    final Multimap<R, LabelledEdge<R>> interSccConnections;
    final Automaton<IARState<R>, ?> subAutomaton;

    SccProcessingResult(Multimap<R, LabelledEdge<R>> interSccConnections,
      Automaton<IARState<R>, ?> subAutomaton) {
      this.interSccConnections = ImmutableMultimap.copyOf(interSccConnections);
      this.subAutomaton = subAutomaton;
    }
  }
}