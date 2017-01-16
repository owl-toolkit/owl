package owl.bdd;

import com.google.common.math.IntMath;

final class Util {
  private static final int BYTE_MASK = BitUtil.intMaskLength(Byte.SIZE);
  private static final int FNV_OFFSET = 0x811c9dc5;
  private static final int FNV_PRIME = 0x1000193;

  private Util() {
  }

  @SuppressWarnings("MagicNumber")
  private static int fnv1aRound(final int hash, final int number) {
    return (hash ^ ((number >>> 24) & BYTE_MASK)
      ^ ((number >>> 16) & BYTE_MASK)
      ^ ((number >>> 8) & BYTE_MASK)
      ^ (number & BYTE_MASK)) * FNV_PRIME;
  }

  private static int fnv1aRound(final int hash, final long number) {
    return fnv1aRound(fnv1aRound(hash, (int) (number >>> Integer.SIZE)), (int) number);
  }

  public static int hash(final long firstKey, final int secondKey) {
    return fnv1aRound(fnv1aRound(FNV_OFFSET, firstKey), secondKey);
  }

  public static int hash(final long firstKey, final long secondKey) {
    return fnv1aRound(fnv1aRound(FNV_OFFSET, firstKey), secondKey);
  }

  public static int hash(final int key) {
    return fnv1aRound(FNV_OFFSET, key);
  }

  public static int hash(final long key) {
    return fnv1aRound(FNV_OFFSET, key);
  }

  @SuppressWarnings("PMD.UseVarargs")
  public static int hash(final int key, final int[] keys) {
    int hash = fnv1aRound(FNV_OFFSET, key);
    for (final int arrayKey : keys) {
      hash = fnv1aRound(hash, arrayKey);
    }
    return hash;
  }

  public static int nextPrime(final int prime) {
    int nextPrime = Math.max(3, prime | 1);

    while (!IntMath.isPrime(nextPrime)) {
      nextPrime += 2;
    }

    return nextPrime;
  }
}
