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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.List;
import java.util.Map;
import owl.automaton.AnnotatedState;
import owl.collections.Collections3;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.SymmetricProductState;

@AutoValue
public abstract class SymmetricRankingState
  implements AnnotatedState<Map<Integer, EquivalenceClass>> {

  @Override
  public abstract Map<Integer, EquivalenceClass> state();

  public abstract List<Map.Entry<Integer, SymmetricProductState>> ranking();

  public abstract int safetyBucket();

  public abstract int safetyBucketIndex();

  static SymmetricRankingState of(Map<Integer, EquivalenceClass> state) {
    return of(state, List.of(), 0, -1);
  }

  static SymmetricRankingState of(Map<Integer, EquivalenceClass> state,
    List<Map.Entry<Integer, SymmetricProductState>> ranking,
    int safetyBucket, int safetyBucketIndex) {
    var copiedState = Map.copyOf(state);
    var copiedRanking = List.copyOf(ranking);

    checkState((safetyBucket == 0 && safetyBucketIndex == -1)
      || (safetyBucket > 0 && safetyBucketIndex >= 0));
    checkState(safetyBucket == 0 || copiedState.containsKey(safetyBucket));
    assert Collections3.isDistinct(copiedRanking) : "The ranking is not distinct: " + copiedRanking;
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
      return String.format("|%s :: %s|", state(), ranking());
    }

    return String
      .format("|%s :: %s :: %d (%d)|", state(), ranking(), safetyBucket(), safetyBucketIndex());
  }
}
