/*
 * Copyright (C) 2016 - 2022  (See AUTHORS)
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

package owl.automaton.minimization;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AbstractMemoizingAutomaton.PrecomputedAutomaton;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.EdgeRelation;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;
import owl.collections.Numbering;

/**
 * This class implements [ICALP'19] minimization of GFG automata.
 *
 * @see owl.Bibliography#ICALP_19_2
 */
public final class GfgNcwMinimization {

  private GfgNcwMinimization() {
  }

  public static CanonicalGfgNcw minimize(
      Automaton<?, ? extends CoBuchiAcceptance> ncw) {

    if (ncw.is(Automaton.Property.DETERMINISTIC)) {
      return minimizeCompleteDcw(ncw.is(Property.COMPLETE)
          ? OmegaAcceptanceCast.castExact(ncw, CoBuchiAcceptance.class)
          : Views.completeCoBuchi(ncw));
    }

    return minimizeCompleteDcw(Determinization.determinizeCoBuchiAcceptance(ncw));
  }

  private static CanonicalGfgNcw minimizeCompleteDcw(
      Automaton<?, CoBuchiAcceptance> dcw) {

    return minimizeCompleteIntDcw(Views.dropStateLabels(dcw).automaton());
  }

  private static CanonicalGfgNcw minimizeCompleteIntDcw(
      AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> dcw) {

    assert dcw.is(Automaton.Property.COMPLETE);
    assert dcw.is(Automaton.Property.DETERMINISTIC);

    // Safe components.
    var safeComponents = SccDecomposition.of(
            dcw.states(),
            EdgeRelation.filter(dcw::edges, edge -> edge.colours().isEmpty()))
        .sccs()
        .stream()
        .map(ImmutableBitSet::copyOf)
        .toList();

    // Normalize.
    var normalized = new NormalizedAutomaton(dcw, safeComponents);
    var languageChecks = new LanguageChecks(normalized);
    // Centralize.
    var safeCentralized = safeCentralize(normalized, safeComponents, languageChecks);
    // Minimize.
    var safeMinimized = safeMinimize(safeCentralized, languageChecks);
    // Package.
    var canonicalGfgNcw = new CanonicalGfgNcw(dcw, safeMinimized, languageChecks);

    assert LanguageContainment.equalsCoBuchi(dcw, canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw);
    assert canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw.is(Automaton.Property.COMPLETE);

    return canonicalGfgNcw;
  }

  public final static class CanonicalGfgNcw {

    public final AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> alphaMaximalUpToHomogenityGfgNcw;
    public final AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> alphaMaximalGfgNcw;

    public final AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> dcw;

    public final List<ImmutableBitSet> sccs;
    public final List<ImmutableBitSet> safeComponents;
    public final List<ImmutableBitSet> languageEquivalenceClasses;
    public final List<ImmutableBitSet> subsafeEquivalentRelation;

    private CanonicalGfgNcw(
        AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> dcw,
        SafeMinimizedAutomaton safeMinimizedAutomaton,
        LanguageChecks languageChecks) {

      this.dcw = dcw;

      int states = safeMinimizedAutomaton.states().size();

      Numbering<ImmutableBitSet> numbering = new Numbering<>(states);
      numbering.lookup(safeMinimizedAutomaton.initialState());

      @SuppressWarnings("unchecked")
      MtBdd<Edge<Integer>>[] edgeTrees = new MtBdd[states];

      for (int state = 0; state < states; state++) {
        edgeTrees[state] = safeMinimizedAutomaton.edgeTree(numbering.lookup(state)).map(
            edges -> Edges.mapSuccessors(edges, numbering::lookup));
      }

      this.alphaMaximalUpToHomogenityGfgNcw = new PrecomputedAutomaton<>(
          safeMinimizedAutomaton.atomicPropositions(),
          safeMinimizedAutomaton.factory(),
          Set.of(0),
          CoBuchiAcceptance.INSTANCE,
          List.of(edgeTrees));

      assert ImmutableBitSet.range(0, states)
          .equals(this.alphaMaximalUpToHomogenityGfgNcw.states());

      for (int state = 0; state < states; state++) {
        edgeTrees[state] = edgeTrees[state].map(edges -> switch (edges.size()) {
          case 0 -> throw new AssertionError();

          case 1 -> {
            var edge = edges.iterator().next();
            yield edge.colours().isEmpty() ? edges : Set.of();
          }

          default -> {
            assert edges.stream().noneMatch(edge -> edge.colours().isEmpty());
            yield Set.of();
          }
        });
      }

      var safeView = new PrecomputedAutomaton<>(
          this.alphaMaximalUpToHomogenityGfgNcw.atomicPropositions(),
          this.alphaMaximalUpToHomogenityGfgNcw.factory(),
          this.alphaMaximalUpToHomogenityGfgNcw.states(),
          AllAcceptance.INSTANCE,
          List.of(edgeTrees)
      );

      this.sccs = SccDecomposition.of(this.alphaMaximalUpToHomogenityGfgNcw).sccs()
          .stream()
          .map(ImmutableBitSet::copyOf)
          .toList();

      this.safeComponents = SccDecomposition.of(safeView).sccs()
          .stream()
          .map(ImmutableBitSet::copyOf)
          .toList();

      this.languageEquivalenceClasses = this.sccs.stream().flatMap(scc -> {
            List<BitSet> languageEquivalenceClassesBuilder = new ArrayList<>(scc.size());

            // Compute language equivalence classes.
            outer:
            for (int sccState : scc) {
              for (BitSet equivalenceClass : languageEquivalenceClassesBuilder) {
                int representative = equivalenceClass.nextSetBit(0);

                if (languageChecks.languageEquivalent(
                    numbering.lookup(sccState).iterator().next(),
                    numbering.lookup(representative).iterator().next())) {

                  equivalenceClass.set(sccState);
                  continue outer;
                }
              }

              var newClass = new BitSet();
              newClass.set(sccState);
              languageEquivalenceClassesBuilder.add(newClass);
            }

            return languageEquivalenceClassesBuilder.stream().map(ImmutableBitSet::copyOf);
          })
          .toList();

      ImmutableBitSet[] relationBuilder = new ImmutableBitSet[states];

      for (ImmutableBitSet languageEquivalenceClass : languageEquivalenceClasses) {
        for (int q1 : languageEquivalenceClass) {
          assert relationBuilder[q1] == null;

          var q1SafeView = new LocalSafeView(safeView, find(safeComponents, q1), q1);
          BitSet larger = new BitSet();

          for (int q2 : languageEquivalenceClass) {
            if (q2 == q1) {
              continue;
            }

            var q2SafeView = new LocalSafeView(safeView, find(safeComponents, q2), q2);

            if (LanguageContainment.containsAll(q1SafeView, q2SafeView)) {
              assert !LanguageContainment.containsAll(q2SafeView, q1SafeView)
                  : "there cannot be strongly equivalent states.";
              larger.set(q2);
            }
          }

          relationBuilder[q1] = ImmutableBitSet.copyOf(larger);
        }
      }

      this.subsafeEquivalentRelation = List.of(relationBuilder);

      {
        int initialState = alphaMaximalUpToHomogenityGfgNcw.initialState();
        ImmutableBitSet initialClass = null;

        for (var equivalenceClass : languageEquivalenceClasses) {
          if (equivalenceClass.contains(initialState)) {
            initialClass = equivalenceClass;
            break;
          }
        }

        this.alphaMaximalGfgNcw = new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
            this.alphaMaximalUpToHomogenityGfgNcw.atomicPropositions(),
            this.alphaMaximalUpToHomogenityGfgNcw.factory(),
            Objects.requireNonNull(initialClass),
            CoBuchiAcceptance.INSTANCE) {

          @Override
          protected MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
            return alphaMaximalUpToHomogenityGfgNcw.edgeTree(state).map(edges ->
                switch (edges.size()) {
                  case 0 -> throw new AssertionError();

                  case 1 -> {
                    int successor = edges.iterator().next().successor();
                    ImmutableBitSet successorClass = null;

                    for (var equivalenceClass : languageEquivalenceClasses) {
                      if (equivalenceClass.contains(successor)) {
                        successorClass = equivalenceClass;
                        break;
                      }
                    }

                    assert !successorClass.isEmpty();
                    Set<Edge<Integer>> alphaMaximalEdges = new HashSet<>(successorClass.size() + 1);
                    alphaMaximalEdges.add(edges.iterator().next());
                    successorClass.forEach((int s) -> alphaMaximalEdges.add(Edge.of(s, 0)));
                    yield alphaMaximalEdges;
                  }

                  default -> {
                    assert languageEquivalenceClasses.contains(
                        ImmutableBitSet.copyOf(Edges.successors(edges)));
                    yield edges;
                  }
                });
          }
        };
      }
    }

    public ImmutableBitSet equivalenceClass(int state) {
      return find(languageEquivalenceClasses, state);
    }

    public ImmutableBitSet successorEquivalenceClass(
        ImmutableBitSet equivalenceClass, ImmutableBitSet sigma) {

      return successorEquivalenceClass(equivalenceClass.first().orElseThrow(), sigma);
    }

    public ImmutableBitSet successorEquivalenceClass(
        int state, ImmutableBitSet sigma) {

      return lookup(alphaMaximalGfgNcw.successors(state, sigma));
    }

    public int nonAlphaSuccessor(int state, ImmutableBitSet sigma) {
      Set<Edge<Integer>> edges = alphaMaximalUpToHomogenityGfgNcw.edges(state, sigma);

      if (edges.size() == 1) {
        var edge = edges.iterator().next();

        if (edge.colours().isEmpty()) {
          int successor = edge.successor();
          assert successor >= 0;
          return successor;
        }
      }

      assert !edges.isEmpty() && edges.stream().allMatch(x -> x.colours().contains(0));
      return -1;
    }

    public ImmutableBitSet initialEquivalenceClass() {
      return lookup(alphaMaximalGfgNcw.initialStates());
    }

    public boolean languageEquivalent(int q1, int q2) {
      return q1 == q2
          || find(languageEquivalenceClasses, q1) == find(languageEquivalenceClasses, q2);
    }

    public boolean subsafeEquivalent(int q1, int q2) {
      return q1 == q2
          || (languageEquivalent(q1, q2) && subsafeEquivalentRelation.get(q1).contains(q2));
    }

    private static ImmutableBitSet find(List<ImmutableBitSet> sets, int state) {
      for (ImmutableBitSet set : sets) {
        if (set.contains(state)) {
          return set;
        }
      }

      throw new IllegalArgumentException(
          String.format("state (%d) not present in states()", state));
    }

    private ImmutableBitSet lookup(Set<Integer> set) {
      for (ImmutableBitSet equivalenceClass : languageEquivalenceClasses) {
        if (equivalenceClass.equals(set)) {
          return equivalenceClass;
        }
      }

      throw new NoSuchElementException();
    }

    private static class LocalSafeView implements Automaton<Integer, AllAcceptance> {

      private final Automaton<Integer, AllAcceptance> globalSafeView;
      private final ImmutableBitSet initialStates;
      private final ImmutableBitSet localSafeComponent;

      private LocalSafeView(Automaton<Integer, AllAcceptance> globalSafeView,
          ImmutableBitSet localSafeComponent, int initialState) {
        Preconditions.checkArgument(localSafeComponent.contains(initialState));
        this.globalSafeView = globalSafeView;
        this.initialStates = ImmutableBitSet.of(initialState);
        this.localSafeComponent = localSafeComponent;
      }

      @Override
      public AllAcceptance acceptance() {
        return AllAcceptance.INSTANCE;
      }

      @Override
      public List<String> atomicPropositions() {
        return globalSafeView.atomicPropositions();
      }

      @Override
      public BddSetFactory factory() {
        return globalSafeView.factory();
      }

      @Override
      public Set<Integer> initialStates() {
        return initialStates;
      }

      @Override
      public Set<Integer> states() {
        return localSafeComponent;
      }

      @Override
      public Set<Edge<Integer>> edges(Integer state, BitSet valuation) {
        Preconditions.checkArgument(localSafeComponent.contains(state));
        return globalSafeView.edges(state, valuation);
      }

      @Override
      public Map<Edge<Integer>, BddSet> edgeMap(Integer state) {
        Preconditions.checkArgument(localSafeComponent.contains(state));
        return globalSafeView.edgeMap(state);
      }

      @Override
      public MtBdd<Edge<Integer>> edgeTree(Integer state) {
        Preconditions.checkArgument(localSafeComponent.contains(state));
        return globalSafeView.edgeTree(state);
      }

      @Override
      public boolean is(Automaton.Property property) {
        return property == Automaton.Property.SEMI_DETERMINISTIC
            || property == Automaton.Property.DETERMINISTIC
            || property == Automaton.Property.LIMIT_DETERMINISTIC
            || Automaton.super.is(property);
      }
    }

  }

  private static SafeCentralizedAutomaton safeCentralize(
      NormalizedAutomaton dcw,
      List<ImmutableBitSet> safeComponents,
      LanguageChecks languageChecks) {

    var frontier = Collections3.maximalElements(safeComponents, (component1, component2) -> {
      int representative1 = component1.iterator().next();

      for (int representative2 : component2) {
        if (languageChecks.subsafeEquivalent(representative1, representative2)) {
          return true;
        }
      }

      return false;
    });

    Integer initialState = null;

    for (Set<Integer> safeComponent : frontier) {
      if (safeComponent.contains(dcw.initialState())) {
        initialState = dcw.initialState();
        break;
      }
    }

    if (initialState == null) {
      outer:
      for (Set<Integer> safeComponent : frontier) {
        for (int state : safeComponent) {
          if (languageChecks.subsafeEquivalent(dcw.initialState(), state)) {
            initialState = state;
            break outer;
          }
        }
      }
    }

    var safeCentralized = new SafeCentralizedAutomaton(
        dcw, Objects.requireNonNull(initialState), frontier, languageChecks);
    safeCentralized.states();
    return safeCentralized;
  }

  private static SafeMinimizedAutomaton safeMinimize(
      SafeCentralizedAutomaton ncw, LanguageChecks languageChecks) {

    List<ImmutableBitSet> equivalenceClasses = Collections3.equivalenceClasses(
            ncw.states(), languageChecks::stronglyEquivalent, true).stream()
        .map(ImmutableBitSet::copyOf).toList();

    int ncwInitialState = ncw.initialState();
    ImmutableBitSet initialState = null;

    for (ImmutableBitSet state : equivalenceClasses) {
      if (state.contains(ncwInitialState)) {
        initialState = state;
        break;
      }
    }

    var safeMinimized = new SafeMinimizedAutomaton(
        ncw, Objects.requireNonNull(initialState), equivalenceClasses);
    safeMinimized.states();
    return safeMinimized;
  }

  private static class NormalizedAutomaton
      extends AbstractMemoizingAutomaton.EdgeTreeImplementation<Integer, CoBuchiAcceptance> {

    @Nullable
    private AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> dcw;
    @Nullable
    private List<Set<Integer>> safeComponents;

    private NormalizedAutomaton(
        AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> dcw,
        List<ImmutableBitSet> safeComponents) {

      super(dcw.atomicPropositions(), dcw.factory(), dcw.initialStates(), dcw.acceptance());
      this.dcw = dcw;
      this.safeComponents = List.copyOf(safeComponents);
    }

    @Override
    protected MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
      assert dcw != null;

      return dcw.edgeTree(state).map(edges -> Collections3.transformSet(edges,
          edge -> component(state) == component(edge.successor()) ? edge : edge.withAcceptance(0)));
    }

    private Set<Integer> component(Integer state) {
      assert safeComponents != null;

      for (Set<Integer> component : safeComponents) {
        if (component.contains(state)) {
          return component;
        }
      }

      throw new IllegalStateException("Internal error.");
    }

    @Override
    protected void explorationCompleted() {
      dcw = null;
      safeComponents = null;
    }
  }

  private static class SafeCentralizedAutomaton
      extends AbstractMemoizingAutomaton.EdgeTreeImplementation<Integer, CoBuchiAcceptance> {

    @Nullable
    private NormalizedAutomaton dcw;
    @Nullable
    private List<ImmutableBitSet> frontier;
    @Nullable
    private LanguageChecks languageChecks;

    private SafeCentralizedAutomaton(
        NormalizedAutomaton dcw,
        int initialState,
        List<ImmutableBitSet> frontier,
        LanguageChecks languageChecks) {

      super(
          dcw.atomicPropositions(), dcw.factory(), Set.of(initialState),
          CoBuchiAcceptance.INSTANCE);
      this.dcw = dcw;
      this.frontier = frontier;
      this.languageChecks = languageChecks;
    }

    @Override
    protected MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
      assert dcw != null;
      return dcw.edgeTree(state).map(x -> transformEdge(Iterables.getOnlyElement(x)));
    }

    private Set<Edge<Integer>> transformEdge(Edge<Integer> edge) {
      assert frontier != null;
      assert languageChecks != null;

      if (edge.colours().isEmpty()) {
        return Set.of(edge);
      }

      assert edge.colours().contains(0);

      int rejectingSuccessor = edge.successor();
      List<Edge<Integer>> rejectingEdges = new ArrayList<>();

      for (Set<Integer> safeComponent : frontier) {
        for (int state : safeComponent) {
          if (languageChecks.languageEquivalent(rejectingSuccessor, state)) {
            rejectingEdges.add(Edge.of(state, 0));
          }
        }
      }

      @SuppressWarnings("unchecked")
      Set<Edge<Integer>> edges = Set.of(rejectingEdges.toArray(Edge[]::new));
      return edges;
    }

    @Override
    protected void explorationCompleted() {
      dcw = null;
      frontier = null;
      languageChecks = null;
    }
  }

  private static class SafeMinimizedAutomaton
      extends AbstractMemoizingAutomaton.EdgesImplementation<ImmutableBitSet, CoBuchiAcceptance> {

    private final SafeCentralizedAutomaton ncw;
    private final List<ImmutableBitSet> immutableEquivalenceClass;

    private SafeMinimizedAutomaton(
        SafeCentralizedAutomaton ncw,
        ImmutableBitSet initialState,
        List<ImmutableBitSet> immutableEquivalenceClass) {
      super(ncw.atomicPropositions(), ncw.factory(), Set.of(initialState),
          CoBuchiAcceptance.INSTANCE);
      this.ncw = ncw;
      this.immutableEquivalenceClass = immutableEquivalenceClass;
    }

    @Override
    protected Set<Edge<ImmutableBitSet>> edgesImpl(ImmutableBitSet state, BitSet valuation) {
      var edges = new HashSet<Edge<ImmutableBitSet>>();

      // TODO: review this!
      for (int representative : state) {
        var type = EdgeType.UNKNOWN;

        for (Edge<Integer> edge : ncw.edges(representative, valuation)) {
          var successorRepresentative = edge.successor();

          if (edge.colours().contains(0)) {
            assert type == EdgeType.UNKNOWN || type == EdgeType.REJECTING;
            type = EdgeType.REJECTING;
          } else {
            assert type == EdgeType.UNKNOWN || type == EdgeType.ACCEPTING;
            type = EdgeType.ACCEPTING;
          }

          var successor
              = findEquivalenceClass(immutableEquivalenceClass, successorRepresentative);
          edges.add(type == EdgeType.REJECTING
              ? Edge.of(successor, 0)
              : Edge.of(successor));
        }
      }

      return edges;
    }

    private enum EdgeType {
      ACCEPTING, REJECTING, UNKNOWN
    }

    private static ImmutableBitSet findEquivalenceClass(
        List<ImmutableBitSet> equivalenceClasses, int representative) {

      for (ImmutableBitSet equivalenceClass : equivalenceClasses) {
        if (equivalenceClass.contains(representative)) {
          return equivalenceClass;
        }
      }

      throw new IllegalStateException("Internal error.");
    }
  }

  static final class LanguageChecks {

    private final int states;

    private final ImmutableBitSet[] equivalenceClassMap;
    private final Boolean[][] safeContained;

    LanguageChecks(AbstractMemoizingAutomaton<Integer, CoBuchiAcceptance> dcw) {
      this.states = dcw.states().size();

      // Check that states are assigned consecutively.
      checkArgument(ImmutableBitSet.copyOf(dcw.states()).equals(ImmutableBitSet.range(0, states)));

      // Computation of language equivalence classes.
      List<ImmutableBitSet> equivalenceClasses;
      {
        // Fin(0) xor Fin(1)
        //   --> (Fin(0) /\ Inf(1)) \/ (Inf(0) /\ Fin(1))
        //   --> (Fin(0) /\ Inf(1)) \/ (Fin(2) /\ Inf(3))

        var symmetricDifference = new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
            dcw.atomicPropositions(),
            dcw.factory(),
            ImmutableBitSet.range(0, states * states),
            RabinAcceptance.of(2)) {

          @Override
          protected MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
            return MtBddOperations.cartesianProduct(
                dcw.edgeTree(fst(state, states)),
                dcw.edgeTree(snd(state, states)),
                (edge1, edge2) -> {
                  BitSet acceptance = new BitSet();
                  if (edge1.colours().contains(0)) {
                    acceptance.set(0);
                    acceptance.set(3);
                  } else {
                    assert edge1.colours().isEmpty();
                  }

                  if (edge2.colours().contains(0)) {
                    acceptance.set(1);
                    acceptance.set(2);
                  } else {
                    assert edge2.colours().isEmpty();
                  }

                  return Edge.of(pair(edge1.successor(), edge2.successor(), states), acceptance);
                });
          }
        };

        var sccDecomposition = SccDecomposition.of(symmetricDifference);
        var rejectingSccs = sccDecomposition.rejectingSccs();
        Map<Integer, Integer> indexMap = sccDecomposition.indexMap();
        var condensationGraph = sccDecomposition.condensation();
        BitSet allReachableSccsAreRejecting = new BitSet();

        for (int i = sccDecomposition.sccs().size() - 1; i >= 0; i--) {
          boolean rejectingScc = true;

          for (int j : condensationGraph.successors(i)) {
            if (i == j) {
              rejectingScc &= rejectingSccs.contains(i);
            } else {
              assert i < j;
              rejectingScc &= allReachableSccsAreRejecting.get(j);
            }
          }

          allReachableSccsAreRejecting.set(i, rejectingScc);
        }

        equivalenceClasses = Collections3.equivalenceClasses(dcw.states(),
                (x, y) -> x.equals(y) || allReachableSccsAreRejecting.get(
                    indexMap.get(pair(x, y, states))),
                true)
            .stream()
            .map(ImmutableBitSet::copyOf)
            .toList();

        equivalenceClassMap = new ImmutableBitSet[states];

        for (int q = 0; q < states; q++) {
          ImmutableBitSet result = null;

          for (ImmutableBitSet equivalenceClass1 : equivalenceClasses) {
            if (equivalenceClass1.contains(q)) {
              result = equivalenceClass1;
              break;
            }
          }

          equivalenceClassMap[q] = Objects.requireNonNull(result);
        }
      }

      // Computation of safe containment.

      {
        Set<Integer> initialStates = equivalenceClasses.stream()
            .mapMulti((ImmutableBitSet equivalenceClass, Consumer<Integer> consumer) -> {
              for (int q : equivalenceClass) {
                for (int p : equivalenceClass) {
                  if (q != p) {
                    consumer.accept(pair(q, p, states + 1));
                  }
                }
              }
            })
            .collect(Collectors.toUnmodifiableSet());

        // States of the following automaton has a non-empty language if 'fst' has a safe run that
        // is not present in 'snd'.
        Automaton<Integer, BuchiAcceptance> notSubsafe = new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
            dcw.atomicPropositions(),
            dcw.factory(),
            initialStates,
            BuchiAcceptance.INSTANCE) {

          @Override
          protected MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
            final int fst = fst(state, states + 1);
            final int snd = snd(state, states + 1);

            assert 0 <= fst && fst < states;
            assert 0 <= snd && snd <= states;

            if (fst == snd || snd == states) {
              return dcw.edgeTree(fst).map(edges -> {
                var edge = Iterables.getOnlyElement(edges);

                if (edge.colours().isEmpty()) {
                  return Set.of(fst == snd
                      ? Edge.of(pair(edge.successor(), edge.successor(), states + 1))
                      : Edge.of(pair(edge.successor(), states, states + 1), 0));
                }

                return Set.of();
              });
            }

            return MtBddOperations.cartesianProduct(
                dcw.edgeTree(fst),
                dcw.edgeTree(snd),
                (edge1, edge2) -> {
                  // Safe run ended for 'fst'.
                  if (edge1.colours().contains(0)) {
                    return null;
                  } else {
                    assert edge1.colours().isEmpty();
                  }

                  // Safe run ended for 'snd'.
                  if (edge2.colours().contains(0)) {
                    return Edge.of(pair(edge1.successor(), states, states + 1), 0);
                  } else {
                    assert edge2.colours().isEmpty();
                  }

                  return Edge.of(pair(edge1.successor(), edge2.successor(), states + 1));
                });
          }
        };

        var sccDecomposition = SccDecomposition.of(notSubsafe);
        var rejectingSccs = sccDecomposition.rejectingSccs();
        Map<Integer, Integer> indexMap = sccDecomposition.indexMap();
        var condensationGraph = sccDecomposition.condensation();
        BitSet allReachableSccsAreRejecting = new BitSet();

        for (int i = sccDecomposition.sccs().size() - 1; i >= 0; i--) {
          boolean rejectingScc = true;

          for (int j : condensationGraph.successors(i)) {
            if (i == j) {
              rejectingScc &= rejectingSccs.contains(i);
            } else {
              assert i < j;
              rejectingScc &= allReachableSccsAreRejecting.get(j);
            }
          }

          allReachableSccsAreRejecting.set(i, rejectingScc);
        }

        safeContained = new Boolean[states][states];

        for (int q = 0; q < states; q++) {
          for (int p = 0; p < states; p++) {
            // We only initialise the value if the states belong to the same equivalence class.
            if (equivalenceClassMap[q] == equivalenceClassMap[p]) {
              if (q == p) {
                safeContained[q][p] = Boolean.TRUE;
              } else {
                var pair = pair(q, p, states + 1);
                assert initialStates.contains(pair);
                safeContained[q][p] = allReachableSccsAreRejecting.get(
                    indexMap.get(pair(q, p, states + 1)));
              }
            }
          }
        }
      }
    }

    public boolean languageEquivalent(int q, int p) {
      return Objects.requireNonNull(equivalenceClassMap[q]) == equivalenceClassMap[p];
    }

    boolean subsafeEquivalent(int q, int p) {
      return languageEquivalent(q, p)
          && Objects.requireNonNull(safeContained[q][p]);
    }

    boolean stronglyEquivalent(int q, int p) {
      return languageEquivalent(q, p)
          && Objects.requireNonNull(safeContained[q][p])
          && Objects.requireNonNull(safeContained[p][q]);
    }

    private int pair(int fst, int snd, int states) {
      assert 0 <= fst && fst < states;
      assert 0 <= snd && snd < states;
      return fst * states + snd;
    }

    private int fst(int pair, int states) {
      assert 0 <= pair && pair < states * states;
      return pair / states;
    }

    private int snd(int pair, int states) {
      assert 0 <= pair && pair < states * states;
      return pair % states;
    }
  }
}
