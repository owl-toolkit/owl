package owl.util;

import java.util.BitSet;
import javax.annotation.Nonnegative;

public final class BitUtil {
  private BitUtil() {
  }

  public static boolean areAllSet(final long store, final BitSet pos) {
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

  public static long clear(final long store, @Nonnegative final int length,
    @Nonnegative final int at) {
    return store & ~(maskLength(length, at));
  }

  public static long clear(final long store, @Nonnegative final int length) {
    return store & ~(maskLength(length));
  }

  public static boolean fits(final int value, @Nonnegative final int bitSize) {
    return fits((long) value, bitSize);
  }

  public static boolean fits(final long value, @Nonnegative final int bitSize) {
    return value <= maskLength(bitSize);
  }

  public static long get(final long store, @Nonnegative final int length) {
    return store & maskLength(length);
  }

  public static long get(final long store, @Nonnegative final int length,
    @Nonnegative final int at) {
    return (store >>> at) & maskLength(length);
  }

  public static long getBit(final long store, @Nonnegative final int at) {
    return (store >> at) & 1L;
  }

  public static long getFirstBit(final long store) {
    return store & 1L;
  }

  public static long getHead(final long store, @Nonnegative final int at) {
    return store >>> at;
  }

  public static int intMaskLength(@Nonnegative final int length) {
    return (1 << length) - 1;
  }

  public static boolean isAnySet(final long store, final BitSet pos) {
    for (int i = pos.nextSetBit(0); i >= 0 && i < Long.SIZE; i = pos.nextSetBit(i + 1)) {
      if (isSet(store, i)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isSet(final long store, @Nonnegative final int pos) {
    assert 0 <= pos && pos < Long.SIZE;
    return ((store >>> pos) & 1L) != 0L;
  }

  public static long maskLength(@Nonnegative final int length) {
    return (1L << length) - 1L;
  }

  public static long maskLength(@Nonnegative final int length, @Nonnegative final int startingAt) {
    return maskLength(length) << startingAt;
  }

  public static int nextSetBit(final long store, @Nonnegative final int position) {
    for (int pos = position; pos < Long.SIZE; pos++) {
      if (isSet(store, pos)) {
        return pos;
      }
    }
    return -1;
  }

  public static long set(final long store, final long value, final int length,
    @Nonnegative final int at) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length, at) | (value << at);
  }

  public static long set(final long store, final long value, @Nonnegative final int length) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length) | value;
  }

  public static long setBit(final long store, @Nonnegative final int at) {
    return store | (1L << at);
  }

  public static long setFirstBit(final long store) {
    return store | 1L;
  }

  public static long unsetBit(final long store, @Nonnegative final int at) {
    return store & ~(1L << at);
  }

  public static long unsetFirstBit(final long store) {
    return store & ~1L;
  }
}
