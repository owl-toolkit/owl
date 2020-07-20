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

package owl.ltl.robust;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RobustnessTest {
  @Test
  void testIterationOrder() {
    Robustness[] values = Robustness.values();
    assertEquals(Robustness.NEVER, values[0]);
    assertEquals(Robustness.ALWAYS, values[values.length - 1]);

    for (int i = 0; i < values.length - 1; i++) {
      assertEquals(values[i].stronger(), values[i + 1]);
      assertEquals(values[i + 1].weaker(), values[i]);
    }
  }
}