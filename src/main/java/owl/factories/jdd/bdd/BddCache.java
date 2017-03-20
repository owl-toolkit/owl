package owl.factories.jdd.bdd;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.util.BitUtil;

/*
 * Possible improvements:
 *  - Not regrow every time but do partial invalidate
 */
@SuppressWarnings("PMD.UseUtilityClass")
final class BddCache {
  private static final int BINARY_CACHE_OPERATION_ID_OFFSET = 61;
  private static final int BINARY_OPERATION_AND = 0;
  private static final int BINARY_OPERATION_EQUIVALENCE = 4;
  private static final int BINARY_OPERATION_EXISTS = 6;
  private static final int BINARY_OPERATION_IMPLIES = 3;
  private static final int BINARY_OPERATION_N_AND = 1;
  private static final int BINARY_OPERATION_OR = 2;
  private static final int BINARY_OPERATION_XOR = 5;
  private static final int CACHE_VALUE_BIT_SIZE = NodeTable.NODE_IDENTIFIER_BIT_SIZE;
  private static final long CACHE_VALUE_MASK = BitUtil.maskLength(CACHE_VALUE_BIT_SIZE);
  private static final int TERNARY_CACHE_OPERATION_ID_OFFSET = 63;
  private static final int TERNARY_OPERATION_ITE = 0;
  private static final int UNARY_CACHE_KEY_OFFSET = 25;
  private static final int UNARY_CACHE_OPERATION_ID_OFFSET = 63;
  private static final int UNARY_CACHE_TYPE_LENGTH = 1;
  private static final int UNARY_OPERATION_NOT = 0;
  private static final Collection<BddCache> cacheShutdownHook = new ConcurrentLinkedDeque<>();
  private static final Logger logger = Logger.getLogger(BddCache.class.getName());
  private final BddImpl associatedBdd;
  private final CacheAccessStatistics binaryAccessStatistics = new CacheAccessStatistics();
  private final int binaryBinsPerHash;
  private final CacheAccessStatistics composeAccessStatistics = new CacheAccessStatistics();
  private final int composeBinsPerHash;
  private final CacheAccessStatistics satisfactionAccessStatistics = new CacheAccessStatistics();
  private final int satisfactionBinsPerHash;
  private final CacheAccessStatistics ternaryAccessStatistics = new CacheAccessStatistics();
  private final int ternaryBinsPerHash;
  private final CacheAccessStatistics unaryAccessStatistics = new CacheAccessStatistics();
  private final int unaryBinsPerHash;
  private final CacheAccessStatistics volatileAccessStatistics = new CacheAccessStatistics();
  private final int volatileBinsPerHash;
  @SuppressWarnings("NullableProblems")
  private long[] binaryKeyStorage;
  @SuppressWarnings("NullableProblems")
  private int[] binaryResultStorage;
  @Nullable
  private int[] composeStorage;
  private int lookupHash = -1;
  private int lookupResult = -1;
  @SuppressWarnings("NullableProblems")
  private int[] satisfactionKeyStorage;
  @SuppressWarnings("NullableProblems")
  private double[] satisfactionResultStorage;
  @SuppressWarnings("NullableProblems")
  private long[] ternaryStorage;
  @SuppressWarnings("NullableProblems")
  private long[] unaryStorage;
  @SuppressWarnings("NullableProblems")
  private int[] volatileKeyStorage;
  @SuppressWarnings("NullableProblems")
  private int[] volatileResultStorage;

  BddCache(BddImpl associatedBdd) {
    this.associatedBdd = associatedBdd;
    BddConfiguration configuration = associatedBdd.getConfiguration();
    unaryBinsPerHash = configuration.cacheUnaryBinsPerHash();
    binaryBinsPerHash = configuration.cacheBinaryBinsPerHash();
    ternaryBinsPerHash = configuration.cacheTernaryBinsPerHash();
    satisfactionBinsPerHash = configuration.cacheSatisfactionBinsPerHash();
    composeBinsPerHash = configuration.cacheComposeBinsPerHash();
    volatileBinsPerHash = configuration.cacheVolatileBinsPerHash();
    reallocateUnary();
    reallocateBinary();
    reallocateTernary();
    reallocateSatisfaction();
    reallocateCompose();
    reallocateVolatile();

    if (configuration.logStatisticsOnShutdown()) {
      logger.log(Level.FINER, "Adding {0} to shutdown hook", this);
      addToShutdownHook(this);
    }
  }

  private static void addToShutdownHook(BddCache cache) {
    ShutdownHookLazyHolder.init();
    cacheShutdownHook.add(cache);
  }

  private static long buildBinaryKeyStore(long operationId, long inputNode1,
    long inputNode2) {
    assert BitUtil.fits(inputNode1, CACHE_VALUE_BIT_SIZE) && BitUtil
      .fits(inputNode2, CACHE_VALUE_BIT_SIZE);
    long store = inputNode1;
    store |= inputNode2 << CACHE_VALUE_BIT_SIZE;
    store |= operationId << BINARY_CACHE_OPERATION_ID_OFFSET;
    return store;
  }

  private static long buildTernaryFirstStore(long operationId, long inputNode1,
    long inputNode2) {
    assert BitUtil.fits(inputNode1, CACHE_VALUE_BIT_SIZE) && BitUtil
      .fits(inputNode2, CACHE_VALUE_BIT_SIZE);
    return inputNode1 | inputNode2 << CACHE_VALUE_BIT_SIZE
      | operationId << TERNARY_CACHE_OPERATION_ID_OFFSET;
  }

  private static long buildTernarySecondStore(long inputNode3, long resultNode) {
    assert BitUtil.fits(inputNode3, CACHE_VALUE_BIT_SIZE) && BitUtil
      .fits(resultNode, CACHE_VALUE_BIT_SIZE);
    return resultNode | inputNode3 << CACHE_VALUE_BIT_SIZE;
  }

  private static long buildUnaryFullKey(long operationId, long inputNode) {
    assert BitUtil.fits(operationId, UNARY_CACHE_TYPE_LENGTH) && BitUtil
      .fits(operationId, CACHE_VALUE_BIT_SIZE);
    return (operationId << UNARY_CACHE_OPERATION_ID_OFFSET) | (inputNode << UNARY_CACHE_KEY_OFFSET);
  }

  private static long buildUnaryStore(long operationId, long inputNode,
    long resultNode) {
    assert BitUtil.fits(inputNode, CACHE_VALUE_BIT_SIZE)
      && BitUtil.fits(resultNode, CACHE_VALUE_BIT_SIZE);
    return resultNode
      | inputNode << UNARY_CACHE_KEY_OFFSET
      | operationId << UNARY_CACHE_OPERATION_ID_OFFSET;
  }

  private static long getInputNodeFromTernarySecondStore(long ternarySecondStore) {
    return ternarySecondStore >>> CACHE_VALUE_BIT_SIZE;
  }

  private static long getResultNodeFromTernarySecondStore(long ternarySecondStore) {
    return ternarySecondStore & CACHE_VALUE_MASK;
  }

  private static long getResultNodeFromUnaryStore(long unaryStore) {
    return unaryStore & CACHE_VALUE_MASK;
  }

  private static long getUnaryFullKeyFromUnaryStore(long unaryStore) {
    return unaryStore & ~BitUtil.maskLength(UNARY_CACHE_KEY_OFFSET);
  }

  private static void insertInLru(long[] array, int first, int offset,
    long newValue) {
    System.arraycopy(array, first, array, first + 1, offset - 1);
    array[first] = newValue;
  }

  private static void insertInLru(int[] array, int first, int offset,
    int newValue) {
    // Copy each element between first and last to its next position
    System.arraycopy(array, first, array, first + 1, offset - 1);
    array[first] = newValue;
  }

  private static void insertInTernaryLru(long[] array, int first, int offset,
    long newFirstValue, long newSecondValue) {
    System.arraycopy(array, first, array, first + 2, offset - 2);
    array[first] = newFirstValue;
    array[first + 1] = newSecondValue;
  }

  private static boolean isBinaryOperation(int operationId) {
    return operationId == BINARY_OPERATION_AND || operationId == BINARY_OPERATION_EQUIVALENCE
      || operationId == BINARY_OPERATION_IMPLIES || operationId == BINARY_OPERATION_N_AND
      || operationId == BINARY_OPERATION_OR || operationId == BINARY_OPERATION_XOR
      || operationId == BINARY_OPERATION_EXISTS;
  }

  private static boolean isTernaryOperation(int operationId) {
    return operationId == TERNARY_OPERATION_ITE;
  }

  private static boolean isUnaryOperation(int operationId) {
    return operationId == UNARY_OPERATION_NOT;
  }

  private static void updateLru(long[] array, int first, int offset) {
    insertInLru(array, first, offset, array[first + offset]);
  }

  private static void updateLru(int[] array, int first, int offset) {
    insertInLru(array, first, offset, array[first + offset]);
  }

  private static void updateTernaryLru(long[] array, int first, int offset) {
    insertInTernaryLru(array, first, offset, array[first + offset], array[first + offset + 1]);
  }

  private int binaryHash(long binaryKey) {
    int binaryHashSize = getBinaryCacheKeyCount();
    int hash = Util.hash(binaryKey) % binaryHashSize;
    if (hash < 0) {
      return hash + binaryHashSize;
    }
    return hash;
  }

  private boolean binaryLookup(int operationId, int inputNode1, int inputNode2) {
    assert associatedBdd.isNodeValid(inputNode1) && associatedBdd.isNodeValid(inputNode2);
    assert isBinaryOperation(operationId);
    long binaryKey = buildBinaryKeyStore((long) operationId, (long) inputNode1,
      (long) inputNode2);
    lookupHash = binaryHash(binaryKey);
    int cachePosition = getBinaryCachePosition(lookupHash);

    if (binaryBinsPerHash == 1) {
      if (binaryKey != binaryKeyStorage[cachePosition]) {
        return false;
      }
      lookupResult = binaryResultStorage[cachePosition];
    } else {
      int offset = -1;
      for (int i = 0; i < binaryBinsPerHash; i++) {
        long binaryKeyStore = binaryKeyStorage[cachePosition + i];
        if (binaryKey == binaryKeyStore) {
          offset = i;
          break;
        }
      }
      if (offset == -1) {
        return false;
      }
      lookupResult = binaryResultStorage[cachePosition + offset];
      if (offset != 0) {
        updateLru(binaryKeyStorage, cachePosition, offset);
        updateLru(binaryResultStorage, cachePosition, offset);
      }
    }
    assert associatedBdd.isNodeValidOrRoot(lookupResult);
    binaryAccessStatistics.cacheHit();
    return true;
  }

  private void binaryPut(int operationId, int hash, int inputNode1,
    int inputNode2, int resultNode) {
    assert isBinaryOperation(operationId) && associatedBdd.isNodeValid(inputNode1) && associatedBdd
      .isNodeValid(inputNode2) && associatedBdd.isNodeValidOrRoot(resultNode);
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

  void clearVolatileCache() {
    volatileAccessStatistics.invalidation();
    Arrays.fill(volatileKeyStorage, 0);
  }

  private int composeHash(int inputNode, int[] replacementArray) {
    int composeHashSize = getComposeKeyCount();
    int hash = Util.hash(inputNode, replacementArray) % composeHashSize;
    if (hash < 0) {
      return hash + composeHashSize;
    }
    return hash;
  }

  private float computeBinaryLoadFactor() {
    int loadedBinaryBins = 0;
    for (int i = 0; i < getBinaryCacheKeyCount(); i++) {
      if (binaryKeyStorage[i] != 0L) {
        loadedBinaryBins++;
      }
    }
    return (float) loadedBinaryBins / (float) getBinaryCacheKeyCount();
  }

  private float computeSatisfactionLoadFactor() {
    int loadedSatisfactionBins = 0;
    for (int i = 0; i < getSatisfactionKeyCount(); i++) {
      if (satisfactionKeyStorage[i] != 0L) {
        loadedSatisfactionBins++;
      }
    }
    return (float) loadedSatisfactionBins / (float) getSatisfactionKeyCount();
  }

  private float computeTernaryLoadFactor() {
    int loadedTernaryBins = 0;
    for (int i = 0; i < getTernaryKeyCount(); i++) {
      if (ternaryStorage[i * 2] != 0L) {
        loadedTernaryBins++;
      }
    }
    return (float) loadedTernaryBins / (float) getTernaryKeyCount();
  }

  private float computeUnaryLoadFactor() {
    int loadedUnaryBins = 0;
    for (int i = 0; i < getUnaryCacheKeyCount(); i++) {
      if (unaryStorage[i] != 0L) {
        loadedUnaryBins++;
      }
    }
    return (float) loadedUnaryBins / (float) getUnaryCacheKeyCount();
  }

  private float computeVolatileLoadFactor() {
    int loadedVolatileBins = 0;
    for (int i = 0; i < getVolatileKeyCount(); i++) {
      if (volatileKeyStorage[i] != 0) {
        loadedVolatileBins++;
      }
    }
    return (float) loadedVolatileBins / (float) getVolatileKeyCount();
  }

  private int ensureMinimumCacheKeyCount(int cacheSize) {
    if (cacheSize < associatedBdd.getConfiguration().minimumNodeTableSize()) {
      return Util.nextPrime(associatedBdd.getConfiguration().minimumNodeTableSize());
    }
    return Util.nextPrime(cacheSize);
  }

  private int getBinaryCacheKeyCount() {
    return binaryKeyStorage.length / binaryBinsPerHash;
  }

  private int getBinaryCachePosition(int hash) {
    return hash * binaryBinsPerHash;
  }

  private int getComposeCachePosition(int hash) {
    return hash * (2 + associatedBdd.numberOfVariables() + composeBinsPerHash);
  }

  private int getComposeKeyCount() {
    assert composeStorage != null;
    return composeStorage.length / (2 + composeBinsPerHash + associatedBdd.numberOfVariables());
  }

  int getLookupHash() {
    return lookupHash;
  }

  int getLookupResult() {
    return lookupResult;
  }

  private int getSatisfactionCachePosition(int hash) {
    return hash * satisfactionBinsPerHash;
  }

  private int getSatisfactionKeyCount() {
    return satisfactionKeyStorage.length / satisfactionBinsPerHash;
  }

  String getStatistics() {
    StringBuilder builder = new StringBuilder(512);
    builder.append("Unary: size: ").append(getUnaryCacheKeyCount()) //
      .append(", load: ").append(computeUnaryLoadFactor()) //
      .append("\n ").append(unaryAccessStatistics) //
      .append("\nBinary: size: ").append(getBinaryCacheKeyCount()) //
      .append(", load: ").append(computeBinaryLoadFactor()) //
      .append("\n ").append(binaryAccessStatistics) //
      .append("\nTernary: size: ").append(getTernaryKeyCount()) //
      .append(", load: ").append(computeTernaryLoadFactor()) //
      .append("\n ").append(ternaryAccessStatistics) //
      .append("\nSatisfaction: size: ").append(getSatisfactionKeyCount()) //
      .append(", load: ").append(computeSatisfactionLoadFactor()) //
      .append("\n ").append(satisfactionAccessStatistics) //
      .append("\nCompose:");
    //noinspection VariableNotUsedInsideIf
    if (composeStorage == null) {
      builder.append(" Disabled");
    } else {
      builder.append(" size: ").append(getComposeKeyCount())
        .append("\n ").append(composeAccessStatistics);
    }
    builder.append("\nCompose volatile: current size: ").append(getVolatileKeyCount()) //
      .append(", load: ").append(computeVolatileLoadFactor()) //
      .append("\n ").append(volatileAccessStatistics);
    return builder.toString();
  }

  private int getTernaryCachePosition(int hash) {
    return hash * ternaryBinsPerHash * 2;
  }

  private int getTernaryKeyCount() {
    return ternaryStorage.length / ternaryBinsPerHash / 2;
  }

  private int getUnaryCacheKeyCount() {
    return unaryStorage.length / unaryBinsPerHash;
  }

  private int getUnaryCachePosition(int hash) {
    return hash * unaryBinsPerHash;
  }

  private int getVolatileCachePosition(int hash) {
    return hash * volatileBinsPerHash;
  }

  private int getVolatileKeyCount() {
    return volatileKeyStorage.length / volatileBinsPerHash;
  }

  private int hashSatisfaction(int node) {
    int satisfactionHashSize = getSatisfactionKeyCount();
    int hash = Util.hash(node) % satisfactionHashSize;
    if (hash < 0) {
      return hash + satisfactionHashSize;
    }
    return hash;
  }

  void invalidate() {
    invalidateUnary();
    invalidateBinary();
    invalidateTernary();
    invalidateSatisfaction();
    invalidateCompose();
    clearVolatileCache();
  }

  @SuppressWarnings("WeakerAccess")
  void invalidateBinary() {
    binaryAccessStatistics.invalidation();
    reallocateBinary();
  }

  void invalidateCompose() {
    composeAccessStatistics.invalidation();
    reallocateCompose();
  }

  void invalidateSatisfaction() {
    satisfactionAccessStatistics.invalidation();
    reallocateSatisfaction();
  }

  @SuppressWarnings("WeakerAccess")
  void invalidateTernary() {
    ternaryAccessStatistics.invalidation();
    reallocateTernary();
  }

  @SuppressWarnings("WeakerAccess")
  void invalidateUnary() {
    unaryAccessStatistics.invalidation();
    reallocateUnary();
  }

  boolean lookupAnd(int inputNode1, int inputNode2) {
    return binaryLookup(BINARY_OPERATION_AND, inputNode1, inputNode2);
  }

  boolean lookupCompose(int inputNode, int[] replacementArray) {
    assert composeStorage != null;
    assert associatedBdd.isNodeValid(inputNode);
    int hash = composeHash(inputNode, replacementArray);
    int cachePosition = getComposeCachePosition(hash);

    lookupHash = hash;

    if (composeStorage[cachePosition] != inputNode) {
      return false;
    }
    for (int i = 0; i < replacementArray.length; i++) {
      if (composeStorage[cachePosition + 2 + i] != replacementArray[i]) {
        return false;
      }
    }
    if (replacementArray.length < associatedBdd.numberOfVariables()
      && composeStorage[cachePosition + 2 + replacementArray.length] != -1) {
      return false;
    }
    lookupResult = composeStorage[cachePosition + 1];
    assert associatedBdd.isNodeValidOrRoot(lookupResult);
    composeAccessStatistics.cacheHit();
    return true;
  }

  boolean lookupEquivalence(int inputNode1, int inputNode2) {
    return binaryLookup(BINARY_OPERATION_EQUIVALENCE, inputNode1, inputNode2);
  }

  boolean lookupExists(int inputNode, int variableCube) {
    return binaryLookup(BINARY_OPERATION_EXISTS, inputNode, variableCube);
  }

  boolean lookupIfThenElse(int inputNode1, int inputNode2, int inputNode3) {
    return ternaryLookup(TERNARY_OPERATION_ITE, inputNode1, inputNode2, inputNode3);
  }

  boolean lookupImplication(int inputNode1, int inputNode2) {
    return binaryLookup(BINARY_OPERATION_IMPLIES, inputNode1, inputNode2);
  }

  boolean lookupNAnd(int inputNode1, int inputNode2) {
    return binaryLookup(BINARY_OPERATION_N_AND, inputNode1, inputNode2);
  }

  boolean lookupNot(int node) {
    return unaryLookup(UNARY_OPERATION_NOT, node);
  }

  boolean lookupOr(int inputNode1, int inputNode2) {
    return binaryLookup(BINARY_OPERATION_OR, inputNode1, inputNode2);
  }

  double lookupSatisfaction(int node) {
    assert associatedBdd.isNodeValid(node);
    int hash = hashSatisfaction(node);
    int cachePosition = getSatisfactionCachePosition(hash);

    lookupHash = hash;
    if (node == satisfactionKeyStorage[cachePosition]) {
      satisfactionAccessStatistics.cacheHit();
      return satisfactionResultStorage[cachePosition];
    }
    return -1.0d;
  }

  boolean lookupVolatile(int inputNode) {
    assert associatedBdd.isNodeValid(inputNode);

    lookupHash = volatileHash(inputNode);
    int cachePosition = getVolatileCachePosition(lookupHash);

    if (volatileBinsPerHash == 1) {
      if (volatileKeyStorage[cachePosition] != inputNode) {
        return false;
      }
      lookupResult = volatileResultStorage[cachePosition];
    } else {
      int offset = -1;
      for (int i = 0; i < volatileBinsPerHash; i++) {
        int keyValue = volatileKeyStorage[cachePosition + i];
        if (keyValue == 0) {
          return false;
        }
        if (keyValue == inputNode) {
          offset = i;
          break;
        }
      }
      if (offset == -1) {
        return false;
      }
      lookupResult = volatileResultStorage[cachePosition + offset];
      if (offset != 0) {
        updateLru(volatileKeyStorage, cachePosition, offset);
        updateLru(volatileResultStorage, cachePosition, offset);
      }
    }
    assert associatedBdd.isNodeValidOrRoot(lookupResult);
    volatileAccessStatistics.cacheHit();
    return true;
  }

  boolean lookupXor(int inputNode1, int inputNode2) {
    return binaryLookup(BINARY_OPERATION_XOR, inputNode1, inputNode2);
  }

  void putAnd(int hash, int inputNode1, int inputNode2, int resultNode) {
    binaryPut(BINARY_OPERATION_AND, hash, inputNode1, inputNode2, resultNode);
  }

  void putCompose(int hash, int inputNode, int[] replacement,
    int resultNode) {
    assert composeStorage != null;
    assert associatedBdd.isNodeValidOrRoot(inputNode)
      && associatedBdd.isNodeValidOrRoot(resultNode);
    assert replacement.length <= associatedBdd.numberOfVariables();

    int cachePosition = getComposeCachePosition(hash);
    composeAccessStatistics.put();

    composeStorage[cachePosition] = inputNode;
    composeStorage[cachePosition + 1] = resultNode;
    System.arraycopy(replacement, 0, composeStorage, cachePosition + 2, replacement.length);
    if (replacement.length < associatedBdd.numberOfVariables()) {
      composeStorage[cachePosition + 2 + replacement.length] = -1;
    }
  }

  void putEquivalence(int hash, int inputNode1, int inputNode2,
    int resultNode) {
    binaryPut(BINARY_OPERATION_EQUIVALENCE, hash, inputNode1, inputNode2, resultNode);
  }

  void putExists(int hash, int inputNode, int variableCube,
    int resultNode) {
    binaryPut(BINARY_OPERATION_EXISTS, hash, inputNode, variableCube, resultNode);
  }

  void putIfThenElse(int hash, int inputNode1, int inputNode2,
    int inputNode3, int resultNode) {
    ternaryPut(TERNARY_OPERATION_ITE, hash, inputNode1, inputNode2, inputNode3, resultNode);
  }

  void putImplication(int hash, int inputNode1, int inputNode2,
    int resultNode) {
    binaryPut(BINARY_OPERATION_IMPLIES, hash, inputNode1, inputNode2, resultNode);
  }

  void putNAnd(int hash, int inputNode1, int inputNode2, int resultNode) {
    binaryPut(BINARY_OPERATION_N_AND, hash, inputNode1, inputNode2, resultNode);
  }

  void putNot(int inputNode, int resultNode) {
    unaryPut(UNARY_OPERATION_NOT, unaryHash(buildUnaryFullKey((long) UNARY_OPERATION_NOT,
      (long) inputNode)), inputNode, resultNode);
  }

  void putNot(int hash, int inputNode, int resultNode) {
    assert associatedBdd.isNodeValid(inputNode) && associatedBdd.isNodeValidOrRoot(resultNode);
    unaryPut(UNARY_OPERATION_NOT, hash, inputNode, resultNode);
  }

  void putOr(int hash, int inputNode1, int inputNode2, int resultNode) {
    binaryPut(BINARY_OPERATION_OR, hash, inputNode1, inputNode2, resultNode);
  }

  void putSatisfaction(int hash, int node, double satisfactionCount) {
    assert associatedBdd.isNodeValid(node);
    int cachePosition = getSatisfactionCachePosition(hash);
    satisfactionKeyStorage[cachePosition] = node;
    satisfactionResultStorage[cachePosition] = satisfactionCount;
    satisfactionAccessStatistics.put();
  }

  void putVolatile(int hash, int inputNode, int resultNode) {
    assert associatedBdd.isNodeValid(inputNode)
      && associatedBdd.isNodeValidOrRoot(resultNode);

    int cachePosition = getVolatileCachePosition(hash);

    volatileAccessStatistics.put();
    if (volatileBinsPerHash == 1) {
      volatileKeyStorage[cachePosition] = inputNode;
      volatileResultStorage[cachePosition] = resultNode;
    } else {
      insertInLru(volatileKeyStorage, cachePosition, volatileBinsPerHash, inputNode);
      insertInLru(volatileResultStorage, cachePosition, volatileBinsPerHash, resultNode);
    }
  }

  void putXor(int hash, int inputNode1, int inputNode2, int resultNode) {
    binaryPut(BINARY_OPERATION_XOR, hash, inputNode1, inputNode2, resultNode);
  }

  private void reallocateBinary() {
    int keyCount = associatedBdd.getTableSize()
      / associatedBdd.getConfiguration().cacheBinaryDivider();
    int actualSize = ensureMinimumCacheKeyCount(keyCount) * binaryBinsPerHash;
    binaryKeyStorage = new long[actualSize];
    binaryResultStorage = new int[actualSize];
  }

  private void reallocateCompose() {
    if (!associatedBdd.getConfiguration().useGlobalComposeCache()) {
      composeStorage = null;
      return;
    }
    int keyCount = associatedBdd.getTableSize()
      / associatedBdd.getConfiguration().cacheComposeDivider();
    int actualSize = ensureMinimumCacheKeyCount(keyCount)
      * (2 + associatedBdd.numberOfVariables());
    composeStorage = new int[actualSize];
  }

  private void reallocateSatisfaction() {
    int keyCount = associatedBdd.getTableSize()
      / associatedBdd.getConfiguration().cacheSatisfactionDivider();
    int actualSize = ensureMinimumCacheKeyCount(keyCount) * satisfactionBinsPerHash;
    satisfactionKeyStorage = new int[actualSize];
    satisfactionResultStorage = new double[actualSize];
  }

  private void reallocateTernary() {
    int keyCount = associatedBdd.getTableSize()
      / associatedBdd.getConfiguration().cacheTernaryDivider();
    int actualSize = ensureMinimumCacheKeyCount(keyCount) * ternaryBinsPerHash * 2;
    ternaryStorage = new long[actualSize];
  }

  private void reallocateUnary() {
    int keyCount = associatedBdd.getTableSize()
      / associatedBdd.getConfiguration().cacheUnaryDivider();
    int actualSize = ensureMinimumCacheKeyCount(keyCount) * unaryBinsPerHash;
    unaryStorage = new long[actualSize];
  }

  void reallocateVolatile() {
    int keyCount = associatedBdd.numberOfVariables()
      * associatedBdd.getConfiguration().cacheVolatileMultiplier();

    volatileAccessStatistics.invalidation();
    int actualSize = Util.nextPrime(keyCount) * volatileBinsPerHash;
    volatileKeyStorage = new int[actualSize];
    volatileResultStorage = new int[actualSize];
  }

  private int ternaryHash(long ternaryFirstStore, int inputNode3) {
    int ternaryHashSize = getTernaryKeyCount();
    int hash = Util.hash(ternaryFirstStore, inputNode3) % ternaryHashSize;
    if (hash < 0) {
      return hash + ternaryHashSize;
    }
    return hash;
  }

  private boolean ternaryLookup(int operationId, int inputNode1, int inputNode2,
    int inputNode3) {
    assert isTernaryOperation(operationId) && associatedBdd.isNodeValid(inputNode1) && associatedBdd
      .isNodeValid(inputNode2) && associatedBdd.isNodeValid(inputNode3);
    assert isTernaryOperation(operationId);

    long constructedTernaryFirstStore = buildTernaryFirstStore((long) operationId,
      (long) inputNode1, (long) inputNode2);
    lookupHash = ternaryHash(constructedTernaryFirstStore, inputNode3);
    int cachePosition = getTernaryCachePosition(lookupHash);

    if (ternaryBinsPerHash == 1) {
      if (constructedTernaryFirstStore != ternaryStorage[cachePosition]) {
        return false;
      }
      long ternarySecondStore = ternaryStorage[cachePosition + 1];
      //noinspection NumericCastThatLosesPrecision
      if (inputNode3 != (int) getInputNodeFromTernarySecondStore(ternarySecondStore)) {
        return false;
      }
      //noinspection NumericCastThatLosesPrecision
      lookupResult = (int) getResultNodeFromTernarySecondStore(ternarySecondStore);
    } else {
      int offset = -1;
      for (int i = 0; i < ternaryBinsPerHash * 2; i += 2) {
        if (constructedTernaryFirstStore == ternaryStorage[cachePosition + i]) {
          long ternarySecondStore = ternaryStorage[cachePosition + i + 1];
          //noinspection NumericCastThatLosesPrecision
          if (inputNode3 == (int) getInputNodeFromTernarySecondStore(ternarySecondStore)) {
            offset = i;
            //noinspection NumericCastThatLosesPrecision
            lookupResult = (int) getResultNodeFromTernarySecondStore(ternarySecondStore);
            break;
          }
        }
      }
      if (offset == -1) {
        return false;
      }
      if (offset != 0) {
        updateTernaryLru(ternaryStorage, cachePosition, offset);
      }
    }

    assert associatedBdd.isNodeValidOrRoot(lookupResult);
    ternaryAccessStatistics.cacheHit();
    return true;
  }

  private void ternaryPut(int operationId, int hash, int inputNode1,
    int inputNode2, int inputNode3, int resultNode) {
    assert associatedBdd.isNodeValid(inputNode1) && associatedBdd.isNodeValid(inputNode2)
      && associatedBdd.isNodeValid(inputNode3) && associatedBdd.isNodeValidOrRoot(resultNode);
    assert isTernaryOperation(operationId);
    int cachePosition = getTernaryCachePosition(hash);
    ternaryAccessStatistics.put();

    long firstStore = buildTernaryFirstStore((long) operationId, (long) inputNode1,
      (long) inputNode2);
    long secondStore = buildTernarySecondStore((long) inputNode3, (long) resultNode);
    if (ternaryBinsPerHash == 1) {
      ternaryStorage[cachePosition] = firstStore;
      ternaryStorage[cachePosition + 1] = secondStore;
    } else {
      insertInTernaryLru(ternaryStorage, cachePosition, ternaryBinsPerHash * 2, firstStore,
        secondStore);
    }
  }

  private int unaryHash(long unaryKey) {
    int unaryHashSize = getUnaryCacheKeyCount();
    int hash = Util.hash(unaryKey) % unaryHashSize;
    if (hash < 0) {
      return hash + unaryHashSize;
    }
    return hash;
  }

  private boolean unaryLookup(int operationId, int inputNode) {
    assert associatedBdd.isNodeValid(inputNode);
    assert isUnaryOperation(operationId);
    long unaryFullKey = buildUnaryFullKey((long) operationId, (long) inputNode);
    lookupHash = unaryHash(unaryFullKey);
    int cachePosition = getUnaryCachePosition(lookupHash);

    if (unaryBinsPerHash == 1) {
      long unaryStore = unaryStorage[cachePosition];
      long unaryStoreFullKey = getUnaryFullKeyFromUnaryStore(unaryStore);
      if (unaryFullKey != unaryStoreFullKey) {
        return false;
      }
      //noinspection NumericCastThatLosesPrecision
      lookupResult = (int) getResultNodeFromUnaryStore(unaryStore);
    } else {
      int offset = -1;
      for (int i = 0; i < unaryBinsPerHash; i++) {
        long unaryStore = unaryStorage[cachePosition + i];
        long unaryStoreFullKey = getUnaryFullKeyFromUnaryStore(unaryStore);
        if (unaryFullKey == unaryStoreFullKey) {
          offset = i;
          //noinspection NumericCastThatLosesPrecision
          lookupResult = (int) getResultNodeFromUnaryStore(unaryStore);
          break;
        }
      }
      if (offset == -1) {
        return false;
      }
      if (offset != 0) {
        updateLru(unaryStorage, cachePosition, offset);
      }
    }

    assert associatedBdd.isNodeValidOrRoot(lookupResult);
    unaryAccessStatistics.cacheHit();
    return true;
  }

  private void unaryPut(int operationId, int hash, int inputNode,
    int resultNode) {
    assert associatedBdd.isNodeValid(inputNode) && associatedBdd.isNodeValidOrRoot(resultNode);
    int cachePosition = getUnaryCachePosition(hash);
    unaryAccessStatistics.put();

    long unaryStore = buildUnaryStore((long) operationId, (long) inputNode,
      (long) resultNode);
    if (unaryBinsPerHash == 1) {
      unaryStorage[cachePosition] = unaryStore;
    } else {
      insertInLru(unaryStorage, cachePosition, unaryBinsPerHash, unaryStore);
    }
  }

  private int volatileHash(int inputNode) {
    int volatileHashSize = getVolatileKeyCount();
    int hash = Util.hash(inputNode) % volatileHashSize;
    if (hash < 0) {
      return hash + volatileHashSize;
    }
    return hash;
  }

  private static final class CacheAccessStatistics {
    private int hitCount = 0;
    private int hitCountSinceInvalidation = 0;
    private int invalidationCount = 0;
    private int putCount = 0;
    private int putCountSinceInvalidation = 0;

    void cacheHit() {
      hitCount++;
      hitCountSinceInvalidation++;
    }

    void invalidation() {
      invalidationCount++;
      hitCountSinceInvalidation = 0;
      putCountSinceInvalidation = 0;
    }

    void put() {
      putCount++;
      putCountSinceInvalidation++;
    }

    @Override
    public String toString() {
      float hitToPutRatio = (float) hitCount / (float) Math.max(putCount, 1);
      return String.format("Cache access: put=%d, hit=%d, hit-to-put=%3.3f%n"
          + "       invalidation: %d times, since last: put=%d, hit=%d",
        putCount, hitCount, hitToPutRatio, invalidationCount,
        putCountSinceInvalidation, hitCountSinceInvalidation);
    }
  }

  private static final class ShutdownHookLazyHolder {
    @Nullable
    private static final Runnable shutdownHook = new ShutdownHookPrinter();

    static {
      Runtime.getRuntime().addShutdownHook(new Thread(shutdownHook));
    }

    static void init() {
      // bogus method to force static initialization
    }
  }

  private static final class ShutdownHookPrinter implements Runnable {
    @Override
    public void run() {
      Logger logger = Logger.getLogger(BddCache.class.getName() + ".Statistics");
      if (!logger.isLoggable(Level.FINE)) {
        return;
      }
      for (BddCache cache : cacheShutdownHook) {
        logger.log(Level.FINE, cache.getStatistics());
      }
    }
  }
}
