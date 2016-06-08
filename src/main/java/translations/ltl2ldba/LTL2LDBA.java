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

import com.google.common.collect.Sets;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.parser.LTLParser;
import translations.Optimisation;
import ltl.Collections3;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.collections.valuationset.BDDValuationSetFactory;
import omega_automaton.collections.valuationset.ValuationSetFactory;
import ltl.Formula;
import ltl.GOperator;
import ltl.SkeletonVisitor;
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
        ValuationSetFactory valuationSetFactory = new BDDValuationSetFactory(formula);
        EquivalenceClassFactory equivalenceClassFactory = new BDDEquivalenceClassFactory(formula);

        formula = Simplifier.simplify(formula, Simplifier.Strategy.MODAL_EXT);

        Set<Set<GOperator>> keys = optimisations.contains(Optimisation.SKELETON) ? formula.accept(SkeletonVisitor.getInstance(SkeletonVisitor.SkeletonApproximation.BOTH)) : Sets.powerSet(formula.gSubformulas());

        AcceptingComponent acceptingComponent = new AcceptingComponent(new Master(valuationSetFactory, optimisations), equivalenceClassFactory, valuationSetFactory, optimisations);
        InitialComponent initialComponent = null;

        if (optimisations.contains(Optimisation.IMPATIENT) && Collections3.isSingleton(keys) && ImpatientStateAnalysis.isImpatientFormula(formula)) {
            Set<GOperator> key = Collections3.getElement(keys);

            EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(Simplifier.simplify(formula.evaluate(key), Simplifier.Strategy.MODAL_EXT));
            acceptingComponent.jumpInitial(initialClazz, key);
            acceptingComponent.generate();
        } else {
            EquivalenceClass initialClazz = equivalenceClassFactory.createEquivalenceClass(formula);
            initialComponent = new InitialComponent(initialClazz, acceptingComponent, valuationSetFactory, optimisations);
            initialComponent.generate();
        }

        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> det =
                new LimitDeterministicAutomaton<>(initialComponent, acceptingComponent, optimisations);
        det.generate();
        return det;
    }

    public static void main(String... args) throws ltl.parser.ParseException, HOAConsumerException {
        LTL2LDBA translation = new LTL2LDBA();

        LTLParser parser = new LTLParser(new StringReader(args[0]));
        Formula formula = parser.parse();

        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> result = translation.apply(formula);
        result.toHOA(new HOAConsumerPrint(System.out), parser.map);
    }
}
