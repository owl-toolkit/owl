package owl.factories.jdd.bdd;

import java.util.BitSet;
import java.util.Iterator;

public interface Bdd {
  /**
   * Constructs the node representing <tt>{@code node1} AND {@code node2}</tt>.
   */
  int and(int node1, int node2);

  /**
   * Constructs the node representing the <i>composition</i> of the function represented by
   * {@code node} with the functions represented by the entries of {@code variableNodes}. More
   * formally, if <tt>f(x_1, x_2, ..., x_n)</tt> is the function represented by {@code node},
   * this method returns <tt>f(f_1(x_1, ..., x_n), ..., f_n(x_1, ..., x_n))</tt>, where
   * <tt>f_i = {@code variableNodes[i]}</tt>
   * <p>
   * The {@code variableNodes} array can contain less than <tt>n</tt> entries, then only the first
   * variables are replaced. Furthermore, -1 can be used as an entry to denote "don't replace this
   * variable" (which semantically is the same as saying "replace this variable by itself"). Note
   * that after the call the -1 entries will be replaced by the actual corresponding variable nodes.
   * </p>
   *
   * @param node
   *     The node to be composed.
   * @param variableNodes
   *     The nodes of the functions with which each variable should be replaced.
   *
   * @return The node representing the composed function.
   */
  int compose(int node, int[] variableNodes);

  /**
   * Auxiliary function useful for updating node variables. It dereferences the inputs and
   * references {@code result}. This is useful for assignments like {@code node = f(in1, in2)}
   * where <tt>f</tt> is some operation on this BDD and both <tt>in1</tt> and <tt>in2</tt> are
   * temporary nodes or not used anymore. In this case, calling
   * {@code node = consume(bdd, node(in1, in2), in1, in2)} updates the references as needed.
   * <p>This would be more concise when implemented using method references, but these are
   * comparatively heavyweight.</p>
   *
   * @param result
   *     The result of some operation on this BDD involving inputNode1 and inputNode2
   * @param inputNode1
   *     First input of the operation.
   * @param inputNode2
   *     Second input of the operation.
   *
   * @return The given {@code result}.
   */
  default int consume(int result, int inputNode1, int inputNode2) {
    reference(result);
    dereference(inputNode1);
    dereference(inputNode2);
    return result;
  }

  /**
   * Counts the number of satisfying assignments for the function represented by this node.
   * </p>
   * <b>Warning:</b> Floating-point overflow easily possible for complex functions!
   */
  double countSatisfyingAssignments(int node);

  /**
   * Creates a new variable and returns the node representing it.
   *
   * @return The node representing the new variable.
   */
  int createVariable();

  /**
   * Decreases the reference count of the specified {@code node}.
   *
   * @param node
   *     The to be referenced node
   *
   * @return The given node, to be used for chaining.
   */
  int dereference(int node);

  /**
   * Constructs the node representing <tt>{@code node1} EQUIVALENT {@code node2}</tt>.
   */
  int equivalence(int node1, int node2);

  /**
   * Checks whether the given {@code node} evaluates to <tt>true</tt> under the given variable
   * assignment specified by {@code assignment}.
   *
   * @param node
   *     The node to evaluate.
   * @param assignment
   *     The variable assignment.
   *
   * @return The truth value of the node under the given assignment.
   */
  boolean evaluate(int node, BitSet assignment);

  /**
   * Constructs the node representing the function obtained by existential quantification of
   * {@code node} with all variables specified by {@code quantifiedVariables}. Formally, let
   * <tt>f(x_1, ..., x_m)</tt> be the function specified by {@code node} and <tt>x_1, ..., x_m</tt>
   * all variables for which {@code quantifiedVariables} is set. This method then constructs
   * <tt>E x_1 E x_2 ... E x_n f(x_1, ..., x_m)</tt>.
   *
   * @param node
   *     The node representing the basis of the quantification.
   * @param quantifiedVariables
   *     The variables which should be quantified over.
   *
   * @return The node representing the quantification.
   */
  int exists(final int node, BitSet quantifiedVariables);

  /**
   * Returns the node representing <tt>false</tt>.
   */
  int getFalseNode();

  int getHigh(int node);

  int getLow(int node);

  /**
   * Iteratively computes all (minimal) solutions of the function represented by {@code node}. The
   * returned solutions are all bit sets representing a path from node to <tt>true</tt> in the graph
   * induced by the BDD structure. Furthermore, the solutions are generated in lexicographic
   * ascending order.
   * <p> <b>Note:</b> The returned iterator modifies the bit set in place. If all solutions should
   * be gathered into a set or similar, they have to be copied after each call to
   * {@link Iterator#next()}.</p>
   *
   * @param node
   *     The node whose solutions should be computed.
   *
   * @return An iterator returning all minimal solutions in ascending order.
   */
  Iterator<BitSet> getMinimalSolutions(int node);

  /**
   * Returns the node representing <tt>true</tt>.
   */
  int getTrueNode();

  int getVariable(int node);

  /**
   * Constructs the node representing <tt>IF {@code ifNode} THEN {@code thenNode} ELSE
   * {@code elseNode}</tt>.
   */
  int ifThenElse(int ifNode, int thenNode, int elseNode);

  /**
   * Constructs the node representing <tt>{@code node1} IMPLIES {@code node2}</tt>.
   */
  int implication(int node1, int node2);

  /**
   * Checks whether the given {@code node1} implies {@code node2}, i.e. if every valuation under
   * which the function represented by {@code node1} evaluates to true also evaluates to true on
   * {@code node2}. This is equivalent to checking if {@link #implication(int, int)} with
   * {@code node1} and {@code node2} as parameters is equal to {@link #getTrueNode()} and equal to
   * checking whether {@code node1} equals <tt>{@code node1} OR {@code node2}</tt>, but faster.
   * <p><b>Note:</b> As many operations are cached, it may be even faster to use an alternative
   * logical representation of implication depending on how the BDD is used before this invocation.
   * E.g. if <tt>{@code node1} OR {@code 2}</tt> has been computed already, checking if
   * <tt>{@code node1} == {@code node1} OR {@code node2}</tt> is a constant time operation.
   * </p>
   *
   * @param node1
   *     The node representing the assumption.
   * @param node2
   *     The node representing the consequence.
   *
   * @return Whether <tt>{@code node1} IMPLIES {@code node2}</tt> is a tautology.
   */
  boolean implies(int node1, int node2);

  boolean isNodeRoot(int node);

  /**
   * Determines whether the given {@code node} represents a variable.
   *
   * @param node
   *     The node to be checked.
   *
   * @return If the {@code node} represents a variable.
   */
  boolean isVariable(int node);

  /**
   * Determines whether the given {@code node} represents a variable or it's negation.
   *
   * @param node
   *     The node to be checked.
   *
   * @return If the {@code node} represents a variable.
   */
  boolean isVariableOrNegated(int node);

  /**
   * Constructs the node representing <tt>NOT {@code node}</tt>.
   *
   * @param node
   *     The node to be negated.
   *
   * @return The negation of the given BDD.
   */
  int not(int node);

  /**
   * Constructs the node representing <tt>{@code node1} NAND {@code node2}</tt>.
   */
  int notAnd(int node1, int node2);

  /**
   * Returns the number of variables in this BDD.
   *
   * @return The number of variables.
   */
  int numberOfVariables();

  /**
   * Constructs the node representing <tt>{@code node1} OR {@code node2}</tt>.
   */
  int or(int node1, int node2);

  /**
   * Increases the reference count of the specified {@code node}.
   *
   * @param node
   *     The to be referenced node
   *
   * @return The given node, to be used for chaining.
   */
  int reference(int node);

  /**
   * Computes the restriction of the given {@code node}, where all variables specified by
   * {@code restrictedVariables} are replaced by the value given in
   * {@code restrictedVariableValues}. Formally, if {@code node} represents
   * <tt>f(x_1, ..., x_n)</tt>, this method computes the node representing
   * <tt>f(x_1, ..., x_{i_1-1}, c_1, x_{i_1+1}, ..., x_{i_2-1}, c_2, x_{i_2+1}, ..., x_n</tt>, where
   * <tt>i_k</tt> are the elements of the {@code restrictedVariables} set and
   * <tt>c_k := restrictedVariableValues.get(i_k)</tt>. This is semantically equivalent to calling
   * compose where all specified variables are replaced by <tt>true</tt> or <tt>false</tt>, but
   * slightly faster.
   *
   * @param node
   *     The node to be restricted.
   * @param restrictedVariables
   *     The variables used in the restriction.
   * @param restrictedVariableValues
   *     The values of the restricted variables.
   *
   * @return The restricted node.
   *
   * @see #compose(int, int[])
   */
  default int restrict(int node, BitSet restrictedVariables, BitSet restrictedVariableValues) {
    int[] compose = new int[numberOfVariables()];

    for (int i = 0; i < compose.length; i++) {
      if (restrictedVariables.get(i)) {
        if (restrictedVariableValues.get(i)) {
          compose[i] = getTrueNode();
        } else {
          compose[i] = getFalseNode();
        }
      } else {
        compose[i] = -1;
      }
    }

    return compose(node, compose);
  }

  /**
   * Computes the <b>support</b> of the function represented by the given {@code node}. The support
   * of a function are all variables which have an influence on its value.
   *
   * @param node
   *     The node whose support should be computed.
   *
   * @return A bit set where a bit at position {@code i} is set iff the {@code i}-th variable is in
   * the support of {@code node}.
   */
  default BitSet support(final int node) {
    return support(node, numberOfVariables());
  }

  /**
   * Computes the <b>support</b> of the function represented by the given {@code node}. The support
   * of a function are all variables which have an influence on its value.
   *
   * @param node
   *     The node whose support should be computed.
   *
   * @return A bit set where a bit at position {@code i} is set iff the {@code i}-th variable is in
   * the support of {@code node}.
   */
  default BitSet support(final int node, final int highestVariable) {
    BitSet bitSet = new BitSet(highestVariable);
    support(node, bitSet, highestVariable);
    return bitSet;
  }

  /**
   * Computes the <b>support</b> of the given {@code node} and writes it in the {@code bitSet}.
   *
   * @param node
   *     The node whose support should be computed.
   * @param bitSet
   *     The BitSet used to store the result.
   *
   * @see #support(int)
   */
  default void support(final int node, final BitSet bitSet) {
    support(node, bitSet, numberOfVariables());
  }

  /**
   * Computes the <b>support</b> of the given {@code node} and writes it in the {@code bitSet}.
   *
   * @param node
   *     The node whose support should be computed.
   * @param bitSet
   *     The BitSet used to store the result.
   *
   * @see #support(int)
   */
  void support(int node, BitSet bitSet, int highestVariable);

  /**
   * Auxiliary function useful for updating node variables. It dereferences {@code inputNode} and
   * references {@code result}. This is useful for assignments like {@code node = f(node, ...)}
   * where <tt>f</tt> is some operation on the BDD. In this case, calling {@code node =
   * updateWith(bdd, f(node, ...), inputNode)} updates the references as needed and leaves the other
   * parameters untouched.
   * <p>This would be more concise when implemented using method references, but these are
   * comparatively heavyweight.</p>
   *
   * @param result
   *     The result of some operation on this BDD.
   * @param inputNode
   *     The node which gets assigned the value of the result.
   *
   * @return The given {@code result}.
   */
  default int updateWith(int result, int inputNode) {
    reference(result);
    dereference(inputNode);
    return result;
  }

  /**
   * Constructs the node representing <tt>{@code node1} XOR {@code node2}</tt>.
   */
  int xor(int node1, int node2);

  /**
   * A wrapper class to guard some node in an area where exceptions can occur. It increases the
   * reference count of the given node and decreases it when it's closed.
   * </p>
   * Note: This should seldom be used, as the overhead of object construction and deconstruction
   * is noticeable.
   */
  final class ReferenceGuard implements AutoCloseable {
    private final Bdd bdd;
    private final int node;

    public ReferenceGuard(final int node, final Bdd bdd) {
      this.node = bdd.reference(node);
      this.bdd = bdd;
    }

    @Override
    public void close() {
      bdd.dereference(node);
    }

    public Bdd getBdd() {
      return bdd;
    }

    public int getNode() {
      return node;
    }
  }
}
