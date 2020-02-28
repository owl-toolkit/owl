package owl.cinterface;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.word.SignedWord;

class MockedCDoublePointer extends MockedPointerBase implements CDoublePointer {
  private final double[] backingArray;

  MockedCDoublePointer(int length) {
    this.backingArray = new double[length];
  }

  @Override
  public double read() {
    return backingArray[0];
  }

  @Override
  public double read(int index) {
    return backingArray[index];
  }

  @Override
  public double read(SignedWord index) {
    throw uoe();
  }

  @Override
  public void write(double value) {
    backingArray[0] = value;
  }

  @Override
  public void write(int index, double value) {
    backingArray[index] = value;
  }

  @Override
  public void write(SignedWord index, double value) {
    throw uoe();
  }

  @Override
  public CDoublePointer addressOf(int index) {
    throw uoe();
  }

  @Override
  public CDoublePointer addressOf(SignedWord index) {
    throw uoe();
  }
}
