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

package owl.translations.ldba2dra;

import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import owl.translations.ldba2dpa.AnnotatedState;
import owl.util.ImmutableObject;

@Immutable
public final class MapRankingState<S, K, V> extends AnnotatedState<S> {
  public final Map<K, V> componentMap;

  private MapRankingState(@Nullable S state, Map<K, V> componentMap) {
    super(state);
    this.componentMap = componentMap;
  }

  static <S, T, K> MapRankingState<S, K, T> of(@Nullable S state) {
    return of(state, Map.of());
  }

  static <S, T, K> MapRankingState<S, K, T> of(@Nullable S state, Map<K, T> ranking) {
    return new MapRankingState<>(state, ranking);
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    MapRankingState<?, ?, ?> that = (MapRankingState<?, ?, ?>) o;
    return Objects.equals(state, that.state) && Objects.equals(componentMap, that.componentMap);
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(state, componentMap);
  }

  @Override
  public String toString() {
    return String.format("|%s :: %s|", state, componentMap);
  }
}
