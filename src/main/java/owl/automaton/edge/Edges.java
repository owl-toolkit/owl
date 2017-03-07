package owl.automaton.edge;

import java.util.BitSet;
import java.util.PrimitiveIterator;
import javax.annotation.Nonnegative;

public final class Edges {
  private Edges() {
  }

  /**
   * Creates an edge which belongs to the specified acceptance sets.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The acceptance sets this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given acceptance.
   */
  public static <S> Edge<S> create(final S successor, final BitSet acceptance) {
    if (acceptance.isEmpty()) {
      return new EdgeSingleton<>(successor);
    }

    if (acceptance.cardinality() == 1) {
      return new EdgeSingleton<>(successor, acceptance.nextSetBit(0));
    }

    if (acceptance.length() <= Long.SIZE) {
      return new EdgeLong<>(successor, acceptance);
    }

    return new EdgeGeneric<>(successor, (BitSet) acceptance.clone());
  }

  /**
   * Creates an edge which belongs to no acceptance set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   *
   * @return An edge leading to {@code successor} with no acceptance.
   */
  public static <S> Edge<S> create(final S successor) {
    return new EdgeSingleton<>(successor);
  }

  /**
   * Creates an edge which belongs to a single acceptance set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The acceptance set this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given acceptance.
   */
  public static <S> EdgeSingleton<S> create(final S successor, @Nonnegative final int acceptance) {
    assert acceptance >= 0;
    return new EdgeSingleton<>(successor, acceptance);
  }

  /**
   * Creates an edge which belongs to the specified acceptance sets.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     An iterator returning the acceptance sets this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given acceptance.
   */
  public static <S> Edge<S> create(final S successor, final PrimitiveIterator.OfInt acceptance) {
    if (!acceptance.hasNext()) {
      return new EdgeSingleton<>(successor);
    }

    int first = acceptance.nextInt();

    if (!acceptance.hasNext()) {
      return new EdgeSingleton<>(successor, first);
    }

    BitSet acceptanceSet = new BitSet();
    acceptanceSet.set(first);

    while (acceptance.hasNext()) {
      acceptanceSet.set(acceptance.nextInt());
    }

    if (acceptanceSet.length() <= Long.SIZE) {
      return new EdgeLong<>(successor, acceptanceSet);
    }

    return new EdgeGeneric<>(successor, acceptanceSet);
  }
}
