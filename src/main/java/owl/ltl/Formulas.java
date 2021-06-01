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

package owl.ltl;

import java.util.Collection;
import java.util.Set;
import owl.collections.Collections3;

public final class Formulas {
  private Formulas() {}

  public static int compare(Set<? extends Formula> set1, Set<? extends Formula> set2) {
    int lengthComparison = Integer.compare(set1.size(), set2.size());

    if (lengthComparison != 0) {
      return lengthComparison;
    }

    int heightComparison = Integer.compare(height(set1), height(set2));

    if (heightComparison != 0) {
      return heightComparison;
    }

    return Collections3.compare(set1, set2);
  }

  public static int height(Formula... formulas) {
    int height = 0;

    for (var formula : formulas) {
      height = Math.max(height, formula.height());
    }

    return height;
  }

  public static int height(Collection<? extends Formula> collection) {
    int height = 0;

    for (var formula : collection) {
      height = Math.max(height, formula.height());
    }

    return height;
  }
}
