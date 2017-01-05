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

import ltl.Formula;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import translations.Optimisation;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.EnumSet;

public class EquivalenceClassStateFactory {

    private final EquivalenceClassFactory factory;
    private final boolean eagerUnfold;
    private final boolean removeRedundantObligations;

    EquivalenceClassStateFactory(EquivalenceClassFactory factory, EnumSet<Optimisation> optimisations) {
        this.factory = factory;
        this.eagerUnfold = optimisations.contains(Optimisation.EAGER_UNFOLD);
        this.removeRedundantObligations = optimisations.contains(Optimisation.REMOVE_REDUNDANT_OBLIGATIONS);
    }

    public EquivalenceClass getInitial(Formula formula) {
        EquivalenceClass clazz = factory.createEquivalenceClass(formula);
        EquivalenceClass initial = getInitial(clazz);
        clazz.free();
        return initial;
    }

    public EquivalenceClass getInitial(EquivalenceClass clazz) {
        return getInitial(clazz, null);
    }

    public EquivalenceClass getInitial(EquivalenceClass clazz, @Nullable EquivalenceClass environment) {
        EquivalenceClass initial;

        if (eagerUnfold) {
            initial = clazz.unfold();
        } else {
            initial = clazz.duplicate();
        }

        if (removeRedundantObligations && environment != null && environment.implies(initial)) {
            initial.free();
            initial = factory.getTrue();
        }

        return initial;
    }

    public EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation) {
        return getSuccessor(clazz, valuation, null);
    }

    public EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation, @Nullable EquivalenceClass environment) {
        EquivalenceClass successor;

        if (eagerUnfold) {
            successor = clazz.temporalStepUnfold(valuation);
        } else {
            successor = clazz.unfoldTemporalStep(valuation);
        }

        if (removeRedundantObligations && environment != null && environment.implies(successor)) {
            successor.free();
            successor = factory.getTrue();
        }

        return successor;
    }

    @Nullable
    public EquivalenceClass[] getSuccessors(EquivalenceClass[] clazz, BitSet valuation, @Nullable EquivalenceClass environment) {
        EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

        for (int i = clazz.length - 1; i >= 0; i--) {
            successors[i] = getSuccessor(clazz[i], valuation, environment);

            if (successors[i].isFalse()) {
                EquivalenceClass.free(successors);
                return null;
            }
        }

        return successors;
    }

    public BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
        if (eagerUnfold) {
            return clazz.getAtoms();
        } else {
            EquivalenceClass unfold = clazz.unfold();
            BitSet atoms = unfold.getAtoms();
            unfold.free();
            return atoms;
        }
    }
}
