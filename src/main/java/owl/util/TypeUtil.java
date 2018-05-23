package owl.util;

import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;

public final class TypeUtil {
  private TypeUtil() {}

  @SuppressWarnings("unchecked")
  public static <S> LabelledEdge<S> cast(LabelledEdge<? extends S> labelledEdge) {
    return (LabelledEdge<S>) labelledEdge;
  }

  @SuppressWarnings("unchecked")
  public static <S> Edge<S> cast(Edge<? extends S> edge) {
    return (Edge<S>) edge;
  }
}
