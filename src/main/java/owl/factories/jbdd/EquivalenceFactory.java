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

package owl.factories.jbdd;

import static owl.factories.jbdd.EquivalenceFactory.BddEquivalenceClass;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import de.tum.in.jbdd.Bdd;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.collections.Collections3;
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

final class EquivalenceFactory extends GcManagedFactory<BddEquivalenceClass>
  implements EquivalenceClassFactory {

  private final List<String> atomicPropositions;
  private final BddVisitor visitor;

  private Formula.TemporalOperator[] reverseMapping;
  private final Map<Formula.TemporalOperator, Integer> mapping;

  private final int trueNode;
  private final int falseNode;

  @Nullable
  private Function<EquivalenceClass, Set<?>> temporalStepTreeCachedMapper;
  @Nullable
  private IdentityHashMap<EquivalenceClass, ValuationTree<?>> temporalStepTreeCache;

  EquivalenceFactory(Bdd bdd, List<String> atomicPropositions) {
    super(bdd);
    this.atomicPropositions = List.copyOf(atomicPropositions);

    int apSize = this.atomicPropositions.size();
    mapping = new HashMap<>();
    reverseMapping = new Formula.TemporalOperator[apSize];
    visitor = new BddVisitor();

    // Register literals.
    for (int i = 0; i < apSize; i++) {
      int node = this.bdd.createVariable();
      assert this.bdd.variable(node) == i;
    }

    trueNode = this.bdd.trueNode();
    falseNode = this.bdd.falseNode();
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
      int variable = mapping.get(formula);
      assert variable >= 0;
      return bdd.variableNode(variable);
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? trueNode : falseNode;
    }

    @Override
    public int visit(Conjunction conjunction) {
      int x = trueNode;

      // Reverse list for better performance!
      for (Formula child : Lists.reverse(conjunction.operands)) {
        int y = child.accept(this);
        x = bdd.consume(bdd.and(x, y), x, y);
      }

      return x;
    }

    @Override
    public int visit(Disjunction disjunction) {
      int x = falseNode;

      // Reverse list for better performance!
      for (Formula child : Lists.reverse(disjunction.operands)) {
        int y = child.accept(this);
        x = bdd.consume(bdd.or(x, y), x, y);
      }

      return x;
    }
  }

  private BddEquivalenceClass cast(EquivalenceClass clazz) {
    if (!this.equals(clazz.factory())) {
      throw new IllegalArgumentException("Incompatible factory.");
    }

    return (BddEquivalenceClass) clazz;
  }

  /**
   * Translate a BDD node into disjunctive normal form.
   */
  private List<Map<Integer, Boolean>> nodeToDisjunctiveNormalForm(int node) {
    if (node == trueNode) {
      var list = new ArrayList<Map<Integer, Boolean>>();
      list.add(new HashMap<>());
      return list;
    }

    if (node == falseNode) {
      return new ArrayList<>();
    }

    int variable = bdd.variable(node);
    int highNode = bdd.high(node);
    int lowNode = bdd.low(node);

    var trueList = nodeToDisjunctiveNormalForm(highNode);
    var falseList = nodeToDisjunctiveNormalForm(lowNode);

    if (variable < atomicPropositions.size()) {
      // high and low cannot be equivalent.
      assert !bdd.implies(highNode, lowNode) || !bdd.implies(lowNode, highNode);

      if (!bdd.implies(highNode, lowNode)) {
        trueList.forEach(x -> x.put(variable, Boolean.TRUE));
      }

      if (!bdd.implies(lowNode, highNode)) {
        falseList.forEach(x -> x.put(variable, Boolean.FALSE));
      }
    } else {
      // The represented Boolean functions are monotone.
      assert bdd.implies(lowNode, highNode);
      trueList.forEach(x -> x.put(variable, Boolean.TRUE));
      // No need to update falseList, since the Boolean function is monotone.
    }

    trueList.addAll(falseList);
    assert Collections3.isDistinct(trueList);

    return Collections3.maximalElements(trueList, (x, y) -> x.entrySet().containsAll(y.entrySet()));
  }

  private DisjunctionSetView dnf(int node) {
    return new DisjunctionSetView(nodeToDisjunctiveNormalForm(node));
  }

  private class DisjunctionSetView extends AbstractSet<Set<Formula>> {
    private final List<Map<Integer, Boolean>> disjunctiveNormalForm;

    private DisjunctionSetView(List<Map<Integer, Boolean>> disjunctiveNormalForm) {
      this.disjunctiveNormalForm = disjunctiveNormalForm.stream()
        .map(Map::copyOf)
        .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Iterator<Set<Formula>> iterator() {
      class Iter implements Iterator<Set<Formula>> {
        private final Iterator<Map<Integer, Boolean>> iterator = disjunctiveNormalForm.iterator();

        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public Set<Formula> next() {
          return new ConjunctionSetView(iterator.next());
        }
      }

      return new Iter();
    }

    @Override
    public Stream<Set<Formula>> stream() {
      return disjunctiveNormalForm.stream().map(ConjunctionSetView::new);
    }

    @Override
    public int size() {
      return disjunctiveNormalForm.size();
    }
  }

  private class ConjunctionSetView extends AbstractSet<Formula> {
    private final Map<Integer, Boolean> clause;

    private ConjunctionSetView(Map<Integer, Boolean> clause) {
      this.clause = clause;
    }

    @Override
    public Iterator<Formula> iterator() {
      class Iter implements Iterator<Formula> {
        private final Iterator<Map.Entry<Integer, Boolean>> iterator = clause.entrySet().iterator();

        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public Formula next() {
          return toFormula(iterator.next());
        }
      }

      return new Iter();
    }

    @Override
    public Stream<Formula> stream() {
      return clause.entrySet().stream().map(this::toFormula);
    }

    @Override
    public int size() {
      return clause.size();
    }

    private Formula toFormula(Map.Entry<Integer, Boolean> entry) {
      int literalOffset = EquivalenceFactory.this.atomicPropositions.size();

      if (entry.getKey() < literalOffset) {
        return Literal.of(entry.getKey(), !entry.getValue());
      }

      assert entry.getValue() : "Only positive temporal operators expected.";
      return reverseMapping[entry.getKey() - literalOffset];
    }
  }

  /**
   * This class does not implement a proper `equals` and `hashCode`, since GcManagedFactory ensures
   * uniqueness.
   */
  @SuppressWarnings("PMD.OverrideBothEqualsAndHashcode") // We only have a "bogus" assert equals
  static final class BddEquivalenceClass implements BddNode, EquivalenceClass {
    private final EquivalenceFactory factory;
    private final int node;

    @Nullable
    private Formula representative;

    // Caches
    @Nullable
    private Set<Formula.TemporalOperator> temporalOperatorsCache;
    @Nullable
    private BddEquivalenceClass unfoldCache;
    @Nullable
    private DisjunctionSetView disjunctiveNormalFormCache;

    private double truenessCache = Double.NaN;

    private BddEquivalenceClass(EquivalenceFactory factory, int node,
      @Nullable Formula internalRepresentative) {
      this.factory = factory;
      this.node = node;
      this.representative = internalRepresentative;
    }

    @Override
    public Set<Set<Formula>> disjunctiveNormalForm() {
      if (disjunctiveNormalFormCache == null) {
        disjunctiveNormalFormCache = factory.dnf(node);
      }

      return Objects.requireNonNull(disjunctiveNormalFormCache);
    }

    private Formula representative() {
      return Objects.requireNonNull(representative);
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
      return node == factory.falseNode;
    }

    @Override
    public boolean isTrue() {
      return node == factory.trueNode;
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
      var otherCasted = factory.cast(other);
      return factory.of(
        Conjunction.of(representative(), otherCasted.representative()),
        factory.bdd.and(node, otherCasted.node));
    }

    @Override
    public EquivalenceClass or(EquivalenceClass other) {
      var otherCasted = factory.cast(other);
      return factory.of(
        Disjunction.of(representative(), otherCasted.representative()),
        factory.bdd.or(node, otherCasted.node));
    }

    @Override
    public EquivalenceClass substitute(
      Function<? super Formula.TemporalOperator, ? extends Formula> substitution) {
      return factory.of(representative().substitute(substitution), true);
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

        // x.unfold().unfold() == x.unfold()
        if (unfoldCache.unfoldCache == null) {
          unfoldCache.unfoldCache = unfoldCache;
        } else {
          assert unfoldCache.unfoldCache.equals(unfoldCache);
        }
      }

      return unfoldCache;
    }

    @Override
    public double trueness() {
      if (isTrue()) {
        return 1.0d;
      }

      if (isFalse()) {
        return 0.0d;
      }

      if (Double.isNaN(truenessCache)) {
        var satisfyingAssignments = new BigDecimal(factory.bdd.countSatisfyingAssignments(node));
        var assignments = BigDecimal.valueOf(2).pow(factory.bdd.numberOfVariables());
        truenessCache = satisfyingAssignments.divide(assignments, 24, RoundingMode.HALF_DOWN)
          .doubleValue();
      }

      return truenessCache;
    }


    @Override
    public boolean equals(Object obj) {
      // Check that we are not comparing classes of different factories
      assert !(obj instanceof EquivalenceClass) || ((EquivalenceClass) obj).factory() == factory();
      return this == obj;
    }
  }
}
