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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Numbering<E> {

  private final Map<E, Integer> mapping;
  private final List<E> reverseMapping;

  public Numbering() {
    mapping = new HashMap<>();
    reverseMapping = new ArrayList<>();
  }

  public Numbering(int initialCapacity) {
    mapping = new HashMap<>(initialCapacity);
    reverseMapping = new ArrayList<>(initialCapacity);
  }

  public int lookup(E element) {
    Integer number = mapping.get(element);

    if (number == null) {
      mapping.put(element, reverseMapping.size());
      reverseMapping.add(element);
      return reverseMapping.size() - 1;
    }

    return number;
  }

  public E lookup(int index) {
    if (reverseMapping.size() <= index) {
      throw new IllegalArgumentException("no mapping defined");
    }

    return reverseMapping.get(index);
  }

  public Map<E, Integer> asMap() {
    return Collections.unmodifiableMap(mapping);
  }
}
