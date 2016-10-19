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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

public class BddTest {
  private static BitSet buildBitSet(final String values) {
    final BitSet bitSet = new BitSet(values.length());
    final char[] characters = values.toCharArray();
    for (int i = 0; i < characters.length; i++) {
      assert characters[i] == (int) '0' || characters[i] == (int) '1';
      bitSet.set(i, characters[i] == (int) '1');
    }
    return bitSet;
  }

  private static BitSet buildBitSet(final int bits, final int size) {
    final BitSet bitSet = new BitSet(size);
    for (int i = 0; i < size; i++) {
      if ((bits & (1 << i)) != 0) {
        bitSet.set(i);
      }
    }
    return bitSet;
  }

  @Test
  public void testSupport() {
    final Bdd bdd = new BddImpl(10);
    final int v1 = bdd.createVariable();
    final int v2 = bdd.createVariable();
    final int v3 = bdd.createVariable();
    final int v4 = bdd.createVariable();
    final int v5 = bdd.createVariable();

    assertThat(bdd.support(v1), is(buildBitSet("100")));
    assertThat(bdd.support(v2), is(buildBitSet("010")));
    assertThat(bdd.support(v3), is(buildBitSet("001")));

    final List<Integer> variables = Arrays.asList(v1, v2, v3, v4, v5);

    // The snippet below builds various BDDs by evaluating every possible subset of variables,
    // combining the variables in this subset with different operations and then checking that the
    // support of each combination equals the variables of the subset.
    final List<Integer> subset = new ArrayList<>(variables.size());
    for (int i = 1; i < 1 << variables.size(); i++) {
      final BitSet subsetBitSet = buildBitSet(i, variables.size());
      subsetBitSet.stream().forEach(setBit -> subset.add(variables.get(setBit)));

      final Iterator<Integer> variableIterator = subset.iterator();
      int variable = variableIterator.next();
      int and = variable;
      int or = variable;
      int xor = variable;
      int imp = variable;
      int equiv = variable;
      while (variableIterator.hasNext()) {
        variable = variableIterator.next();
        and = bdd.and(and, variable);
        or = bdd.or(or, variable);
        xor = bdd.xor(xor, variable);
        imp = bdd.implication(imp, variable);
        equiv = bdd.equivalence(equiv, variable);
      }
      assertThat(bdd.support(and), is(subsetBitSet));
      assertThat(bdd.support(or), is(subsetBitSet));
      assertThat(bdd.support(xor), is(subsetBitSet));
      assertThat(bdd.support(imp), is(subsetBitSet));
      assertThat(bdd.support(equiv), is(subsetBitSet));
      subset.clear();
    }
  }

  @Test
  public void testIfThenElse() {
    final Bdd bdd = new BddImpl(10);
    final int v1 = bdd.createVariable();
    final int v2 = bdd.createVariable();
    final int v1andv2 = bdd.and(v1, v2);
    assertThat(bdd.ifThenElse(v1, v1, v1), is(v1));
    assertThat(bdd.ifThenElse(v1, v1andv2, v1andv2), is(v1andv2));
    assertThat(bdd.ifThenElse(v1, v1andv2, v2), is(v2));
    assertThat(bdd.ifThenElse(v1, v2, 0), is(bdd.and(v1, v2)));
    assertThat(bdd.ifThenElse(v1, 1, v2), is(bdd.or(v1, v2)));
    assertThat(bdd.ifThenElse(v1, bdd.not(v2), v2), is(bdd.xor(v1, v2)));
    assertThat(bdd.ifThenElse(v1, 0, 1), is(bdd.not(v1)));
    assertThat(bdd.ifThenElse(v1, v2, bdd.not(v2)), is(bdd.equivalence(v1, v2)));
  }

  @SuppressWarnings("ReuseOfLocalVariable")
  @Test
  public void testCompose() {
    final BddImpl bdd = new BddImpl(10);
    final int v1 = bdd.createVariable();
    final int nv1 = bdd.not(v1);
    final int v2 = bdd.createVariable();
    final int v3 = bdd.createVariable();

    final int v2orv3 = bdd.or(v2, v3);
    final int v1andv2 = bdd.and(v1, v2);
    final int v1andv2orv3 = bdd.and(v1, bdd.reference(v2orv3));
    final int nv1andv2orv3 = bdd.and(nv1, bdd.reference(v2orv3));

    int composition = bdd.compose(v1andv2, new int[] {v1, v2, v3});
    assertThat(v1andv2, is(composition));
    composition = bdd.compose(v1andv2, new int[] {v1, v2orv3, v3});
    assertThat(composition, is(v1andv2orv3));
    composition = bdd.compose(composition, new int[] {nv1});
    assertThat(composition, is(nv1andv2orv3));
    composition = bdd.compose(composition, new int[] {nv1});
    assertThat(composition, is(v1andv2orv3));
    composition = bdd.compose(v1andv2, new int[] {v2, v2});
    assertThat(composition, is(v2));
  }

  @Test
  public void internalTest() {
    final BddImpl bdd = new BddImpl(2); // <-- want mucho garbage collections
    final int v1 = bdd.createVariable();
    final int v2 = bdd.createVariable();
    final int v3 = bdd.createVariable();
    final int v4 = bdd.createVariable();

    // check deadnodes counter
    final int dum = bdd.reference(bdd.and(v3, v2));
    assertThat(bdd.getApproximateDeadNodeCount(), is(0));
    bdd.dereference(dum);
    assertThat(1, is(bdd.getApproximateDeadNodeCount()));

    // TODO: add test that throws exception on double free.
    // bdd.dereference(dum);
    // assertThat(bdd.dead_nodes, is(" still one dead node", 1));

    // test garbage collection:
    bdd.grow(); // make sure there is room for it
    final int g1 = bdd.and(v3, v2);
    final int g2 = bdd.reference(bdd.or(g1, v1));
    assertThat(bdd.gc(), is(0));
    bdd.dereference(g2);

    // bdd.show_table();
    assertThat(bdd.gc(), is(2));
    bdd.gc(); // Should free g1 and g2

    final int nv1 = bdd.reference(bdd.not(v1));
    final int nv2 = bdd.reference(bdd.not(v2));

    // and, or, not [MUST REF INTERMEDIATE STUFF OR THEY WILL DISSAPPEAR DURING GC]
    final int n1 = bdd.reference(bdd.and(v1, v2));
    final int orn12 = bdd.reference(bdd.or(nv1, nv2));
    final int n2 = bdd.reference(bdd.not(orn12));
    assertThat(n1, is(n2));

    // XOR:
    final int h1 = bdd.reference(bdd.and(v1, nv2));
    final int h2 = bdd.reference(bdd.and(v2, nv1));
    final int x1 = bdd.reference(bdd.or(h1, h2));
    bdd.dereference(h1);
    bdd.dereference(h2);
    final int x2 = bdd.reference(bdd.xor(v1, v2));
    assertThat(x1, is(x2));
    bdd.dereference(x1);
    bdd.dereference(x2);

    // equivalence
    final int b1 = bdd.or(n1, bdd.and(bdd.not(v1), bdd.not(v2)));
    final int b2 = bdd.equivalence(v1, v2);
    assertThat(b1, is(b2));
    assertThat(bdd.isWorkStackEmpty(), is(true));

    // nodeCount
    assertThat(bdd.nodeCount(0), is(0));
    assertThat(bdd.nodeCount(1), is(0));
    assertThat(bdd.nodeCount(v1), is(1));
    assertThat(bdd.nodeCount(nv2), is(1));
    assertThat(bdd.nodeCount(bdd.and(v1, v2)), is(2));
    assertThat(bdd.nodeCount(bdd.xor(v1, v2)), is(3));

    // approximateNodeCount
    assertThat(bdd.approximateNodeCount(0), is(0));
    assertThat(bdd.approximateNodeCount(1), is(0));
    assertThat(bdd.approximateNodeCount(v1), is(1));
    assertThat(bdd.approximateNodeCount(nv2), is(1));
    assertThat(bdd.approximateNodeCount(bdd.and(v1, v2)), is(2));
    assertThat(bdd.approximateNodeCount(bdd.xor(v1, v2)), is(3));

    final int qs1 = bdd.reference(bdd.xor(v1, v2));
    final int qs2 = bdd.reference(bdd.xor(v3, v4));
    final int qs3 = bdd.reference(bdd.xor(qs1, qs2));
    assertThat(bdd.approximateNodeCount(qs1), is(3));
    assertThat(bdd.approximateNodeCount(qs2), is(3));
    assertThat(bdd.approximateNodeCount(qs3), is(15));
    assertThat(bdd.nodeCount(qs3), is(7));
    bdd.dereference(qs1);
    bdd.dereference(qs2);
    bdd.dereference(qs3);

    // satcount
    assertThat(bdd.countSatisfyingAssignments(0), is(0d));
    assertThat(bdd.countSatisfyingAssignments(1), is(16d));
    assertThat(bdd.countSatisfyingAssignments(v1), is(8d));
    assertThat(bdd.countSatisfyingAssignments(n1), is(4d));
    assertThat(bdd.countSatisfyingAssignments(b1), is(8d));
  }

  @Test
  public void testMember() {
    // TEST MEMBER: taken from the brace/rudell/bryant paper
    final BddImpl bdd = new BddImpl(20);
    final int v1 = bdd.createVariable();
    final int v2 = bdd.createVariable();

    final int p1 = bdd.reference(bdd.and(v1, v2));
    final int p2 = bdd.reference(bdd.or(v1, v2));
    final int p3 = bdd.reference(bdd.and(bdd.not(v1), v2));
    final int p4 = bdd.reference(bdd.and(bdd.not(v2), v1));

    final BitSet valuation = new BitSet(2);
    valuation.set(1);
    assertThat(bdd.evaluate(p1, valuation), is(false));
    assertThat(bdd.evaluate(p2, valuation), is(true));
    assertThat(bdd.evaluate(p3, valuation), is(true));
    assertThat(bdd.evaluate(p4, valuation), is(false));
  }

  @Test
  public void testWorkStack() {
    final BddImpl bdd = new BddImpl(20);
    final int v1 = bdd.createVariable();
    final int v2 = bdd.createVariable();
    final int temporaryNode = bdd.pushToWorkStack(bdd.and(v1, v2));
    bdd.gc();
    assertThat(bdd.isNodeValidOrRoot(temporaryNode), is(true));
    bdd.popWorkStack();
    bdd.gc();
    assertThat(bdd.isNodeValidOrRoot(temporaryNode), is(false));
  }
}
