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

package owl.translations.fgx2dpa;

import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import owl.collections.ValuationSet;
import owl.ltl.Formula;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class StateReduction {
  abstract Formula formula();

  abstract List<PromisedSet> permutation();

  abstract Map<State, ValuationSet> successors();


  static StateReduction of(State state, Map<State, ValuationSet> successors) {
    return StateReductionTuple.create(state.formula(), state.permutation(), successors);
  }
}
