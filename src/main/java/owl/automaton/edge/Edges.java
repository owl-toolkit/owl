package owl.automaton.edge;

import com.google.common.collect.Iterables;
import de.tum.in.naturals.NaturalsTransformer;
import de.tum.in.naturals.bitset.ImmutableBitSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.BitSet;
import java.util.PrimitiveIterator;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nonnegative;

public final class Edges {
  private Edges() {}

  /**
   * Creates an edge which belongs to the specified delegate sets.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The delegate sets this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given delegate.
   */
  public static <S> Edge<S> create(S successor, BitSet acceptance) {
    if (acceptance.isEmpty()) {
      return new EdgeSingleton<>(successor);
    }

    if (acceptance.cardinality() == 1) {
      return new EdgeSingleton<>(successor, acceptance.nextSetBit(0));
    }

    if (acceptance.length() <= Long.SIZE) {
      return new EdgeLong<>(successor, acceptance);
    }

    return new EdgeGeneric<>(successor, ImmutableBitSet.copyOf(acceptance));
  }

  /**
   * Creates an edge which belongs to no delegate set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   *
   * @return An edge leading to {@code successor} with no delegate.
   */
  public static <S> Edge<S> create(S successor) {
    return new EdgeSingleton<>(successor);
  }

  /**
   * Creates an edge which belongs to a single delegate set.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     The delegate set this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given delegate.
   */
  public static <S> EdgeSingleton<S> create(S successor, @Nonnegative int acceptance) {
    assert acceptance >= 0;
    return new EdgeSingleton<>(successor, acceptance);
  }

  /**
   * Creates an edge which belongs to the specified delegate sets.
   *
   * @param successor
   *     Successor of this edge.
   * @param <S>
   *     Type of the successor.
   * @param acceptance
   *     An iterator returning the delegate sets this edge should belong to.
   *
   * @return An edge leading to {@code successor} with given delegate.
   */
  public static <S> Edge<S> create(S successor, PrimitiveIterator.OfInt acceptance) {
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

    if (acceptanceSet.cardinality() == 1) {
      // Could happen if acceptance returns duplicates
      return new EdgeSingleton<>(successor, first);
    }

    if (acceptanceSet.length() <= Long.SIZE) {
      return new EdgeLong<>(successor, acceptanceSet);
    }

    return new EdgeGeneric<>(successor, ImmutableBitSet.copyOf(acceptanceSet));
  }

  public static <S> Edge<S> transformAcceptance(Edge<S> original, IntUnaryOperator transformer) {
    OfInt originalAcceptance = original.acceptanceSetIterator();
    IntIterator newAcceptance = new NaturalsTransformer(originalAcceptance, transformer);
    return create(original.getSuccessor(), newAcceptance);
  }

  public static <S> Iterable<S> toSuccessors(Iterable<Edge<S>> edges) {
    return Iterables.transform(edges, Edge::getSuccessor);
  }
}
