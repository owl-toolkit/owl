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

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.bdd.EquivalenceClassFactory;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragments;
import owl.ltl.visitors.PropositionalVisitor;
import owl.translations.delag.DependencyTree.FallbackLeaf;
import owl.translations.delag.DependencyTree.Leaf;
import owl.translations.delag.DependencyTree.Type;

class DependencyTreeFactory<T> extends PropositionalVisitor<DependencyTree<T>> {

  private final ProductState.Builder<T> builder;
  private final Function<Formula, ? extends Automaton<T, ?>> constructor;
  private final EquivalenceClassFactory factory;
  private final Map<Formula, Automaton<T, ?>> automatonCache = new HashMap<>();
  int setNumber;

  DependencyTreeFactory(EquivalenceClassFactory factory,
    Function<LabelledFormula, ? extends Automaton<T, ?>> constructor) {
    this.factory = factory;
    setNumber = 0;
    builder = ProductState.builder();
    this.constructor = formula -> automatonCache.computeIfAbsent(formula, x ->
      AcceptanceOptimizations.transform(
        constructor.apply(LabelledFormula.of(x, this.factory.atomicPropositions())))
      );
  }

  ProductState<T> buildInitialState() {
    return builder.build();
  }

  @Override
  protected DependencyTree<T> visit(Formula.TemporalOperator formula) {
    return defaultAction(formula, null);
  }

  @Override
  public DependencyTree<T> visit(Literal literal) {
    return defaultAction(literal, null);
  }

  private DependencyTree<T> defaultAction(Formula formula,
    @Nullable PropositionalFormula<Integer> piggyback) {
    Leaf<T> leaf = DependencyTree.createLeaf(formula, setNumber, () -> constructor.apply(formula),
      piggyback);

    if (leaf.type == Type.CO_SAFETY || leaf.type == Type.SAFETY) {
      builder.addSafety(formula, factory.of(formula.unfold()));
    }

    if (leaf instanceof FallbackLeaf<T> fallbackLeaf) {
      assert piggyback == null;
      setNumber += fallbackLeaf.automaton.acceptance().acceptanceSets();
      T initialState = Iterables.getOnlyElement(fallbackLeaf.automaton.initialStates(), null);

      if (initialState == null) {
        builder.addFinished(fallbackLeaf, Boolean.FALSE);
      } else {
        builder.addFallback(formula, initialState);
      }
    } else if (piggyback == null) {
      setNumber++;
    }

    return leaf;
  }

  @Nullable
  private PropositionalFormula<Integer> findPiggybackableLeaf(List<DependencyTree<T>> leafs) {
    for (DependencyTree<T> leaf : leafs) {
      if ((leaf instanceof Leaf)
        && (((Leaf<?>) leaf).type == Type.LIMIT_GF || ((Leaf<?>) leaf).type == Type.LIMIT_FG)) {
        return leaf.getAcceptanceExpression();
      }
    }

    return null;
  }

  private List<DependencyTree<T>> group(Iterable<Formula> formulas, List<Formula> safety,
    List<Formula> coSafety, List<Formula> finite) {
    List<DependencyTree<T>> children = new ArrayList<>();

    formulas.forEach(x -> {
      if (SyntacticFragments.isFinite(x)) {
        finite.add(x);
        return;
      }

      if (SyntacticFragments.isCoSafety(x)) {
        coSafety.add(x);
        return;
      }

      if (SyntacticFragments.isSafety(x)) {
        safety.add(x);
        return;
      }

      children.add(x.accept(this));
    });

    return children;
  }

  @Override
  public DependencyTree<T> visit(Disjunction disjunction) {
    List<Formula> safety = new ArrayList<>();
    List<Formula> coSafety = new ArrayList<>();
    List<Formula> finite = new ArrayList<>();

    List<DependencyTree<T>> children = group(disjunction.operands, safety, coSafety, finite);

    if (safety.isEmpty()) {
      coSafety.addAll(finite);
    } else {
      safety.addAll(finite);
    }

    if (!safety.isEmpty()) {
      children.add(defaultAction(Disjunction.of(safety), findPiggybackableLeaf(children)));
    }

    if (!coSafety.isEmpty()) {
      children.add(defaultAction(Disjunction.of(coSafety), null));
    }

    return DependencyTree.createOr(children);
  }

  @Override
  public DependencyTree<T> visit(BooleanConstant booleanConstant) {
    throw new IllegalStateException("The input formula should be constant-free.");
  }

  @Override
  public DependencyTree<T> visit(Conjunction conjunction) {
    List<Formula> safety = new ArrayList<>();
    List<Formula> coSafety = new ArrayList<>();
    List<Formula> finite = new ArrayList<>();

    List<DependencyTree<T>> children = group(conjunction.operands, safety, coSafety, finite);

    if (coSafety.isEmpty()) {
      safety.addAll(finite);
    } else {
      coSafety.addAll(finite);
    }

    if (!safety.isEmpty()) {
      children.add(defaultAction(Conjunction.of(safety), null));
    }

    if (!coSafety.isEmpty()) {
      children.add(defaultAction(Conjunction.of(coSafety), findPiggybackableLeaf(children)));
    }

    return DependencyTree.createAnd(children);
  }
}
