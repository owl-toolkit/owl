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

package owl.translations.ltl2dpa;

import javax.annotation.Nonnull;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.translations.ltl2ldba.AcceptingComponent;

final class WrappedParityAutomaton extends ParityAutomaton<AcceptingComponent.State> {
  private final AcceptingComponent ba;

  WrappedParityAutomaton(AcceptingComponent ba) {
    super(ba, new ParityAcceptance(1, ParityAcceptance.Priority.EVEN));
    this.ba = ba;
    initialStates = ba.getInitialStates();
  }

  @Nonnull
  @Override
  protected Edge<AcceptingComponent.State> generateRejectingEdge(
    AcceptingComponent.State successor) {
    return Edges.create(successor);
  }

  @Nonnull
  @Override
  public AcceptingComponent.State generateRejectingTrap() {
    return ba.generateRejectingTrap();
  }
}