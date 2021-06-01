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

import java.util.List;
import java.util.Map;

/**
 * A trieMap maps sequences of keys to a value. Tries do no support 'null' keys and values.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 */
public interface TrieMap<K, V> extends Map<List<K>, V> {

  boolean containsKeyWithPrefix(List<?> prefix);

  Map<K, ? extends TrieMap<K, V>> subTries();

  /**
   * Retrieves the trieMap associated with the given prefix. Any changes to the subtrie are
   * reflected in the trieMap and vice-versa.
   *
   * @param prefix the prefix of the key.
   * @return the corresponding trie map.
   */
  TrieMap<K, V> subTrie(List<? extends K> prefix);
}
