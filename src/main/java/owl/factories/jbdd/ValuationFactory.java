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
import static owl.factories.jbdd.ValuationFactory.BddValuationSet;

import de.tum.in.jbdd.Bdd;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.HashCommon;
import java.math.BigInteger;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;

final class ValuationFactory extends GcManagedFactory<BddValuationSet>
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

    universe = create(factory.trueNode());
    empty = create(factory.falseNode());
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
    return create(bdd.variableNode(literal));
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
    return create(bdd.not(getNode(set)));
  }

  @Override
  public boolean contains(ValuationSet set, BitSet valuation) {
    return bdd.evaluate(getNode(set), valuation);
  }

  @Override
  public boolean implies(ValuationSet one, ValuationSet other) {
    return bdd.implies(getNode(one), getNode(other));
  }

  @Override
  public void forEach(ValuationSet set, Consumer<? super BitSet> action) {
    bdd.forEachSolution(getNode(set), action);
  }

  @Override
  public void forEach(ValuationSet set, BitSet restriction, Consumer<? super BitSet> action) {
    // TODO Make this native to the bdd?
    int variables = bdd.numberOfVariables();

    BitSet restrictedVariables = BitSets.copyOf(restriction);
    restrictedVariables.flip(0, variables);

    int restrict = bdd.restrict(getNode(set), restrictedVariables, EMPTY);
    bdd.forEachPath(restrict, (solution, solutionSupport) -> {
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
    return !bdd.implies(getNode(set), bdd.not(getNode(other)));
  }


  @Override
  public ValuationSet intersection(ValuationSet set1, ValuationSet set2) {
    return create(bdd.and(getNode(set1), getNode(set2)));
  }

  @Override
  public ValuationSet union(ValuationSet set1, ValuationSet set2) {
    return create(bdd.or(getNode(set1), getNode(set2)));
  }

  @Override
  public BooleanExpression<AtomLabel> toExpression(ValuationSet set) {
    return toExpression(getNode(set));
  }

  @Override
  public <S> ValuationTree<S> inverse(Map<S, ValuationSet> sets) {
    if (sets.isEmpty()) {
      return ValuationTree.of(List.of());
    }

    int offset = alphabet.size();
    int requiredVariables = sets.size() - (bdd.numberOfVariables() - offset);

    if (requiredVariables > 0) {
      bdd.createVariables(requiredVariables);
    }

    // Build BDD describing the tree:
    int node = bdd.trueNode();
    var list = List.copyOf(sets.entrySet());

    for (int i = 0; i < list.size(); i++) {
      var entry = list.get(i);
      int relation = bdd.reference(bdd.equivalence(
        bdd.variableNode(offset + i), getNode(entry.getValue())));
      node = bdd.consume(bdd.and(node, relation), node, relation);
    }

    ValuationTree<S> result = inverseMemoized(node, new HashMap<>(),
      i -> list.get(i - offset).getKey(), offset + list.size());
    bdd.dereference(node);
    return result;
  }

  @Override
  public Iterator<BitSet> iterator(ValuationSet set) {
    return bdd.solutionIterator(getNode(set));
  }

  @Override
  public BigInteger size(ValuationSet set) {
    return bdd.countSatisfyingAssignments(getNode(set));
  }

  private <S> ValuationTree<S> inverseMemoized(int node, Map<Integer, ValuationTree<S>> cache,
    IntFunction<S> mapper, int maxSize) {
    assert node != bdd.trueNode();
    assert node != bdd.falseNode();

    var tree = cache.get(node);

    if (tree != null) {
      return tree;
    }

    int variable = bdd.variable(node);

    if (variable < alphabetSize()) {
      tree = ValuationTree.of(variable,
        inverseMemoized(bdd.high(node), cache, mapper, maxSize),
        inverseMemoized(bdd.low(node), cache, mapper, maxSize));
    } else {
      tree = ValuationTree.of(getOnlySatisfyingAssignment(node, maxSize - 1)
        .stream().mapToObj(mapper).collect(toUnmodifiableSet()));
    }

    cache.put(node, tree);
    return tree;
  }

  private BitSet getOnlySatisfyingAssignment(int node, int largestVariable) {
    assert node != bdd.trueNode();
    assert node != bdd.falseNode();
    int variable = bdd.variable(node);

    if (variable < largestVariable) {
      int high = bdd.high(node);

      if (high == bdd.falseNode()) {
        return getOnlySatisfyingAssignment(bdd.low(node), largestVariable);
      } else {
        assert bdd.low(node) == bdd.falseNode();
        assert high != bdd.trueNode();
        var assignment = getOnlySatisfyingAssignment(high, largestVariable);
        assignment.set(variable);
        return assignment;
      }
    } else {
      assert variable == largestVariable;
      assert bdd.trueNode() == bdd.low(node)
        || bdd.falseNode() == bdd.low(node);
      assert bdd.trueNode() == bdd.high(node)
        || bdd.falseNode() == bdd.high(node);

      if (bdd.high(node) == bdd.trueNode()) {
        var set = new BitSet();
        set.set(variable);
        return set;
      } else {
        return new BitSet();
      }
    }
  }

  private BooleanExpression<AtomLabel> toExpression(int node) {
    if (node == bdd.falseNode()) {
      return FALSE;
    }

    if (node == bdd.trueNode()) {
      return TRUE;
    }

    BooleanExpression<AtomLabel> letter = new BooleanExpression<>(
      AtomLabel.createAPIndex(bdd.variable(node)));
    BooleanExpression<AtomLabel> pos = toExpression(bdd.high(node));
    BooleanExpression<AtomLabel> neg = toExpression(bdd.low(node));

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


  private BddValuationSet create(int node) {
    return canonicalize(new BddValuationSet(this, node));
  }

  private int createBdd(BitSet set, BitSet base) {
    assert base.length() <= alphabet.size();
    int node = bdd.trueNode();

    for (int i = base.nextSetBit(0); i != -1; i = base.nextSetBit(i + 1)) {
      node = createBddUpdateHelper(set, i, node);
    }

    return node;
  }

  private int createBdd(BitSet set) {
    int node = bdd.trueNode();

    for (int i = 0; i < alphabet.size(); i++) {
      node = createBddUpdateHelper(set, i, node);
    }

    return node;
  }

  private int createBddUpdateHelper(BitSet set, int var, int node) {
    int variableNode = bdd.variableNode(var);
    assert bdd.isVariable(variableNode);
    return bdd.and(node, set.get(var) ? variableNode : bdd.not(variableNode));
  }

  private int getNode(ValuationSet vs) {
    assert this.equals(vs.getFactory());
    int node = ((BddValuationSet) vs).node;
    assert bdd.getReferenceCount(node) > 0 || bdd.getReferenceCount(node) == -1;
    return node;
  }

  static final class BddValuationSet extends ValuationSet implements BddNode {
    private final int node;

    private BddValuationSet(ValuationFactory bdd, int node) {
      super(bdd);
      this.node = node;
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
      return node == other.node;
    }

    @Override
    public int hashCode() {
      return HashCommon.mix(node);
    }

    @Override
    public int node() {
      return node;
    }
  }
}
