package owl.bdd;

import org.immutables.value.Value;

@SuppressWarnings("PMD.AtLeastOneConstructor")
@Value.Immutable
class BddConfiguration {
  private static final int DEFAULT_CACHE_BINARY_BINS_PER_HASH = 3;
  private static final int DEFAULT_CACHE_BINARY_DIVIDER = 32;
  private static final int DEFAULT_CACHE_COMPOSE_DIVIDER = 128;
  private static final int DEFAULT_CACHE_SATISFACTION_BINS_PER_HASH = 1;
  private static final int DEFAULT_CACHE_SATISFACTION_DIVIDER = 32;
  private static final int DEFAULT_CACHE_TERNARY_BINS_PER_HASH = 3;
  private static final int DEFAULT_CACHE_TERNARY_DIVIDER = 64;
  private static final int DEFAULT_CACHE_UNARY_BINS_PER_HASH = 3;
  private static final int DEFAULT_CACHE_UNARY_DIVIDER = 32;
  private static final int DEFAULT_CACHE_VOLATILE_BINS_PER_HASH = 2;
  private static final int DEFAULT_CACHE_VOLATILE_MULTIPLIER = 2;
  private static final int DEFAULT_MINIMUM_CACHE_SIZE = 32;
  private static final int DEFAULT_MINIMUM_NODE_TABLE_SIZE = 100;
  private static final float DEFAULT_NODE_TABLE_FREE_NODE_PERCENTAGE = 0.05f;
  private static final int DEFAULT_NODE_TABLE_GC_DEAD_NODE_COUNT = 2000;
  private static final int DEFAULT_NODE_TABLE_IS_BIG_THRESHOLD = 40000;
  private static final int DEFAULT_NODE_TABLE_IS_SMALL_THRESHOLD = 2000;
  private static final int DEFAULT_NODE_TABLE_MAXIMUM_GROWTH = 50000;
  private static final int DEFAULT_NODE_TABLE_MINIMUM_FREE_NODE_COUNT = 1000;
  private static final int DEFAULT_NODE_TABLE_MINIMUM_GROWTH = 5000;

  @Value.Default
  public int minimumNodeTableSize() {
    return DEFAULT_MINIMUM_NODE_TABLE_SIZE;
  }

  @Value.Default
  public int minimumCacheSize() {
    return DEFAULT_MINIMUM_CACHE_SIZE;
  }

  @Value.Default
  public int minimumDeadNodesCountForGcInGrow() {
    return DEFAULT_NODE_TABLE_GC_DEAD_NODE_COUNT;
  }

  @Value.Default
  public int minimumFreeNodeCountAfterGc() {
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
  public float minimumFreeNodePercentageAfterGc() {
    return DEFAULT_NODE_TABLE_FREE_NODE_PERCENTAGE;
  }

  @Value.Default
  public int cacheVolatileMultiplier() {
    return DEFAULT_CACHE_VOLATILE_MULTIPLIER;
  }

  @Value.Default
  public int cacheComposeDivider() {
    return DEFAULT_CACHE_COMPOSE_DIVIDER;
  }

  public int cacheComposeBinsPerHash() {
    // Currently only 1 supported
    return 1;
  }

  @Value.Default
  public int cacheVolatileBinsPerHash() {
    return DEFAULT_CACHE_VOLATILE_BINS_PER_HASH;
  }

  @Value.Default
  public boolean useGlobalComposeCache() {
    return false;
  }

  @Value.Default
  public boolean useShannonExists() {
    return true;
  }
}
