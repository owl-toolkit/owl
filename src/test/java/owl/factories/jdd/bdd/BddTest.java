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

package owl.factories.jdd.bdd;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

/**
 * A collection of simple tests for the BDD class.
 */
public class BddTest {
  private static BitSet buildBitSet(String values) {
    BitSet bitSet = new BitSet(values.length());
    char[] characters = values.toCharArray();
    for (int i = 0; i < characters.length; i++) {
      assert characters[i] == (int) '0' || characters[i] == (int) '1';
      bitSet.set(i, characters[i] == (int) '1');
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

  /**
   * This is a remainder of the original JDD.
   */
  @Test
  public void internalTest() {
    BddImpl bdd = new BddImpl(2); // <-- want mucho garbage collections
    int v1 = bdd.createVariable();
    int v2 = bdd.createVariable();
    int v3 = bdd.createVariable();
    int v4 = bdd.createVariable();

    // check deadnodes counter
    int dum = bdd.reference(bdd.and(v3, v2));
    assertThat(bdd.getApproximateDeadNodeCount(), is(0));
    bdd.dereference(dum);
    assertThat(1, is(bdd.getApproximateDeadNodeCount()));

    // test garbage collection:
    bdd.grow(); // make sure there is room for it
    int g1 = bdd.and(v3, v2);
    int g2 = bdd.reference(bdd.or(g1, v1));
    assertThat(bdd.forceGc(), is(0));
    bdd.dereference(g2);

    // bdd.show_table();
    assertThat(bdd.forceGc(), is(2));
    bdd.forceGc(); // Should free g1 and g2

    int nv1 = bdd.reference(bdd.not(v1));
    int nv2 = bdd.reference(bdd.not(v2));

    // and, or, not [MUST REF INTERMEDIATE STUFF OR THEY WILL DISSAPPEAR DURING GC]
    int n1 = bdd.reference(bdd.and(v1, v2));
    int orn12 = bdd.reference(bdd.or(nv1, nv2));
    int n2 = bdd.reference(bdd.not(orn12));
    assertThat(n1, is(n2));

    // XOR:
    int h1 = bdd.reference(bdd.and(v1, nv2));
    int h2 = bdd.reference(bdd.and(v2, nv1));
    int x1 = bdd.reference(bdd.or(h1, h2));
    bdd.dereference(h1);
    bdd.dereference(h2);
    int x2 = bdd.reference(bdd.xor(v1, v2));
    assertThat(x1, is(x2));
    bdd.dereference(x1);
    bdd.dereference(x2);

    // equivalence
    int b1 = bdd.or(n1, bdd.and(bdd.not(v1), bdd.not(v2)));
    int b2 = bdd.equivalence(v1, v2);
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

    int qs1 = bdd.reference(bdd.xor(v1, v2));
    int qs2 = bdd.reference(bdd.xor(v3, v4));
    int qs3 = bdd.reference(bdd.xor(qs1, qs2));
    assertThat(bdd.approximateNodeCount(qs1), is(3));
    assertThat(bdd.approximateNodeCount(qs2), is(3));
    assertThat(bdd.approximateNodeCount(qs3), is(15));
    assertThat(bdd.nodeCount(qs3), is(7));
    bdd.dereference(qs1);
    bdd.dereference(qs2);
    bdd.dereference(qs3);

    // satcount
    assertThat(bdd.countSatisfyingAssignments(0), is(0.0d));
    assertThat(bdd.countSatisfyingAssignments(1), is(16.0d));
    assertThat(bdd.countSatisfyingAssignments(v1), is(8.0d));
    assertThat(bdd.countSatisfyingAssignments(n1), is(4.0d));
    assertThat(bdd.countSatisfyingAssignments(b1), is(8.0d));
  }

  @SuppressWarnings("ReuseOfLocalVariable")
  @Test
  public void testCompose() {
    BddImpl bdd = new BddImpl(10);
    int v1 = bdd.createVariable();
    int nv1 = bdd.not(v1);
    int v2 = bdd.createVariable();
    int v3 = bdd.createVariable();

    int v2orv3 = bdd.or(v2, v3);
    int v1andv2 = bdd.and(v1, v2);
    int v1andv2orv3 = bdd.and(v1, bdd.reference(v2orv3));
    int nv1andv2orv3 = bdd.and(nv1, bdd.reference(v2orv3));

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
  public void testIfThenElse() {
    Bdd bdd = new BddImpl(10);
    int v1 = bdd.createVariable();
    int v2 = bdd.createVariable();
    int v1andv2 = bdd.and(v1, v2);
    assertThat(bdd.ifThenElse(v1, v1, v1), is(v1));
    assertThat(bdd.ifThenElse(v1, v1andv2, v1andv2), is(v1andv2));
    assertThat(bdd.ifThenElse(v1, v1andv2, v2), is(v2));
    assertThat(bdd.ifThenElse(v1, v2, 0), is(bdd.and(v1, v2)));
    assertThat(bdd.ifThenElse(v1, 1, v2), is(bdd.or(v1, v2)));
    assertThat(bdd.ifThenElse(v1, bdd.not(v2), v2), is(bdd.xor(v1, v2)));
    assertThat(bdd.ifThenElse(v1, 0, 1), is(bdd.not(v1)));
    assertThat(bdd.ifThenElse(v1, v2, bdd.not(v2)), is(bdd.equivalence(v1, v2)));
  }

  @Test
  public void testMember() {
    // TEST MEMBER: taken from the brace/rudell/bryant paper
    BddImpl bdd = new BddImpl(20);
    int v1 = bdd.createVariable();
    int v2 = bdd.createVariable();

    int p1 = bdd.reference(bdd.and(v1, v2));
    int p2 = bdd.reference(bdd.or(v1, v2));
    int p3 = bdd.reference(bdd.and(bdd.not(v1), v2));
    int p4 = bdd.reference(bdd.and(bdd.not(v2), v1));

    BitSet valuation = new BitSet(2);
    valuation.set(1);
    assertThat(bdd.evaluate(p1, valuation), is(false));
    assertThat(bdd.evaluate(p2, valuation), is(true));
    assertThat(bdd.evaluate(p3, valuation), is(true));
    assertThat(bdd.evaluate(p4, valuation), is(false));
  }

  @Test
  public void testMinimalSolutionsForConstants() {
    Bdd bdd = new BddImpl(20);

    List<BitSet> falseSolutions = Lists.newArrayList(bdd.getMinimalSolutions(bdd.getFalseNode()));
    assertThat(falseSolutions, is(Collections.emptyList()));

    List<BitSet> trueSolutions = Lists.newArrayList(bdd.getMinimalSolutions(bdd.getTrueNode()));
    assertThat(trueSolutions, is(Collections.singletonList(new BitSet())));
  }

  @Test
  public void testSupport() {
    Bdd bdd = new BddImpl(10);
    int v1 = bdd.createVariable();
    int v2 = bdd.createVariable();
    int v3 = bdd.createVariable();
    int v4 = bdd.createVariable();
    int v5 = bdd.createVariable();

    assertThat(bdd.support(v1), is(buildBitSet("100")));
    assertThat(bdd.support(v2), is(buildBitSet("010")));
    assertThat(bdd.support(v3), is(buildBitSet("001")));

    List<Integer> variables = Arrays.asList(v1, v2, v3, v4, v5);

    // The snippet below builds various BDDs by evaluating every possible subset of variables,
    // combining the variables in this subset with different operations and then checking that the
    // support of each combination equals the variables of the subset.
    List<Integer> subset = new ArrayList<>(variables.size());
    for (int i = 1; i < 1 << variables.size(); i++) {
      BitSet subsetBitSet = buildBitSet(i, variables.size());
      subsetBitSet.stream().forEach(setBit -> subset.add(variables.get(setBit)));

      Iterator<Integer> variableIterator = subset.iterator();
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
  public void testWorkStack() {
    BddImpl bdd = new BddImpl(20);
    int v1 = bdd.createVariable();
    int v2 = bdd.createVariable();
    int temporaryNode = bdd.pushToWorkStack(bdd.and(v1, v2));
    bdd.forceGc();
    assertThat(bdd.isNodeValidOrRoot(temporaryNode), is(true));
    bdd.popWorkStack();
    bdd.forceGc();
    assertThat(bdd.isNodeValidOrRoot(temporaryNode), is(false));
  }
}
