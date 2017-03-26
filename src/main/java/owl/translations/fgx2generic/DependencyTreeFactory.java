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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.visitors.DefaultVisitor;
import owl.translations.fgx2generic.DependencyTree.FallbackLeaf;
import owl.translations.fgx2generic.DependencyTree.Leaf;
import owl.translations.fgx2generic.DependencyTree.Type;

class DependencyTreeFactory<T> extends DefaultVisitor<DependencyTree<T>> {

  private final ProductState.Builder<T> builder;
  private final Function<Formula, ? extends Automaton<T, ? extends OmegaAcceptance>> constructor;
  private final EquivalenceClassFactory factory;
  int setNumber;

  DependencyTreeFactory(Factories factory,
    Function<Formula, ? extends Automaton<T, ? extends OmegaAcceptance>> constructor) {
    this.factory = factory.equivalenceClassFactory;
    setNumber = 0;
    builder = ProductState.builder();
    this.constructor = constructor;
  }

  ProductState<T> buildInitialState() {
    return builder.build();
  }

  @Override
  protected DependencyTree<T> defaultAction(Formula formula) {
    Leaf<T> leaf = DependencyTree.createLeaf(formula, setNumber, () ->
      constructor.apply(formula));

    if (leaf.type == Type.COSAFETY || leaf.type == Type.SAFETY) {
      builder.safety.put(formula, factory.createEquivalenceClass(formula.unfold()));
    }

    if (leaf instanceof FallbackLeaf) {
      FallbackLeaf<T> fallbackLeaf = (FallbackLeaf<T>) leaf;
      setNumber += fallbackLeaf.automaton.getAcceptance().getAcceptanceSets();
      builder.fallback.put(formula, fallbackLeaf.automaton.getInitialState());
    } else {
      setNumber++;
    }

    return leaf;
  }

  @Override
  public DependencyTree<T> visit(BooleanConstant booleanConstant) {
    throw new IllegalStateException("The input formula should be constant-free.");
  }

  @Override
  public DependencyTree<T> visit(Conjunction conjunction) {
    List<Formula> safety = new ArrayList<>();
    List<Formula> coSafety = new ArrayList<>();
    List<DependencyTree<T>> children = group(conjunction.children, safety, coSafety);

    if (!safety.isEmpty()) {
      children.add(defaultAction(Conjunction.create(safety)));
    }

    if (!coSafety.isEmpty()) {
      children.add(defaultAction(Conjunction.create(coSafety)));
    }

    return DependencyTree.createAnd(children);
  }

  @Override
  public DependencyTree<T> visit(Disjunction disjunction) {
    List<Formula> safety = new ArrayList<>();
    List<Formula> coSafety = new ArrayList<>();
    List<DependencyTree<T>> children = group(disjunction.children, safety, coSafety);

    if (!safety.isEmpty()) {
      children.add(defaultAction(Disjunction.create(safety)));
    }

    if (!coSafety.isEmpty()) {
      children.add(defaultAction(Disjunction.create(coSafety)));
    }

    return DependencyTree.createOr(children);
  }

  private List<DependencyTree<T>> group(Iterable<Formula> formulas, List<Formula> safety,
    List<Formula> coSafety) {
    List<DependencyTree<T>> children = new ArrayList<>();
    List<Formula> xFragment = new ArrayList<>();

    formulas.forEach(x -> {
      if (Fragments.isX(x)) {
        xFragment.add(x);
        return;
      }

      if (Fragments.isCoSafety(x)) {
        coSafety.add(x);
        return;
      }

      if (Fragments.isSafety(x)) {
        safety.add(x);
        return;
      }

      children.add(x.accept(this));
    });

    if (!safety.isEmpty()) {
      safety.addAll(xFragment);
    } else {
      coSafety.addAll(xFragment);
    }

    return children;
  }
}
