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

package translations.ltl2ldba;

import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.visitors.AlphabetVisitor;
import ltl.parser.ParseException;
import ltl.parser.Parser;
import translations.Optimisation;
import omega_automaton.collections.Collections3;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.valuationset.BDDValuationSetFactory;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.Formula;
import ltl.GOperator;
import ltl.equivalence.BDDEquivalenceClassFactory;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.simplifier.Simplifier;
import translations.ldba.LimitDeterministicAutomaton;

import java.io.StringReader;
import java.util.*;
import java.util.function.Function;

public class LTL2LDBA implements Function<Formula, LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent>> {

    private final EnumSet<Optimisation> optimisations;

    public LTL2LDBA() {
        this(EnumSet.allOf(Optimisation.class));
    }

    public LTL2LDBA(EnumSet<Optimisation> optimisations) {
        this.optimisations = optimisations;
    }

    @Override
    public LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> apply(Formula formula) {
        formula = Simplifier.simplify(formula, Simplifier.Strategy.MODAL_EXT);

        ValuationSetFactory valuationSetFactory = new BDDValuationSetFactory(AlphabetVisitor.extractAlphabet(formula));
        EquivalenceClassFactory equivalenceClassFactory = new BDDEquivalenceClassFactory(formula);

        Collection<Set<GOperator>> keys = GMonitorSelector.selectMonitors(optimisations.contains(Optimisation.MINIMAL_GSETS) ? GMonitorSelector.Strategy.MIN_DNF : GMonitorSelector.Strategy.ALL, formula, equivalenceClassFactory);

        AcceptingComponent acceptingComponent = new AcceptingComponent(equivalenceClassFactory, valuationSetFactory, optimisations);
        InitialComponent initialComponent = null;

        EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(formula);

        if (initialClazz.isFalse() || optimisations.contains(Optimisation.FORCE_JUMPS) && Collections3.isSingleton(keys) && StateAnalysis.isJumpNecessary(initialClazz)) {
            acceptingComponent.jumpInitial(initialClazz, Collections3.isSingleton(keys) ? Collections3.getElement(keys) : Collections.emptySet());
        } else {
            initialComponent = new InitialComponent(initialClazz, acceptingComponent, valuationSetFactory, optimisations, equivalenceClassFactory);
        }

        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> det
                = new LimitDeterministicAutomaton<>(initialComponent, acceptingComponent, optimisations);
        det.generate();
        return det;
    }

    public static void main(String... args) throws ParseException, HOAConsumerException {
        LTL2LDBA translation = new LTL2LDBA();

        Parser parser = new Parser(new StringReader(args[0]));
        Formula formula = parser.formula();

        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> result = translation.apply(formula);
        result.toHOA(new HOAConsumerPrint(System.out), parser.map);
    }
}
