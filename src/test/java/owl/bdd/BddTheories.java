package owl.bdd;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hamcrest.number.IsCloseTo;
import org.junit.After;
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
  private static final Logger logger = Logger.getLogger(BddTheories.class.getName());
  private static final BddImpl bdd = new BddImpl(10);
  /* The @DataPoints annotated method is called multiple times - which would create new variables
   * each time, exploding the runtime of the tests. */
  private static final Collection<UnaryDataPoint> unaryDataPoints;
  private static final Collection<BinaryDataPoint> binaryDataPoints;
  private static final Collection<TernaryDataPoint> ternaryDataPoints;
  private static final Int2ObjectMap<SyntaxTree> syntaxTreeMap;
  private static final List<BitSet> valuations;
  private static final int initialNodeCount;
  private static final int initialReferencedNodeCount;
  private static final int variableCount = 10;
  private static final int treeDepth = 10;
  private static final int treeWidth = 1000;
  private static final int unaryCount = 5000;
  private static final int binaryCount = 5000;
  private static final int ternaryCount = 5000;

  static {
    final Random filter = new Random(0L);
    final IntList variables = new IntArrayList(variableCount);
    for (int i = 0; i < variableCount; i++) {
      variables.add(bdd.createVariable());
    }

    // It is important other the generation of data is ordered for the tests to be reproducible
    syntaxTreeMap = new Int2ObjectLinkedOpenHashMap<>();

    syntaxTreeMap.put(bdd.getFalseNode(), SyntaxTree.constant(false));
    syntaxTreeMap.put(bdd.getTrueNode(), SyntaxTree.constant(true));
    for (int i = 0; i < variables.size(); i++) {
      final SyntaxTree literal = SyntaxTree.literal(i);
      syntaxTreeMap.put(variables.get(i), literal);
      syntaxTreeMap.put(bdd.reference(bdd.not(variables.get(i))), SyntaxTree.not(literal));
    }

    final Object2IntMap<SyntaxTree> treeToNodeMap = new Object2IntLinkedOpenHashMap<>();
    for (final Map.Entry<Integer, SyntaxTree> treeEntry : syntaxTreeMap.entrySet()) {
      treeToNodeMap.put(treeEntry.getValue(), treeEntry.getKey());
    }
    final Set<SyntaxTree> previousDepthSet = new HashSet<>(treeToNodeMap.keySet());
    final List<SyntaxTree> candidates = new ArrayList<>();
    logger.log(Level.FINER, "Generating syntax trees from {0} base expressions",
        previousDepthSet.size());
    for (int depth = 1; depth < treeDepth; depth++) {
      logger.log(Level.FINEST, "Building tree depth {0}", depth);

      candidates.addAll(previousDepthSet);
      previousDepthSet.clear();
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
          assertThat(bdd.isWorkStackEmpty(), is(true));
          createdFromCandidates.forEach((node, tree) -> {
            if (syntaxTreeMap.containsKey(node)) {
              return;
            }
            bdd.reference(node);
            treeToNodeMap.put(tree, node);
            syntaxTreeMap.put(node, tree);
            previousDepthSet.add(tree);
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
    for (int i = 0; i < 1 << variables.size(); i++) {
      @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
      final BitSet bitSet = new BitSet(variables.size());
      for (int j = 0; j < variables.size(); j++) {
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
  public static List<UnaryDataPoint> unaryDataPoints() {
    return ImmutableList.copyOf(unaryDataPoints);
  }

  @DataPoints
  public static List<BinaryDataPoint> binaryDataPoints() {
    return ImmutableList.copyOf(binaryDataPoints);
  }

  @DataPoints
  public static List<TernaryDataPoint> ternaryDataPoints() {
    return ImmutableList.copyOf(ternaryDataPoints);
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
    assertThat(bdd.isWorkStackEmpty(), is(true));
    return resultSet;
  }

  @After
  public void checkInvariants() {
    assertThat(bdd.isWorkStackEmpty(), is(true));
    if (skipCheckRandom.nextInt(100) == 0) {
      bdd.check();
      assertThat(bdd.referencedNodeCount(), is(initialReferencedNodeCount));
      assertThat(bdd.nodeCount(), is(initialNodeCount));
    }
  }

  @Theory(nullsAccepted = false)
  public void testNotAnd(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
    final int notAnd = bdd.notAnd(node1, node2);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(notAnd, valuation), is(!bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(notAnd, valuation), is(true));
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
  public void testImplication(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
    final int implication = bdd.implication(node1, node2);

    if (bdd.implies(node1, node2)) {
      for (final BitSet valuation : valuations) {
        final boolean implies = !bdd.evaluate(node1, valuation) || bdd.evaluate(node2, valuation);
        assertThat(node1 + " implies " + node2 + ", but " + valuation + " failed.",
            implies, is(true));
      }

      assertThat(node1 + " implies " + node2 + ", but implication construction not constant one.",
          implication, is(bdd.getTrueNode()));
    } else {
      assertThat(node1 + " does not imply " + node2 + ", but implication construction is "
          + "constant one.", implication, is(not(bdd.getTrueNode())));
    }
  }

  @Theory(nullsAccepted = false)
  public void testNot(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));

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
  public void testAnd(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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
  public void testOr(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

    final int or = bdd.or(node1, node2);

    for (final BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(or, valuation), is(true));
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
  public void testEquivalence(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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
  public void testXor(final BinaryDataPoint dataPoint) {
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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

  @Theory(nullsAccepted = false)
  public void testEvaluateTree(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));
    assumeThat(dataPoint.getTree().depth() <= 7, is(true));

    for (final BitSet valuation : valuations) {
      assertThat(bdd.evaluate(node, valuation), is(dataPoint.getTree().evaluate(valuation)));
    }
  }

  @Theory(nullsAccepted = false)
  public void testComposeTree(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));

    final SyntaxTree syntaxTree = dataPoint.getTree();
    final IntSet containedVariables = syntaxTree.containedVariables();
    assumeThat(containedVariables.size() <= 5, is(true));

    final int[] composeArray = new int[variableCount];

    final Random selectionRandom = new Random((long) node);
    final IntList availableNodes = new IntArrayList(syntaxTreeMap.keySet());
    for (int i = 0; i < variableCount; i++) {
      if (containedVariables.contains(i)) {
        final int replacementBddIndex = selectionRandom.nextInt(availableNodes.size());
        composeArray[i] = availableNodes.get(replacementBddIndex);
      } else {
        composeArray[i] = i;
      }
    }
    final Int2ObjectMap<SyntaxTree> replacementMap = new Int2ObjectOpenHashMap<>();
    for (int i = 0; i < composeArray.length; i++) {
      replacementMap.put(i, syntaxTreeMap.get(composeArray[i]));
    }
    final int composedNode = bdd.compose(node, composeArray);
    final SyntaxTree composeTree = SyntaxTree.buildReplacementTree(syntaxTree, replacementMap);
    for (final BitSet valuation : valuations) {
      assertThat(bdd.evaluate(composedNode, valuation), is(composeTree.evaluate(valuation)));
    }
  }

  @Theory(nullsAccepted = false)
  public void testCountSatisfyingAssignments(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));

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
  public void testIsVariable(final UnaryDataPoint dataPoint) {
    final SyntaxTree.SyntaxTreeNode rootNode = dataPoint.getTree().getRootNode();
    if (rootNode instanceof SyntaxTree.SyntaxTreeLiteral) {
      assertThat(bdd.isVariable(dataPoint.getNode()), is(true));
      assertThat(bdd.isVariableOrNegated(dataPoint.getNode()), is(true));
      return;
    }
    if (rootNode instanceof SyntaxTree.SyntaxTreeNot) {
      final SyntaxTree.SyntaxTreeNode child = ((SyntaxTree.SyntaxTreeNot) rootNode).getChild();
      if (child instanceof SyntaxTree.SyntaxTreeLiteral) {
        assertThat(bdd.isVariable(dataPoint.getNode()), is(false));
        assertThat(bdd.isVariableOrNegated(dataPoint.getNode()), is(true));
        return;
      }
    }
    final BitSet support = bdd.support(dataPoint.getNode());
    assertThat(bdd.isVariableOrNegated(dataPoint.getNode()), is(support.cardinality() == 1));
  }

  @Theory(nullsAccepted = false)
  public void testSupportTree(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));
    final IntSet containedVariables = dataPoint.getTree().containedVariables();
    assumeThat(containedVariables.size() <= 5, is(true));

    // For each variable, we iterate through all possible valuations and check if there ever is any
    // difference.
    if (containedVariables.isEmpty()) {
      assertThat(bdd.support(node).isEmpty(), is(true));
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
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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
  public void testGetLowAndHigh(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValid(node), is(true));
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
  @SuppressWarnings("PMD.ExceptionAsFlowControl")
  public void testReferenceGuard(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));
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

  @Theory(nullsAccepted = false)
  public void testReferenceAndDereference(final UnaryDataPoint dataPoint) {
    final int node = dataPoint.getNode();
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));
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

  @Theory(nullsAccepted = false)
  public void testConsume(final BinaryDataPoint dataPoint) {
    // This test simply tests if the semantics of consume are as specified, i.e.
    // consume(result, input1, input2) reduces the reference count of the inputs and increases that
    // of result
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(node1, is(not(node2)));
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
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
  public void testUpdateWith(final BinaryDataPoint dataPoint) {
    // This test simply tests if the semantics of updateWith are as specified, i.e.
    // updateWith(result, input) reduces the reference count of the input and increases that of
    // result
    final int node1 = dataPoint.getLeft();
    final int node2 = dataPoint.getRight();
    assumeThat(node1, is(not(node2)));
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
    assumeThat(bdd.isNodeSaturated(node1) || bdd.isNodeSaturated(node2), is(false));

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

  private static final class UnaryDataPoint {
    private final int node;
    private final SyntaxTree tree;

    public UnaryDataPoint(final int node, final SyntaxTree tree) {
      this.node = node;
      this.tree = tree;
    }

    public int getNode() {
      return node;
    }

    public SyntaxTree getTree() {
      return tree;
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

    @Override
    public int hashCode() {
      return Objects.hash(node);
    }
  }

  @SuppressWarnings("unused")
  private static final class BinaryDataPoint {
    private final int left;
    private final int right;
    private final SyntaxTree leftTree;
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

    @Override
    public int hashCode() {
      return Objects.hash(left, right);
    }

    public int getLeft() {
      return left;
    }

    public int getRight() {
      return right;
    }

    public SyntaxTree getLeftTree() {
      return leftTree;
    }

    public SyntaxTree getRightTree() {
      return rightTree;
    }
  }

  @SuppressWarnings("unused")
  private static final class TernaryDataPoint {
    private final int first;
    private final int second;
    private final int third;
    private final SyntaxTree firstTree;
    private final SyntaxTree secondTree;
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

    @Override
    public int hashCode() {
      return Objects.hash(first, second, third);
    }

    public int getFirst() {
      return first;
    }

    public int getSecond() {
      return second;
    }

    public int getThird() {
      return third;
    }

    public SyntaxTree getFirstTree() {
      return firstTree;
    }

    public SyntaxTree getSecondTree() {
      return secondTree;
    }

    public SyntaxTree getThirdTree() {
      return thirdTree;
    }
  }
}
