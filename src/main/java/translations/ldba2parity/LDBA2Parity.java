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
import ltl.parser.Parser;
import omega_automaton.Automaton;
import translations.Optimisation;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.LTL2LDBA;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import translations.ldba.LimitDeterministicAutomaton;

import java.io.StringReader;
import java.util.EnumSet;
import java.util.function.Function;

public class LDBA2Parity<I extends AutomatonState<I>, A extends AutomatonState<A>> implements Function<LimitDeterministicAutomaton<I, A, ? extends GeneralisedBuchiAcceptance, ?, ?>, Automaton<?, ?>> {

    /* Consume Input -> RUST? */
    @Override
    public Automaton<?, ?> apply(LimitDeterministicAutomaton<I, A, ? extends GeneralisedBuchiAcceptance, ?, ?> ldba) {
        if (ldba.isDeterministic()) {
            return ldba.getAcceptingComponent();
        }

        ParityAutomaton parity = new ParityAutomaton((LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent>) ldba);
        parity.generate();
        return parity;
    }

    public static void main(String... args) throws ltl.parser.ParseException, HOAConsumerException {
        if (args.length == 0) {
            args = new String[]{"G F a"};
        }

        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        optimisations.remove(Optimisation.STATE_LABEL_ANALYSIS);

        LTL2LDBA translation = new LTL2LDBA(optimisations);
        LDBA2Parity<InitialComponent.State, AcceptingComponent.State> translation2 = new LDBA2Parity();

        Parser parser = new Parser(new StringReader(args[0]));
        Formula formula = parser.formula();

        Automaton<?, ?> result = translation.andThen(translation2).apply(formula);
        result.toHOA(new HOAConsumerPrint(System.out), parser.map);
    }
}
