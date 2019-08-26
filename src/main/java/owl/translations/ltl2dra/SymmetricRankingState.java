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

package owl.translations.ltl2dra;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import java.util.Map;
import owl.automaton.util.AnnotatedState;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.SymmetricProductState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

@AutoValue
abstract class SymmetricRankingState implements AnnotatedState<Map<Integer, EquivalenceClass>> {

  @Override
  public abstract Map<Integer, EquivalenceClass> state();

  abstract Table<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState> table();

  abstract int safetyBucket();

  abstract int safetyBucketIndex();

  static SymmetricRankingState of(Map<Integer, EquivalenceClass> state) {
    return of(state, ImmutableTable.of(), 0, -1);
  }

  static SymmetricRankingState of(Map<Integer, EquivalenceClass> state,
    Table<Integer, SymmetricEvaluatedFixpoints, SymmetricProductState> ranking,
    int safetyBucket,
    int safetyBucketIndex) {
    var copiedState = Map.copyOf(state);
    var copiedRanking = ImmutableTable.copyOf(ranking);

    checkState((safetyBucket == 0 && safetyBucketIndex == -1)
      || (safetyBucket > 0 && safetyBucketIndex >= 0));
    checkState(safetyBucket == 0 || copiedState.containsKey(safetyBucket));

    return new AutoValue_SymmetricRankingState(
      copiedState, copiedRanking, safetyBucket, safetyBucketIndex);
  }

  @Override
  public abstract boolean equals(Object object);

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    if (safetyBucket() == 0) {
      return String.format("|%s :: %s|", state(), table());
    }

    return String
      .format("|%s :: %s :: %d (%d)|", state(), table(), safetyBucket(), safetyBucketIndex());
  }
}
