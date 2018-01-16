/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.BitSet;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import owl.automaton.Automaton.Property;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;

public final class MutableAutomatonFactory {
  private MutableAutomatonFactory() {
  }

  /**
   * Creates an empty automaton with given acceptance condition. The {@code valuationSetFactory} is
   * used as transition backend.
   *
   * @param acceptance
   *     The acceptance of the new automaton.
   * @param valuationSetFactory
   *     The transition valuation set factory
   * @param <S>
   *     The states of the automaton.
   * @param <A>
   *     The acceptance condition of the automaton.
   *
   * @return Empty automaton with the specified parameters.
   */
  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> create(A acceptance,
    ValuationSetFactory valuationSetFactory) {
    return new HashMapAutomaton<>(valuationSetFactory, acceptance);
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> create(A acceptance,
    ValuationSetFactory valuationSetFactory, Collection<S> initialStates,
    BiFunction<S, BitSet, Edge<S>> successors, Function<S, BitSet> alphabet) {
    MutableAutomaton<S, A> automaton = create(acceptance, valuationSetFactory);
    AutomatonUtil.exploreDeterministic(automaton, initialStates, successors, alphabet);
    automaton.setInitialStates(initialStates);
    return automaton;
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> create(
    Automaton<S, A> automaton) {
    Preconditions.checkArgument(automaton.is(Property.DETERMINISTIC),
      "Only deterministic automata supported");
    // TODO Efficient copy of HashMapAutomaton
    return create(automaton.getAcceptance(),
      automaton.getFactory(), automaton.getInitialStates(), automaton::getEdge,
      (x) -> null);
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> singleton(S state,
    ValuationSetFactory factory, A acceptance) {
    return singleton(state, factory, acceptance, IntSets.EMPTY_SET);
  }

  public static <S, A extends OmegaAcceptance> MutableAutomaton<S, A> singleton(S state,
    ValuationSetFactory factory, A acceptance, IntSet acceptanceSet) {
    BitSet loopAcceptance = new BitSet();
    acceptanceSet.forEach((IntConsumer) loopAcceptance::set);
    return create(acceptance, factory, Set.of(state),
      (s, vs) -> Edge.of(state, loopAcceptance), s -> new BitSet(0));
  }
}
