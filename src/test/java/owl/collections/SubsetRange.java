package owl.collections;

/**
 * Created by tlm on 27/03/17.
 */
final class SubsetRange {
  public final int high;
  public final int low;

  public SubsetRange(int low, int high) {
    assert low <= high;
    this.low = low;
    this.high = high;
  }

  public boolean contains(SubsetRange other) {
    return low <= other.low && other.high <= high;
  }

  @Override
  public String toString() {
    return String.format("[%d, %d)", low, high);
  }
}
