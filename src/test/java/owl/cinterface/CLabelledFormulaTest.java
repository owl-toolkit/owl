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

package owl.cinterface;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.CONSTANT_FALSE;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.CONSTANT_TRUE;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.UNUSED;
import static owl.cinterface.CLabelledFormula.AtomicPropositionStatus.USED;
import static owl.cinterface.CLabelledFormula.simplify;

import java.util.List;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;

class CLabelledFormulaTest {

  @Test
  void testSimplifyAtomicPropostionStatusesModal() {
    var formula = LtlParser.parse("G (req | F gra)", List.of("req", "gra"));

    var mockedPointer1 = UnmanagedMemory.mallocCIntPointer(2);
    var mockedPointer2 = UnmanagedMemory.mallocCIntPointer(2);
    var mockedPointer3 = UnmanagedMemory.mallocCIntPointer(2);

    simplify(formula, 0, mockedPointer1, 2);
    simplify(formula, 1, mockedPointer2, 2);
    simplify(formula, 2, mockedPointer3, 2);

    assertAll(
      () -> assertTrue(mockedPointer1.read(0) == CONSTANT_TRUE.ordinal()
        || mockedPointer1.read(1) == CONSTANT_TRUE.ordinal()),
      () -> assertTrue(mockedPointer2.read(0) == CONSTANT_TRUE.ordinal()
        || mockedPointer2.read(1) == CONSTANT_TRUE.ordinal()),
      () -> assertEquals(List.of(CONSTANT_FALSE, CONSTANT_FALSE), mockedPointer3)
    );
  }

  @Test
  void testVariableStatusesPropositional() {

    var formula = LtlParser.parse(
      "i1 | !i2 | (o1 & !o2 & (i4 <-> o4))",
      List.of("i1", "i2", "i3", "i4", "o1", "o2", "o3", "o4"));

    var mockedPointer = UnmanagedMemory.mallocCIntPointer(8);

    simplify(formula, 4, mockedPointer, 8);

    assertEquals(List.of(
      // Inputs:
      CONSTANT_FALSE, CONSTANT_TRUE, UNUSED, USED,
      // Outputs:
      CONSTANT_TRUE, CONSTANT_FALSE, UNUSED, USED),
      mockedPointer);
  }

  private static void assertEquals(List<AtomicPropositionStatus> expected, CIntPointer actual) {
    for (int i = 0; i < expected.size(); i++) {
      Assertions.assertEquals(expected.get(i).ordinal(), actual.read(i));
    }
  }
}