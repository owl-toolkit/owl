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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.edge.Edge;

public final class ValuationSetMapUtil {

  private ValuationSetMapUtil() {
  }

  public static <K> Set<K> findAll(Map<K, ValuationSet> map, BitSet valuation) {
    Set<K> edges = new HashSet<>(map.size());

    map.forEach((key, valuations) -> {
      if (valuations.contains(valuation)) {
        edges.add(key);
      }
    });

    return edges;
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

  @Nullable
  public static <K> K findOnly(Map<K, ValuationSet> map, BitSet valuation) {
    @Nullable
    K key = null;

    for (Map.Entry<K, ValuationSet> entry : map.entrySet()) {
      if (entry.getValue().contains(valuation)) {
        checkArgument(key == null, "Multiple entries found for valuation %s: %s and %s",
          valuation, key, entry.getKey());
        key = entry.getKey();
      }
    }

    return key;
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, S state, ValuationSet valuations) {
    ValuationSet complement = valuations.complement();

    map.entrySet().removeIf(entry -> {
      S successorState = entry.getKey().getSuccessor();

      if (!Objects.equals(state, successorState)) {
        return false;
      }

      ValuationSet edgeValuation = entry.getValue();
      entry.setValue(edgeValuation.intersection(complement));
      return edgeValuation.isEmpty();
    });
  }

  public static <K> void update(Map<K, ValuationSet> map, Function<K, K> keyUpdater) {
    Map<K, ValuationSet> secondMap = new HashMap<>();

    map.entrySet().removeIf((entry) -> {
      K oldKey = entry.getKey();
      K newKey = keyUpdater.apply(oldKey);

      if (Objects.equals(oldKey, newKey)) {
        return false;
      }

      if (newKey != null) {
        secondMap.merge(newKey, entry.getValue(), ValuationSet::union);
      }

      return true;
    });

    secondMap.forEach((edge, valuations) -> map.merge(edge, valuations, ValuationSet::union));
  }
}
