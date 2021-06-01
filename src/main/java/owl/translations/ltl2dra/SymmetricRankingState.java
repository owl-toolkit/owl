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

package owl.translations.ltl2dra;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import java.util.Map;
import owl.automaton.AnnotatedState;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.SymmetricProductState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

@AutoValue
public abstract class SymmetricRankingState
  implements AnnotatedState<Map<Integer, EquivalenceClass>> {

  SymmetricRankingState() {
    // This constructor is intentionally empty. Only AutoValue is allowed to subclass.
  }

  @Override
  public abstract Map<Integer, EquivalenceClass> state();

  public abstract Table<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState> table();

  public static SymmetricRankingState of(Map<Integer, EquivalenceClass> state) {
    return of(state, ImmutableTable.of());
  }

  static SymmetricRankingState of(Map<Integer, EquivalenceClass> state,
    Table<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState> ranking) {
    var copiedState = Map.copyOf(state);
    var copiedRanking = ImmutableTable.copyOf(ranking);
    return new AutoValue_SymmetricRankingState(copiedState, copiedRanking);
  }

  @Override
  public abstract boolean equals(Object object);

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return String.format("|%s :: %s|", state(), table());
  }
}
