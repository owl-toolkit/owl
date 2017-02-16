package owl.factories.jdd.bdd;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/* Implementation notes:
 * - Many of the methods are practically copy-paste of each other except for a few variables and
 *   corner cases, as the structure of BDD algorithms is the same for most of the operations.
 * - Due to the implementation of all operations, variable numbers increase while descending the
 *   tree of a particular node. */
@SuppressWarnings({"PMD.GodClass"})
final class BddImpl extends NodeTable implements Bdd {
  private static final int FALSE_NODE = 0;
  private static final int TRUE_NODE = 1;
  private final BddCache cache;
  private final IntList variableNodes = new IntArrayList();
  private int numberOfVariables;

  BddImpl(final int nodeSize) {
    this(nodeSize, ImmutableBddConfiguration.builder().build());
  }

  BddImpl(final int nodeSize, final BddConfiguration configuration) {
    super(Util.nextPrime(nodeSize), configuration);
    cache = new BddCache(this);
    numberOfVariables = 0;
  }

  private static boolean isVariableOrNegatedStore(final long nodeStore) {
    final int low = (int) getLowFromStore(nodeStore);
    final int high = (int) getHighFromStore(nodeStore);
    return low == FALSE_NODE && high == TRUE_NODE
      || low == TRUE_NODE && high == FALSE_NODE;
  }

  @Override
  public int and(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int result = andRecursive(node1, node2);
    popWorkStack(2);
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

  @Override
  public int compose(final int node, final int[] variableReplacementNodes) {
    assert variableReplacementNodes.length <= numberOfVariables;

    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    // Guard the elements and replace -1 by actual variable reference
    pushToWorkStack(node);
    int elements = 1;
    for (int i = 0; i < variableReplacementNodes.length; i++) {
      if (variableReplacementNodes[i] == -1) {
        variableReplacementNodes[i] = variableNodes.get(i);
      } else {
        assert isNodeValidOrRoot(variableReplacementNodes[i]);
        if (!isNodeSaturated(variableReplacementNodes[i])) {
          pushToWorkStack(variableReplacementNodes[i]);
          elements++;
        }
      }
    }

    int highestReplacedVariable = variableReplacementNodes.length - 1;
    // Optimise the replacement array
    for (int i = variableReplacementNodes.length - 1; i >= 0; i--) {
      final int value = variableReplacementNodes[i];
      if (value != variableNodes.get(i)) {
        highestReplacedVariable = i;
        break;
      }
    }
    if (highestReplacedVariable == -1) {
      return node;
    }

    final int hash;
    if (getConfiguration().useGlobalComposeCache()) {
      if (cache.lookupCompose(node, variableReplacementNodes)) {
        return cache.getLookupResult();
      }
      hash = cache.getLookupHash();
    } else {
      hash = -1;
    }

    cache.clearVolatileCache();
    final int result = composeRecursive(node, variableReplacementNodes, highestReplacedVariable);
    popWorkStack(elements);
    if (getConfiguration().useGlobalComposeCache()) {
      cache.putCompose(hash, node, variableReplacementNodes, result);
    }
    return result;
  }

  private int composeRecursive(final int node, final int[] variableNodes,
    final int highestReplacedVariable) {
    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    final long nodeStore = getNodeStore(node);
    final int nodeVariable = (int) getVariableFromStore(nodeStore);
    // The tree is sorted (variable 0 on top), hence if the algorithm descended "far enough" there
    // will not be any replacements.
    if (nodeVariable > highestReplacedVariable) {
      return node;
    }

    if (cache.lookupVolatile(node)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();

    final int variableReplacementNode = variableNodes[nodeVariable];
    final int resultNode;
    // Short-circuit constant replacements.
    if (variableReplacementNode == TRUE_NODE) {
      resultNode = composeRecursive((int) getHighFromStore(nodeStore), variableNodes,
        highestReplacedVariable);
    } else if (variableReplacementNode == FALSE_NODE) {
      resultNode = composeRecursive((int) getLowFromStore(nodeStore), variableNodes,
        highestReplacedVariable);
    } else {
      final int lowCompose = pushToWorkStack(composeRecursive((int) getLowFromStore(nodeStore),
        variableNodes, highestReplacedVariable));
      final int highCompose = pushToWorkStack(composeRecursive((int) getHighFromStore(nodeStore),
        variableNodes, highestReplacedVariable));
      resultNode = ifThenElseRecursive(variableReplacementNode, highCompose, lowCompose);
      popWorkStack(2);
    }
    cache.putVolatile(hash, node, resultNode);
    return resultNode;
  }

  @Override
  public double countSatisfyingAssignments(final int node) {
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

  @Override
  public int createVariable() {
    final int variableNode = makeNode(numberOfVariables, 0, 1);
    saturateNode(variableNode);
    final int notVariableNode = makeNode(numberOfVariables, 1, 0);
    saturateNode(notVariableNode);
    variableNodes.add(variableNode);
    numberOfVariables++;

    cache.putNot(variableNode, notVariableNode);
    cache.putNot(notVariableNode, variableNode);
    growTree(numberOfVariables);
    cache.invalidateSatisfaction();
    cache.invalidateCompose();
    cache.reallocateVolatile();

    return variableNode;
  }

  private int cube(final BitSet cubeVariables) {
    int node = TRUE_NODE;
    int currentVariableNumber = cubeVariables.nextSetBit(0);
    while (currentVariableNumber != -1) {
      // Variable nodes are saturated, no need to guard them
      pushToWorkStack(node);
      node = andRecursive(node, variableNodes.getInt(currentVariableNumber));
      popWorkStack();
      currentVariableNumber = cubeVariables.nextSetBit(currentVariableNumber + 1);
    }
    return node;
  }

  @Override
  public int equivalence(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = equivalenceRecursive(node1, node2);
    popWorkStack(2);
    return ret;
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

  @Override
  public boolean evaluate(final int node, final BitSet assignment) {
    int currentBdd = node;
    long currentBddStore;
    int currentBddVariable;
    while (currentBdd >= 2) {
      currentBddStore = getNodeStore(currentBdd);
      currentBddVariable = (int) getVariableFromStore(currentBddStore);
      if (assignment.get(currentBddVariable)) {
        currentBdd = (int) getHighFromStore(currentBddStore);
      } else {
        currentBdd = (int) getLowFromStore(currentBddStore);
      }
    }
    return currentBdd == TRUE_NODE;
  }

  @Override
  public int exists(final int node, final BitSet quantifiedVariables) {
    if (getConfiguration().useShannonExists()) {
      return existsShannon(node, quantifiedVariables);
    }
    return existsSelfSubstitution(node, quantifiedVariables);
  }

  @VisibleForTesting
  int existsSelfSubstitution(final int node, final BitSet quantifiedVariables) {
    assert quantifiedVariables.previousSetBit(quantifiedVariables.length()) <= numberOfVariables;
    if (quantifiedVariables.cardinality() == numberOfVariables) {
      return TRUE_NODE;
    }

    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    int workStackElements = 1;
    pushToWorkStack(node);

    int quantifiedNode = node;
    final int[] replacementArray = new int[quantifiedVariables.length()];
    for (int i = 0; i < replacementArray.length; i++) {
      replacementArray[i] = variableNodes.get(i);
    }
    for (int i = 0; i < quantifiedVariables.length(); i++) {
      if (!quantifiedVariables.get(i)) {
        continue;
      }
      final int variableNode = replacementArray[i];

      replacementArray[i] = TRUE_NODE;
      cache.clearVolatileCache();
      // compute f(x, 1)
      replacementArray[i] =
        pushToWorkStack(composeRecursive(quantifiedNode, replacementArray, i));
      cache.clearVolatileCache();
      // compute f(x, f(x, 1))
      quantifiedNode = composeRecursive(quantifiedNode, replacementArray, i);
      popWorkStack();
      // restore previous replacement value
      replacementArray[i] = variableNode;
      pushToWorkStack(quantifiedNode);
      workStackElements += 1;
    }
    popWorkStack(workStackElements);
    return quantifiedNode;
  }

  @VisibleForTesting
  int existsShannon(final int node, final BitSet quantifiedVariables) {
    assert quantifiedVariables.previousSetBit(quantifiedVariables.length()) <= numberOfVariables;
    if (quantifiedVariables.cardinality() == numberOfVariables) {
      return TRUE_NODE;
    }

    pushToWorkStack(node);
    final int quantifiedVariablesCube = cube(quantifiedVariables);
    pushToWorkStack(quantifiedVariablesCube);
    final int result = existsShannonRecursive(node, quantifiedVariablesCube);
    popWorkStack(2);
    return result;
  }

  private int existsShannonRecursive(final int node, final int quantifiedVariableCube) {
    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }
    if (quantifiedVariableCube == TRUE_NODE) {
      return node;
    }

    final long nodeStore = getNodeStore(node);
    final int nodeVariable = (int) getVariableFromStore(nodeStore);

    int currentCubeNode = quantifiedVariableCube;
    long currentCubeNodeStore = getNodeStore(currentCubeNode);
    int currentCubeNodeVariable = (int) getVariableFromStore(currentCubeNodeStore);
    while (currentCubeNodeVariable < nodeVariable) {
      currentCubeNode = (int) getHighFromStore(currentCubeNodeStore);
      if (currentCubeNode == TRUE_NODE) {
        // No more variables to project
        return node;
      }
      currentCubeNodeStore = getNodeStore(currentCubeNode);
      currentCubeNodeVariable = (int) getVariableFromStore(currentCubeNodeStore);
    }

    if (isVariableOrNegatedStore(nodeStore)) {
      if (nodeVariable == currentCubeNodeVariable) {
        return TRUE_NODE;
      }
      return node;
    }

    if (cache.lookupExists(node, currentCubeNode)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();

    // The "root" of the cube is guarded in the main invocation - no need to guard its descendants
    final int lowExists = pushToWorkStack(existsShannonRecursive((int) getLowFromStore(nodeStore),
      currentCubeNode));
    final int highExists = pushToWorkStack(existsShannonRecursive(
      (int) getHighFromStore(nodeStore), currentCubeNode));
    final int resultNode;
    if (currentCubeNodeVariable > nodeVariable) {
      // The variable of this node is smaller than the variable looked for - only propagate the
      // quantification downward
      resultNode = makeNode(nodeVariable, lowExists, highExists);
    } else {
      // nodeVariable == nextVariable, i.e. "quantify out" the current node.
      resultNode = orRecursive(lowExists, highExists);
    }
    popWorkStack(2);
    cache.putExists(hash, node, currentCubeNode, resultNode);
    return resultNode;
  }

  String getCacheStatistics() {
    return cache.getStatistics();
  }

  @Override
  public int getFalseNode() {
    return FALSE_NODE;
  }

  @Override
  public Iterator<BitSet> getMinimalSolutions(final int node) {
    assert isNodeValidOrRoot(node);

    if (node == FALSE_NODE) {
      return Collections.emptyIterator();
    }
    if (numberOfVariables() == 0) {
      // This implies that node == TRUE_NODE, as there only exist FALSE and TRUE in that case
      return Iterators.singletonIterator(new BitSet());
    }
    return new MinimalSolutionIterator(this, node);
  }

  @Override
  public int getTrueNode() {
    return TRUE_NODE;
  }

  @Override
  public int ifThenElse(final int ifNode, final int thenNode, final int elseNode) {
    assert isNodeValidOrRoot(ifNode) && isNodeValidOrRoot(thenNode) && isNodeValidOrRoot(elseNode);
    pushToWorkStack(ifNode);
    pushToWorkStack(thenNode);
    pushToWorkStack(elseNode);
    final int result = ifThenElseRecursive(ifNode, thenNode, elseNode);
    popWorkStack(3);
    return result;
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
    final int highNode =
      pushToWorkStack(ifThenElseRecursive(ifHighNode, thenHighNode, elseHighNode));
    final int result = makeNode(minVar, lowNode, highNode);
    popWorkStack(2);
    cache.putIfThenElse(hash, ifNode, thenNode, elseNode, result);
    return result;
  }

  @Override
  public int implication(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = implicationRecursive(node1, node2);
    popWorkStack(2);
    return ret;
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

  @Override
  public boolean implies(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    return impliesRecursive(node1, node2);
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

  @Override
  public boolean isVariable(final int node) {
    if (isNodeRoot(node)) {
      return false;
    }
    final long nodeStore = getNodeStore(node);
    return (int) getLowFromStore(nodeStore) == FALSE_NODE
      && (int) getHighFromStore(nodeStore) == TRUE_NODE;
  }

  @Override
  public boolean isVariableOrNegated(final int node) {
    assert isNodeValidOrRoot(node);
    if (isNodeRoot(node)) {
      return false;
    }
    final long nodeStore = getNodeStore(node);
    return isVariableOrNegatedStore(nodeStore);
  }

  // TODO: Inline.
  private int makeNode(final int variable, final int low, final int high) {
    if (low == high) {
      return low;
    } else {
      return add(variable, low, high);
    }
  }

  @Override
  public int not(final int node) {
    assert isNodeValidOrRoot(node);
    pushToWorkStack(node);
    final int ret = notRecursive(node);
    popWorkStack();
    return ret;
  }

  @Override
  public int notAnd(final int node1, final int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = notAndRecursive(node1, node2);
    popWorkStack(2);
    return ret;
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

  @Override
  public int numberOfVariables() {
    return numberOfVariables;
  }

  @Override
  public int or(final int node1, final int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int result = orRecursive(node1, node2);
    popWorkStack(2);
    return result;
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

  @Override
  void postRemovalCallback() {
    cache.invalidate();
  }

  @Override
  public int restrict(final int node, final BitSet restrictedVariables,
    final BitSet restrictedVariableValues) {
    assert isNodeValidOrRoot(node);
    pushToWorkStack(node);
    cache.clearVolatileCache();
    final int resultNode = restrictRecursive(node, restrictedVariables, restrictedVariableValues);
    popWorkStack();
    return resultNode;
  }

  private int restrictRecursive(final int node, final BitSet restrictedVariables,
    final BitSet restrictedVariableValues) {
    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    final long nodeStore = getNodeStore(node);
    final int nodeVariable = (int) getVariableFromStore(nodeStore);
    // The tree is sorted (variable 0 on top), hence if the algorithm descended far enough there
    // will not be any replacements.
    if (nodeVariable >= restrictedVariables.length()) {
      return node;
    }

    if (cache.lookupVolatile(node)) {
      return cache.getLookupResult();
    }
    final int hash = cache.getLookupHash();

    final int resultNode;
    if (restrictedVariables.get(nodeVariable)) {
      if (restrictedVariableValues.get(nodeVariable)) {
        resultNode = restrictRecursive((int) getHighFromStore(nodeStore), restrictedVariables,
          restrictedVariableValues);
      } else {
        resultNode = restrictRecursive((int) getLowFromStore(nodeStore), restrictedVariables,
          restrictedVariableValues);
      }
    } else {
      final int lowRestrict = pushToWorkStack(restrictRecursive((int) getLowFromStore(nodeStore),
        restrictedVariables, restrictedVariableValues));
      final int highRestrict = pushToWorkStack(restrictRecursive((int) getHighFromStore(nodeStore),
        restrictedVariables, restrictedVariableValues));
      resultNode = makeNode(nodeVariable, lowRestrict, highRestrict);
      popWorkStack(2);
    }
    cache.putVolatile(hash, node, resultNode);
    return resultNode;
  }

  @Override
  public void support(final int node, final BitSet bitSet, final int highestVariable) {
    assert isNodeValidOrRoot(node);
    assert 0 <= highestVariable && highestVariable <= numberOfVariables;
    bitSet.clear();
    supportRecursive(node, bitSet, highestVariable);
    unMarkTree(node);
  }

  private void supportRecursive(final int node, final BitSet bitSet, final int highestVariable) {
    if (isNodeRoot(node)) {
      return;
    }

    final long nodeStore = getNodeStore(node);

    if (isNodeStoreMarked(nodeStore)) {
      return;
    }

    final int variable = (int) getVariableFromStore(nodeStore);

    if (variable < highestVariable) {
      bitSet.set(variable);
      markNode(node);
      supportRecursive((int) getLowFromStore(nodeStore), bitSet, highestVariable);
      supportRecursive((int) getHighFromStore(nodeStore), bitSet, highestVariable);
    }
  }

  @Override
  public int xor(final int node1, final int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    final int ret = xorRecursive(node1, node2);
    popWorkStack(2);
    return ret;
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

  private static final class MinimalSolutionIterator implements Iterator<BitSet> {
    private final BddImpl bdd;
    private final BitSet bitSet;
    private final int[] path;
    private boolean firstRun = true;
    private int highestLowVariableWithNonFalseHighBranch;
    private int leafPosition;
    private boolean next;

    MinimalSolutionIterator(final BddImpl bdd, final int node) {
      // Require at least one possible solution to exist.
      assert bdd.isNodeValid(node) || node == TRUE_NODE;
      // Assignments don't make much sense otherwise
      assert bdd.numberOfVariables() > 0;

      this.bdd = bdd;
      this.path = new int[bdd.numberOfVariables()];
      this.bitSet = new BitSet(bdd.numberOfVariables());

      Arrays.fill(path, -1);
      this.path[0] = node;
      leafPosition = 0;
      next = true;
    }

    @Override
    public boolean hasNext() {
      return next;
    }

    @Override
    public BitSet next() throws NoSuchElementException {
      int currentNode;
      if (firstRun) {
        firstRun = false;
        currentNode = path[0];
      } else {
        // Backtrack on the current path until we find a node set to low and non-false high branch
        currentNode = path[leafPosition];
        int branchPosition = leafPosition;
        while (bitSet.get(branchPosition) || bdd.getHigh(currentNode) == FALSE_NODE) {
          branchPosition -= 1;
          while (path[branchPosition] == -1) {
            branchPosition -= 1;
          }
          if (branchPosition == -1) {
            throw new NoSuchElementException("No next element");
          }
          currentNode = path[branchPosition];
        }
        assert !bitSet.get(branchPosition) && bdd.getHigh(currentNode) != FALSE_NODE;
        assert bdd.getVariable(currentNode) == branchPosition;

        // currentNode is the lowest node we can switch to high.
        bitSet.clear(branchPosition + 1, leafPosition + 1);
        Arrays.fill(path, branchPosition + 1, leafPosition + 1, -1);

        // currentNode gets switched to high and we descend that tree below.
        bitSet.set(branchPosition);
        assert path[branchPosition] == currentNode;
        currentNode = bdd.getHigh(path[branchPosition]);
        assert currentNode != FALSE_NODE;
        leafPosition = branchPosition;
      }

      // Descend the tree, searching for a new true node and determine if there is a next
      // assignment.

      // If there is a possible path higher up, there definitely are more solutions
      next = highestLowVariableWithNonFalseHighBranch < leafPosition;

      while (currentNode != TRUE_NODE) {
        assert currentNode != FALSE_NODE;
        final long currentNodeStore = bdd.getNodeStore(currentNode);
        leafPosition = (int) getVariableFromStore(currentNodeStore);
        path[leafPosition] = currentNode;

        final int low = (int) getLowFromStore(currentNodeStore);
        if (low == FALSE_NODE) {
          // Descend high path
          bitSet.set(leafPosition);
          currentNode = (int) getHighFromStore(currentNodeStore);
        } else {
          // If there is a non-false high node, we will be able to swap this node later on so we
          // definitely have a next assignment. On the other hand, if there is no such node, the
          // last possible assignment has been reached, as there are no more possible switches
          // higher up in the tree.
          if (!next && (int) getHighFromStore(currentNodeStore) != FALSE_NODE) {
            next = true;
            highestLowVariableWithNonFalseHighBranch = leafPosition;
          }
          currentNode = low;
        }
      }
      assert bdd.evaluate(path[0], bitSet);
      return bitSet;
    }
  }
}
