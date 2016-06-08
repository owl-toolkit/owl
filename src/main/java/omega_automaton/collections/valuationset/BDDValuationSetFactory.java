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

package omega_automaton.collections.valuationset;

import com.google.common.collect.Sets;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import ltl.Collections3;
import ltl.*;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.IntStream;

public class BDDValuationSetFactory implements ValuationSetFactory {

    final int alphabetSize;
    final BDDFactory factory;

    public BDDValuationSetFactory(Formula formula) {
        this(AlphabetVisitor.extractAlphabet(formula));
    }

    public BDDValuationSetFactory(int alphabet) {
        alphabetSize = alphabet;
        factory = BDDFactory.init("jdd", (32 * alphabetSize) + 32, 1000);
        factory.setVarNum(Math.max(alphabetSize, 1));

        // Silence library, TODO: move to logging util class
        try {
            Method m = BDDValuationSetFactory.class.getDeclaredMethod("callback", int.class, Object.class);
            factory.registerGCCallback(this, m);
            factory.registerReorderCallback(this, m);
            factory.registerResizeCallback(this, m);
        } catch (SecurityException | NoSuchMethodException e) {
            System.err.println("Failed to silence BDD library: " + e);
        }
    }

    @Override
    public BDDValuationSet createEmptyValuationSet() {
        return new BDDValuationSet(factory.zero());
    }

    @Override
    public BDDValuationSet createUniverseValuationSet() {
        return new BDDValuationSet(factory.one());
    }

    @Override
    public BDDValuationSet createValuationSet(BitSet valuation) {
        return new BDDValuationSet(createBDD(valuation, IntStream.range(0, alphabetSize)));
    }

    @Override
    public BDDValuationSet createValuationSet(BitSet valuation, BitSet restrictedAlphabet) {
        return new BDDValuationSet(createBDD(valuation, restrictedAlphabet.stream()));
    }

    @Override
    public int getSize() {
        return alphabetSize;
    }

    public void callback(int x, Object stats) {

    }

    static Formula createRepresentative(BDD bdd) {
        if (bdd.isOne()) {
            return BooleanConstant.TRUE;
        }

        if (bdd.isZero()) {
            return BooleanConstant.FALSE;
        }

        int letter = bdd.level();
        Formula pos = createRepresentative(bdd.high());
        Formula neg = createRepresentative(bdd.low());
        return Disjunction.create(Conjunction.create(new Literal(letter), pos), Conjunction.create(new Literal(letter, true), neg));
    }

    BDD createBDD(int letter, boolean negate) {
        if (letter < 0 || letter > alphabetSize) {
            throw new IllegalArgumentException("The alphabet does not contain the following letter: " + letter);
        }

        if (negate) {
            return factory.nithVar(letter);
        }

        return factory.ithVar(letter);
    }

    BDD createBDD(BitSet set, IntStream base) {
        final BDD bdd = factory.one();
        base.forEach(letter -> bdd.andWith(createBDD(letter, !set.get(letter))));
        return bdd;
    }

    static boolean isSatAssignment(BDD valuations, BitSet valuation) {
        BDD current = valuations;

        while (!current.isOne() && !current.isZero()) {
            if (valuation.get(current.var())) {
                current = current.high();
            } else {
                current = current.low();
            }
        }

        return current.isOne();
    }

    public class BDDValuationSet extends AbstractSet<BitSet> implements ValuationSet {

        private BDD valuations;

        BDDValuationSet(BDD bdd) {
            valuations = bdd;
        }

        @Override
        public Formula toFormula() {
            return createRepresentative(valuations);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof BDDValuationSet) {
                BDD otherBDD = ((BDDValuationSet) o).valuations;
                return otherBDD.equals(valuations);
            }

            return false;
        }

        @Override
        public boolean isUniverse() {
            return valuations.isOne();
        }

        @Override
        public int hashCode() {
            return valuations.hashCode();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof BitSet)) {
                return false;
            }

            return isSatAssignment(valuations, (BitSet) o);
        }

        @Nonnull
        @Override
        public Iterator<BitSet> iterator() {
            return Collections3.powerSet(alphabetSize).stream().filter(this::contains).iterator();
        }

        @Override
        public int size() {
            return (int) Math.round(valuations.satCount());
        }

        @Override
        public boolean isEmpty() {
            return valuations.isZero();
        }

        @Override
        public boolean add(BitSet v) {
            BDDValuationSet vs = createValuationSet(v);
            return update(vs.valuations.or(valuations));
        }

        @Override
        public boolean addAll(@Nonnull Collection<? extends BitSet> c) {
            if (c instanceof BDDValuationSet) {
                BDD otherValuations = ((BDDValuationSet) c).valuations;
                BDD newValuations = valuations.or(otherValuations);
                return update(newValuations);
            }

            return super.addAll(c);
        }

        @Override
        public boolean retainAll(@Nonnull Collection<?> c) {
            if (c instanceof BDDValuationSet) {
                BDD otherValuations = ((BDDValuationSet) c).valuations;
                BDD newValuations = valuations.and(otherValuations);
                return update(newValuations);
            }

            return super.retainAll(c);
        }

        @Override
        public boolean removeAll(@Nonnull Collection<?> c) {
            if (c instanceof BDDValuationSet) {
                BDD otherValuations = ((BDDValuationSet) c).valuations;
                BDD newValuations = valuations.and(otherValuations.not());
                return update(newValuations);
            }

            return super.removeAll(c);
        }

        @Override
        public ValuationSet complement() {
            return new BDDValuationSet(valuations.not());
        }

        @Override
        public BDDValuationSet clone() {
            return new BDDValuationSet(valuations.id());
        }

        @Override
        public String toString() {
            return Sets.newHashSet(iterator()).toString();
        }

        private boolean update(BDD newValue) {
            if (valuations.equals(newValue)) {
                return false;
            }

            valuations = newValue;
            return true;
        }
    }
}
