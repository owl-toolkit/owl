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
import com.google.common.collect.Iterables;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.generated.ParseException;
import org.junit.Test;
import owl.automaton.StoredBuchiAutomaton;
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
  private static final List<String> MAPPING = ImmutableList.of("a");

  @Test
  public void testApply() throws ParseException {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    final NBA2LDBA translation = new NBA2LDBA(optimisations);

    StoredBuchiAutomaton.Builder builder = new StoredBuchiAutomaton.Builder();
    HOAFParser.parseHOA(new ByteArrayInputStream(INPUT.getBytes(StandardCharsets.UTF_8)), builder);
    final StoredBuchiAutomaton nba = Iterables.getOnlyElement(builder.getAutomata());

    nba.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    HoaPrintable result = translation.apply(nba);
    result.setVariables(MAPPING);
    result.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
  }
}
