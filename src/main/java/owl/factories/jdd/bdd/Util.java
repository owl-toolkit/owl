package owl.factories.jdd.bdd;

import com.google.common.math.IntMath;
import owl.util.BitUtil;

final class Util {
  private static final int BYTE_MASK = BitUtil.intMaskLength(Byte.SIZE);
  private static final int FNV_OFFSET = 0x811c9dc5;
  private static final int FNV_PRIME = 0x1000193;

  private Util() {
  }

  @SuppressWarnings("MagicNumber")
  private static int fnv1aRound(int hash, int number) {
    return (hash ^ ((number >>> 24) & BYTE_MASK)
      ^ ((number >>> 16) & BYTE_MASK)
      ^ ((number >>> 8) & BYTE_MASK)
      ^ (number & BYTE_MASK)) * FNV_PRIME;
  }

  private static int fnv1aRound(int hash, long number) {
    //noinspection NumericCastThatLosesPrecision
    return fnv1aRound(fnv1aRound(hash, (int) (number >>> Integer.SIZE)), (int) number);
  }

  public static int hash(long firstKey, int secondKey) {
    return fnv1aRound(fnv1aRound(FNV_OFFSET, firstKey), secondKey);
  }

  public static int hash(long firstKey, long secondKey) {
    return fnv1aRound(fnv1aRound(FNV_OFFSET, firstKey), secondKey);
  }

  public static int hash(int key) {
    return fnv1aRound(FNV_OFFSET, key);
  }

  public static int hash(long key) {
    return fnv1aRound(FNV_OFFSET, key);
  }

  public static int hash(int key, int[] keys) {
    int hash = fnv1aRound(FNV_OFFSET, key);
    for (int arrayKey : keys) {
      hash = fnv1aRound(hash, arrayKey);
    }
    return hash;
  }

  public static int nextPrime(int prime) {
    int nextPrime = Math.max(3, prime | 1);

    while (!IntMath.isPrime(nextPrime)) {
      nextPrime += 2;
    }

    return nextPrime;
  }
}
