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

package owl.translations.nba2ldba;

import com.google.common.collect.ImmutableList;

import java.util.EnumSet;
import java.util.List;

import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.generated.ParseException;
import org.junit.Test;

import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.output.HoaPrintable;
import owl.translations.Optimisation;

public class NBA2LDBATest {

  private static final String INPUT = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "AP: 1 \"a\"\n"
    + "--BODY--\n"
    + "State: 0 {0}\n"
    + " [0]   1 \n"
    + "State: 1 \n"
    + " [t]   0 \n"
    + " [!0]  1 \n"
    + "--END--";
  
  private static final String INPUT2 = "HOA: v1\n"
      + "States: 2\n"
      + "Start: 0\n"
      + "acc-name: Buchi\n"
      + "Acceptance: 1 Inf(0)\n"
      + "AP: 1 \"a\"\n"
      + "--BODY--\n"
      + "State: 0 {0}\n"
      + " [0]   1 \n"
      + "State: 1 \n"
      + " [!0]  0 \n"
      + " [!0]  1 \n"
      + "--END--";
  
  private static final String INPUT3 =  "HOA: v1\n" 
      + "States: 3\n" 
      + "Start: 0\n" 
      + "acc-name: Buchi\n"
      + "Acceptance: 1 Inf(0)\n"
      + "properties: trans-acc trans-label\n" 
      + "AP: 2 \"a\" \"b\"\n" 
      + "--BODY--\n" 
      + "State: 1\n" 
      + "[0] 1 {0}\n" 
      + "[!0] 2 {0}\n" 
      + "State: 0\n" 
      + "[0] 1\n" 
      + "[t] 0\n" 
      + "[!0] 2\n" 
      + "State: 2\n" 
      + "[0 & 1] 1 {0}\n" 
      + "[!0 & 1] 2 {0}\n" 
      + "--END--" ; 
  
  private static final List<String> MAPPING = ImmutableList.of("a");

  @Test
  public void testApply() throws ParseException {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    NBA2LDBAFunction<HoaState> translation = new NBA2LDBAFunction<>(optimisations);

    Automaton<HoaState, BuchiAcceptance> automaton =
      AutomatonReader.readHoa(INPUT, BuchiAcceptance.class);

    automaton.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    HoaPrintable result = translation.apply(automaton);
    result.setVariables(MAPPING);
    result.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    result.toHoa(new HOAConsumerPrint(System.out));
  }
  
  @Test
  public void testApply2() throws ParseException {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    NBA2LDBAFunction<HoaState> translation = new NBA2LDBAFunction<>(optimisations);

    Automaton<HoaState, BuchiAcceptance> automaton =
      AutomatonReader.readHoa(INPUT2, BuchiAcceptance.class);

    automaton.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    HoaPrintable result = translation.apply(automaton);
    result.setVariables(MAPPING);
    result.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    result.toHoa(new HOAConsumerPrint(System.out));
  }
  
  @Test
  public void testApply3() throws ParseException {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    NBA2LDBAFunction<HoaState> translation = new NBA2LDBAFunction<>(optimisations);

    Automaton<HoaState, BuchiAcceptance> automaton =
      AutomatonReader.readHoa(INPUT3, BuchiAcceptance.class);

    automaton.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    HoaPrintable result = translation.apply(automaton);
    result.setVariables(MAPPING);
    result.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    result.toHoa(new HOAConsumerPrint(System.out));
  }
}
