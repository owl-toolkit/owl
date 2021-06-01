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
import java.util.Set;

public interface TrieSet<E> extends Set<List<E>> {

  boolean containsKeyWithPrefix(List<?> prefix);

  Map<E, ? extends TrieSet<E>> subTries();

  /**
   * Retrieves the trieSet associated with the given prefix. Any changes to the subtrie are
   * reflected in the trie set and vice-versa.
   *
   * @param prefix the prefix of the key.
   * @return the corresponding trie set.
   */
  TrieSet<E> subTrie(List<? extends E> prefix);
}
