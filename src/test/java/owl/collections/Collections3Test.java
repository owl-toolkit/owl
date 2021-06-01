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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static owl.collections.Collections3.maximalElements;
import static owl.collections.Collections3.partition;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.ClassNamingConventions")
class Collections3Test {

  @Test
  void testMaximalElements() {
    assertEquals(List.of(),
      maximalElements(List.of(), Object::equals));

    assertEquals(List.of(0),
      maximalElements(List.of(0), Integer::equals));

    assertEquals(List.of(100),
      maximalElements(List.of(2, 3, -1, 0, 100, 5, 100, 99, 1, 0), (x, y) -> x < y));

    assertEquals(List.of("a", "b"),
      maximalElements(List.of("baa", "aa", "a", "b", "a"), String::startsWith));
  }

  @Test
  void testPartition() {
    assertEquals(List.of(),
      partition(Set.of(), Object::equals));

    assertEquals(List.of(Set.of(0)),
      partition(Set.of(0), Object::equals));

    assertEquals(Set.of(Set.of(1, 2, 3), Set.of(11, 12, 13), Set.of(100)),
      Set.copyOf(partition(Set.of(1, 2, 3, 11, 12, 13, 100), (x, y) -> Math.abs(x - y) == 1)));
  }
}