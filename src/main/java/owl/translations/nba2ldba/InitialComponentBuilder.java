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
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edges;

public final class InitialComponentBuilder<S> implements ExploreBuilder<S, S, NoneAcceptance> {

  private final Automaton<S, BuchiAcceptance> nba;

  private InitialComponentBuilder(Automaton<S, BuchiAcceptance> nba) {
    this.nba = nba;
  }

  public static <S> InitialComponentBuilder<S> create(Automaton<S, BuchiAcceptance> nba) {
    return new InitialComponentBuilder<>(nba);
  }

  @Override
  public S add(S stateKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MutableAutomaton<S, NoneAcceptance> build() {
    MutableAutomaton<S, NoneAcceptance> automaton = AutomatonFactory
      .create(new NoneAcceptance(), nba.getFactory());

    AutomatonUtil.explore(automaton, nba.getInitialStates(), (state, valuation) ->
      Collections2.transform(nba.getSuccessors(state, valuation), Edges::create));

    automaton.setInitialStates(nba.getInitialStates());
    return automaton;
  }
}
