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

package owl.game;

import java.util.List;
import java.util.function.Function;
import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.util.annotation.Tuple;

@Value.Immutable
@Tuple
public abstract class Game<S, A extends OmegaAcceptance> {

  public abstract Automaton<S, A> automaton();

  public abstract Owner owner(S state);

  public final List<String> variables(Owner owner) {
    return owner == Owner.ENVIRONMENT ? ENVIRONMENTvariables() : SYSTEMvariables();
  }

  public abstract List<String> ENVIRONMENTvariables();

  public abstract List<String> SYSTEMvariables();

  public static <S, A extends OmegaAcceptance> Game<S, A> of(Automaton<S, A> automaton, List<String> envVariables) {
    return null;
  }

  public <B extends OmegaAcceptance> Game<S, B> updateAutomaton(Function<Automaton<S, A>, Automaton<S, B>> updater) {
    return of(updater.apply(automaton()), ENVIRONMENTvariables());
  }

  public enum Owner {
    /**
     * This player wants to dissatisfy the acceptance condition.
     */
    ENVIRONMENT,

    /**
     * This player wants to satisfy the acceptance condition.
     */
    SYSTEM;

    public Owner opponent() {
      return this == ENVIRONMENT ? SYSTEM : ENVIRONMENT;
    }
  }
}
