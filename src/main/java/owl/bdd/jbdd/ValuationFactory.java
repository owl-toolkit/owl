/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.bdd.jbdd;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static owl.bdd.jbdd.ValuationFactory.BddValuationSet;

import de.tum.in.jbdd.Bdd;
import de.tum.in.naturals.bitset.BitSets;
import java.math.BigInteger;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.bdd.ValuationSet;
import owl.bdd.ValuationSetFactory;
import owl.collections.ValuationTree;

final class ValuationFactory extends GcManagedFactory<BddValuationSet>
  implements ValuationSetFactory {
  private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);
  private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);

  private final List<String> atomicPropositions;
  private final BddValuationSet empty;
  private final BddValuationSet universe;

  private final int trueNode;
  private final int falseNode;

  ValuationFactory(Bdd factory, List<String> atomicPropositions) {
    super(factory);
    this.atomicPropositions = List.copyOf(atomicPropositions);

    factory.createVariables(this.atomicPropositions.size());
    assert factory.numberOfVariables() == this.atomicPropositions.size();

    trueNode = factory.trueNode();
    falseNode = factory.falseNode();

    universe = create(trueNode);
    empty = create(falseNode);
  }

  @Override
  public List<String> atomicPropositions() {
    return atomicPropositions;
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

    BitSet restrictedVariables = owl.collections.BitSet2.copyOf(restriction);
    restrictedVariables.flip(0, variables);

    int restrict = bdd.restrict(getNode(set), restrictedVariables, new BitSet());
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

    int offset = atomicPropositions.size();
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

  private <S> ValuationTree<S> inverseMemoized(int node, Map<Integer, ValuationTree<S>> cache,
    IntFunction<S> mapper, int maxSize) {
    assert node != bdd.trueNode();
    assert node != bdd.falseNode();

    var tree = cache.get(node);

    if (tree != null) {
      return tree;
    }

    int variable = bdd.variable(node);

    if (variable < atomicPropositions().size()) {
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
    assert base.length() <= atomicPropositions.size();
    int node = bdd.trueNode();

    for (int i = base.nextSetBit(0); i != -1; i = base.nextSetBit(i + 1)) {
      node = createBddUpdateHelper(set, i, node);
    }

    return node;
  }

  private int createBdd(BitSet set) {
    int node = bdd.trueNode();

    for (int i = 0; i < atomicPropositions.size(); i++) {
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
    assert this.equals(vs.factory());
    int node = ((BddValuationSet) vs).node;
    assert bdd.getReferenceCount(node) > 0 || bdd.getReferenceCount(node) == -1;
    return node;
  }

  private <E> ValuationTree<E> filter(ValuationTree<E> tree, int bddNode) {
    if (bddNode == falseNode) {
      return ValuationTree.of();
    }

    if (bddNode == trueNode) {
      return tree;
    }

    int bddVariable = bdd.variable(bddNode);
    int bddHigh = bdd.high(bddNode);
    int bddLow = bdd.low(bddNode);

    if (tree instanceof ValuationTree.Leaf) {
      return ValuationTree.of(bddVariable, filter(tree, bddHigh), filter(tree, bddLow));
    }

    var node = (ValuationTree.Node<E>) tree;

    if (bddVariable == node.variable) {
      return ValuationTree.of(node.variable,
        filter(node.trueChild, bddHigh),
        filter(node.falseChild, bddLow));
    } else if (bddVariable < node.variable) {
      return ValuationTree.of(bddVariable,
        filter(tree, bddHigh),
        filter(tree, bddLow));
    } else {
      return ValuationTree.of(node.variable,
        filter(node.trueChild, bddNode),
        filter(node.falseChild, bddNode));
    }
  }

  /**
   * This class does not implement a proper `equals` and `hashCode`, since GcManagedFactory ensures
   * uniqueness.
   */
  @SuppressWarnings("PMD.OverrideBothEqualsAndHashcode") // We only have a "bogus" assert equals
  static final class BddValuationSet extends ValuationSet implements BddNode {
    private final ValuationFactory factory;
    private final int node;

    private BddValuationSet(ValuationFactory bdd, int node) {
      this.factory = bdd;
      this.node = node;
    }

    @Override
    public ValuationSetFactory factory() {
      return factory;
    }

    public ValuationSet complement() {
      return factory.create(factory.bdd.not(node));
    }

    @Override
    public ValuationSet project(BitSet quantifiedAtomicPropositions) {
      return factory.create(factory.bdd.exists(node, quantifiedAtomicPropositions));
    }

    @Override
    public ValuationSet relabel(IntUnaryOperator mapping) {
      int size = factory.atomicPropositions.size();
      int[] subsitutions = new int[factory.atomicPropositions.size()];

      for (int i = 0; i < size; i++) {
        int j = mapping.applyAsInt(i);

        if (j == -1) {
          subsitutions[i] = -1;
        } else if (0 <= j && j < size) {
          subsitutions[i] = factory.bdd.variableNode(j);
        } else {
          throw new IllegalArgumentException(
            String.format("Invalid mapping: {0} -> {1}", i, j));
        }
      }

      return factory.create(factory.bdd.compose(node, subsitutions));
    }

    @Override
    public <E> ValuationTree<E> filter(ValuationTree<E> tree) {
      return factory.filter(tree, node);
    }

    @Override
    public int node() {
      return node;
    }

    @Override
    public BigInteger size() {
      return factory.bdd.countSatisfyingAssignments(node);
    }


    @Override
    public boolean equals(Object obj) {
      // Check that we are not comparing classes of different factories
      assert !(obj instanceof ValuationSet) || ((ValuationSet) obj).factory() == factory();
      return this == obj;
    }
  }
}
