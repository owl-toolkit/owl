/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import com.google.common.collect.Streams;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A TrieMap is a Map with sequences as keys that are organized in a Trie for value retrieval.
 *
 * @param <K> List of K ist a key in this map.
 * @param <V> Values stored.
 */
public class HashTrieMap<K,V> extends AbstractMap<List<K>, V> implements TrieMap<K, V> {

  @SuppressWarnings("PMD.LooseCoupling")
  private final HashMap<K, HashTrieMap<K,V>> map;
  @Nullable
  private V value;

  // Caches
  @Nullable
  private EntrySet cachedEntrySet;

  public HashTrieMap() {
    cachedEntrySet = null;
    map = new HashMap<>();
    value = null;
  }

  // Implement Trie methods.

  @Override
  public boolean containsKeyWithPrefix(List<?> prefix) {
    var subTrie = subTrieIfPresent(prefix);
    return subTrie != null && !subTrie.isEmpty();
  }

  @Override
  public Map<K, ? extends TrieMap<K, V>> subTries() {
    return Collections.unmodifiableMap(map);
  }

  @Override
  public HashTrieMap<K, V> subTrie(List<? extends K> prefix) {
    var subTrie = this;

    for (K key : prefix) { // Implicit null check.
      subTrie = subTrie.map.computeIfAbsent(Objects.requireNonNull(key), x -> new HashTrieMap<>());
    }

    return subTrie;
  }

  @Nullable
  private HashTrieMap<K, V> subTrieIfPresent(Object obj) {
    Objects.requireNonNull(obj);

    if (!(obj instanceof List)) {
      return null;
    }

    var prefix = (List<?>) obj;
    var subTrie = this;

    for (Object key : prefix) {
      subTrie = subTrie.map.get(Objects.requireNonNull(key));

      if (subTrie == null) {
        return null;
      }
    }

    return subTrie;
  }

  // Implement Map methods.

  @Override
  public boolean containsKey(Object key) {
    var subTrie = subTrieIfPresent(key);
    return subTrie != null && subTrie.value != null;
  }

  @Override
  public boolean containsValue(Object value) {
    if (value.equals(this.value)) { // Implicit null check
      return true;
    }

    for (var subTrie : map.values()) {
      if (subTrie.containsValue(value)) {
        return true;
      }
    }

    return false;
  }

  @Override
  @Nullable
  public V get(Object key) {
    var subTrie = subTrieIfPresent(key);
    return subTrie == null ? null : subTrie.value;
  }

  @Override
  public V put(List<K> key, V value) {
    var subTrie = subTrie(key);
    V oldValue = subTrie.value;
    subTrie.value = Objects.requireNonNull(value);
    return oldValue;
  }

  @Nullable
  @Override
  public V remove(Object key) {
    var subTrie = subTrieIfPresent(key);

    if (subTrie == null) {
      return null;
    }

    V oldValue = subTrie.value;
    subTrie.value = null;
    return oldValue;
  }

  @Override
  public void clear() {
    value = null;
    map.values().forEach(HashTrieMap::clear);
  }

  @Override
  public boolean isEmpty() {
    return value == null && map.values().stream().allMatch(TrieMap::isEmpty);
  }

  @Override
  public int size() {
    int size = value == null ? 0 : 1;

    for (var subTrie : map.values()) {
      size += subTrie.size();
    }

    return size;
  }

  @Override
  public Set<Entry<List<K>, V>> entrySet() {
    if (cachedEntrySet == null) {
      cachedEntrySet = new EntrySet();
    }

    return cachedEntrySet;
  }

  private class EntrySet extends AbstractSet<Entry<List<K>, V>> {
    @Override
    public Iterator<Entry<List<K>, V>> iterator() {
      return stream().iterator();
    }

    @Override
    public Stream<Entry<List<K>, V>> stream() {
      Stream<Entry<List<K>, V>> childrenStream = map.entrySet().stream().flatMap(x ->
        x.getValue().entrySet().stream().map(y -> {
          List<K> newKey = new ArrayList<>();
          newKey.add(x.getKey());
          newKey.addAll(y.getKey());
          return Map.entry(newKey, y.getValue());
        }));

      if (value == null) {
        return childrenStream;
      }

      return Streams.concat(Stream.of(Map.entry(List.of(), value)), childrenStream);
    }

    @Override
    public void clear() {
      HashTrieMap.this.clear();
    }

    @Override
    public boolean isEmpty() {
      return HashTrieMap.this.isEmpty();
    }

    @Override
    public int size() {
      return HashTrieMap.this.size();
    }
  }
}
