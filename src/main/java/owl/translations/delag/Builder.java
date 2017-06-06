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

package owl.translations.delag;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.BitSet;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.GenericAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;

public class Builder<T>
  implements Function<Formula, Automaton<State<T>, ? extends OmegaAcceptance>> {

  private final Function<Formula, Automaton<T, OmegaAcceptance>> fallback;
  @Nullable
  private LoadingCache<ProductState<T>, History> requiredHistoryCache;

  public Builder(Function<Formula, Automaton<T, OmegaAcceptance>> fallback) {
    this.fallback = fallback;
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

    requiredHistoryCache = CacheBuilder.newBuilder().maximumSize(1024L).build(
      new CacheLoader<ProductState<T>, History>() {
        @Override
        public History load(ProductState<T> key) {
          return History.create(tree.getRequiredHistory(key));
        }
      });

    ProductState<T> initialProduct = treeConverter.buildInitialState();
    initialState = new State<>(initialProduct, getHistory(null, new BitSet(), initialProduct));

    GenericAcceptance acceptance = new GenericAcceptance(sets, expression);
    return AutomatonFactory.createStreamingAutomaton(acceptance, initialState,
      factories.valuationSetFactory, (x, y) -> this.getSuccessor(tree, x, y));
  }

  private History getHistory(History past, BitSet present, ProductState<T> state) {
    History requiredHistory = requiredHistoryCache.getUnchecked(state);
    return History.stepHistory(past, present, requiredHistory);
  }

  @Nullable
  private Edge<State<T>> getSuccessor(DependencyTree<T> tree, State<T> state, BitSet valuation) {
    ProductState.Builder<T> builder = ProductState.builder();
    Boolean acc = tree.buildSuccessor(state, valuation, builder);

    if (acc != null && !acc) {
      return null;
    }

    ProductState<T> successor = builder.build();
    History history = getHistory(state.past, valuation, successor);
    return Edges.create(new State<>(successor, history), tree.getAcceptance(state, valuation, acc));
  }
}