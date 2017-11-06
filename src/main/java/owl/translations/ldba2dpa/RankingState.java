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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import owl.collections.Collections3;
import owl.util.ImmutableObject;

@Immutable
public final class RankingState<S, T> extends ImmutableObject {
  final ImmutableList<T> ranking;
  @Nullable
  final S state;
  final int safetyProgress;

  private RankingState(@Nullable S state, ImmutableList<T> ranking, int safetyProgress) {
    assert Collections3.isDistinct(ranking);
    this.state = state;
    this.ranking = ranking;
    this.safetyProgress = safetyProgress;
  }

  static <S, T> RankingState<S, T> create(@Nullable S initialComponentState) {
    return create(initialComponentState, ImmutableList.of(), -1);
  }

  static <S, T> RankingState<S, T> create(@Nullable S initialComponentState, List<T> ranking,
    int safetyProgress) {
    return new RankingState<>(initialComponentState, ImmutableList.copyOf(ranking), safetyProgress);
  }

  public static <S, T> RankingState<S, T> createSink() {
    return create(null);
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    RankingState<?, ?> that = (RankingState<?, ?>) o;
    return safetyProgress == that.safetyProgress
      && Objects.equals(state, that.state)
      && Objects.equals(ranking, that.ranking);
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(state, ranking, safetyProgress);
  }

  @Override
  public String toString() {
    return String.format("|%s :: %s :: %s|", state, ranking, safetyProgress);
  }
}
