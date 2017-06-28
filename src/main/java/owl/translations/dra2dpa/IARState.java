package owl.translations.dra2dpa;

import de.tum.in.naturals.IntPreOrder;
import java.util.Objects;

public final class IARState<R> {
  private final int hashCode;
  private final R originalState;
  private final IntPreOrder record;

  private IARState(R originalState, IntPreOrder record) {
    this.originalState = originalState;
    this.record = record;
    hashCode = originalState.hashCode() * 31 + record.hashCode();
  }

  public static <R> IARState<R> active(R originalState, IntPreOrder record) {
    return new IARState<>(originalState, record);
  }

  public static <R> IARState<R> trivial(R originalState) {
    return new IARState<>(originalState, IntPreOrder.empty());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IARState)) {
      return false;
    }

    IARState<?> other = (IARState<?>) o;
    return Objects.equals(originalState, other.originalState)
      && Objects.equals(record, other.record);
  }

  public R getOriginalState() {
    return originalState;
  }

  public IntPreOrder getRecord() {
    return record;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    if (record.size() == 0) {
      return String.format("{%s}", getOriginalState());
    }
    return String.format("{%s|%s}", getOriginalState(), record);
  }
}
