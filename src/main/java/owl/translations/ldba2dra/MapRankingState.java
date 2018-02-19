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
import org.immutables.value.Value;
import owl.automaton.util.AnnotatedState;

@Value.Style(of = "ofInternal")
@Value.Immutable(builder = false, copy = false, prehash = true)
public abstract class MapRankingState<S, K, V> implements AnnotatedState<S> {

  @Override
  @Value.Parameter
  public abstract S state();

  @Value.Parameter
  abstract Map<K, V> componentMap();

  static <S, T, K> MapRankingState<S, K, T> of(S state) {
    return of(state, Map.of());
  }

  static <S, T, K> MapRankingState<S, K, T> of(S state, Map<K, T> ranking) {
    return ImmutableMapRankingState.ofInternal(state, ranking);
  }

  @Override
  public String toString() {
    return String.format("|%s :: %s|", state(), componentMap());
  }
}
