package owl.automaton.edge;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnegative;

public final class Edges {
  private static final Map<BitSet, BitSet> internalCache = new HashMap<>();

  private Edges() {
  }

  /**
   * Creates an edge which belongs to the specified acceptance sets. The passed {@code acceptance}
   * must not be modified afterwards.
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

    @SuppressWarnings("UseOfClone")
    final BitSet cachedSet = internalCache
      .computeIfAbsent(acceptance, key -> (BitSet) acceptance.clone());
    assert Objects.equals(cachedSet, acceptance);
    return new EdgeGeneric<>(successor, cachedSet);
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
}
