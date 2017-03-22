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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
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
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.translations.Optimisation;

public class Builder implements Function<Formula, Automaton<ProductState, GenericAcceptance>> {

  private final EnumSet<Optimisation> optimisations;

  public Builder(EnumSet<Optimisation> optimisations) {
    this.optimisations = EnumSet.copyOf(optimisations);
  }

  @Override
  public Automaton<ProductState, GenericAcceptance> apply(Formula formula) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    Factories factories = Registry.getFactories(rewritten);

    ProductState initialState;
    DependencyTree tree;

    DependencyTreeFactory treeConverter = new DependencyTreeFactory(factories);
    tree = formula.accept(treeConverter);
    BooleanExpression<AtomAcceptance> expression = tree.getAcceptanceExpression();
    int sets = treeConverter.setNumber;

    {
      int maxHistory = tree.getMaxRequiredHistoryLength();
      BitSet bitSet = new BitSet();
      ImmutableList<BitSet> defaultHistory = Lists2.tabulate((x) -> bitSet, maxHistory);
      initialState = new ProductState(treeConverter.getInitialSafetyState(),
        getHistory(defaultHistory, bitSet, treeConverter.getInitialSafetyState(), tree));
    }

    GenericAcceptance acceptance = new GenericAcceptance(sets, expression);

    MutableAutomaton<ProductState, GenericAcceptance> automaton = AutomatonFactory
      .create(acceptance, factories.valuationSetFactory);

    AutomatonUtil.exploreDeterministic(automaton, Collections.singletonList(initialState),
      (x, y) -> getSuccessor(x, y, tree));
    automaton.setInitialState(initialState);
    return automaton;
  }

  private ImmutableList<BitSet> getHistory(ImmutableList<BitSet> previousHistory, BitSet valuation,
    ImmutableMap<Formula, EquivalenceClass> currentState, DependencyTree tree) {
    ImmutableList.Builder<BitSet> history = new ImmutableList.Builder<>();

    if (optimisations.contains(Optimisation.DYNAMIC_HISTORY)) {
      List<BitSet> requiredHistory = tree.getRequiredHistory(currentState);

      int i = 0;
      for (BitSet historyValuation : requiredHistory) {

        if (i == 0) {
          historyValuation.and(valuation);
        } else if (i <= previousHistory.size()) {
          historyValuation.and(previousHistory.get(i - 1));
        } else {
          historyValuation.clear();
        }

        history.add(historyValuation);
        i++;
      }
    } else if (!previousHistory.isEmpty()) {
      history.add(valuation);
      history.addAll(previousHistory.subList(0, previousHistory.size() - 1));
    }

    return history.build();
  }

  private Edge<ProductState> getSuccessor(ProductState state, BitSet valuation,
    DependencyTree tree) {
    ImmutableMap<Formula, EquivalenceClass> safetySuccessor = ImmutableMap.copyOf(
      Maps.transformValues(state.safetyStates, x -> x.unfoldTemporalStep(valuation)));
    ProductState successor = new ProductState(safetySuccessor,
      getHistory(state.history, valuation, safetySuccessor, tree));
    BitSet acceptance = tree.getEdgeAcceptance(state, valuation);
    return Edges.create(successor, acceptance);
  }
}