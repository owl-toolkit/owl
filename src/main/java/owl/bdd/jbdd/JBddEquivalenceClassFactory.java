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

import static owl.bdd.jbdd.JBddEquivalenceClassFactory.JBddEquivalenceClass;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import de.tum.in.jbdd.Bdd;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
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
import owl.bdd.EquivalenceClassFactory;
import owl.collections.BitSet2;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.Literal;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.PropositionalIntVisitor;

final class JBddEquivalenceClassFactory extends JBddGcManagedFactory<JBddEquivalenceClass>
  implements EquivalenceClassFactory {

  private final List<String> atomicPropositions;
  private final JBddVisitor visitor;

  private TemporalOperator[] reverseMapping;
  private final Map<TemporalOperator, Integer> mapping;

  private final int trueNode;
  private final int falseNode;

  @Nullable
  private Function<EquivalenceClass, Set<?>> temporalStepTreeCachedMapper;
  @Nullable
  private IdentityHashMap<EquivalenceClass, ValuationTree<?>> temporalStepTreeCache;

  JBddEquivalenceClassFactory(Bdd bdd, List<String> atomicPropositions) {
    super(bdd);
    this.atomicPropositions = List.copyOf(atomicPropositions);

    int apSize = this.atomicPropositions.size();
    mapping = new HashMap<>();
    reverseMapping = new TemporalOperator[apSize];
    visitor = new JBddVisitor();

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

  private JBddEquivalenceClass of(Formula formula, boolean scanForUnknown) {
    if (scanForUnknown) {
      // Scan for unknown modal operators.
      var newPropositions = formula.subformulas(TemporalOperator.class)
        .stream().filter(x -> !mapping.containsKey(x)).sorted().collect(Collectors.toList());

      if (!newPropositions.isEmpty()) {
        // Create variables.
        int literalOffset = atomicPropositions.size();
        int newSize = mapping.size() + newPropositions.size();
        reverseMapping = Arrays.copyOf(reverseMapping, newSize);

        for (TemporalOperator proposition : newPropositions) {
          int variable = bdd.variable(bdd.createVariable());
          mapping.put(proposition, variable);
          reverseMapping[variable - literalOffset] = proposition;
        }
      }
    }

    return of(formula, bdd.dereference(formula.accept(visitor)));
  }

  private JBddEquivalenceClass of(@Nullable Formula representative, int node) {
    var clazz = canonicalize(new JBddEquivalenceClass(this, node, representative));

    if (clazz.representative == null) {
      clazz.representative = representative;
    }

    return clazz;
  }

  // Translates a formula into a BDD under the assumption every subformula is already registered.
  private final class JBddVisitor extends PropositionalIntVisitor {
    @Override
    public int visit(Literal literal) {
      Preconditions.checkArgument(literal.getAtom() < atomicPropositions.size());
      int node = bdd.variableNode(literal.getAtom());
      return literal.isNegated() ? bdd.not(node) : node;
    }

    @Override
    protected int visit(TemporalOperator formula) {
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

  private JBddEquivalenceClass cast(EquivalenceClass clazz) {
    if (!this.equals(clazz.factory())) {
      throw new IllegalArgumentException("Incompatible factory.");
    }

    return (JBddEquivalenceClass) clazz;
  }

  /**
   * Compute all paths to falseNode.
   */
  private List<List<Integer>> zeroPaths(int node, Map<Integer, List<List<Integer>>> cache) {

    List<List<Integer>> zeroPaths = cache.get(node);

    if (zeroPaths != null) {
      return zeroPaths;
    }

    JBddEquivalenceClass wrapper = canonicalWrapper(node);

    if (wrapper != null && wrapper.zeroPathsCache != null) {
      zeroPaths = wrapper.zeroPathsCache;
      cache.put(node, zeroPaths);
      return zeroPaths;
    }

    if (node == trueNode) {
      zeroPaths = List.of();
      cache.put(trueNode, zeroPaths);
      return zeroPaths;
    }

    if (node == falseNode) {
      zeroPaths = List.of(List.of());
      cache.put(falseNode, zeroPaths);
      return zeroPaths;
    }

    int variable = bdd.variable(node);
    int highNode = bdd.high(node);
    int lowNode = bdd.low(node);

    var highZeroPaths = zeroPaths(highNode, cache);
    var lowZeroPaths = new ArrayList<>(zeroPaths(lowNode, cache));

    boolean highImpliesLow = bdd.implies(highNode, lowNode);
    boolean lowImpliesHigh = bdd.implies(lowNode, highNode);

    if (!highImpliesLow) {
      lowZeroPaths.replaceAll(zeroPath -> {
        List<Integer> extendedZeroPath = new ArrayList<>(zeroPath.size() + 1);
        extendedZeroPath.add(-(variable + 1));
        extendedZeroPath.addAll(zeroPath);
        extendedZeroPath.sort(Integer::compare);
        return List.copyOf(extendedZeroPath);
      });
    }

    if (variable < atomicPropositions.size()) {
      // high and low cannot be equivalent.
      assert !highImpliesLow || !lowImpliesHigh;

      if (lowImpliesHigh) {
        lowZeroPaths.addAll(highZeroPaths);
      } else {
        // Update and copy into lowZeroPaths.
        highZeroPaths.forEach(zeroPath -> {
          List<Integer> extendedZeroPath = new ArrayList<>(zeroPath.size() + 1);
          extendedZeroPath.add(variable);
          extendedZeroPath.addAll(zeroPath);
          extendedZeroPath.sort(Integer::compare);
          lowZeroPaths.add(List.copyOf(extendedZeroPath));
        });
      }

    } else {
      // The represented Boolean functions are monotone.
      assert lowImpliesHigh;
      lowZeroPaths.addAll(highZeroPaths);
    }

    lowZeroPaths.sort(Comparator.comparing(List::size));
    zeroPaths = List.copyOf(Collections3.maximalElements(
      lowZeroPaths, JBddEquivalenceClassFactory::containsAll));
    cache.put(node, zeroPaths);

    // Cache zeroPaths in wrapper.
    if (wrapper != null && wrapper.zeroPathsCache == null) {
      wrapper.zeroPathsCache = zeroPaths;
    }

    return zeroPaths;
  }

  /**
   * Compute all paths to trueNode.
   */
  private List<List<Integer>> onePaths(int node, Map<Integer, List<List<Integer>>> cache) {

    List<List<Integer>> onePaths = cache.get(node);

    if (onePaths != null) {
      return onePaths;
    }

    JBddEquivalenceClass wrapper = canonicalWrapper(node);

    if (wrapper != null && wrapper.onePathsCache != null) {
      onePaths = wrapper.onePathsCache;
      cache.put(node, onePaths);
      return onePaths;
    }

    if (node == trueNode) {
      onePaths = List.of(List.of());
      cache.put(trueNode, onePaths);
      return onePaths;
    }

    if (node == falseNode) {
      onePaths = List.of();
      cache.put(falseNode, onePaths);
      return onePaths;
    }

    int variable = bdd.variable(node);
    int highNode = bdd.high(node);
    int lowNode = bdd.low(node);

    var highOnePaths = new ArrayList<>(onePaths(highNode, cache));
    var lowOnePaths = onePaths(lowNode, cache);

    boolean highImpliesLow = bdd.implies(highNode, lowNode);
    boolean lowImpliesHigh = bdd.implies(lowNode, highNode);

    if (!highImpliesLow) {
      highOnePaths.replaceAll(onePath -> {
        List<Integer> extendedOnePath = new ArrayList<>(onePath.size() + 1);
        extendedOnePath.add(variable);
        extendedOnePath.addAll(onePath);
        extendedOnePath.sort(Integer::compare);
        return List.copyOf(extendedOnePath);
      });
    }

    if (variable < atomicPropositions.size()) {
      // high and low cannot be equivalent.
      assert !highImpliesLow || !lowImpliesHigh;

      if (lowImpliesHigh) {
        highOnePaths.addAll(lowOnePaths);
      } else {
        // Update and copy into highOnePaths.
        lowOnePaths.forEach(onePath -> {
          List<Integer> extendedOnePath = new ArrayList<>(onePath.size() + 1);
          extendedOnePath.add(-(variable + 1));
          extendedOnePath.addAll(onePath);
          extendedOnePath.sort(Integer::compare);
          highOnePaths.add(List.copyOf(extendedOnePath));
        });
      }

    } else {
      // The represented Boolean functions are monotone.
      assert lowImpliesHigh;
      highOnePaths.addAll(lowOnePaths);
    }

    highOnePaths.sort(Comparator.comparing(List::size));
    onePaths = List.copyOf(Collections3.maximalElements(
      highOnePaths, JBddEquivalenceClassFactory::containsAll));
    cache.put(node, onePaths);

    // Cache onePaths in wrapper.
    if (wrapper != null && wrapper.onePathsCache == null) {
      wrapper.onePathsCache = onePaths;
    }

    return onePaths;
  }

  private static boolean containsAll(List<Integer> path, List<Integer> otherPath) {
    // Index in the path list.
    int j = 0;
    int pathSize = path.size();

    for (int i = 0, otherPathSize = otherPath.size(); i < otherPathSize; i++) {

      // There are too many elements left in otherPath, path cannot contain all elements of it.
      if (pathSize - j < otherPathSize - i) {
        return false;
      }

      int value = Integer.MIN_VALUE;
      int otherValue = otherPath.get(i);

      // Search in the sorted list for a matching value
      for (; value < otherValue && j < pathSize; j++) {
        value = path.get(j);
      }

      if (value != otherValue) {
        return false;
      }
    }

    return true;
  }

  /**
   * This class does not implement a proper `equals` and `hashCode`, since GcManagedFactory ensures
   * uniqueness.
   */
  @SuppressWarnings("PMD.OverrideBothEqualsAndHashcode") // We only have a "bogus" assert equals
  static final class JBddEquivalenceClass implements JBddNode, EquivalenceClass {

    private final JBddEquivalenceClassFactory factory;
    private final int node;

    @Nullable
    private Formula representative;

    // Caches
    @Nullable
    private Set<TemporalOperator> temporalOperatorsCache;
    @Nullable
    private JBddEquivalenceClass unfoldCache;
    @Nullable
    private List<List<Integer>> zeroPathsCache;
    @Nullable
    private List<List<Integer>> onePathsCache;
    @Nullable
    private BitSet atomicPropositionsCache;
    @Nullable
    private BitSet atomicPropositionsCacheIncludeNested;

    private double truenessCache = Double.NaN;

    private JBddEquivalenceClass(JBddEquivalenceClassFactory factory, int node,
      @Nullable Formula internalRepresentative) {
      this.factory = factory;
      this.node = node;
      this.representative = internalRepresentative;
    }

    @Override
    public Set<Set<Formula>> conjunctiveNormalForm() {
      if (zeroPathsCache == null) {
        zeroPathsCache = List.copyOf(factory.zeroPaths(node, new HashMap<>()));
      }

      return new CnfView(factory.atomicPropositions.size(), factory.reverseMapping, zeroPathsCache);
    }

    @Override
    public Set<Set<Formula>> disjunctiveNormalForm() {
      if (onePathsCache == null) {
        onePathsCache = List.copyOf(factory.onePaths(node, new HashMap<>()));
      }

      return new DnfView(factory.atomicPropositions.size(), onePathsCache, factory.reverseMapping);
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
    public BitSet atomicPropositions() {
      if (atomicPropositionsCache == null) {
        atomicPropositionsCache = BitSet2.copyOf(
          factory.bdd.support(node, factory.atomicPropositions.size()));
      }

      return BitSet2.copyOf(atomicPropositionsCache);
    }

    @Override
    public BitSet atomicPropositions(boolean includeNested) {
      if (!includeNested) {
        return atomicPropositions();
      }

      if (atomicPropositionsCacheIncludeNested == null) {
        BitSet atomicPropositions = BitSet2.copyOf(factory.bdd.support(node));
        int literalOffset = factory.atomicPropositions.size();
        int i = atomicPropositions.nextSetBit(literalOffset);

        for (; i >= 0; i = atomicPropositions.nextSetBit(i + 1)) {
          atomicPropositions.clear(i);
          atomicPropositions.or(
            factory.reverseMapping[i - literalOffset].atomicPropositions(true));
        }

        atomicPropositionsCacheIncludeNested = atomicPropositions;
      }

      return BitSet2.copyOf(atomicPropositionsCacheIncludeNested);
    }

    @Override
    public Set<TemporalOperator> temporalOperators() {
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
      Function<? super TemporalOperator, ? extends Formula> substitution) {
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
          assert unfoldCache.unfoldCache == unfoldCache;
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

  private static class CnfView extends AbstractSet<Set<Formula>> {
    private final int atomicPropositions;
    private final TemporalOperator[] reverseMapping;
    private final List<List<Integer>> zeroPaths;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    private CnfView(
      int atomicPropositions, TemporalOperator[] reverseMapping, List<List<Integer>> zeroPaths) {
      this.atomicPropositions = atomicPropositions;
      this.reverseMapping = reverseMapping;
      this.zeroPaths = zeroPaths;
    }

    @Override
    public Iterator<Set<Formula>> iterator() {
      return Iterators.transform(zeroPaths.iterator(), this::transform);
    }

    @Override
    public Stream<Set<Formula>> stream() {
      return zeroPaths.stream().map(this::transform);
    }

    @Override
    public boolean isEmpty() {
      return zeroPaths.isEmpty();
    }

    @Override
    public int size() {
      return zeroPaths.size();
    }

    private CnfClauseView transform(List<Integer> zeroPath) {
      return new CnfClauseView(atomicPropositions, reverseMapping, zeroPath);
    }
  }

  private static class DnfView extends AbstractSet<Set<Formula>> {
    private final int atomicPropositions;
    private final List<List<Integer>> onePaths;
    private final TemporalOperator[] reverseMapping;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    private DnfView(
      int atomicPropositions, List<List<Integer>> onePaths, TemporalOperator[] reverseMapping) {
      this.atomicPropositions = atomicPropositions;
      this.onePaths = onePaths;
      this.reverseMapping = reverseMapping;
    }

    @Override
    public Iterator<Set<Formula>> iterator() {
      return Iterators.transform(onePaths.iterator(), this::transform);
    }

    @Override
    public Stream<Set<Formula>> stream() {
      return onePaths.stream().map(this::transform);
    }

    @Override
    public boolean isEmpty() {
      return onePaths.isEmpty();
    }

    @Override
    public int size() {
      return onePaths.size();
    }

    private DnfClauseView transform(List<Integer> onePath) {
      return new DnfClauseView(atomicPropositions, onePath, reverseMapping);
    }
  }

  private static class CnfClauseView extends AbstractSet<Formula> {
    private final int atomicPropositions;
    private final TemporalOperator[] reverseMapping;
    private final List<Integer> zeroPath;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    private CnfClauseView(
      int atomicPropositions, TemporalOperator[] reverseMapping, List<Integer> zeroPath) {
      this.atomicPropositions = atomicPropositions;
      this.reverseMapping = reverseMapping;
      this.zeroPath = zeroPath;
    }

    @Override
    public Iterator<Formula> iterator() {
      return Iterators.transform(zeroPath.iterator(), this::transform);
    }

    @Override
    public Stream<Formula> stream() {
      return zeroPath.stream().map(this::transform);
    }

    @Override
    public boolean isEmpty() {
      return zeroPath.isEmpty();
    }

    @Override
    public int size() {
      return zeroPath.size();
    }

    private Formula transform(int zeroPathNode) {
      assert zeroPathNode < atomicPropositions : "Node encodes non-negated TemporalOperator";

      if (0 <= zeroPathNode) {
        return Literal.of(zeroPathNode, true);
      }

      int negatedNode = -(zeroPathNode + 1);

      if (negatedNode < atomicPropositions) {
        return Literal.of(negatedNode);
      }

      return reverseMapping[negatedNode - atomicPropositions];
    }
  }

  private static class DnfClauseView extends AbstractSet<Formula> {
    private final int atomicPropositions;
    private final List<Integer> onePath;
    private final TemporalOperator[] reverseMapping;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    private DnfClauseView(
      int atomicPropositions, List<Integer> onePath, TemporalOperator[] reverseMapping) {
      this.atomicPropositions = atomicPropositions;
      this.onePath = onePath;
      this.reverseMapping = reverseMapping;
    }

    @Override
    public Iterator<Formula> iterator() {
      return Iterators.transform(onePath.iterator(), this::transform);
    }

    @Override
    public Stream<Formula> stream() {
      return onePath.stream().map(this::transform);
    }

    @Override
    public boolean isEmpty() {
      return onePath.isEmpty();
    }

    @Override
    public int size() {
      return onePath.size();
    }

    private Formula transform(int onePathNode) {
      assert -(atomicPropositions + 1) <= onePathNode : "Node encodes negation of TemporalOperator";

      if (onePathNode < 0) {
        return Literal.of(-(onePathNode + 1), true);
      }

      if (onePathNode < atomicPropositions) {
        return Literal.of(onePathNode);
      }

      return reverseMapping[onePathNode - atomicPropositions];
    }
  }
}
