/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.translations.dra2dpa;

import static java.util.Objects.requireNonNullElse;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import de.tum.in.naturals.IntPreOrder;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.SingletonAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.optimization.ParityAcceptanceOptimizations;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class IARBuilder<R> {
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "dra2dpa",
    "Converts a Rabin automaton into a parity automaton",
    new Options()
      .addOption(new Option(null, "odd", false, "Odd priority (default: even)"))
      .addOption(new Option(null, "min", false, "Min priority (default: max)")),
    (commandLine, environment) -> {
      Parity parity = Parity.of(!commandLine.hasOption("min"), !commandLine.hasOption("odd"));
      return OwlModule.AutomatonTransformer.of(automaton ->
        new IARBuilder<>(automaton, parity).build(), RabinAcceptance.class);
    });

  private final Automaton<R, RabinAcceptance> rabinAutomaton;
  private final Parity parity;

  public IARBuilder(Automaton<R, RabinAcceptance> rabinAutomaton, Parity parity) {
    this.rabinAutomaton = rabinAutomaton;
    this.parity = parity;
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.HOA_INPUT_MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  public Automaton<IARState<R>, ParityAcceptance> build() {
    if (rabinAutomaton.initialStates().isEmpty()) {
      return EmptyAutomaton.of(rabinAutomaton.factory(), new ParityAcceptance(1, parity));
    }

    RabinAcceptance rabinAcceptance = rabinAutomaton.acceptance();
    Set<RabinPair> rabinPairs = Set.copyOf(rabinAcceptance.pairs());
    int rejectingPriority = parity.even() ? 1 : 0;
    if (rabinPairs.isEmpty()) {
      R rabinState = rabinAutomaton.initialStates().iterator().next();

      ParityAcceptance acceptance = new ParityAcceptance(parity.even() ? 2 : 1, parity);
      return SingletonAutomaton.of(rabinAutomaton.factory(), IARState.of(rabinState),
        acceptance, acceptance.rejectingSet().orElseThrow());
    }

    MutableAutomaton<IARState<R>, ParityAcceptance> resultAutomaton;

    Multimap<R, IARState<R>> stateMap = HashMultimap.create();

    ParityAcceptance acceptance = new ParityAcceptance(0, parity);
    resultAutomaton = HashMapAutomaton.of(acceptance, rabinAutomaton.factory());

    List<Set<R>> decomposition = SccDecomposition.of(rabinAutomaton).sccsWithoutTransient();

    Set<R> missingStates = new HashSet<>(rabinAutomaton.states());
    decomposition.forEach(missingStates::removeAll);

    for (Set<R> scc : decomposition) {
      Set<RabinPair> noInfEdgePairs = new HashSet<>(rabinPairs);

      for (R state : scc) {
        for (Edge<R> edge : rabinAutomaton.edges(state)) {
          noInfEdgePairs.removeIf(pair -> edge.inSet(pair.infSet()));
        }
      }

      var activeRabinPairs = Set.copyOf(Sets.difference(rabinPairs, noInfEdgePairs));
      if (activeRabinPairs.isEmpty()) {
        for (R state : scc) {
          IARState<R> iarState = IARState.of(state);
          resultAutomaton.addState(iarState);
          stateMap.put(state, iarState);
        }
        for (R state : scc) {
          rabinAutomaton.edgeMap(state).forEach((edge, valuation) -> {
            if (scc.contains(edge.successor())) {
              resultAutomaton.addEdge(IARState.of(state), valuation,
                Edge.of(IARState.of(edge.successor()), rejectingPriority));
            }
          });
        }
      } else {
        Views.Filter<R> sccFilter = Views.Filter.of(Set.of(scc.iterator().next()), scc::contains);
        var filtered = Views.filtered(rabinAutomaton, sccFilter);
        var subAutomaton = build(filtered, activeRabinPairs);
        MutableAutomatonUtil.copyInto(subAutomaton, resultAutomaton);
        subAutomaton.states().forEach(state -> stateMap.put(state.state(), state));

        assert subAutomaton.states().stream()
          .map(IARState::state)
          .collect(Collectors.toSet())
          .equals(scc);
      }
    }

    for (R missingState : missingStates) {
      IARState<R> iarState = IARState.of(missingState);
      resultAutomaton.addState(iarState);
      stateMap.put(missingState, iarState);
    }

    for (Set<R> scc : decomposition) {
      for (R state : scc) {
        Collection<IARState<R>> iarStates = stateMap.get(state);
        rabinAutomaton.edgeMap(state).forEach(
          (edge, valuation) -> {
            R successor = edge.successor();
            if (!scc.contains(successor)) {
              IARState<R> successorIar = stateMap.get(successor).iterator().next();
              for (IARState<R> iarState : iarStates) {
                resultAutomaton.addEdge(iarState, valuation, Edge.of(successorIar));
              }
            }
          }
        );
      }
    }
    for (R state : missingStates) {
      Collection<IARState<R>> iarStates = stateMap.get(state);
      rabinAutomaton.edgeMap(state).forEach(
        (edge, valuation) -> {
          R successor = edge.successor();
          IARState<R> successorIar = stateMap.get(successor).iterator().next();
          for (IARState<R> iarState : iarStates) {
            resultAutomaton.addEdge(iarState, valuation, Edge.of(successorIar));
          }
        }
      );
    }

    assert Objects.equals(stateMap.keySet(), rabinAutomaton.states());
    // Can we make this choice less arbitrary?
    resultAutomaton.initialStates(rabinAutomaton.initialStates().stream()
      .map(stateMap::get)
      .map(Collection::iterator)
      .map(Iterator::next)
      .collect(Collectors.toSet()));

    resultAutomaton.trim();
    optimizeInitialStates(resultAutomaton, false);

    resultAutomaton.trim();
    ParityAcceptanceOptimizations.setAcceptingSets(resultAutomaton);
    return resultAutomaton;
  }


  private void optimizeInitialStates(MutableAutomaton<IARState<R>, ParityAcceptance> automaton,
    boolean singleScc) {
    /* Idea: Pick good initial permutations for the initial states and remove unreachable states */

    SccDecomposition<IARState<R>> sccDecomposition = SccDecomposition.of(automaton);
    List<Set<IARState<R>>> sccs = Lists.reverse(sccDecomposition.sccsWithoutTransient());
    List<IARState<R>> newInitialStates = new ArrayList<>();
    Set<R> initialStatesToSearch = automaton.initialStates().stream()
      .map(IARState::state)
      .collect(Collectors.toSet());

    Iterable<IARState<R>> statesToSearch;
    if (singleScc) {
      statesToSearch = sccs.stream()
        .filter(sccDecomposition::isBottomScc)
        .findAny().orElseThrow();
    } else {
      statesToSearch = Iterables.concat(sccs);
    }

    for (IARState<R> state : statesToSearch) {
      R rabinState = state.state();
      if (initialStatesToSearch.remove(rabinState)) {
        newInitialStates.add(state);
        if (initialStatesToSearch.isEmpty()) {
          break;
        }
      }
    }

    if (!initialStatesToSearch.isEmpty()) {
      // Might happen that the initial state is transient in the rabin automaton, too
      assert !singleScc;
      Set<R> foundInitialStates = newInitialStates.stream()
        .map(IARState::state)
        .collect(Collectors.toSet());
      automaton.initialStates().stream()
        .filter(s -> !foundInitialStates.contains(s.state()))
        .forEach(newInitialStates::add);
    }

    assert newInitialStates.stream().map(IARState::state).collect(Collectors.toSet())
      .equals(automaton.initialStates().stream().map(IARState::state).collect(Collectors.toSet()));

    automaton.initialStates(newInitialStates);
    automaton.trim();
  }

  private void optimizeStateRefinement(MutableAutomaton<IARState<R>, ParityAcceptance> automaton) {
    /* Idea: The IAR records have a notion of "refinement". We now eliminate all states which are
     * refined by some other state. */

    Map<R, Multimap<IntPreOrder, IntPreOrder>> topElements = new HashMap<>();

    for (IARState<R> state : automaton.states()) {
      var refinements = topElements.computeIfAbsent(state.state(), k -> ArrayListMultimap.create());
      var iterator = refinements.asMap().entrySet().iterator();
      Collection<IntPreOrder> refined = new ArrayList<>();
      IntPreOrder stateOrder = state.record();

      boolean isTop = true;
      while (iterator.hasNext()) {
        var entry = iterator.next();
        IntPreOrder entryOrder = entry.getKey();
        if (entryOrder.refines(stateOrder)) {
          entry.getValue().add(stateOrder);
          isTop = false;
          break;
        }
        if (stateOrder.refines(entryOrder)) {
          refined.add(entryOrder);
          refined.addAll(entry.getValue());
          iterator.remove();
        }
      }
      if (isTop) {
        refined.add(stateOrder);
        refinements.putAll(stateOrder, refined);
      }
    }

    Table<R, IntPreOrder, IARState<R>> refinementTable = HashBasedTable.create();
    for (var entry : topElements.entrySet()) {
      R rabinState = entry.getKey();
      var refinements = entry.getValue();
      refinements.forEach((precise, coarse) -> {
        if (!coarse.equals(precise)) {
          refinementTable.put(rabinState, coarse, IARState.of(rabinState, precise));
        }
      });
    }

    // Update initial states, for each initial state, pick its refinement (if there is any)
    automaton.initialStates(automaton.initialStates().stream()
      .map(initialState -> requireNonNullElse(refinementTable.get(initialState.state(),
        initialState.record()), initialState)).collect(Collectors.toSet()));

    // Update edges
    automaton.updateEdges((state, edge) -> {
      // For each edge, pick the refined successor (if there is a refinement)
      IARState<R> successor = edge.successor();
      IARState<R> refinedSuccessor = refinementTable.get(successor.state(), successor.record());
      return refinedSuccessor == null ? edge : edge.withSuccessor(refinedSuccessor);
    });

    automaton.trim();
  }

  private IARExplorer<R> explorer(Automaton<R, RabinAcceptance> rabinAutomaton,
    Set<RabinPair> activeRabinPairs) {
    int numberOfTrackedPairs = activeRabinPairs.size();
    IntPreOrder initialRecord = IntPreOrder.coarsest(numberOfTrackedPairs);
    Set<IARState<R>> initialStates = rabinAutomaton.initialStates().stream()
      .map(initialRabinState -> IARState.of(initialRabinState, initialRecord))
      .collect(Collectors.toSet());
    return new IARExplorer<>(rabinAutomaton, initialStates, activeRabinPairs, parity);
  }

  private MutableAutomaton<IARState<R>, ParityAcceptance>
  build(Automaton<R, RabinAcceptance> rabinAutomaton, Set<RabinPair> activeRabinPairs) {
    assert SccDecomposition.of(rabinAutomaton).sccs().size() == 1;
    IARExplorer<R> explorer = explorer(rabinAutomaton, activeRabinPairs);
    var resultAutomaton = MutableAutomatonUtil.asMutable(explorer);
    optimizeInitialStates(resultAutomaton, true);
    assert SccDecomposition.of(resultAutomaton).sccs().size() == 1;
    optimizeStateRefinement(resultAutomaton);
    return resultAutomaton;
  }

  private static final class IARExplorer<S> extends
    AbstractImmutableAutomaton<IARState<S>, ParityAcceptance> {
    private final Automaton<S, RabinAcceptance> rabinAutomaton;
    private final RabinPair[] indexToPair;
    private final Parity parity;

    IARExplorer(Automaton<S, RabinAcceptance> rabinAutomaton, Set<IARState<S>> initialStates,
      Set<RabinPair> trackedPairs, Parity parity) {
      super(rabinAutomaton.factory(), initialStates, new ParityAcceptance(0, parity));
      this.rabinAutomaton = rabinAutomaton;
      indexToPair = trackedPairs.toArray(RabinPair[]::new);
      this.parity = parity;
    }

    @Nullable
    @Override
    public Edge<IARState<S>> edge(IARState<S> state, BitSet valuation) {
      Edge<S> edge = rabinAutomaton.edge(state.state(), valuation);
      return edge == null ? null : computeSuccessorEdge(state.record(), edge);
    }

    @Override
    public Set<Edge<IARState<S>>> edges(IARState<S> state) {
      IntPreOrder record = state.record();
      return Collections3.transformSet(rabinAutomaton.edges(state.state()),
        edge -> computeSuccessorEdge(record, edge));
    }

    @Override
    public Set<Edge<IARState<S>>> edges(IARState<S> state, BitSet valuation) {
      IntPreOrder record = state.record();
      return Collections3.transformSet(rabinAutomaton.edges(state.state(), valuation),
        edge -> computeSuccessorEdge(record, edge));
    }

    @Override
    public Map<Edge<IARState<S>>, ValuationSet> edgeMap(IARState<S> state) {
      IntPreOrder record = state.record();
      return Collections3.transformMap(rabinAutomaton.edgeMap(state.state()),
        rabinState -> computeSuccessorEdge(record, rabinState));
    }

    @Override
    public ValuationTree<Edge<IARState<S>>> edgeTree(IARState<S> state) {
      IntPreOrder record = state.record();
      return rabinAutomaton.edgeTree(state.state()).map(edges ->
        Collections3.transformSet(edges, edge -> computeSuccessorEdge(record, edge)));
    }

    @Override
    public List<PreferredEdgeAccess> preferredEdgeAccess() {
      return rabinAutomaton.preferredEdgeAccess();
    }

    private Edge<IARState<S>> computeSuccessorEdge(IntPreOrder record, Edge<S> rabinEdge) {
      S rabinSuccessor = rabinEdge.successor();
      int classCount = record.classes();

      if (classCount == 0) {
        int priority = parity.even() ? 1 : 0;
        return Edge.of(IARState.of(rabinEdge.successor()), priority);
      }

      int matchOffset = 0;
      boolean infMatch = false;
      // If without preorder fix ascending order arbitrarily to resolve ties equally
      BitSet seenFin = new BitSet();
      int currentOffset = 0;
      for (int currentClass = 0; currentClass < classCount; currentClass++) {
        int[] classes = record.equivalenceClass(currentClass);
        currentOffset += classes.length;
        for (int rabinIndex : classes) {
          RabinPair rabinPair = indexToPair[rabinIndex];
          if (rabinEdge.inSet(rabinPair.finSet())) {
            matchOffset = currentOffset;
            infMatch = false;
            seenFin.set(rabinIndex);
          } else if (matchOffset < currentOffset
            && rabinEdge.inSet(rabinPair.infSet())) {
            matchOffset = currentOffset;
            infMatch = true;
          }
        }
      }

      int priority;
      if (parity.max()) {
        if (parity.even()) {
          if (matchOffset == 0) {
            priority = 1;
          } else {
            priority = infMatch ? matchOffset * 2 : matchOffset * 2 + 1;
          }
        } else {
          if (matchOffset == 0) {
            priority = 0;
          } else {
            priority = infMatch ? matchOffset * 2 + 1 : matchOffset * 2 + 2;
          }
        }
      } else {
        int inverse = (indexToPair.length - matchOffset + 1) * 2;
        if (parity.even()) {
          if (matchOffset == 0) {
            priority = indexToPair.length * 2 + 1;
          } else {
            priority = infMatch ? inverse : inverse - 1;
          }
        } else {
          if (matchOffset == 0) {
            priority = indexToPair.length * 2;
          } else {
            priority = infMatch ? inverse - 1 : inverse - 2;
          }
        }
      }

      assert priority >= 0 || matchOffset == 0
        : "Negative priority for " + parity + ": Match at " + matchOffset + " ("
        + (infMatch ? "INF" : "FIN") + "), " + indexToPair.length;

      IntPreOrder successorRecord;
      // TODO Pick existing?
      successorRecord = record;

      for (int i = seenFin.nextSetBit(0); i >= 0; i = seenFin.nextSetBit(i + 1)) {
        successorRecord = successorRecord.generation(IntSets.singleton(i));

        if (i == Integer.MAX_VALUE) {
          break; // or (i+1) would overflow
        }
      }

      IARState<S> successorState = IARState.of(rabinSuccessor, successorRecord);
      return Edge.of(successorState, priority);
    }
  }
}