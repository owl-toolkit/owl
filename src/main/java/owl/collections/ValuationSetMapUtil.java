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

package owl.collections;

import com.google.common.collect.Collections2;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.edge.Edge;

public final class ValuationSetMapUtil {

  private ValuationSetMapUtil() {
  }

  public static <K> void add(Map<K, ValuationSet> map, K key, ValuationSet valuations) {
    map.merge(key, valuations, ValuationSet::union);
  }

  public static <K> void add(Map<K, ValuationSet> map, Map<K, ValuationSet> secondMap) {
    secondMap.forEach((edge, valuations) -> add(map, edge, valuations));
  }

  @Nullable
  public static <K> K findFirst(Map<K, ValuationSet> map, BitSet valuation) {
    for (Map.Entry<K, ValuationSet> entry : map.entrySet()) {
      if (entry.getValue().contains(valuation)) {
        return entry.getKey();
      }
    }

    return null;
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, S state) {
    map.keySet().removeIf(edge -> edge.getSuccessor().equals(state));
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, S state, ValuationSet valuations) {
    map.entrySet().removeIf(entry -> {
      S successorState = entry.getKey().getSuccessor();

      if (!state.equals(successorState)) {
        return false;
      }

      ValuationSet edgeValuation = entry.getValue();
      edgeValuation.removeAll(valuations);
      return edgeValuation.isEmpty();
    });
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, Edge<S> edge,
    ValuationSet valuations) {
    map.computeIfPresent(edge, (key, value) -> {
      value.removeAll(valuations);

      if (value.isEmpty()) {
        return null;
      }

      return value;
    });
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, ValuationSet valuations) {
    map.values().removeIf(vs -> {
      vs.removeAll(valuations);
      return vs.isEmpty();
    });
  }

  public static <K> void update(Map<K, ValuationSet> map, Function<K, K> keyUpdater) {
    Map<K, ValuationSet> secondMap = new HashMap<>();

    map.entrySet().removeIf((entry) -> {
      K oldKey = entry.getKey();
      K newKey = keyUpdater.apply(oldKey);

      if (oldKey.equals(newKey)) {
        return false;
      }

      if (newKey != null) {
        add(secondMap, newKey, entry.getValue());
      }

      return true;
    });

    add(map, secondMap);
  }

  public static <S> Collection<S> viewSuccessors(Map<Edge<S>, ValuationSet> map) {
    return Collections2.transform(map.keySet(), Edge::getSuccessor);
  }
}
