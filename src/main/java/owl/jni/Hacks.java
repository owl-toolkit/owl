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

package owl.jni;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.Collector;

final class Hacks {
  private Hacks() {}

  static LabelledFormula attachDummyAlphabet(Formula formula) {
    int largestAtom = Collector.collectAtoms(formula).stream().max().orElse(0);
    return LabelledFormula.of(formula, IntStream
      .range(0, largestAtom + 1)
      .mapToObj(i -> "p" + i)
      .collect(Collectors.toUnmodifiableList()));
  }
}
