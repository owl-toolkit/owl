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

public class VSFactory implements ValuationSetFactory {

  private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);
  private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);
  private final long[] vars;

  public VSFactory(int alphabet) {
    vars = new long[alphabet];

    for (int i = 0; i < alphabet; i++) {
      vars[i] = JSylvan.ithvar(i);
    }
  }

  long createBDD(BitSet set, PrimitiveIterator.OfInt base) {
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

  long createBDD(BitSet set) {
    return createBDD(set, IntStream.range(0, vars.length).iterator());
  }

  @Override
  public ValuationSet createEmptyValuationSet() {
    return new BDDValuationSet(JSylvan.getFalse());
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
    } else if (neg.isFALSE()) {
      return pos;
    }

    return pos.or(neg);
  }

  @Override
  public ValuationSet createUniverseValuationSet() {
    return new BDDValuationSet(JSylvan.getTrue());
  }

  @Override
  public ValuationSet createValuationSet(BitSet valuation, BitSet restrictedAlphabet) {
    return new BDDValuationSet(createBDD(valuation, restrictedAlphabet.stream().iterator()));
  }

  @Override
  public ValuationSet createValuationSet(BitSet valuation) {
    return new BDDValuationSet(createBDD(valuation));
  }

  @Override
  public int getSize() {
    return vars.length;
  }

  private class BDDValuationSet implements ValuationSet {

    private static final int INVALID_BDD = -1;
    private long bdd;

    BDDValuationSet(long bdd) {
      this.bdd = bdd;
    }

    @Override
    public void add(@Nonnull BitSet set) {
      bdd = JSylvan.orConsuming(bdd, createBDD(set));
    }

    @Override
    public void addAll(@Nonnull ValuationSet other) {
      BDDValuationSet otherSet = (BDDValuationSet) other;
      bdd = JSylvan.orConsuming(bdd, JSylvan.ref(otherSet.bdd));
    }

    @Override
    public void addAllWith(@Nonnull ValuationSet other) {
      addAll(other);
      other.free();
    }

    @Override
    public ValuationSet complement() {
      return new BDDValuationSet(JSylvan.ref(JSylvan.makeNot(bdd)));
    }

    @Override
    public boolean contains(BitSet valuation) {
      return JSylvan.evaluate(bdd, valuation);
    }

    @Override
    public boolean containsAll(ValuationSet other) {
      assert other instanceof BDDValuationSet;

      BDDValuationSet otherSet = (BDDValuationSet) other;
      return JSylvan.implies(otherSet.bdd, bdd);
    }

    @Override
    public BDDValuationSet copy() {
      return new BDDValuationSet(JSylvan.ref(bdd));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BDDValuationSet bitSets = (BDDValuationSet) o;
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
      ValuationSet thisClone = this.copy();
      thisClone.retainAll(other);
      return thisClone;
    }

    @Override
    public boolean intersects(ValuationSet other) {
      long bdd = JSylvan.and(this.bdd, ((BDDValuationSet) other).bdd);
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

    @Nonnull
    public Iterator<BitSet> iterator() {
      return BitSets.powerSet(vars.length).stream().filter(this::contains).iterator();
    }

    @Override
    public void removeAll(@Nonnull ValuationSet other) {
      assert other instanceof BDDValuationSet;

      BDDValuationSet otherSet = (BDDValuationSet) other;
      long notOtherIndex = JSylvan.ref(JSylvan.makeNot(otherSet.bdd));
      bdd = JSylvan.andConsuming(bdd, notOtherIndex);
    }

    @Override
    public void retainAll(@Nonnull ValuationSet other) {
      assert other instanceof BDDValuationSet;

      BDDValuationSet otherSet = (BDDValuationSet) other;
      bdd = JSylvan.andConsuming(bdd, JSylvan.ref(otherSet.bdd));
    }

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
