/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.nba2ldba;

import javax.annotation.Nonnull;
import omega_automaton.StoredBuchiAutomaton;
import owl.translations.ldba.AbstractInitialComponent;

public class InitialComponent
  extends AbstractInitialComponent<StoredBuchiAutomaton.State, AcceptingComponent.State> {

  private final AcceptingComponent acceptingComponent;
  private final StoredBuchiAutomaton nba;

  InitialComponent(StoredBuchiAutomaton nba, AcceptingComponent acceptingComponent) {
    super(nba.getFactories());
    this.nba = nba;
    this.transitions.putAll(nba.getTransitions());
    this.acceptingComponent = acceptingComponent;
  }

  StoredBuchiAutomaton.State createState(StoredBuchiAutomaton.State initialState) {
    initialStates.add(initialState);
    return initialState;
  }

  @Override
  public void generateJumps(@Nonnull StoredBuchiAutomaton.State state) {
    if (nba.isAccepting(state)) {
      AcceptingComponent.State succ2 = acceptingComponent.createState(state);
      epsilonJumps.put(state, succ2);
    }
  }
}
