/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class RobustnessTest {
  @Test
  public void testIterationOrder() {
    Robustness[] values = Robustness.values();
    assertThat(values[0], is(Robustness.NEVER));
    assertThat(values[values.length - 1], is(Robustness.ALWAYS));
    for (int i = 0; i < values.length - 1; i++) {
      assertThat(values[i].stronger(), is(values[i + 1]));
      assertThat(values[i + 1].weaker(), is(values[i]));
    }
  }
}