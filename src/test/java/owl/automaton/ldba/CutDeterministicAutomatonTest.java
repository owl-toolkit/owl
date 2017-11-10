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

package owl.automaton.ldba;

import java.util.EnumSet;
import java.util.function.Function;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import org.junit.Before;
import org.junit.Test;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpoint.GeneralizedBreakpointState;

public class CutDeterministicAutomatonTest {
  private LimitDeterministicAutomaton<EquivalenceClass, ?, ?, ?> ldba;

  @Before
  public void setUp() throws Exception {
    EnumSet<Optimisation> optimisations = EnumSet.of(
      Optimisation.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES,
      Optimisation.EAGER_UNFOLD,
      Optimisation.FORCE_JUMPS, Optimisation.SUPPRESS_JUMPS,
      Optimisation.OPTIMISED_STATE_STRUCTURE);

    Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
      GeneralizedBreakpointState, GeneralizedBuchiAcceptance, GObligations>> function =
      LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(optimisations);
    ldba = function.apply(LtlParser.parse("a | F b | G c | G d"));
  }

  @Test
  public void test() {
    ldba.asCutDeterministicAutomaton().toHoa(
      new HOAIntermediateCheckValidity(new HOAConsumerNull()));
  }
}