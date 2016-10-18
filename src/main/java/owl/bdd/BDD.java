// TODO:
//
//
// 1. examining the PCK-rate [procent cache entries kept after garbage collection] is
//    much higher for operatorCache [90 % for 10xQueens] than neg_cache [<3% same example].
//    yhis must have to do with the loadrate [100% and 2.5% resp.], do we really need to
//    test-and-invalidateStore caches when the load-factor is so low??
//
// 2. BDDTrace does very bad on dme1.trace, it stopps at "and(lv_1870, lv_1877); % 17867" !!!
//     (check it out in the owl.internal.bug package)
//
// 3. possible bug: if the number of variables is changed, do we need to clear the satCache??
//
// 4. replace and company look a bit sloow. try to optimize it
//
// 5. see if we can share some caches
//
// 6. why are not we partially cleaning the "relativeProductCache"?
//
// 7. SAT is slow, find out why!
//
//

package owl.bdd;

import java.util.BitSet;

public class BDD extends NodeTable {
  /* Implementation notes: Many of the methods are practically copy-paste of each other except for
   * a few variables, as the structure of BDD algorithms is the same for most of the operations. */
  public static final int ONE = 1;
  public static final int ZERO = 0;
  private final BDDCache cache;
  private int numberOfVariables;

  public BDD(final int nodeSize) {
    super(Util.prevPrime(nodeSize), ImmutableBDDConfiguration.builder().build());
    cache = new BDDCache(this);
    numberOfVariables = 0;
  }

  /**
   * Auxiliary function useful for updating Node variables. It dereferences {@code inputNode} and
   * references {@code result}. This is useful for assignments like {@code node = f(node, ...)}
   * where <tt>f</tt> is some operation on the BDD. In this case, calling {@code node =
   * updateWith(f(node, ...), inputNode)} updates the references as needed and leaves the other
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
  public int updateWith(final int result, final int inputNode) {
    ref(result);
    deref(inputNode);
    return result;
  }

  /**
   * Auxiliary function useful for updating node variables. It dereferences the inputs and
   * references {@code result}. This is useful for assignments like {@code node = f(in1, in2)}
   * where <tt>f</tt> is some operation on this BDD and both <tt>in1</tt> and <tt>in2</tt> are
   * temporary nodes or not used anymore. In this case, calling
   * {@code node = consume(node(in1, in2), in1, in2)} updates the references as needed.
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
  public int consume(final int result, final int inputNode1, final int inputNode2) {
    ref(result);
    deref(inputNode1);
    deref(inputNode2);
    return result;
  }

  /**
   * Returns the number of variables in this BDD.
   *
   * @return The number of variables.
   */
  public final int numberOfVariables() {
    return numberOfVariables;
  }

  /**
   * Creates a new variable and returns the node representing it.
   *
   * @return The node representing the new variable.
   */
  public final int createVar() {
    int variableNode = makeNode(numberOfVariables, 0, 1);
    saturateNode(variableNode);
    int notVariableNode = makeNode(numberOfVariables, 1, 0);
    saturateNode(notVariableNode);
    numberOfVariables++;

    cache.putNot(variableNode, notVariableNode);
    cache.putNot(notVariableNode, variableNode);
    growTree(numberOfVariables);
    cache.invalidateSatisfaction();

    return variableNode;
  }

  public final int compose(final int node, final int[] variableNodes) {
    assert variableNodes.length <= numberOfVariables;

    pushToWorkStack(node);
    for (int variableNode : variableNodes) {
      assert isNodeValidOrRoot(variableNode);
      assert getVariable(variableNode) < variableNodes.length;
      pushToWorkStack(variableNode);
    }
    int result = composeRecursive(node, variableNodes);
    popWorkStack(variableNodes.length + 1);
    return result;
  }

  /**
   * Constructs the node representing <tt>IF {@code fNode} THEN {@code gNode} ELSE {@code hNode}
   * </tt>.
   */
  public final int ite(final int fNode, final int gNode, final int hNode) {
    assert isNodeValidOrRoot(fNode) && isNodeValidOrRoot(gNode) && isNodeValidOrRoot(hNode);
    pushToWorkStack(fNode);
    pushToWorkStack(gNode);
    pushToWorkStack(hNode);
    final int result = iteRecursive(fNode, gNode, hNode);
    popWorkStack(3);
    return result;
  }

  /**
   * Constructs the node representing <tt>{@code node1} AND {@code node2}</tt>.
   */
  public final int and(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int result = andRecursive(node1, node2);
    popWorkStack(2);
    return result;
  }

  /**
   * Constructs the node representing <tt>{@code node1} OR {@code node2}</tt>.
   */
  public final int or(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int result = orRecursive(node1, node2);
    popWorkStack(2);
    return result;
  }

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
  public boolean evaluate(final int node, final BitSet assignment) {
    int currentBdd = node;
    while (currentBdd >= 2) {
      final long currentBddStore = getNodeStore(currentBdd);
      final int currentBddVariable = (int) getVariableFromStore(currentBddStore);
      if (assignment.get(currentBddVariable)) {
        currentBdd = (int) getHighFromStore(currentBddStore);
      } else {
        currentBdd = (int) getLowFromStore(currentBddStore);
      }
    }
    return currentBdd == ONE;
  }

  /**
   * Constructs the node representing <tt>{@code node1} NAND {@code node2}</tt>.
   */
  public final int nAnd(int node1, int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = nAndRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  /**
   * Constructs the node representing <tt>{@code node1} XOR {@code node2}</tt>.
   */
  public final int xor(int node1, int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = xorRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  /**
   * Constructs the node representing <tt>{@code node1} EQUIVALENT {@code node2}</tt>.
   */
  public final int equivalence(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = equivalenceRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  /**
   * Constructs the node representing <tt>{@code node1} IMPLIES {@code node2}</tt>.
   */
  public final int implication(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = implicationRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  /**
   * Checks whether the given {@code node1} implies {@code node2}, i.e. if every valuation under
   * which the function represented by {@code node1} evaluates to true also evaluates to true on
   * {@code node2}. This is equivalent to checking if {@link #implication(int, int)} with
   * {@code node1} and {@code node2} as parameters is equal to {@link #ONE} and equal to checking
   * whether {@code node1} equals <tt>{@code node1} OR {@code node2}</tt>, but faster.
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
  public final boolean implies(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    return impliesRecursive(node1, node2);
  }

  /**
   * Constructs the node representing <tt>NOT {@code node}</tt>.
   *
   * @param node
   *     The node to be negated.
   *
   * @return The negation of the given BDD.
   */
  public final int not(int node) {
    assert isNodeValidOrRoot(node);
    pushToWorkStack(node);
    int ret = notRecursive(node);
    popWorkStack();
    return ret;
  }

  /**
   * Counts the number of satisfying assignments for the function represented by this node.
   * </p>
   * <b>Warning:</b> Floating-point overflow easily possible for complex functions!
   */
  public final double countSatisfyingAssignments(int node) {
    // TODO Add overflow checks, an int version and a BigInteger version
    if (node == ZERO) {
      return 0d;
    }
    if (node == ONE) {
      //noinspection MagicNumber
      return Math.pow(2d, (double) numberOfVariables);
    }
    final long nodeStore = getNodeStore(node);
    final double variable = (double) getVariableFromStore(nodeStore);
    //noinspection MagicNumber
    return Math.pow(2d, variable) * countSatisfyingAssignmentsRecursive(node);
  }

  /**
   * Determines whether the given {@code node} represents a variable.
   *
   * @param node
   *     The node to be checked.
   *
   * @return If the {@code node} represents a variable.
   */
  public final boolean isVariable(final int node) {
    if (isNodeRoot(node)) {
      return false;
    }
    long nodeStore = getNodeStore(node);
    return (int) getLowFromStore(nodeStore) == ZERO && (int) getHighFromStore(nodeStore) == ONE;
  }

  /**
   * Determines whether the given {@code node} represents a variable or it's negation.
   *
   * @param node
   *     The node to be checked.
   *
   * @return If the {@code node} represents a variable.
   */
  public final boolean isVariableOrNegated(final int node) {
    if (!isNodeValidOrRoot(node)) {
      return false;
    }
    if (isNodeRoot(node)) {
      return false;
    }
    final long nodeStore = getNodeStore(node);
    final int low = (int) getLowFromStore(nodeStore);
    final int high = (int) getHighFromStore(nodeStore);
    return low == ZERO && high == ONE ||
        low == ONE && high == ZERO;
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
  public final BitSet support(final int node) {
    assert isNodeValidOrRoot(node);
    final BitSet bitSet = new BitSet(numberOfVariables);
    support(node, bitSet);
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
  public final void support(final int node, final BitSet bitSet) {
    bitSet.clear();
    supportRecursive(node, bitSet);
    unmarkTree(node);
  }

  final String getCacheStatistics() {
    return cache.getStatistics();
  }

  @Override
  final void postRemovalCallback() {
    cache.invalidate();
  }

  private double countSatisfyingAssignmentsRecursive(final int node) {
    if (node == ZERO) {
      return 0d;
    }
    if (node == ONE) {
      return 1d;
    }

    final double cacheLookup = cache.lookupSatisfaction(node);
    if (cacheLookup >= 0d) {
      return cacheLookup;
    }
    final int hash = cache.getLookupHash();

    final long nodeStore = getNodeStore(node);
    final int nodeVar = (int) getVariableFromStore(nodeStore);

    final int lowNode = (int) getLowFromStore(nodeStore);
    final double lowCount;
    if (lowNode == ZERO) {
      lowCount = 0d;
    } else if (lowNode == ONE) {
      lowCount = Math.pow(2d, (double) (numberOfVariables - nodeVar - 1));
    } else {
      final long lowStore = getNodeStore(lowNode);
      final int lowVar = (int) getVariableFromStore(lowStore);
      lowCount = countSatisfyingAssignmentsRecursive(lowNode) *
          Math.pow(2d, (double) (lowVar - nodeVar - 1));
    }

    final int highNode = (int) getHighFromStore(nodeStore);
    final double highCount;
    if (highNode == ZERO) {
      highCount = 0d;
    } else if (highNode == ONE) {
      highCount = Math.pow(2d, (double) (numberOfVariables - nodeVar - 1));
    } else {
      final long highStore = getNodeStore(highNode);
      final int highVar = (int) getVariableFromStore(highStore);
      highCount = countSatisfyingAssignmentsRecursive(highNode) *
          Math.pow(2d, (double) (highVar - nodeVar - 1));
    }

    final double result = lowCount + highCount;
    cache.putSatisfaction(hash, node, result);
    return result;
  }

  private void supportRecursive(final int node, final BitSet bitSet) {
    if (isNodeRoot(node)) {
      return;
    }
    final long nodeStore = getNodeStore(node);
    if (isNodeStoreMarked(nodeStore)) {
      return;
    }
    bitSet.set((int) getVariableFromStore(nodeStore));
    markNode(node);
    supportRecursive((int) getLowFromStore(nodeStore), bitSet);
    supportRecursive((int) getHighFromStore(nodeStore), bitSet);
  }

  private int composeRecursive(final int node, final int[] variableNodes) {
    if (node == ONE) {
      return node;
    }
    if (node == ZERO) {
      return node;
    }

    final long nodeStore = getNodeStore(node);
    final int nodeVariable = (int) getVariableFromStore(nodeStore);
    if (nodeVariable >= variableNodes.length) {
      return node;
    }
    // TODO Caches
    final int lowCompose = composeRecursive((int) getLowFromStore(nodeStore), variableNodes);
    final int highCompose = composeRecursive((int) getHighFromStore(nodeStore), variableNodes);
    return ite(variableNodes[nodeVariable], highCompose, lowCompose);
  }

  private int makeNode(int variable, int low, int high) {
    if (low == high) {
      return low;
    } else {
      return add(variable, low, high);
    }
  }

  private int iteRecursive(int fNode, int gNode, int hNode) {
    if (fNode == 1) {
      return gNode;
    }
    if (fNode == 0) {
      return hNode;
    }
    if (gNode == hNode) {
      return gNode;
    }
    if (gNode == 1) {
      if (hNode == 0) {
        return fNode;
      }
      return orRecursive(fNode, hNode);
    }
    if (gNode == 0) {
      if (hNode == 1) {
        return notRecursive(fNode);
      }
      int result = andRecursive(pushToWorkStack(notRecursive(fNode)), hNode);
      popWorkStack();
      return result;
    }

    if (hNode == 0) {
      return andRecursive(fNode, gNode);
    }
    if (hNode == 1) {
      int result = nAndRecursive(fNode, pushToWorkStack(notRecursive(gNode)));
      popWorkStack();
      return result;
    }

    if (cache.lookupITE(fNode, gNode, hNode)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final long fStore = getNodeStore(fNode);
    final long gStore = getNodeStore(gNode);
    final long hStore = getNodeStore(hNode);

    final int fVar = (int) getVariableFromStore(fStore);
    final int gVar = (int) getVariableFromStore(gStore);
    final int hVar = (int) getVariableFromStore(hStore);

    final int minVar = Math.min(fVar, Math.min(gVar, hVar));
    final int fLowNode;
    final int fHighNode;
    final int gLowNode;
    final int gHighNode;
    final int hLowNode;
    final int hHighNode;

    if (fVar == minVar) {
      fLowNode = (int) getLowFromStore(fStore);
      fHighNode = (int) getHighFromStore(fStore);
    } else {
      fLowNode = fNode;
      fHighNode = fNode;
    }
    if (gVar == minVar) {
      gLowNode = (int) getLowFromStore(gStore);
      gHighNode = (int) getHighFromStore(gStore);
    } else {
      gLowNode = gNode;
      gHighNode = gNode;
    }
    if (hVar == minVar) {
      hLowNode = (int) getLowFromStore(hStore);
      hHighNode = (int) getHighFromStore(hStore);
    } else {
      hLowNode = hNode;
      hHighNode = hNode;
    }

    final int lowNode = pushToWorkStack(iteRecursive(fLowNode, gLowNode, hLowNode));
    final int highNode = pushToWorkStack(iteRecursive(fHighNode, gHighNode, hHighNode));
    final int result = makeNode(minVar, lowNode, highNode);
    popWorkStack(2);
    cache.putITE(hash, fNode, gNode, hNode, result);
    return result;
  }

  private int andRecursive(int node1, int node2) {
    if (node1 == node2 || node2 == ONE) {
      return node1;
    }
    if (node1 == ZERO || node2 == ZERO) {
      return 0;
    }
    if (node1 == ONE) {
      return node2;
    }

    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupAnd(node2, node1)) {
        // We have a cache hit for this operation
        return cache.getLookupResult();
      }
      final int hash = cache.getLookupHash();

      // Guard the result - the recursive calls may cause the table to grow, kicking off a gc.
      // If the produced variables are not guarded, they may get invalidated.
      final int lowNode = pushToWorkStack(andRecursive((int) getLowFromStore(node2store), node1));
      final int highNode = pushToWorkStack(andRecursive((int) getHighFromStore(node2store), node1));
      final int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putAnd(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupAnd(node1, node2)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final int lowNode;
    final int highNode;
    if (node1var == node2var) {
      lowNode = andRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = andRecursive((int) getHighFromStore(node1store),
          (int) getHighFromStore(node2store));
      pushToWorkStack(highNode);
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(andRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(andRecursive((int) getHighFromStore(node1store), node2));
    }
    final int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putAnd(hash, node1, node2, resultNode);
    return resultNode;
  }

  private int orRecursive(int node1, int node2) {
    if (node1 == ONE || node2 == ONE) {
      return 1;
    }
    if (node1 == ZERO || node1 == node2) {
      return node2;
    }
    if (node2 == ZERO) {
      return node1;
    }

    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupOr(node2, node1)) {
        return cache.getLookupResult();
      }
      final int hash = cache.getLookupHash();
      final int lowNode = pushToWorkStack(orRecursive((int) getLowFromStore(node2store), node1));
      final int highNode = pushToWorkStack(orRecursive((int) getHighFromStore(node2store), node1));
      final int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putOr(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupOr(node1, node2)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final int lowNode;
    final int highNode;
    if (node1var == node2var) {
      lowNode = orRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = pushToWorkStack(orRecursive((int) getHighFromStore(node1store),
          (int) getHighFromStore(node2store)));
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(orRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(orRecursive((int) getHighFromStore(node1store), node2));
    }
    final int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putOr(hash, node1, node2, resultNode);
    return resultNode;
  }

  private int xorRecursive(final int node1, final int node2) {
    if (node1 == node2) {
      return 0;
    }
    if (node1 == ZERO) {
      return node2;
    }
    if (node2 == ZERO) {
      return node1;
    }
    if (node1 == ONE) {
      return notRecursive(node2);
    }
    if (node2 == ONE) {
      return notRecursive(node1);
    }

    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupXor(node2, node1)) {
        return cache.getLookupResult();
      }
      final int hash = cache.getLookupHash();
      final int lowNode = pushToWorkStack(xorRecursive((int) getLowFromStore(node2store), node1));
      final int highNode = pushToWorkStack(xorRecursive((int) getHighFromStore(node2store), node1));
      final int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putXor(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupXor(node1, node2)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final int lowNode;
    final int highNode;
    if (node1var == node2var) {
      lowNode = xorRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = pushToWorkStack(xorRecursive((int) getHighFromStore(node1store),
          (int) getHighFromStore(node2store)));
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(xorRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(xorRecursive((int) getHighFromStore(node1store), node2));
    }
    final int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putXor(hash, node1, node2, resultNode);
    return resultNode;
  }

  private boolean impliesRecursive(final int node1, final int node2) {
    if (node1 == ZERO) {
      // False implies anything
      return true;
    }
    if (node2 == ZERO) {
      // node1 != ZERO
      return false;
    }
    if (node2 == ONE) {
      // node1 != ZERO
      return true;
    }
    if (node1 == ONE) {
      // node2 != ONE
      return false;
    }
    if (node1 == node2) {
      // Trivial implication
      return true;
    }

    if (cache.lookupImplication(node1, node2)) {
      return cache.getLookupResult() == ONE;
    }
    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (node1var == node2var) {
      return
          impliesRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store)) &&
              impliesRecursive((int) getHighFromStore(node1store),
                  (int) getHighFromStore(node2store));
    } else if (node1var < node2var) {
      return impliesRecursive((int) getLowFromStore(node1store), node2) &&
          impliesRecursive((int) getHighFromStore(node1store), node2);
    } else {
      return impliesRecursive(node1, (int) getLowFromStore(node2store)) &&
          impliesRecursive(node1, (int) getHighFromStore(node2store));
    }
  }

  private int notRecursive(int node) {
    if (node == ZERO) {
      return ONE;
    }
    if (node == ONE) {
      return ZERO;
    }

    if (cache.lookupNot(node)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final long nodeStore = getNodeStore(node);

    final int lowNode = pushToWorkStack(notRecursive((int) getLowFromStore(nodeStore)));
    final int highNode = pushToWorkStack(notRecursive((int) getHighFromStore(nodeStore)));
    final int resultNode = makeNode((int) getVariableFromStore(nodeStore), lowNode, highNode);
    popWorkStack(2);
    cache.putNot(hash, node, resultNode);
    return resultNode;
  }

  private int equivalenceRecursive(int node1, int node2) {
    if (node1 == node2) {
      return ONE;
    }
    if (node1 == ZERO) {
      return notRecursive(node2);
    }
    if (node1 == ONE) {
      return node2;
    }
    if (node2 == ZERO) {
      return notRecursive(node1);
    }
    if (node2 == ONE) {
      return node1;
    }

    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupEquivalence(node2, node1)) {
        return cache.getLookupResult();
      }
      final int hash = cache.getLookupHash();
      final int lowNode = equivalenceRecursive((int) getLowFromStore(node2store), node1);
      pushToWorkStack(lowNode);
      final int highNode = equivalenceRecursive((int) getHighFromStore(node2store), node1);
      pushToWorkStack(highNode);
      final int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putEquivalence(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupEquivalence(node1, node2)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final int lowNode;
    final int highNode;
    if (node1var == node2var) {
      lowNode = equivalenceRecursive((int) getLowFromStore(node1store),
          (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = equivalenceRecursive((int) getHighFromStore(node1store),
          (int) getHighFromStore(node2store));
      pushToWorkStack(highNode);
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(equivalenceRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(equivalenceRecursive((int) getHighFromStore(node1store), node2));
    }
    final int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putEquivalence(hash, node1, node2, resultNode);
    return resultNode;
  }

  private int nAndRecursive(int node1, int node2) {
    if (node1 == 0 || node2 == 0) {
      return 1;
    }
    if (node1 == 1 || node1 == node2) {
      return notRecursive(node2);
    }
    if (node2 == 1) {
      return notRecursive(node1);
    }

    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupNAnd(node2, node1)) {
        return cache.getLookupResult();
      }
      final int hash = cache.getLookupHash();
      final int lowNode = nAndRecursive((int) getLowFromStore(node2store), node1);
      pushToWorkStack(lowNode);
      final int highNode = nAndRecursive((int) getHighFromStore(node2store), node1);
      pushToWorkStack(highNode);
      final int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putNAnd(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupNAnd(node1, node2)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final int lowNode;
    final int highNode;
    if (node1var == node2var) {
      lowNode = nAndRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = nAndRecursive((int) getHighFromStore(node1store),
          (int) getHighFromStore(node2store));
      pushToWorkStack(highNode);
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(nAndRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(nAndRecursive((int) getHighFromStore(node1store), node2));
    }
    final int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putNAnd(hash, node1, node2, resultNode);
    return resultNode;
  }

  private int implicationRecursive(int node1, int node2) {
    if (node1 == ZERO || node2 == ONE || node1 == node2) {
      return ONE;
    }
    if (node1 == ONE) {
      return node2;
    }
    if (node2 == ZERO) {
      return notRecursive(node1);
    }

    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (cache.lookupImplication(node1, node2)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final int lowNode;
    final int highNode;
    final int decisionVar;
    if (node1var > node2var) {
      lowNode = pushToWorkStack(implicationRecursive(node1, (int) getLowFromStore(node2store)));
      highNode = pushToWorkStack(implicationRecursive(node1, (int) getHighFromStore(node2store)));
      decisionVar = node2var;
    } else if (node1var == node2var) {
      lowNode = pushToWorkStack(implicationRecursive((int) getLowFromStore(node1store),
          (int) getLowFromStore(node2store)));
      highNode = pushToWorkStack(implicationRecursive((int) getHighFromStore(node1store),
          (int) getHighFromStore(node2store)));
      decisionVar = node1var;
    } else {
      lowNode = pushToWorkStack(implicationRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(implicationRecursive((int) getHighFromStore(node1store), node2));
      decisionVar = node1var;
    }
    final int resultNode = makeNode(decisionVar, lowNode, highNode);
    popWorkStack(2);
    cache.putImplication(hash, node1, node2, resultNode);
    return resultNode;
  }

  /**
   * A wrapper class to guard some node in an area where exceptions can occur. It increases the
   * reference count of the given node and decreases it when it's closed.
   * </p>
   * Note: This should seldom be used, as the overhead of object construction and deconstruction
   * is noticeable.
   */
  @SuppressWarnings("unused")
  public static final class ReferenceGuard implements AutoCloseable {
    private final BDD bdd;
    private final int node;

    public ReferenceGuard(int node, BDD bdd) {
      this.node = bdd.ref(node);
      this.bdd = bdd;
    }

    @Override
    public void close() {
      bdd.deref(node);
    }

    public BDD getBdd() {
      return bdd;
    }

    public int getNode() {
      return node;
    }
  }
}
