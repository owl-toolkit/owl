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
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GenericAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Lists2;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.translations.Optimisation;

public class Builder<T>
  implements Function<Formula, Automaton<State<T>, ? extends OmegaAcceptance>> {

  private final Function<Formula, Automaton<T, OmegaAcceptance>> fallback;
  private final EnumSet<Optimisation> optimisations;

  public Builder(Function<Formula, Automaton<T, OmegaAcceptance>> fallback,
    EnumSet<Optimisation> optimisations) {
    this.fallback = fallback;
    this.optimisations = EnumSet.copyOf(optimisations);
  }

  @Override
  public Automaton<State<T>, ? extends OmegaAcceptance> apply(Formula formula) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    Factories factories = Registry.getFactories(rewritten);

    if (rewritten == BooleanConstant.FALSE) {
      return AutomatonFactory.empty(factories.valuationSetFactory);
    }

    if (rewritten == BooleanConstant.TRUE) {
      return AutomatonFactory.universe(new State<>(), factories.valuationSetFactory);
    }

    State<T> initialState;

    DependencyTreeFactory<T> treeConverter = new DependencyTreeFactory<>(factories,
      fallback);
    DependencyTree<T> tree = formula.accept(treeConverter);
    BooleanExpression<AtomAcceptance> expression = tree.getAcceptanceExpression();
    int sets = treeConverter.setNumber;

    {
      int maxHistory = tree.getMaxRequiredHistoryLength();
      BitSet bitSet = new BitSet();
      ProductState<T> initialProduct = treeConverter.buildInitialState();
      ImmutableList<BitSet> defaultHistory = Lists2.tabulate((x) -> bitSet, maxHistory + 1);
      initialState = new State<>(initialProduct, getHistory(defaultHistory, initialProduct, tree));
    }

    GenericAcceptance acceptance = new GenericAcceptance(sets, expression);

    MutableAutomaton<State<T>, GenericAcceptance> automaton = AutomatonFactory
      .create(acceptance, factories.valuationSetFactory);

    AutomatonUtil.exploreDeterministic(automaton, Collections.singletonList(initialState),
      (x, y) -> this.getSuccessor(tree, x, y));
    automaton.setInitialState(initialState);
    return automaton;
  }

  private ImmutableList<BitSet> getHistory(List<BitSet> previousHistory,
    ProductState<T> currentState, DependencyTree<T> tree) {
    ImmutableList.Builder<BitSet> history = new ImmutableList.Builder<>();

    if (optimisations.contains(Optimisation.DYNAMIC_HISTORY)) {
      List<BitSet> requiredHistory = tree.getRequiredHistory(currentState);

      int i = 0;
      for (BitSet historyValuation : requiredHistory) {

        if (i < previousHistory.size()) {
          historyValuation.and(previousHistory.get(i));
        } else {
          historyValuation.clear();
        }

        history.add(historyValuation);
        i++;
      }
    } else if (!previousHistory.isEmpty()) {
      history.addAll(previousHistory.subList(0, previousHistory.size() - 1));
    }

    return history.build();
  }

  @Nullable
  private Edge<State<T>> getSuccessor(DependencyTree<T> tree, State<T> state, BitSet valuation) {
    ProductState.Builder<T> builder = ProductState.builder();
    Boolean acc = tree.buildSuccessor(state, valuation, builder);

    if (acc != null && !acc) {
      return null;
    }

    ProductState<T> successor = builder.build();
    List<BitSet> history = getHistory(Lists2.cons(valuation, state.history), successor, tree);
    return Edges.create(new State<>(successor, history), tree.getAcceptance(state, valuation, acc));
  }
}