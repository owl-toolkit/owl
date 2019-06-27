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

package owl.translations.ltl2dpa;

import java.util.List;
import org.immutables.value.Value;
import owl.automaton.util.AnnotatedState;
import owl.collections.Collections3;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.AsymmetricProductState;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class AsymmetricRankingState implements AnnotatedState<EquivalenceClass> {
  @Override
  public abstract EquivalenceClass state();

  abstract List<AsymmetricProductState> ranking();

  abstract int safetyIndex();

  static AsymmetricRankingState of(EquivalenceClass state) {
    return of(state, List.of(), -1);
  }

  static AsymmetricRankingState of(EquivalenceClass state, List<AsymmetricProductState> ranking,
    int safetyProgress) {
    assert Collections3.isDistinct(ranking) : "The following list is not distinct: " + ranking;
    return AsymmetricRankingStateTuple.create(state, ranking, safetyProgress);
  }

  @Override
  public String toString() {
    return String.format("|%s :: %s :: %s|", state(), ranking(), safetyIndex());
  }
}
