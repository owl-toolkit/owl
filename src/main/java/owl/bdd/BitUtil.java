package owl.bdd;

final class BitUtil {
  private BitUtil() {
  }

  public static long clear(final long store, final int length, final int at) {
    return store & ~(maskLength(length, at));
  }

  public static long clear(final long store, final int length) {
    return store & ~(maskLength(length));
  }

  public static boolean fits(final int value, final int bitSize) {
    return fits((long) value, bitSize);
  }

  public static boolean fits(final long value, final int bitSize) {
    return value <= maskLength(bitSize);
  }

  public static long get(final long store, final int length) {
    return store & maskLength(length);
  }

  public static long get(final long store, final int length, final int at) {
    return (store >>> at) & maskLength(length);
  }

  public static long getBit(final long store, final int at) {
    return (store >> at) & 1L;
  }

  public static long getFirstBit(final long store) {
    return store & 1L;
  }

  public static long getHead(final long store, final int at) {
    return store >>> at;
  }

  public static int intMaskLength(final int length) {
    return (1 << length) - 1;
  }

  public static long maskLength(final int length) {
    return (1L << length) - 1L;
  }

  public static long maskLength(final int length, final int startingAt) {
    return maskLength(length) << startingAt;
  }

  public static long set(final long store, final long value, final int length, final int at) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length, at) | (value << at);
  }

  public static long set(final long store, final long value, final int length) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length) | value;
  }

  public static long setBit(final long store, final int at) {
    return store | (1L << at);
  }

  public static long setFirstBit(final long store) {
    return store | 1L;
  }

  public static long unsetBit(final long store, final int at) {
    return store & ~(1L << at);
  }

  public static long unsetFirstBit(final long store) {
    return store & ~1L;
  }
}
