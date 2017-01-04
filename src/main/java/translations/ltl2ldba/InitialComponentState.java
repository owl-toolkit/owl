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

import ltl.equivalence.EquivalenceClass;
import omega_automaton.AutomatonState;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Objects;
import java.util.Set;

public class InitialComponentState implements AutomatonState<InitialComponentState> {

    private final InitialComponent<?, ?> parent;
    private final EquivalenceClass clazz;
    // TODO: Move this map to parent.
    private Set<?> jumps;

    public InitialComponentState(InitialComponent<?, ?> parent, EquivalenceClass clazz) {
        this.parent = parent;
        this.clazz = clazz;
    }

    @Nullable
    @Override
    public Edge<InitialComponentState> getSuccessor(@Nonnull BitSet valuation) {
        EquivalenceClass successorClass;

        if (parent.eager) {
            successorClass = clazz.temporalStepUnfold(valuation);
        } else {
            successorClass = clazz.unfoldTemporalStep(valuation);
        }

        // TODO: Move this map to parent.
        if (jumps == null) {
            jumps = parent.selector.select(clazz);
        }

        // Suppress edge, if successor is a non-accepting state
        if (successorClass.isFalse() || !jumps.contains(null)) {
            return null;
        }

        InitialComponentState successor = new InitialComponentState(parent, successorClass);
        return successorClass.isTrue() ? Edges.create(successor, parent.ACCEPT) : Edges.create(successor);
    }

    @Nonnull
    @Override
    public BitSet getSensitiveAlphabet() {
        if (parent.eager) {
            return clazz.getAtoms();
        } else {
            EquivalenceClass unfold = clazz.unfold();
            BitSet atoms = unfold.getAtoms();
            unfold.free();
            return atoms;
        }
    }

    @Override
    public String toString() {
        return clazz.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InitialComponentState that = (InitialComponentState) o;
        return Objects.equals(clazz, that.clazz);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    public EquivalenceClass getClazz() {
        return clazz;
    }

    public void free() {
        clazz.free();
    }
}
