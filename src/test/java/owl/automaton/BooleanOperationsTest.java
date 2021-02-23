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

package owl.automaton;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;
import owl.bdd.FactorySupplier;

class BooleanOperationsTest {

  @Test
  void deterministicComplementEmpty() {


    var factory = FactorySupplier.defaultSupplier().getBddSetFactory(List.of("a"));
    var emptyAutomaton = EmptyAutomaton.of(factory, BuchiAcceptance.INSTANCE);
    var complementAutomaton = BooleanOperations.deterministicComplement(
      emptyAutomaton, new Object(), ParityAcceptance.class);

    Assertions.assertTrue(complementAutomaton.is(Automaton.Property.DETERMINISTIC));
    Assertions.assertTrue(complementAutomaton.is(Automaton.Property.COMPLETE));
    Assertions.assertEquals(1, complementAutomaton.states().size());
    Assertions.assertFalse(LanguageEmptiness.isEmpty(complementAutomaton));

    var complementComplementAutomaton = BooleanOperations.deterministicComplement(
      complementAutomaton, null, ParityAcceptance.class);

    Assertions.assertTrue(complementComplementAutomaton.is(Automaton.Property.DETERMINISTIC));
    Assertions.assertTrue(complementComplementAutomaton.is(Automaton.Property.COMPLETE));
    Assertions.assertEquals(1, complementComplementAutomaton.states().size());
    Assertions.assertTrue(LanguageEmptiness.isEmpty(complementComplementAutomaton));
  }
}