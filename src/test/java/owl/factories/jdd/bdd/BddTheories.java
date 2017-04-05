package owl.factories.jdd.bdd;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertFalse;
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
import owl.collections.ints.BitSets;

/**
 * Tests various logical functions of BDDs and checks invariants.
 */
@RunWith(Theories.class)
@SuppressWarnings({"checkstyle:javadoc"})
public class BddTheories {
  private static final BddImpl bdd;
  private static final int binaryCount = 2000;
  private static final Collection<BinaryDataPoint> binaryDataPoints;
  private static final int initialNodeCount;
  private static final int initialReferencedNodeCount;
  private static final Logger logger = Logger.getLogger(BddTheories.class.getName());
  private static final Int2ObjectMap<SyntaxTree> syntaxTreeMap;
  private static final int ternaryCount = 1000;
  private static final Collection<TernaryDataPoint> ternaryDataPoints;
  private static final int treeDepth = 15;
  private static final int treeWidth = 500;
  private static final int unaryCount = 6000;
  /* The @DataPoints annotated method is called multiple times - which would create new variables
   * each time, exploding the runtime of the tests. */
  private static final Collection<UnaryDataPoint> unaryDataPoints;
  private static final Set<BitSet> valuations;
  private static final int variableCount = 10;
  private static final IntList variableList;

  static {
    // It is important other the generation of data is ordered for the tests to be reproducible.

    logger.log(Level.INFO, "Building base BDD structure");
    // Have a lot of GC sweeps
    BddConfiguration config = ImmutableBddConfiguration.builder()
      .logStatisticsOnShutdown(false)
      .maximumNodeTableGrowth(100)
      .minimumNodeTableGrowth(100)
      .build();
    bdd = new BddImpl(10, config);

    Random filter = new Random(0L);
    variableList = new IntArrayList(variableCount);
    for (int i = 0; i < variableCount; i++) {
      variableList.add(bdd.createVariable());
    }

    syntaxTreeMap = new Int2ObjectLinkedOpenHashMap<>();
    syntaxTreeMap.put(bdd.getFalseNode(), SyntaxTree.constant(false));
    syntaxTreeMap.put(bdd.getTrueNode(), SyntaxTree.constant(true));
    for (int i = 0; i < variableList.size(); i++) {
      SyntaxTree literal = SyntaxTree.literal(i);
      syntaxTreeMap.put(variableList.get(i), literal);
      syntaxTreeMap.put(bdd.reference(bdd.not(variableList.get(i))), SyntaxTree.not(literal));
    }

    // Although a set would be more suitable, a list is needed so that the data is always generated
    // in the same order.
    Object2IntMap<SyntaxTree> treeToNodeMap = new Object2IntLinkedOpenHashMap<>();
    List<SyntaxTree> previousDepthNodes = new LinkedList<>();

    // Syntax tree map is a linked map, hence entry set is sorted.
    for (Map.Entry<Integer, SyntaxTree> treeEntry : syntaxTreeMap.entrySet()) {
      treeToNodeMap.put(treeEntry.getValue(), treeEntry.getKey());
      previousDepthNodes.add(treeEntry.getValue());
    }
    logger.log(Level.FINER, "Generating syntax trees from {0} base expressions",
      previousDepthNodes.size());
    List<SyntaxTree> candidates = new ArrayList<>();
    for (int depth = 1; depth < treeDepth; depth++) {
      logger.log(Level.FINEST, "Building tree depth {0}", depth);

      candidates.addAll(previousDepthNodes);
      previousDepthNodes.clear();
      Collections.shuffle(candidates, filter);
      List<SyntaxTree> leftCandidates = ImmutableList.copyOf(candidates);
      Collections.shuffle(candidates, filter);
      List<SyntaxTree> rightCandidates = ImmutableList.copyOf(candidates);
      candidates.clear();

      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      Int2ObjectMap<SyntaxTree> createdFromCandidates = new Int2ObjectLinkedOpenHashMap<>();
      for (SyntaxTree left : leftCandidates) {
        for (SyntaxTree right : rightCandidates) {
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
          assertThat(bdd.isWorkStackEmpty(), is(true));
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

    Collection<UnaryDataPoint> unaryDataPointSet = new HashSet<>();
    IntList availableNodes = new IntArrayList(syntaxTreeMap.keySet());
    while (unaryDataPointSet.size() < unaryCount) {
      int node = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      UnaryDataPoint dataPoint = new UnaryDataPoint(node, syntaxTreeMap.get(node));
      unaryDataPointSet.add(dataPoint);
    }
    Collection<BinaryDataPoint> binaryDataPointSet = new HashSet<>();
    while (binaryDataPointSet.size() < binaryCount) {
      int left = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      int right = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      BinaryDataPoint dataPoint = new BinaryDataPoint(left, right, syntaxTreeMap.get(left),
        syntaxTreeMap.get(right));
      binaryDataPointSet.add(dataPoint);
    }
    Collection<TernaryDataPoint> ternaryDataPointSet = new HashSet<>();
    while (ternaryDataPointSet.size() < ternaryCount) {
      int first = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      int second = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      int third = availableNodes.getInt(filter.nextInt(availableNodes.size()));
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      TernaryDataPoint dataPoint = new TernaryDataPoint(first, second, third,
        syntaxTreeMap.get(first), syntaxTreeMap.get(second), syntaxTreeMap.get(third));
      ternaryDataPointSet.add(dataPoint);
    }
    unaryDataPoints = ImmutableSet.copyOf(unaryDataPointSet);
    binaryDataPoints = ImmutableSet.copyOf(binaryDataPointSet);
    ternaryDataPoints = ImmutableSet.copyOf(ternaryDataPointSet);

    logger.log(Level.INFO, "Built {0} nodes ({1} referenced), {2} unary, {3} binary and {4} ternary"
      + " data points", new Object[] {initialNodeCount, initialReferencedNodeCount,
      unaryDataPointSet.size(), binaryDataPointSet.size(), ternaryDataPointSet.size()});

    BitSet alphabet = new BitSet();
    for (int i = 0; i < variableList.size(); i++) {
      alphabet.set(i);
    }
    valuations = BitSets.powerSet(alphabet);
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

  @SuppressWarnings("UseOfClone")
  private static BitSet copyBitSet(BitSet bitSet) {
    return (BitSet) bitSet.clone();
  }

  @SuppressWarnings("TypeMayBeWeakened")
  private static IntSet doBddOperations(int node1, int node2) {
    IntSet resultSet = new IntOpenHashSet();
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

  private static Iterator<BitSet> getBitSetIterator(BitSet enabledVariables) {
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
  public void testAnd(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    int and = bdd.and(node1, node2);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(and, valuation), is(bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(and, valuation), is(false));
      }
    }

    bdd.pushToWorkStack(and);
    int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    int notNode1orNotNode2 = bdd.pushToWorkStack(bdd.or(notNode1, notNode2));
    int andDeMorganConstruction = bdd.not(notNode1orNotNode2);
    bdd.popWorkStack(4);
    assertThat(and, is(andDeMorganConstruction));

    bdd.pushToWorkStack(and);
    int andIteConstruction = bdd.ifThenElse(node1, node2, bdd.getFalseNode());
    bdd.popWorkStack();
    assertThat(and, is(andIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testComposeTree(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    SyntaxTree syntaxTree = dataPoint.getTree();
    IntSet containedVariables = syntaxTree.containedVariables();
    assumeTrue(containedVariables.size() <= 5);

    int[] composeArray = new int[variableCount];

    Random selectionRandom = new Random((long) node);
    IntList availableNodes = new IntArrayList(syntaxTreeMap.keySet());
    for (int i = 0; i < variableCount; i++) {
      if (containedVariables.contains(i)) {
        int replacementBddIndex = selectionRandom.nextInt(availableNodes.size());
        composeArray[i] = availableNodes.get(replacementBddIndex);
      } else {
        composeArray[i] = variableList.get(i);
      }
    }
    int[] composeNegativeArray = new int[variableCount];
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

    Int2ObjectMap<SyntaxTree> replacementMap = new Int2ObjectOpenHashMap<>();
    for (int i = 0; i < composeArray.length; i++) {
      int variableReplacement = composeArray[i];
      if (variableReplacement != -1) {
        replacementMap.put(i, syntaxTreeMap.get(variableReplacement));
      }
    }
    int composeNode = bdd.pushToWorkStack(bdd.compose(node, composeArray));
    SyntaxTree composeTree = SyntaxTree.buildReplacementTree(syntaxTree, replacementMap);

    assertTrue(Iterators.all(getBitSetIterator(bdd.support(node)), bitSet -> {
      assert bitSet != null;
      return bdd.evaluate(composeNode, bitSet) == composeTree.evaluate(bitSet);
    }));

    int composeNegativeNode = bdd.compose(node, composeNegativeArray);
    assertThat(composeNegativeNode, is(composeNode));
    int composeCutoffNode = bdd.compose(node, composeCutoffArray);
    assertThat(composeCutoffNode, is(composeNode));
    bdd.popWorkStack();
  }

  @Theory(nullsAccepted = false)
  public void testConsume(BinaryDataPoint dataPoint) {
    // This test simply tests if the semantics of consume are as specified, i.e.
    // consume(result, input1, input2) reduces the reference count of the inputs and increases that
    // of result
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeThat(node1, is(not(node2)));
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    assumeThat(bdd.isNodeSaturated(node1) || bdd.isNodeSaturated(node2), is(false));

    bdd.reference(node1);
    bdd.reference(node2);
    int node1referenceCount = bdd.getReferenceCount(node1);
    int node2referenceCount = bdd.getReferenceCount(node2);

    for (int operationNode : doBddOperations(node1, node2)) {
      if (bdd.isNodeSaturated(operationNode)) {
        continue;
      }
      int operationRefCount = bdd.getReferenceCount(operationNode);
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
  public void testCountSatisfyingAssignments(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    long satisfyingAssignments = 0L;
    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node, valuation)) {
        satisfyingAssignments += 1L;
      }
    }
    assertThat(bdd.countSatisfyingAssignments(node), new IsCloseTo((double) satisfyingAssignments,
      0.1));
  }

  @Theory(nullsAccepted = false)
  public void testEquivalence(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    int equivalence = bdd.equivalence(node1, node2);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(equivalence, valuation), is(bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(equivalence, valuation), is(!bdd.evaluate(node2, valuation)));
      }
    }

    bdd.pushToWorkStack(equivalence);
    int node1andNode2 = bdd.pushToWorkStack(bdd.and(node1, node2));
    int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    int notNode1andNotNode2 = bdd.pushToWorkStack(bdd.and(notNode1, notNode2));
    int equivalenceAndOrConstruction = bdd.or(node1andNode2, notNode1andNotNode2);
    bdd.popWorkStack(5);
    assertThat(equivalence, is(equivalenceAndOrConstruction));

    bdd.pushToWorkStack(equivalence);
    int equivalenceIteConstruction = bdd.ifThenElse(node1, node2, notNode2);
    bdd.popWorkStack();
    assertThat(equivalence, is(equivalenceIteConstruction));

    bdd.pushToWorkStack(equivalence);
    int node1ImpliesNode2 = bdd.pushToWorkStack(bdd.implication(node1, node2));
    int node2ImpliesNode1 = bdd.pushToWorkStack(bdd.implication(node2, node1));
    int equivalenceBiImplicationConstruction = bdd.and(node1ImpliesNode2, node2ImpliesNode1);
    bdd.popWorkStack(3);
    assertThat(equivalence, is(equivalenceBiImplicationConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testEvaluateTree(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    assumeTrue(dataPoint.getTree().depth() <= 7);

    for (BitSet valuation : valuations) {
      assertThat(bdd.evaluate(node, valuation), is(dataPoint.getTree().evaluate(valuation)));
    }
  }

  @Theory(nullsAccepted = false)
  public void testExistsSelfSubstitution(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    BitSet quantificationBitSet = new BitSet(bdd.numberOfVariables());
    Random quantificationRandom = new Random((long) node);
    for (int i = 0; i < bdd.numberOfVariables(); i++) {
      if (quantificationRandom.nextInt(bdd.numberOfVariables()) < 5) {
        quantificationBitSet.set(i);
      }
    }
    assumeThat(quantificationBitSet.cardinality(), lessThanOrEqualTo(5));

    int existsNode = bdd.existsSelfSubstitution(node, quantificationBitSet);
    BitSet supportIntersection = bdd.support(existsNode);
    supportIntersection.and(quantificationBitSet);
    assertTrue(supportIntersection.isEmpty());

    BitSet unquantifiedVariables = copyBitSet(quantificationBitSet);
    unquantifiedVariables.flip(0, bdd.numberOfVariables());

    assertTrue(Iterators.all(getBitSetIterator(unquantifiedVariables),
      unquantifiedAssignment -> {
        assert unquantifiedAssignment != null;
        boolean bddEvaluation = bdd.evaluate(existsNode, unquantifiedAssignment);
        boolean setEvaluation = Iterators.any(getBitSetIterator(quantificationBitSet),
          bitSet -> {
            if (bitSet == null) {
              return false;
            }
            BitSet actualBitSet = copyBitSet(bitSet);
            actualBitSet.or(unquantifiedAssignment);
            return bdd.evaluate(node, actualBitSet);
          });
        return bddEvaluation == setEvaluation;
      }));
  }

  @Theory(nullsAccepted = false)
  public void testExistsShannon(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    BitSet quantificationBitSet = new BitSet(bdd.numberOfVariables());
    Random quantificationRandom = new Random((long) node);
    for (int i = 0; i < bdd.numberOfVariables(); i++) {
      if (quantificationRandom.nextInt(bdd.numberOfVariables()) < 5) {
        quantificationBitSet.set(i);
      }
    }
    assumeThat(quantificationBitSet.cardinality(), lessThanOrEqualTo(5));

    int existsNode = bdd.existsShannon(node, quantificationBitSet);
    BitSet supportIntersection = bdd.support(existsNode);
    supportIntersection.and(quantificationBitSet);
    assertTrue(supportIntersection.isEmpty());

    BitSet unquantifiedVariables = copyBitSet(quantificationBitSet);
    unquantifiedVariables.flip(0, bdd.numberOfVariables());

    assertTrue(Iterators.all(getBitSetIterator(unquantifiedVariables),
      unquantifiedAssignment -> {
        assert unquantifiedAssignment != null;
        boolean bddEvaluation = bdd.evaluate(existsNode, unquantifiedAssignment);
        boolean setEvaluation = Iterators.any(getBitSetIterator(quantificationBitSet),
          bitSet -> {
            if (bitSet == null) {
              return false;
            }
            BitSet actualBitSet = copyBitSet(bitSet);
            actualBitSet.or(unquantifiedAssignment);
            return bdd.evaluate(node, actualBitSet);
          });
        return bddEvaluation == setEvaluation;
      }));
  }

  @Theory(nullsAccepted = false)
  public void testGetLowAndHigh(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValid(node));
    int low = bdd.getLow(node);
    int high = bdd.getHigh(node);
    if (bdd.isVariableOrNegated(node)) {
      if (bdd.isVariable(node)) {
        assertThat(low, is(bdd.getFalseNode()));
        assertThat(high, is(bdd.getTrueNode()));
      } else {
        assertThat(low, is(bdd.getTrueNode()));
        assertThat(high, is(bdd.getFalseNode()));
      }
    } else {
      ImmutableSet<Integer> rootNodes =
        ImmutableSet.of(bdd.getFalseNode(), bdd.getTrueNode());
      assumeThat(rootNodes.contains(low) && rootNodes.contains(high), is(false));
    }
  }

  @Theory
  public void testGetMinimalSolutions(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    BitSet support = bdd.support(node);
    assumeThat(support.cardinality(), lessThanOrEqualTo(7));

    Set<BitSet> solutionBitSets = new HashSet<>();
    BitSet supportFromSolutions = new BitSet(bdd.numberOfVariables());
    Iterator<BitSet> solutionIterator = bdd.getMinimalSolutions(node);
    BitSet previous = null;
    while (solutionIterator.hasNext()) {
      BitSet bitSet = solutionIterator.next();
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
    Set<BitSet> assignments = new BddPathExplorer(bdd, node).getAssignments();
    assertThat(solutionBitSets, is(assignments));
  }

  @Theory(nullsAccepted = false)
  public void testIfThenElse(TernaryDataPoint dataPoint) {
    int ifNode = dataPoint.getFirst();
    int thenNode = dataPoint.getSecond();
    int elseNode = dataPoint.getThird();
    assumeThat(bdd.isNodeValidOrRoot(ifNode)
      && bdd.isNodeValidOrRoot(thenNode)
      && bdd.isNodeValidOrRoot(elseNode), is(true));

    int ifThenElse = bdd.ifThenElse(ifNode, thenNode, elseNode);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(ifNode, valuation)) {
        assertThat(bdd.evaluate(ifThenElse, valuation), is(bdd.evaluate(thenNode, valuation)));
      } else {
        assertThat(bdd.evaluate(ifThenElse, valuation), is(bdd.evaluate(elseNode, valuation)));
      }
    }

    bdd.pushToWorkStack(ifThenElse);
    int notIf = bdd.pushToWorkStack(bdd.not(ifNode));
    int ifImpliesThen = bdd.pushToWorkStack(bdd.implication(ifNode, thenNode));
    int notIfImpliesThen = bdd.pushToWorkStack(bdd.implication(notIf, elseNode));
    int ifThenElseImplicationConstruction = bdd.and(ifImpliesThen, notIfImpliesThen);
    bdd.popWorkStack(4);
    assertThat("ITE construction failed for " + ifNode + "," + thenNode + "," + elseNode,
      ifThenElse, is(ifThenElseImplicationConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testImplication(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    int implication = bdd.implication(node1, node2);
    for (BitSet valuation : valuations) {
      boolean implies = !bdd.evaluate(node1, valuation) || bdd.evaluate(node2, valuation);
      assertThat(bdd.evaluate(implication, valuation), is(implies));
    }

    bdd.pushToWorkStack(implication);
    int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    int implicationConstruction = bdd.or(notNode1, node2);
    bdd.popWorkStack(2);
    assertThat(implication, is(implicationConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testImplies(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    int implication = bdd.implication(node1, node2);

    if (bdd.implies(node1, node2)) {
      for (BitSet valuation : valuations) {
        assertTrue(!bdd.evaluate(node1, valuation) || bdd.evaluate(node2, valuation));
      }
      assertThat(implication, is(bdd.getTrueNode()));
    } else {
      assertThat(implication, is(not(bdd.getTrueNode())));
    }
  }

  @Theory(nullsAccepted = false)
  public void testIsVariable(UnaryDataPoint dataPoint) {
    SyntaxTree.SyntaxTreeNode rootNode = dataPoint.getTree().getRootNode();
    if (rootNode instanceof SyntaxTree.SyntaxTreeLiteral) {
      assertTrue(bdd.isVariable(dataPoint.getNode()));
      assertTrue(bdd.isVariableOrNegated(dataPoint.getNode()));
    } else if (rootNode instanceof SyntaxTree.SyntaxTreeNot) {
      SyntaxTree.SyntaxTreeNode child = ((SyntaxTree.SyntaxTreeNot) rootNode).getChild();
      if (child instanceof SyntaxTree.SyntaxTreeLiteral) {
        assertThat(bdd.isVariable(dataPoint.getNode()), is(false));
        assertTrue(bdd.isVariableOrNegated(dataPoint.getNode()));
      }
    }
    BitSet support = bdd.support(dataPoint.getNode());
    assertThat(bdd.isVariableOrNegated(dataPoint.getNode()), is(support.cardinality() == 1));
  }

  @Theory(nullsAccepted = false)
  public void testNot(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    int not = bdd.not(node);

    for (BitSet valuation : valuations) {
      assertThat(bdd.evaluate(not, valuation), is(!bdd.evaluate(node, valuation)));
    }

    bdd.pushToWorkStack(not);
    assertThat(bdd.not(not), is(node));
    bdd.popWorkStack();

    bdd.pushToWorkStack(not);
    int notIteConstruction = bdd.ifThenElse(node, bdd.getFalseNode(), bdd.getTrueNode());
    bdd.popWorkStack();
    assertThat(not, is(notIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testNotAnd(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    int notAnd = bdd.notAnd(node1, node2);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(notAnd, valuation), is(!bdd.evaluate(node2, valuation)));
      } else {
        assertTrue(bdd.evaluate(notAnd, valuation));
      }
    }

    bdd.pushToWorkStack(notAnd);
    int node1andNode2 = bdd.pushToWorkStack(bdd.and(node1, node2));
    int notNode1AndNode2 = bdd.not(node1andNode2);
    bdd.popWorkStack(2);
    assertThat(notAnd, is(notNode1AndNode2));

    bdd.pushToWorkStack(notAnd);
    int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    int notAndIteConstruction = bdd.ifThenElse(node1, notNode2, bdd.getTrueNode());
    bdd.popWorkStack(2);
    assertThat(notAnd, is(notAndIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testOr(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    int or = bdd.or(node1, node2);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertTrue(bdd.evaluate(or, valuation));
      } else {
        assertThat(bdd.evaluate(or, valuation), is(bdd.evaluate(node2, valuation)));
      }
    }

    bdd.pushToWorkStack(or);
    int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    int notNode1andNotNode2 = bdd.pushToWorkStack(bdd.and(notNode1, notNode2));
    int orDeMorganConstruction = bdd.not(notNode1andNotNode2);
    bdd.popWorkStack(4);
    assertThat(or, is(orDeMorganConstruction));

    bdd.pushToWorkStack(or);
    int orIteConstruction = bdd.ifThenElse(node1, bdd.getTrueNode(), node2);
    bdd.popWorkStack();
    assertThat(or, is(orIteConstruction));
  }

  @Theory(nullsAccepted = false)
  public void testReferenceAndDereference(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    assumeThat(bdd.isNodeSaturated(node), is(false));

    int referenceCount = bdd.getReferenceCount(node);
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
  public void testReferenceGuard(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    assumeThat(bdd.isNodeSaturated(node), is(false));

    int referenceCount = bdd.getReferenceCount(node);
    try {
      try (Bdd.ReferenceGuard guard = new Bdd.ReferenceGuard(node, bdd)) {
        assertThat(bdd.getReferenceCount(node), is(referenceCount + 1));
        assertThat(guard.getBdd(), is(bdd));
        assertThat(guard.getNode(), is(node));
        //noinspection ThrowCaughtLocally - We deliberately want to test the exception handling here
        throw new IllegalArgumentException("Bogus");
      }
    } catch (IllegalArgumentException ignored) {
      assertThat(bdd.getReferenceCount(node), is(referenceCount));
    }
  }

  @Theory
  public void testRestrict(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));

    Random restrictRandom = new Random((long) node);
    BitSet restrictedVariables = new BitSet(bdd.numberOfVariables());
    BitSet restrictedVariableValues = new BitSet(bdd.numberOfVariables());
    int[] composeArray = new int[bdd.numberOfVariables()];
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
      int restrictNode = bdd.pushToWorkStack(
        bdd.restrict(node, restrictedVariables, restrictedVariableValues));
      int composeNode = bdd.compose(node, composeArray);
      bdd.popWorkStack();

      assertThat(restrictNode, is(composeNode));

      BitSet restrictSupport = bdd.support(restrictNode);
      restrictSupport.and(restrictedVariables);
      assertTrue(restrictSupport.isEmpty());

      restrictedVariables.clear();
      restrictedVariableValues.clear();
    }

  }

  @Theory(nullsAccepted = false)
  public void testSupportTree(UnaryDataPoint dataPoint) {
    int node = dataPoint.getNode();
    assumeTrue(bdd.isNodeValidOrRoot(node));
    IntSet containedVariables = dataPoint.getTree().containedVariables();
    assumeTrue(containedVariables.size() <= 5);

    // For each variable, we iterate through all possible valuations and check if there ever is any
    // difference.
    if (containedVariables.isEmpty()) {
      assertTrue(bdd.support(node).isEmpty());
    } else {
      // Have some arbitrary ordering
      IntList containedVariableList = new IntArrayList(containedVariables);
      BitSet valuation = new BitSet(variableCount);
      BitSet support = new BitSet(variableCount);

      for (int checkedVariable : containedVariableList) {
        int checkedContainedIndex = containedVariableList.indexOf(checkedVariable);
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
          boolean negative = bdd.evaluate(node, valuation);
          valuation.set(checkedVariable);
          boolean positive = bdd.evaluate(node, valuation);
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
  public void testSupportUnion(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    BitSet node1Support = bdd.support(node1);
    BitSet node2Support = bdd.support(node2);
    BitSet supportUnion = copyBitSet(node1Support);
    supportUnion.or(node2Support);

    for (int operationNode : doBddOperations(node1, node2)) {
      BitSet operationSupport = bdd.support(operationNode);
      operationSupport.stream().forEach(setBit -> assertThat(supportUnion.get(setBit), is(true)));
    }
  }

  @Theory(nullsAccepted = false)
  public void testUpdateWith(BinaryDataPoint dataPoint) {
    // This test simply tests if the semantics of updateWith are as specified, i.e.
    // updateWith(result, input) reduces the reference count of the input and increases that of
    // result
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeThat(node1, is(not(node2)));
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));
    assumeFalse(bdd.isNodeSaturated(node1) || bdd.isNodeSaturated(node2));

    bdd.reference(node1);
    bdd.reference(node2);
    int node1referenceCount = bdd.getReferenceCount(node1);
    int node2referenceCount = bdd.getReferenceCount(node2);
    bdd.updateWith(node1, node1);
    assertThat(bdd.getReferenceCount(node1), is(node1referenceCount));

    for (int operationNode : doBddOperations(node1, node2)) {
      if (bdd.isNodeSaturated(operationNode)) {
        continue;
      }
      int operationRefCount = bdd.getReferenceCount(operationNode);
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
  public void testXor(BinaryDataPoint dataPoint) {
    int node1 = dataPoint.getLeft();
    int node2 = dataPoint.getRight();
    assumeTrue(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2));

    int xor = bdd.xor(node1, node2);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(xor, valuation), is(!bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(xor, valuation), is(bdd.evaluate(node2, valuation)));
      }
    }

    bdd.pushToWorkStack(xor);
    int notNode1 = bdd.pushToWorkStack(bdd.not(node1));
    int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    int notNode1AndNode2 = bdd.pushToWorkStack(bdd.and(notNode1, node2));
    int node1andNotNode2 = bdd.pushToWorkStack(bdd.and(node1, notNode2));
    int xorConstruction = bdd.or(node1andNotNode2, notNode1AndNode2);
    bdd.popWorkStack(5);
    assertThat(xor, is(xorConstruction));
  }

  private static final class BddPathExplorer {
    private final Set<BitSet> assignments;
    private final Bdd bdd;

    BddPathExplorer(Bdd bdd, int startingNode) {
      this.bdd = bdd;
      this.assignments = new HashSet<>();
      if (startingNode == bdd.getTrueNode()) {
        assignments.add(new BitSet(bdd.numberOfVariables()));
      } else if (startingNode != bdd.getFalseNode()) {
        IntList path = new IntArrayList();
        path.add(startingNode);
        recurse(path, new BitSet(bdd.numberOfVariables()));
      }
    }

    Set<BitSet> getAssignments() {
      return assignments;
    }

    private void recurse(IntList currentPath, BitSet currentAssignment) {
      int pathLeaf = currentPath.getInt(currentPath.size() - 1);
      int low = bdd.getLow(pathLeaf);
      int high = bdd.getHigh(pathLeaf);

      if (low == bdd.getTrueNode()) {
        assignments.add(copyBitSet(currentAssignment));
      } else if (low != bdd.getFalseNode()) {
        IntList recursePath = new IntArrayList(currentPath);
        recursePath.add(low);
        recurse(recursePath, currentAssignment);
      }

      if (high != bdd.getFalseNode()) {
        BitSet assignment = copyBitSet(currentAssignment);
        assignment.set(bdd.getVariable(pathLeaf));
        if (high == bdd.getTrueNode()) {
          assignments.add(assignment);
        } else {
          IntList recursePath = new IntArrayList(currentPath);
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

    BinaryDataPoint(int left, int right, SyntaxTree leftTree,
      SyntaxTree rightTree) {
      this.left = left;
      this.right = right;
      this.leftTree = leftTree;
      this.rightTree = rightTree;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof BinaryDataPoint)) {
        return false;
      }
      BinaryDataPoint other = (BinaryDataPoint) object;
      return left == other.left && right == other.right;
    }

    int getLeft() {
      return left;
    }

    SyntaxTree getLeftTree() {
      return leftTree;
    }

    int getRight() {
      return right;
    }

    SyntaxTree getRightTree() {
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

    BitSetIterator(BitSet restriction) {
      this(restriction.length(), restriction);
    }

    BitSetIterator(int size, BitSet restriction) {
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

    TernaryDataPoint(int first, int second, int third,
      SyntaxTree firstTree, SyntaxTree secondTree, SyntaxTree thirdTree) {
      this.first = first;
      this.second = second;
      this.third = third;
      this.firstTree = firstTree;
      this.secondTree = secondTree;
      this.thirdTree = thirdTree;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof TernaryDataPoint)) {
        return false;
      }
      TernaryDataPoint other = (TernaryDataPoint) object;
      return first == other.first && second == other.second && third == other.third;
    }

    int getFirst() {
      return first;
    }

    SyntaxTree getFirstTree() {
      return firstTree;
    }

    int getSecond() {
      return second;
    }

    SyntaxTree getSecondTree() {
      return secondTree;
    }

    int getThird() {
      return third;
    }

    SyntaxTree getThirdTree() {
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

    UnaryDataPoint(int node, SyntaxTree tree) {
      this.node = node;
      this.tree = tree;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof UnaryDataPoint)) {
        return false;
      }
      UnaryDataPoint other = (UnaryDataPoint) object;
      return node == other.node;
    }

    int getNode() {
      return node;
    }

    SyntaxTree getTree() {
      return tree;
    }

    @Override
    public int hashCode() {
      return Objects.hash(node);
    }
  }
}
