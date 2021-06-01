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

package owl.automaton;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;

class BooleanOperationsTest {

  @Test
  void deterministicComplementEmpty() {

    var emptyAutomaton = EmptyAutomaton.of(List.of("a"), BuchiAcceptance.INSTANCE);
    var complementAutomaton = BooleanOperations.deterministicComplement(
      emptyAutomaton, ParityAcceptance.class);

    Assertions.assertTrue(complementAutomaton.is(Automaton.Property.DETERMINISTIC));
    Assertions.assertTrue(complementAutomaton.is(Automaton.Property.COMPLETE));
    Assertions.assertEquals(1, complementAutomaton.states().size());
    Assertions.assertFalse(LanguageEmptiness.isEmpty(complementAutomaton));

    var complementComplementAutomaton = BooleanOperations.deterministicComplement(
      complementAutomaton, ParityAcceptance.class);

    Assertions.assertTrue(complementComplementAutomaton.is(Automaton.Property.DETERMINISTIC));
    Assertions.assertTrue(complementComplementAutomaton.is(Automaton.Property.COMPLETE));
    Assertions.assertEquals(1, complementComplementAutomaton.states().size());
    Assertions.assertTrue(LanguageEmptiness.isEmpty(complementComplementAutomaton));
  }
}