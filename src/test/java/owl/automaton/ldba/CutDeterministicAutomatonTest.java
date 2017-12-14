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
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import org.junit.Test;
import owl.ltl.EquivalenceClass;
import owl.ltl.parser.LtlParser;
import owl.run.TestEnvironment;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public class CutDeterministicAutomatonTest {
  @Test
  public void test() {
    EnumSet<Configuration> configuration = EnumSet.of(
      Configuration.EAGER_UNFOLD,
      Configuration.FORCE_JUMPS,
      Configuration.SUPPRESS_JUMPS,
      Configuration.OPTIMISED_STATE_STRUCTURE);

    LimitDeterministicAutomaton<EquivalenceClass, ?, ?, ?> ldba = LTL2LDBAFunction
      .createGeneralizedBreakpointLDBABuilder(TestEnvironment.INSTANCE,
        configuration).apply(LtlParser.parse("a | F b | G c | G d"));

    ldba.asCutDeterministicAutomaton().toHoa(new HOAIntermediateCheckValidity(
      new HOAConsumerNull()));
  }
}