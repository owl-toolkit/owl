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
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import owl.collections.Trie;
import owl.util.ImmutableObject;

@Immutable
public final class RankingState<S, T> extends ImmutableObject {
  final ImmutableList<T> ranking;
  @Nullable
  final S state;
  final int volatileIndex;

  private RankingState(@Nullable S state, ImmutableList<T> ranking, int volatileIndex) {
    this.state = state;
    this.ranking = ranking;
    this.volatileIndex = volatileIndex;
  }

  public static <S, T> RankingState<S, T> createSink() {
    return create(null);
  }

  static <S, T> RankingState<S, T> create(S initialComponentState) {
    return create(initialComponentState, ImmutableList.of(), 0, null);
  }

  static <S, T> RankingState<S, T> create(@Nullable S initialComponentState, List<T> ranking,
    int volatileIndex, @Nullable Map<S, Trie<T>> trieMap) {
    if (trieMap != null) {
      trieMap.computeIfAbsent(initialComponentState, x -> new Trie<>()).add(ranking);
    }

    return new RankingState<>(initialComponentState, ImmutableList.copyOf(ranking), volatileIndex);
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    RankingState<?, ?> that = (RankingState<?, ?>) o;
    return that.volatileIndex == this.volatileIndex
      && Objects.equals(state, that.state)
      && Objects.equals(ranking, that.ranking);
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(state, ranking, volatileIndex);
  }

  @Override
  public String toString() {
    return String.format("|%s :: %s :: %d|", state, ranking, volatileIndex);
  }
}
