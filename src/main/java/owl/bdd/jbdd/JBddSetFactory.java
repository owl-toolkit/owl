/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer, Remco Abraham)
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

import static owl.bdd.jbdd.JBddSetFactory.JBddSet;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;
import static owl.logic.propositional.PropositionalFormula.falseConstant;
import static owl.logic.propositional.PropositionalFormula.trueConstant;

import com.google.common.base.Preconditions;
import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

final class JBddSetFactory extends JBddGcManagedFactory<JBddSet> implements BddSetFactory {

  private final int trueNode;
  private final int falseNode;
  private int variables;

  JBddSetFactory(int size) {
    super(createBdd(size), false);

    trueNode = this.bdd.trueNode();
    falseNode = this.bdd.falseNode();
    variables = 0;
  }

  private static Bdd createBdd(int size) {
    var configuration = ImmutableBddConfiguration.builder()
        .logStatisticsOnShutdown(false)
        .useGlobalComposeCache(false)
        .integrityDuplicatesMaximalSize(50)
        .cacheBinaryDivider(4)
        .cacheTernaryDivider(4)
        .growthFactor(4)
        .build();

    // Do not use buildBddIterative, since 'support(...)' is broken.
    return BddFactory.buildBddRecursive(size, configuration);
  }

  @Override
  public BddSet of(boolean booleanConstant) {
    return create(booleanConstant ? trueNode : falseNode);
  }

  @Override
  public BddSet of(int variable) {
    return create(variableNode(variable));
  }

  @Override
  public BddSet of(BitSet valuation, int upTo) {
    int node = bdd.trueNode();

    for (int i = 0; i < upTo; i++) {
      node = createBddUpdateHelper(valuation, i, node);
    }

    return create(node);
  }

  @Override
  public BddSet of(BitSet valuation, BitSet support) {
    int node = bdd.trueNode();

    for (int i = support.nextSetBit(0); i != -1; i = support.nextSetBit(i + 1)) {
      node = createBddUpdateHelper(valuation, i, node);
    }

    return create(node);
  }

  @Override
  public BddSet union(BddSet... bddSets) {
    int node = bdd.falseNode();
    for (BddSet bddSet : bddSets) {
      node = bdd.updateWith(bdd.or(((JBddSet) bddSet).node, node), node);
    }
    return create(bdd.dereference(node));
  }

  @Override
  public BddSet intersection(BddSet... bddSets) {
    int node = bdd.trueNode();
    for (BddSet bddSet : bddSets) {
      node = bdd.updateWith(bdd.and(((JBddSet) bddSet).node, node), node);
    }
    return create(bdd.dereference(node));
  }

  @Override
  public BddSet of(PropositionalFormula<Integer> expression) {
    return null;
  }

  @Override
  public <S> MtBdd<S> toMtBdd(Map<? extends S, ? extends BddSet> sets) {
    MtBdd<S> union = MtBdd.of();

    for (Map.Entry<? extends S, ? extends BddSet> entry : sets.entrySet()) {
      union = MtBddOperations.union(union,
          toTree(entry.getKey(), getNode(entry.getValue()), new HashMap<>()));
    }

    return union;
  }

  private PropositionalFormula<Integer> toExpression(int node) {
    if (node == falseNode) {
      return falseConstant();
    }

    if (node == trueNode) {
      return trueConstant();
    }

    var atomicProposition = Variable.of(bdd.variable(node));
    return Disjunction.of(
        Conjunction.of(atomicProposition, toExpression(bdd.high(node))),
        Conjunction.of(Negation.of(atomicProposition), toExpression(bdd.low(node))));
  }

  private int variableNode(int variable) {
    if (variable >= variables) {
      bdd.createVariables((variable + 1) - variables);
      variables = bdd.numberOfVariables();
    }

    Objects.checkIndex(variable, variables);
    return bdd.variableNode(variable);
  }

  private JBddSet create(int node) {
    return canonicalize(new JBddSet(this, node));
  }

  private int createBddUpdateHelper(BitSet set, int var, int node) {
    int variableNode = variableNode(var);
    assert bdd.isVariable(variableNode);
    return bdd.and(node, set.get(var) ? variableNode : bdd.not(variableNode));
  }

  private int getNode(BddSet vs) {
    Preconditions.checkArgument(this == vs.factory());
    int node = ((JBddSet) vs).node;
    assert bdd.getReferenceCount(node) > 0 || bdd.getReferenceCount(node) == -1;
    return node;
  }

  private <E> MtBdd<E> toTree(E value, int bddNode, Map<? super Integer, MtBdd<E>> cache) {
    var tree = cache.get(bddNode);

    if (tree != null) {
      return tree;
    }

    if (bddNode == falseNode) {
      tree = MtBdd.of();
    } else if (bddNode == trueNode) {
      tree = MtBdd.of(value);
    } else {
      tree = MtBdd.of(bdd.variable(bddNode),
          toTree(value, bdd.high(bddNode), cache),
          toTree(value, bdd.low(bddNode), cache));
    }

    cache.put(bddNode, tree);
    return tree;
  }

  private <E> MtBdd<E> filter(MtBdd<E> tree, int bddNode) {
    if (bddNode == falseNode) {
      return MtBdd.of();
    }

    if (bddNode == trueNode) {
      return tree;
    }

    int bddVariable = bdd.variable(bddNode);
    int bddHigh = bdd.high(bddNode);
    int bddLow = bdd.low(bddNode);

    if (tree instanceof MtBdd.Leaf) {
      return MtBdd.of(bddVariable, filter(tree, bddHigh), filter(tree, bddLow));
    }

    var node = (MtBdd.Node<E>) tree;

    if (bddVariable == node.variable) {
      return MtBdd.of(node.variable,
          filter(node.trueChild, bddHigh),
          filter(node.falseChild, bddLow));
    } else if (bddVariable < node.variable) {
      return MtBdd.of(bddVariable,
          filter(tree, bddHigh),
          filter(tree, bddLow));
    } else {
      return MtBdd.of(node.variable,
          filter(node.trueChild, bddNode),
          filter(node.falseChild, bddNode));
    }
  }

  /**
   * This class does not implement a proper `equals` and `hashCode`, since GcManagedFactory ensures
   * uniqueness.
   */
  @SuppressWarnings("PMD.OverrideBothEqualsAndHashcode") // We only have a "bogus" assert equals
  static final class JBddSet implements JBddNode, BddSet {

    private final JBddSetFactory factory;
    private final int node;

    @Nullable
    private BitSet supportCache;

    private JBddSet(JBddSetFactory factory, int node) {
      this.factory = factory;
      this.node = node;
    }

    @Override
    public BddSetFactory factory() {
      return factory;
    }

    @Override
    public BddSet complement() {
      return factory.create(factory.bdd.not(node));
    }

    @Override
    public BddSet project(ImmutableBitSet quantifiedAtomicPropositions) {
      return factory.create(
          factory.bdd.exists(node, quantifiedAtomicPropositions.copyInto(new BitSet())));
    }

    @Override
    public BddSet relabel(IntUnaryOperator mapping) {
      BitSet support = support();
      int[] substitutions = new int[support.length()];
      Arrays.fill(substitutions, -1);

      for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i + 1)) {
        int j = mapping.applyAsInt(i);

        if (j == -1) {
          substitutions[i] = -1;
        } else if (j >= 0) {
          substitutions[i] = factory.variableNode(j);
        } else {
          throw new IllegalArgumentException(
              String.format("Invalid mapping: {%s} -> {%s}", i, j));
        }
      }

      return factory.create(factory.bdd.compose(node, substitutions));
    }

    @Override
    public <E> MtBdd<E> intersection(MtBdd<E> tree) {
      return factory.filter(tree, node);
    }

    @Override
    public int node() {
      return node;
    }

    @Override
    public boolean isEmpty() {
      return node == factory.falseNode;
    }

    @Override
    public boolean isUniverse() {
      return node == factory.trueNode;
    }

    @Override
    public boolean contains(BitSet valuation) {
      return factory.bdd.evaluate(node, valuation);
    }

    @Override
    public boolean containsAll(BddSet valuationSet) {
      return factory.bdd.implies(factory.getNode(valuationSet), node);
    }

    @Override
    public BddSet union(BddSet other) {
      return factory.create(factory.bdd.or(node, factory.getNode(other)));
    }

    @Override
    public BddSet union(BddSet... bddSets) {
      int node = this.node;
      factory.bdd.reference(node);
      for (BddSet bddSet : bddSets) {
        node = factory.bdd.updateWith(factory.bdd.or(((JBddSet) bddSet).node, node), node);
      }
      return factory.create(factory.bdd.dereference(node));
    }

    @Override
    public BddSet intersection(BddSet other) {
      return factory.create(factory.bdd.and(node, factory.getNode(other)));
    }

    @Override
    public BddSet intersection(BddSet... bddSets) {
      int node = this.node;
      factory.bdd.reference(node);
      for (BddSet bddSet : bddSets) {
        node = factory.bdd.updateWith(factory.bdd.and(((JBddSet) bddSet).node, node), node);
      }
      return factory.create(factory.bdd.dereference(node));
    }

    @Override
    public PropositionalFormula<Integer> toExpression() {
      return factory.toExpression(node);
    }

    @Override
    public String toString() {
      return '[' + this.toExpression().toString() + ']';
    }

    @Override
    public Iterator<BitSet> iterator(int support) {
      BitSet supportBitSet = new BitSet();
      supportBitSet.set(0, support);
      return iterator(ImmutableBitSet.copyOf(supportBitSet));
    }

    @Override
    public Iterator<BitSet> iterator(ImmutableBitSet support) {
      return createBddIterator(support, support.first(), node, new BitSet());
    }

    private Iterator<BitSet> createBddIterator(
        ImmutableBitSet support, OptionalInt currentVariable, int node, BitSet path) {

      if (node == factory.falseNode) {
        return Collections.emptyIterator();
      }

      if (node == factory.trueNode) {
        if (currentVariable.isEmpty()) {
          return List.of(BitSet2.copyOf(path)).iterator();
        }

        return new BddIterator(support, currentVariable.getAsInt(), node, path);
      }

      int bddVariable = factory.bdd.variable(node);

      if (currentVariable.isEmpty()
          || bddVariable < currentVariable.getAsInt()
          || !support.contains(bddVariable)) {

        throw new IllegalArgumentException("Detected a BDD-variable that is not in the support.");
      }

      assert 0 <= bddVariable;
      assert 0 <= currentVariable.getAsInt();
      assert currentVariable.getAsInt() <= bddVariable;

      // Kicking down the can.
      return new BddIterator(support, currentVariable.getAsInt(), node, path);
    }

    // This is a non-static class in order to keep the JBddSet alive and thus protect its node from
    // the GC.
    private class BddIterator implements Iterator<BitSet> {

      private final int variable;
      private final int node;
      private final ImmutableBitSet support;
      private final BitSet path;

      @Nullable
      private Iterator<BitSet> lowIterator;
      @Nullable
      private Iterator<BitSet> highIterator;

      private BddIterator(ImmutableBitSet support, int variable, int node, BitSet path) {
        Bdd bdd = factory.bdd;

        assert node != factory.falseNode;

        this.variable = variable;
        this.node = node;
        this.support = support;
        this.path = path;

        lowIterator = createBddIterator(support,
            support.higher(variable),
            node != factory.trueNode && variable == bdd.variable(node)
                ? bdd.low(node)
                : node,
            path);
      }

      private void initHighIterator() {
        assert !lowIterator.hasNext();
        lowIterator = null;
        path.clear(variable + 1, support.last().orElseThrow() + 1);
        path.set(variable);
        highIterator = createBddIterator(
            support,
            support.higher(variable),
            node != factory.trueNode && variable == factory.bdd.variable(node)
                ? factory.bdd.high(node)
                : node,
            path);
      }

      private void checkInvariants() {
        assert lowIterator == null || highIterator == null;
        assert lowIterator != null || highIterator != null;
      }

      @Override
      public boolean hasNext() {
        checkInvariants();

        if (lowIterator != null) {
          if (lowIterator.hasNext()) {
            return true;
          }

          initHighIterator();
        }

        checkInvariants();
        return highIterator.hasNext();
      }

      @Override
      public BitSet next() {
        checkInvariants();

        if (lowIterator != null) {
          if (lowIterator.hasNext()) {
            return lowIterator.next();
          }

          initHighIterator();
        }

        checkInvariants();
        return highIterator.next();
      }
    }

    @Override
    public Optional<BitSet> element() {
      if (this.isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(factory.bdd.getSatisfyingAssignment(node));
    }

    @Override
    public BitSet support() {
      if (supportCache == null) {
        BitSet support = factory.bdd.support(node);
        supportCache = BitSet2.copyOf(support);
        return support;
      }

      return BitSet2.copyOf(supportCache);
    }
  }
}
