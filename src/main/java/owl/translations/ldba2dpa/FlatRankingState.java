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

package owl.translations.ldba2dpa;

import java.util.List;
import org.immutables.value.Value;
import owl.automaton.util.AnnotatedState;
import owl.collections.Collections3;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class FlatRankingState<S, T> implements AnnotatedState<S> {
  @Override
  public abstract S state();

  abstract List<T> ranking();

  abstract int safetyProgress();


  public static <S, T> FlatRankingState<S, T> of(S state) {
    return of(state, List.of(), -1);
  }

  static <S, T> FlatRankingState<S, T> of(S state, List<T> ranking, int safetyProgress) {
    assert Collections3.isDistinct(ranking);
    return FlatRankingStateTuple.create(state, ranking, safetyProgress);
  }


  @Override
  public String toString() {
    return String.format("|%s :: %s :: %s|", state(), ranking(), safetyProgress());
  }
}