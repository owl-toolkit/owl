/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import static java.util.stream.Collectors.toUnmodifiableSet;

import de.tum.in.jbdd.Bdd;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
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
  public List<String> alphabet() {
    return alphabet;
  }


  @Override
  public ValuationSet empty() {
    return empty;
  }

  @Override
  public ValuationSet of(int literal) {
    return create(factory.getVariableNode(literal));
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
  public void forEach(ValuationSet set, Consumer<? super BitSet> action) {
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
  public void forEach(ValuationSet set, BitSet restriction, Consumer<? super BitSet> action) {
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
  public ValuationSet union(ValuationSet set1, ValuationSet set2) {
    return create(factory.or(getBdd(set1), getBdd(set2)));
  }

  @Override
  public BooleanExpression<AtomLabel> toExpression(ValuationSet set) {
    return toExpression(getBdd(set));
  }

  @Override
  public <S> ValuationTree<S> inverse(Map<S, ValuationSet> sets) {
    if (sets.isEmpty()) {
      return ValuationTree.of(List.of());
    }

    int offset = alphabet.size();
    int requiredVariables = sets.size() - (factory.numberOfVariables() - offset);

    if (requiredVariables > 0) {
      factory.createVariables(requiredVariables);
    }

    // Build BDD describing the tree:
    int bdd = factory.getTrueNode();
    var list = List.copyOf(sets.entrySet());

    for (int i = 0; i < list.size(); i++) {
      var entry = list.get(i);
      int relation = factory.reference(factory.equivalence(
        factory.getVariableNode(offset + i), getBdd(entry.getValue())));
      bdd = factory.consume(factory.and(bdd, relation), bdd, relation);
    }

    ValuationTree<S> result = inverseMemoized(bdd, new HashMap<>(),
      i -> list.get(i - offset).getKey(), offset + list.size());
    factory.dereference(bdd);
    return result;
  }

  private <S> ValuationTree<S> inverseMemoized(int bdd, Map<Integer, ValuationTree<S>> cache,
    IntFunction<S> mapper, int maxSize) {
    assert bdd != factory.getTrueNode();
    assert bdd != factory.getFalseNode();

    var tree = cache.get(Integer.valueOf(bdd));

    if (tree != null) {
      return tree;
    }

    int variable = factory.getVariable(bdd);

    if (variable < alphabetSize()) {
      tree = ValuationTree.of(variable,
        inverseMemoized(factory.getHigh(bdd), cache, mapper, maxSize),
        inverseMemoized(factory.getLow(bdd), cache, mapper, maxSize));
    } else {
      tree = ValuationTree.of(getOnlySatisfyingAssignment(bdd, maxSize - 1)
        .stream().mapToObj(mapper).collect(toUnmodifiableSet()));
    }

    cache.put(Integer.valueOf(bdd), tree);
    return tree;
  }

  private BitSet getOnlySatisfyingAssignment(int bdd, int largestVariable) {
    assert bdd != factory.getTrueNode();
    assert bdd != factory.getFalseNode();
    int variable = factory.getVariable(bdd);

    if (variable < largestVariable) {
      int high = factory.getHigh(bdd);

      if (high == factory.getFalseNode()) {
        return getOnlySatisfyingAssignment(factory.getLow(bdd), largestVariable);
      } else {
        assert factory.getLow(bdd) == factory.getFalseNode();
        assert high != factory.getTrueNode();
        var assignment = getOnlySatisfyingAssignment(high, largestVariable);
        assignment.set(variable);
        return assignment;
      }
    } else {
      assert variable == largestVariable;
      assert factory.getTrueNode() == factory.getLow(bdd)
        || factory.getFalseNode() == factory.getLow(bdd);
      assert factory.getTrueNode() == factory.getHigh(bdd)
        || factory.getFalseNode() == factory.getHigh(bdd);

      if (factory.getHigh(bdd) == factory.getTrueNode()) {
        var set = new BitSet();
        set.set(variable);
        return set;
      } else {
        return new BitSet();
      }
    }
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
    return canonicalize(new BddValuationSet(this, bdd));
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

  static final class BddValuationSet extends ValuationSet implements BddWrapper {
    final int bdd;

    BddValuationSet(ValuationFactory factory, int bdd) {
      super(factory);
      this.bdd = bdd;
    }

    @Override
    public int bdd() {
      return bdd;
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
