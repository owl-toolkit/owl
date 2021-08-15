/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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
import com.google.common.collect.Lists;
import de.tum.in.jbdd.Bdd;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.MtBdd;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.PropositionalIntVisitor;

final class JBddEquivalenceClassFactory extends JBddGcManagedFactory<JBddEquivalenceClass>
  implements EquivalenceClassFactory {

  private static final int[] EMPTY = {};

  private final List<String> atomicPropositions;
  private final JBddVisitor visitor;

  private TemporalOperator[] reverseMapping;
  private final Map<TemporalOperator, Integer> mapping;

  private final int trueNode;
  private final int falseNode;

  private final EquivalenceClass trueClass;
  private final EquivalenceClass falseClass;

  @Nullable
  private Function<EquivalenceClass, Set<?>> temporalStepTreeCachedMapper;
  @Nullable
  private IdentityHashMap<EquivalenceClass, MtBdd<?>> temporalStepTreeCache;

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

    trueClass = of(BooleanConstant.TRUE, trueNode);
    falseClass = of(BooleanConstant.FALSE, falseNode);
  }

  @Override
  public List<String> atomicPropositions() {
    return atomicPropositions;
  }

  @Override
  public EquivalenceClass of(boolean value) {
    return value ? trueClass : falseClass;
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

  @Override
  public EquivalenceClass and(Collection<? extends EquivalenceClass> classes) {
    @Nullable
    Set<Formula> representatives = new HashSet<>();
    int andNode = trueNode;

    for (EquivalenceClass clazz : classes) {
      var castedClass = cast(clazz);

      if (castedClass.representative == null) {
        representatives = null;
      } else if (representatives != null) {
        representatives.add(castedClass.representative);
      }

      andNode = bdd.updateWith(bdd.and(andNode, castedClass.node), andNode);

      if (andNode == falseNode) {
        return falseClass;
      }
    }

    return of(
      representatives == null ? null : Conjunction.of(representatives),
      bdd.dereference(andNode));
  }

  @Override
  public EquivalenceClass or(Collection<? extends EquivalenceClass> classes) {
    @Nullable
    Set<Formula> representatives = new HashSet<>();
    int orNode = falseNode;

    for (EquivalenceClass clazz : classes) {
      var castedClass = cast(clazz);

      if (castedClass.representative == null) {
        representatives = null;
      } else if (representatives != null) {
        representatives.add(castedClass.representative);
      }

      orNode = bdd.updateWith(bdd.or(orNode, castedClass.node), orNode);

      if (orNode == trueNode) {
        return trueClass;
      }
    }

    return of(
      representatives == null ? null : Disjunction.of(representatives),
      bdd.dereference(orNode));
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
  private List<int[]> zeroPaths(int node, Map<Integer, List<int[]>> cache) {

    List<int[]> zeroPaths = cache.get(node);

    if (zeroPaths != null) {
      return zeroPaths;
    }

    JBddEquivalenceClass wrapper = canonicalWrapper(node);

    // Access cached values.
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
      zeroPaths = List.of(EMPTY);
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
        int[] extendedZeroPath = Arrays.copyOf(zeroPath, zeroPath.length + 1);
        extendedZeroPath[zeroPath.length] = -(variable + 1);
        Arrays.sort(extendedZeroPath);
        return extendedZeroPath;
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
          int[] extendedZeroPath = Arrays.copyOf(zeroPath, zeroPath.length + 1);
          extendedZeroPath[zeroPath.length] = variable;
          Arrays.sort(extendedZeroPath);
          lowZeroPaths.add(extendedZeroPath);
        });
      }

    } else {
      // The represented Boolean functions are monotone.
      assert lowImpliesHigh;
      lowZeroPaths.addAll(highZeroPaths);
    }

    zeroPaths = maximalElements(lowZeroPaths.toArray(int[][]::new));
    cache.put(node, zeroPaths);

    // Cache zeroPaths in wrapper.
    if (wrapper != null && wrapper.zeroPathsCache == null) {
      wrapper.zeroPathsCache = zeroPaths;
    }

    return zeroPaths;
  }

  // Warning: This is a performance-sensitive method.
  private static List<int[]> maximalElements(int[][] paths) {
    int removedElements = 0;

    // Remove subsumed elements (including duplicates).
    for (int i = 0; i < paths.length; i++) {
      int[] ei = paths[i];

      if (ei == null) {
        continue;
      }

      for (int j = 0; j < paths.length; j++) {
        if (i == j) {
          continue;
        }

        int[] ej = paths[j];

        if (ej == null) {
          continue;
        }

        if (containsAll(ej, ei)) {
          paths[j] = null;
          removedElements++;
        }
      }
    }

    List<int[]> prunedPaths = new ArrayList<>(paths.length - removedElements);

    for (int[] path : paths) {
      if (path != null) {
        prunedPaths.add(path);
      }
    }

    // Pre-sort for recursive calls.
    prunedPaths.sort(Comparator.comparingInt(x -> x.length));
    return List.copyOf(prunedPaths);
  }

  /**
   * Compute all paths to trueNode.
   */
  private List<int[]> onePaths(int node, Map<Integer, List<int[]>> cache) {

    List<int[]> onePaths = cache.get(node);

    if (onePaths != null) {
      return onePaths;
    }

    JBddEquivalenceClass wrapper = canonicalWrapper(node);

    // Access cached values.
    if (wrapper != null && wrapper.onePathsCache != null) {
      onePaths = wrapper.onePathsCache;
      cache.put(node, onePaths);
      return onePaths;
    }

    if (node == trueNode) {
      onePaths = List.of(EMPTY);
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
        int[] extendedOnePath = Arrays.copyOf(onePath, onePath.length + 1);
        extendedOnePath[onePath.length] = variable;
        Arrays.sort(extendedOnePath);
        return extendedOnePath;
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
          int[] extendedOnePath = Arrays.copyOf(onePath, onePath.length + 1);
          extendedOnePath[onePath.length] = -(variable + 1);
          Arrays.sort(extendedOnePath);
          highOnePaths.add(extendedOnePath);
        });
      }

    } else {
      // The represented Boolean functions are monotone.
      assert lowImpliesHigh;
      highOnePaths.addAll(lowOnePaths);
    }

    onePaths = maximalElements(highOnePaths.toArray(int[][]::new));
    cache.put(node, onePaths);

    // Cache onePaths in wrapper.
    if (wrapper != null && wrapper.onePathsCache == null) {
      wrapper.onePathsCache = onePaths;
    }

    return onePaths;
  }

  private static boolean containsAll(int[] path, int[] otherPath) {
    if (path.length < otherPath.length) {
      return false;
    }

    if (path.length == otherPath.length) {
      return Arrays.equals(path, otherPath);
    }

    // Index in the path list.
    int j = 0;

    for (int i = 0; i < otherPath.length; i++) {
      // There are too many elements left in otherPath, path cannot contain all elements of it.
      if (path.length - j < otherPath.length - i) {
        return false;
      }

      int value = Integer.MIN_VALUE;
      int otherValue = otherPath[i];

      // Search in the sorted list for a matching value
      for (; value < otherValue && j < path.length; j++) {
        value = path[j];
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
    private JBddEquivalenceClass unfoldCache;
    @Nullable
    private JBddEquivalenceClass notCache;
    @Nullable
    private List<int[]> zeroPathsCache;
    @Nullable
    private List<int[]> onePathsCache;
    @Nullable
    private Set<Set<Formula>> cnfView;
    @Nullable
    private Set<Set<Formula>> dnfView;

    @Nullable
    private ImmutableBitSet atomicPropositionsCache;
    @Nullable
    private ImmutableBitSet atomicPropositionsCacheIncludeNested;
    @Nullable
    private Set<TemporalOperator> temporalOperatorsCache;
    @Nullable
    private Set<TemporalOperator> temporalOperatorsCacheIncludeNested;

    private double truenessCache = Double.NaN;

    private JBddEquivalenceClass(JBddEquivalenceClassFactory factory, int node,
      @Nullable Formula internalRepresentative) {
      this.factory = factory;
      this.node = node;
      this.representative = internalRepresentative;
    }

    @Override
    public Set<Set<Formula>> conjunctiveNormalForm() {
      if (cnfView == null) {
        if (zeroPathsCache == null) {
          zeroPathsCache = List.copyOf(factory.zeroPaths(node, new HashMap<>()));
        }

        List<Set<Formula>> clauses = new ArrayList<>(zeroPathsCache.size());
        int atomicPropositions = factory.atomicPropositions.size();
        Formula[] reverseMapping = factory.reverseMapping;

        for (int[] zeroPath : zeroPathsCache) {
          Formula[] clause = new Formula[zeroPath.length];

          for (int j = 0; j < zeroPath.length; j++) {
            int zeroPathNode = zeroPath[j];
            assert zeroPathNode < atomicPropositions
              : "Node encodes non-negated TemporalOperator";

            if (0 <= zeroPathNode) {
              clause[j] = Literal.of(zeroPathNode, true);
            } else {
              int negatedNode = -(zeroPathNode + 1);

              if (negatedNode < atomicPropositions) {
                clause[j] = Literal.of(negatedNode);
              } else {
                clause[j] = reverseMapping[negatedNode - atomicPropositions];
              }
            }
          }

          clauses.add(new DistinctList<>(List.of(clause)));
        }

        cnfView = new DistinctList<>(clauses);
      }

      return Objects.requireNonNull(cnfView);
    }

    @Override
    public Set<Set<Formula>> disjunctiveNormalForm() {
      if (dnfView == null) {
        if (onePathsCache == null) {
          onePathsCache = List.copyOf(factory.onePaths(node, new HashMap<>()));
        }

        List<Set<Formula>> clauses = new ArrayList<>(onePathsCache.size());
        int atomicPropositions = factory.atomicPropositions.size();
        Formula[] reverseMapping = factory.reverseMapping;

        for (int[] onePath : onePathsCache) {
          Formula[] clause = new Formula[onePath.length];

          for (int j = 0; j < onePath.length; j++) {
            int onePathNode = onePath[j];
            assert -(atomicPropositions + 1) <= onePathNode
              : "Node encodes negation of TemporalOperator";

            if (onePathNode < 0) {
              clause[j] = Literal.of(-(onePathNode + 1), true);
            } else if (onePathNode < atomicPropositions) {
              clause[j] = Literal.of(onePathNode);
            } else {
              clause[j] = reverseMapping[onePathNode - atomicPropositions];
            }
          }

          clauses.add(new DistinctList<>(List.of(clause)));
        }

        dnfView = new DistinctList<>(clauses);
      }

      return Objects.requireNonNull(dnfView);
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
        : PrintVisitor.toString(
            LabelledFormula.of(representative, factory.atomicPropositions), false);
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
    public ImmutableBitSet atomicPropositions(boolean includeNested) {
      if (atomicPropositionsCache == null || atomicPropositionsCacheIncludeNested == null) {
        initialiseSupportBasedCaches();
      }

      assert atomicPropositionsCache != null;
      assert atomicPropositionsCacheIncludeNested != null;
      return includeNested ? atomicPropositionsCacheIncludeNested : atomicPropositionsCache;
    }

    @Override
    public Set<TemporalOperator> temporalOperators(boolean includeNested) {
      if (temporalOperatorsCache == null || temporalOperatorsCacheIncludeNested == null) {
        initialiseSupportBasedCaches();
      }

      assert temporalOperatorsCache != null;
      assert temporalOperatorsCacheIncludeNested != null;
      return includeNested ? temporalOperatorsCacheIncludeNested : temporalOperatorsCache;
    }

    private void initialiseSupportBasedCaches() {
      int atomicPropositionsSize = factory.atomicPropositions.size();
      BitSet support = factory.bdd.support(node);

      // Compute atomicPropositions(false)
      BitSet atomicPropositions = BitSet2.copyOf(support);
      if (atomicPropositionsSize < atomicPropositions.length()) {
        atomicPropositions.clear(atomicPropositionsSize, atomicPropositions.length());
      }

      // Compute temporalOperators(false)
      support.clear(0, atomicPropositionsSize);
      TemporalOperator[] temporalOperators = support.stream()
        .mapToObj(i -> factory.reverseMapping[i - atomicPropositionsSize])
        .toArray(TemporalOperator[]::new);

      // Compute atomicPropositions(true), temporalOperators(true)
      BitSet atomicPropositionsIncludeNested = BitSet2.copyOf(atomicPropositions);
      Set<TemporalOperator> temporalOperatorsIncludeNested
        = new HashSet<>(5 * temporalOperators.length);

      for (TemporalOperator temporalOperator : temporalOperators) {
        atomicPropositionsIncludeNested.or(temporalOperator.atomicPropositions(true));
        temporalOperatorsIncludeNested.addAll(temporalOperator.subformulas(TemporalOperator.class));
      }

      assert atomicPropositionsCache == null;
      atomicPropositionsCache = ImmutableBitSet.copyOf(atomicPropositions);

      assert atomicPropositionsCacheIncludeNested == null;
      atomicPropositionsCacheIncludeNested =
        atomicPropositions.equals(atomicPropositionsIncludeNested)
          ? atomicPropositionsCache
          : ImmutableBitSet.copyOf(atomicPropositionsIncludeNested);

      assert temporalOperatorsCache == null;
      temporalOperatorsCache = Set.of(temporalOperators);

      assert temporalOperatorsCacheIncludeNested == null;
      temporalOperatorsCacheIncludeNested =
        temporalOperators.length == temporalOperatorsIncludeNested.size()
          ? temporalOperatorsCache
          : Set.of(temporalOperatorsIncludeNested.toArray(TemporalOperator[]::new));
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
    public <T> MtBdd<T> temporalStepTree(Function<EquivalenceClass, Set<T>> mapper) {
      if (factory.temporalStepTreeCache == null
        || !mapper.equals(factory.temporalStepTreeCachedMapper)) {
        factory.temporalStepTreeCachedMapper = (Function) mapper;
        factory.temporalStepTreeCache = new IdentityHashMap<>();
      }

      return temporalStepTree(representative(), new BitSet(), mapper,
        (IdentityHashMap) factory.temporalStepTreeCache);
    }

    @Override
    public EquivalenceClass not() {
      if (notCache == null) {
        notCache = factory.cast(factory.of(representative().not()));
        assert factory.cast(notCache).notCache == null;
        notCache.notCache = this;
      }

      assert notCache.notCache == this;
      return notCache;
    }

    private <T> MtBdd<T> temporalStepTree(
      Formula initialRepresentative,
      BitSet pathTrace,
      Function<EquivalenceClass, Set<T>> mapper,
      IdentityHashMap<EquivalenceClass, MtBdd<T>> cache) {

      var tree = cache.get(this);

      if (tree != null) {
        return tree;
      }

      var alphabet = factory.atomicPropositions;
      var bdd = factory.bdd;

      int atom = bdd.isNodeRoot(node) ? alphabet.size() : bdd.variable(node);

      if (atom >= alphabet.size()) {
        tree = MtBdd.of(mapper.apply(
          factory.of(initialRepresentative.temporalStep(pathTrace), false)));
      } else {
        pathTrace.set(atom);
        var trueSubTree = factory.of(null, bdd.high(node))
          .temporalStepTree(initialRepresentative, pathTrace, mapper, cache);

        pathTrace.clear(atom, pathTrace.length());
        var falseSubTree = factory.of(null, bdd.low(node))
          .temporalStepTree(initialRepresentative, pathTrace, mapper, cache);

        tree = MtBdd.of(atom, trueSubTree, falseSubTree);
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

  private static final class DistinctList<E> extends AbstractSet<E> {
    private final List<E> elements;

    private DistinctList(List<E> elements) {
      // assert Collections3.isDistinct(this.elements);
      this.elements = List.copyOf(elements);
    }

    @Override
    public Iterator<E> iterator() {
      return elements.iterator();
    }

    @Override
    public int size() {
      return elements.size();
    }

    @Override
    public boolean isEmpty() {
      return elements.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return elements.contains(o);
    }

    @Override
    public Object[] toArray() {
      return elements.toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
      return elements.toArray(array);
    }

    @Override
    public <T> T[] toArray(IntFunction<T[]> generator) {
      return elements.toArray(generator);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
      elements.forEach(action);
    }

    @Override
    public Spliterator<E> spliterator() {
      return Spliterators.spliterator(elements,
        Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL);
    }
  }
}
