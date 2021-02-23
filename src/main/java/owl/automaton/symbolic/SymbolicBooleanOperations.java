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

package owl.automaton.symbolic;

import java.util.List;
import owl.automaton.Automaton;

/**
 * Boolean operations on symbolic automata.
 *
 * <p>See for an equivalent on explicit automata, see {@link owl.automaton.BooleanOperations}.
 */
@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public final class SymbolicBooleanOperations {

  private SymbolicBooleanOperations() {}

  public static SymbolicAutomaton<?>
    intersection(SymbolicAutomaton<?> automaton1, SymbolicAutomaton<?> automaton2) {

    throw new RuntimeException("not yet implemented for: " + automaton1 + ", " + automaton2);
  }

  public static <S> Automaton<List<S>, ?>
    intersection(List<? extends Automaton<S, ?>> automata) {

    throw new RuntimeException("not yet implemented for: " + automata);
  }

  public static SymbolicAutomaton<?>
    deterministicUnion(SymbolicAutomaton<?> automaton1, SymbolicAutomaton<?> automaton2) {

    throw new RuntimeException("not yet implemented for: " + automaton1 + ", " + automaton2);
  }

  public static <S> Automaton<List<S>, ?>
    deterministicUnion(List<? extends Automaton<S, ?>> automata) {

    throw new RuntimeException("not yet implemented for: " + automata);
  }
}
