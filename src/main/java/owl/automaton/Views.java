/*
 * Copyright (C) 2017, 2022  (Salomon Sickert, Tobias Meggendorfer, Remco Abraham)
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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;
import owl.collections.Numbering;
import owl.collections.Pair;

public final class Views {

  private Views() {
  }

  public static <S> Automaton<Optional<S>, ?> complete(Automaton<S, ?> automaton) {

    Set<Optional<S>> initialStates = automaton.initialStates().isEmpty()
        ? Set.of(Optional.empty())
        : automaton.initialStates()
            .stream()
            .map(Optional::of)
            .collect(Collectors.toUnmodifiableSet());

    Optional<ImmutableBitSet> rejectingSet = automaton.acceptance().rejectingSet();
    EmersonLeiAcceptance acceptance;
    boolean dropAcceptanceSets;
    Edge<Optional<S>> sinkEdge;

    // Patch acceptance, if necessary.
    if (rejectingSet.isEmpty()) {
      acceptance = CoBuchiAcceptance.INSTANCE;
      dropAcceptanceSets = true;
      sinkEdge = Edge.of(Optional.empty(), ImmutableBitSet.of(0));
    } else {
      acceptance = automaton.acceptance();
      dropAcceptanceSets = false;
      sinkEdge = Edge.of(Optional.empty(), rejectingSet.get());
    }

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        automaton.atomicPropositions(),
        automaton.factory(),
        initialStates,
        acceptance) {

      @Override
      protected MtBdd<Edge<Optional<S>>> edgeTreeImpl(Optional<S> state) {
        if (state.isEmpty()) {
          return MtBdd.of(sinkEdge);
        }

        var edgeTree = automaton.edgeTree(state.get());

        if (dropAcceptanceSets) {
          return edgeTree.map(edges -> edges.isEmpty()
              ? Set.of(sinkEdge)
              : Collections3.transformSet(edges,
                  edge -> Edge.of(Optional.of(edge.successor()))));
        }

        return edgeTree.map(edges -> edges.isEmpty()
            ? Set.of(sinkEdge)
            : Collections3.transformSet(edges,
                edge -> edge.withSuccessor(Optional.of(edge.successor()))));
      }

      @Override
      public boolean is(Property property) {
        return property == Property.COMPLETE || super.is(property);
      }
    };
  }

  public static <S> Automaton<Optional<S>, ? extends BuchiAcceptance>
  completeBuchi(Automaton<S, ? extends BuchiAcceptance> automaton) {

    return OmegaAcceptanceCast.cast(Views.complete(automaton), BuchiAcceptance.class);
  }

  public static <S> Automaton<Optional<S>, ? extends CoBuchiAcceptance>
  completeCoBuchi(Automaton<S, ? extends CoBuchiAcceptance> automaton) {

    return OmegaAcceptanceCast.cast(Views.complete(automaton), CoBuchiAcceptance.class);
  }

  /**
   * Create a filtered view on the passed automaton. The returned automaton only contains states
   * that are reachable from the initial states. States can be protected from removal by marking
   * them as initial. It is assumed that passed automaton is not changed.
   *
   * @param automaton the backing automaton
   * @param filter    the filter defined on the automaton
   * @param <S>       the type of the states
   * @param <A>       the type of
   * @return a on-the-fly generated view on the automaton.
   */
  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A> filtered(
      Automaton<S, A> automaton, Filter<S> filter) {
    return new AutomatonView<>(automaton, filter);
  }

  @AutoValue
  public abstract static class Filter<S> {

    @Nullable
    abstract Set<S> initialStates();

    @Nullable
    abstract Predicate<S> stateFilter();

    @Nullable
    abstract BiPredicate<S, Edge<S>> edgeFilter();

    public static <S> Builder<S> builder() {
      return new AutoValue_Views_Filter.Builder<>();
    }

    public static <S> Filter<S> of(Set<S> initialStates, Predicate<S> stateFilter) {
      Builder<S> builder = builder();
      return builder.initialStates(initialStates).stateFilter(stateFilter).build();
    }

    public static <S> Filter<S> of(Predicate<S> stateFilter) {
      Builder<S> builder = builder();
      return builder.stateFilter(stateFilter).build();
    }

    public static <S> Filter<S> of(Predicate<S> stateFilter, BiPredicate<S, Edge<S>> edgeFilter) {
      Builder<S> builder = builder();
      return builder.stateFilter(stateFilter).edgeFilter(edgeFilter).build();
    }

    @AutoValue.Builder
    public abstract static class Builder<S> {

      public abstract Builder<S> initialStates(@Nullable Set<S> initialStates);

      public abstract Builder<S> stateFilter(@Nullable Predicate<S> filter);

      public abstract Builder<S> edgeFilter(@Nullable BiPredicate<S, Edge<S>> filter);

      public abstract Filter<S> build();
    }
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<S, A>
  replaceInitialStates(Automaton<S, ? extends A> automaton, Set<? extends S> initialStates) {

    Set<S> initialStatesCopy = Set.copyOf(initialStates);

    return new Automaton<>() {

      @Nullable
      private Set<S> statesCache = null;

      @Override
      public A acceptance() {
        return automaton.acceptance();
      }

      @Override
      public List<String> atomicPropositions() {
        return automaton.atomicPropositions();
      }

      @Override
      public BddSetFactory factory() {
        return automaton.factory();
      }

      @Override
      public Set<S> initialStates() {
        return initialStatesCopy;
      }

      @Override
      public Set<S> states() {
        if (statesCache == null) {
          Deque<S> workList = new ArrayDeque<>(initialStatesCopy);
          Set<S> reachableStates = new HashSet<>();

          while (!workList.isEmpty()) {
            S state = workList.remove();
            reachableStates.add(state);

            for (S successor : automaton.successors(state)) {
              if (reachableStates.add(successor)) {
                workList.add(successor);
              }
            }
          }

          statesCache = Set.copyOf(reachableStates);
        }

        return statesCache;
      }

      @Override
      public Set<Edge<S>> edges(S state, BitSet valuation) {
        checkArgument(statesCache == null || statesCache.contains(state));
        return automaton.edges(state, valuation);
      }

      @Override
      public Map<Edge<S>, BddSet> edgeMap(S state) {
        checkArgument(statesCache == null || statesCache.contains(state));
        return automaton.edgeMap(state);
      }

      @Override
      public MtBdd<Edge<S>> edgeTree(S state) {
        checkArgument(statesCache == null || statesCache.contains(state));
        return automaton.edgeTree(state);
      }
    };
  }

  private static final class AutomatonView<S, A extends EmersonLeiAcceptance>
      extends AbstractMemoizingAutomaton.EdgeTreeImplementation<S, A> {

    private final Automaton<S, A> backingAutomaton;

    @Nullable
    private final Predicate<S> stateFilter;

    @Nullable
    private final BiPredicate<S, Edge<S>> edgeFilter;

    private AutomatonView(Automaton<S, A> automaton, Filter<S> settings) {
      super(
          automaton.atomicPropositions(),
          automaton.factory(),
          initialStates(automaton, settings),
          automaton.acceptance());

      if (automaton instanceof AutomatonView<S, A> view) {
        this.backingAutomaton = view.backingAutomaton;
        this.stateFilter = and(view.stateFilter, settings.stateFilter());
        this.edgeFilter = and(view.edgeFilter, settings.edgeFilter());
      } else {
        this.backingAutomaton = automaton;
        this.stateFilter = settings.stateFilter();
        this.edgeFilter = settings.edgeFilter();
      }
    }

    @Nullable
    static <T> Predicate<T> and(
        @Nullable Predicate<T> predicate1,
        @Nullable Predicate<T> predicate2) {
      if (predicate1 == null) {
        return predicate2;
      }

      if (predicate2 == null) {
        return predicate1;
      }

      return predicate1.and(predicate2);
    }

    @Nullable
    static <T, U> BiPredicate<T, U> and(
        @Nullable BiPredicate<T, U> predicate1,
        @Nullable BiPredicate<T, U> predicate2) {
      if (predicate1 == null) {
        return predicate2;
      }

      if (predicate2 == null) {
        return predicate1;
      }

      return predicate1.and(predicate2);
    }

    private static <S> Set<S> initialStates(
        Automaton<S, ?> automaton, Filter<S> settings) {
      Set<S> initialStates = settings.initialStates();

      if (initialStates == null) {
        initialStates = automaton.initialStates();
      }

      Predicate<S> stateFilter = settings.stateFilter();

      if (stateFilter == null) {
        return initialStates;
      }

      return Sets.filter(initialStates, stateFilter::test);
    }

    private boolean stateFilter(S state) {
      return stateFilter == null || stateFilter.test(state);
    }

    private boolean edgeFilter(S state, Edge<S> edge) {
      return edgeFilter == null || edgeFilter.test(state, edge);
    }

    @Override
    public MtBdd<Edge<S>> edgeTreeImpl(S state) {
      checkArgument(stateFilter(state));

      var edges = backingAutomaton.edgeTree(state);

      if (stateFilter == null && edgeFilter == null) {
        return edges;
      }

      return edges.map((Set<Edge<S>> set) -> set.stream()
          .filter(edge -> edgeFilter(state, edge) && stateFilter(edge.successor()))
          .collect(Collectors.toUnmodifiableSet()));
    }
  }

  /**
   * This is essentially {@code fmap :: (S -> T) -> Automaton<S, A> -> Automaton<T, A>}. When the
   * function is injective, the effect is just replacing states of type S with states of type T. If
   * it is not, the result will be a quotient wrt. the equivalence classes induced by the
   * preimages.
   *
   * @param <S>             input state type
   * @param <T>             output state type
   * @param <A>             acceptance condition
   * @param automaton       input automaton
   * @param mappingFunction function from S to T
   * @return Output automaton where states are mapped from S to T (and possibly quotiented)
   */
  public static <S, T, A extends EmersonLeiAcceptance> Automaton<T, A>
  quotientAutomaton(Automaton<S, A> automaton, Function<? super S, ? extends T> mappingFunction) {

    Map<S, T> mapping = new HashMap<>();
    ImmutableListMultimap.Builder<T, S> reverseMappingBuilder = ImmutableListMultimap.builder();

    for (var state : automaton.states()) {
      var mappedState = mappingFunction.apply(state);
      mapping.put(state, mappedState);
      reverseMappingBuilder.put(mappedState, state);
    }

    return new QuotientAutomaton<>(automaton, Map.copyOf(mapping), reverseMappingBuilder.build());
  }

  private static class QuotientAutomaton<S, T, A extends EmersonLeiAcceptance>
      extends AbstractMemoizingAutomaton.EdgeTreeImplementation<T, A> {

    private final Automaton<S, A> automaton;
    private final Map<S, T> mapping;
    private final ImmutableListMultimap<T, S> reverseMapping;

    private QuotientAutomaton(Automaton<S, A> automaton, Map<S, T> mapping,
        ImmutableListMultimap<T, S> reverseMapping) {
      super(
          automaton.atomicPropositions(),
          automaton.factory(),
          automaton.initialStates().stream().map(mapping::get).collect(Collectors.toSet()),
          automaton.acceptance());

      this.automaton = automaton;
      this.mapping = Map.copyOf(mapping);
      this.reverseMapping = reverseMapping;
    }

    @Override
    public MtBdd<Edge<T>> edgeTreeImpl(T state) {
      return MtBddOperations.cartesianProduct(
          reverseMapping.get(state).stream().map(automaton::edgeTree).toList()
      ).map(x -> {
        Set<Edge<T>> set = new HashSet<>();
        for (List<Edge<S>> edges : x) {
          for (Edge<S> edge : edges) {
            set.add(edge.mapSuccessor(mapping::get));
          }
        }
        return set;
      });
    }
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<Pair<S, ImmutableBitSet>, A>
  stateAcceptanceAutomaton(Automaton<S, ? extends A> automaton) {

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        automaton.atomicPropositions(),
        automaton.factory(),
        automaton.initialStates().stream()
            .map(state -> Pair.of(state, ImmutableBitSet.of()))
            .collect(Collectors.toUnmodifiableSet()),
        automaton.acceptance()) {

      @Override
      protected MtBdd<Edge<Pair<S, ImmutableBitSet>>> edgeTreeImpl(Pair<S, ImmutableBitSet> state) {

        return automaton.edgeTree(state.fst()).map(edges -> edges.stream()
            .map(edge -> edge.mapSuccessor(successor -> Pair.of(successor, edge.colours())))
            .collect(Collectors.toUnmodifiableSet()));

      }
    };
  }

  public static <S, A extends EmersonLeiAcceptance> Automaton<Integer, A>
  dropStateLabels(Automaton<S, ? extends A> automaton) {

    if (automaton.initialStates().isEmpty()) {
      return EmptyAutomaton.of(
          automaton.atomicPropositions(),
          automaton.factory(),
          automaton.acceptance());
    }

    Numbering<S> mapping = new Numbering<>();

    return new DropStateLabelsImpl<>(
        automaton,
        Collections3.transformSet(automaton.initialStates(), mapping::lookup),
        mapping);
  }

  public static <S> Automaton<S, ParityAcceptance> convertParity(
      Automaton<S, ? extends ParityAcceptance> automaton, ParityAcceptance.Parity toParity) {

    if (automaton.acceptance().parity() == toParity) {
      return (Automaton<S, ParityAcceptance>) automaton;
    }

    if (automaton.acceptance().parity().max() != toParity.max()) {
      throw new UnsupportedOperationException();
    }

    int sets = automaton.acceptance().acceptanceSets();
    var newParityAcceptance = new ParityAcceptance(sets + 1, toParity);

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        automaton.atomicPropositions(),
        automaton.initialStates(),
        newParityAcceptance) {

      @Override
      protected MtBdd<Edge<S>> edgeTreeImpl(S state) {
        return automaton.edgeTree(state)
            .map(x -> Collections3.transformSet(x, y -> y.mapAcceptance(z -> z + 1)));
      }
    };
  }

  private static class DropStateLabelsImpl<S, A extends EmersonLeiAcceptance>
      extends AbstractMemoizingAutomaton.EdgeTreeImplementation<Integer, A> {

    @Nullable
    private Automaton<S, ? extends A> automaton;
    @Nullable
    private Numbering<S> mapping;

    private DropStateLabelsImpl(
        Automaton<S, ? extends A> automaton, Set<Integer> initialStates, Numbering<S> mapping) {
      super(
          automaton.atomicPropositions(), automaton.factory(), initialStates,
          automaton.acceptance());
      this.automaton = automaton;
      this.mapping = mapping;
    }

    @Override
    protected final MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
      assert automaton != null;
      assert mapping != null;

      return automaton
          .edgeTree(mapping.lookup(state))
          .map(x -> Collections3.transformSet(x, y -> y.mapSuccessor(mapping::lookup)));
    }

    @Override
    protected final void explorationCompleted() {
      automaton = null;
      mapping = null;
    }
  }
}
