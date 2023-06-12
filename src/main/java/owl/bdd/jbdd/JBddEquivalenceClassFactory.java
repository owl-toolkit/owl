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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.MtBdd;
import owl.ltl.*;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.PropositionalIntVisitor;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static owl.bdd.jbdd.JBddEquivalenceClassFactory.JBddEquivalenceClass;

final class JBddEquivalenceClassFactory extends JBddGcManagedFactory<JBddEquivalenceClass>
  implements EquivalenceClassFactory {

  private static final List<int[]> LIST_OF_EMPTY = List.of(new int[]{});

  private final List<String> atomicPropositions;
  private final JBddVisitor visitor;

  private final int atomicPropositionsVariables;
  private TemporalOperator[] temporalOperatorReverseMapping;
  private final Map<TemporalOperator, Integer> temporalOperatorMapping;

  private final int trueNode;
  private final int falseNode;

  private final EquivalenceClass trueClass;
  private final EquivalenceClass falseClass;

  private final Encoding encoding;

  @Nullable
  private final JBddEquivalenceClassFactory reencodingFactory;

  private final List<EquivalenceClass> protectFromGc = new ArrayList<>();
  private final Map<Formula, JBddEquivalenceClass> lookupCache = new HashMap<>();

  JBddEquivalenceClassFactory(Bdd bdd, List<String> atomicPropositions, Encoding encoding) {
    super(bdd);

    this.atomicPropositions = List.copyOf(atomicPropositions);

    temporalOperatorMapping = new HashMap<>();
    temporalOperatorReverseMapping = new TemporalOperator[32];
    visitor = new JBddVisitor();

    this.encoding = encoding;

    // Register literals.
    //  0 -> 0
    // !0 -> 1 (If AP_COMBINED is selected, then 1 is not unused and !0 -> !0)
    //  1 -> 2
    // !1 -> 3 ...
    this.atomicPropositionsVariables = 2 * this.atomicPropositions.size();
    this.bdd.createVariables(atomicPropositionsVariables);

    if (this.encoding == Encoding.AP_SEPARATE) {
        reencodingFactory = new JBddEquivalenceClassFactory(
                BddFactory.buildBdd(ImmutableBddConfiguration.builder().initialSize(32_000).build()), atomicPropositions, Encoding.AP_COMBINED);
    } else {
      assert this.encoding == Encoding.AP_COMBINED;
      reencodingFactory = null;
    }

    trueNode = this.bdd.trueNode();
    falseNode = this.bdd.falseNode();

    trueClass = of(BooleanConstant.TRUE, trueNode);
    falseClass = of(BooleanConstant.FALSE, falseNode);
  }

  @Override
  public Encoding defaultEncoding() {
    return encoding;
  }

  @Override
  public void clearCaches() {
    protectFromGc.clear();
    lookupCache.clear();
  }

  @Override
  public EquivalenceClassFactory withDefaultEncoding(Encoding encoding) {
    if (encoding == this.encoding) {
      return this;
    }

    if (encoding == Encoding.AP_SEPARATE) {
      assert this.encoding == Encoding.AP_COMBINED;
      throw new IllegalArgumentException("Cannot switch into a coarser encoding.");
    }

    assert encoding == Encoding.AP_COMBINED;
    assert this.encoding == Encoding.AP_SEPARATE;
    return Objects.requireNonNull(reencodingFactory);
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
    var clazz = lookupCache.get(formula);

    if (clazz != null) {
      return clazz;
    }

    if (scanForUnknown) {
      // Scan for unknown modal operators.
      var newPropositions = formula.subformulas(TemporalOperator.class)
        .stream()
        .filter(y -> !temporalOperatorMapping.containsKey(y))
        .collect(Collectors.toCollection(TreeSet::new));

      if (!newPropositions.isEmpty()) {
        // Create variables.
        int newSize = temporalOperatorMapping.size() + newPropositions.size();

        if (temporalOperatorReverseMapping.length < newSize) {
          temporalOperatorReverseMapping = Arrays.copyOf(temporalOperatorReverseMapping, newSize);
        }

        for (TemporalOperator proposition : newPropositions) {
          int variable = bdd.variable(bdd.createVariable());
          temporalOperatorMapping.put(proposition, variable);
          temporalOperatorReverseMapping[variable - atomicPropositionsVariables] = proposition;
        }
      }
    }

    return of(formula, bdd.dereference(formula.accept(visitor)));
  }

  private JBddEquivalenceClass of(@Nullable Formula representative, int node) {
    var clazz = canonicalize(new JBddEquivalenceClass(this, node, representative));

    // We cache all returned instances with strong references. The caches can be cleared by using
    // the clear caches method.
    if (representative != null) {
      lookupCache.put(representative, clazz);

      if (clazz.representative == null) {
        clazz.representative = representative;
      }
    } else if (clazz.representative == null) {
      protectFromGc.add(clazz);
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
      if (encoding == Encoding.AP_COMBINED) {
        Preconditions.checkArgument(literal.getAtom() < atomicPropositions.size());
        int node = bdd.variableNode(2 * literal.getAtom());
        return literal.isNegated() ? bdd.not(node) : node;
      } else {
        assert encoding == Encoding.AP_SEPARATE;
        int variable = literal.isNegated() ? 2 * literal.getAtom() + 1 : 2 * literal.getAtom();
        Preconditions.checkArgument(variable < atomicPropositionsVariables);
        return bdd.variableNode(variable);
      }
    }

    @Override
    protected int visit(TemporalOperator formula) {
      int variable = temporalOperatorMapping.get(formula);
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
  private List<int[]> zeroPaths(int node) {

    if (node == trueNode) {
      return List.of();
    }

    if (node == falseNode) {
      return LIST_OF_EMPTY;
    }

    JBddEquivalenceClass wrapper = of(null, node);

    // Access cached values.
    if (wrapper.zeroPathsCache != null) {
      return wrapper.zeroPathsCache;
    }

    int variable = bdd.variable(node);
    int highNode = bdd.high(node);
    int lowNode = bdd.low(node);

    var highZeroPaths = zeroPaths(highNode);
    var lowZeroPaths = new ArrayList<>(zeroPaths(lowNode));

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

    if (encoding == Encoding.AP_COMBINED && variable < atomicPropositionsVariables) {
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

    var zeroPaths = maximalElements(lowZeroPaths.toArray(int[][]::new));

    // Cache zeroPaths in wrapper.
    if (wrapper.zeroPathsCache == null) {
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
  private List<int[]> onePaths(int node) {

    if (node == trueNode) {
      return LIST_OF_EMPTY;
    }

    if (node == falseNode) {
      return List.of();
    }

    JBddEquivalenceClass wrapper = of(null, node);

    // Access cached values.
    if (wrapper.onePathsCache != null) {
      return wrapper.onePathsCache;
    }

    int variable = bdd.variable(node);
    int highNode = bdd.high(node);
    int lowNode = bdd.low(node);

    var highOnePaths = new ArrayList<>(onePaths(highNode));
    var lowOnePaths = onePaths(lowNode);

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

    if (encoding == Encoding.AP_COMBINED && variable < atomicPropositionsVariables) {
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

    var onePaths = maximalElements(highOnePaths.toArray(int[][]::new));

    // Cache onePaths in wrapper.
    if (wrapper.onePathsCache == null) {
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
    private MtBdd<EquivalenceClass> temporalStepTreeCache;
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
    private List<Formula> supportCache;
    @Nullable
    private List<Formula> supportCacheIncludeNested;
    @Nullable
    private EquivalenceClass encodeCache;

    private double truenessCache = Double.NaN;

    private JBddEquivalenceClass(JBddEquivalenceClassFactory factory, int node,
      @Nullable Formula internalRepresentative) {
      this.factory = factory;
      this.node = node;
      this.representative = internalRepresentative;
    }

    @Override
    public Encoding encoding() {
      return factory.encoding;
    }

    @Override
    public EquivalenceClass encode(Encoding encoding) {
      if (factory.encoding == encoding) {
        return this;
      }

      if (factory.encoding == Encoding.AP_COMBINED) {
        assert encoding == Encoding.AP_SEPARATE;
        throw new IllegalArgumentException("Cannot encode into a coarser encoding.");
      }

      assert factory.encoding == Encoding.AP_SEPARATE;
      assert encoding == Encoding.AP_COMBINED;

      if (encodeCache == null) {
        encodeCache = factory.reencodingFactory.of(representative());
      }

      return Objects.requireNonNull(encodeCache);
    }

    @Override
    public Set<Set<Formula>> conjunctiveNormalForm() {
      if (cnfView == null) {
        if (zeroPathsCache == null) {
          zeroPathsCache = List.copyOf(factory.zeroPaths(node));
        }

        List<Set<Formula>> clauses = new ArrayList<>(zeroPathsCache.size());
        int atomicPropositionsVariables = factory.atomicPropositionsVariables;
        TemporalOperator[] reverseMapping = factory.temporalOperatorReverseMapping;

        for (int[] zeroPath : zeroPathsCache) {
          Formula[] clause = new Formula[zeroPath.length];

          for (int j = 0; j < zeroPath.length; j++) {
            int zeroPathVariable = zeroPath[j];
            assert zeroPathVariable < atomicPropositionsVariables
              : "Node encodes non-negated TemporalOperator";

            if (0 <= zeroPathVariable) {
              assert factory.encoding == Encoding.AP_COMBINED;
              assert zeroPathVariable % 2 == 0;
              clause[j] = Literal.of(zeroPathVariable / 2, true);
            } else {
              int negatedVariable = -(zeroPathVariable + 1);

              if (negatedVariable < atomicPropositionsVariables) {
                if (negatedVariable % 2 == 0) {
                  clause[j] = Literal.of(negatedVariable / 2, false);
                } else {
                  assert factory.encoding == Encoding.AP_SEPARATE;
                  clause[j] = Literal.of((negatedVariable - 1) / 2, true);
                }
              } else {
                clause[j] = reverseMapping[negatedVariable - atomicPropositionsVariables];
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
          onePathsCache = List.copyOf(factory.onePaths(node));
        }

        List<Set<Formula>> clauses = new ArrayList<>(onePathsCache.size());
        int atomicPropositionsVariables = factory.atomicPropositionsVariables;
        TemporalOperator[] reverseMapping = factory.temporalOperatorReverseMapping;

        for (int[] onePath : onePathsCache) {
          Formula[] clause = new Formula[onePath.length];

          for (int j = 0; j < onePath.length; j++) {
            int onePathVariable = onePath[j];
            assert -(atomicPropositionsVariables + 1) <= onePathVariable
              : "Node encodes negation of TemporalOperator";

            if (onePathVariable < 0) {
              assert factory.encoding == Encoding.AP_COMBINED;
              assert (-(onePathVariable + 1)) % 2 == 0;
              clause[j] = Literal.of((-(onePathVariable + 1)) / 2, true);
            } else if (onePathVariable < atomicPropositionsVariables) {

              if (onePathVariable % 2 == 0) {
                clause[j] = Literal.of(onePathVariable / 2, false);
              } else {
                assert factory.encoding == Encoding.AP_SEPARATE;
                clause[j] = Literal.of((onePathVariable - 1) / 2, true);
              }

            } else {
              clause[j] = reverseMapping[onePathVariable - atomicPropositionsVariables];
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
    public List<Formula> support(boolean includeNested) {
      if (supportCache == null || supportCacheIncludeNested == null) {
        initialiseSupportCaches();
      }

      return Objects.requireNonNull(includeNested ? supportCacheIncludeNested : supportCache);
    }

    private void initialiseSupportCaches() {
      int atomicPropositionsRegion = factory.atomicPropositionsVariables;
      BitSet supportBitSet = factory.bdd.support(node);

      // Compute support(false)
      Formula[] support = new Formula[supportBitSet.cardinality()];
      int j = 0;

      for (int i = supportBitSet.nextSetBit(0); i >= 0; i = supportBitSet.nextSetBit(i + 1)) {

        if (i < atomicPropositionsRegion) {
          assert factory.encoding == Encoding.AP_SEPARATE || i % 2 == 0;
          support[j] = i % 2 == 0
            ? Literal.of(i / 2, false)
            : Literal.of((i - 1) / 2, true);
        } else {
          support[j] = factory.temporalOperatorReverseMapping[i - atomicPropositionsRegion];
        }

        j++;

        if (i == Integer.MAX_VALUE) {
          throw new IllegalStateException("Support is too large.");
        }
      }

      Arrays.sort(support);

      assert supportCache == null;
      supportCache = List.of(support);

      // Compute support(true)
      Set<Formula> supportIncludeNested = new TreeSet<>(Formula::compareTo);

      for (Formula formula : support) {
        supportIncludeNested.addAll(
          formula.subformulas(x -> x instanceof Literal || x instanceof TemporalOperator));
      }

      if (factory.encoding == Encoding.AP_COMBINED) {
        Set<Literal> negatedLiterals = new HashSet<>();

        supportIncludeNested.removeIf(x -> {
          if (x instanceof Literal && ((Literal) x).isNegated()) {
            negatedLiterals.add((Literal) x);
            return true;
          }

          return false;
        });

        for (Literal negatedLiteral : negatedLiterals) {
          supportIncludeNested.add(Literal.of(negatedLiteral.getAtom()));
        }
      }

      assert supportCacheIncludeNested == null;
      supportCacheIncludeNested = supportIncludeNested.size() == supportCache.size()
        ? supportCache
        : List.copyOf(supportIncludeNested);
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

      var newRepresentative = representative().substitute(substitution);

      if (newRepresentative.equals(representative)) {
        return this;
      }

      return factory.of(newRepresentative, true);
    }

    @Override
    public MtBdd<EquivalenceClass> temporalStepTree() {
      if (temporalStepTreeCache == null) {
        temporalStepTree(representative(), new BitSet());
      }

      return Objects.requireNonNull(temporalStepTreeCache);
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

    private MtBdd<EquivalenceClass> temporalStepTree(
      Formula initialRepresentative, BitSet pathTrace) {

      if (temporalStepTreeCache != null) {
        return temporalStepTreeCache;
      }

      var bdd = factory.bdd;
      int atom = bdd.isNodeLeaf(node) ? factory.atomicPropositionsVariables : bdd.variable(node);

      if (atom >= factory.atomicPropositionsVariables) {
        temporalStepTreeCache = MtBdd.of(Set.of(
          factory.of(initialRepresentative.temporalStep(pathTrace), false)));
      } else {
        int atomicProposition;
        int trueSubTreeNode;
        int falseSubTreeNode;

        // Walk the BDD in diamond shape.
        if (atom % 2 == 0) {
          atomicProposition = atom / 2;

          trueSubTreeNode = bdd.high(node);
          if (!bdd.isNodeLeaf(trueSubTreeNode) && bdd.variable(trueSubTreeNode) == atom + 1) {
            trueSubTreeNode = bdd.low(trueSubTreeNode);
          }

          falseSubTreeNode = bdd.low(node);
          if (!bdd.isNodeLeaf(falseSubTreeNode) && bdd.variable(falseSubTreeNode) == atom + 1) {
            falseSubTreeNode = bdd.high(falseSubTreeNode);
          }
        } else {
          assert factory.encoding == Encoding.AP_SEPARATE;
          atomicProposition = (atom - 1) / 2;
          trueSubTreeNode = bdd.low(node);
          falseSubTreeNode = bdd.high(node);
        }

        pathTrace.set(atomicProposition);
        var trueSubTree = factory.of(null, trueSubTreeNode)
          .temporalStepTree(initialRepresentative, pathTrace);

        pathTrace.clear(atomicProposition, pathTrace.length());
        var falseSubTree = factory.of(null, falseSubTreeNode)
          .temporalStepTree(initialRepresentative, pathTrace);

        temporalStepTreeCache = MtBdd.of(atomicProposition, trueSubTree, falseSubTree);
      }

      return Objects.requireNonNull(temporalStepTreeCache);
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

    // We cannot use this if we use re-encoding of BDDs.
    // @Override
    // public boolean equals(Object obj) {
    //   // Check that we are not comparing classes of different factories
    //   assert !(obj instanceof EquivalenceClass)
    //     || ((EquivalenceClass) obj).factory() == factory();
    //   return this == obj;
    // }
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
