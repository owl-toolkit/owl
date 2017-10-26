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

package owl.factories.jdd;

import com.google.common.collect.Sets;
import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.Iterator;
import javax.annotation.Nonnull;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class ValuationFactory implements ValuationSetFactory {
  private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);
  private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);
  private final Bdd factory;

  public ValuationFactory(int alphabet) {
    factory = BddFactory.buildBdd((1024 * alphabet * alphabet) + 256);

    for (int i = 0; i < alphabet; i++) {
      factory.createVariable();
    }
  }

  private int createBdd(BitSet set, BitSet base) {
    int bdd = factory.getTrueNode();
    for (int i = base.nextSetBit(0); i != -1; i = base.nextSetBit(i + 1)) {
      bdd = createBddUpdateHelper(set, i, bdd);
    }
    return bdd;
  }

  private int createBdd(BitSet set) {
    int bdd = factory.getTrueNode();
    for (int i = 0; i < factory.numberOfVariables(); i++) {
      bdd = createBddUpdateHelper(set, i, bdd);
    }
    return bdd;
  }

  private int createBddUpdateHelper(BitSet set, int var, int bdd) {
    if (set.get(var)) {
      // Variables are saturated.
      return factory.updateWith(factory.and(bdd, factory.getVariableNode(var)), bdd);
    } else {
      // This is fine, since "not vars[i]" is a saturated variable.
      return factory.updateWith(factory.and(bdd, factory.not(factory.getVariableNode(var))), bdd);
    }
  }

  @Override
  public ValuationSet createEmptyValuationSet() {
    return new BddValuationSet(factory.getFalseNode());
  }

  private BooleanExpression<AtomLabel> createRepresentative(int bdd) {
    if (bdd == factory.getFalseNode()) {
      return FALSE;
    }

    if (bdd == factory.getTrueNode()) {
      return TRUE;
    }

    if (bdd == BddValuationSet.INVALID_BDD) {
      throw new IllegalStateException("Valuationset already freed!");
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
    }
    if (neg.isFALSE()) {
      return pos;
    }

    return pos.or(neg);
  }

  @Override
  public ValuationSet createUniverseValuationSet() {
    return new BddValuationSet(factory.getTrueNode());
  }

  @Override
  public ValuationSet createValuationSet(BitSet valuation, BitSet restrictedAlphabet) {
    return new BddValuationSet(createBdd(valuation, restrictedAlphabet));
  }

  @Override
  public ValuationSet createValuationSet(BitSet valuation) {
    return new BddValuationSet(createBdd(valuation));
  }

  @Override
  public int getSize() {
    return factory.numberOfVariables();
  }

  private final class BddValuationSet implements ValuationSet {
    static final int INVALID_BDD = -1;
    private int bdd;

    BddValuationSet(int bdd) {
      this.bdd = bdd;
    }

    @Override
    public void add(BitSet valuation) {
      int bddSet = createBdd(valuation);
      bdd = factory.consume(factory.or(bdd, bddSet), bdd, bddSet);
    }

    @Override
    public void addAll(ValuationSet other) {
      assert other instanceof BddValuationSet;
      BddValuationSet otherSet = (BddValuationSet) other;
      bdd = factory.updateWith(factory.or(bdd, otherSet.bdd), bdd);
    }

    @Override
    public ValuationSet complement() {
      return new BddValuationSet(factory.reference(factory.not(bdd)));
    }

    @Override
    public boolean contains(BitSet valuation) {
      return factory.evaluate(bdd, valuation);
    }

    @Override
    public boolean containsAll(ValuationSet vs) {
      assert vs instanceof BddValuationSet;
      BddValuationSet otherSet = (BddValuationSet) vs;
      return factory.implies(otherSet.bdd, bdd);
    }

    @Override
    public BddValuationSet copy() {
      return new BddValuationSet(factory.reference(bdd));
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
      //if (factory.isNodeRoot(bdd)) {
      //  factory.dereference(bdd);
      //  bdd = INVALID_BDD;
      //}
    }

    @Override
    public int hashCode() {
      return bdd;
    }

    @Override
    public ValuationSet intersect(ValuationSet v2) {
      ValuationSet thisClone = this.copy();
      thisClone.retainAll(v2);
      return thisClone;
    }

    @Override
    public boolean intersects(ValuationSet value) {
      return !intersect(value).isEmpty();
    }

    @Override
    public boolean isEmpty() {
      return bdd == factory.getFalseNode();
    }

    @Override
    public boolean isUniverse() {
      return bdd == factory.getTrueNode();
    }

    @Override
    @Nonnull
    public Iterator<BitSet> iterator() {
      return BitSets.powerSet(factory.numberOfVariables()).stream()
        .filter(this::contains).iterator();
    }

    @Override
    public void removeAll(ValuationSet other) {
      assert other instanceof BddValuationSet;

      BddValuationSet otherSet = (BddValuationSet) other;
      int notOtherIndex = factory.reference(factory.not(otherSet.bdd));
      bdd = factory.updateWith(factory.and(bdd, notOtherIndex), bdd);
      factory.dereference(notOtherIndex);
    }

    @Override
    public void retainAll(ValuationSet other) {
      assert other instanceof BddValuationSet;

      BddValuationSet otherSet = (BddValuationSet) other;
      bdd = factory.updateWith(factory.and(bdd, otherSet.bdd), bdd);
    }

    @Override
    public int size() {
      return Math.toIntExact(Math.round(factory.countSatisfyingAssignments(bdd)));
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
