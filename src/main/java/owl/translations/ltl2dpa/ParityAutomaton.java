/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.ltl2dpa;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.atomic.AtomicInteger;
import owl.automaton.AutomatonState;
import owl.automaton.LegacyAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.factories.Factories;

public abstract class ParityAutomaton<S extends AutomatonState<S>>
  extends LegacyAutomaton<S, ParityAcceptance> {

  // fb-contrib does not honour the restrictions made by the type parameter.
  @SuppressFBWarnings("ocp")
  ParityAutomaton(ParityAcceptance acceptance, Factories factories, AtomicInteger integer) {
    super(acceptance, factories, integer);
  }

  @SuppressFBWarnings("ocp")
  ParityAutomaton(LegacyAutomaton<S, ?> ba, ParityAcceptance acceptance) {
    super(ba, acceptance);
  }

  void complement() {
    complete();
    acceptance = acceptance.complement();
  }
}
