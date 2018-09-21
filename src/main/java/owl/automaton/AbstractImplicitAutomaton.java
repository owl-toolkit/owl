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

import java.util.Collection;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.ValuationSetFactory;

public abstract class AbstractImplicitAutomaton<S, A extends OmegaAcceptance> extends
  AbstractCachedStatesAutomaton<S, A> {

  protected final A acceptance;
  protected final Set<S> initialStates;
  protected final ValuationSetFactory factory;

  public AbstractImplicitAutomaton(ValuationSetFactory factory, Collection<S> initialStates,
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
    return super.onlyInitialState();
  }

  @Override
  public final Set<S> initialStates() {
    return initialStates;
  }
}
