/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.ltl2ldba;

import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.TwoPartAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.collections.Either;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.LtlLanguageExpressible;
import owl.ltl.SyntacticFragments;

/**
 * Translation-specific internal representation of LDBAs. Due to visibility constraints
 * implementation-private methods are public. However, there are not intended to be used outside of
 * the ltl2dpa and ltl2dra packages. The only allowed method for external code is the
 * {@link AnnotatedLDBA#copyAsMutable()} method.
 *
 * @param <S> initial component states
 * @param <T> accepting component states
 * @param <B> acceptance condition
 * @param <X> internal annotation
 * @param <Y> internal annotation
 */
public final class AnnotatedLDBA<S, T extends LtlLanguageExpressible,
  B extends GeneralizedBuchiAcceptance, X, Y> {

  private final MutableAutomaton<S, AllAcceptance> initialComponent;
  private final SetMultimap<S, T> epsilonJumps;
  private final MutableAutomaton<T, B> acceptingComponent;

  private final X annotation;
  private final Y stateAnnotation;

  private final Function<S, EquivalenceClass> languageFunction;

  private AnnotatedLDBA(MutableAutomaton<S, AllAcceptance> initialComponent,
    MutableAutomaton<T, B> acceptingComponent,
    SetMultimap<S, T> epsilonJumps,
    Function<S, EquivalenceClass> languageFunction,
    X annotation,
    Y stateAnnotation) {
    this.initialComponent = initialComponent;
    this.epsilonJumps = epsilonJumps;
    this.acceptingComponent = acceptingComponent;

    this.languageFunction = languageFunction;

    this.annotation = annotation;
    this.stateAnnotation = stateAnnotation;

    assert this.acceptingComponent.is(Automaton.Property.SEMI_DETERMINISTIC);
  }

  static <S, T extends LtlLanguageExpressible, Acc extends GeneralizedBuchiAcceptance, X, Y>
  AnnotatedLDBA<S, T, Acc, X, Y> build(
    MutableAutomaton<S, AllAcceptance> initialComponent,
    AcceptingComponentBuilder<T, Acc> acceptingComponentBuilder,
    Function<S, Set<T>> jumps,
    Function<S, EquivalenceClass> languageFunction,
    X annotation,
    Y stateAnnotation) {

    SetMultimap<S, T> epsilonJumps = MultimapBuilder.hashKeys().hashSetValues().build();

    for (Set<S> scc : SccDecomposition.of(initialComponent).sccsWithoutTransient()) {
      for (S state : scc) {
        var targets = jumps.apply(state);
        acceptingComponentBuilder.addInitialStates(targets);
        epsilonJumps.putAll(state, targets);
      }
    }

    MutableAutomaton<T, Acc> acceptingComponent = acceptingComponentBuilder.build();

    // Preprocess:
    // Remove dead states in the accepting component. Note that the .values() collection is backed
    // by the internal map of the epsilonJumps, hence removal is forwarded.
    AcceptanceOptimizations.removeDeadStates(acceptingComponent);
    epsilonJumps.values().retainAll(acceptingComponent.states());

    for (Set<S> scc : Lists.reverse(SccDecomposition.of(initialComponent).sccs())) {
      if (scc.stream().noneMatch(x -> epsilonJumps.keySet().contains(x)
        || SyntacticFragments.isSafety(languageFunction.apply(x)))
        && scc.stream().allMatch(s -> scc.containsAll(initialComponent.successors(s)))) {
        // The is a BSCC without protected states. Safe to remove.
        initialComponent.removeStateIf(scc::contains);
        initialComponent.trim();
      }
    }

    return new AnnotatedLDBA<>(initialComponent, acceptingComponent, epsilonJumps,
      languageFunction, annotation, stateAnnotation);
  }

  public B acceptance() {
    return acceptingComponent.acceptance();
  }

  public Automaton<T, B> acceptingComponent() {
    return acceptingComponent;
  }

  public X annotation() {
    return annotation;
  }

  public MutableAutomaton<Either<S, T>, B> copyAsMutable() {
    var mutableAutomaton = HashMapAutomaton.copyOf(new AutomatonView());
    AcceptanceOptimizations.removeDeadStates(mutableAutomaton);
    return mutableAutomaton;
  }

  public ValuationSetFactory factory() {
    return acceptingComponent.factory();
  }

  public Automaton<S, AllAcceptance> initialComponent() {
    return initialComponent;
  }

  public Y stateAnnotation() {
    return stateAnnotation;
  }

  private class AutomatonView extends TwoPartAutomaton<S, T, B> {
    @Override
    public String name() {
      return initialComponent.name();
    }

    @Override
    protected Set<S> initialStatesA() {
      return initialComponent.initialStates();
    }

    @Override
    protected Set<T> initialStatesB() {
      return Set.of();
    }

    @Override
    protected Set<Edge<S>> edgesA(S state, BitSet valuation) {
      return initialComponent.edges(state, valuation);
    }

    @Override
    protected Set<Edge<T>> edgesB(T state, BitSet valuation) {
      return acceptingComponent.edges(state, valuation);
    }

    @Override
    protected ValuationTree<Edge<S>> edgeTreeA(S state) {
      return initialComponent.edgeTree(state);
    }

    @Override
    protected ValuationTree<Edge<T>> edgeTreeB(T state) {
      return acceptingComponent.edgeTree(state);
    }

    @Override
    protected Set<T> moveAtoB(S state) {
      return Collections.unmodifiableSet(epsilonJumps.get(state));
    }

    @Override
    protected Set<Edge<Either<S, T>>> deduplicate(Set<Edge<Either<S, T>>> edges) {
      Edge<Either<S, T>> initialComponentEdge = null;
      Set<Edge<Either<S, T>>> acceptingComponentEdges = new HashSet<>();

      for (var edge : edges) {
        if (edge.successor().type() == Either.Type.LEFT) {
          assert initialComponentEdge == null;
          initialComponentEdge = edge;
        } else {
          acceptingComponentEdges.add(edge);
        }
      }

      if (initialComponentEdge == null) {
        return edges;
      }

      var initialComponentLanguage = languageFunction
        .apply(initialComponentEdge.successor().left());

      if (SyntacticFragments.isSafety(initialComponentLanguage)) {
        acceptingComponentEdges.removeIf(x ->
          initialComponentLanguage.equals(x.successor().right().language()));
      }

      if (acceptingComponentEdges.size() == edges.size() - 1) {
        return edges;
      }

      acceptingComponentEdges.add(initialComponentEdge);
      return acceptingComponentEdges;
    }

    @Override
    public B acceptance() {
      return acceptingComponent.acceptance();
    }

    @Override
    public ValuationSetFactory factory() {
      return acceptingComponent.factory();
    }
  }

  interface AcceptingComponentBuilder<S, B extends GeneralizedBuchiAcceptance> {
    void addInitialStates(Collection<? extends S> initialStates);

    MutableAutomaton<S, B> build();
  }
}