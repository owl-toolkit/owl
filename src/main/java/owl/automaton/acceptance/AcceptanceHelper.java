package owl.automaton.acceptance;

import com.google.common.collect.Iterables;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.edge.Edge;

final class AcceptanceHelper {
  private AcceptanceHelper() {
  }

  public static <S> Function<S, Iterable<Edge<S>>> filterSuccessorFunction(
    Function<S, Iterable<Edge<S>>> successorFunction, Set<S> set) {
    return filterSuccessorFunction(successorFunction, edge -> set.contains(edge
      .getSuccessor()));
  }

  public static <S> Function<S, Iterable<Edge<S>>> filterSuccessorFunction(
    Function<S, Iterable<Edge<S>>> successorFunction, Predicate<Edge<S>> predicate) {
    return successorFunction.andThen(iter -> Iterables.filter(iter, predicate::test));
  }

}
