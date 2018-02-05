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

import com.google.common.collect.Collections2;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;

public final class ValuationSetMapUtil {

  private ValuationSetMapUtil() {
  }

  public static <K> void add(Map<K, ValuationSet> map, K key, ValuationSet valuations) {
    ValuationSetFactory factory = valuations.getFactory();
    map.merge(key, valuations, factory::union);
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

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, Predicate<? super S> predicate) {
    map.entrySet().removeIf(entry -> predicate.test(entry.getKey().getSuccessor()));
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, S state, ValuationSet valuations) {
    ValuationSetFactory factory = valuations.getFactory();
    ValuationSet complement = factory.complement(valuations);

    map.entrySet().removeIf(entry -> {
      S successorState = entry.getKey().getSuccessor();

      if (!Objects.equals(state, successorState)) {
        return false;
      }

      ValuationSet edgeValuation = entry.getValue();
      entry.setValue(factory.intersection(edgeValuation, complement));
      return edgeValuation.isEmpty();
    });
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, Edge<S> edge,
    ValuationSet valuations) {
    ValuationSetFactory factory = valuations.getFactory();
    ValuationSet complement = factory.complement(valuations);

    map.computeIfPresent(edge, (key, value) -> {
      value = factory.intersection(value, complement);

      if (value.isEmpty()) {
        return null;
      }

      return value;
    });
  }

  public static <S> void remove(Map<Edge<S>, ValuationSet> map, ValuationSet valuations) {
    ValuationSetFactory factory = valuations.getFactory();
    ValuationSet complement = factory.complement(valuations);

    map.entrySet().removeIf(entry -> {
      entry.setValue(factory.intersection(entry.getValue(), complement));
      return entry.getValue().isEmpty();
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
        add(secondMap, newKey, entry.getValue());
      }

      return true;
    });

    secondMap.forEach((edge, valuations) -> add(map, edge, valuations));
  }

  public static <S> Collection<S> viewSuccessors(Map<Edge<S>, ValuationSet> map) {
    return Collections2.transform(map.keySet(), Edge::getSuccessor);
  }
}
