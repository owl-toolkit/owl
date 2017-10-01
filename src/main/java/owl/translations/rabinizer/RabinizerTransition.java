package owl.translations.rabinizer;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.function.IntConsumer;
import owl.collections.ValuationSet;

/**
 * This class is used to represent one edge in the Rabinizer product automaton. This way, we can
 * first compute the transition system and then successively compute the acceptance condition, i.e.
 * we determine for each pair which edges belong to it instead of determining which pairs belong to
 * a particular edge. This way of "looping" is cheaper, since there is some information that can be
 * cached globally.
 */
final class RabinizerTransition {
  private static final ValuationSet[] EMPTY = new ValuationSet[0];

  private final RabinizerState successorState;
  private ValuationSet[] successorAcceptance = EMPTY;

  RabinizerTransition(RabinizerState successorState) {
    this.successorState = successorState;
  }

  void addAcceptance(ValuationSet valuations, int acceptance) {
    if (successorAcceptance.length <= acceptance) {
      successorAcceptance = Arrays.copyOf(successorAcceptance, acceptance + 1);
    }
    ValuationSet old = successorAcceptance[acceptance];
    if (old == null) {
      successorAcceptance[acceptance] = valuations.copy();
    } else {
      old.addAll(valuations);
    }
  }

  void addAcceptance(ValuationSet valuations, IntSet acceptanceSet) {
    acceptanceSet.forEach((IntConsumer) acceptance -> addAcceptance(valuations, acceptance));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RabinizerTransition)) {
      return false;
    }

    RabinizerTransition cache = (RabinizerTransition) o;
    return successorState.equals(cache.successorState);
  }

  public RabinizerState getRabinizerSuccessor() {
    return successorState;
  }

  ValuationSet[] getSuccessorAcceptance() {
    return successorAcceptance;
  }

  @Override
  public int hashCode() {
    return successorState.hashCode();
  }

  @Override
  public String toString() {
    return successorState.toString();
  }
}
