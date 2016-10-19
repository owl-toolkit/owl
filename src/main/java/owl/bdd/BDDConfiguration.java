package owl.bdd;

import org.immutables.value.Value;

@Value.Immutable
class BDDConfiguration {
  private static final int DEFAULT_NODE_TABLE_GC_DEAD_NODE_COUNT = 2000;
  private static final int DEFAULT_MINIMUM_NODE_TABLE_SIZE = 100;
  private static final int DEFAULT_MINIMUM_CACHE_SIZE = 32;
  private static final int DEFAULT_NODE_TABLE_MINIMUM_FREE_NODE_COUNT = 1000;
  private static final float DEFAULT_NODE_TABLE_FREE_NODE_PERCENTAGE = 0.05f;
  private static final int DEFAULT_NODE_TABLE_MINIMUM_GROWTH = 5000;
  private static final int DEFAULT_NODE_TABLE_MAXIMUM_GROWTH = 50000;
  private static final int DEFAULT_NODE_TABLE_IS_SMALL_THRESHOLD = 2000;
  private static final int DEFAULT_NODE_TABLE_IS_BIG_THRESHOLD = 40000;
  private static final int DEFAULT_CACHE_UNARY_DIVIDER = 16;
  private static final int DEFAULT_CACHE_BINARY_DIVIDER = 2;
  private static final int DEFAULT_CACHE_TERNARY_DIVIDER = 8;
  private static final int DEFAULT_CACHE_SATISFACTION_DIVIDER = 16;
  private static final int DEFAULT_CACHE_UNARY_BINS_PER_HASH = 4;
  private static final int DEFAULT_CACHE_BINARY_BINS_PER_HASH = 4;
  private static final int DEFAULT_CACHE_TERNARY_BINS_PER_HASH = 4;
  private static final int DEFAULT_CACHE_SATISFACTION_BINS_PER_HASH = 1;

  @Value.Default
  public int minimumNodeTableSize() {
    return DEFAULT_MINIMUM_NODE_TABLE_SIZE;
  }

  @Value.Default
  public int minimumCacheSize() {
    return DEFAULT_MINIMUM_CACHE_SIZE;
  }

  @Value.Default
  public int minimumDeadNodesCountForGCInGrow() {
    return DEFAULT_NODE_TABLE_GC_DEAD_NODE_COUNT;
  }

  @Value.Default
  public int minimumFreeNodeCountAfterGC() {
    return DEFAULT_NODE_TABLE_MINIMUM_FREE_NODE_COUNT;
  }

  @Value.Default
  public int minimumNodeTableGrowth() {
    return DEFAULT_NODE_TABLE_MINIMUM_GROWTH;
  }

  @Value.Default
  public int maximumNodeTableGrowth() {
    return DEFAULT_NODE_TABLE_MAXIMUM_GROWTH;
  }

  @Value.Default
  public int nodeTableSmallThreshold() {
    return DEFAULT_NODE_TABLE_IS_SMALL_THRESHOLD;
  }

  @Value.Default
  public int nodeTableBigThreshold() {
    return DEFAULT_NODE_TABLE_IS_BIG_THRESHOLD;
  }

  @Value.Default
  public int cacheUnaryDivider() {
    return DEFAULT_CACHE_UNARY_DIVIDER;
  }

  @Value.Default
  public int cacheBinaryDivider() {
    return DEFAULT_CACHE_BINARY_DIVIDER;
  }

  @Value.Default
  public int cacheTernaryDivider() {
    return DEFAULT_CACHE_TERNARY_DIVIDER;
  }

  @Value.Default
  public int cacheSatisfactionDivider() {
    return DEFAULT_CACHE_SATISFACTION_DIVIDER;
  }

  @Value.Default
  public int cacheUnaryBinsPerHash() {
    return DEFAULT_CACHE_UNARY_BINS_PER_HASH;
  }

  @Value.Default
  public int cacheBinaryBinsPerHash() {
    return DEFAULT_CACHE_BINARY_BINS_PER_HASH;
  }

  @Value.Default
  public int cacheTernaryBinsPerHash() {
    return DEFAULT_CACHE_TERNARY_BINS_PER_HASH;
  }

  @Value.Default
  public int cacheSatisfactionBinsPerHash() {
    return DEFAULT_CACHE_SATISFACTION_BINS_PER_HASH;
  }

  @Value.Default
  public float minimumFreeNodePercentageAfterGC() {
    return DEFAULT_NODE_TABLE_FREE_NODE_PERCENTAGE;
  }


}
