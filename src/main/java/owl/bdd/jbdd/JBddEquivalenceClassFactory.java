/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer)
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
import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddConfiguration;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.MtBdd;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Formula.NaryPropositionalOperator;
import owl.ltl.Formula.TemporalOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.PropositionalVisitor;

final class JBddEquivalenceClassFactory
    extends JBddGcManagedFactory<JBddEquivalenceClass>
    implements EquivalenceClassFactory {

  /*
   * Implementation Note:
   *
   * This implementation assumes that usually new instances are created using temporalStepTree() and
   * unfold() and thus caches these results with strong references. Further, it retains strong
   * references to classes created for TemporalOperators and Literals in order to cache their
   * results. Thus in order to avoid memory filling the memory, implementations are advised to
   * drop references to this class and all nested classes as early as possible.
   *
   * Lastly, for constructing new formulas, it seems better to build the upper-half (variables
   * encoding atomic propositions) of the tree by an explicit ITE-construction.
   **/

  private static final List<int[]> LIST_OF_EMPTY = List.of(new int[]{});

  private final List<String> atomicPropositions;
  private final ConversionVisitor visitor;
  private final UnfoldVisitor unfoldVisitor;

  private final int atomicPropositionsVariables;
  private TemporalOperator[] temporalOperatorReverseMapping;

  private final List<JBddEquivalenceClass> literalMapping;

  private final Map<TemporalOperator, JBddEquivalenceClass> temporalOperatorMapping;

  private final int trueNode;
  private final int falseNode;

  private final JBddEquivalenceClass trueClass;
  private final JBddEquivalenceClass falseClass;

  private final Encoding encoding;

  @Nullable
  private final JBddEquivalenceClassFactory reencodingFactory;

  private final Map<Formula.NaryPropositionalOperator, JBddEquivalenceClass> lookupCache
      = new HashMap<>();

  // Sort nodes by their smallest variable (in reverse order).
  //
  // Warning:
  //   This order is important for the performance of the bdd.and(..) and bdd.or(..) calls.
  //   DO NOT CHANGE WITHOUT PERFORMANCE ANALYSIS.
  private final Comparator<JBddEquivalenceClass> nodeComparator = new Comparator<JBddEquivalenceClass>() {
    @Override
    public int compare(JBddEquivalenceClass node1, JBddEquivalenceClass node2) {
      int variable1 = bdd.isNodeRoot(node1.node) ? Integer.MAX_VALUE : bdd.variable(node1.node);
      int variable2 = bdd.isNodeRoot(node2.node) ? Integer.MAX_VALUE : bdd.variable(node2.node);

      return Integer.compare(variable2, variable1);
    }
  };

  JBddEquivalenceClassFactory(List<String> atomicPropositions, Encoding encoding) {
    super(createBdd(atomicPropositions.size()), true);

    this.atomicPropositions = List.copyOf(atomicPropositions);

    temporalOperatorMapping = new HashMap<>();
    temporalOperatorReverseMapping = new TemporalOperator[32];
    visitor = new ConversionVisitor();
    unfoldVisitor = new UnfoldVisitor();

    this.encoding = encoding;

    // Register literals.
    //  0 -> 0
    // !0 -> 1 (If AP_COMBINED is selected, then 1 is not unused and !0 -> !0)
    //  1 -> 2
    // !1 -> 3 ...
    this.atomicPropositionsVariables = 2 * this.atomicPropositions.size();
    this.bdd.createVariables(atomicPropositionsVariables);

    JBddEquivalenceClass[] literalMapping = new JBddEquivalenceClass[atomicPropositionsVariables];

    for (int i = 0; i < atomicPropositionsVariables; i++) {
      Literal literal = Literal.of(i / 2, i % 2 == 1);

      assert i == (2 * literal.getAtom()) + (literal.isNegated() ? 1 : 0);

      if (encoding == Encoding.AP_COMBINED) {
        int node = bdd.variableNode(2 * literal.getAtom());
        literalMapping[i] = of(literal, literal.isNegated() ? bdd.not(node) : node);
      } else {
        assert encoding == Encoding.AP_SEPARATE;
        int variable = literal.isNegated() ? 2 * literal.getAtom() + 1 : 2 * literal.getAtom();
        Preconditions.checkArgument(variable < atomicPropositionsVariables);
        literalMapping[i] = of(literal, bdd.variableNode(variable));
      }
    }

    this.literalMapping = List.of(literalMapping);

    if (this.encoding == Encoding.AP_SEPARATE) {
      reencodingFactory = new JBddEquivalenceClassFactory(atomicPropositions, Encoding.AP_COMBINED);
    } else {
      assert this.encoding == Encoding.AP_COMBINED;
      reencodingFactory = null;
    }

    trueNode = this.bdd.trueNode();
    falseNode = this.bdd.falseNode();

    trueClass = of(BooleanConstant.TRUE, trueNode);
    falseClass = of(BooleanConstant.FALSE, falseNode);
  }

  static Bdd createBdd(int atomicPropositionsSize) {
    int size = 1024 * (atomicPropositionsSize + 1);

    // Garbage collection is disabled, since it is triggered too frequently and has an adverse
    // impact on the runtime.
    BddConfiguration configuration = ImmutableBddConfiguration.builder()
        .logStatisticsOnShutdown(false)
        .useGlobalComposeCache(false)
        .useGarbageCollection(false)
        .cacheBinaryDivider(8)
        .cacheTernaryDivider(8)
        .growthFactor(4)
        .build();

    // Do not use buildBddIterative, since 'support(...)' is broken.
    return BddFactory.buildBddRecursive(size, configuration);
  }

  @Override
  public Encoding defaultEncoding() {
    return encoding;
  }

  @Override
  public void clearCaches() {
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
  public JBddEquivalenceClass of(boolean value) {
    return value ? trueClass : falseClass;
  }

  public JBddEquivalenceClass of(BooleanConstant booleanConstant) {
    return booleanConstant.value ? trueClass : falseClass;
  }

  public JBddEquivalenceClass of(Literal literal) {
    int i = 2 * literal.getAtom() + (literal.isNegated() ? 1 : 0);
    return literalMapping.get(i);
  }

  @Override
  public JBddEquivalenceClass of(Formula formula) {
    return of(formula, true);
  }

  private JBddEquivalenceClass of(Formula formula, boolean scanForUnknown) {
    if (scanForUnknown) {
      // Scan for unknown modal operators.
      var newPropositions = formula.subformulas(TemporalOperator.class)
          .stream()
          .filter(y -> !temporalOperatorMapping.containsKey(y))
          .sorted()
          .toList();

      if (!newPropositions.isEmpty()) {
        // Create variables.
        int newSize = temporalOperatorMapping.size() + newPropositions.size();

        if (temporalOperatorReverseMapping.length < newSize) {
          temporalOperatorReverseMapping = Arrays.copyOf(temporalOperatorReverseMapping, newSize);
        }

        for (TemporalOperator proposition : newPropositions) {
          int variableNode = bdd.createVariable();
          temporalOperatorMapping.put(proposition, of(proposition, variableNode));
          temporalOperatorReverseMapping
              [bdd.variable(variableNode) - atomicPropositionsVariables] = proposition;
        }
      }
    }

    return encoding == Encoding.AP_COMBINED && !formula.atomicPropositions(false).isEmpty()
        ? viaIte(formula)
        : formula.accept(visitor);
  }

  private JBddEquivalenceClass viaIte(Formula formula) {
    if (formula instanceof BooleanConstant booleanConstant) {
      return of(booleanConstant.value);
    }

    if (formula instanceof Literal literal) {
      return of(literal);
    }

    if (formula instanceof TemporalOperator temporalOperator) {
      return temporalOperatorMapping.get(temporalOperator);
    }

    var cachedClass = lookupCache.get((NaryPropositionalOperator) formula);

    if (cachedClass != null) {
      return cachedClass;
    }

    var atomicPropositions = formula.atomicPropositions(false);

    if (atomicPropositions.isEmpty()) {
      return of(formula, false);
    }

    int variable = atomicPropositions.nextSetBit(0);

    abstract class LiteralSubstitution extends PropositionalVisitor<Formula> {

      @Override
      public Formula visit(BooleanConstant booleanConstant) {
        return booleanConstant;
      }

      @Override
      public Formula visit(Conjunction conjunction) {
        return Conjunction.of(conjunction.map(x -> x.accept(this)));
      }

      @Override
      public Formula visit(Disjunction disjunction) {
        return Disjunction.of(disjunction.map(x -> x.accept(this)));
      }

      @Override
      protected Formula visit(TemporalOperator formula) {
        return formula;
      }
    }

    var trueFormula = formula.accept(new LiteralSubstitution() {
      @Override
      public Formula visit(Literal literal) {
        return literal.getAtom() == variable ? BooleanConstant.of(!literal.isNegated()) : literal;
      }
    });

    var falseFormula = formula.accept(new LiteralSubstitution() {
      @Override
      public Formula visit(Literal literal) {
        return literal.getAtom() == variable ? BooleanConstant.of(literal.isNegated()) : literal;
      }
    });

    return of(formula, bdd.ifThenElse(
        bdd.variableNode(2 * variable),
        viaIte(trueFormula).node,
        viaIte(falseFormula).node));
  }

  private JBddEquivalenceClass of(@Nullable Formula representative, int node) {
    var clazz = canonicalWrapper(node);

    if (clazz == null) {
      clazz = canonicalize(new JBddEquivalenceClass(this, node, representative));
    }

    if (representative instanceof NaryPropositionalOperator propositionalOperator) {
      lookupCache.put(propositionalOperator, clazz);

      if (clazz.representative == null) {
        clazz.representative = propositionalOperator;
      }
    } else if (representative != null) {
      assert representative instanceof BooleanConstant
          || representative instanceof Literal
          || representative instanceof TemporalOperator;
      assert representative.equals(clazz.representative);
    }

    return clazz;
  }

  @Override
  public EquivalenceClass and(Collection<? extends EquivalenceClass> classes) {
    return andInternal(classes.toArray(JBddEquivalenceClass[]::new));
  }

  private JBddEquivalenceClass andInternal(JBddEquivalenceClass[] classes) {

    Arrays.sort(classes, nodeComparator);
    List<Formula> representatives = new ArrayList<>(classes.length);
    int andNode = trueNode;

    for (JBddEquivalenceClass clazz : classes) {
      Preconditions.checkArgument(clazz.factory == this);
      andNode = bdd.updateWith(bdd.and(andNode, clazz.node), andNode);

      if (andNode == falseNode) {
        return falseClass;
      }

      representatives.add(clazz.representative);
    }

    // bdd.dereference
    return of(Conjunction.of(representatives), andNode);
  }

  @Override
  public EquivalenceClass or(Collection<? extends EquivalenceClass> classes) {
    return orInternal(classes.toArray(JBddEquivalenceClass[]::new));
  }

  private JBddEquivalenceClass orInternal(JBddEquivalenceClass[] classes) {

    Arrays.sort(classes, nodeComparator);
    List<Formula> representatives = new ArrayList<>(classes.length);
    int orNode = falseNode;

    for (JBddEquivalenceClass clazz : classes) {
      Preconditions.checkArgument(clazz.factory == this);
      orNode = bdd.updateWith(bdd.or(orNode, clazz.node), orNode);

      if (orNode == trueNode) {
        return trueClass;
      }

      representatives.add(clazz.representative);
    }

    // bdd.dereference
    return of(Disjunction.of(representatives), orNode);
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
   * This class does not implement `equals` and `hashCode`, since GcManagedFactory ensures
   * uniqueness.
   */
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
    private Set<TemporalOperator> temporalOperatorsCache;
    @Nullable
    private Set<TemporalOperator> temporalOperatorsCacheIncludeNested;
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
      if (supportCache == null) {
        initialiseSupportCaches();
      }

      return Objects.requireNonNull(
          includeNested ? supportCacheIncludeNested : supportCache);
    }

    @Override
    public Set<TemporalOperator> temporalOperators(boolean includeNested) {
      if (supportCache == null) {
        initialiseSupportCaches();
      }

      return Objects.requireNonNull(
          includeNested ? temporalOperatorsCacheIncludeNested : temporalOperatorsCache);
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

      // Compute temporalOperators(false)
      {
        int firstTemporalOperator = 0;
        int s = supportCache.size();

        while (firstTemporalOperator < s
            && supportCache.get(firstTemporalOperator) instanceof Literal) {
          firstTemporalOperator++;
        }

        assert temporalOperatorsCache == null;
        @SuppressWarnings("unchecked")
        List<TemporalOperator> castedSublist
            = (List) supportCache.subList(firstTemporalOperator, s);
        temporalOperatorsCache
            = Set.of(castedSublist.toArray(TemporalOperator[]::new));
      }

      // Compute temporalOperators(true)
      {
        int firstTemporalOperator = 0;
        int s = supportCacheIncludeNested.size();

        while (firstTemporalOperator < s
            && supportCacheIncludeNested.get(firstTemporalOperator) instanceof Literal) {
          firstTemporalOperator++;
        }

        assert temporalOperatorsCacheIncludeNested == null;
        @SuppressWarnings("unchecked")
        List<TemporalOperator> castedSublist
            = (List) supportCacheIncludeNested.subList(firstTemporalOperator, s);
        temporalOperatorsCacheIncludeNested
            = Set.of(castedSublist.toArray(TemporalOperator[]::new));
      }
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
      int atom = bdd.isNodeRoot(node) ? factory.atomicPropositionsVariables : bdd.variable(node);

      if (atom >= factory.atomicPropositionsVariables) {
        temporalStepTreeCache = MtBdd.of(
            factory.of(initialRepresentative.temporalStep(pathTrace), false));
      } else {
        int atomicProposition;
        int trueSubTreeNode;
        int falseSubTreeNode;

        // Walk the BDD in diamond shape.
        if (atom % 2 == 0) {
          atomicProposition = atom / 2;

          trueSubTreeNode = bdd.high(node);
          if (!bdd.isNodeRoot(trueSubTreeNode) && bdd.variable(trueSubTreeNode) == atom + 1) {
            trueSubTreeNode = bdd.low(trueSubTreeNode);
          }

          falseSubTreeNode = bdd.low(node);
          if (!bdd.isNodeRoot(falseSubTreeNode) && bdd.variable(falseSubTreeNode) == atom + 1) {
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
    public JBddEquivalenceClass unfold() {
      if (unfoldCache == null) {
        // If the representative is a Boolean formula than we use a Visitor to combine it from
        // existing EquivalanceClasses. If the representative is a temporal operator we compute we
        // construct a new EquivalenceClass.
        unfoldCache = representative instanceof TemporalOperator temporalOperator
            ? factory.of(temporalOperator.unfold(), false)
            : representative().accept(factory.unfoldVisitor);

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

  // Translates a formula into a BDD under the assumption every subformula is already registered.
  private final class ConversionVisitor extends PropositionalVisitor<JBddEquivalenceClass> {

    @Override
    public JBddEquivalenceClass visit(BooleanConstant booleanConstant) {
      return of(booleanConstant);
    }

    @Override
    public JBddEquivalenceClass visit(Conjunction conjunction) {
      var cache = lookupCache.get(conjunction);

      if (cache != null) {
        return cache;
      }

      var disjuncts = conjunction.operands;
      int s = disjuncts.size();
      var nodes = new JBddEquivalenceClass[s];

      for (int i = 0; i < s; i++) {
        nodes[i] = disjuncts.get(i).accept(this);
      }

      return andInternal(nodes);
    }

    @Override
    public JBddEquivalenceClass visit(Disjunction disjunction) {
      var cache = lookupCache.get(disjunction);

      if (cache != null) {
        return cache;
      }

      var disjuncts = disjunction.operands;
      int s = disjuncts.size();
      var nodes = new JBddEquivalenceClass[s];

      for (int i = 0; i < s; i++) {
        nodes[i] = disjuncts.get(i).accept(this);
      }

      return orInternal(nodes);
    }

    @Override
    public JBddEquivalenceClass visit(Literal literal) {
      return of(literal);
    }

    @Override
    protected JBddEquivalenceClass visit(TemporalOperator formula) {
      return temporalOperatorMapping.get(formula);
    }
  }

  private final class UnfoldVisitor extends PropositionalVisitor<JBddEquivalenceClass> {

    @Override
    public JBddEquivalenceClass visit(BooleanConstant booleanConstant) {
      return of(booleanConstant.value);
    }

    @Override
    public JBddEquivalenceClass visit(Conjunction conjunction) {
      var conjuncts = conjunction.operands;
      var nodes = new JBddEquivalenceClass[conjuncts.size()];

      for (int i = 0; i < nodes.length; i++) {
        nodes[i] = conjuncts.get(i).accept(this);
      }

      return JBddEquivalenceClassFactory.this.andInternal(nodes);
    }

    @Override
    public JBddEquivalenceClass visit(Disjunction disjunction) {
      var disjuncts = disjunction.operands;
      var nodes = new JBddEquivalenceClass[disjuncts.size()];

      for (int i = 0; i < nodes.length; i++) {
        nodes[i] = disjuncts.get(i).accept(this);
      }

      return JBddEquivalenceClassFactory.this.orInternal(nodes);
    }

    @Override
    public JBddEquivalenceClass visit(Literal literal) {
      return of(literal);
    }

    @Override
    protected JBddEquivalenceClass visit(TemporalOperator temporalOperator) {
      return temporalOperatorMapping.get(temporalOperator).unfold();
    }
  }
}
