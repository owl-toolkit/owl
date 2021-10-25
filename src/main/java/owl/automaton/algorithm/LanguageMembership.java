/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.automaton.algorithm;

import com.google.auto.value.AutoValue;
import java.util.BitSet;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.UltimatelyPeriodicWord;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ImmutableBitSet;

public final class LanguageMembership {

  private LanguageMembership() {}

  public static <S, A extends EmersonLeiAcceptance> boolean contains(Automaton<S, A> automaton,
    UltimatelyPeriodicWord word) {
    return !LanguageEmptiness.isEmpty(new IndexedAutomaton<>(automaton, word));
  }

  @AutoValue
  abstract static class IndexedState<S> implements AnnotatedState<S> {
    public abstract int index();

    @Override
    public abstract S state();

    static <S> IndexedState<S> of(int index, S state) {
      return new AutoValue_LanguageMembership_IndexedState<>(index, state);
    }
  }

  private static final class IndexedAutomaton<S, A extends EmersonLeiAcceptance>
    extends AbstractMemoizingAutomaton.EdgesImplementation<IndexedState<S>, A> {

    private final Automaton<S, A> automaton;
    private final UltimatelyPeriodicWord word;

    private IndexedAutomaton(Automaton<S, A> automaton, UltimatelyPeriodicWord word) {
      super(
        automaton.atomicPropositions(),
        automaton.factory(),
        automaton.initialStates().stream()
          .map(x -> IndexedState.of(-word.prefix().size(), x))
          .collect(Collectors.toUnmodifiableSet()),
        automaton.acceptance());
      this.automaton = automaton;
      this.word = word;
    }

    @Override
    protected Set<Edge<IndexedState<S>>> edgesImpl(IndexedState<S> state, BitSet valuation) {
      ImmutableBitSet allowedValuation;

      if (state.index() < 0) {
        allowedValuation = word.prefix().get(-state.index() - 1);
      } else {
        allowedValuation = word.period().get(state.index());
      }

      if (!allowedValuation.equals(ImmutableBitSet.copyOf(valuation))) {
        return Set.of();
      }

      Set<Edge<S>> edges = automaton.edges(state.state(), valuation);
      int nextIndex;
      if (state.index() + 1 >= word.period().size()) {
        nextIndex = (state.index() + 1) % word.period().size();
      } else {
        nextIndex = state.index() + 1;
      }

      return edges.stream()
        .map(x -> x.mapSuccessor(y -> IndexedState.of(nextIndex, y)))
        .collect(Collectors.toUnmodifiableSet());
    }
  }
}
