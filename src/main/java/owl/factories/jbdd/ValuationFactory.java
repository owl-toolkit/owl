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

package owl.factories.jbdd;

import com.google.common.collect.ImmutableList;
import de.tum.in.jbdd.Bdd;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

final class ValuationFactory extends GcManagedFactory<ValuationSet> implements ValuationSetFactory {
  private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);
  private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);

  private final ImmutableList<String> alphabet;
  private final ValuationSet empty;
  private final ValuationSet universe;

  ValuationFactory(Bdd factory, List<String> alphabet) {
    super(factory);
    this.alphabet = ImmutableList.copyOf(alphabet);

    for (int i = 0; i < alphabet.size(); i++) {
      factory.createVariable();
    }

    universe = createValuationSet(factory.getTrueNode());
    empty = createValuationSet(factory.getFalseNode());
  }

  @Override
  public ValuationSet complement(ValuationSet set) {
    BddValuationSet castedSet = (BddValuationSet) set;
    return createValuationSet(factory.reference(factory.not(castedSet.bdd)));
  }

  @Override
  public ValuationSet union(Iterator<ValuationSet> sets) {
    int bdd = factory.getFalseNode();

    while (sets.hasNext()) {
      BddValuationSet set = (BddValuationSet) sets.next();
      bdd = factory.updateWith(factory.or(bdd, set.bdd), bdd);
    }

    return createValuationSet(bdd);
  }

  @Override
  public ValuationSet intersection(Iterator<ValuationSet> sets) {
    int bdd = factory.getTrueNode();

    while (sets.hasNext()) {
      BddValuationSet set = (BddValuationSet) sets.next();
      bdd = factory.updateWith(factory.and(bdd, set.bdd), bdd);
    }

    return createValuationSet(bdd);
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
  public ValuationSet of() {
    return empty;
  }

  private BooleanExpression<AtomLabel> createRepresentative(int bdd) {
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
    }
    if (neg.isFALSE()) {
      return pos;
    }

    return pos.or(neg);
  }

  @Override
  public ValuationSet universe() {
    return universe;
  }

  @Override
  public ValuationSet of(BitSet valuation, BitSet restrictedAlphabet) {
    return createValuationSet(createBdd(valuation, restrictedAlphabet));
  }

  @Override
  public ValuationSet of(BitSet valuation) {
    return createValuationSet(createBdd(valuation));
  }

  private ValuationSet createValuationSet(int bdd) {
    summonReaper();
    return canonicalize(bdd, new BddValuationSet(bdd));
  }

  @Override
  public List<String> alphabet() {
    return alphabet;
  }

  final class BddValuationSet implements ValuationSet {
    final int bdd;

    BddValuationSet(int bdd) {
      this.bdd = bdd;
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
    public BitSet any() {
      for (BitSet set : BitSets.powerSet(alphabetSize())) {
        if (contains(set)) {
          return set;
        }
      }

      throw new NoSuchElementException();
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
    public void forEach(Consumer<? super BitSet> action) {
      int variables = factory.numberOfVariables();

      factory.forEachMinimalSolution(bdd, (solution, solutionSupport) -> {
        solutionSupport.flip(0, variables);
        BitSets.powerSet(solutionSupport).forEach(nonRelevantValuation -> {
          nonRelevantValuation.or(solution);
          action.accept(nonRelevantValuation);
          nonRelevantValuation.and(solutionSupport);
        });
        solutionSupport.flip(0, variables);
      });
    }

    @SuppressWarnings("UseOfClone")
    @Override
    public void forEach(BitSet restriction, Consumer<? super BitSet> action) {
      // TODO Make this native to the factory?
      int variables = factory.numberOfVariables();

      BitSet restrictedVariables = (BitSet) restriction.clone();
      restrictedVariables.flip(0, variables);
      int restrict = factory.reference(factory.restrict(bdd, restrictedVariables, new BitSet(0)));

      factory.forEachMinimalSolution(restrict, (solution, solutionSupport) -> {
        assert !solution.intersects(restrictedVariables);
        solutionSupport.xor(restriction);
        BitSets.powerSet(solutionSupport).forEach(nonRelevantValuation -> {
          solution.or(nonRelevantValuation);
          action.accept(solution);
          solution.andNot(nonRelevantValuation);
        });
        solutionSupport.xor(restriction);
      });

      factory.dereference(restrict);
    }

    @Override
    public int hashCode() {
      return HashCommon.mix(bdd);
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
    public ValuationSetFactory getFactory() {
      return ValuationFactory.this;
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
      if (isEmpty()) {
        return "[]";
      }

      StringBuilder builder = new StringBuilder(factory.numberOfVariables() * 10 + 10);
      builder.append('[');
      forEach(bitSet -> builder.append(bitSet).append(", "));
      builder.setLength(builder.length() - 2);
      builder.append(']');
      return builder.toString();
    }
  }
}
