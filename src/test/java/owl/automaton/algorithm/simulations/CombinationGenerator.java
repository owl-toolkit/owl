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

package owl.automaton.algorithm.simulations;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

public final class CombinationGenerator {

  private CombinationGenerator() {}

  private static int count(int mask) {
    int n;
    int m = mask;
    for (n = 0; m > 0; ++n) {
      m &= (m - 1);
    }
    return n;
  }

  public static <R> List<List<R>> comb(List<R> input, int k) {
    List<List<R>> out = new ArrayList<>();

    for (int i = 1; i <= k; i++) {
      for (int mask = 0; mask < (1 << input.size()); mask++) {
        if (count(mask) == i) {
          List<R> list = new ArrayList<>();

          int bound = input.size();

          for (int i1 = 0; i1 < bound; i1++) {
            if ((mask & (1 << i1)) > 0) {
              list.add(input.get(i1));
            }
          }

          out.add(list);
        }
      }
    }

    return Lists.reverse(out);
  }
}
