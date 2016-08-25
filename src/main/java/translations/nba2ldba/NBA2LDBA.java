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

package translations.nba2ldba;

import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.generated.ParseException;
import omega_automaton.StoredBuchiAutomaton;
import omega_automaton.acceptance.BuchiAcceptance;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;

import java.util.EnumSet;
import java.util.function.Function;

public class NBA2LDBA implements Function<StoredBuchiAutomaton, LimitDeterministicAutomaton<YCInit.State, YCAcc.State, BuchiAcceptance, YCInit, YCAcc>> {

    private final EnumSet<Optimisation> optimisations;

    public NBA2LDBA() {
        this(EnumSet.allOf(Optimisation.class));
    }

    public NBA2LDBA(EnumSet<Optimisation> optimisations) {
        this.optimisations = optimisations;
    }

    @Override
    public LimitDeterministicAutomaton<YCInit.State, YCAcc.State, BuchiAcceptance, YCInit, YCAcc> apply(StoredBuchiAutomaton nba) {
        LimitDeterministicAutomaton<YCInit.State, YCAcc.State, BuchiAcceptance, YCInit, YCAcc> ldba;

        // Short-cut for translation
        if (nba.isDeterministic()) {
            ldba = new LimitDeterministicAutomaton<>(null, new YCAcc(nba), optimisations);
        } else {
            YCAcc acceptingComponent = new YCAcc(nba);
            ldba = new LimitDeterministicAutomaton<>(new YCInit(nba, acceptingComponent), acceptingComponent, optimisations);
        }

        ldba.generate();
        return ldba;
    }

    public static void main(String... args) throws ParseException {
        NBA2LDBA translation = new NBA2LDBA();

        StoredBuchiAutomaton.Builder builder = new StoredBuchiAutomaton.Builder();
        HOAFParser.parseHOA(System.in, builder);

        for (StoredBuchiAutomaton nba : builder.getAutomata()) {
            LimitDeterministicAutomaton<YCInit.State, YCAcc.State, BuchiAcceptance, YCInit, YCAcc> result = translation.apply(nba);
            result.toHOA(new HOAConsumerPrint(System.out), null);
        }
    }
}
