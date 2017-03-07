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
@SuppressWarnings("NumericCastThatLosesPrecision")
final class BddImpl extends NodeTable implements Bdd {
  private static final int FALSE_NODE = 0;
  private static final int TRUE_NODE = 1;
  private final BddCache cache;
  private final IntList variableNodes = new IntArrayList();
  private int numberOfVariables;

  BddImpl(int nodeSize) {
    this(nodeSize, ImmutableBddConfiguration.builder().build());
  }

  BddImpl(int nodeSize, BddConfiguration configuration) {
    super(Util.nextPrime(nodeSize), configuration);
    cache = new BddCache(this);
    numberOfVariables = 0;
  }

  private static boolean isVariableOrNegatedStore(long nodeStore) {
    //noinspection NumericCastThatLosesPrecision
    int low = (int) getLowFromStore(nodeStore);
    //noinspection NumericCastThatLosesPrecision
    int high = (int) getHighFromStore(nodeStore);
    return low == FALSE_NODE && high == TRUE_NODE
      || low == TRUE_NODE && high == FALSE_NODE;
  }

  @Override
  public int and(int node1, int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int result = andRecursive(node1, node2);
    popWorkStack(2);
    return result;
  }

  private int andRecursive(int node1, int node2) {
    if (node1 == node2 || node2 == TRUE_NODE) {
      return node1;
    }
    if (node1 == FALSE_NODE || node2 == FALSE_NODE) {
      return 0;
    }
    if (node1 == TRUE_NODE) {
      return node2;
    }

    long node1store = getNodeStore(node1);
    long node2store = getNodeStore(node2);
    //noinspection NumericCastThatLosesPrecision
    int node1var = (int) getVariableFromStore(node1store);
    //noinspection NumericCastThatLosesPrecision
    int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupAnd(node2, node1)) {
        // We have a cache hit for this operation
        return cache.getLookupResult();
      }
      int hash = cache.getLookupHash();

      // Guard the result - the recursive calls may cause the table to grow, kicking off a gc.
      // If the produced variables are not guarded, they may get invalidated.
      //noinspection NumericCastThatLosesPrecision
      int lowNode = pushToWorkStack(andRecursive((int) getLowFromStore(node2store), node1));
      //noinspection NumericCastThatLosesPrecision
      int highNode = pushToWorkStack(andRecursive((int) getHighFromStore(node2store), node1));
      int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putAnd(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupAnd(node1, node2)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();
    int lowNode;
    int highNode;
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
    int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putAnd(hash, node1, node2, resultNode);
    return resultNode;
  }

  @Override
  public int compose(int node, int[] variableNodes) {
    assert variableNodes.length <= numberOfVariables;

    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    // Guard the elements and replace -1 by actual variable reference
    pushToWorkStack(node);
    int elements = 1;
    for (int i = 0; i < variableNodes.length; i++) {
      if (variableNodes[i] == -1) {
        variableNodes[i] = this.variableNodes.get(i);
      } else {
        assert isNodeValidOrRoot(variableNodes[i]);
        if (!isNodeSaturated(variableNodes[i])) {
          pushToWorkStack(variableNodes[i]);
          elements++;
        }
      }
    }

    int highestReplacedVariable = variableNodes.length - 1;
    // Optimise the replacement array
    for (int i = variableNodes.length - 1; i >= 0; i--) {
      int value = variableNodes[i];
      if (value != this.variableNodes.get(i)) {
        highestReplacedVariable = i;
        break;
      }
    }
    if (highestReplacedVariable == -1) {
      return node;
    }

    int hash;
    if (getConfiguration().useGlobalComposeCache()) {
      if (cache.lookupCompose(node, variableNodes)) {
        return cache.getLookupResult();
      }
      hash = cache.getLookupHash();
    } else {
      hash = -1;
    }

    cache.clearVolatileCache();
    int result = composeRecursive(node, variableNodes, highestReplacedVariable);
    popWorkStack(elements);
    if (getConfiguration().useGlobalComposeCache()) {
      cache.putCompose(hash, node, variableNodes, result);
    }
    return result;
  }

  private int composeRecursive(int node, int[] variableNodes,
    int highestReplacedVariable) {
    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    long nodeStore = getNodeStore(node);
    //noinspection NumericCastThatLosesPrecision
    int nodeVariable = (int) getVariableFromStore(nodeStore);
    // The tree is sorted (variable 0 on top), hence if the algorithm descended "far enough" there
    // will not be any replacements.
    if (nodeVariable > highestReplacedVariable) {
      return node;
    }

    if (cache.lookupVolatile(node)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();

    int variableReplacementNode = variableNodes[nodeVariable];
    int resultNode;
    // Short-circuit constant replacements.
    if (variableReplacementNode == TRUE_NODE) {
      resultNode = composeRecursive((int) getHighFromStore(nodeStore), variableNodes,
        highestReplacedVariable);
    } else if (variableReplacementNode == FALSE_NODE) {
      resultNode = composeRecursive((int) getLowFromStore(nodeStore), variableNodes,
        highestReplacedVariable);
    } else {
      //noinspection NumericCastThatLosesPrecision
      int lowCompose = pushToWorkStack(composeRecursive((int) getLowFromStore(nodeStore),
        variableNodes, highestReplacedVariable));
      //noinspection NumericCastThatLosesPrecision
      int highCompose = pushToWorkStack(composeRecursive((int) getHighFromStore(nodeStore),
        variableNodes, highestReplacedVariable));
      resultNode = ifThenElseRecursive(variableReplacementNode, highCompose, lowCompose);
      popWorkStack(2);
    }
    cache.putVolatile(hash, node, resultNode);
    return resultNode;
  }

  @Override
  public double countSatisfyingAssignments(int node) {
    // TODO Add overflow checks, an int version and a BigInteger version
    if (node == FALSE_NODE) {
      return 0.0d;
    }
    if (node == TRUE_NODE) {
      //noinspection NonReproducibleMathCall
      return Math.pow(2.0d, (double) numberOfVariables);
    }
    long nodeStore = getNodeStore(node);
    double variable = (double) getVariableFromStore(nodeStore);
    //noinspection NonReproducibleMathCall
    return Math.pow(2.0d, variable) * countSatisfyingAssignmentsRecursive(node);
  }

  private double countSatisfyingAssignmentsRecursive(int node) {
    if (node == FALSE_NODE) {
      return 0.0d;
    }
    if (node == TRUE_NODE) {
      return 1.0d;
    }

    double cacheLookup = cache.lookupSatisfaction(node);
    if (cacheLookup >= 0.0d) {
      return cacheLookup;
    }
    int hash = cache.getLookupHash();

    long nodeStore = getNodeStore(node);
    //noinspection NumericCastThatLosesPrecision
    int nodeVar = (int) getVariableFromStore(nodeStore);

    //noinspection NumericCastThatLosesPrecision
    int lowNode = (int) getLowFromStore(nodeStore);
    double lowCount;
    if (lowNode == FALSE_NODE) {
      lowCount = 0.0d;
    } else if (lowNode == TRUE_NODE) {
      //noinspection NonReproducibleMathCall
      lowCount = Math.pow(2.0d, (double) (numberOfVariables - nodeVar - 1));
    } else {
      long lowStore = getNodeStore(lowNode);
      //noinspection NumericCastThatLosesPrecision
      int lowVar = (int) getVariableFromStore(lowStore);
      //noinspection NonReproducibleMathCall
      lowCount = countSatisfyingAssignmentsRecursive(lowNode)
        * Math.pow(2.0d, (double) (lowVar - nodeVar - 1));
    }

    //noinspection NumericCastThatLosesPrecision
    int highNode = (int) getHighFromStore(nodeStore);
    double highCount;
    if (highNode == FALSE_NODE) {
      highCount = 0.0d;
    } else if (highNode == TRUE_NODE) {
      //noinspection NonReproducibleMathCall
      highCount = Math.pow(2.0d, (double) (numberOfVariables - nodeVar - 1));
    } else {
      long highStore = getNodeStore(highNode);
      //noinspection NumericCastThatLosesPrecision
      int highVar = (int) getVariableFromStore(highStore);
      //noinspection NonReproducibleMathCall
      highCount = countSatisfyingAssignmentsRecursive(highNode)
        * Math.pow(2.0d, (double) (highVar - nodeVar - 1));
    }

    double result = lowCount + highCount;
    cache.putSatisfaction(hash, node, result);
    return result;
  }

  @Override
  public int createVariable() {
    int variableNode = makeNode(numberOfVariables, 0, 1);
    saturateNode(variableNode);
    int notVariableNode = makeNode(numberOfVariables, 1, 0);
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

  @Override
  public int cube(BitSet cubeVariables) {
    int node = TRUE_NODE;
    for (int currentVariableNumber = cubeVariables.nextSetBit(0); currentVariableNumber != -1;
         currentVariableNumber = cubeVariables.nextSetBit(currentVariableNumber + 1)) {
      // Variable nodes are saturated, no need to guard them
      pushToWorkStack(node);
      node = andRecursive(node, variableNodes.getInt(currentVariableNumber));
      popWorkStack();
    }
    return node;
  }

  @Override
  public int equivalence(int node1, int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = equivalenceRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  private int equivalenceRecursive(int node1, int node2) {
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

    long node1store = getNodeStore(node1);
    long node2store = getNodeStore(node2);
    //noinspection NumericCastThatLosesPrecision
    int node1var = (int) getVariableFromStore(node1store);
    //noinspection NumericCastThatLosesPrecision
    int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupEquivalence(node2, node1)) {
        return cache.getLookupResult();
      }
      int hash = cache.getLookupHash();
      //noinspection NumericCastThatLosesPrecision
      int lowNode = equivalenceRecursive((int) getLowFromStore(node2store), node1);
      pushToWorkStack(lowNode);
      //noinspection NumericCastThatLosesPrecision
      int highNode = equivalenceRecursive((int) getHighFromStore(node2store), node1);
      pushToWorkStack(highNode);
      int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putEquivalence(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupEquivalence(node1, node2)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();
    int lowNode;
    int highNode;
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
    int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putEquivalence(hash, node1, node2, resultNode);
    return resultNode;
  }

  @Override
  public boolean evaluate(int node, BitSet assignment) {
    int currentBdd = node;
    while (currentBdd >= 2) {
      long currentBddStore = getNodeStore(currentBdd);
      int currentBddVariable = (int) getVariableFromStore(currentBddStore);
      if (assignment.get(currentBddVariable)) {
        currentBdd = (int) getHighFromStore(currentBddStore);
      } else {
        currentBdd = (int) getLowFromStore(currentBddStore);
      }
    }
    return currentBdd == TRUE_NODE;
  }

  @Override
  public int exists(int node, BitSet quantifiedVariables) {
    if (getConfiguration().useShannonExists()) {
      return existsShannon(node, quantifiedVariables);
    }
    return existsSelfSubstitution(node, quantifiedVariables);
  }

  @VisibleForTesting
  int existsSelfSubstitution(int node, BitSet quantifiedVariables) {
    assert quantifiedVariables.previousSetBit(quantifiedVariables.length()) <= numberOfVariables;
    if (quantifiedVariables.cardinality() == numberOfVariables) {
      return TRUE_NODE;
    }

    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    pushToWorkStack(node);

    int[] replacementArray = new int[quantifiedVariables.length()];
    for (int i = 0; i < replacementArray.length; i++) {
      replacementArray[i] = variableNodes.get(i);
    }
    int quantifiedNode = node;
    int workStackElements = 1;
    for (int i = 0; i < quantifiedVariables.length(); i++) {
      if (!quantifiedVariables.get(i)) {
        continue;
      }
      int variableNode = replacementArray[i];

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
  int existsShannon(int node, BitSet quantifiedVariables) {
    assert quantifiedVariables.previousSetBit(quantifiedVariables.length()) <= numberOfVariables;
    if (quantifiedVariables.cardinality() == numberOfVariables) {
      return TRUE_NODE;
    }

    pushToWorkStack(node);
    int quantifiedVariablesCube = cube(quantifiedVariables);
    pushToWorkStack(quantifiedVariablesCube);
    int result = existsShannonRecursive(node, quantifiedVariablesCube);
    popWorkStack(2);
    return result;
  }

  private int existsShannonRecursive(int node, int quantifiedVariableCube) {
    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }
    if (quantifiedVariableCube == TRUE_NODE) {
      return node;
    }

    long nodeStore = getNodeStore(node);
    //noinspection NumericCastThatLosesPrecision
    int nodeVariable = (int) getVariableFromStore(nodeStore);

    int currentCubeNode = quantifiedVariableCube;
    long currentCubeNodeStore = getNodeStore(currentCubeNode);
    //noinspection NumericCastThatLosesPrecision
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
    int hash = cache.getLookupHash();

    // The "root" of the cube is guarded in the main invocation - no need to guard its descendants
    //noinspection NumericCastThatLosesPrecision
    int lowExists = pushToWorkStack(existsShannonRecursive((int) getLowFromStore(nodeStore),
      currentCubeNode));
    //noinspection NumericCastThatLosesPrecision
    int highExists = pushToWorkStack(existsShannonRecursive(
      (int) getHighFromStore(nodeStore), currentCubeNode));
    int resultNode;
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
  public Iterator<BitSet> getMinimalSolutions(int node) {
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
  public int getVariableNode(int variableNumber) {
    assert 0 <= variableNumber && variableNumber < numberOfVariables();
    return variableNodes.get(variableNumber);
  }

  @Override
  public int ifThenElse(int ifNode, int thenNode, int elseNode) {
    assert isNodeValidOrRoot(ifNode) && isNodeValidOrRoot(thenNode) && isNodeValidOrRoot(elseNode);
    pushToWorkStack(ifNode);
    pushToWorkStack(thenNode);
    pushToWorkStack(elseNode);
    int result = ifThenElseRecursive(ifNode, thenNode, elseNode);
    popWorkStack(3);
    return result;
  }

  private int ifThenElseRecursive(int ifNode, int thenNode, int elseNode) {
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
      int result = andRecursive(pushToWorkStack(notRecursive(ifNode)), elseNode);
      popWorkStack();
      return result;
    }

    if (elseNode == 0) {
      return andRecursive(ifNode, thenNode);
    }
    if (elseNode == 1) {
      int result = notAndRecursive(ifNode, pushToWorkStack(notRecursive(thenNode)));
      popWorkStack();
      return result;
    }

    if (cache.lookupIfThenElse(ifNode, thenNode, elseNode)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();
    long ifStore = getNodeStore(ifNode);
    long thenStore = getNodeStore(thenNode);
    long elseStore = getNodeStore(elseNode);

    //noinspection NumericCastThatLosesPrecision
    int ifVar = (int) getVariableFromStore(ifStore);
    //noinspection NumericCastThatLosesPrecision
    int thenVar = (int) getVariableFromStore(thenStore);
    //noinspection NumericCastThatLosesPrecision
    int elseVar = (int) getVariableFromStore(elseStore);

    int minVar = Math.min(ifVar, Math.min(thenVar, elseVar));
    int ifLowNode;
    int ifHighNode;

    if (ifVar == minVar) {
      ifLowNode = (int) getLowFromStore(ifStore);
      ifHighNode = (int) getHighFromStore(ifStore);
    } else {
      ifLowNode = ifNode;
      ifHighNode = ifNode;
    }

    int thenHighNode;
    int thenLowNode;
    if (thenVar == minVar) {
      thenLowNode = (int) getLowFromStore(thenStore);
      thenHighNode = (int) getHighFromStore(thenStore);
    } else {
      thenLowNode = thenNode;
      thenHighNode = thenNode;
    }

    int elseHighNode;
    int elseLowNode;
    if (elseVar == minVar) {
      elseLowNode = (int) getLowFromStore(elseStore);
      elseHighNode = (int) getHighFromStore(elseStore);
    } else {
      elseLowNode = elseNode;
      elseHighNode = elseNode;
    }

    int lowNode = pushToWorkStack(ifThenElseRecursive(ifLowNode, thenLowNode, elseLowNode));
    int highNode =
      pushToWorkStack(ifThenElseRecursive(ifHighNode, thenHighNode, elseHighNode));
    int result = makeNode(minVar, lowNode, highNode);
    popWorkStack(2);
    cache.putIfThenElse(hash, ifNode, thenNode, elseNode, result);
    return result;
  }

  @Override
  public int implication(int node1, int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = implicationRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  private int implicationRecursive(int node1, int node2) {
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
    int hash = cache.getLookupHash();

    long node1store = getNodeStore(node1);
    long node2store = getNodeStore(node2);
    //noinspection NumericCastThatLosesPrecision
    int node1var = (int) getVariableFromStore(node1store);
    //noinspection NumericCastThatLosesPrecision
    int node2var = (int) getVariableFromStore(node2store);

    int lowNode;
    int highNode;
    int decisionVar;
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
    int resultNode = makeNode(decisionVar, lowNode, highNode);
    popWorkStack(2);
    cache.putImplication(hash, node1, node2, resultNode);
    return resultNode;
  }

  @Override
  public boolean implies(int node1, int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    return impliesRecursive(node1, node2);
  }

  private boolean impliesRecursive(int node1, int node2) {
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
    long node1store = getNodeStore(node1);
    long node2store = getNodeStore(node2);
    //noinspection NumericCastThatLosesPrecision
    int node1var = (int) getVariableFromStore(node1store);
    //noinspection NumericCastThatLosesPrecision
    int node2var = (int) getVariableFromStore(node2store);

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
  public boolean isVariable(int node) {
    if (isNodeRoot(node)) {
      return false;
    }
    long nodeStore = getNodeStore(node);
    return (int) getLowFromStore(nodeStore) == FALSE_NODE
      && (int) getHighFromStore(nodeStore) == TRUE_NODE;
  }

  @Override
  public boolean isVariableNegated(int node) {
    if (isNodeRoot(node)) {
      return false;
    }
    long nodeStore = getNodeStore(node);
    return (int) getLowFromStore(nodeStore) == TRUE_NODE
      && (int) getHighFromStore(nodeStore) == FALSE_NODE;
  }

  @Override
  public boolean isVariableOrNegated(int node) {
    assert isNodeValidOrRoot(node);
    if (isNodeRoot(node)) {
      return false;
    }
    long nodeStore = getNodeStore(node);
    return isVariableOrNegatedStore(nodeStore);
  }

  @Override
  public int not(int node) {
    assert isNodeValidOrRoot(node);
    pushToWorkStack(node);
    int ret = notRecursive(node);
    popWorkStack();
    return ret;
  }

  @Override
  public int notAnd(int node1, int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = notAndRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  private int notAndRecursive(int node1, int node2) {
    if (node1 == 0 || node2 == 0) {
      return 1;
    }
    if (node1 == 1 || node1 == node2) {
      return notRecursive(node2);
    }
    if (node2 == 1) {
      return notRecursive(node1);
    }

    long node1store = getNodeStore(node1);
    long node2store = getNodeStore(node2);
    //noinspection NumericCastThatLosesPrecision
    int node1var = (int) getVariableFromStore(node1store);
    //noinspection NumericCastThatLosesPrecision
    int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupNAnd(node2, node1)) {
        return cache.getLookupResult();
      }
      int hash = cache.getLookupHash();
      //noinspection NumericCastThatLosesPrecision
      int lowNode = notAndRecursive((int) getLowFromStore(node2store), node1);
      pushToWorkStack(lowNode);
      //noinspection NumericCastThatLosesPrecision
      int highNode = notAndRecursive((int) getHighFromStore(node2store), node1);
      pushToWorkStack(highNode);
      int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putNAnd(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupNAnd(node1, node2)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();
    int lowNode;
    int highNode;
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
    int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putNAnd(hash, node1, node2, resultNode);
    return resultNode;
  }

  private int notRecursive(int node) {
    if (node == FALSE_NODE) {
      return TRUE_NODE;
    }
    if (node == TRUE_NODE) {
      return FALSE_NODE;
    }

    if (cache.lookupNot(node)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();
    long nodeStore = getNodeStore(node);

    //noinspection NumericCastThatLosesPrecision
    int lowNode = pushToWorkStack(notRecursive((int) getLowFromStore(nodeStore)));
    //noinspection NumericCastThatLosesPrecision
    int highNode = pushToWorkStack(notRecursive((int) getHighFromStore(nodeStore)));
    //noinspection NumericCastThatLosesPrecision
    int resultNode = makeNode((int) getVariableFromStore(nodeStore), lowNode, highNode);
    popWorkStack(2);
    cache.putNot(hash, node, resultNode);
    return resultNode;
  }

  @Override
  void notifyGcRun() {
    // TODO partial invalidate - don't need to re-compute hashes, only throw out all invalidated
    // nodes
    cache.invalidate();
  }

  @Override
  void notifyTableSizeChanged() {
    cache.invalidate();
  }

  @Override
  public int numberOfVariables() {
    return numberOfVariables;
  }

  @Override
  public int or(int node1, int node2) {
    assert isNodeValidOrRoot(node1) && isNodeValidOrRoot(node2);
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int result = orRecursive(node1, node2);
    popWorkStack(2);
    return result;
  }

  private int orRecursive(int node1, int node2) {
    if (node1 == TRUE_NODE || node2 == TRUE_NODE) {
      return 1;
    }
    if (node1 == FALSE_NODE || node1 == node2) {
      return node2;
    }
    if (node2 == FALSE_NODE) {
      return node1;
    }

    long node1store = getNodeStore(node1);
    long node2store = getNodeStore(node2);
    //noinspection NumericCastThatLosesPrecision
    int node1var = (int) getVariableFromStore(node1store);
    //noinspection NumericCastThatLosesPrecision
    int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupOr(node2, node1)) {
        return cache.getLookupResult();
      }
      int hash = cache.getLookupHash();
      //noinspection NumericCastThatLosesPrecision
      int lowNode = pushToWorkStack(orRecursive((int) getLowFromStore(node2store), node1));
      //noinspection NumericCastThatLosesPrecision
      int highNode = pushToWorkStack(orRecursive((int) getHighFromStore(node2store), node1));
      int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putOr(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupOr(node1, node2)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();
    int lowNode;
    int highNode;
    if (node1var == node2var) {
      lowNode = orRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = pushToWorkStack(orRecursive((int) getHighFromStore(node1store),
        (int) getHighFromStore(node2store)));
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(orRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(orRecursive((int) getHighFromStore(node1store), node2));
    }
    int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putOr(hash, node1, node2, resultNode);
    return resultNode;
  }

  @Override
  public int restrict(int node, BitSet restrictedVariables,
    BitSet restrictedVariableValues) {
    assert isNodeValidOrRoot(node);
    pushToWorkStack(node);
    cache.clearVolatileCache();
    int resultNode = restrictRecursive(node, restrictedVariables, restrictedVariableValues);
    popWorkStack();
    return resultNode;
  }

  private int restrictRecursive(int node, BitSet restrictedVariables,
    BitSet restrictedVariableValues) {
    if (node == TRUE_NODE || node == FALSE_NODE) {
      return node;
    }

    long nodeStore = getNodeStore(node);
    //noinspection NumericCastThatLosesPrecision
    int nodeVariable = (int) getVariableFromStore(nodeStore);
    // The tree is sorted (variable 0 on top), hence if the algorithm descended far enough there
    // will not be any replacements.
    if (nodeVariable >= restrictedVariables.length()) {
      return node;
    }

    if (cache.lookupVolatile(node)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();

    int resultNode;
    if (restrictedVariables.get(nodeVariable)) {
      if (restrictedVariableValues.get(nodeVariable)) {
        resultNode = restrictRecursive((int) getHighFromStore(nodeStore), restrictedVariables,
          restrictedVariableValues);
      } else {
        resultNode = restrictRecursive((int) getLowFromStore(nodeStore), restrictedVariables,
          restrictedVariableValues);
      }
    } else {
      //noinspection NumericCastThatLosesPrecision
      int lowRestrict = pushToWorkStack(restrictRecursive((int) getLowFromStore(nodeStore),
        restrictedVariables, restrictedVariableValues));
      //noinspection NumericCastThatLosesPrecision
      int highRestrict = pushToWorkStack(restrictRecursive((int) getHighFromStore(nodeStore),
        restrictedVariables, restrictedVariableValues));
      resultNode = makeNode(nodeVariable, lowRestrict, highRestrict);
      popWorkStack(2);
    }
    cache.putVolatile(hash, node, resultNode);
    return resultNode;
  }

  @Override
  public void support(int node, BitSet bitSet, int highestVariable) {
    assert isNodeValidOrRoot(node);
    assert 0 <= highestVariable && highestVariable <= numberOfVariables;
    bitSet.clear();
    supportRecursive(node, bitSet, highestVariable);
    unMarkTree(node);
  }

  private void supportRecursive(int node, BitSet bitSet, int highestVariable) {
    if (isNodeRoot(node)) {
      return;
    }

    long nodeStore = getNodeStore(node);

    if (isNodeStoreMarked(nodeStore)) {
      return;
    }

    //noinspection NumericCastThatLosesPrecision
    int variable = (int) getVariableFromStore(nodeStore);

    if (variable < highestVariable) {
      bitSet.set(variable);
      markNode(node);
      supportRecursive((int) getLowFromStore(nodeStore), bitSet, highestVariable);
      supportRecursive((int) getHighFromStore(nodeStore), bitSet, highestVariable);
    }
  }

  @Override
  public int xor(int node1, int node2) {
    pushToWorkStack(node1);
    pushToWorkStack(node2);
    int ret = xorRecursive(node1, node2);
    popWorkStack(2);
    return ret;
  }

  private int xorRecursive(int node1, int node2) {
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

    long node1store = getNodeStore(node1);
    long node2store = getNodeStore(node2);
    //noinspection NumericCastThatLosesPrecision
    int node1var = (int) getVariableFromStore(node1store);
    //noinspection NumericCastThatLosesPrecision
    int node2var = (int) getVariableFromStore(node2store);

    if (node1var > node2var) {
      if (cache.lookupXor(node2, node1)) {
        return cache.getLookupResult();
      }
      int hash = cache.getLookupHash();
      //noinspection NumericCastThatLosesPrecision
      int lowNode = pushToWorkStack(xorRecursive((int) getLowFromStore(node2store), node1));
      //noinspection NumericCastThatLosesPrecision
      int highNode = pushToWorkStack(xorRecursive((int) getHighFromStore(node2store), node1));
      int resultNode = makeNode(node2var, lowNode, highNode);
      popWorkStack(2);
      cache.putXor(hash, node2, node1, resultNode);
      return resultNode;
    }

    if (cache.lookupXor(node1, node2)) {
      return cache.getLookupResult();
    }
    int hash = cache.getLookupHash();
    int lowNode;
    int highNode;
    if (node1var == node2var) {
      lowNode = xorRecursive((int) getLowFromStore(node1store), (int) getLowFromStore(node2store));
      pushToWorkStack(lowNode);
      highNode = pushToWorkStack(xorRecursive((int) getHighFromStore(node1store),
        (int) getHighFromStore(node2store)));
    } else { // v < getVariable(node2)
      lowNode = pushToWorkStack(xorRecursive((int) getLowFromStore(node1store), node2));
      highNode = pushToWorkStack(xorRecursive((int) getHighFromStore(node1store), node2));
    }
    int resultNode = makeNode(node1var, lowNode, highNode);
    popWorkStack(2);
    cache.putXor(hash, node1, node2, resultNode);
    return resultNode;
  }

  private static final class MinimalSolutionIterator implements Iterator<BitSet> {
    private final BddImpl bdd;
    private final BitSet bitSet;
    private final int[] path;
    private boolean firstRun = true;
    private int highestLowVariableWithNonFalseHighBranch = 0;
    private int leafPosition;
    private boolean next;

    MinimalSolutionIterator(BddImpl bdd, int node) {
      // Require at least one possible solution to exist.
      assert bdd.isNodeValid(node) || node == TRUE_NODE;
      // Assignments don't make much sense otherwise
      assert bdd.numberOfVariables() > 0;

      this.bdd = bdd;
      this.path = new int[bdd.numberOfVariables()];
      this.bitSet = new BitSet(bdd.numberOfVariables());

      path[0] = node;
      Arrays.fill(path, 1, path.length, -1);
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
          do {
            branchPosition -= 1;
          }
          while (path[branchPosition] == -1);
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
        long currentNodeStore = bdd.getNodeStore(currentNode);
        leafPosition = (int) getVariableFromStore(currentNodeStore);
        path[leafPosition] = currentNode;

        //noinspection NumericCastThatLosesPrecision
        int low = (int) getLowFromStore(currentNodeStore);
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
