package owl.bdd;

import java.util.BitSet;

public interface Bdd {
  /**
   * Constructs the node representing <tt>{@code node1} AND {@code node2}</tt>.
   */
  int and(int node1, int node2);

  @SuppressWarnings({"PMD.UseVarargs"})
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
  default int consume(final int result, final int inputNode1, final int inputNode2) {
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
  int dereference(final int node);

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
   * Returns the node representing <tt>false</tt>.
   */
  int getFalseNode();

  int getHigh(final int node);

  int getLow(final int node);

  /**
   * Returns the node representing <tt>true</tt>.
   */
  int getTrueNode();

  int getVariable(final int node);

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
   * Constructs the node representing <tt>IF {@code ifNode} THEN {@code thenNode} ELSE
   * {@code elseNode}</tt>.
   */
  int ifThenElse(int ifNode, int thenNode, int elseNode);

  /**
   * Constructs the node representing <tt>{@code node1} NAND {@code node2}</tt>.
   */
  int notAnd(int node1, int node2);

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
  int reference(final int node);

  /**
   * Computes the <b>support</b> of the function represented by the given {@code node}. The support
   * of a function are all variables which have an influence on its value.
   *
   * @param node
   *     The node whose support should be computed.
   *
   * @return A bit set where a bit at position {@code i} is set iff the {@code i}-th variable is in
   *     the support of {@code node}.
   */
  BitSet support(int node);

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
  void support(int node, BitSet bitSet);

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
  default int updateWith(final int result, final int inputNode) {
    reference(result);
    dereference(inputNode);
    return result;
  }

  /**
   * Constructs the node representing <tt>{@code node1} XOR {@code node2}</tt>.
   */
  int xor(int node1, int node2);

  boolean isNodeRoot(int node);

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

    public ReferenceGuard(int node, Bdd bdd) {
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
