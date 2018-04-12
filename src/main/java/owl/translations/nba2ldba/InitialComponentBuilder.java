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

import com.google.common.collect.Collections2;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.MutableAutomatonBuilder;

final class InitialComponentBuilder<S> implements MutableAutomatonBuilder<S, S, NoneAcceptance> {

  private final Automaton<S, GeneralizedBuchiAcceptance> nba;

  private InitialComponentBuilder(Automaton<S, GeneralizedBuchiAcceptance> nba) {
    this.nba = nba;
  }

  static <S> InitialComponentBuilder<S> create(Automaton<S, GeneralizedBuchiAcceptance> nba) {
    return new InitialComponentBuilder<>(nba);
  }

  @Override
  public S add(S stateKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MutableAutomaton<S, NoneAcceptance> build() {
    MutableAutomaton<S, NoneAcceptance> automaton = MutableAutomatonFactory
      .create(NoneAcceptance.INSTANCE, nba.factory());

    AutomatonUtil.explore(automaton, nba.initialStates(), (state, valuation) ->
      Collections2.transform(nba.successors(state, valuation), Edge::of));

    automaton.initialStates(nba.initialStates());
    return automaton;
  }
}
