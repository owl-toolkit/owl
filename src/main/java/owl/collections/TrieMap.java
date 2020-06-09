/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.collections;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A TrieMap is a Map with sequences as keys that are organized in a Trie for value retrieval.
 * @param <K> List of K ist a key in this map.
 * @param <V> Values stored.
 */
public class TrieMap<K,V> {
  @Nullable
  private V value = null;

  public Map<K, TrieMap<K,V>> suc = new HashMap<>();

  /** Returns a fresh empty trie. */
  public static <K,V> TrieMap<K,V> create() {
    return new TrieMap<K,V>();
  }

  public boolean isEmpty() {
    return value == null && suc.isEmpty();
  }

  public Optional<TrieMap<K,V>> traverse(List<K> ks, boolean create) {
    var curr = this;
    for (var k : ks) {
      if (!curr.suc.containsKey(k)) {
        if (!create) {
          return Optional.empty();
        }
        curr.suc.put(k, TrieMap.create());
      }
      curr = curr.suc.get(k);
    }
    return Optional.of(curr);
  }

  /** Put value at provided key position. Will replace existing. */
  public void put(List<K> ks, V val) {
    var curr = traverse(ks, true).get(); //safe to do, as we create if missing
    curr.value = val;
  }

  /** Check whether the value at the precise given key (for inSubtree=false)
   *  or its subtree (for inSubtree=true) exists.
   */
  public boolean has(List<K> ks, boolean inSubtree) {
    return get(ks, inSubtree).isPresent();
  }

  /** Retrieve value at given key. If any=true, returns any value in subtree rooted at key. */
  public Optional<V> get(List<K> ks, boolean any) {
    var curr = traverse(ks, false);
    if (curr.isEmpty()) {
      return Optional.empty();
    }

    var tr = curr.get();
    if (!any && tr.value == null) {
      return Optional.empty();
    }

    while (tr.value == null) {
      if (tr.suc.isEmpty()) {
        return Optional.empty();
      }
      tr = tr.suc.values().iterator().next();
    }

    return Optional.of(tr.value);
  }

  public Optional<V> getRootValue() {
    return value == null ? Optional.empty() : Optional.of(value);
  }

  /** Returns size (O(n) operation, traverses tree). */
  public int size() {
    int sz = value == null ? 0 : 1;
    for (var sub : suc.values()) {
      sz += sub.size();
    }
    return sz;
  }

}
