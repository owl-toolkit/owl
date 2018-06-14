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

package owl.translations.fgx2dpa;

import java.util.List;
import java.util.Set;
import org.immutables.value.Value;
import owl.ltl.BooleanConstant;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class State {
  abstract Formula formula();

  abstract Set<Monitor<GOperator>> monitorsG();

  abstract Set<Monitor<FOperator>> monitorsF();

  abstract int priority();

  abstract List<PromisedSet> permutation();

  public static State of(Formula formula, Set<Monitor<GOperator>> monitorsG,
    Set<Monitor<FOperator>> monitorsF, int priority, List<PromisedSet> permutation) {
    return StateTuple.create(formula, monitorsG, monitorsF, priority, permutation);
  }

  static State of(BooleanConstant constant) {
    int priority = constant.equals(BooleanConstant.TRUE) ? 2 : 3;
    return of(constant, Set.of(), Set.of(), priority,
      List.of(PromisedSet.of(Set.of(), List.of(),constant)));
  }
}
