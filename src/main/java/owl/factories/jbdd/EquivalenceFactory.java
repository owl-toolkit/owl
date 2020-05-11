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

import static owl.factories.jbdd.EquivalenceFactory.BddEquivalenceClass;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import de.tum.in.jbdd.Bdd;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.PropositionalIntVisitor;
import owl.ltl.visitors.Visitor;

final class EquivalenceFactory extends GcManagedFactory<BddEquivalenceClass>
  implements EquivalenceClassFactory {

  private final List<String> atomicPropositions;

  private final BddEquivalenceClass falseClass;
  private final BddEquivalenceClass trueClass;
  private final BddVisitor visitor;

  private Formula.TemporalOperator[] reverseMapping;
  private final Object2IntMap<Formula.TemporalOperator> mapping;

  @Nullable
  private Function<EquivalenceClass, Set<?>> temporalStepTreeCachedMapper;
  @Nullable
  private IdentityHashMap<EquivalenceClass, ValuationTree<?>> temporalStepTreeCache;

  public EquivalenceFactory(Bdd bdd, List<String> atomicPropositions) {
    super(bdd);
    this.atomicPropositions = List.copyOf(atomicPropositions);

    int apSize = this.atomicPropositions.size();
    mapping = new Object2IntOpenHashMap<>();
    mapping.defaultReturnValue(-1);
    reverseMapping = new Formula.TemporalOperator[apSize];
    visitor = new BddVisitor();

    // Register literals.
    for (int i = 0; i < apSize; i++) {
      int node = this.bdd.createVariable();
      assert this.bdd.variable(node) == i;
    }

    trueClass = new BddEquivalenceClass(this, this.bdd.trueNode(), BooleanConstant.TRUE);
    falseClass = new BddEquivalenceClass(this, this.bdd.falseNode(), BooleanConstant.FALSE);
  }

  @Override
  public List<String> atomicPropositions() {
    return atomicPropositions;
  }

  @Override
  public EquivalenceClass of(Formula formula) {
    return of(formula, true);
  }

  private BddEquivalenceClass of(Formula formula, boolean scanForUnknown) {
    if (scanForUnknown) {
      // Scan for unknown modal operators.
      var newPropositions = formula.subformulas(Formula.TemporalOperator.class)
        .stream().filter(x -> !mapping.containsKey(x)).sorted().collect(Collectors.toList());

      if (!newPropositions.isEmpty()) {
        // Create variables.
        int literalOffset = atomicPropositions.size();
        int newSize = mapping.size() + newPropositions.size();
        reverseMapping = Arrays.copyOf(reverseMapping, newSize);

        for (Formula.TemporalOperator proposition : newPropositions) {
          int variable = bdd.variable(bdd.createVariable());
          mapping.put(proposition, variable);
          reverseMapping[variable - literalOffset] = proposition;
        }
      }
    }

    return of(formula, bdd.dereference(formula.accept(visitor)));
  }

  private BddEquivalenceClass of(@Nullable Formula representative, int node) {
    if (node == bdd.trueNode()) {
      return trueClass;
    }

    if (node == bdd.falseNode()) {
      return falseClass;
    }

    var clazz = canonicalize(new BddEquivalenceClass(this, node, representative));

    if (clazz.representative == null) {
      clazz.representative = representative;
    }

    return clazz;
  }

  // Translates a formula into a BDD under the assumption every subformula is already registered.
  private final class BddVisitor extends PropositionalIntVisitor {
    @Override
    public int visit(Literal literal) {
      Preconditions.checkArgument(literal.getAtom() < atomicPropositions.size());
      int node = bdd.variableNode(literal.getAtom());
      return literal.isNegated() ? bdd.not(node) : node;
    }

    @Override
    protected int visit(Formula.TemporalOperator formula) {
      int variable = mapping.getInt(formula);
      assert variable >= 0;
      return bdd.variableNode(variable);
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? bdd.trueNode() : bdd.falseNode();
    }

    @Override
    public int visit(Conjunction conjunction) {
      int x = bdd.trueNode();

      // Reverse list for better performance!
      for (Formula child : Lists.reverse(conjunction.operands)) {
        int y = child.accept(this);
        x = bdd.consume(bdd.and(x, y), x, y);
      }

      return x;
    }

    @Override
    public int visit(Disjunction disjunction) {
      int x = bdd.falseNode();

      // Reverse list for better performance!
      for (Formula child : Lists.reverse(disjunction.operands)) {
        int y = child.accept(this);
        x = bdd.consume(bdd.or(x, y), x, y);
      }

      return x;
    }
  }

  private BddEquivalenceClass cast(EquivalenceClass clazz) {
    var castedClazz = (BddEquivalenceClass) clazz;
    assert equals(castedClazz.factory);
    return castedClazz;
  }

  public static final class BddEquivalenceClass implements BddNode, EquivalenceClass {
    private final EquivalenceFactory factory;
    private final int node;

    @Nullable
    private Formula representative;
    @Nullable
    private Set<Formula.TemporalOperator> temporalOperatorsCache = null;
    @Nullable
    private EquivalenceClass unfoldCache = null;

    private BddEquivalenceClass(EquivalenceFactory factory, int node,
      @Nullable Formula representative) {
      this.factory = factory;
      this.node = node;
      this.representative = representative;
    }

    @Override
    public Formula representative() {
      return Objects.requireNonNull(representative);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof EquivalenceClass)) {
        return false;
      }

      BddEquivalenceClass that = factory.cast((EquivalenceClass) obj);
      return node == that.node;
    }

    @Override
    public int hashCode() {
      return HashCommon.mix(node);
    }

    @Override
    public int node() {
      return node;
    }

    @Override
    public String toString() {
      return representative == null
        ? String.format("%d", node)
        : PrintVisitor.toString(representative, factory.atomicPropositions, false);
    }

    @Override
    public EquivalenceClassFactory factory() {
      return factory;
    }

    @Override
    public boolean isFalse() {
      return factory.bdd.falseNode() == node;
    }

    @Override
    public boolean isTrue() {
      return factory.bdd.trueNode() == node;
    }

    @Override
    public BitSet atomicPropositions(boolean includeNested) {
      if (!includeNested) {
        return factory.bdd.support(node, factory.atomicPropositions.size());
      }

      BitSet atomicPropositions = factory.bdd.support(node);
      int literalOffset = factory.atomicPropositions.size();
      int i = atomicPropositions.nextSetBit(literalOffset);

      for (; i >= 0; i = atomicPropositions.nextSetBit(i + 1)) {
        atomicPropositions.clear(i);
        atomicPropositions.or(
          factory.reverseMapping[i - literalOffset].atomicPropositions(true));
      }

      return atomicPropositions;
    }

    @Override
    public Set<Formula.TemporalOperator> temporalOperators() {
      if (temporalOperatorsCache == null) {
        BitSet support = factory.bdd.support(node);
        int literalOffset = factory.atomicPropositions.size();
        support.clear(0, literalOffset);
        temporalOperatorsCache = support.stream()
          .mapToObj(i -> factory.reverseMapping[i - literalOffset])
          .collect(Collectors.toUnmodifiableSet());
      }

      return temporalOperatorsCache;
    }

    @Override
    public boolean implies(EquivalenceClass other) {
      return factory.bdd.implies(node, factory.cast(other).node);
    }

    @Override
    public EquivalenceClass and(EquivalenceClass other) {
      return factory.of(
        Conjunction.of(representative(), other.representative()),
        factory.bdd.and(node, factory.cast(other).node));
    }

    @Override
    public EquivalenceClass or(EquivalenceClass other) {
      return factory.of(
        Disjunction.of(representative(), other.representative()),
        factory.bdd.or(node, factory.cast(other).node));
    }

    @Override
    public EquivalenceClass substitute(
      Function<? super Formula.TemporalOperator, ? extends Formula> substitution) {
      return factory.of(representative().substitute(substitution));
    }

    @Override
    public EquivalenceClass accept(Visitor<? extends Formula> visitor) {
      return factory.of(representative().accept(visitor));
    }

    @Override
    public EquivalenceClass temporalStep(BitSet valuation) {
      return factory.of(representative().temporalStep(valuation), false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ValuationTree<T> temporalStepTree(Function<EquivalenceClass, Set<T>> mapper) {
      if (factory.temporalStepTreeCache == null
        || !mapper.equals(factory.temporalStepTreeCachedMapper)) {
        factory.temporalStepTreeCachedMapper = (Function) mapper;
        factory.temporalStepTreeCache = new IdentityHashMap<>();
      }

      return temporalStepTree(representative(), new BitSet(), mapper,
        (IdentityHashMap) factory.temporalStepTreeCache);
    }

    private <T> ValuationTree<T> temporalStepTree(
      Formula initialRepresentative,
      BitSet pathTrace,
      Function<EquivalenceClass, Set<T>> mapper,
      IdentityHashMap<EquivalenceClass, ValuationTree<T>> cache) {

      var tree = cache.get(this);

      if (tree != null) {
        return tree;
      }

      var alphabet = factory.atomicPropositions;
      var bdd = factory.bdd;

      int atom = bdd.isNodeRoot(node) ? alphabet.size() : bdd.variable(node);

      if (atom >= alphabet.size()) {
        tree = ValuationTree.of(mapper.apply(
          factory.of(initialRepresentative.temporalStep(pathTrace), false)));
      } else {
        pathTrace.set(atom);
        var trueSubTree = factory.of(null, bdd.high(node))
          .temporalStepTree(initialRepresentative, pathTrace, mapper, cache);

        pathTrace.clear(atom, pathTrace.length());
        var falseSubTree = factory.of(null, bdd.low(node))
          .temporalStepTree(initialRepresentative, pathTrace, mapper, cache);

        tree = ValuationTree.of(atom, trueSubTree, falseSubTree);
      }

      cache.put(this, tree);
      return tree;
    }

    @Override
    public EquivalenceClass unfold() {
      if (unfoldCache == null) {
        unfoldCache = factory.of(representative().unfold(), false);
      }

      return unfoldCache;
    }

    @Override
    public double trueness() {
      var satisfyingAssignments = new BigDecimal(factory.bdd.countSatisfyingAssignments(node));
      var assignments = BigDecimal.valueOf(2).pow(factory.bdd.numberOfVariables());
      return satisfyingAssignments.divide(assignments, 24, RoundingMode.HALF_DOWN).doubleValue();
    }
  }
}
