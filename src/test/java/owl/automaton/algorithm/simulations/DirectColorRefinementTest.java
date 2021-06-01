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

import static owl.automaton.algorithm.simulations.CommonAutomata.anotherRefinementAutomaton;
import static owl.automaton.algorithm.simulations.CommonAutomata.simpleColorRefinementAutomaton;

import org.junit.jupiter.api.Test;

public class DirectColorRefinementTest {
  @Test
  void simpleAutomatonTest() {
    var aut = simpleColorRefinementAutomaton();
    var otherAut = anotherRefinementAutomaton();

    var refinementRel = ColorRefinement.of(aut);
    var gameRel = new BuchiSimulation().directSimulation(aut, aut, 1);
    assert refinementRel.containsAll(gameRel);
    assert gameRel.containsAll(refinementRel);

    var otherRefinementRel = ColorRefinement.of(otherAut);
    var otherGameRel = new BuchiSimulation().directSimulation(otherAut, otherAut, 1);
    assert otherRefinementRel.containsAll(otherGameRel);
    assert otherGameRel.containsAll(otherRefinementRel);
  }
}
