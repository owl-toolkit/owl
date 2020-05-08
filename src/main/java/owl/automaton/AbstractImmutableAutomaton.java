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

package owl.automaton;

import java.util.BitSet;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.factories.ValuationSetFactory;

/**
 * This abstract class implements storage and retrieval of commonly fixed values, and overrides the
 * default implementations for {@link Automaton#states()}, {@link Automaton#accept(Visitor)} in
 * order to cache the set of reachable states for later use. Subclassing classes are excepted to
 * implement an immutable automaton and to posses a fixed transition relation.
 **/
public abstract class AbstractImmutableAutomaton<S, A extends OmegaAcceptance>
  implements Automaton<S, A> {

  protected final A acceptance;
  protected final Set<S> initialStates;
  protected final ValuationSetFactory factory;

  @Nullable
  private Set<S> statesCache;

  /**
   * Constructor which fixes alphabet, initial states, and acceptance condition. The transition
   * relation is given by subclassing and overriding suitable methods.
   *
   * @param factory The alphabet.
   * @param initialStates The initial states.
   * @param acceptance The acceptance condition.
   */
  public AbstractImmutableAutomaton(ValuationSetFactory factory, Set<S> initialStates,
    A acceptance) {
    this.factory = factory;
    this.acceptance = acceptance;
    this.initialStates = Set.copyOf(initialStates);
  }

  @Override
  public final A acceptance() {
    return acceptance;
  }

  @Override
  public final ValuationSetFactory factory() {
    return factory;
  }

  @Override
  public final S onlyInitialState() {
    return Automaton.super.onlyInitialState();
  }

  @Override
  public final Set<S> initialStates() {
    return initialStates;
  }

  @Override
  public final Set<S> states() {
    if (statesCache == null) {
      statesCache = Set.copyOf(DefaultImplementations.getReachableStates(this));
    }

    return statesCache;
  }

  @Override
  public final void accept(EdgeVisitor<S> visitor) {
    Set<S> exploredStates = DefaultImplementations.visit(this, visitor);

    if (statesCache == null) {
      statesCache = Set.copyOf(exploredStates);
    }
  }

  @Override
  public final void accept(EdgeMapVisitor<S> visitor) {
    if (statesCache == null) {
      statesCache = Set.copyOf(DefaultImplementations.visit(this, visitor));
    } else {
      for (S state : statesCache) {
        visitor.enter(state);
        visitor.visit(state, edgeMap(state));
        visitor.exit(state);
      }
    }
  }

  @Override
  public final void accept(EdgeTreeVisitor<S> visitor) {
    if (statesCache == null) {
      statesCache = Set.copyOf(DefaultImplementations.visit(this, visitor));
    } else {
      for (S state : statesCache) {
        visitor.enter(state);
        visitor.visit(state, edgeTree(state));
        visitor.exit(state);
      }
    }
  }

  @Nullable
  protected final Set<S> cache() {
    return statesCache;
  }

  /**
   * This class provides a skeleton implementation to create a non-deterministic on-the-fly
   * constructed automaton.
   */
  public abstract static class NonDeterministicEdgesAutomaton<S, A extends OmegaAcceptance>
    extends AbstractImmutableAutomaton<S, A>
    implements EdgesAutomatonMixin<S, A> {

    public NonDeterministicEdgesAutomaton(ValuationSetFactory factory, Set<S> initialStates,
      A acceptance) {
      super(factory, initialStates, acceptance);
    }
  }

  /**
   * This class provides a skeleton implementation to create a non-deterministic on-the-fly
   * constructed automaton that uses {@link owl.collections.ValuationSet} as the main
   * representation of the transition relation.
   */
  public abstract static class NonDeterministicEdgeMapAutomaton<S, A extends OmegaAcceptance>
    extends AbstractImmutableAutomaton<S, A>
    implements EdgeMapAutomatonMixin<S, A> {

    public NonDeterministicEdgeMapAutomaton(ValuationSetFactory factory, Set<S> initialStates,
      A acceptance) {
      super(factory, initialStates, acceptance);
    }
  }

  /**
   * This class provides a skeleton implementation to create a non-deterministic on-the-fly
   * constructed automaton that uses {@link owl.collections.ValuationTree} as the main
   * representation of the transition relation.
   */
  public abstract static class NonDeterministicEdgeTreeAutomaton<S, A extends OmegaAcceptance>
    extends AbstractImmutableAutomaton<S, A>
    implements EdgeTreeAutomatonMixin<S, A> {

    public NonDeterministicEdgeTreeAutomaton(ValuationSetFactory factory, Set<S> initialStates,
      A acceptance) {
      super(factory, initialStates, acceptance);
    }
  }

  /**
   * This class provides a skeleton implementation to create a semi-deterministic on-the-fly
   * constructed automaton.
   */
  public abstract static class SemiDeterministicEdgesAutomaton<S, A extends OmegaAcceptance>
    extends AbstractImmutableAutomaton<S, A>
    implements EdgesAutomatonMixin<S, A> {

    public SemiDeterministicEdgesAutomaton(ValuationSetFactory factory, Set<S> initialStates,
      A acceptance) {
      super(factory, initialStates, acceptance);
    }

    @Override
    public abstract Edge<S> edge(S state, BitSet valuation);

    @Override
    public final Set<Edge<S>> edges(S state, BitSet valuation) {
      return Collections3.ofNullable(edge(state, valuation));
    }

    @Override
    public final boolean is(Property property) {
      return property == Property.SEMI_DETERMINISTIC
        || property == Property.LIMIT_DETERMINISTIC
        || super.is(property);
    }
  }
}
