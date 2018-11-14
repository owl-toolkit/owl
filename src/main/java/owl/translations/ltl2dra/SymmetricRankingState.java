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

package owl.translations.ltl2dra;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import java.util.Map;
import org.immutables.value.Value;
import owl.automaton.util.AnnotatedState;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.SymmetricProductState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
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
    return SymmetricRankingStateTuple.create(state, ranking, safetyBucket, safetyBucketIndex);
  }

  @Value.Check
  protected void check() {
    checkState((safetyBucket() == 0 && safetyBucketIndex() == -1)
      || (safetyBucket() > 0 && safetyBucketIndex() >= 0));
    checkState(safetyBucket() == 0 || state().containsKey(safetyBucket()));
  }

  @Override
  public String toString() {
    if (safetyBucket() == 0) {
      return String.format("|%s :: %s|", state(), table());
    }

    return String
      .format("|%s :: %s :: %d (%d)|", state(), table(), safetyBucket(), safetyBucketIndex());
  }
}
