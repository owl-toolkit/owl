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

package owl.factories.sylvan;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jsylvan.JSylvan;
import owl.collections.BitSets;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public class ValuationFactory implements ValuationSetFactory {
  private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);
  private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);
  private final long[] vars;

  public ValuationFactory(int alphabet) {
    vars = new long[alphabet];

    for (int i = 0; i < alphabet; i++) {
      vars[i] = JSylvan.ithvar(i);
    }
  }

  private long createBdd(BitSet set, PrimitiveIterator.OfInt base) {
    long bdd = JSylvan.getTrue();

    while (base.hasNext()) {
      int i = base.nextInt();

      // Variables are saturated.
      if (set.get(i)) {
        bdd = JSylvan.andConsuming(bdd, vars[i]);
      } else {
        // This is fine, since "not vars[i]" is a saturated variable.
        bdd = JSylvan.andConsuming(bdd, JSylvan.makeNot(vars[i]));
      }
    }

    return bdd;
  }

  private long createBdd(BitSet set) {
    return createBdd(set, IntStream.range(0, vars.length).iterator());
  }

  @Override
  public ValuationSet createEmptyValuationSet() {
    return new BddValuationSet(JSylvan.getFalse());
  }

  BooleanExpression<AtomLabel> createRepresentative(long bdd) {
    if (bdd == JSylvan.getFalse()) {
      return FALSE;
    }

    if (bdd == JSylvan.getTrue()) {
      return TRUE;
    }

    BooleanExpression<AtomLabel> letter = new BooleanExpression<>(
      AtomLabel.createAPIndex(JSylvan.getVar(bdd)));
    BooleanExpression<AtomLabel> pos = createRepresentative(JSylvan.getThen(bdd));
    BooleanExpression<AtomLabel> neg = createRepresentative(JSylvan.getElse(bdd));

    if (pos.isTRUE()) {
      pos = letter;
    } else if (!pos.isFALSE()) {
      pos = letter.and(pos);
    }

    if (neg.isTRUE()) {
      neg = letter.not();
    } else if (!neg.isFALSE()) {
      neg = letter.not().and(neg);
    }

    if (pos.isFALSE()) {
      return neg;
    }
    if (neg.isFALSE()) {
      return pos;
    }

    return pos.or(neg);
  }

  @Override
  public ValuationSet createUniverseValuationSet() {
    return new BddValuationSet(JSylvan.getTrue());
  }

  @Override
  public ValuationSet createValuationSet(BitSet valuation, BitSet restrictedAlphabet) {
    return new BddValuationSet(createBdd(valuation, restrictedAlphabet.stream().iterator()));
  }

  @Override
  public ValuationSet createValuationSet(BitSet valuation) {
    return new BddValuationSet(createBdd(valuation));
  }

  @Override
  public int getSize() {
    return vars.length;
  }

  private class BddValuationSet implements ValuationSet {

    private static final int INVALID_BDD = -1;
    private long bdd;

    BddValuationSet(long bdd) {
      this.bdd = bdd;
    }

    @Override
    public void add(BitSet set) {
      bdd = JSylvan.orConsuming(bdd, createBdd(set));
    }

    @Override
    public void addAll(ValuationSet other) {
      assert other instanceof BddValuationSet;
      BddValuationSet that = (BddValuationSet) other;
      bdd = JSylvan.orConsuming(bdd, JSylvan.ref(that.bdd));
    }

    @Override
    public ValuationSet complement() {
      return new BddValuationSet(JSylvan.ref(JSylvan.makeNot(bdd)));
    }

    @Override
    public boolean contains(BitSet valuation) {
      return JSylvan.evaluate(bdd, valuation);
    }

    @Override
    public boolean containsAll(ValuationSet other) {
      assert other instanceof BddValuationSet;

      BddValuationSet otherSet = (BddValuationSet) other;
      return JSylvan.implies(otherSet.bdd, bdd);
    }

    @Override
    public BddValuationSet copy() {
      return new BddValuationSet(JSylvan.ref(bdd));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BddValuationSet bitSets = (BddValuationSet) o;
      return bdd == bitSets.bdd;
    }

    @Override
    public void free() {
      if (JSylvan.isVariableOrNegated(bdd)) {
        JSylvan.deref(bdd);
        bdd = INVALID_BDD;
      }
    }

    @Override
    public int hashCode() {
      return Long.hashCode(bdd);
    }

    @Override
    public ValuationSet intersect(ValuationSet other) {
      ValuationSet clone = this.copy();
      clone.retainAll(other);
      return clone;
    }

    @Override
    public boolean intersects(ValuationSet other) {
      assert other instanceof BddValuationSet;
      long bdd = JSylvan.and(this.bdd, ((BddValuationSet) other).bdd);
      boolean result = bdd != JSylvan.getFalse();
      JSylvan.deref(bdd);
      return result;
    }

    @Override
    public boolean isEmpty() {
      return bdd == JSylvan.getFalse();
    }

    @Override
    public boolean isUniverse() {
      return bdd == JSylvan.getTrue();
    }

    @Override
    @Nonnull
    public Iterator<BitSet> iterator() {
      return BitSets.powerSet(vars.length).stream().filter(this::contains).iterator();
    }

    @Override
    public void removeAll(ValuationSet other) {
      assert other instanceof BddValuationSet;

      BddValuationSet otherSet = (BddValuationSet) other;
      long notOtherIndex = JSylvan.ref(JSylvan.makeNot(otherSet.bdd));
      bdd = JSylvan.andConsuming(bdd, notOtherIndex);
    }

    @Override
    public void retainAll(ValuationSet other) {
      assert other instanceof BddValuationSet;

      BddValuationSet otherSet = (BddValuationSet) other;
      bdd = JSylvan.andConsuming(bdd, JSylvan.ref(otherSet.bdd));
    }

    @Override
    public int size() {
      return Iterators.size(iterator());
    }

    @Override
    public BooleanExpression<AtomLabel> toExpression() {
      return createRepresentative(bdd);
    }

    @Override
    public String toString() {
      return Sets.newHashSet(iterator()).toString();
    }
  }
}
