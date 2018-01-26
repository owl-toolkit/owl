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
public final class FlatRankingState<S, T> extends AnnotatedState<S> {
  public final ImmutableList<T> ranking;
  public final int safetyProgress;

  private FlatRankingState(@Nullable S state, List<T> ranking, int safetyProgress) {
    super(state);
    assert Collections3.isDistinct(ranking);
    this.ranking = ImmutableList.copyOf(ranking);
    this.safetyProgress = safetyProgress;
  }

  static <S, T> FlatRankingState<S, T> of(@Nullable S state) {
    return of(state, List.of(), -1);
  }

  static <S, T> FlatRankingState<S, T> of(@Nullable S state, List<T> ranking, int safetyProgress) {
    return new FlatRankingState<>(state, ranking, safetyProgress);
  }

  public static <S, T> FlatRankingState<S, T> of() {
    return of(null);
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    FlatRankingState<?, ?> that = (FlatRankingState<?, ?>) o;
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
