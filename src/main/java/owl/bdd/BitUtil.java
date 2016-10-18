package owl.bdd;

final class BitUtil {
  private BitUtil() {
  }

  public static long get(long store, int length) {
    return store & maskLength(length);
  }

  public static long get(long store, int length, int at) {
    return (store >>> at) & maskLength(length);
  }

  public static long getHead(long store, int at) {
    return store >>> at;
  }

  public static long set(long store, long value, int length, int at) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length, at) | (value << at);
  }

  public static long clear(long store, int length, int at) {
    return store & ~(maskLength(length, at));
  }

  public static long clear(long store, int length) {
    return store & ~(maskLength(length));
  }

  public static long set(long store, long value, int length) {
    assert value <= maskLength(length) : "Bit size exceeded";
    return clear(store, length) | value;
  }

  public static long getBit(long store, int at) {
    return (store >> at) & 1L;
  }

  public static long getFirstBit(long store) {
    return store & 1L;
  }

  public static long setBit(long store, int at) {
    return store | (1L << at);
  }

  public static long setFirstBit(long store) {
    return store | 1L;
  }

  public static long unsetBit(long store, int at) {
    return store & ~(1L << at);
  }

  public static long unsetFirstBit(long store) {
    return store & ~1L;
  }

  public static boolean fits(int value, int bitSize) {
    return fits((long) value, bitSize);
  }

  public static boolean fits(long value, int bitSize) {
    return value <= maskLength(bitSize);
  }

  public static long maskLength(int length) {
    return (1L << length) - 1L;
  }

  public static int intMaskLength(int length) {
    return (1 << length) - 1;
  }

  public static long maskLength(int length, int startingAt) {
    return maskLength(length) << startingAt;
  }
}
