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

package owl.util;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.SingletonAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;

class ParallelEvaluationTest {

  @Test
  void takeSmallest() {
    Automaton<Object, GeneralizedBuchiAcceptance> empty1
      = EmptyAutomaton.of(List.of("a", "b"), GeneralizedBuchiAcceptance.of(1));

    Automaton<Object, GeneralizedBuchiAcceptance> empty2
      = EmptyAutomaton.of(List.of("a", "b"), GeneralizedBuchiAcceptance.of(2));

    Automaton<Object, GeneralizedBuchiAcceptance> singleton1
      = SingletonAutomaton.of(List.of("a", "b"), new Object(), GeneralizedBuchiAcceptance.of(1));

    Automaton<Object, GeneralizedBuchiAcceptance> singleton2
      = SingletonAutomaton.of(List.of("a", "b"), new Object(), GeneralizedBuchiAcceptance.of(2));

    Assertions.assertEquals(empty1,
      ParallelEvaluation.takeSmallest(List.of(empty1, empty2, singleton1, singleton2)));
    Assertions.assertEquals(empty1,
      ParallelEvaluation.takeSmallest(List.of(singleton1, singleton2, empty1, empty2)));

    Assertions.assertEquals(empty2,
      ParallelEvaluation.takeSmallest(List.of(empty2, singleton1, singleton2)));
    Assertions.assertEquals(empty2,
      ParallelEvaluation.takeSmallest(List.of(singleton2, singleton1, empty2)));

    Assertions.assertEquals(singleton1,
      ParallelEvaluation.takeSmallest(List.of(singleton1, singleton2)));
    Assertions.assertEquals(singleton1,
      ParallelEvaluation.takeSmallest(List.of(singleton2, singleton1)));

    Assertions.assertEquals(singleton2,
      ParallelEvaluation.takeSmallest(List.of(singleton2)));
  }
}