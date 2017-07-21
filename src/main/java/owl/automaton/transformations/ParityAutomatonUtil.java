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

package owl.automaton.transformations;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Collections2;
import java.util.BitSet;
import java.util.Collection;
import java.util.function.Supplier;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;

public final class ParityAutomatonUtil {

  private ParityAutomatonUtil() {
  }

  public static <S> MutableAutomaton<S, ParityAcceptance> changeAcceptance(
    MutableAutomaton<S, BuchiAcceptance> automaton) {
    return new WrappedBuchiAutomaton<>(automaton);
  }

  public static <S> void complete(MutableAutomaton<S, ParityAcceptance> automaton,
    Supplier<S> sinkSupplier) {
    BitSet rejectingAcceptance = new BitSet();
    ParityAcceptance parityCondition = automaton.getAcceptance();

    if (parityCondition.getAcceptanceSets() < 2) {
      parityCondition.setAcceptanceSets(2);
    }

    if (parityCondition.getPriority() == Priority.EVEN) {
      rejectingAcceptance.set(1);
    } else {
      rejectingAcceptance.set(0);
    }

    AutomatonUtil.complete(automaton, sinkSupplier, () -> rejectingAcceptance);
  }

  public static <S> void complement(MutableAutomaton<S, ParityAcceptance> automaton,
    Supplier<S> sinkSupplier) {
    complete(automaton, sinkSupplier);
    automaton.getAcceptance().complement();
  }

  private static final class WrappedBuchiAutomaton<S>
    extends ForwardingMutableAutomaton<S, ParityAcceptance, BuchiAcceptance> {

    private final ParityAcceptance acceptance;

    @Override
    public ParityAcceptance getAcceptance() {
      return acceptance;
    }

    WrappedBuchiAutomaton(MutableAutomaton<S, BuchiAcceptance> backingAutomaton) {
      super(backingAutomaton);
      acceptance = new ParityAcceptance(2, Priority.EVEN);
    }

    private boolean isAcceptanceCompat() {
      return acceptance.getAcceptanceSets() == 2 && acceptance.getPriority() == Priority.EVEN;
    }

    private Edge<S> convertBuchiToParity(Edge<S> edge) {
      if (edge.inSet(0)) {
        return edge;
      }

      return Edges.create(edge.getSuccessor(), 1);
    }

    private Edge<S> convertParityToBuchi(Edge<S> edge) {
      checkState(isAcceptanceCompat());

      if (edge.inSet(0)) {
        return Edges.create(edge.getSuccessor(), 0);
      }

      return Edges.create(edge.getSuccessor());
    }

    @Override
    public void addEdge(S source, BitSet valuation, Edge<S> edge) {
      super.addEdge(source, valuation, convertParityToBuchi(edge));
    }

    @Override
    public void addEdge(S source, ValuationSet valuations, Edge<S> edge) {
      super.addEdge(source, valuations, convertParityToBuchi(edge));
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      //noinspection ConstantConditions
      return Collections2.transform(super.getLabelledEdges(state), labelledEdge ->
        new LabelledEdge<>(convertBuchiToParity(labelledEdge.edge), labelledEdge.valuations));
    }
  }
}
