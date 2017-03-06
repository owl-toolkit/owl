package owl.factories.jdd.bdd;

import static com.google.common.base.Preconditions.checkState;
import static owl.util.BitUtil.fits;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.util.BitUtil;

/* Implementation notes:
 * - Many asserts in the long store accessor functions are commented out, as the JVM might not
 *   inline the methods when they are too big. */
@SuppressWarnings({"PMD.AvoidDeeplyNestedIfStmts", "PMD.GodClass", "WeakerAccess", "unused"})
class NodeTable {
  /**
   * The maximal supported bit size of the node identifier. Internally, we store the tree structure
   * (variable, low and high) inside longs, which means that we have a special upper bound for each
   * of those values. 25 bits allows us to have 33.554.432 different nodes, which should be more
   * than enough.
   */
  public static final int NODE_IDENTIFIER_BIT_SIZE = 25;
  /* Bits allocated for the hash list references */
  private static final int CHAIN_REFERENCE_BIT_SIZE = NODE_IDENTIFIER_BIT_SIZE;
  private static final int INITIAL_STACK_SIZE = 32;
  private static final long LIST_REFERENCE_MASK = BitUtil.maskLength(CHAIN_REFERENCE_BIT_SIZE);
  /* The _OFFSET variables are used to indicate the bit offset of each value,
   * the _MASK variables are the masks used to filter the values _after_ shifting them to the front
   */
  private static final int LOW_OFFSET = 1;
  /* Bits allocated for the reference counter */
  private static final int REFERENCE_COUNT_BIT_SIZE = 13;
  private static final long MAXIMAL_REFERENCE_COUNT = 1L << REFERENCE_COUNT_BIT_SIZE - 1;
  private static final long REFERENCE_COUNT_MASK = BitUtil.maskLength(REFERENCE_COUNT_BIT_SIZE);
  private static final int REFERENCE_COUNT_OFFSET = 1;
  private static final int CHAIN_NEXT_OFFSET = REFERENCE_COUNT_BIT_SIZE + REFERENCE_COUNT_OFFSET;
  private static final int CHAIN_START_OFFSET =
    REFERENCE_COUNT_BIT_SIZE + REFERENCE_COUNT_OFFSET + CHAIN_REFERENCE_BIT_SIZE;
  /* Bits allocated for the high and low numbers */
  private static final int TREE_REFERENCE_BIT_SIZE = NODE_IDENTIFIER_BIT_SIZE;
  private static final int HIGH_OFFSET = TREE_REFERENCE_BIT_SIZE + 1;
  private static final long TREE_REFERENCE_MASK = BitUtil.maskLength(TREE_REFERENCE_BIT_SIZE);
  /* Bits allocated for the variable number */
  private static final int VARIABLE_BIT_SIZE = 13;
  /* Mask used to indicate invalid nodes */
  private static final long INVALID_NODE_VALUE = BitUtil.maskLength(VARIABLE_BIT_SIZE);
  private static final int VARIABLE_OFFSET = 2 * TREE_REFERENCE_BIT_SIZE + 1;
  private static final Logger logger = Logger.getLogger(NodeTable.class.getName());

  static {
    //noinspection ConstantConditions
    assert VARIABLE_BIT_SIZE + 2 * TREE_REFERENCE_BIT_SIZE + 1 <= Long.SIZE :
      "Bit sizes don't match";
    //noinspection ConstantConditions
    assert 2 * CHAIN_REFERENCE_BIT_SIZE <= Long.SIZE : "Bit sizes don't match";
  }

  private final BddConfiguration configuration;
  /* Under-approximation of dead node count. */
  private int approximateDeadNodeCount;
  /* Tracks the index of the last node which is referenced. Invariants on this variable:
   * biggestReferencedNode <= biggestValidNode and if a node has positive reference count, its
   * index is less than or equal to biggestReferencedNode. */
  private int biggestReferencedNode;
  /* Keep track of the last used node to terminate some loops early. The invariant is that if a node
   * is valid, then the node index is less than or equal to biggestValidNode. */
  private int biggestValidNode;
  /* First free (invalid) node, used when a new node is created. */
  private int firstFreeNode;
  /* Number of free (invalid) nodes. Used to determine if the table needs to be grown when adding a
   * node. Potentially, we could instead check if the next chain entry of firstFreeNode is 0. */
  private int freeNodeCount;
  /* The markStack is used by the marking algorithm to keep track of nodes which need to be
   * explored. This is realized by a field for various reasons. First, we don't have to allocate it
   * every time we want to recursively mark. Secondly, if we want to mark multiple node trees, we
   * can simply put them on the stack and then run the mark algorithm. */
  private int[] markStack;
  /* The nodeStorage contains the actual BDD nodes, where each long is used to store the variable,
   * low, high and a mask used by various internal algorithms. These values are manipulated through
   * various static helper functions. Layout: <---VAR---><---HIGH--><---LOW---><MASK> */
  private long[] nodeStorage;
  /* The referenceStorage contains multiple values used to ensure uniqueness of nodes and keeping
   * track of reference counts. Aside from the straightforward reference counting, the array is used
   * as a hash map for existing nodes and a linked list for free nodes. The semantics of the "next
   * chain entry" change, depending on whether the node is valid or not.
   *
   * When a node with a certain hash is created, we add a pointer to the corresponding hash bucket
   * obtainable by getChainStartFromStore. Whenever we add another node with the same value, this
   * node gets added to the chain and one can traverse the chain by repeatedly calling
   * getNextChainEntryFromStore on the chain start. If however a node is invalid, the "next chain
   * entry" points to the next free node. This saves some time when creating nodes, as we don't have
   * to scan through our BDD to find the next node which we can update a value.
   *
   * Layout: <---PREV--><---NEXT--><---REF---><SATURATED> */
  private long[] referenceStorage;
  /* This is just the number of variables. As currently, BDD and NodeTable are separate, both need
   * to keep track of this value. While not entirely necessary, we can use this value to ensure
   * that our markStack is big enough for a full recursive marking. */
  private int treeSize;
  /* The work stack is used to store intermediate nodes created by some BDD operations. While
   * constructing a new BDD, e.g. v1 and v2, we may need to create multiple intermediate BDDs. As
   * during each creation the node table may run out of space, GC might be called and could
   * delete the intermediately created nodes. As increasing and decreasing the reference counter
   * every time is more expensive than just putting the values on the stack, we use this data
   * structure. */
  private int[] workStack;
  /* Current top of the work stack. */
  private int workStackTos;

  NodeTable(final int nodeSize, final BddConfiguration configuration) {
    this.configuration = configuration;
    final int nodeCount = Math.max(nodeSize, configuration.minimumNodeTableSize());
    nodeStorage = new long[nodeCount];
    referenceStorage = new long[nodeCount];

    firstFreeNode = 2;
    freeNodeCount = nodeCount - 2;
    biggestReferencedNode = 1;
    biggestValidNode = 1;

    Arrays.fill(nodeStorage, 2, nodeCount, invalidStore());
    for (int i = 0; i < nodeCount - 1; i++) {
      referenceStorage[i] = startNoneAndNextStore(i + 1);
    }
    referenceStorage[nodeCount - 1] = startNoneAndNextStore(0);

    nodeStorage[0] = buildNodeStore(INVALID_NODE_VALUE, 0L, 0L);
    nodeStorage[1] = buildNodeStore(INVALID_NODE_VALUE, 1L, 1L);
    referenceStorage[0] = saturateStore(referenceStorage[0]);
    referenceStorage[1] = saturateStore(referenceStorage[1]);

    workStack = new int[INITIAL_STACK_SIZE];
    markStack = new int[INITIAL_STACK_SIZE];
  }

  private static long buildNodeStore(final long variable, final long low, final long high) {
    assert fits(variable, VARIABLE_BIT_SIZE) && fits(low, TREE_REFERENCE_BIT_SIZE) && fits(high,
      TREE_REFERENCE_BIT_SIZE) : "Bit size exceeded";
    long store = 0L;
    store |= low << LOW_OFFSET;
    store |= high << HIGH_OFFSET;
    store |= variable << VARIABLE_OFFSET;
    return store;
  }

  private static long clearChainStartInStore(final long referenceStore) {
    //noinspection NumericOverflow
    return referenceStore & ~(LIST_REFERENCE_MASK << CHAIN_START_OFFSET);
  }

  private static long decreaseReferenceCountInStore(final long referenceStore) {
    assert !isStoreSaturated(referenceStore);
    return referenceStore - 2L;
  }

  private static long getChainStartFromStore(final long referenceStore) {
    return (referenceStore >>> CHAIN_START_OFFSET) & LIST_REFERENCE_MASK;
  }

  static long getHighFromStore(final long nodeStore) {
    return (nodeStore >>> HIGH_OFFSET) & TREE_REFERENCE_MASK;
  }

  static long getLowFromStore(final long nodeStore) {
    return (nodeStore >>> LOW_OFFSET) & TREE_REFERENCE_MASK;
  }

  private static long getMarkFromStore(final long nodeStore) {
    return nodeStore & 1L;
  }

  private static int getMarkStackSizeForTreeSize(final int treeSize) {
    return treeSize * 4 + 3;
  }

  private static long getNextChainEntryFromStore(final long referenceStore) {
    return (referenceStore >>> CHAIN_NEXT_OFFSET) & LIST_REFERENCE_MASK;
  }

  private static long getReferenceCountFromStore(final long referenceStore) {
    assert !isStoreSaturated(referenceStore);
    return (referenceStore >>> 1) & REFERENCE_COUNT_MASK;
  }

  static long getVariableFromStore(final long nodeStore) {
    return nodeStore >>> VARIABLE_OFFSET;
  }

  private static long increaseReferenceCountInStore(final long referenceStore) {
    assert !isStoreSaturated(referenceStore);
    return referenceStore + 2L;
  }

  private static long invalidStore() {
    return INVALID_NODE_VALUE << VARIABLE_OFFSET;
  }

  static boolean isNodeStoreMarked(final long nodeStore) {
    return getMarkFromStore(nodeStore) != 0L;
  }

  private static boolean isReferencedOrSaturatedNodeStore(final long referenceStore) {
    return isStoreSaturated(referenceStore) || getReferenceCountFromStore(referenceStore) > 0L;
  }

  private static boolean isStoreSaturated(final long referenceStore) {
    return (referenceStore & 1L) != 0L;
  }

  private static boolean isValidNodeStore(final long nodeStore) {
    return (nodeStore >>> VARIABLE_OFFSET) != INVALID_NODE_VALUE;
  }

  private static boolean nodeStoresEqual(final long nodeStore, final long otherNodeStore) {
    // See if the two nodes are equal. As the layout is <VAL><HI><LO><MASK>, value, high and low
    // are equal if the two differ at most on the mask.
    return (nodeStore >>> 1) == (otherNodeStore >>> 1);
  }

  private static long saturateStore(final long referenceStore) {
    return referenceStore | 1L;
  }

  private static long setChainStartInStore(final long referenceStore, final long chainStart) {
    assert 0L <= chainStart && fits(chainStart, CHAIN_REFERENCE_BIT_SIZE);
    //noinspection NumericOverflow
    return (referenceStore & ~(LIST_REFERENCE_MASK << CHAIN_START_OFFSET))
      | chainStart << CHAIN_START_OFFSET;
  }

  private static long setMarkInStore(final long nodeStore) {
    assert isValidNodeStore(nodeStore);
    return nodeStore | 1L;
  }

  private static long setNextChainEntryInStore(final long referenceStore, final long next) {
    assert 0L <= next && fits(next, CHAIN_REFERENCE_BIT_SIZE);
    return (referenceStore & ~(LIST_REFERENCE_MASK << CHAIN_NEXT_OFFSET))
      | next << CHAIN_NEXT_OFFSET;
  }

  private static long startNoneAndNextStore(final int next) {
    assert 0 <= next && fits(next, CHAIN_REFERENCE_BIT_SIZE);
    return ((long) next) << CHAIN_NEXT_OFFSET;
  }

  private static long unsetMarkInStore(final long nodeStore) {
    assert isValidNodeStore(nodeStore);
    return nodeStore & ~1L;
  }

  /**
   * Over-approximates the number of nodes below the specified {@code node}, possibly counting
   * shared sub-trees multiple times. Guaranteed to be bigger or equal to {@link #nodeCount(int)}.
   *
   * @param node
   *     The node to be counted.
   *
   * @return An approximate number of non-root nodes below {@code node}.
   *
   * @see #nodeCount(int)
   */
  public final int approximateNodeCount(final int node) {
    assert isNodeValidOrRoot(node);
    if (isNodeRoot(node)) {
      return 0;
    }
    final long bddStore = nodeStorage[node];
    return 1 + approximateNodeCount((int) getLowFromStore(bddStore)) + approximateNodeCount(
      (int) getHighFromStore(bddStore));
  }

  /**
   * Performs some integrity / invariant checks.
   *
   * @return True. This way, check can easily be called by an {@code assert} statement.
   */
  @SuppressWarnings("ConstantConditions")
  final boolean check() {
    checkState(biggestReferencedNode <= biggestValidNode);

    // Check the biggestValidNode variable
    checkState(biggestValidNode < 2 || isValidNodeStore(nodeStorage[biggestValidNode]),
      "Node (s) is not valid or root", nodeToStringSupplier(biggestValidNode));
    for (int i = biggestValidNode + 1; i < getTableSize(); i++) {
      checkState(!isValidNodeStore(nodeStorage[i]), "Node (%s) is valid", nodeToStringSupplier(i));
    }

    // Check biggestReferencedNode variable
    checkState(isReferencedOrSaturatedNodeStore(referenceStorage[biggestReferencedNode]),
      "Node (%s) is not referenced", nodeToStringSupplier(biggestReferencedNode));
    for (int i = biggestReferencedNode + 1; i < getTableSize(); i++) {
      checkState(!isReferencedOrSaturatedNodeStore(referenceStorage[i]), "Node (%s) is referenced",
        nodeToStringSupplier(i));
    }

    // Check invalid nodes are not referenced
    for (int i = 2; i <= biggestReferencedNode; i++) {
      if (isReferencedOrSaturatedNodeStore(referenceStorage[i])) {
        checkState(isValidNodeStore(nodeStorage[i]), "Node (%s) is referenced but invalid",
          nodeToStringSupplier(i));
      }
    }

    // Check if the number of free nodes is correct
    int count = 2;
    for (int i = 2; i <= biggestValidNode; i++) {
      if (isValidNodeStore(nodeStorage[i])) {
        count++;
      }
    }
    checkState(count == (getTableSize() - freeNodeCount),
      "Invalid # of free nodes: #live=%s, size=%s, free=%s", count, getTableSize(),
      freeNodeCount);

    // Check each node's children
    for (int i = 2; i <= biggestValidNode; i++) {
      final long nodeStore = nodeStorage[i];
      if (isValidNodeStore(nodeStore)) {
        final int low = (int) getLowFromStore(nodeStore);
        final int high = (int) getHighFromStore(nodeStore);
        checkState(isNodeValidOrRoot(low), "Invalid low entry (%s) -> (%s)",
          nodeToStringSupplier(i), nodeToStringSupplier(low));
        checkState(isNodeValidOrRoot(high), "Invalid high entry (%s) -> (%s)",
          nodeToStringSupplier(i), nodeToStringSupplier(high));
        if (!isNodeRoot(low)) {
          checkState(getVariableFromStore(nodeStore) < getVariableFromStore(nodeStorage[low]),
            "(%s) -> (%s) does not descend tree", nodeToStringSupplier(i),
            nodeToStringSupplier(low));
        }
        if (!isNodeRoot(high)) {
          checkState(getVariableFromStore(nodeStore) < getVariableFromStore(nodeStorage[high]),
            "(%s) -> (%s) does not descend tree", nodeToStringSupplier(i),
            nodeToStringSupplier(high));
        }
      }
    }

    // Check if there are duplicate nodes
    final int maximalNodeCountChecked = 500;
    if (getTableSize() < maximalNodeCountChecked) {
      for (int i = 2; i <= biggestValidNode; i++) {
        final long storeOfI = nodeStorage[i];
        if (isValidNodeStore(storeOfI)) {
          for (int j = i + 1; j < getTableSize(); j++) {
            final long storeOfJ = nodeStorage[j];
            if (isValidNodeStore(storeOfJ)) {
              checkState(!nodeStoresEqual(storeOfI, storeOfJ), "Duplicate entries (%s) and (%s)",
                nodeToStringSupplier(i), nodeToStringSupplier(j));
            }
          }
        }
      }
    }

    // Check the integrity of the hash chain
    for (int i = 2; i < getTableSize(); i++) {
      final long nodeStore = nodeStorage[i];
      if (isValidNodeStore(nodeStore)) {
        // Check if each element is in its own hash chain
        int chainPosition = (int) getChainStartFromStore(
          referenceStorage[hashNodeStore(nodeStore)]);
        boolean found = false;
        @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
        final StringBuilder hashChain = new StringBuilder();
        while (chainPosition != 0) {
          hashChain.append(' ').append(chainPosition);
          if (chainPosition == i) {
            found = true;
            break;
          }
          chainPosition = (int) getNextChainEntryFromStore(referenceStorage[chainPosition]);
        }
        checkState(found, "(%s) is not contained in it's hash list: %s", nodeToStringSupplier(i),
          hashChain);
      }
    }

    // Check firstFreeNode
    for (int i = 2; i < firstFreeNode; i++) {
      checkState(isValidNodeStore(nodeStorage[i]), "Invalid node (%s) smaller than firstFreeNode",
        nodeToStringSupplier(i));
    }

    // Check free nodes chain
    int currentFreeNode = firstFreeNode;
    do {
      checkState(!isValidNodeStore(nodeStorage[currentFreeNode]),
        "Node (%s) in free node chain is valid", nodeToStringSupplier(currentFreeNode));
      final int nextFreeNode = (int) getNextChainEntryFromStore(referenceStorage[currentFreeNode]);
      // This also excludes possible loops
      checkState(nextFreeNode == 0 || currentFreeNode < nextFreeNode,
        "Free node chain is not well ordered, %s <= %s", nextFreeNode, currentFreeNode);
      checkState(nextFreeNode < nodeStorage.length,
        "Next free node points over horizon, %s -> %s (%s)", currentFreeNode, nextFreeNode,
        nodeStorage.length);
      currentFreeNode = nextFreeNode;
    }
    while (currentFreeNode != 0);

    return true;
  }

  private void connectHashList(final int node, final int hash) {
    assert isNodeValid(node) && 0 <= hash && hash == hashNodeStore(nodeStorage[node]);
    final long hashReferenceStore = referenceStorage[hash];
    final int hashChainStart = (int) getChainStartFromStore(hashReferenceStore);

    // Search the hash list if this node is already in there in order to avoid loops
    int currentChainNode = hashChainStart;
    while (currentChainNode != 0) {
      if (currentChainNode == node) {
        // The node is already contained in the hash list
        return;
      }
      final long currentChainStore = referenceStorage[currentChainNode];
      assert (int) getNextChainEntryFromStore(currentChainStore) != currentChainNode;
      currentChainNode = (int) getNextChainEntryFromStore(currentChainStore);
    }

    referenceStorage[node] = setNextChainEntryInStore(referenceStorage[node],
      (long) hashChainStart);
    // when node == hash, we have to re-fetch the changed entry and can't reuse hashReferenceStore
    referenceStorage[hash] = setChainStartInStore(referenceStorage[hash], (long) node);
  }

  public final int dereference(final int node) {
    assert isNodeValidOrRoot(node);
    final long referenceStore = referenceStorage[node];
    if (isStoreSaturated(referenceStore)) {
      return node;
    }
    assert getReferenceCountFromStore(referenceStore) > 0L;
    if (getReferenceCountFromStore(referenceStore) == 1L) {
      // After decrease its 0

      // We may be under-approximating the actual dead node count here - it could be the case that
      // this node was the only one keeping it's children "alive".
      approximateDeadNodeCount++;
      if (node == biggestReferencedNode) {
        // Update biggestReferencedNode
        for (int i = biggestReferencedNode - 1; i >= 0; i--) {
          if (isReferencedOrSaturatedNodeStore(referenceStorage[i])) {
            biggestReferencedNode = i;
            break;
          }
        }
      }
    }
    referenceStorage[node] = decreaseReferenceCountInStore(referenceStore);
    return node;
  }

  private int doGarbageCollection() {
    assert check();
    int topOfStack = 0;
    for (int i = 0; i < workStackTos; i++) {
      final int node = workStack[i];
      if (!isNodeRoot(node) && isNodeValid(node)) {
        ensureMarkStackSize(topOfStack);
        markStack[topOfStack] = node;
        topOfStack += 1;
      }
    }

    // Clear chain starts (we need to rebuild them) and push referenced nodes on the mark stack.
    // Loops are merged so that referenceStore[i] does not have to be loaded twice (this the JVM
    // probably can't optimise as there are no guarantees about multi-threading etc.). All those
    // nodes bigger than biggestValidNode will be cleared below.
    referenceStorage[0] = clearChainStartInStore(referenceStorage[0]);
    referenceStorage[1] = clearChainStartInStore(referenceStorage[1]);
    for (int i = 2; i <= biggestValidNode; i++) {
      final long referenceStore = referenceStorage[i];
      if (i <= biggestReferencedNode && isReferencedOrSaturatedNodeStore(referenceStore)) {
        ensureMarkStackSize(topOfStack);
        markStack[topOfStack] = i;
        topOfStack += 1;
      }
      referenceStorage[i] = clearChainStartInStore(referenceStore);
    }
    markAllOnStack(topOfStack);

    final int previousFreeNodes = freeNodeCount;
    firstFreeNode = 0;
    freeNodeCount = getTableSize() - (biggestValidNode + 1);

    // Connect all definitely invalid nodes in the free node chain
    for (int i = getTableSize() - 1; i > biggestValidNode; i--) {
      referenceStorage[i] = setNextChainEntryInStore(referenceStorage[i], (long) firstFreeNode);
      firstFreeNode = i;
    }
    // Rebuild hash chain for valid nodes, connect invalid nodes into the free chain
    // We need to rebuild the chain for unused nodes first as a smaller, unused node might be part
    // of a chain containing bigger nodes which are in use.
    for (int i = biggestValidNode; i >= 2; i--) {
      final long nodeStore = nodeStorage[i];
      if (!isNodeStoreMarked(nodeStore)) {
        // This node is unused
        nodeStorage[i] = invalidStore();
        referenceStorage[i] = setNextChainEntryInStore(referenceStorage[i], (long) firstFreeNode);
        firstFreeNode = i;
        if (i == biggestValidNode) {
          biggestValidNode--;
        }
        freeNodeCount++;
      }
    }
    for (int i = biggestValidNode; i >= 2; i--) {
      final long nodeStore = nodeStorage[i];
      if (isNodeStoreMarked(nodeStore)) {
        // This node is used
        nodeStorage[i] = unsetMarkInStore(nodeStore);
        connectHashList(i, hashNodeStore(nodeStore));
      }
    }

    approximateDeadNodeCount = 0;

    assert check();
    return freeNodeCount - previousFreeNodes;
  }

  private void ensureMarkStackSize(final int size) {
    if (size < markStack.length) {
      return;
    }
    // At least double the size each new allocation
    markStack = Arrays.copyOf(markStack, Math.max(size, 2 * markStack.length));
  }

  private void ensureWorkStackSize(final int size) {
    if (size < workStack.length) {
      return;
    }
    final int newSize = workStack.length * 2;
    workStack = Arrays.copyOf(workStack, newSize);
  }

  /**
   * Perform garbage collection by freeing up dead nodes.
   *
   * @return Number of freed nodes.
   */
  public final int forceGc() {
    final int freedNodes = doGarbageCollection();
    notifyGcRun();
    return freedNodes;
  }

  public final int getApproximateDeadNodeCount() {
    return approximateDeadNodeCount;
  }

  final BddConfiguration getConfiguration() {
    return configuration;
  }

  public final int getFreeNodeCount() {
    return freeNodeCount;
  }

  private int getGrowSize(final int currentSize) {
    assert 0 <= configuration.minimumNodeTableGrowth()
      && 0 <= configuration.maximumNodeTableGrowth()
      && configuration.minimumNodeTableGrowth() <= configuration.maximumNodeTableGrowth();

    // TODO: Maybe check available memory
    if (currentSize <= configuration.nodeTableSmallThreshold()) {
      return currentSize + configuration.minimumNodeTableGrowth();
    }
    if (currentSize >= configuration.nodeTableBigThreshold()) {
      return currentSize + configuration.maximumNodeTableGrowth();
    }
    // Linear interpolation between {smallThresh, bigThresh} and values {smallGrowth, bigGrowth}
    final int growthDiff = configuration.maximumNodeTableGrowth()
      - configuration.minimumNodeTableGrowth();
    final int thresholdDiff = configuration.nodeTableBigThreshold()
      - configuration.nodeTableSmallThreshold();
    final int offset = currentSize - configuration.nodeTableSmallThreshold();
    final int growth = configuration.minimumNodeTableGrowth() + offset * growthDiff / thresholdDiff;
    return currentSize + growth;
  }

  public final int getHigh(final int node) {
    assert isNodeValid(node);
    return (int) getHighFromStore(nodeStorage[node]);
  }

  public final int getLow(final int node) {
    assert isNodeValid(node);
    return (int) getLowFromStore(nodeStorage[node]);
  }

  /**
   * Returns the {@code node}'s store. Probably only useful if used in conjunction with the static
   * node store manipulation methods of this class. Undefined behaviour when the node is invalid.
   *
   * @param node
   *     The node whose store should be returned.
   *
   * @return The store of {@code node}.
   *
   * @see #isNodeValid(int)
   */
  final long getNodeStore(final int node) {
    assert isNodeValid(node);
    return nodeStorage[node];
  }

  /**
   * Returns the reference count of the given node or -1 if the node is saturated.
   *
   * @param node
   *     The node to be queried.
   *
   * @return The reference count of {@code node}.
   */
  public final int getReferenceCount(final int node) {
    assert isNodeValidOrRoot(node);
    final long referenceStore = referenceStorage[node];
    if (isStoreSaturated(referenceStore)) {
      return -1;
    }
    return (int) getReferenceCountFromStore(referenceStore);
  }

  public final int getTableSize() {
    return nodeStorage.length;
  }

  public final int getVariable(final int node) {
    assert isNodeValid(node);
    return (int) getVariableFromStore(nodeStorage[node]);
  }

  /**
   * Tries to free space by garbage collection and, if that does not yield enough free nodes,
   * re-sizes the table, recreating hashes.
   *
   * @return Whether the table size has changed.
   */
  final boolean grow() {
    assert check();
    logger.log(Level.FINE, "Grow of {0} requested", this);

    if (approximateDeadNodeCount > 0
      || getTableSize() > configuration.minimumDeadNodesCountForGcInGrow()) {
      logger.log(Level.FINE, "{0} has size {1} and at least {2} dead nodes",
        new Object[] {this, getTableSize(), approximateDeadNodeCount});

      doGarbageCollection();

      if (isEnoughFreeNodesAfterGc(freeNodeCount, getTableSize())) {
        notifyGcRun(); // Force all caches to be wiped out!
        return false;
      }
    }

    // Could not free enough space by GC, start growing:
    logger.log(Level.FINER, "Growing the table of {0}", this);

    final int oldSize = getTableSize();
    final int newSize = getGrowSize(oldSize);
    assert oldSize < newSize;

    nodeStorage = Arrays.copyOf(nodeStorage, newSize);
    referenceStorage = Arrays.copyOf(referenceStorage, newSize);

    // Invalidate the new nodes and insert them into the reference list
    Arrays.fill(nodeStorage, oldSize, newSize, invalidStore());
    for (int i = 0; i < oldSize; i++) {
      long referenceStore = referenceStorage[i];
      referenceStore = clearChainStartInStore(referenceStore);
      referenceStore = setNextChainEntryInStore(referenceStore, (long) (i + 1));
      referenceStorage[i] = referenceStore;
    }
    for (int i = oldSize; i < newSize - 1; i++) {
      referenceStorage[i] = startNoneAndNextStore(i + 1);
    }
    referenceStorage[newSize - 1] = startNoneAndNextStore(0);

    firstFreeNode = oldSize;
    freeNodeCount = newSize - oldSize;

    // Update the hash references and free nodes chain of the old nodes.
    for (int i = oldSize - 1; i >= 2; i--) {
      final long nodeStore = nodeStorage[i];
      if (isValidNodeStore(nodeStore)) {
        connectHashList(i, hashNodeStore(nodeStore));
      } else {
        assert !isValidNodeStore(nodeStore);
        referenceStorage[i] = setNextChainEntryInStore(referenceStorage[i], (long) firstFreeNode);
        firstFreeNode = i;
        freeNodeCount++;
      }
    }

    assert check();

    notifyTableSizeChanged();
    return true;
  }

  /**
   * Inform the table about a new tree size, i.e. the amount of variables changed.
   *
   * @param newTreeSize
   *     The new maximal tree height (when interpreting a BDD as a graph)
   */
  final void growTree(final int newTreeSize) {
    assert newTreeSize >= treeSize;
    treeSize = newTreeSize;
    ensureMarkStackSize(getMarkStackSizeForTreeSize(newTreeSize));
    ensureWorkStackSize(newTreeSize * 2);
  }

  private int hashNodeStore(final long nodeStore) {
    final int tableSize = getTableSize();
    // Mark must not be hashed
    final int hash = Util.hash(nodeStore >>> 1) % tableSize;
    if (hash < 0) {
      return hash + tableSize;
    }
    return hash;
  }

  // I really feel like not simplifying here improves readability.
  @SuppressWarnings({"RedundantIfStatement", "PMD.SimplifyBooleanReturns"})
  private boolean isEnoughFreeNodesAfterGc(final int freeNodesCount, final int nodeCount) {
    if (freeNodesCount > configuration.minimumFreeNodeCountAfterGc()) {
      return true;
    }
    if (freeNodesCount > (int) ((float) nodeCount * configuration
      .minimumFreeNodePercentageAfterGc())) {
      return true;
    }
    return false;
  }

  /**
   * Determines whether the given {@code node} is a root node (i.e. either <tt>false</tt>
   * or <tt>true</tt>.
   *
   * @param node
   *     The node to be checked.
   *
   * @return If {@code node} is a root node.
   */
  public final boolean isNodeRoot(final int node) {
    assert 0 <= node && node < getTableSize();
    return node <= 1;
  }

  /**
   * Checks if the given {@code node} is saturated. This can happen if the node is explicitly marked
   * as saturated or gets referenced too often.
   *
   * @param node
   *     The node to be checked
   *
   * @return Whether the node is saturated
   *
   * @see #saturateNode(int)
   */
  public final boolean isNodeSaturated(final int node) {
    assert isNodeValidOrRoot(node);
    return isStoreSaturated(referenceStorage[node]);
  }

  public final boolean isNodeValid(final int node) {
    assert 0 <= node && node < getTableSize();
    return 2 <= node && node <= biggestValidNode && isValidNodeStore(nodeStorage[node]);
  }

  /**
   * Determines if the given {@code node} is either a root node or valid. For most operations it is
   * required that this is the case.
   *
   * @param node
   *     The node to be checked.
   *
   * @return If {@code} is valid or root node.
   *
   * @see #isNodeRoot(int)
   */
  public final boolean isNodeValidOrRoot(final int node) {
    assert 0 <= node && node < getTableSize() : node + " invalid";
    return node <= biggestValidNode && (isNodeRoot(node) || isValidNodeStore(nodeStorage[node]));
  }

  private boolean isNoneMarked() {
    for (int i = 2; i < nodeStorage.length; i++) {
      if (isNodeStoreMarked(nodeStorage[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if the work stack is empty. Mostly useful for integrity checking.
   *
   * @return True iff the work stack is empty.
   */
  final boolean isWorkStackEmpty() {
    return workStackTos == 0;
  }

  final int makeNode(final int variable, final int low, final int high) {
    if (low == high) {
      return low;
    }

    final long nodeStore = buildNodeStore((long) variable, (long) low, (long) high);
    int hash = hashNodeStore(nodeStore);
    int currentLookupNode = (int) getChainStartFromStore(referenceStorage[hash]);
    assert currentLookupNode < getTableSize() : "Invalid previous entry for " + hash;

    // Search for the the node in the hash chain
    while (currentLookupNode != 0) {
      if (nodeStoresEqual(nodeStore, nodeStorage[currentLookupNode])) {
        return currentLookupNode;
      }
      final long currentLookupNodeReferenceStore = referenceStorage[currentLookupNode];
      assert (int) getNextChainEntryFromStore(currentLookupNodeReferenceStore) != currentLookupNode;
      currentLookupNode = (int) getNextChainEntryFromStore(currentLookupNodeReferenceStore);
    }

    // Check we have enough space to add the node
    assert freeNodeCount > 0;
    if (freeNodeCount == 1) {
      // We need a starting point for the free chain node, hence grow if only one node is remaining
      // instead of occupying that node
      if (grow()) { // NOPMD
        // Table size has changed, hence re-hash
        hash = hashNodeStore(nodeStore);
      }
    }

    // Take next free node
    final int freeNode = firstFreeNode;
    firstFreeNode = (int) getNextChainEntryFromStore(referenceStorage[firstFreeNode]);
    assert firstFreeNode < nodeStorage.length;
    freeNodeCount--;
    assert !isValidNodeStore(nodeStorage[freeNode]) : "Overwriting existing node";
    // Adjust and write node
    nodeStorage[freeNode] = nodeStore;
    if (biggestValidNode < freeNode) {
      biggestValidNode = freeNode;
    }
    connectHashList(freeNode, hash);
    return freeNode;
  }

  private void markAllOnStack(final int topOfMarkStack) {
    int currentTopOfStack = topOfMarkStack;
    /* The algorithm does not descend into trees whose root is marked, hence at the start of the
     * algorithm, there must be no marks in the tree to ensure correctness. */
    assert isNoneMarked();
    for (int i = 0; i < currentTopOfStack; i++) {
      final int node = markStack[i];
      nodeStorage[node] = setMarkInStore(nodeStorage[node]);
    }

    ensureMarkStackSize(currentTopOfStack + getMarkStackSizeForTreeSize(treeSize));
    while (currentTopOfStack > 0) {
      currentTopOfStack -= 1;
      final int currentNode = markStack[currentTopOfStack];
      final long currentNodeStore = nodeStorage[currentNode];
      assert !isNodeRoot(currentNode) && isNodeStoreMarked(currentNodeStore);

      final int low = (int) getLowFromStore(currentNodeStore);
      if (low > 1) {
        final long lowStore = nodeStorage[low];
        if (!isNodeStoreMarked(lowStore)) {
          nodeStorage[low] = setMarkInStore(lowStore);
          markStack[currentTopOfStack] = low;
          currentTopOfStack += 1;
        }
      }

      final int high = (int) getHighFromStore(currentNodeStore);
      if (high > 1) {
        final long highStore = nodeStorage[high];
        if (!isNodeStoreMarked(highStore)) {
          nodeStorage[high] = setMarkInStore(highStore);
          markStack[currentTopOfStack] = high;
          currentTopOfStack += 1;
        }
      }
    }
  }

  final void markNode(final int node) {
    assert isNodeValid(node);
    nodeStorage[node] = setMarkInStore(nodeStorage[node]);
  }

  /**
   * Counts the number of nodes below the specified {@code node}.
   *
   * @param node
   *     The node to be counted.
   *
   * @return The number of non-root nodes below {@code node}.
   */
  public final int nodeCount(final int node) {
    assert isNodeValidOrRoot(node);
    assert isNoneMarked();
    final int result = nodeCountRecursive(node);
    if (result > 0) {
      unMarkTree(node);
    }
    assert isNoneMarked();
    return result;
  }

  /**
   * Counts the number of active nodes in the BDD (i.e. the ones which are not invalid),
   * <b>excluding</b> the root nodes.
   * </p>
   * Note that this operation is somewhat expensive.
   *
   * @return Number of active nodes.
   */
  public final int nodeCount() {
    // Strategy: We gather all root nodes (i.e. nodes which are referenced) on the mark stack, mark
    // all of their children, count all marked nodes and un-mark them.
    assert isNoneMarked();
    int topOfStack = 0;
    for (int i = 2; i < getTableSize(); i++) {
      final long nodeStore = nodeStorage[i];
      if (isValidNodeStore(nodeStore)) {
        final long referenceStore = referenceStorage[i];
        if (isStoreSaturated(referenceStore) || getReferenceCountFromStore(referenceStore) > 0L) {
          ensureMarkStackSize(topOfStack);
          markStack[topOfStack] = i;
          topOfStack += 1;
        }
      }
    }
    markAllOnStack(topOfStack);
    int count = 0;
    for (int i = 2; i < getTableSize(); i++) {
      final long nodeStore = nodeStorage[i];
      if (isValidNodeStore(nodeStore)) {
        final long unmarkedNodeStore = unsetMarkInStore(nodeStore);
        if (nodeStore != unmarkedNodeStore) { // Node was marked
          count++;
          nodeStorage[i] = unmarkedNodeStore;
        }
      }
    }
    assert isNoneMarked();
    return count;
  }

  private int nodeCountRecursive(final int node) {
    if (isNodeRoot(node)) {
      return 0;
    }
    final long bddStore = nodeStorage[node];
    if (isNodeStoreMarked(bddStore)) {
      // This node has already been counted
      return 0;
    }
    nodeStorage[node] = setMarkInStore(bddStore);
    return 1 + nodeCountRecursive((int) getLowFromStore(bddStore)) + nodeCountRecursive(
      (int) getHighFromStore(bddStore));
  }

  final String nodeToString(final int node) {
    final long nodeStore = nodeStorage[node];
    if (!isValidNodeStore(nodeStore)) {
      return String.format("%5d| == INVALID ==", node);
    }
    final long referenceStore = referenceStorage[node];
    final String referenceCountString;
    if (isStoreSaturated(referenceStore)) {
      referenceCountString = "SAT";
    } else {
      referenceCountString = String.format("%3d", getReferenceCountFromStore(referenceStore));
    }
    return String.format("%5d|%3d|%5d|%5d|%s", node, getVariableFromStore(nodeStore),
      getLowFromStore(nodeStore), getHighFromStore(nodeStore), referenceCountString);
  }

  final NodeToStringSupplier nodeToStringSupplier(final int node) {
    return new NodeToStringSupplier(this, node);
  }

  void notifyGcRun() { /* do nothing */ }

  void notifyTableSizeChanged() {
    /* do nothing */
  }

  /**
   * Removes the topmost element from the stack.
   *
   * @see #pushToWorkStack(int)
   */
  final void popWorkStack() {
    assert workStackTos >= 1;
    workStackTos--;
  }

  /**
   * Removes the {@code amount} topmost elements from the stack.
   *
   * @param amount
   *     The amount of elements to be removed.
   *
   * @see #pushToWorkStack(int)
   */
  final void popWorkStack(final int amount) {
    assert workStackTos >= amount;
    workStackTos -= amount;
  }

  /**
   * Pushes the given node onto the stack. While a node is on the work stack, it will not be
   * garbage collected. Hence, elements should be popped from the stack as soon as they are not
   * used anymore.
   *
   * @param node
   *     The node to be pushed.
   *
   * @return The given {@code node}, to be used for chaining.
   *
   * @see #popWorkStack(int)
   */
  final int pushToWorkStack(final int node) {
    assert isNodeValidOrRoot(node);
    ensureWorkStackSize(workStackTos);
    workStack[workStackTos] = node;
    workStackTos += 1;
    return node;
  }

  public final int reference(final int node) {
    assert isNodeValidOrRoot(node);
    final long referenceStore = referenceStorage[node];
    if (isStoreSaturated(referenceStore)) {
      return node;
    }
    assert 0L <= getReferenceCountFromStore(referenceStore);

    // This node is referenced to often, we have to saturate it. :(
    if (getReferenceCountFromStore(referenceStore) == MAXIMAL_REFERENCE_COUNT) {
      referenceStorage[node] = saturateStore(referenceStore);
    } else {
      referenceStorage[node] = increaseReferenceCountInStore(referenceStore);
    }
    // Can't decrease approximateDeadNodeCount here - we may reference a node for the first time.
    if (node > biggestReferencedNode) {
      biggestReferencedNode = node;
    }
    return node;
  }

  /**
   * Counts the number of referenced or saturated nodes.
   *
   * @return Number of referenced nodes.
   */
  public final int referencedNodeCount() {
    int count = 0;
    for (int i = 2; i < biggestReferencedNode; i++) {
      if (isValidNodeStore(nodeStorage[i]) && isReferencedOrSaturatedNodeStore(
        referenceStorage[i])) {
        count++;
      }
    }
    return count;
  }

  final void saturateNode(final int node) {
    assert isNodeValidOrRoot(node);
    if (node > biggestReferencedNode) {
      biggestReferencedNode = node;
    }
    referenceStorage[node] = saturateStore(referenceStorage[node]);
  }

  /**
   * Generates a string representation of the given {@code node}.
   *
   * @param node
   *     The node to be printed.
   *
   * @return A string representing the given node.
   */
  public final String treeToString(final int node) {
    assert isNodeValidOrRoot(node);
    assert isNoneMarked();
    if (isNodeRoot(node)) {
      return "Node " + node + "\n";
    }
    @SuppressWarnings("PMD.ConsecutiveLiteralAppends")
    final StringBuilder builder = new StringBuilder(50).append("Node ").append(node).append('\n')
      .append("  NODE|VAR| LOW | HIGH|REF\n");
    treeToStringRecursive(node, builder);
    unMarkTree(node);
    return builder.toString();
  }

  private void treeToStringRecursive(final int node, final StringBuilder builder) {
    if (isNodeRoot(node)) {
      return;
    }
    final long nodeStore = nodeStorage[node];
    if (isNodeStoreMarked(nodeStore)) {
      return;
    }
    nodeStorage[node] = setMarkInStore(nodeStore);
    builder.append(' ').append(nodeToString(node)).append('\n');
    treeToStringRecursive((int) getLowFromStore(nodeStore), builder);
    treeToStringRecursive((int) getHighFromStore(nodeStore), builder);
  }

  /**
   * Removes the marks from all nodes below the specified one. This algorithm assumes that every
   * node to be unmarked has a marked parent.
   *
   * @param node
   *     The node whose descendants should be unmarked.
   */
  final void unMarkTree(final int node) {
    assert isNodeValidOrRoot(node);
    unMarkTreeRecursive(node);
    assert isNoneMarked();
  }

  private void unMarkTreeRecursive(final int node) {
    if (isNodeRoot(node)) {
      return;
    }

    final long nodeStore = nodeStorage[node];
    if (!isNodeStoreMarked(nodeStore)) {
      return;
    }

    nodeStorage[node] = unsetMarkInStore(nodeStore);
    unMarkTreeRecursive((int) getLowFromStore(nodeStore));
    unMarkTreeRecursive((int) getHighFromStore(nodeStore));
  }

  private static final class NodeToStringSupplier {
    private final int node;
    private final NodeTable table;

    public NodeToStringSupplier(final NodeTable table, final int node) {
      this.table = table;
      this.node = node;
    }

    @Override
    public String toString() {
      return table.nodeToString(node);
    }
  }
}
