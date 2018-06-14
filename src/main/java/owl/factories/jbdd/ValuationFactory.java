/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
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

import de.tum.in.jbdd.Bdd;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

final class ValuationFactory extends GcManagedFactory<ValuationFactory.BddValuationSet>
  implements ValuationSetFactory {
  private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);
  private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);
  private static final BitSet EMPTY = new BitSet(0);

  private final List<String> alphabet;
  private final BddValuationSet empty;
  private final BddValuationSet universe;

  ValuationFactory(Bdd factory, List<String> alphabet) {
    super(factory);
    this.alphabet = List.copyOf(alphabet);

    factory.createVariables(this.alphabet.size());
    assert factory.numberOfVariables() == this.alphabet.size();

    universe = create(factory.getTrueNode());
    empty = create(factory.getFalseNode());
  }

  @Override
  public int alphabetSize() {
    return alphabet.size();
  }

  @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
  @Override
  public List<String> alphabet() {
    return alphabet;
  }


  @Override
  public ValuationSet empty() {
    return empty;
  }

  @Override
  public ValuationSet of(BitSet valuation, BitSet restrictedAlphabet) {
    return create(createBdd(valuation, restrictedAlphabet));
  }

  @Override
  public ValuationSet of(BitSet valuation) {
    return create(createBdd(valuation));
  }

  @Override
  public ValuationSet universe() {
    return universe;
  }

  @Override
  public ValuationSet complement(ValuationSet set) {
    return create(factory.not(getBdd(set)));
  }


  @Override
  public BitSet any(ValuationSet set) {
    return factory.getSatisfyingAssignment(getBdd(set));
  }

  @Override
  public boolean contains(ValuationSet set, BitSet valuation) {
    return factory.evaluate(getBdd(set), valuation);
  }

  @Override
  public boolean contains(ValuationSet set, ValuationSet other) {
    return factory.implies(getBdd(set), getBdd(other));
  }

  @Override
  public void forEach(ValuationSet set, Consumer<BitSet> action) {
    int variables = factory.numberOfVariables();

    factory.forEachMinimalSolution(getBdd(set), (solution, solutionSupport) -> {
      solutionSupport.flip(0, variables);
      BitSets.powerSet(solutionSupport).forEach(nonRelevantValuation -> {
        nonRelevantValuation.or(solution);
        action.accept(nonRelevantValuation);
        nonRelevantValuation.and(solutionSupport);
      });
      solutionSupport.flip(0, variables);
    });
  }

  @Override
  public void forEach(ValuationSet set, BitSet restriction, Consumer<BitSet> action) {
    // TODO Make this native to the factory?
    int variables = factory.numberOfVariables();

    BitSet restrictedVariables = BitSets.copyOf(restriction);
    restrictedVariables.flip(0, variables);

    int restrict = factory.restrict(getBdd(set), restrictedVariables, EMPTY);
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
  }

  @Override
  public boolean intersects(ValuationSet set, ValuationSet other) {
    return !factory.implies(getBdd(set), factory.not(getBdd(other)));
  }


  @Override
  public ValuationSet intersection(ValuationSet set1, ValuationSet set2) {
    return create(factory.and(getBdd(set1), getBdd(set2)));
  }

  @Override
  public ValuationSet intersection(Iterator<ValuationSet> sets) {
    int bdd = factory.getTrueNode();

    while (sets.hasNext()) {
      bdd = factory.and(bdd, getBdd(sets.next()));
    }

    return create(bdd);
  }


  @Override
  public ValuationSet union(ValuationSet set1, ValuationSet set2) {
    return create(factory.or(getBdd(set1), getBdd(set2)));
  }

  @Override
  public ValuationSet union(Iterator<ValuationSet> sets) {
    int bdd = factory.getFalseNode();

    while (sets.hasNext()) {
      bdd = factory.or(bdd, getBdd(sets.next()));
    }

    return create(bdd);
  }


  @Override
  public ValuationSet minus(ValuationSet set1, ValuationSet set2) {
    return create(factory.notAnd(getBdd(set1), getBdd(set2)));
  }


  @Override
  public BooleanExpression<AtomLabel> toExpression(ValuationSet set) {
    return toExpression(getBdd(set));
  }

  private BooleanExpression<AtomLabel> toExpression(int bdd) {
    if (bdd == factory.getFalseNode()) {
      return FALSE;
    }

    if (bdd == factory.getTrueNode()) {
      return TRUE;
    }

    BooleanExpression<AtomLabel> letter = new BooleanExpression<>(
      AtomLabel.createAPIndex(factory.getVariable(bdd)));
    BooleanExpression<AtomLabel> pos = toExpression(factory.getHigh(bdd));
    BooleanExpression<AtomLabel> neg = toExpression(factory.getLow(bdd));

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


  private BddValuationSet create(int bdd) {
    return canonicalize(bdd, new BddValuationSet(this, bdd));
  }

  private int createBdd(BitSet set, BitSet base) {
    assert base.length() <= alphabet.size();
    int bdd = factory.getTrueNode();

    for (int i = base.nextSetBit(0); i != -1; i = base.nextSetBit(i + 1)) {
      bdd = createBddUpdateHelper(set, i, bdd);
    }

    return bdd;
  }

  private int createBdd(BitSet set) {
    int bdd = factory.getTrueNode();

    for (int i = 0; i < alphabet.size(); i++) {
      bdd = createBddUpdateHelper(set, i, bdd);
    }

    return bdd;
  }

  private int createBddUpdateHelper(BitSet set, int var, int bdd) {
    int variableNode = factory.getVariableNode(var);
    assert factory.isVariable(variableNode);
    return factory.and(bdd, set.get(var) ? variableNode : factory.not(variableNode));
  }

  private int getBdd(ValuationSet vs) {
    assert this.equals(vs.getFactory());
    int bdd = ((BddValuationSet) vs).bdd;
    assert factory.getReferenceCount(bdd) > 0 || factory.getReferenceCount(bdd) == -1;
    return bdd;
  }

  static final class BddValuationSet extends ValuationSet {
    final int bdd;

    BddValuationSet(ValuationFactory factory, int bdd) {
      super(factory);
      this.bdd = bdd;
    }

    @Override
    public int hashCode() {
      return HashCommon.mix(bdd);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || !getClass().equals(o.getClass())) {
        return false;
      }

      BddValuationSet other = (BddValuationSet) o;
      assert getFactory().equals(other.getFactory());
      return bdd == other.bdd;
    }
  }
}
