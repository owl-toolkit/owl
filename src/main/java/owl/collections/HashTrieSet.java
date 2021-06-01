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

import com.google.common.collect.Maps;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A TrieMap is a Map with sequences as keys that are organized in a Trie for value retrieval.
 *
 */
public class HashTrieSet<E> extends AbstractSet<List<E>> implements TrieSet<E> {

  // Dummy value to associate with an Object in the backing TrieMap
  private static final Object PRESENT = new Object();

  private final HashTrieMap<E, Object> map;

  private HashTrieSet(HashTrieMap<E, Object> internalMap) {
    this.map = internalMap;
  }

  public HashTrieSet() {
    this.map = new HashTrieMap<>();
  }

  public HashTrieSet(Collection<? extends List<E>> collection) {
    this();
    collection.forEach(element -> map.put(element, PRESENT));
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  @Override
  public boolean add(List<E> es) {
    return map.put(es, PRESENT) == null;
  }

  @Override
  public boolean remove(Object o) {
    return map.remove(o) == PRESENT;
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public Iterator<List<E>> iterator() {
    return map.keySet().iterator();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean containsKeyWithPrefix(List<?> prefix) {
    return map.containsKeyWithPrefix(prefix);
  }

  @Override
  public Map<E, ? extends TrieSet<E>> subTries() {
    return Maps.transformValues(map.subTries(), x -> new HashTrieSet<>((HashTrieMap<E, Object>) x));
  }

  @Override
  public TrieSet<E> subTrie(List<? extends E> prefix) {
    return new HashTrieSet<>(map.subTrie(prefix));
  }

  @Override
  public Stream<List<E>> stream() {
    return map.keySet().stream();
  }

  @Override
  public void forEach(Consumer<? super List<E>> action) {
    map.forEach((x, y) -> action.accept(x));
  }
}
