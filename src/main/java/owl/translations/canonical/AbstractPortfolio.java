/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.translations.canonical;

import java.util.Optional;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.ltl.LabelledFormula;

public abstract class AbstractPortfolio<A extends EmersonLeiAcceptance>
  implements Function<LabelledFormula, Optional<Automaton<?, ? extends A>>> {

  final Class<A> acceptanceBound;

  AbstractPortfolio(Class<A> acceptanceBound) {
    this.acceptanceBound = acceptanceBound;
  }

  boolean isAllowed(Class<? extends EmersonLeiAcceptance> acceptance) {
    return OmegaAcceptanceCast.isInstanceOf(acceptance, acceptanceBound);
  }

  Optional<Automaton<?, ? extends A>> box(Automaton<?, ?> automaton) {
    return Optional.of(OmegaAcceptanceCast.cast(automaton, acceptanceBound));
  }
}
