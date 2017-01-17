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
import java.util.BitSet;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import omega_automaton.collections.Collections3;
import owl.bdd.Bdd;
import owl.bdd.BddFactory;

public class BDDValuationSetFactory implements ValuationSetFactory {

  private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);
  private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);
  private final Bdd factory;
  private final int vars[];

  public BDDValuationSetFactory(int alphabet) {
    vars = new int[alphabet];
    factory = BddFactory.buildBdd((1024 * alphabet * alphabet) + 256);

    for (int i = 0; i < alphabet; i++) {
      vars[i] = factory.createVariable();
    }
  }

  int createBDD(BitSet set, PrimitiveIterator.OfInt base) {
    int bdd = factory.getTrueNode();

    while (base.hasNext()) {
      int i = base.nextInt();

      // Variables are saturated.
      if (set.get(i)) {
        bdd = factory.updateWith(factory.and(bdd, vars[i]), bdd);
      } else {
        // This is fine, since "not vars[i]" is a saturated variable.
        bdd = factory.updateWith(factory.and(bdd, factory.not(vars[i])), bdd);
      }
    }

    return bdd;
  }

  int createBDD(BitSet set) {
    return createBDD(set, IntStream.range(0, vars.length).iterator());
  }

  @Override
  public ValuationSet createEmptyValuationSet() {
    return new BDDValuationSet(factory.getFalseNode());
  }

  BooleanExpression<AtomLabel> createRepresentative(int bdd) {
    if (bdd == factory.getFalseNode()) {
      return FALSE;
    }

    if (bdd == factory.getTrueNode()) {
      return TRUE;
    }

    BooleanExpression<AtomLabel> letter = new BooleanExpression<>(
      AtomLabel.createAPIndex(factory.getVariable(bdd)));
    BooleanExpression<AtomLabel> pos = createRepresentative(factory.getHigh(bdd));
    BooleanExpression<AtomLabel> neg = createRepresentative(factory.getLow(bdd));

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
    return new BDDValuationSet(factory.getTrueNode());
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
    private int bdd;

    BDDValuationSet(int bdd) {
      this.bdd = bdd;
    }

    @Override
    public void add(@Nonnull BitSet set) {
      int bddSet = createBDD(set);
      bdd = factory.consume(factory.or(bdd, bddSet), bdd, bddSet);
    }

    @Override
    public void addAll(@Nonnull ValuationSet other) {
      BDDValuationSet otherSet = (BDDValuationSet) other;
      bdd = factory.updateWith(factory.or(bdd, otherSet.bdd), bdd);
    }

    @Override
    public void addAllWith(@Nonnull ValuationSet other) {
      addAll(other);
      other.free();
    }

    @Override
    public ValuationSet complement() {
      return new BDDValuationSet(factory.reference(factory.not(bdd)));
    }

    @Override
    public boolean contains(BitSet valuation) {
      return factory.evaluate(bdd, valuation);
    }

    @Override
    public boolean containsAll(ValuationSet other) {
      BDDValuationSet otherSet = (BDDValuationSet) other;
      return factory.implies(otherSet.bdd, bdd);
    }

    @Override
    public BDDValuationSet copy() {
      return new BDDValuationSet(factory.reference(bdd));
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
      if (factory.isNodeRoot(bdd)) {
        factory.dereference(bdd);
        bdd = INVALID_BDD;
      }
    }

    @Override
    public int hashCode() {
      return bdd;
    }

    public ValuationSet intersect(ValuationSet other) {
      ValuationSet thisClone = this.copy();
      thisClone.retainAll(other);
      return thisClone;
    }

    public boolean intersects(ValuationSet other) {
      return !intersect(other).isEmpty();
    }

    @Override
    public boolean isEmpty() {
      return bdd == factory.getFalseNode();
    }

    @Override
    public boolean isUniverse() {
      return bdd == factory.getTrueNode();
    }

    @Nonnull
    public Iterator<BitSet> iterator() {
      return Collections3.powerSet(vars.length).stream().filter(this::contains).iterator();
    }

    @Override
    public void removeAll(@Nonnull ValuationSet other) {
      BDDValuationSet otherSet = (BDDValuationSet) other;
      int notOtherIndex = factory.reference(factory.not(otherSet.bdd));
      bdd = factory.updateWith(factory.and(bdd, notOtherIndex), bdd);
      factory.dereference(notOtherIndex);
    }

    @Override
    public void retainAll(@Nonnull ValuationSet other) {
      BDDValuationSet otherSet = (BDDValuationSet) other;
      bdd = factory.updateWith(factory.and(bdd, otherSet.bdd), bdd);
    }

    public int size() {
      return (int) Math.round(factory.countSatisfyingAssignments(bdd));
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
