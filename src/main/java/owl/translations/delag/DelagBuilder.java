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

package owl.translations.delag;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.SingletonAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.FactorySupplier;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;

public class DelagBuilder
  implements Function<LabelledFormula, Automaton<State<Integer>, ? extends EmersonLeiAcceptance>> {

  private final Function<? super LabelledFormula, ? extends Automaton<?, ?>> fallback;

  public DelagBuilder(Function<? super LabelledFormula, ? extends Automaton<?, ?>> fallback) {
    this.fallback = fallback;
  }

  @Override
  public Automaton<State<Integer>, EmersonLeiAcceptance> apply(LabelledFormula inputFormula) {
    LabelledFormula formula = inputFormula.nnf();
    List<String> atomicPropositions = List.copyOf(formula.atomicPropositions());

    if (formula.formula().equals(BooleanConstant.FALSE)) {
      return EmptyAutomaton.of(
        atomicPropositions,
        EmersonLeiAcceptance.of(PropositionalFormula.falseConstant()));
    }

    if (formula.formula().equals(BooleanConstant.TRUE)) {
      return SingletonAutomaton.of(
        atomicPropositions,
        new State<>(),
        AllAcceptance.INSTANCE,
        Set.of());
    }

    DependencyTreeFactory<Integer> treeConverter = new DependencyTreeFactory<>(
      FactorySupplier.defaultSupplier().getEquivalenceClassFactory(atomicPropositions),
    x -> Views.dropStateLabels(fallback.apply(x)));

    DependencyTree<Integer> tree = formula.formula().accept(treeConverter);
    var expression = tree.getAcceptanceExpression();

    ProductState<Integer> initialProduct = treeConverter.buildInitialState();
    State<Integer> initialState = new State<>(initialProduct,
      History.stepHistory(null, new BitSet(),
        History.create(tree.getRequiredHistory(initialProduct))));

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
      atomicPropositions,
      Set.of(initialState),
      EmersonLeiAcceptance.of(expression)) {

      private final Map<ProductState<?>, History> requiredHistory = new HashMap<>();

      @Override
      public Edge<State<Integer>> edgeImpl(State<Integer> state, BitSet valuation) {
        ProductState.Builder<Integer> builder = ProductState.builder();
        Boolean acc = tree.buildSuccessor(state, valuation, builder);

        if (acc != null && !acc) {
          return null;
        }

        var successor = builder.build();
        var history = History.stepHistory(state.past, valuation,
          requiredHistory.computeIfAbsent(successor,
            x -> History.create(tree.getRequiredHistory(successor))));
        var acceptance = tree.getAcceptance(state, valuation, acc);

        if (acceptance().acceptanceSets() <= acceptance.length()) {
          acceptance.clear(acceptance().acceptanceSets(), acceptance.length());
        }

        return Edge.of(new State<>(successor, history), acceptance);
      }
    };
  }
}
