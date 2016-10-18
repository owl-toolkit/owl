package owl.bdd;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.After;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * Tests various logical functions of BDDs and checks invariants.
 */
@RunWith(Theories.class)
public class BDDTheories {
  private static final BDDImpl bdd = new BDDImpl(10);
  /* The @DataPoints annotated method is called multiple times - which would create new variables
   * each time, exploding the runtime of the tests. */
  private static final List<Integer> bddDataPoints;
  private static final List<BitSet> valuations;
  private static final int initialNodeCount;
  private static final int initialReferencedNodeCount;
  private static final int variableCount = 10;
  private static final float bddLoadFactor = 0.1f;

  static {
    Random filter = new Random(0L);
    IntList variables = new IntArrayList(variableCount);
    for (int i = 0; i < variableCount; i++) {
      variables.add(bdd.createVariable());
    }

    Set<Integer> set = new TreeSet<>(Arrays.asList(bdd.getFalseNode(), bdd.getTrueNode()));
    for (int i = 0; i < variables.size(); i++) {
      set.add(variables.get(i));
      set.add(bdd.reference(bdd.not(variables.get(i))));
      for (int j = i; j < variables.size(); j++) {
        // It is important that the stream is ordered for the tests to be reproducible
        doBinaryOperations(variables.get(i), variables.get(j)).stream()
            .filter(node -> filter.nextFloat() < bddLoadFactor)
            .map(bdd::reference)
            .forEach(node -> set.add(bdd.reference(node)));
      }
    }
    initialNodeCount = bdd.nodeCount();
    initialReferencedNodeCount = bdd.referencedNodeCount();

    ImmutableList.Builder<BitSet> valuationBuilder = ImmutableList.builder();
    for (int i = 0; i < 1 << variables.size(); i++) {
      BitSet bitSet = new BitSet(variables.size());

      for (int j = 0; j < variables.size(); j++) {
        if (((i >>> j) & 1) == 1) {
          bitSet.set(j);
        }
      }
      //noinspection ResultOfMethodCallIgnored
      valuationBuilder.add(bitSet);
    }

    bddDataPoints = ImmutableList.copyOf(set);
    valuations = valuationBuilder.build();
  }

  @DataPoints
  public static List<Integer> bddDataPoints() {
    return ImmutableList.copyOf(bddDataPoints);
  }

  private static BitSet copyBitSet(BitSet bitSet) {
    return BitSet.valueOf(bitSet.toLongArray());
  }

  @SuppressWarnings("TypeMayBeWeakened")
  private static IntSet doBinaryOperations(int node1, int node2) {
    IntSet resultSet = new IntOpenHashSet();
    resultSet.add(bdd.pushToWorkStack(bdd.and(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.or(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.xor(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.equivalence(node1, node2)));
    resultSet.add(bdd.pushToWorkStack(bdd.implication(node1, node2)));
    bdd.popWorkStack(5);
    assertThat(bdd.isWorkStackEmpty(), is(true));
    return resultSet;
  }

  @After
  public void checkInvariants() {
    bdd.check();
    assertThat(bdd.isWorkStackEmpty(), is(true));
    assertThat(bdd.referencedNodeCount(), is(initialReferencedNodeCount));
    assertThat(bdd.nodeCount(), is(initialNodeCount));
  }

  @Theory
  public void testNAnd(int node1, int node2) {
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
    int nAnd = bdd.nAnd(node1, node2);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(nAnd, valuation), is(!bdd.evaluate(node2, valuation)));
      } else {
        assertThat(bdd.evaluate(nAnd, valuation), is(true));
      }
    }

    bdd.pushToWorkStack(nAnd);
    int node1andNode2 = bdd.pushToWorkStack(bdd.and(node1, node2));
    int notNode1AndNode2 = bdd.not(node1andNode2);
    bdd.popWorkStack(2);
    assertThat(nAnd, is(notNode1AndNode2));

    bdd.pushToWorkStack(nAnd);
    int notNode2 = bdd.pushToWorkStack(bdd.not(node2));
    int nAndIteConstruction = bdd.ite(node1, notNode2, bdd.getTrueNode());
    bdd.popWorkStack(2);
    assertThat(nAnd, is(nAndIteConstruction));
  }

  @Theory
  public void testImplication(int node1, int node2) {
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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

  @Theory
  public void testImplies(int node1, int node2) {
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
    int implication = bdd.implication(node1, node2);

    if (bdd.implies(node1, node2)) {
      for (BitSet valuation : valuations) {
        boolean implies = !bdd.evaluate(node1, valuation) || bdd.evaluate(node2, valuation);
        assertThat(node1 + " implies " + node2 + ", but " + valuation + " failed.",
            implies, is(true));
      }

      assertThat(node1 + " implies " + node2 + ", but implication construction not constant one.",
          implication, is(bdd.getTrueNode()));
    } else {
      assertThat(node1 + " does not imply " + node2 + ", but implication construction is " +
          "constant one.", implication, is(not(bdd.getTrueNode())));
    }
  }

  @Theory
  public void testNot(int node) {
    assumeThat(bdd.isNodeValidOrRoot(node), is(true));

    int not = bdd.not(node);

    for (BitSet valuation : valuations) {
      assertThat(bdd.evaluate(not, valuation), is(!bdd.evaluate(node, valuation)));
    }

    bdd.pushToWorkStack(not);
    assertThat(bdd.not(not), is(node));
    bdd.popWorkStack();

    bdd.pushToWorkStack(not);
    int notIteConstruction = bdd.ite(node, bdd.getFalseNode(), bdd.getTrueNode());
    bdd.popWorkStack();
    assertThat(not, is(notIteConstruction));
  }

  @Theory
  public void testIte(int fNode, int gNode, int hNode) {
    assumeThat(bdd.isNodeValidOrRoot(fNode) &&
        bdd.isNodeValidOrRoot(gNode) &&
        bdd.isNodeValidOrRoot(hNode), is(true));

    int ite = bdd.ite(fNode, gNode, hNode);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(fNode, valuation)) {
        assertThat(bdd.evaluate(ite, valuation), is(bdd.evaluate(gNode, valuation)));
      } else {
        assertThat(bdd.evaluate(ite, valuation), is(bdd.evaluate(hNode, valuation)));
      }
    }

    bdd.pushToWorkStack(ite);
    int notF = bdd.pushToWorkStack(bdd.not(fNode));
    int fImpG = bdd.pushToWorkStack(bdd.implication(fNode, gNode));
    int notFImpH = bdd.pushToWorkStack(bdd.implication(notF, hNode));
    int iteImplicationConstruction = bdd.and(fImpG, notFImpH);
    bdd.popWorkStack(4);
    assertThat("ITE construction failed for " + fNode + "," + gNode + "," + hNode,
        ite, is(iteImplicationConstruction));
  }

  @Theory
  public void testAnd(int node1, int node2) {
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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
    int andIteConstruction = bdd.ite(node1, node2, bdd.getFalseNode());
    bdd.popWorkStack();
    assertThat(and, is(andIteConstruction));
  }

  @Theory
  public void testOr(int node1, int node2) {
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

    int or = bdd.or(node1, node2);

    for (BitSet valuation : valuations) {
      if (bdd.evaluate(node1, valuation)) {
        assertThat(bdd.evaluate(or, valuation), is(true));
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
    int orIteConstruction = bdd.ite(node1, bdd.getTrueNode(), node2);
    bdd.popWorkStack();
    assertThat(or, is(orIteConstruction));
  }

  @Theory
  public void testEquivalence(int node1, int node2) {
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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
    int equivalenceIteConstruction = bdd.ite(node1, node2, notNode2);
    bdd.popWorkStack();
    assertThat(equivalence, is(equivalenceIteConstruction));

    bdd.pushToWorkStack(equivalence);
    int node1ImpliesNode2 = bdd.pushToWorkStack(bdd.implication(node1, node2));
    int node2ImpliesNode1 = bdd.pushToWorkStack(bdd.implication(node2, node1));
    int equivalenceBiImplicationConstruction = bdd.and(node1ImpliesNode2, node2ImpliesNode1);
    bdd.popWorkStack(3);
    assertThat(equivalence, is(equivalenceBiImplicationConstruction));
  }

  @Theory
  public void testXor(int node1, int node2) {
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

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

  @Theory
  public void testSupport(int node1, int node2) {
    // TODO This test is very basic. But I think without further knowledge about node1 and node2
    // there is not much of a possibility to deduce the support set (except by replicating the
    // current code - which is kind of pointless in testing). If we implement a possibility to
    // generate all satisfying assignments (which is not hard), we can thoroughly test support.
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));

    BitSet node1Support = bdd.support(node1);
    BitSet node2Support = bdd.support(node2);
    BitSet supportUnion = copyBitSet(node1Support);
    supportUnion.or(node2Support);

    for (int operationNode : doBinaryOperations(node1, node2)) {
      BitSet operationSupport = bdd.support(operationNode);
      operationSupport.stream().forEach(setBit -> assertThat(supportUnion.get(setBit), is(true)));
    }
  }

  @Theory
  public void testReferenceAndDereference(int node) {
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
  public void testConsume(int node1, int node2) {
    // This test simply tests if the semantics of consume are as specified, i.e.
    // consume(result, input1, input2) reduces the reference count of the inputs and increases that
    // of result
    assumeThat(node1, is(not(node2)));
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
    assumeThat(bdd.isNodeSaturated(node1) || bdd.isNodeSaturated(node2), is(false));

    bdd.reference(node1);
    bdd.reference(node2);
    int node1referenceCount = bdd.getReferenceCount(node1);
    int node2referenceCount = bdd.getReferenceCount(node2);

    for (int operationNode : doBinaryOperations(node1, node2)) {
      if (bdd.isNodeSaturated(operationNode)) {
        continue;
      }
      int operationRefCount = bdd.getReferenceCount(operationNode);
      assertThat(bdd.consume(operationNode, node1, node2), is(operationNode));

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

  @Theory
  public void testUpdateWith(int node1, int node2) {
    // This test simply tests if the semantics of updateWith are as specified, i.e.
    // updateWith(result, input) reduces the reference count of the input and increases that of
    // result
    assumeThat(node1, is(not(node2)));
    assumeThat(bdd.isNodeValidOrRoot(node1) && bdd.isNodeValidOrRoot(node2), is(true));
    assumeThat(bdd.isNodeSaturated(node1) || bdd.isNodeSaturated(node2), is(false));

    bdd.reference(node1);
    bdd.reference(node2);
    int node1referenceCount = bdd.getReferenceCount(node1);
    int node2referenceCount = bdd.getReferenceCount(node2);
    bdd.updateWith(node1, node1);
    assertThat(bdd.getReferenceCount(node1), is(node1referenceCount));

    for (int operationNode : doBinaryOperations(node1, node2)) {
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
}
