package owl.bdd;

import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hamcrest.number.IsCloseTo;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * Tests various logical functions of BDDs and checks invariants.
 */
@RunWith(Theories.class)
@SuppressWarnings({"PMD.GodClass", "checkstyle:javadoc"})
public class BddTheories {
  private static final BddImpl bdd = new BddImpl(10);
  private static final int binaryCount = 10000;
  private static final Collection<BinaryDataPoint> binaryDataPoints;
  private static final int initialNodeCount;
  private static final int initialReferencedNodeCount;
  private static final Logger logger = Logger.getLogger(BddTheories.class.getName());
  private static final Int2ObjectMap<SyntaxTree> syntaxTreeMap;
  private static final int ternaryCount = 10000;
  private static final Collection<TernaryDataPoint> ternaryDataPoints;
  private static final int treeDepth = 15;
  private static final int treeWidth = 2000;
  private static final int unaryCount = 10000;
  /* The @DataPoints annotated method is called multiple times - which would create new variables
   * each time, exploding the runtime of the tests. */
  private static final Collection<UnaryDataPoint> unaryDataPoints;
  private static final List<BitSet> valuations;
  private static final int variableCount = 10;
  private static final IntList variableList;

  static {
    // It is important other the generation of data is ordered for the tests to be reproducible.

    final Random filter = new Random(0L);
    variableList = new IntArrayList(variableCount);
    for (int i = 0; i < variableCount; i++) {
      variableList.add(bdd.createVariable());
    }

    syntaxTreeMap = new Int2ObjectLinkedOpenHashMap<>();
    syntaxTreeMap.put(bdd.getFalseNode(), SyntaxTree.constant(false));
    syntaxTreeMap.put(bdd.getTrueNode(), SyntaxTree.constant(true));
    for (int i = 0; i < variableList.size(); i++) {
      final SyntaxTree literal = SyntaxTree.literal(i);
      syntaxTreeMap.put(variableList.get(i), literal);
      syntaxTreeMap.put(bdd.reference(bdd.not(variableList.get(i))), SyntaxTree.not(literal));
    }

    // Although a set would be more suitable, a list is needed so that the data is always generated
    // in the same order.
    final Object2IntMap<SyntaxTree> treeToNodeMap = new Object2IntLinkedOpenHashMap<>();
    final List<SyntaxTree> previousDepthNodes = new LinkedList<>();

    // Syntax tree map is a linked map, hence entry set is sorted.
    for (final Map.Entry<Integer, SyntaxTree> treeEntry : syntaxTreeMap.entrySet()) {
      treeToNodeMap.put(treeEntry.getValue(), treeEntry.getKey());
      previousDepthNodes.add(treeEntry.getValue());
    }
    final List<SyntaxTree> candidates = new ArrayList<>();
    logger.log(Level.FINER, "Generating syntax trees from {0} base expressions",
      previousDepthNodes.size());
    for (int depth = 1; depth < treeDepth; depth++) {
      logger.log(Level.FINEST, "Building tree depth {0}", depth);

      candidates.addAll(previousDepthNodes);
      previousDepthNodes.clear();
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      final Int2ObjectMap<SyntaxTree> createdFromCandidates = new Int2ObjectLinkedOpenHashMap<>();
      Collections.shuffle(candidates, filter);
      final List<SyntaxTree> leftCandidates = ImmutableList.copyOf(candidates);
      Collections.shuffle(candidates, filter);
      final List<SyntaxTree> rightCandidates = ImmutableList.copyOf(candidates);
      candidates.clear();

      for (final SyntaxTree left : leftCandidates) {
        for (final SyntaxTree right : rightCandidates) {
          int pushedNodes = 0;
          if (filter.nextBoolean()) {
            createdFromCandidates.put(bdd.pushToWorkStack(bdd.and(treeToNodeMap.getInt(left),
              treeToNodeMap.getInt(right))), SyntaxTree.and(left, right));
            pushedNodes += 1;
          }
          if (filter.nextBoolean()) {
            createdFromCandidates.put(bdd.pushToWorkStack(bdd.or(treeToNodeMap.getInt(left),
              treeToNodeMap.getInt(right))), SyntaxTree.or(left, right));
            pushedNodes += 1;
          }
          if (filter.nextBoolean()) {
            createdFromCandidates.put(bdd.pushToWorkStack(bdd.xor(treeToNodeMap.getInt(left),
              treeToNodeMap.getInt(right))), SyntaxTree.xor(left, right));
            pushedNodes += 1;
          }
          if (filter.nextBoolean()) {
            createdFromCandidates.put(bdd.pushToWorkStack(
              bdd.implication(treeToNodeMap.getInt(left), treeToNodeMap.getInt(right))),
              SyntaxTree.implication(left, right));
            pushedNodes += 1;
          }
          if (filter.nextBoolean()) {
            createdFromCandidates.put(bdd.pushToWorkStack(
              bdd.equivalence(treeToNodeMap.getInt(left), treeToNodeMap.getInt(right))),
              SyntaxTree.equivalence(left, right));
            pushedNodes += 1;
          }
          if (filter.nextBoolean()) {
            createdFromCandidates.put(bdd.pushToWorkStack(bdd.not(treeToNodeMap.getInt(left))),
              SyntaxTree.not(left));
            pushedNodes += 1;
          }
          if (filter.nextBoolean()) {
            createdFromCandidates.put(bdd.pushToWorkStack(bdd.not(treeToNodeMap.getInt(right))),
              SyntaxTree.not(right));
            pushedNodes += 1;
          }
          bdd.popWorkStack(pushedNodes);
          assertTrue(bdd.isWorkStackEmpty());
          createdFromCandidates.forEach((node, tree) -> {
            if (syntaxTreeMap.containsKey(node)) {
              return;
            }
            bdd.reference(node);
            treeToNodeMap.put(tree, node);
            syntaxTreeMap.put(node, tree);
            previousDepthNodes.add(tree);
          });
          createdFromCandidates.clear();
          if (treeToNodeMap.size() >= treeWidth * depth) {
            break;
          }
        }
        if (treeToNodeMap.size() >= treeWidth * depth) {
          break;
        }
      }
    }
    assertThat(bdd.numberOfVariables(), is(variableCount));
    initialNodeCount = bdd.nodeCount();
    initialReferencedNodeCount = bdd.referencedNodeCount();

    final Collection<UnaryDataPoint> unaryDataPointSet = new HashSet<>();
    final Collection<BinaryDataPoint> binaryDataPointSet = new HashSet<>();
    final Collection<TernaryDataPoint> ternaryDataPointSet = new HashSet<>();
    final IntList availableNodes = new IntArrayList(syntaxTreeMap.keySet());
    while (unaryDataPointSet.size() < unaryCount) {
      final int node = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      final UnaryDataPoint dataPoint = new UnaryDataPoint(node, syntaxTreeMap.get(node));
      unaryDataPointSet.add(dataPoint);
    }
    while (binaryDataPointSet.size() < binaryCount) {
      final int left = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      final int right = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      final BinaryDataPoint dataPoint = new BinaryDataPoint(left, right, syntaxTreeMap.get(left),
        syntaxTreeMap.get(right));
      binaryDataPointSet.add(dataPoint);
    }
    while (ternaryDataPointSet.size() < ternaryCount) {
      final int first = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      final int second = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      final int third = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      final TernaryDataPoint dataPoint = new TernaryDataPoint(first, second, third,
        syntaxTreeMap.get(first), syntaxTreeMap.get(second), syntaxTreeMap.get(third));
      ternaryDataPointSet.add(dataPoint);
    }
    unaryDataPoints = ImmutableSet.copyOf(unaryDataPointSet);
    binaryDataPoints = ImmutableSet.copyOf(binaryDataPointSet);
    ternaryDataPoints = ImmutableSet.copyOf(ternaryDataPointSet);

    logger.log(Level.INFO, "Built {0} nodes ({1} referenced), {2} unary, {3} binary and {4} ternary"
      + " data points", new Object[] {initialNodeCount, initialReferencedNodeCount,
      unaryDataPointSet.size(), binaryDataPointSet.size(), ternaryDataPointSet.size()});

    final ImmutableList.Builder<BitSet> valuationBuilder = ImmutableList.builder();
    for (int i = 0; i < 1 << variableList.size(); i++) {
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      final BitSet bitSet = new BitSet(variableList.size());
      for (int j = 0; j < variableList.size(); j++) {
        if (((i >>> j) & 1) == 1) {
          bitSet.set(j);
        }
      }
      //noinspection ResultOfMethodCallIgnored
      valuationBuilder.add(bitSet);
    }

    valuations = valuationBuilder.build();
  }

  private final Random skipCheckRandom = new Random(0L);

  @DataPoints
  public static List<BinaryDataPoint> binaryDataPoints() {
    return ImmutableList.copyOf(binaryDataPoints);
  }

  @AfterClass
  public static void check() {
    doCheckInvariants();
  }

  private static BitSet copyBitSet(final BitSet bitSet) {
    return BitSet.valueOf(bitSet.toLongArray());
  }

  @SuppressWarnings("TypeMayBeWeakened")
  private static IntSet doBddOperations(final int node1, final int node2) {
    final IntSet resultSet = new IntOpenHashSet();
    resultSet.add(bdd.pushToWorkStack(bdd.and(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.or(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.xor(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.implication(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.equivalence(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.not(node1)));
    resultSet.add(bdd.pushToWorkStack(bdd.not(node2)));
    bdd.popWorkStack(7);
    assertTrue(bdd.isWorkStackEmpty());
    return resultSet;
  }

  private static void doCheckInvariants() {
    assertTrue(bdd.isWorkStackEmpty());
    bdd.check();
    assertThat(bdd.referencedNodeCount(), is(initialReferencedNodeCount));
    assertThat(bdd.nodeCount(), is(initialNodeCount));
  }

  private static Iterator<BitSet> getBitSetIterator(final BitSet enabledVariables) {
    if (enabledVariables.cardinality() == 0) {
      return Iterators.singletonIterator(new BitSet());
    }
    return new BitSetIterator(enabledVariables);
  }

  @AfterClass
  public static void statistics() {
    logger.log(Level.INFO, "Cache:\n{0}", new Object[] {bdd.getCacheStatistics()});
  }

  @DataPoints
  public static List<TernaryDataPoint> ternaryDataPoints() {
    return ImmutableList.copyOf(ternaryDataPoints);
  }

  @DataPoints
  public static List<UnaryDataPoint> unaryDataPoints() {
    return ImmutableList.copyOf(unaryDataPoints);
  }

  @After
  public void checkInvariants() {
    if (skipCheckRandom.nextInt(500) == 0) {
      doCheckInvariants();
    }
  }

  @Theory(nullsAccepted = false)
  public void testAnd(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    final int and = bdd.and(node1, node2);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(and, valuation), is(bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(and, valuation), is(false));
      }
    }

    bdd.pushToWorkStack(and);
    final int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    final int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    final int notNode1orNotNode2 = bdd.pushToWorkStack(bdd.or(notNode1, notNode2));
    final int andDeMorganConstruction = bdd.not(notNode1orNotNode2);
    bdd.popWorkStack(4);
    assertThat(and, is(andDeMorganConstruction));

    bdd.pushToWorkStack(and);
    final int andIteConstruction = bdd.ifThenElse(node1, node2, bdd.getFalseNode());
    bdd.popWorkStack();
    assertThat(and, is(andIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testComposeTree(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    final SyntaxTree syntaxTree = dataPoint.getTree();
    final IntSet containedVariables = syntaxTree.containedVariables();
    assumeTrue(containedVariables.size() <= 5);

    final int[] composeArray = new int[variableCount];
    final int[] composeNegativeArray = new int[variableCount];

    final Random selectionRandom = new Random((long) node);
    final IntList availableNodes = new IntArrayList(syntaxTreeMap.keySet());
    for (int i = 0; i < variableCount; i++) {
      if (containedVariables.contains(i)) {
        final int replacementBddIndex = selectionRandom.nextInt(availableNodes.size());
        composeArray[i] = availableNodes.get(replacementBddIndex);
      } else {
        composeArray[i] = variableList.get(i);
      }
    }
    for (int i = 0; i < variableCount; i++) {
      if (bdd.isVariable(composeArray[i]) && bdd.getVariable(composeArray[i]) == i) {
        composeNegativeArray[i] = -1;
      } else {
        composeNegativeArray[i] = composeArray[i];
      }
    }

    int[] composeCutoffArray = new int[0];
    for (int i = composeArray.length - 1; i >= 0; i--) {
      if (composeNegativeArray[i] != -1) {
        composeCutoffArray = Arrays.copyOf(composeArray, i + 1);
        break;
      }
    }

    final Int2ObjectMap<SyntaxTree> replacementMap = new Int2ObjectOpenHashMap<>();
    for (int i = 0; i < composeArray.length; i++) {
      final int variableReplacement = composeArray[i];
      if (variableReplacement != -1) {
        replacementMap.put(i, syntaxTreeMap.get(variableReplacement));
      }
    }
    final int composeNode = bdd.pushToWorkStack(bdd.compose(node, composeArray));
    final SyntaxTree composeTree = SyntaxTree.buildReplacementTree(syntaxTree, replacementMap);

    assertTrue(Iterators.all(getBitSetIterator(bdd.support(node)), bitSet -> {
      assert bitSet != null;
      return bdd.evaluate(composeNode, bitSet) == composeTree.evaluate(bitSet);
    }));

    final int composeNegativeNode = bdd.compose(node, composeNegativeArray);
    assertThat(composeNegativeNode, is(composeNode));
    final int composeCutoffNode = bdd.compose(node, composeCutoffArray);
    assertThat(composeCutoffNode, is(composeNode));
    bdd.popWorkStack();
  }

  @Theory(nullsAccepted = false)
  public void testConsume(final BinaryDataPoint dataPoint) {
    // This test simply tests if the semantics of consume are as specified, i.e.
    // consume(result, input1, input2) reduces the reference count of the inputs and increases that
    // of result
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(node1, is(not(node2)));
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    assumeThat(bdd.isNodeSaturated(node1) || bdd.isNodeSaturated(node2), is(false));

    bdd.reference(node1);
    bdd.reference(node2);
    final int node1referenceCount = bdd.getReferenceCount(node1);
    final int node2referenceCount = bdd.getReferenceCount(node2);

    for (final int operationNode : doBddOperations(node1, node2)) {
      if (bdd.isNodeSaturated(operationNode)) {
        continue;
      }
      final int operationRefCount = bdd.getReferenceCount(operationNode);
      assertThat(bdd.consume(operationNode, node1, node2),
        is(operationNode));

      if (operationNode == node1) {
        assertThat(bdd.getReferenceCount(node1), is(node1referenceCount));
        assertThat(bdd.getReferenceCount(node2), is(node2referenceCount - 1));
      } else if (operationNode == node2) {
        assertThat(bdd.getReferenceCount(node1), is(node1referenceCount - 1));
        assertThat(bdd.getReferenceCount(node2), is(node2referenceCount));
      } else {
        assertThat(bdd.getReferenceCount(node1), is(node1referenceCount - 1));
        assertThat(bdd.getReferenceCount(node2), is(node2referenceCount - 1));
        assertThat(bdd.getReferenceCount(operationNode), is(operationRefCount + 1));
      }

      bdd.reference(node1);
      bdd.reference(node2);
      bdd.dereference(operationNode);
    }

    assertThat(bdd.getReferenceCount(node1), is(node1referenceCount));
    assertThat(bdd.getReferenceCount(node2), is(node2referenceCount));
    bdd.dereference(node1);
    bdd.dereference(node2);
  }

  @Theory(nullsAccepted = false)
  public void testCountSatisfyingAssignments(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    long satisfyingAssignments = 0L;
    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node, valuation)) {
        satisfyingAssignments += 1L;
      }
    }
    assertThat(bdd.countSatisfyingAssignments(node), new IsCloseTo((double) satisfyingAssignments,
      0.1));
  }

  @Theory(nullsAccepted = false)
  public void testEquivalence(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    final int equivalence = bdd.equivalence(node1, node2);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(equivalence, valuation), is(bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(equivalence, valuation), is(!bdd.evaluate(node2, valuation)));
      }
    }

    bdd.pushToWorkStack(equivalence);
    final int node1andNode2 = bdd.pushToWorkStack(bdd.and(node1, node2));
    final int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    final int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    final int notNode1andNotNode2 = bdd.pushToWorkStack(bdd.and(notNode1, notNode2));
    final int equivalenceAndOrConstruction = bdd.or(node1andNode2, notNode1andNotNode2);
    bdd.popWorkStack(5);
    assertThat(equivalence, is(equivalenceAndOrConstruction));

    bdd.pushToWorkStack(equivalence);
    final int equivalenceIteConstruction = bdd.ifThenElse(node1, node2, notNode2);
    bdd.popWorkStack();
    assertThat(equivalence, is(equivalenceIteConstruction));

    bdd.pushToWorkStack(equivalence);
    final int node1ImpliesNode2 = bdd.pushToWorkStack(bdd.implication(node1, node2));
    final int node2ImpliesNode1 = bdd.pushToWorkStack(bdd.implication(node2, node1));
    final int equivalenceBiImplicationConstruction = bdd.and(node1ImpliesNode2, node2ImpliesNode1);
    bdd.popWorkStack(3);
    assertThat(equivalence, is(equivalenceBiImplicationConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testEvaluateTree(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    assumeTrue(dataPoint.getTree().depth() <= 7);

    for (final BitSet valuation : valuations) {
      assertThat(bdd.evaluate(node, valuation), is(dataPoint.getTree().evaluate(valuation)));
    }
  }

  @Theory(nullsAccepted = false)
  public void testExistsSelfSubstitution(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    final BitSet quantificationBitSet = new BitSet(bdd.numberOfVariables());
    final Random quantificationRandom = new Random((long) node);
    for (int i = 0; i < bdd.numberOfVariables(); i++) {
      if (quantificationRandom.nextInt(bdd.numberOfVariables()) < 5) {
        quantificationBitSet.set(i);
      }
    }
    assumeThat(quantificationBitSet.cardinality(), lessThanOrEqualTo(5));

    final int existsNode = bdd.existsSelfSubstitution(node, quantificationBitSet);
    final BitSet supportIntersection = bdd.support(existsNode);
    supportIntersection.and(quantificationBitSet);
    assertTrue(supportIntersection.isEmpty());

    final BitSet unquantifiedVariables = copyBitSet(quantificationBitSet);
    unquantifiedVariables.flip(0, bdd.numberOfVariables());

    assertTrue(Iterators.all(getBitSetIterator(unquantifiedVariables), unquantifiedAssignment -> {
      assert unquantifiedAssignment != null;
      return bdd.evaluate(existsNode, unquantifiedAssignment) ==
        Iterators.any(getBitSetIterator(quantificationBitSet), bitSet -> {
          if (bitSet == null) {
            return false;
          }
          final BitSet actualBitSet = copyBitSet(bitSet);
          actualBitSet.or(unquantifiedAssignment);
          return bdd.evaluate(node, actualBitSet);
        });
    }));
  }

  @Theory(nullsAccepted = false)
  public void testExistsShannon(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    final BitSet quantificationBitSet = new BitSet(bdd.numberOfVariables());
    final Random quantificationRandom = new Random((long) node);
    for (int i = 0; i < bdd.numberOfVariables(); i++) {
      if (quantificationRandom.nextInt(bdd.numberOfVariables()) < 5) {
        quantificationBitSet.set(i);
      }
    }
    assumeThat(quantificationBitSet.cardinality(), lessThanOrEqualTo(5));

    final int existsNode = bdd.existsShannon(node, quantificationBitSet);
    final BitSet supportIntersection = bdd.support(existsNode);
    supportIntersection.and(quantificationBitSet);
    assertTrue(supportIntersection.isEmpty());

    final BitSet unquantifiedVariables = copyBitSet(quantificationBitSet);
    unquantifiedVariables.flip(0, bdd.numberOfVariables());

    assertTrue(Iterators.all(getBitSetIterator(unquantifiedVariables), unquantifiedAssignment -> {
      assert unquantifiedAssignment != null;
      return bdd.evaluate(existsNode, unquantifiedAssignment) ==
        Iterators.any(getBitSetIterator(quantificationBitSet), bitSet -> {
          if (bitSet == null) {
            return false;
          }
          final BitSet actualBitSet = copyBitSet(bitSet);
          actualBitSet.or(unquantifiedAssignment);
          return bdd.evaluate(node, actualBitSet);
        });
    }));
  }

  @Theory(nullsAccepted = false)
  public void testGetLowAndHigh(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValid(node));
    final int low = bdd.getLow(node);
    final int high = bdd.getHigh(node);
    if (bdd.isVariableOrNegated(node)) {
      if (bdd.isVariable(node)) {
        assertThat(low, is(bdd.getFalseNode()));
        assertThat(high, is(bdd.getTrueNode()));
      } else {
        assertThat(low, is(bdd.getTrueNode()));
        assertThat(high, is(bdd.getFalseNode()));
      }
    } else {
      final ImmutableSet<Integer> rootNodes =
        ImmutableSet.of(bdd.getFalseNode(), bdd.getTrueNode());
      assumeThat(rootNodes.contains(low) && rootNodes.contains(high), is(false));
    }
  }

  @Theory
  public void testGetMinimalSolutions(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    final BitSet support = bdd.support(node);
    assumeThat(support.cardinality(), lessThanOrEqualTo(7));

    final Set<BitSet> solutionBitSets = new HashSet<>();
    final BitSet supportFromSolutions = new BitSet(bdd.numberOfVariables());
    final Iterator<BitSet> solutionIterator = bdd.getMinimalSolutions(node);
    BitSet previous = null;
    while (solutionIterator.hasNext()) {
      final BitSet bitSet = solutionIterator.next();
      if (previous != null) {
        // Check that the solution is lexicographic bigger or equal to the previous one - the
        // equal case gets checked later
        for (int i = 0; i < bitSet.length(); i++) {
          if (bitSet.get(i)) {
            if (!previous.get(i)) {
              break;
            }
          } else {
            assertFalse(previous.get(i));
          }
        }
      }
      previous = copyBitSet(bitSet);
      // No solution is generated twice
      assertTrue(solutionBitSets.add(previous));
      supportFromSolutions.or(previous);
    }

    // supportFromSolutions has to be a subset of support (see ~a for example)
    supportFromSolutions.or(support);
    assertThat(supportFromSolutions, is(support));

    // Build up all minimal solutions using a naive algorithm
    final Set<BitSet> assignments = new BddPathExplorer(bdd, node).getAssignments();
    assertThat(solutionBitSets, is(assignments));
  }

  @Theory(nullsAccepted = false)
  public void testIfThenElse(final TernaryDataPoint dataPoint) {
    final int ifNode = dataPoint.getFirst();
    final int thenNode = dataPoint.getSecond();
    final int elseNode = dataPoint.getThird();
    assumeThat(bdd.isNodeValidOrRoot(ifNode)
      && bdd.isNodeValidOrRoot(thenNode)
      && bdd.isNodeValidOrRoot(elseNode), is(true));

    final int ifThenElse = bdd.ifThenElse(ifNode, thenNode, elseNode);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(ifNode, valuation)) {
        assertThat(bdd.evaluate(ifThenElse, valuation), is(bdd.evaluate(thenNode, valuation)));
      } else {
        assertThat(bdd.evaluate(ifThenElse, valuation), is(bdd.evaluate(elseNode, valuation)));
      }
    }

    bdd.pushToWorkStack(ifThenElse);
    final int notIf = bdd.pushToWorkStack(bdd.not(ifNode));
    final int ifImpliesThen = bdd.pushToWorkStack(bdd.implication(ifNode, thenNode));
    final int notIfImpliesThen = bdd.pushToWorkStack(bdd.implication(notIf, elseNode));
    final int ifThenElseImplicationConstruction = bdd.and(ifImpliesThen, notIfImpliesThen);
    bdd.popWorkStack(4);
    assertThat("ITE construction failed for " + ifNode + "," + thenNode + "," + elseNode,
      ifThenElse, is(ifThenElseImplicationConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testImplication(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    final int implication = bdd.implication(node1, node2);
    for (final BitSet valuation : valuations) {
      final boolean implies = !bdd.evaluate(node1, valuation) || bdd.evaluate(node2, valuation);
      assertThat(bdd.evaluate(implication, valuation), is(implies));
    }

    bdd.pushToWorkStack(implication);
    final int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    final int implicationConstruction = bdd.or(notNode1, node2);
    bdd.popWorkStack(2);
    assertThat(implication, is(implicationConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testImplies(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    final int implication = bdd.implication(node1, node2);

    if (bdd.implies(node1, node2)) {
      for (final BitSet valuation : valuations) {
        assertTrue(!bdd.evaluate(node1, valuation) || bdd.evaluate(node2, valuation));
      }
      assertThat(implication, is(bdd.getTrueNode()));
    } else {
      assertThat(implication, is(not(bdd.getTrueNode())));
    }
  }

  @Theory(nullsAccepted = false)
  public void testIsVariable(final UnaryDataPoint dataPoint) {
    final SyntaxTree.SyntaxTreeNode rootNode = dataPoint.getTree().getRootNode();
    if (rootNode instanceof SyntaxTree.SyntaxTreeLiteral) {
      assertTrue(bdd.isVariable(dataPoint.getNode()));
      assertTrue(bdd.isVariableOrNegated(dataPoint.getNode()));
    } else if (rootNode instanceof SyntaxTree.SyntaxTreeNot) {
      final SyntaxTree.SyntaxTreeNode child = ((SyntaxTree.SyntaxTreeNot) rootNode).getChild();
      if (child instanceof SyntaxTree.SyntaxTreeLiteral) {
        assertThat(bdd.isVariable(dataPoint.getNode()), is(false));
        assertTrue(bdd.isVariableOrNegated(dataPoint.getNode()));
      }
    }
    final BitSet support = bdd.support(dataPoint.getNode());
    assertThat(bdd.isVariableOrNegated(dataPoint.getNode()), is(support.cardinality() == 1));
  }

  @Theory(nullsAccepted = false)
  public void testNot(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    final int not = bdd.not(node);

    for (final BitSet valuation : valuations) {
      assertThat(bdd.evaluate(not, valuation), is(!bdd.evaluate(node, valuation)));
    }

    bdd.pushToWorkStack(not);
    assertThat(bdd.not(not), is(node));
    bdd.popWorkStack();

    bdd.pushToWorkStack(not);
    final int notIteConstruction = bdd.ifThenElse(node, bdd.getFalseNode(), bdd.getTrueNode());
    bdd.popWorkStack();
    assertThat(not, is(notIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testNotAnd(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    final int notAnd = bdd.notAnd(node1, node2);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(notAnd, valuation), is(!bdd.evaluate(node2, valuation)));
      } else {
        assertTrue(bdd.evaluate(notAnd, valuation));
      }
    }

    bdd.pushToWorkStack(notAnd);
    final int node1andNode2 = bdd.pushToWorkStack(bdd.and(node1, node2));
    final int notNode1AndNode2 = bdd.not(node1andNode2);
    bdd.popWorkStack(2);
    assertThat(notAnd, is(notNode1AndNode2));

    bdd.pushToWorkStack(notAnd);
    final int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    final int notAndIteConstruction = bdd.ifThenElse(node1, notNode2, bdd.getTrueNode());
    bdd.popWorkStack(2);
    assertThat(notAnd, is(notAndIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testOr(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    final int or = bdd.or(node1, node2);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertTrue(bdd.evaluate(or, valuation));
      } else {
        assertThat(bdd.evaluate(or, valuation), is(bdd.evaluate(node2, valuation)));
      }
    }

    bdd.pushToWorkStack(or);
    final int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    final int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    final int notNode1andNotNode2 = bdd.pushToWorkStack(bdd.and(notNode1, notNode2));
    final int orDeMorganConstruction = bdd.not(notNode1andNotNode2);
    bdd.popWorkStack(4);
    assertThat(or, is(orDeMorganConstruction));

    bdd.pushToWorkStack(or);
    final int orIteConstruction = bdd.ifThenElse(node1, bdd.getTrueNode(), node2);
    bdd.popWorkStack();
    assertThat(or, is(orIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testReferenceAndDereference(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    assumeThat(bdd.isNodeSaturated(node), is(false));

    final int referenceCount = bdd.getReferenceCount(node);
    for (int i = referenceCount; i > 0; i--) {
      bdd.dereference(node);
      assertThat(bdd.getReferenceCount(node), is(i - 1));
    }
    for (int i = 0; i < referenceCount; i++) {
      bdd.reference(node);
      assertThat(bdd.getReferenceCount(node), is(i + 1));
    }
    assertThat(bdd.getReferenceCount(node), is(referenceCount));
  }

  @Theory
  @SuppressWarnings("PMD.ExceptionAsFlowControl")
  public void testReferenceGuard(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    assumeThat(bdd.isNodeSaturated(node), is(false));

    final int referenceCount = bdd.getReferenceCount(node);
    try {
      try (Bdd.ReferenceGuard guard = new Bdd.ReferenceGuard(node, bdd)) {
        assertThat(bdd.getReferenceCount(node), is(referenceCount + 1));
        assertThat(guard.getBdd(), is(bdd));
        assertThat(guard.getNode(), is(node));
        // We deliberately want to test the exception handling here
        throw new IllegalArgumentException("Bogus");
      }
    } catch (final IllegalArgumentException e) {
      assertThat(bdd.getReferenceCount(node), is(referenceCount));
    }
  }

  @Theory
  public void testRestrict(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    final Random restrictRandom = new Random((long) node);
    final BitSet restrictedVariables = new BitSet(bdd.numberOfVariables());
    final BitSet restrictedVariableValues = new BitSet(bdd.numberOfVariables());
    final int[] composeArray = new int[bdd.numberOfVariables()];
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < bdd.numberOfVariables(); j++) {
        if (restrictRandom.nextBoolean()) {
          restrictedVariables.set(j);
          if (restrictRandom.nextBoolean()) {
            restrictedVariableValues.set(j);
            composeArray[j] = bdd.getTrueNode();
          } else {
            composeArray[j] = bdd.getFalseNode();
          }
        } else {
          composeArray[j] = -1;
        }
      }
      final int restrictNode = bdd.pushToWorkStack(
        bdd.restrict(node, restrictedVariables, restrictedVariableValues));
      final int composeNode = bdd.compose(node, composeArray);
      bdd.popWorkStack();

      assertThat(restrictNode, is(composeNode));

      final BitSet restrictSupport = bdd.support(restrictNode);
      restrictSupport.and(restrictedVariables);
      assertTrue(restrictSupport.isEmpty());

      restrictedVariables.clear();
      restrictedVariableValues.clear();
    }

  }

  @Theory(nullsAccepted = false)
  public void testSupportTree(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    final IntSet containedVariables = dataPoint.getTree().containedVariables();
    assumeTrue(containedVariables.size() <= 5);

    // For each variable, we iterate through all possible valuations and check if there ever is any
    // difference.
    if (containedVariables.isEmpty()) {
      assertTrue(bdd.support(node).isEmpty());
    } else {
      // Have some arbitrary ordering
      final IntList containedVariableList = new IntArrayList(containedVariables);
      final BitSet valuation = new BitSet(variableCount);
      final BitSet support = new BitSet(variableCount);

      for (final int checkedVariable : containedVariableList) {
        final int checkedContainedIndex = containedVariableList.indexOf(checkedVariable);
        // Only iterate over all possible valuations of involved variables, otherwise this test
        // might explode
        for (int i = 0; i < 1 << containedVariables.size(); i++) {
          if (((i >>> checkedContainedIndex) & 1) == 1) {
            continue;
          }
          valuation.clear();
          for (int j = 0; j < containedVariables.size(); j++) {
            if (((i >>> j) & 1) == 1) {
              valuation.set(containedVariableList.getInt(j));
            }
          }

          // Check if
          final boolean negative = bdd.evaluate(node, valuation);
          valuation.set(checkedVariable);
          final boolean positive = bdd.evaluate(node, valuation);
          if (negative != positive) {
            support.set(checkedVariable);
            break;
          }
        }
      }
      assertThat(bdd.support(node), is(support));
    }
  }

  @Theory(nullsAccepted = false)
  public void testSupportUnion(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    final BitSet node1Support = bdd.support(node1);
    final BitSet node2Support = bdd.support(node2);
    final BitSet supportUnion = copyBitSet(node1Support);
    supportUnion.or(node2Support);

    for (final int operationNode : doBddOperations(node1, node2)) {
      final BitSet operationSupport = bdd.support(operationNode);
      operationSupport.stream().forEach(setBit -> assertThat(supportUnion.get(setBit), is(true)));
    }
  }

  @Theory(nullsAccepted = false)
  public void testUpdateWith(final BinaryDataPoint dataPoint) {
    // This test simply tests if the semantics of updateWith are as specified, i.e.
    // updateWith(result, input) reduces the reference count of the input and increases that of
    // result
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(node1, is(not(node2)));
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    assumeFalse(bdd.isNodeSaturated(node1) || bdd.isNodeSaturated(node2));

    bdd.reference(node1);
    bdd.reference(node2);
    final int node1referenceCount = bdd.getReferenceCount(node1);
    final int node2referenceCount = bdd.getReferenceCount(node2);
    bdd.updateWith(node1, node1);
    assertThat(bdd.getReferenceCount(node1), is(node1referenceCount));

    for (final int operationNode : doBddOperations(node1, node2)) {
      if (bdd.isNodeSaturated(operationNode)) {
        continue;
      }
      final int operationRefCount = bdd.getReferenceCount(operationNode);
      assertThat(bdd.updateWith(operationNode, node1), is(operationNode));

      if (operationNode == node1) {
        assertThat(bdd.getReferenceCount(node1), is(node1referenceCount));
        assertThat(bdd.getReferenceCount(node2), is(node2referenceCount));
      } else if (operationNode == node2) {
        assertThat(bdd.getReferenceCount(node1), is(node1referenceCount - 1));
        assertThat(bdd.getReferenceCount(node2), is(node2referenceCount + 1));
      } else {
        assertThat(bdd.getReferenceCount(node1), is(node1referenceCount - 1));
        assertThat(bdd.getReferenceCount(node2), is(node2referenceCount));
        assertThat(bdd.getReferenceCount(operationNode), is(operationRefCount + 1));
      }

      bdd.reference(node1);
      bdd.dereference(operationNode);
    }

    assertThat(bdd.getReferenceCount(node1), is(node1referenceCount));
    assertThat(bdd.getReferenceCount(node2), is(node2referenceCount));
    bdd.dereference(node1);
    bdd.dereference(node2);
  }

  @Theory(nullsAccepted = false)
  public void testXor(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    final int xor = bdd.xor(node1, node2);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(xor, valuation), is(!bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(xor, valuation), is(bdd.evaluate(node2, valuation)));
      }
    }

    bdd.pushToWorkStack(xor);
    final int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    final int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    final int notNode1AndNode2 = bdd.pushToWorkStack(bdd.and(notNode1, node2));
    final int node1andNotNode2 = bdd.pushToWorkStack(bdd.and(node1, notNode2));
    final int xorConstruction = bdd.or(node1andNotNode2, notNode1AndNode2);
    bdd.popWorkStack(5);
    assertThat(xor, is(xorConstruction));
  }

  private static final class BddPathExplorer {
    private final Set<BitSet> assignments;
    private final Bdd bdd;

    public BddPathExplorer(final Bdd bdd, final int startingNode) {
      this.bdd = bdd;
      this.assignments = new HashSet<>();
      if (startingNode == bdd.getTrueNode()) {
        assignments.add(new BitSet(bdd.numberOfVariables()));
      } else if (startingNode != bdd.getFalseNode()) {
        final IntList path = new IntArrayList();
        path.add(startingNode);
        recurse(path, new BitSet(bdd.numberOfVariables()));
      }
    }

    public Set<BitSet> getAssignments() {
      return assignments;
    }

    private void recurse(final IntList currentPath, final BitSet currentAssignment) {
      final int pathLeaf = currentPath.getInt(currentPath.size() - 1);
      final int low = bdd.getLow(pathLeaf);
      final int high = bdd.getHigh(pathLeaf);

      if (low == bdd.getTrueNode()) {
        assignments.add(copyBitSet(currentAssignment));
      } else if (low != bdd.getFalseNode()) {
        final IntList recursePath = new IntArrayList(currentPath);
        recursePath.add(low);
        recurse(recursePath, currentAssignment);
      }

      if (high != bdd.getFalseNode()) {
        final BitSet assignment = copyBitSet(currentAssignment);
        assignment.set(bdd.getVariable(pathLeaf));
        if (high == bdd.getTrueNode()) {
          assignments.add(assignment);
        } else {
          final IntList recursePath = new IntArrayList(currentPath);
          recursePath.add(high);
          recurse(recursePath, assignment);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  private static final class BinaryDataPoint {
    private final int left;
    private final SyntaxTree leftTree;
    private final int right;
    private final SyntaxTree rightTree;

    public BinaryDataPoint(final int left, final int right, final SyntaxTree leftTree,
      final SyntaxTree rightTree) {
      this.left = left;
      this.right = right;
      this.leftTree = leftTree;
      this.rightTree = rightTree;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof BinaryDataPoint)) {
        return false;
      }
      final BinaryDataPoint other = (BinaryDataPoint) o;
      return left == other.left &&
        right == other.right;
    }

    public int getLeft() {
      return left;
    }

    public SyntaxTree getLeftTree() {
      return leftTree;
    }

    public int getRight() {
      return right;
    }

    public SyntaxTree getRightTree() {
      return rightTree;
    }

    @Override
    public int hashCode() {
      return Objects.hash(left, right);
    }
  }

  private static final class BitSetIterator implements Iterator<BitSet> {
    private final BitSet bitSet;
    private final int[] restrictionPositions;
    private final BitSet variableRestriction;
    private int assignment;

    BitSetIterator(final BitSet restriction) {
      this(restriction.length(), restriction);
    }

    BitSetIterator(final int size, final BitSet restriction) {
      assert restriction.cardinality() > 0;
      this.bitSet = new BitSet(size);
      this.variableRestriction = restriction;
      this.assignment = 0;
      this.restrictionPositions = new int[restriction.cardinality()];

      restrictionPositions[0] = restriction.nextSetBit(0);
      for (int i = 1; i < restrictionPositions.length; i++) {
        restrictionPositions[i] = restriction.nextSetBit(restrictionPositions[i - 1] + 1);
      }
    }

    @Override
    public boolean hasNext() {
      return !Objects.equals(bitSet, variableRestriction);
    }

    @Override
    public BitSet next() throws NoSuchElementException {
      if (assignment == 1 << restrictionPositions.length) {
        throw new NoSuchElementException("No next element");
      }

      bitSet.clear();

      for (int restrictionPosition = 0; restrictionPosition < restrictionPositions.length;
           restrictionPosition++) {
        if (((assignment >>> restrictionPosition) & 1) == 1) {
          bitSet.set(restrictionPositions[restrictionPosition]);
        }
      }
      assignment += 1;
      return bitSet;
    }
  }

  @SuppressWarnings("unused")
  private static final class TernaryDataPoint {
    private final int first;
    private final SyntaxTree firstTree;
    private final int second;
    private final SyntaxTree secondTree;
    private final int third;
    private final SyntaxTree thirdTree;

    public TernaryDataPoint(final int first, final int second, final int third,
      final SyntaxTree firstTree, final SyntaxTree secondTree, final SyntaxTree thirdTree) {
      this.first = first;
      this.second = second;
      this.third = third;
      this.firstTree = firstTree;
      this.secondTree = secondTree;
      this.thirdTree = thirdTree;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TernaryDataPoint)) {
        return false;
      }
      final TernaryDataPoint other = (TernaryDataPoint) o;
      return first == other.first &&
        second == other.second &&
        third == other.third;
    }

    public int getFirst() {
      return first;
    }

    public SyntaxTree getFirstTree() {
      return firstTree;
    }

    public int getSecond() {
      return second;
    }

    public SyntaxTree getSecondTree() {
      return secondTree;
    }

    public int getThird() {
      return third;
    }

    public SyntaxTree getThirdTree() {
      return thirdTree;
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second, third);
    }
  }

  private static final class UnaryDataPoint {
    private final int node;
    private final SyntaxTree tree;

    public UnaryDataPoint(final int node, final SyntaxTree tree) {
      this.node = node;
      this.tree = tree;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof UnaryDataPoint)) {
        return false;
      }
      final UnaryDataPoint other = (UnaryDataPoint) o;
      return node == other.node;
    }

    public int getNode() {
      return node;
    }

    public SyntaxTree getTree() {
      return tree;
    }

    @Override
    public int hashCode() {
      return Objects.hash(node);
    }
  }
}
