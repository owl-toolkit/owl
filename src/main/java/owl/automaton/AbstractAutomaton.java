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

package owl.automaton;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.bdd.BddSetFactory;

/**
 * This class provides a skeletal implementation of the {@code Automaton}
 * interface to minimize the effort required to implement this interface.
 *
 * <p>It assumes that the automaton is immutable, i.e., the set of initial states, the
 * transition relation, and the acceptance condition is fixed. It makes use of this
 * assumption by caching the set of states.
 *
 * @param <S> the state type
 * @param <A> the acceptance condition type
 **/
public abstract class AbstractAutomaton<S, A extends OmegaAcceptance>
  implements Automaton<S, A> {

  protected final A acceptance;
  protected final Set<S> initialStates;
  protected final BddSetFactory factory;

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
  public AbstractAutomaton(
    BddSetFactory factory, Set<S> initialStates, A acceptance) {

    this.factory = factory;
    this.acceptance = acceptance;
    this.initialStates = Set.copyOf(initialStates);
  }

  @Override
  public final A acceptance() {
    return acceptance;
  }

  @Override
  public final BddSetFactory factory() {
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
      Set<S> reachableStates = new HashSet<>(initialStates);
      Deque<S> workQueue = new ArrayDeque<>(reachableStates);

      while (!workQueue.isEmpty()) {
        for (S successor : successors(workQueue.remove())) {
          if (reachableStates.add(successor)) {
            workQueue.add(successor);
          }
        }
      }

      statesCache = Set.copyOf(reachableStates);
    }

    return statesCache;
  }

  @Nullable
  protected final Set<S> cache() {
    return statesCache;
  }
}
