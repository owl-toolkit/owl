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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.algorithm.LanguageEmptiness;

class ViewsTest {

  @Test
  void completeEmptyAutomaton() {
    var emptyAutomaton = EmptyAutomaton.of(List.of("a"), AllAcceptance.INSTANCE);
    var completeAutomaton = Views.complete(emptyAutomaton);

    assertEquals(1, completeAutomaton.initialStates().size());
    assertEquals(completeAutomaton.states(), completeAutomaton.initialStates());
    assertEquals(Map.of(), AutomatonUtil.getIncompleteStates(completeAutomaton));
    assertTrue(completeAutomaton.is(Automaton.Property.COMPLETE));
    assertTrue(LanguageEmptiness.isEmpty(completeAutomaton));
  }
}