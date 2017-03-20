package owl.util;

import java.util.BitSet;
import javax.annotation.Nonnegative;

public final class BitUtil {
  private BitUtil() {
  }

  public static boolean areAllSet(long store, BitSet pos) {
    if (pos.length() >= Long.SIZE) {
      return false;
    }

    for (int i = pos.nextSetBit(0); i >= 0; i = pos.nextSetBit(i + 1)) {
      if (!isSet(store, i)) {
        return false;
      }
    }
    return true;
  }

  public static long clear(long store, @Nonnegative int length,
    @Nonnegative int at) {
    return store & ~(maskLength(length, at));
  }

  public static long clear(long store, @Nonnegative int length) {
    return store & ~(maskLength(length));
  }

  public static boolean fits(int value, @Nonnegative int bitSize) {
    return fits((long) value, bitSize);
  }

  public static boolean fits(long value, @Nonnegative int bitSize) {
    return value <= maskLength(bitSize);
  }

  public static long get(long store, @Nonnegative int length) {
    return store & maskLength(length);
  }

  public static long get(long store, @Nonnegative int length,
    @Nonnegative int at) {
    return (store >>> at) & maskLength(length);
  }

  public static long getBit(long store, @Nonnegative int at) {
    return (store >> at) & 1L;
  }

  public static long getFirstBit(long store) {
    return store & 1L;
  }

  public static long getHead(long store, @Nonnegative int at) {
    return store >>> at;
  }

  public static int intMaskLength(@Nonnegative int length) {
    return (1 << length) - 1;
  }

  public static boolean isAnySet(long store, BitSet pos) {
    for (int i = pos.nextSetBit(0); i >= 0 && i < Long.SIZE; i = pos.nextSetBit(i + 1)) {
      if (isSet(store, i)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isSet(long store, @Nonnegative int pos) {
    assert 0 <= pos && pos < Long.SIZE;
    return ((store >>> pos) & 1L) != 0L;
  }

  public static long maskLength(@Nonnegative int length) {
    return (1L << length) - 1L;
  }

  public static long maskLength(@Nonnegative int length, @Nonnegative int startingAt) {
    return maskLength(length) << startingAt;
  }

  public static int nextSetBit(long store, @Nonnegative int position) {
    for (int pos = position; pos < Long.SIZE; pos++) {
      if (isSet(store, pos)) {
        return pos;
      }
    }
    return -1;
  }

  public static long set(long store, long value, int length, @Nonnegative int at) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length, at) | (value << at);
  }

  public static long set(long store, long value, @Nonnegative int length) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length) | value;
  }

  public static long setBit(long store, @Nonnegative int at) {
    return store | (1L << at);
  }

  public static long setFirstBit(long store) {
    return store | 1L;
  }

  public static long unsetBit(long store, @Nonnegative int at) {
    return store & ~(1L << at);
  }

  public static long unsetFirstBit(long store) {
    return store & ~1L;
  }
}
