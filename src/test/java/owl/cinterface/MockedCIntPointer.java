package owl.cinterface;

import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.SignedWord;

class MockedCIntPointer extends MockedPointerBase implements CIntPointer {

  private final int[] backingArray;
  private final int offset;

  MockedCIntPointer(int length) {
    this.backingArray = new int[length];
    this.offset = 0;
  }

  @SuppressWarnings("PMD.ArrayIsStoredDirectly")
  private MockedCIntPointer(int[] backingArray, int offset) {
    this.backingArray = backingArray;
    this.offset = offset;
  }

  @Override
  public int read() {
    return backingArray[offset];
  }

  @Override
  public int read(int index) {
    return backingArray[offset + index];
  }

  @Override
  public int read(SignedWord index) {
    throw uoe();
  }

  @Override
  public void write(int value) {
    backingArray[offset] = value;
  }

  @Override
  public void write(int index, int value) {
    backingArray[index + offset] = value;
  }

  @Override
  public void write(SignedWord index, int value) {
    throw uoe();
  }

  @Override
  public CIntPointer addressOf(int index) {
    return new MockedCIntPointer(backingArray, offset + index);
  }

  @Override
  public CIntPointer addressOf(SignedWord index) {
    throw uoe();
  }
}
