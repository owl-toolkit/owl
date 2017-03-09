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

package owl.translations.fgx2generic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GenericAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Lists2;
import owl.collections.ValuationSet;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.Formula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.XDepthVisitor;

public class Builder implements Function<Formula, Automaton<HistoryState, GenericAcceptance>> {

  @Override
  public Automaton<HistoryState, GenericAcceptance> apply(Formula formula) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);

    Factories factories = Registry.getFactories(rewritten);
    int history = XDepthVisitor.getDepth(rewritten);
    GenericAcceptanceComputation visitor = new GenericAcceptanceComputation(factories, history);

    BooleanExpression<AtomAcceptance> expression = rewritten.accept(visitor);
    int sets = visitor.getSets().size();
    GenericAcceptance acceptance = new GenericAcceptance(sets, expression);

    MutableAutomaton<HistoryState, GenericAcceptance> automaton = AutomatonFactory
      .create(acceptance, factories.valuationSetFactory);

    BitSet bitSet = new BitSet();
    HistoryState state = new HistoryState(Lists2.tabulate((i) -> bitSet, history));

    AutomatonUtil.exploreDeterministic(automaton, Collections.singletonList(state),
      (x, y) -> getSuccessor(visitor.getSets(), x, y));
    automaton.setInitialState(state);
    return automaton;
  }

  private Edge<HistoryState> getSuccessor(Map<List<ValuationSet>, Integer> sets, HistoryState state,
    BitSet valuation) {
    BitSet acceptance = new BitSet();

    // Replace by Lists2.
    List<BitSet> seenValuations = Lists
      .newArrayList(Iterables.concat(state.history, ImmutableList.of(valuation)));

    sets.forEach((set, id) -> {
      if (Lists2.zipAllMatch(set, seenValuations, ValuationSet::contains)) {
        acceptance.set(id);
      }
    });

    List<BitSet> newHistory = Lists2.shift(new ArrayList<>(state.history), valuation);
    HistoryState successor = new HistoryState(ImmutableList.copyOf(newHistory));
    return Edges.create(successor, acceptance);
  }
}
