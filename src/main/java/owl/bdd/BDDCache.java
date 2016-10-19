package owl.bdd;

/*
 * Possible improvements:
 *  - Not regrow every time but do partial invalidate
 */
final class BDDCache {
  private static final int CACHE_VALUE_BIT_SIZE = NodeTable.NODE_IDENTIFIER_BIT_SIZE;
  private static final long CACHE_VALUE_MASK = BitUtil.maskLength(CACHE_VALUE_BIT_SIZE);
  private static final int UNARY_CACHE_TYPE_LENGTH = 1;
  private static final int UNARY_CACHE_OPERATION_ID_OFFSET = 63;
  private static final int UNARY_CACHE_KEY_OFFSET = 25;
  private static final int UNARY_OPERATION_NOT = 0;
  private static final int BINARY_OPERATION_AND = 0;
  private static final int BINARY_OPERATION_N_AND = 1;
  private static final int BINARY_OPERATION_OR = 2;
  private static final int BINARY_OPERATION_IMPLIES = 3;
  private static final int BINARY_OPERATION_EQUIVALENCE = 4;
  private static final int BINARY_OPERATION_XOR = 5;
  private static final int TERNARY_OPERATION_ITE = 0;
  private static final int BINARY_CACHE_OPERATION_ID_OFFSET = 61;
  private static final int TERNARY_CACHE_OPERATION_ID_OFFSET = 63;

  private final BDDImpl associatedBdd;
  private final CacheAccessStatistics unaryAccessStatistics = new CacheAccessStatistics();
  private final CacheAccessStatistics binaryAccessStatistics = new CacheAccessStatistics();
  private final CacheAccessStatistics ternaryAccessStatistics = new CacheAccessStatistics();
  private final CacheAccessStatistics satisfactionAccessStatistics = new CacheAccessStatistics();
  private final int unaryBinsPerHash;
  private final int binaryBinsPerHash;
  private final int ternaryBinsPerHash;
  private final int satisfactionBinsPerHash;
  private long[] binaryKeyStorage;
  private int[] binaryResultStorage;
  private int lookupHash = -1;
  private int lookupResult = -1;
  private long[] ternaryStorage;
  private long[] unaryStorage;
  private int[] satisfactionKeyStorage;
  private double[] satisfactionResultStorage;

  public BDDCache(final BDDImpl associatedBdd) {
    this.associatedBdd = associatedBdd;
    final BDDConfiguration configuration = associatedBdd.getConfiguration();
    unaryBinsPerHash = configuration.cacheUnaryBinsPerHash();
    binaryBinsPerHash = configuration.cacheBinaryBinsPerHash();
    ternaryBinsPerHash = configuration.cacheTernaryBinsPerHash();
    satisfactionBinsPerHash = configuration.cacheSatisfactionBinsPerHash();
    reallocateUnary();
    reallocateBinary();
    reallocateTernary();
    reallocateSatisfaction();
  }

  private static void insertInLru(final long[] array, final int first, final int offset,
      final long newValue) {
    System.arraycopy(array, first, array, first + 1, offset - 1);
    array[first] = newValue;
  }

  private static void insertInTernaryLru(final long[] array, final int first, final int offset,
      final long newFirstValue, final long newSecondValue) {
    System.arraycopy(array, first, array, first + 2, offset - 2);
    array[first] = newFirstValue;
    array[first + 1] = newSecondValue;
  }

  private static void insertInLru(final int[] array, final int first, final int offset,
      final int newValue) {
    // Copy each element between first and last to its next position
    System.arraycopy(array, first, array, first + 1, offset - 1);
    array[first] = newValue;
  }

  private static void updateTernaryLru(final long[] array, final int first, final int offset) {
    insertInTernaryLru(array, first, offset, array[first + offset], array[first + offset + 1]);
  }

  private static void updateLru(final long[] array, final int first, final int offset) {
    insertInLru(array, first, offset, array[first + offset]);
  }

  private static void updateLru(final int[] array, final int first, final int offset) {
    insertInLru(array, first, offset, array[first + offset]);
  }

  private static long buildUnaryStore(final long operationId, final long inputNode,
      final long resultNode) {
    assert BitUtil.fits(inputNode, CACHE_VALUE_BIT_SIZE) &&
        BitUtil.fits(resultNode, CACHE_VALUE_BIT_SIZE);
    long store = resultNode;
    store |= inputNode << UNARY_CACHE_KEY_OFFSET;
    store |= operationId << UNARY_CACHE_OPERATION_ID_OFFSET;
    return store;
  }

  private static long getResultNodeFromUnaryStore(long unaryStore) {
    return unaryStore & CACHE_VALUE_MASK;
  }

  private static long buildBinaryKeyStore(final long operationId, final long inputNode1,
      final long inputNode2) {
    assert BitUtil.fits(inputNode1, CACHE_VALUE_BIT_SIZE) &&
        BitUtil.fits(inputNode2, CACHE_VALUE_BIT_SIZE);
    long store = inputNode1;
    store |= inputNode2 << CACHE_VALUE_BIT_SIZE;
    store |= operationId << BINARY_CACHE_OPERATION_ID_OFFSET;
    return store;
  }

  private static long getUnaryFullKeyFromUnaryStore(final long unaryStore) {
    return unaryStore & ~BitUtil.maskLength(UNARY_CACHE_KEY_OFFSET);
  }

  private static long buildUnaryFullKey(final long operationId, final long inputNode) {
    assert BitUtil.fits(operationId, UNARY_CACHE_TYPE_LENGTH) &&
        BitUtil.fits(operationId, CACHE_VALUE_BIT_SIZE);
    return (operationId << UNARY_CACHE_OPERATION_ID_OFFSET) |
        (inputNode << UNARY_CACHE_KEY_OFFSET);
  }

  private static boolean isUnaryOperation(final int operationId) {
    return operationId == UNARY_OPERATION_NOT;
  }

  private static boolean isBinaryOperation(final int operationId) {
    return operationId == BINARY_OPERATION_AND || operationId == BINARY_OPERATION_EQUIVALENCE ||
        operationId == BINARY_OPERATION_IMPLIES || operationId == BINARY_OPERATION_N_AND ||
        operationId == BINARY_OPERATION_OR || operationId == BINARY_OPERATION_XOR;
  }

  private static boolean isTernaryOperation(final int operationId) {
    return operationId == TERNARY_OPERATION_ITE;
  }

  private static long buildTernaryFirstStore(long operationId, long inputNode1, long inputNode2) {
    assert BitUtil.fits(inputNode1, CACHE_VALUE_BIT_SIZE) &&
        BitUtil.fits(inputNode2, CACHE_VALUE_BIT_SIZE);
    long store = inputNode1;
    store |= inputNode2 << CACHE_VALUE_BIT_SIZE;
    store |= operationId << TERNARY_CACHE_OPERATION_ID_OFFSET;
    return store;
  }

  private static long buildTernarySecondStore(long inputNode3, long resultNode) {
    assert BitUtil.fits(inputNode3, CACHE_VALUE_BIT_SIZE) &&
        BitUtil.fits(resultNode, CACHE_VALUE_BIT_SIZE);
    long store = resultNode;
    store |= inputNode3 << CACHE_VALUE_BIT_SIZE;
    assert inputNode3 == getInputNodeFromTernarySecondStore(store);
    assert resultNode == getResultNodeFromTernarySecondStore(store);
    return store;
  }

  private static long getResultNodeFromTernarySecondStore(final long ternarySecondStore) {
    return ternarySecondStore & CACHE_VALUE_MASK;
  }

  private static long getInputNodeFromTernarySecondStore(final long ternarySecondStore) {
    return ternarySecondStore >>> CACHE_VALUE_BIT_SIZE;
  }

  boolean lookupNot(final int node) {
    return unaryLookup(UNARY_OPERATION_NOT, node);
  }

  boolean lookupAnd(final int inputNode1, final int inputNode2) {
    return binaryLookup(BINARY_OPERATION_AND, inputNode1, inputNode2);
  }

  boolean lookupOr(final int inputNode1, final int inputNode2) {
    return binaryLookup(BINARY_OPERATION_OR, inputNode1, inputNode2);
  }

  boolean lookupXor(final int inputNode1, final int inputNode2) {
    return binaryLookup(BINARY_OPERATION_XOR, inputNode1, inputNode2);
  }

  boolean lookupEquivalence(final int inputNode1, final int inputNode2) {
    return binaryLookup(BINARY_OPERATION_EQUIVALENCE, inputNode1, inputNode2);
  }

  boolean lookupNAnd(final int inputNode1, final int inputNode2) {
    return binaryLookup(BINARY_OPERATION_N_AND, inputNode1, inputNode2);
  }

  boolean lookupITE(final int inputNode1, final int inputNode2, final int inputNode3) {
    return ternaryLookup(TERNARY_OPERATION_ITE, inputNode1, inputNode2, inputNode3);
  }

  boolean lookupImplication(final int inputNode1, final int inputNode2) {
    return binaryLookup(BINARY_OPERATION_IMPLIES, inputNode1, inputNode2);
  }

  double lookupSatisfaction(final int node) {
    assert associatedBdd.isNodeValid(node);
    final int hash = hashSatisfaction(node);
    final int cachePosition = getSatisfactionCachePosition(hash);

    lookupHash = hash;
    if (node == satisfactionKeyStorage[cachePosition]) {
      satisfactionAccessStatistics.cacheHit();
      return satisfactionResultStorage[cachePosition];
    }
    satisfactionAccessStatistics.cacheMiss();
    return -1d;
  }

  void invalidateUnary() {
    unaryAccessStatistics.invalidation();
    reallocateUnary();
  }

  void invalidateTernary() {
    ternaryAccessStatistics.invalidation();
    reallocateTernary();
  }

  void invalidateSatisfaction() {
    satisfactionAccessStatistics.invalidation();
    reallocateSatisfaction();
  }

  void invalidateBinary() {
    binaryAccessStatistics.invalidation();
    reallocateBinary();
  }

  void invalidate() {
    invalidateUnary();
    invalidateBinary();
    invalidateTernary();
    invalidateSatisfaction();
  }

  void putNot(final int hash, final int inputNode, final int resultNode) {
    assert associatedBdd.isNodeValid(inputNode) && associatedBdd.isNodeValidOrRoot(resultNode);
    unaryPut(UNARY_OPERATION_NOT, hash, inputNode, resultNode);
  }

  void putAnd(final int hash, final int inputNode1, final int inputNode2,
      final int resultNode) {
    binaryPut(BINARY_OPERATION_AND, hash, inputNode1, inputNode2, resultNode);
  }

  void putNAnd(final int hash, final int inputNode1, final int inputNode2,
      final int resultNode) {
    binaryPut(BINARY_OPERATION_N_AND, hash, inputNode1, inputNode2, resultNode);
  }

  void putOr(final int hash, final int inputNode1, final int inputNode2,
      final int resultNode) {
    binaryPut(BINARY_OPERATION_OR, hash, inputNode1, inputNode2, resultNode);
  }

  void putImplication(final int hash, final int inputNode1, final int inputNode2,
      final int resultNode) {
    binaryPut(BINARY_OPERATION_IMPLIES, hash, inputNode1, inputNode2, resultNode);
  }

  void putXor(final int hash, final int inputNode1, final int inputNode2,
      final int resultNode) {
    binaryPut(BINARY_OPERATION_XOR, hash, inputNode1, inputNode2, resultNode);
  }

  void putEquivalence(final int hash, final int inputNode1, final int inputNode2,
      final int resultNode) {
    binaryPut(BINARY_OPERATION_EQUIVALENCE, hash, inputNode1, inputNode2, resultNode);
  }

  void putNot(final int inputNode, final int resultNode) {
    unaryPut(UNARY_OPERATION_NOT, unaryHash(buildUnaryFullKey((long) UNARY_OPERATION_NOT,
        (long) inputNode)), inputNode, resultNode);
  }

  void putITE(final int hash, final int inputNode1, final int inputNode2, final int inputNode3,
      final int resultNode) {
    ternaryPut(TERNARY_OPERATION_ITE, hash, inputNode1, inputNode2, inputNode3, resultNode);
  }

  void putSatisfaction(final int hash, final int node, double satisfactionCount) {
    assert associatedBdd.isNodeValid(node);
    final int cachePosition = getSatisfactionCachePosition(hash);
    satisfactionKeyStorage[cachePosition] = node;
    satisfactionResultStorage[cachePosition] = satisfactionCount;
    satisfactionAccessStatistics.put();
  }

  int getLookupHash() {
    return lookupHash;
  }

  int getLookupResult() {
    return lookupResult;
  }

  String getStatistics() {
    return "Unary:\n" +
        " size: " + getUnaryCacheBinCount() + ", load: " + computeUnaryLoadFactor() + "\n" +
        " " + unaryAccessStatistics + "\n" +
        "Binary:\n" +
        " size: " + getBinaryCacheBinCount() + ", load: " + computeBinaryLoadFactor() + "\n" +
        " " + binaryAccessStatistics + "\n" +
        "Ternary:\n" +
        " size: " + getTernaryBinCount() + ", load: " + computeTernaryLoadFactor() + "\n" +
        " " + ternaryAccessStatistics + "\n" +
        "Satisfaction:\n" +
        " size: " + getSatisfactionBinCount() + ", load: " + computeSatisfactionLoadFactor() +
        "\n" +
        " " + satisfactionAccessStatistics + "\n";
  }

  private int ensureMinimumCacheSize(final int cacheSize) {
    if (cacheSize < associatedBdd.getConfiguration().minimumNodeTableSize()) {
      return Util.nextPrime(associatedBdd.getConfiguration().minimumNodeTableSize());
    }
    return Util.nextPrime(cacheSize);
  }

  private int hashSatisfaction(int node) {
    final int satisfactionHashSize = getSatisfactionBinCount();
    final int hash = Util.hash(node) % satisfactionHashSize;
    if (hash < 0) {
      return hash + satisfactionHashSize;
    }
    return hash;
  }

  private int getSatisfactionBinCount() {
    return satisfactionKeyStorage.length / satisfactionBinsPerHash;
  }

  private int getUnaryCacheStorageSize(final int tableSize) {
    final int baseSize = tableSize * unaryBinsPerHash /
        associatedBdd.getConfiguration().cacheUnaryDivider();
    return ensureMinimumCacheSize(baseSize);
  }

  private int getBinaryCacheStorageSize(final int tableSize) {
    final int baseSize = tableSize * binaryBinsPerHash /
        associatedBdd.getConfiguration().cacheBinaryDivider();
    return ensureMinimumCacheSize(baseSize);
  }

  private int getTernaryCacheStorageSize(final int tableSize) {
    final int baseSize = tableSize * ternaryBinsPerHash
        / associatedBdd.getConfiguration().cacheTernaryDivider();
    return ensureMinimumCacheSize(baseSize);
  }

  private int getSatisfactionCacheStorageSize(final int tableSize) {
    final int baseSize = tableSize * satisfactionBinsPerHash /
        associatedBdd.getConfiguration().cacheSatisfactionDivider();
    return ensureMinimumCacheSize(baseSize);
  }

  private int getUnaryCachePosition(final int hash) {
    return hash * unaryBinsPerHash;
  }

  private int getBinaryCachePosition(final int hash) {
    return hash * binaryBinsPerHash;
  }

  private int getTernaryCachePosition(final int hash) {
    return hash * ternaryBinsPerHash * 2;
  }

  private int getSatisfactionCachePosition(final int hash) {
    return hash * satisfactionBinsPerHash;
  }

  private void reallocateBinary() {
    final int tableSize = associatedBdd.getTableSize();
    binaryKeyStorage = new long[getBinaryCacheStorageSize(tableSize)];
    binaryResultStorage = new int[binaryKeyStorage.length];
  }

  private void reallocateUnary() {
    final int tableSize = associatedBdd.getTableSize();
    unaryStorage = new long[getUnaryCacheStorageSize(tableSize)];
  }

  private void reallocateTernary() {
    final int tableSize = associatedBdd.getTableSize();
    ternaryStorage = new long[getTernaryCacheStorageSize(tableSize)];
  }

  private void reallocateSatisfaction() {
    final int tableSize = associatedBdd.getTableSize();
    satisfactionKeyStorage = new int[getSatisfactionCacheStorageSize(tableSize)];
    satisfactionResultStorage = new double[satisfactionKeyStorage.length];
  }

  private int getTernaryBinCount() {
    return ternaryStorage.length / ternaryBinsPerHash / 2;
  }

  private boolean ternaryLookup(int operationId, int inputNode1, int inputNode2, int inputNode3) {
    assert isTernaryOperation(operationId) && associatedBdd.isNodeValid(inputNode1) &&
        associatedBdd.isNodeValid(inputNode2) && associatedBdd.isNodeValid(inputNode3);
    assert isTernaryOperation(operationId);

    final long constructedTernaryFirstStore = buildTernaryFirstStore((long) operationId,
        (long) inputNode1, (long) inputNode2);
    final int hash = ternaryHash(constructedTernaryFirstStore, inputNode3);
    final int cachePosition = getTernaryCachePosition(hash);

    int resultNode = -1;
    lookupHash = hash;

    if (ternaryBinsPerHash == 1) {
      if (constructedTernaryFirstStore != ternaryStorage[cachePosition]) {
        ternaryAccessStatistics.cacheMiss();
        return false;
      }
      final long ternarySecondStore = ternaryStorage[cachePosition + 1];
      if (inputNode3 != (int) getInputNodeFromTernarySecondStore(ternarySecondStore)) {
        ternaryAccessStatistics.cacheMiss();
        return false;
      }
      resultNode = (int) getResultNodeFromTernarySecondStore(ternarySecondStore);
    } else {
      int offset = -1;
      for (int i = 0; i < ternaryBinsPerHash * 2; i += 2) {
        if (constructedTernaryFirstStore == ternaryStorage[cachePosition + i]) {
          final long ternarySecondStore = ternaryStorage[cachePosition + i + 1];
          if (inputNode3 == (int) getInputNodeFromTernarySecondStore(ternarySecondStore)) {
            offset = i;
            resultNode = (int) getResultNodeFromTernarySecondStore(ternarySecondStore);
            break;
          }
        }
      }
      if (offset == -1) {
        ternaryAccessStatistics.cacheMiss();
        return false;
      }
      if (offset != 0) {
        updateTernaryLru(ternaryStorage, cachePosition, offset);
      }
    }

    assert associatedBdd.isNodeValidOrRoot(resultNode);
    lookupResult = resultNode;
    ternaryAccessStatistics.cacheHit();
    return true;
  }

  private int ternaryHash(long ternaryFirstStore, int inputNode3) {
    final int ternaryHashSize = getTernaryBinCount();
    final int hash = Util.hash(ternaryFirstStore, inputNode3) % ternaryHashSize;
    if (hash < 0) {
      return hash + ternaryHashSize;
    }
    return hash;
  }

  private void ternaryPut(int operationId, int hash, int inputNode1, int inputNode2,
      int inputNode3, int resultNode) {
    assert associatedBdd.isNodeValid(inputNode1) && associatedBdd.isNodeValid(inputNode2) &&
        associatedBdd.isNodeValid(inputNode3) && associatedBdd.isNodeValidOrRoot(resultNode);
    assert isTernaryOperation(operationId);
    final int cachePosition = getTernaryCachePosition(hash);
    ternaryAccessStatistics.put();

    final long firstStore = buildTernaryFirstStore((long) operationId, (long) inputNode1,
        (long) inputNode2);
    final long secondStore = buildTernarySecondStore((long) inputNode3, (long) resultNode);
    if (ternaryBinsPerHash == 1) {
      ternaryStorage[cachePosition] = firstStore;
      ternaryStorage[cachePosition + 1] = secondStore;
    } else {
      insertInTernaryLru(ternaryStorage, cachePosition, ternaryBinsPerHash * 2, firstStore,
          secondStore);
    }
  }

  private int getBinaryCacheBinCount() {
    return binaryKeyStorage.length / binaryBinsPerHash;
  }

  private void binaryPut(int operationId, int hash, int inputNode1, int inputNode2,
      int resultNode) {
    assert isBinaryOperation(operationId) && associatedBdd.isNodeValid(inputNode1) &&
        associatedBdd.isNodeValid(inputNode2) && associatedBdd.isNodeValidOrRoot(resultNode);
    int cachePosition = getBinaryCachePosition(hash);
    binaryAccessStatistics.put();

    long binaryKeyStore = buildBinaryKeyStore((long) operationId, (long) inputNode1,
        (long) inputNode2);
    if (binaryBinsPerHash == 1) {
      binaryKeyStorage[cachePosition] = binaryKeyStore;
      binaryResultStorage[cachePosition] = resultNode;
    } else {
      insertInLru(binaryKeyStorage, cachePosition, binaryBinsPerHash, binaryKeyStore);
      insertInLru(binaryResultStorage, cachePosition, binaryBinsPerHash, resultNode);
    }
  }

  private int getUnaryCacheBinCount() {
    return unaryStorage.length / unaryBinsPerHash;
  }

  private boolean binaryLookup(final int operationId, final int inputNode1, final int inputNode2) {
    assert associatedBdd.isNodeValid(inputNode1) && associatedBdd.isNodeValid(inputNode2);
    assert isBinaryOperation(operationId);
    final long binaryKey =
        buildBinaryKeyStore((long) operationId, (long) inputNode1, (long) inputNode2);
    final int hash = binaryHash(binaryKey);
    final int cachePosition = getBinaryCachePosition(hash);

    final int resultNode;
    lookupHash = hash;

    if (binaryBinsPerHash == 1) {
      if (binaryKey != binaryKeyStorage[cachePosition]) {
        binaryAccessStatistics.cacheMiss();
        return false;
      }
      resultNode = binaryResultStorage[cachePosition];
    } else {
      int offset = -1;
      for (int i = 0; i < binaryBinsPerHash; i++) {
        final long binaryKeyStore = binaryKeyStorage[cachePosition + i];
        if (binaryKey == binaryKeyStore) {
          offset = i;
          break;
        }
      }
      if (offset == -1) {
        binaryAccessStatistics.cacheMiss();
        return false;
      }
      resultNode = binaryResultStorage[cachePosition + offset];
      if (offset != 0) {
        updateLru(binaryKeyStorage, cachePosition, offset);
        updateLru(binaryResultStorage, cachePosition, offset);
      }
    }
    assert associatedBdd.isNodeValidOrRoot(resultNode);
    lookupResult = resultNode;
    binaryAccessStatistics.cacheHit();
    return true;
  }

  private int unaryHash(long unaryKey) {
    final int unaryHashSize = getUnaryCacheBinCount();
    final int hash = Util.hash(unaryKey) % unaryHashSize;
    if (hash < 0) {
      return hash + unaryHashSize;
    }
    return hash;
  }

  private int binaryHash(long binaryKey) {
    final int binaryHashSize = getBinaryCacheBinCount();
    final int hash = Util.hash(binaryKey) % binaryHashSize;
    if (hash < 0) {
      return hash + binaryHashSize;
    }
    return hash;
  }

  private boolean unaryLookup(final int operationId, final int inputNode) {
    assert associatedBdd.isNodeValid(inputNode);
    assert isUnaryOperation(operationId);
    final long unaryFullKey = buildUnaryFullKey((long) operationId, (long) inputNode);
    final int hash = unaryHash(unaryFullKey);
    final int cachePosition = getUnaryCachePosition(hash);

    int resultNode = -1;
    lookupHash = hash;

    if (unaryBinsPerHash == 1) {
      final long unaryStore = unaryStorage[cachePosition];
      long unaryStoreFullKey = getUnaryFullKeyFromUnaryStore(unaryStore);
      if (unaryFullKey != unaryStoreFullKey) {
        unaryAccessStatistics.cacheMiss();
        return false;
      }
      resultNode = (int) getResultNodeFromUnaryStore(unaryStore);
    } else {
      int offset = -1;
      for (int i = 0; i < unaryBinsPerHash; i++) {
        final long unaryStore = unaryStorage[cachePosition + i];
        final long unaryStoreFullKey = getUnaryFullKeyFromUnaryStore(unaryStore);
        if (unaryFullKey == unaryStoreFullKey) {
          offset = i;
          resultNode = (int) getResultNodeFromUnaryStore(unaryStore);
          break;
        }
      }
      if (offset == -1) {
        unaryAccessStatistics.cacheMiss();
        return false;
      }
      if (offset != 0) {
        updateLru(unaryStorage, cachePosition, offset);
      }
    }

    assert associatedBdd.isNodeValidOrRoot(resultNode);
    lookupResult = resultNode;
    unaryAccessStatistics.cacheHit();
    return true;
  }

  private void unaryPut(final int operationId, final int hash, final int inputNode,
      final int resultNode) {
    assert associatedBdd.isNodeValid(inputNode) && associatedBdd.isNodeValidOrRoot(resultNode);
    final int cachePosition = getUnaryCachePosition(hash);
    unaryAccessStatistics.put();

    final long unaryStore = buildUnaryStore((long) operationId, (long) inputNode,
        (long) resultNode);
    if (unaryBinsPerHash == 1) {
      unaryStorage[cachePosition] = unaryStore;
    } else {
      insertInLru(unaryStorage, cachePosition, unaryBinsPerHash, unaryStore);
    }
  }

  private float computeUnaryLoadFactor() {
    int loadedUnaryBins = 0;
    for (int i = 0; i < getUnaryCacheBinCount(); i++) {
      if (unaryStorage[i] != 0L) {
        loadedUnaryBins++;
      }
    }
    return (float) loadedUnaryBins / (float) getUnaryCacheBinCount();
  }

  private float computeBinaryLoadFactor() {
    int loadedBinaryBins = 0;
    for (int i = 0; i < getBinaryCacheBinCount(); i++) {
      if (binaryKeyStorage[i] != 0L) {
        loadedBinaryBins++;
      }
    }
    return (float) loadedBinaryBins / (float) getBinaryCacheBinCount();
  }

  private float computeTernaryLoadFactor() {
    int loadedTernaryBins = 0;
    for (int i = 0; i < getTernaryBinCount(); i++) {
      if (ternaryStorage[i * 2] != 0L) {
        loadedTernaryBins++;
      }
    }
    return (float) loadedTernaryBins / (float) getTernaryBinCount();
  }

  private float computeSatisfactionLoadFactor() {
    int loadedSatisfactionBins = 0;
    for (int i = 0; i < getSatisfactionBinCount(); i++) {
      if (satisfactionKeyStorage[i] != 0L) {
        loadedSatisfactionBins++;
      }
    }
    return (float) loadedSatisfactionBins / (float) getSatisfactionBinCount();
  }

  private static final class CacheAccessStatistics {
    private int hitCount = 0;
    private int hitCountSinceInvalidation = 0;
    private int missCount = 0;
    private int missCountSinceInvalidation = 0;
    private int invalidationCount = 0;
    private int putCount = 0;
    private int putCountSinceInvalidation = 0;

    @Override
    public String toString() {
      return "CacheAccessStatistics{" +
          "\n  put=" + putCount + ",hit=" + hitCount + ",miss=" + (missCount - putCount) +
          "\n  invalidation=" + invalidationCount +
          "\n  put=" + putCountSinceInvalidation + ",hit=" + hitCountSinceInvalidation + ",miss" +
          (missCountSinceInvalidation - putCountSinceInvalidation) +
          '}';
    }

    void cacheHit() {
      hitCount++;
      hitCountSinceInvalidation++;
    }

    void cacheMiss() {
      missCount++;
      missCountSinceInvalidation++;
    }

    void invalidation() {
      invalidationCount++;
      hitCountSinceInvalidation = 0;
      missCountSinceInvalidation = 0;
      putCountSinceInvalidation = 0;
    }

    void put() {
      putCount++;
      putCountSinceInvalidation++;
    }
  }
}
