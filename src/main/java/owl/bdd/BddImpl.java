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

@SuppressWarnings({"PMD.GodClass"})
class BddImpl extends NodeTable implements Bdd {
  /* Implementation notes: Many of the methods are practically copy-paste of each other except for
   * a few variables, as the structure of BDD algorithms is the same for most of the operations. */
  private static final int TRUE_NODE = 1;
  private static final int FALSE_NODE = 0;
  private final BddCache cache;
  private int numberOfVariables;

  BddImpl(final int nodeSize) {
    super(Util.prevPrime(nodeSize), ImmutableBddConfiguration.builder().build());
    cache = new BddCache(this);
    numberOfVariables = 0;
  }


  @Override
  public final int numberOfVariables() {
    return numberOfVariables;
  }

  @Override
  public final int createVariable() {
    final int variableNode = makeNode(numberOfVariables, 0, 1);
    saturateNode(variableNode);
    final int notVariableNode = makeNode(numberOfVariables, 1, 0);
    saturateNode(notVariableNode);
    numberOfVariables++;

    cache.putNot(variableNode, notVariableNode);
    cache.putNot(notVariableNode, variableNode);
    growTree(numberOfVariables);
    cache.invalidateSatisfaction();

    return variableNode;
  }

  @Override
  public final int compose(final int node, final int[] variableNodes) {
    assert variableNodes.length <= numberOfVariables;

    pushToWorkStack(node);
    for (final int variableNode : variableNodes) {
      assert isNodeValidOrRoot(variableNode);
      assert getVariable(variableNode) < variableNodes.length;
      pushToWorkStack(variableNode);
    }
    final int result = composeRecursive(node, variableNodes);
    popWorkStack(variableNodes.length + 1);
    return result;
  }

  @Override
  public final int ifThenElse(final int ifNode, final int thenNode, final int elseNode) {
    assert isNodeValidOrRoot(ifNode) && isNodeValidOrRoot(thenNode) && isNodeValidOrRoot(elseNode);
    pushToWorkStack(ifNode);
    pushToWorkStack(thenNode);
    pushToWorkStack(elseNode);
    final int result = ifThenElseRecursive(ifNode, thenNode, elseNode);
    popWorkStack(3);
    return result;
  }

  @Override
  public final int and(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int result = andRecursive(node1, node2);
    popWorkStack(2);
    return result;
  }

  @Override
  public final int or(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int result = orRecursive(node1, node2);
    popWorkStack(2);
    return result;
  }

  @Override
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
    return currentBdd == TRUE_NODE;
  }

  @Override
  public final int notAnd(final int node1, final int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = notAndRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  @Override
  public final int xor(final int node1, final int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = xorRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  @Override
  public final int equivalence(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = equivalenceRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  @Override
  public final int implication(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = implicationRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  @Override
  public final boolean implies(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    return impliesRecursive(node1, node2);
  }

  @Override
  public final int not(final int node) {
    assert isNodeValidOrRoot(node);
    pushToWorkStack(node);
    final int ret = notRecursive(node);
    popWorkStack();
    return ret;
  }

  @Override
  public final double countSatisfyingAssignments(final int node) {
    // TODO Add overflow checks, an int version and a BigInteger version
    if (node == FALSE_NODE) {
      return 0d;
    }
    if (node == TRUE_NODE) {
      //noinspection MagicNumber
      return Math.pow(2d, (double) numberOfVariables);
    }
    final long nodeStore = getNodeStore(node);
    final double variable = (double) getVariableFromStore(nodeStore);
    //noinspection MagicNumber
    return Math.pow(2d, variable) * countSatisfyingAssignmentsRecursive(node);
  }

  @Override
  public final boolean isVariable(final int node) {
    if (isNodeRoot(node)) {
      return false;
    }
    final long nodeStore = getNodeStore(node);
    return (int) getLowFromStore(nodeStore) == FALSE_NODE
        && (int) getHighFromStore(nodeStore) == TRUE_NODE;
  }

  @Override
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
    return low == FALSE_NODE && high == TRUE_NODE
        || low == TRUE_NODE && high == FALSE_NODE;
  }

  @Override
  public final BitSet support(final int node) {
    assert isNodeValidOrRoot(node);
    final BitSet bitSet = new BitSet(numberOfVariables);
    support(node, bitSet);
    return bitSet;
  }

  @Override
  public final void support(final int node, final BitSet bitSet) {
    bitSet.clear();
    supportRecursive(node, bitSet);
    unmarkTree(node);
  }

  @Override
  public int getFalseNode() {
    return FALSE_NODE;
  }

  @Override
  public int getTrueNode() {
    return TRUE_NODE;
  }

  final String getCacheStatistics() {
    return cache.getStatistics();
  }

  @Override
  final void postRemovalCallback() {
    cache.invalidate();
  }

  private double countSatisfyingAssignmentsRecursive(final int node) {
    if (node == FALSE_NODE) {
      return 0d;
    }
    if (node == TRUE_NODE) {
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
    if (lowNode == FALSE_NODE) {
      lowCount = 0d;
    } else if (lowNode == TRUE_NODE) {
      lowCount = Math.pow(2d, (double) (numberOfVariables - nodeVar - 1));
    } else {
      final long lowStore = getNodeStore(lowNode);
      final int lowVar = (int) getVariableFromStore(lowStore);
      lowCount = countSatisfyingAssignmentsRecursive(lowNode)
          * Math.pow(2d, (double) (lowVar - nodeVar - 1));
    }

    final int highNode = (int) getHighFromStore(nodeStore);
    final double highCount;
    if (highNode == FALSE_NODE) {
      highCount = 0d;
    } else if (highNode == TRUE_NODE) {
      highCount = Math.pow(2d, (double) (numberOfVariables - nodeVar - 1));
    } else {
      final long highStore = getNodeStore(highNode);
      final int highVar = (int) getVariableFromStore(highStore);
      highCount = countSatisfyingAssignmentsRecursive(highNode)
          * Math.pow(2d, (double) (highVar - nodeVar - 1));
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

  @SuppressWarnings("PMD.UseVarargs")
  private int composeRecursive(final int node, final int[] variableNodes) {
    if (node == TRUE_NODE) {
      return node;
    }
    if (node == FALSE_NODE) {
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
    return ifThenElse(variableNodes[nodeVariable], highCompose, lowCompose);
  }

  private int makeNode(final int variable, final int low, final int high) {
    if (low == high) {
      return low;
    } else {
      return add(variable, low, high);
    }
  }

  private int ifThenElseRecursive(final int ifNode, final int thenNode, final int elseNode) {
    if (ifNode == 1) {
      return thenNode;
    }
    if (ifNode == 0) {
      return elseNode;
    }
    if (thenNode == elseNode) {
      return thenNode;
    }
    if (thenNode == 1) {
      if (elseNode == 0) {
        return ifNode;
      }
      return orRecursive(ifNode, elseNode);
    }
    if (thenNode == 0) {
      if (elseNode == 1) {
        return notRecursive(ifNode);
      }
      final int result = andRecursive(pushToWorkStack(notRecursive(ifNode)), elseNode);
      popWorkStack();
      return result;
    }

    if (elseNode == 0) {
      return andRecursive(ifNode, thenNode);
    }
    if (elseNode == 1) {
      final int result = notAndRecursive(ifNode, pushToWorkStack(notRecursive(thenNode)));
      popWorkStack();
      return result;
    }

    if (cache.lookupIfThenElse(ifNode, thenNode, elseNode)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();
    final long ifStore = getNodeStore(ifNode);
    final long thenStore = getNodeStore(thenNode);
    final long elseStore = getNodeStore(elseNode);

    final int ifVar = (int) getVariableFromStore(ifStore);
    final int thenVar = (int) getVariableFromStore(thenStore);
    final int elseVar = (int) getVariableFromStore(elseStore);

    final int minVar = Math.min(ifVar, Math.min(thenVar, elseVar));
    final int ifLowNode;
    final int ifHighNode;
    final int thenLowNode;
    final int thenHighNode;
    final int elseLowNode;
    final int elseHighNode;

    if (ifVar == minVar) {
      ifLowNode = (int) getLowFromStore(ifStore);
      ifHighNode = (int) getHighFromStore(ifStore);
    } else {
      ifLowNode = ifNode;
      ifHighNode = ifNode;
    }
    if (thenVar == minVar) {
      thenLowNode = (int) getLowFromStore(thenStore);
      thenHighNode = (int) getHighFromStore(thenStore);
    } else {
      thenLowNode = thenNode;
      thenHighNode = thenNode;
    }
    if (elseVar == minVar) {
      elseLowNode = (int) getLowFromStore(elseStore);
      elseHighNode = (int) getHighFromStore(elseStore);
    } else {
      elseLowNode = elseNode;
      elseHighNode = elseNode;
    }

    final int lowNode = pushToWorkStack(ifThenElseRecursive(ifLowNode, thenLowNode, elseLowNode));
    final int highNode = pushToWorkStack(
        ifThenElseRecursive(ifHighNode, thenHighNode, elseHighNode));
    final int result = makeNode(minVar, lowNode, highNode);
    popWorkStack(2);
    cache.putIfThenElse(hash, ifNode, thenNode, elseNode, result);
    return result;
  }

  private int andRecursive(final int node1, final int node2) {
    if (node1 == node2 || node2 == TRUE_NODE) {
      return node1;
    }
    if (node1 == FALSE_NODE || node2 == FALSE_NODE) {
      return 0;
    }
    if (node1 == TRUE_NODE) {
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

  private int orRecursive(final int node1, final int node2) {
    if (node1 == TRUE_NODE || node2 == TRUE_NODE) {
      return 1;
    }
    if (node1 == FALSE_NODE || node1 == node2) {
      return node2;
    }
    if (node2 == FALSE_NODE) {
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
    if (node1 == FALSE_NODE) {
      return node2;
    }
    if (node2 == FALSE_NODE) {
      return node1;
    }
    if (node1 == TRUE_NODE) {
      return notRecursive(node2);
    }
    if (node2 == TRUE_NODE) {
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
    if (node1 == FALSE_NODE) {
      // False implies anything
      return true;
    }
    if (node2 == FALSE_NODE) {
      // node1 != FALSE_NODE
      return false;
    }
    if (node2 == TRUE_NODE) {
      // node1 != FALSE_NODE
      return true;
    }
    if (node1 == TRUE_NODE) {
      // node2 != TRUE_NODE
      return false;
    }
    if (node1 == node2) {
      // Trivial implication
      return true;
    }

    if (cache.lookupImplication(node1, node2)) {
      return cache.getLookupResult() == TRUE_NODE;
    }
    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

    if (node1var == node2var) {
      return
          impliesRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store))
              && impliesRecursive((int) getHighFromStore(node1store),
              (int) getHighFromStore(node2store));
    } else if (node1var < node2var) {
      return impliesRecursive((int) getLowFromStore(node1store), node2)
          && impliesRecursive((int) getHighFromStore(node1store), node2);
    } else {
      return impliesRecursive(node1, (int) getLowFromStore(node2store))
          && impliesRecursive(node1, (int) getHighFromStore(node2store));
    }
  }

  private int notRecursive(final int node) {
    if (node == FALSE_NODE) {
      return TRUE_NODE;
    }
    if (node == TRUE_NODE) {
      return FALSE_NODE;
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

  private int equivalenceRecursive(final int node1, final int node2) {
    if (node1 == node2) {
      return TRUE_NODE;
    }
    if (node1 == FALSE_NODE) {
      return notRecursive(node2);
    }
    if (node1 == TRUE_NODE) {
      return node2;
    }
    if (node2 == FALSE_NODE) {
      return notRecursive(node1);
    }
    if (node2 == TRUE_NODE) {
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

  private int notAndRecursive(final int node1, final int node2) {
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
      final int lowNode = notAndRecursive((int) getLowFromStore(node2store), node1);
      pushToWorkStack(lowNode);
      final int highNode = notAndRecursive((int) getHighFromStore(node2store), node1);
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
      lowNode = notAndRecursive((int) getLowFromStore(node1store),
          (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = notAndRecursive((int) getHighFromStore(node1store),
          (int) getHighFromStore(node2store));
      pushToWorkStack(highNode);
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(notAndRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(notAndRecursive((int) getHighFromStore(node1store), node2));
    }
    final int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putNAnd(hash, node1, node2, resultNode);
    return resultNode;
  }

  private int implicationRecursive(final int node1, final int node2) {
    if (node1 == FALSE_NODE || node2 == TRUE_NODE || node1 == node2) {
      return TRUE_NODE;
    }
    if (node1 == TRUE_NODE) {
      return node2;
    }
    if (node2 == FALSE_NODE) {
      return notRecursive(node1);
    }

    if (cache.lookupImplication(node1, node2)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();

    final long node1store = getNodeStore(node1);
    final long node2store = getNodeStore(node2);
    final int node1var = (int) getVariableFromStore(node1store);
    final int node2var = (int) getVariableFromStore(node2store);

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
}
