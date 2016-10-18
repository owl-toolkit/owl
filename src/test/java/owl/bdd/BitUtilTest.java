package owl.bdd;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

@SuppressWarnings("NumericOverflow")
public class BitUtilTest {
  private static long l(String string) {
    return Long.parseUnsignedLong(string, 2);
  }

  @Test
  public void testGetFromStart() {
    long store = l("1111");
    assertThat(BitUtil.get(store, 0), is(l("0000")));
    assertThat(BitUtil.get(store, 2), is(l("0011")));
    assertThat(BitUtil.get(store, 4), is(l("1111")));
    assertThat(BitUtil.get(store, 8), is(l("1111")));
  }

  @Test
  public void testGetAt() {
    long store = l("1001");
    assertThat(BitUtil.get(store, 2, 0), is(l("01")));
    assertThat(BitUtil.get(store, 4, 0), is(l("1001")));
    assertThat(BitUtil.get(store, 8, 0), is(l("1001")));
    assertThat(BitUtil.get(store, 2, 2), is(l("10")));
    assertThat(BitUtil.get(store, 2, 1), is(l("00")));
    assertThat(BitUtil.get(store, 4, 2), is(l("10")));
  }

  @Test
  public void testGetHead() {
    long store = l("1010") << 60;
    assertThat(BitUtil.getHead(store, 62), is(l("10")));
    assertThat(BitUtil.getHead(store, 60), is(l("1010")));
    assertThat(BitUtil.getHead(store, 58), is(l("101000")));
  }

  @Test
  public void testSetFromStart() {
    long store = l("1001");
    assertThat(BitUtil.set(store, l("01"), 2), is(l("1001")));
    assertThat(BitUtil.set(store, l("1001"), 4), is(l("1001")));
    assertThat(BitUtil.set(store, l("1001"), 8), is(l("1001")));
    assertThat(BitUtil.set(store, l("00"), 2), is(l("1000")));
    assertThat(BitUtil.set(store, l("0000"), 4), is(l("0000")));
    assertThat(BitUtil.set(store, l("0000"), 8), is(l("0000")));
  }

  @Test
  public void testSet() {
    long store = BitUtil.set(0, l("11"), 4, 0);
    assertThat(store, is(l("11")));
    store = BitUtil.set(store, l("11"), 2, 4);
    assertThat(store, is(l("110011")));
    store = BitUtil.set(store, l("00"), 4, 0);
    assertThat(store, is(l("110000")));
    store = BitUtil.set(store, l("1111"), 4, 2);
    assertThat(store, is(l("111100")));
  }

  @Test
  public void testClearFromStart() {
    long store = l("1111");
    assertThat(BitUtil.clear(store, 0), is(l("1111")));
    assertThat(BitUtil.clear(store, 2), is(l("1100")));
    assertThat(BitUtil.clear(store, 4), is(l("0000")));
    assertThat(BitUtil.clear(store, 8), is(l("0000")));
  }

  @Test
  public void testClear() {
    long store = l("11011011");
    assertThat(BitUtil.clear(store, 0, 4), is(l("11011011")));
    assertThat(BitUtil.clear(store, 4, 4), is(l("1011")));
    assertThat(BitUtil.clear(store, 4, 2), is(l("11000011")));
    assertThat(BitUtil.clear(store, 4, 1), is(l("11000001")));
    assertThat(BitUtil.clear(store, 4, 3), is(l("10000011")));
    assertThat(BitUtil.clear(store, 8, 2), is(l("00000011")));
  }

  @Test
  public void testGetBit() {
    long store = l("101");
    assertThat(BitUtil.getBit(store, 0), is(1L));
    assertThat(BitUtil.getBit(store, 1), is(0L));
    assertThat(BitUtil.getBit(store, 2), is(1L));
    assertThat(BitUtil.getBit(store, 3), is(0L));
  }

  @Test
  public void testSetBit() {
    assertThat(BitUtil.setBit(l("00"), 1), is(l("10")));
    assertThat(BitUtil.setBit(l("10"), 1), is(l("10")));
    assertThat(BitUtil.setBit(l("10"), 0), is(l("11")));
    assertThat(BitUtil.setBit(l("11"), 0), is(l("11")));
    assertThat(BitUtil.setBit(l("11"), 1), is(l("11")));
  }

  @Test
  public void testUnsetBit() {
    assertThat(BitUtil.unsetBit(l("00"), 1), is(l("00")));
    assertThat(BitUtil.unsetBit(l("10"), 1), is(l("00")));
    assertThat(BitUtil.unsetBit(l("10"), 0), is(l("10")));
    assertThat(BitUtil.unsetBit(l("11"), 0), is(l("10")));
    assertThat(BitUtil.unsetBit(l("11"), 1), is(l("01")));
  }

  @Test
  public void testGetFirstBit() {
    assertThat(BitUtil.getFirstBit(l("00")), is(0L));
    assertThat(BitUtil.getFirstBit(l("01")), is(1L));
    assertThat(BitUtil.getFirstBit(l("10")), is(0L));
  }

  @Test
  public void testSetFirstBit() {
    assertThat(BitUtil.setFirstBit(l("00")), is(l("01")));
    assertThat(BitUtil.setFirstBit(l("01")), is(l("01")));
    assertThat(BitUtil.setFirstBit(l("10")), is(l("11")));
    assertThat(BitUtil.setFirstBit(l("11")), is(l("11")));
  }

  @Test
  public void testUnsetFirstBit() {
    assertThat(BitUtil.unsetFirstBit(l("00")), is(l("00")));
    assertThat(BitUtil.unsetFirstBit(l("01")), is(l("00")));
    assertThat(BitUtil.unsetFirstBit(l("10")), is(l("10")));
    assertThat(BitUtil.unsetFirstBit(l("11")), is(l("10")));
  }

  @Test
  public void testMaskLength() {
    assertThat(BitUtil.maskLength(0), is(l("0")));
    assertThat(BitUtil.maskLength(2), is(l("11")));
    assertThat(BitUtil.maskLength(4), is(l("1111")));
  }

  @Test
  public void testMaskLengthAt() {
    assertThat(BitUtil.maskLength(0, 0), is(l("0")));
    assertThat(BitUtil.maskLength(0, 2), is(l("0")));
    assertThat(BitUtil.maskLength(2, 0), is(l("11")));
    assertThat(BitUtil.maskLength(2, 2), is(l("1100")));
    assertThat(BitUtil.maskLength(4, 0), is(l("1111")));
    assertThat(BitUtil.maskLength(4, 2), is(l("111100")));
  }
}
