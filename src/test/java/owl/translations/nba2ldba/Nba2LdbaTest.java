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

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Iterables;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.HOAFParser;
import omega_automaton.StoredBuchiAutomaton;
import omega_automaton.output.HOAPrintable;
import org.junit.Test;
import owl.translations.Optimisation;

public class Nba2LdbaTest {

  private static final String INPUT = "HOA: v1\n" +
    "States: 2\n" +
    "Start: 0\n" +
    "acc-name: Buchi\n" +
    "Acceptance: 1 Inf(0)\n" +
    "AP: 1 \"a\"\n" +
    "--BODY--\n" +
    "State: 0 {0}\n" +
    " [0]   1 \n" +
    "State: 1 \n" +
    " [t]   0 \n" +
    " [!0]  1 \n" +
    "--END--";
  private static final BiMap<String, Integer> MAPPING = ImmutableBiMap.of("a", 0);

  @Test
  public void testApply() throws Exception {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    final Nba2Ldba translation = new Nba2Ldba(optimisations);

    StoredBuchiAutomaton.Builder builder = new StoredBuchiAutomaton.Builder();
    HOAFParser.parseHOA(new ByteArrayInputStream(INPUT.getBytes(StandardCharsets.UTF_8)), builder);
    final StoredBuchiAutomaton nba = Iterables.getOnlyElement(builder.getAutomata());

    nba.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    HOAPrintable result = translation.apply(nba);
    result.setAtomMapping(MAPPING.inverse());
    result.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
  }
}
