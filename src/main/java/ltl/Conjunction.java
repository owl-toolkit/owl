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

package ltl;

import com.google.common.collect.ImmutableSet;
import ltl.visitors.BinaryVisitor;
import ltl.visitors.Visitor;
import ltl.visitors.VoidVisitor;

import java.util.*;
import java.util.stream.Stream;

public final class Conjunction extends PropositionalFormula {

    public Conjunction(ImmutableSet<Formula> conjuncts) { super(conjuncts);}

    public Conjunction(Collection<? extends Formula> conjuncts) {
        super(conjuncts);
    }

    public Conjunction(Formula... conjuncts) {
        super(conjuncts);
    }

    public Conjunction(Stream<? extends Formula> formulaStream) {
        super(formulaStream);
    }

    @Override
    public Formula not() {
        return new Disjunction(children.stream().map(Formula::not));
    }

    @Override
    public void accept(VoidVisitor v) {
        v.visit(this);
    }

    @Override
    public <R> R accept(Visitor<R> v) {
        return v.visit(this);
    }

    @Override
    public <A, B> A accept(BinaryVisitor<A, B> v, B f) {
        return v.visit(this, f);
    }

    @Override
    public Formula unfold() {
        return create(children.stream().map(Formula::unfold));
    }

    @Override
    public Formula temporalStep(BitSet valuation) {
        return create(children.stream().map(c -> c.temporalStep(valuation)));
    }

    @Override
    protected char getOperator() {
        return '&';
    }

    public static Formula create(Formula... formulaStream) {
        return create(Arrays.stream(formulaStream));
    }

    public static Formula create(Stream<? extends Formula> formulaStream) {
        Iterator<? extends Formula> iterator = formulaStream.iterator();
        ImmutableSet.Builder<Formula> builder = ImmutableSet.builder();

        while (iterator.hasNext()) {
            Formula child = iterator.next();

            if (child == BooleanConstant.FALSE) {
                return BooleanConstant.FALSE;
            }

            if (child == BooleanConstant.TRUE) {
                continue;
            }

            if (child instanceof Conjunction) {
                builder.addAll(((Conjunction) child).children);
            } else {
                builder.add(child);
            }
        }

        ImmutableSet<Formula> set = builder.build();

        if (set.isEmpty()) {
            return BooleanConstant.TRUE;
        }

        if (set.size() == 1) {
            return set.iterator().next();
        }

        return new Conjunction(set);
    }

    @Override
    protected int hashCodeOnce() {
        return Objects.hash(Conjunction.class, children);
    }
}
