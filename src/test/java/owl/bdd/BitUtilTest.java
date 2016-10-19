package owl.bdd;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

@SuppressWarnings({"NumericOverflow", "PMD.AvoidDuplicateLiterals"})
public class BitUtilTest {
  private static long strToL(final String str) {
    return Long.parseUnsignedLong(str, 2);
  }

  @Test
  public void testGetFromStart() {
    final long store = strToL("1111");
    assertThat(BitUtil.get(store, 0), is(strToL("0000")));
    assertThat(BitUtil.get(store, 2), is(strToL("0011")));
    assertThat(BitUtil.get(store, 4), is(strToL("1111")));
    assertThat(BitUtil.get(store, 8), is(strToL("1111")));
  }

  @Test
  public void testGetAt() {
    final long store = strToL("1001");
    assertThat(BitUtil.get(store, 2, 0), is(strToL("01")));
    assertThat(BitUtil.get(store, 4, 0), is(strToL("1001")));
    assertThat(BitUtil.get(store, 8, 0), is(strToL("1001")));
    assertThat(BitUtil.get(store, 2, 2), is(strToL("10")));
    assertThat(BitUtil.get(store, 2, 1), is(strToL("00")));
    assertThat(BitUtil.get(store, 4, 2), is(strToL("10")));
  }

  @Test
  public void testGetHead() {
    final long store = strToL("1010") << 60;
    assertThat(BitUtil.getHead(store, 62), is(strToL("10")));
    assertThat(BitUtil.getHead(store, 60), is(strToL("1010")));
    assertThat(BitUtil.getHead(store, 58), is(strToL("101000")));
  }

  @Test
  public void testSetFromStart() {
    final long store = strToL("1001");
    assertThat(BitUtil.set(store, strToL("01"), 2), is(strToL("1001")));
    assertThat(BitUtil.set(store, strToL("1001"), 4), is(strToL("1001")));
    assertThat(BitUtil.set(store, strToL("1001"), 8), is(strToL("1001")));
    assertThat(BitUtil.set(store, strToL("00"), 2), is(strToL("1000")));
    assertThat(BitUtil.set(store, strToL("0000"), 4), is(strToL("0000")));
    assertThat(BitUtil.set(store, strToL("0000"), 8), is(strToL("0000")));
  }

  @Test
  public void testSet() {
    long store = BitUtil.set(0L, strToL("11"), 4, 0);
    assertThat(store, is(strToL("11")));
    store = BitUtil.set(store, strToL("11"), 2, 4);
    assertThat(store, is(strToL("110011")));
    store = BitUtil.set(store, strToL("00"), 4, 0);
    assertThat(store, is(strToL("110000")));
    store = BitUtil.set(store, strToL("1111"), 4, 2);
    assertThat(store, is(strToL("111100")));
  }

  @Test
  public void testClearFromStart() {
    final long store = strToL("1111");
    assertThat(BitUtil.clear(store, 0), is(strToL("1111")));
    assertThat(BitUtil.clear(store, 2), is(strToL("1100")));
    assertThat(BitUtil.clear(store, 4), is(strToL("0000")));
    assertThat(BitUtil.clear(store, 8), is(strToL("0000")));
  }

  @Test
  public void testClear() {
    final long store = strToL("11011011");
    assertThat(BitUtil.clear(store, 0, 4), is(strToL("11011011")));
    assertThat(BitUtil.clear(store, 4, 4), is(strToL("1011")));
    assertThat(BitUtil.clear(store, 4, 2), is(strToL("11000011")));
    assertThat(BitUtil.clear(store, 4, 1), is(strToL("11000001")));
    assertThat(BitUtil.clear(store, 4, 3), is(strToL("10000011")));
    assertThat(BitUtil.clear(store, 8, 2), is(strToL("00000011")));
  }

  @Test
  public void testGetBit() {
    final long store = strToL("101");
    assertThat(BitUtil.getBit(store, 0), is(1L));
    assertThat(BitUtil.getBit(store, 1), is(0L));
    assertThat(BitUtil.getBit(store, 2), is(1L));
    assertThat(BitUtil.getBit(store, 3), is(0L));
  }

  @Test
  public void testSetBit() {
    assertThat(BitUtil.setBit(strToL("00"), 1), is(strToL("10")));
    assertThat(BitUtil.setBit(strToL("10"), 1), is(strToL("10")));
    assertThat(BitUtil.setBit(strToL("10"), 0), is(strToL("11")));
    assertThat(BitUtil.setBit(strToL("11"), 0), is(strToL("11")));
    assertThat(BitUtil.setBit(strToL("11"), 1), is(strToL("11")));
  }

  @Test
  public void testUnsetBit() {
    assertThat(BitUtil.unsetBit(strToL("00"), 1), is(strToL("00")));
    assertThat(BitUtil.unsetBit(strToL("10"), 1), is(strToL("00")));
    assertThat(BitUtil.unsetBit(strToL("10"), 0), is(strToL("10")));
    assertThat(BitUtil.unsetBit(strToL("11"), 0), is(strToL("10")));
    assertThat(BitUtil.unsetBit(strToL("11"), 1), is(strToL("01")));
  }

  @Test
  public void testGetFirstBit() {
    assertThat(BitUtil.getFirstBit(strToL("00")), is(0L));
    assertThat(BitUtil.getFirstBit(strToL("01")), is(1L));
    assertThat(BitUtil.getFirstBit(strToL("10")), is(0L));
  }

  @Test
  public void testSetFirstBit() {
    assertThat(BitUtil.setFirstBit(strToL("00")), is(strToL("01")));
    assertThat(BitUtil.setFirstBit(strToL("01")), is(strToL("01")));
    assertThat(BitUtil.setFirstBit(strToL("10")), is(strToL("11")));
    assertThat(BitUtil.setFirstBit(strToL("11")), is(strToL("11")));
  }

  @Test
  public void testUnsetFirstBit() {
    assertThat(BitUtil.unsetFirstBit(strToL("00")), is(strToL("00")));
    assertThat(BitUtil.unsetFirstBit(strToL("01")), is(strToL("00")));
    assertThat(BitUtil.unsetFirstBit(strToL("10")), is(strToL("10")));
    assertThat(BitUtil.unsetFirstBit(strToL("11")), is(strToL("10")));
  }

  @Test
  public void testMaskLength() {
    assertThat(BitUtil.maskLength(0), is(strToL("0")));
    assertThat(BitUtil.maskLength(2), is(strToL("11")));
    assertThat(BitUtil.maskLength(4), is(strToL("1111")));
  }

  @Test
  public void testMaskLengthAt() {
    assertThat(BitUtil.maskLength(0, 0), is(strToL("0")));
    assertThat(BitUtil.maskLength(0, 2), is(strToL("0")));
    assertThat(BitUtil.maskLength(2, 0), is(strToL("11")));
    assertThat(BitUtil.maskLength(2, 2), is(strToL("1100")));
    assertThat(BitUtil.maskLength(4, 0), is(strToL("1111")));
    assertThat(BitUtil.maskLength(4, 2), is(strToL("111100")));
  }
}
