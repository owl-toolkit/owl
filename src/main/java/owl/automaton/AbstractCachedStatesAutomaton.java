/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;

public abstract class AbstractCachedStatesAutomaton<S, A extends OmegaAcceptance>
  implements Automaton<S, A> {

  @Nullable
  private Set<S> statesCache;

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
}
