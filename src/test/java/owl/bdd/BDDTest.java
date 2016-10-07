/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.bdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

public class BDDTest {
  // TODO rewrite this in many small fragments. At least have > 80% coverage.

  private static boolean testBitSet(BitSet bitSet, boolean[] values) {
    assert values.length <= bitSet.length();
    for (int i = 0; i < values.length; i++) {
      if (bitSet.get(i) != values[i]) {
        return false;
      }
    }
    for (int i = values.length; i < bitSet.length(); i++) {
      if (bitSet.get(i)) {
        return false;
      }
    }
    return true;
  }

  private static BitSet buildBitSet(String values) {
    BitSet bitSet = new BitSet(values.length());
    char[] characters = values.toCharArray();
    for (int i = 0; i < characters.length; i++) {
      assert characters[i] == '0' || characters[i] == '1';
      bitSet.set(i, characters[i] == '1');
    }
    return bitSet;
  }

  private static BitSet buildBitSet(int bits, int size) {
    BitSet bitSet = new BitSet(size);
    for (int i = 0; i < size; i++) {
      if ((bits & (1 << i)) != 0) {
        bitSet.set(i);
      }
    }
    return bitSet;
  }

  @Test
  public void testSupport() {
    BDD bdd = new BDD(10);
    int v1 = bdd.createVar();
    int v2 = bdd.createVar();
    int v3 = bdd.createVar();
    int v4 = bdd.createVar();
    int v5 = bdd.createVar();

    assertEquals(buildBitSet("100"), bdd.support(v1));
    assertEquals(buildBitSet("010"), bdd.support(v2));
    assertEquals(buildBitSet("001"), bdd.support(v3));

    List<Integer> variables = Arrays.asList(v1, v2, v3, v4, v5);

    // The snippet below builds various BDDs by evaluating every possible subset of variables,
    // combining the variables in this subset with different operations and then checking that the
    // support of each combination equals the variables of the subset.
    List<Integer> subset = new ArrayList<>(variables.size());
    for (int i = 1; i < 1 << variables.size(); i++) {
      BitSet subsetBitSet = buildBitSet(i, variables.size());
      subsetBitSet.stream().forEach(setBit -> subset.add(variables.get(setBit)));

      Iterator<Integer> variableIterator = subset.iterator();
      int var = variableIterator.next();
      int and = var;
      int or = var;
      int xor = var;
      int imp = var;
      int equiv = var;
      while (variableIterator.hasNext()) {
        var = variableIterator.next();
        and = bdd.and(and, var);
        or = bdd.or(or, var);
        xor = bdd.xor(xor, var);
        imp = bdd.implication(imp, var);
        equiv = bdd.equivalence(equiv, var);
      }
      assertEquals(subsetBitSet, bdd.support(and));
      assertEquals(subsetBitSet, bdd.support(or));
      assertEquals(subsetBitSet, bdd.support(xor));
      assertEquals(subsetBitSet, bdd.support(imp));
      assertEquals(subsetBitSet, bdd.support(equiv));
      subset.clear();
    }
  }

  @Test
  public void testITE() {
    BDD bdd = new BDD(10);
    int v1 = bdd.createVar();
    int v2 = bdd.createVar();
    int v1andv2 = bdd.and(v1, v2);
    assertEquals(v1, bdd.ite(v1, v1, v1));
    assertEquals(v1andv2, bdd.ite(v1, v1andv2, v1andv2));
    assertEquals(v2, bdd.ite(v1, v1andv2, v2));
    assertEquals(bdd.and(v1, v2), bdd.ite(v1, v2, 0));
    assertEquals(bdd.or(v1, v2), bdd.ite(v1, 1, v2));
    assertEquals(bdd.xor(v1, v2), bdd.ite(v1, bdd.not(v2), v2));
    assertEquals(bdd.not(v1), bdd.ite(v1, 0, 1));
    assertEquals(bdd.equivalence(v1, v2), bdd.ite(v1, v2, bdd.not(v2)));
  }

  @Test
  public void testCompose() {
    BDD bdd = new BDD(10);
    int v1 = bdd.createVar();
    int nv1 = bdd.not(v1);
    int v2 = bdd.createVar();
    int v3 = bdd.createVar();

    int v2orv3 = bdd.or(v2, v3);
    int v1andv2 = bdd.and(v1, v2);
    int v1andv2orv3 = bdd.and(v1, bdd.ref(v2orv3));
    int nv1andv2orv3 = bdd.and(nv1, bdd.ref(v2orv3));

    int[] composeArray = {v1, v2, v3};
    int composition = bdd.compose(v1andv2, composeArray);
    assertEquals(composition, v1andv2);

    composeArray = new int[] {v1, v2orv3, v3};
    composition = bdd.compose(v1andv2, composeArray);
    assertEquals(v1andv2orv3, composition);

    composeArray = new int[] {nv1};
    composition = bdd.compose(composition, composeArray);
    assertEquals(nv1andv2orv3, composition);
    composeArray = new int[] {nv1};
    composition = bdd.compose(composition, composeArray);
    assertEquals(v1andv2orv3, composition);

    composeArray = new int[] {v2, v2};
    composition = bdd.compose(v1andv2, composeArray);
    assertEquals(v2, composition);
  }

  @Test
  public void internalTest() {
    BDD bdd = new BDD(2); // <-- want mucho garbage collections
    int v1 = bdd.createVar();
    int v2 = bdd.createVar();
    int v3 = bdd.createVar();
    int v4 = bdd.createVar();

    // check deadnodes counter
    int dum = bdd.ref(bdd.and(v3, v2));
    assertEquals("no dead nodes at start", 0, bdd.getApproximateDeadNodeCount());
    bdd.deref(dum);
    assertEquals(bdd.getApproximateDeadNodeCount(), 1);

    // TODO: add test that throws exception on double free.
    // bdd.deref(dum);
    // assertEquals(" still one dead node", 1, bdd.dead_nodes);

    // test garbage collection:
    bdd.grow(); // make sure there is room for it
    int g1 = bdd.and(v3, v2);
    int g2 = bdd.ref(bdd.or(g1, v1));
    assertEquals("should not free g1 (recusrive dep)", 0, bdd.gc());
    bdd.deref(g2);

    // bdd.show_table();
    assertEquals("should free g2 thus also g1 (recusrive dep)", 2, bdd.gc());
    bdd.gc(); // Should free g1 and g2

    int nv1 = bdd.ref(bdd.not(v1));
    int nv2 = bdd.ref(bdd.not(v2));

    // and, or, not [MUST REF INTERMEDIATE STUFF OR THEY WILL DISSAPPEAR DURING GC]
    int n1 = bdd.ref(bdd.and(v1, v2));
    int orn12 = bdd.ref(bdd.or(nv1, nv2));
    int n2 = bdd.ref(bdd.not(orn12));
    assertEquals("BDD canonicity (and/or/not)", n2, n1);

    // XOR:
    int h1 = bdd.ref(bdd.and(v1, nv2));
    int h2 = bdd.ref(bdd.and(v2, nv1));
    int x1 = bdd.ref(bdd.or(h1, h2));
    bdd.deref(h1);
    bdd.deref(h2);
    int x2 = bdd.ref(bdd.xor(v1, v2));
    assertEquals("BDD canonicity (XOR)", x2, x1);
    bdd.deref(x1);
    bdd.deref(x2);

    // equivalence
    int b1 = bdd.or(n1, bdd.and(bdd.not(v1), bdd.not(v2)));
    int b2 = bdd.equivalence(v1, v2);
    assertEquals("BDD canonicity (equivalence)", b2, b1);

    assertTrue("workset stack should be empty", bdd.isWorkStackEmpty());

    // nodeCount
    assertEquals("nodeCount (1)", 0, bdd.nodeCount(0));
    assertEquals("nodeCount (2)", 0, bdd.nodeCount(1));
    assertEquals("nodeCount (3)", 1, bdd.nodeCount(v1));
    assertEquals("nodeCount (4)", 1, bdd.nodeCount(nv2));
    assertEquals("nodeCount (5)", 2, bdd.nodeCount(bdd.and(v1, v2)));
    assertEquals("nodeCount (6)", 3, bdd.nodeCount(bdd.xor(v1, v2)));

    // approximateNodeCount
    assertEquals("approximateNodeCount (1)", 0, bdd.approximateNodeCount(0));
    assertEquals("approximateNodeCount (2)", 0, bdd.approximateNodeCount(1));
    assertEquals("approximateNodeCount (3)", 1, bdd.approximateNodeCount(v1));
    assertEquals("approximateNodeCount (4)", 1, bdd.approximateNodeCount(nv2));
    assertEquals("approximateNodeCount (5)", 2, bdd.approximateNodeCount(bdd.and(v1, v2)));
    assertEquals("approximateNodeCount (6)", 3, bdd.approximateNodeCount(bdd.xor(v1, v2)));

    // this shows the difference
    int qs1 = bdd.ref(bdd.xor(v1, v2));
    int qs2 = bdd.ref(bdd.xor(v3, v4));
    int qs3 = bdd.ref(bdd.xor(qs1, qs2));
    assertEquals("approximateNodeCount (7)", 3, bdd.approximateNodeCount(qs1));
    assertEquals("approximateNodeCount (8)", 3, bdd.approximateNodeCount(qs2));
    assertEquals("approximateNodeCount (9)", 15, bdd.approximateNodeCount(qs3));
    assertEquals("nodeCount (7)", 7, bdd.nodeCount(qs3));
    bdd.deref(qs1);
    bdd.deref(qs2);
    bdd.deref(qs3);

    // satcount
    assertEquals("countSatisfyingAssignments(0)", (double) 0, bdd.countSatisfyingAssignments(0),
        0.00001);
    assertEquals("countSatisfyingAssignments(1)", (double) 16, bdd.countSatisfyingAssignments(1),
        0.00001);
    assertEquals("countSatisfyingAssignments(v1)", (double) 8, bdd.countSatisfyingAssignments(v1),
        0.00001);
    assertEquals("countSatisfyingAssignments(n1)", (double) 4, bdd.countSatisfyingAssignments(n1),
        0.00001);
    assertEquals("countSatisfyingAssignments(b1)", (double) 8, bdd.countSatisfyingAssignments(b1),
        0.00001);

    // test relProd:
    int rel0 = bdd.ref(bdd.xor(v1, v2));

    int reltmp = bdd.ref(bdd.and(rel0, v1));
    bdd.deref(reltmp);

    reltmp = bdd.ref(bdd.and(rel0, nv1));
    bdd.deref(reltmp);
  }

  @Test
  public void testMember() {
    // TEST MEMBER: taken from the brace/rudell/bryant paper
    BDD bdd = new BDD(20);
    int v1 = bdd.createVar();
    int v2 = bdd.createVar();

    int p1 = bdd.ref(bdd.and(v1, v2));
    int p2 = bdd.ref(bdd.or(v1, v2));
    int p3 = bdd.ref(bdd.and(bdd.not(v1), v2));
    int p4 = bdd.ref(bdd.and(bdd.not(v2), v1));

    BitSet valuation = new BitSet(2);
    valuation.set(1);
    assertFalse(bdd.evaluate(p1, valuation));
    assertTrue(bdd.evaluate(p2, valuation));
    assertTrue(bdd.evaluate(p3, valuation));
    assertFalse(bdd.evaluate(p4, valuation));
  }

  @Test
  public void testWorkStack() {
    BDD bdd = new BDD(20);
    int v1 = bdd.createVar();
    int v2 = bdd.createVar();
    int temporaryNode = bdd.pushToWorkStack(bdd.and(v1, v2));
    bdd.gc();
    assertTrue(bdd.isNodeValidOrRoot(temporaryNode));
    bdd.popWorkStack();
    bdd.gc();
    assertFalse(bdd.isNodeValidOrRoot(temporaryNode));
  }
}
