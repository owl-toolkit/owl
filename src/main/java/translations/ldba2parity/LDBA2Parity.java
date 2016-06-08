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

package translations.ldba2parity;

import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.Formula;
import ltl.parser.LTLParser;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.LTL2LDBA;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import translations.ldba.LimitDeterministicAutomaton;

import java.io.StringReader;
import java.util.function.Function;

public class LDBA2Parity<I extends AutomatonState<I>, A extends AutomatonState<A>> implements Function<LimitDeterministicAutomaton<I, A, ? extends GeneralisedBuchiAcceptance, ?, ?>, ParityAutomaton> {

    @Override
    public ParityAutomaton apply(LimitDeterministicAutomaton<I, A, ? extends GeneralisedBuchiAcceptance, ?, ?> ldba) {
        ParityAutomaton parity = new ParityAutomaton(ldba);
        parity.generate();
        return parity;
    }

    public static void main(String... args) throws ltl.parser.ParseException, HOAConsumerException {
        if (args.length == 0) {
            args = new String[]{"F G a"};
        }

        LTL2LDBA translation = new LTL2LDBA();
        LDBA2Parity<InitialComponent.State, AcceptingComponent.State> translation2 = new LDBA2Parity();

        LTLParser parser = new LTLParser(new StringReader(args[0]));
        Formula formula = parser.parse();

        ParityAutomaton result = translation.andThen(translation2).apply(formula);
        result.toHOA(new HOAConsumerPrint(System.out), parser.map);
    }
}
