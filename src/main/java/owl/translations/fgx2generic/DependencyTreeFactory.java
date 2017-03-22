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

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.visitors.DefaultVisitor;
import owl.translations.fgx2generic.DependencyTree.And;
import owl.translations.fgx2generic.DependencyTree.Leaf;
import owl.translations.fgx2generic.DependencyTree.Or;
import owl.translations.fgx2generic.DependencyTree.Type;

class DependencyTreeFactory extends DefaultVisitor<DependencyTree> {

  private final EquivalenceClassFactory factory;
  private final ImmutableMap.Builder<Formula, EquivalenceClass> safetyMap;
  int setNumber;

  DependencyTreeFactory(Factories factory) {
    this.factory = factory.equivalenceClassFactory;
    setNumber = 0;
    safetyMap = ImmutableMap.builder();
  }

  @Override
  protected DependencyTree defaultAction(Formula formula) {
    Leaf leaf = new Leaf(formula, setNumber);

    if (leaf.type == Type.COSAFETY || leaf.type == Type.SAFETY) {
      safetyMap.put(formula, factory.createEquivalenceClass(formula));
    }

    setNumber++;
    return leaf;
  }

  ImmutableMap<Formula, EquivalenceClass> getInitialSafetyState() {
    return safetyMap.build();
  }

  @Override
  public DependencyTree visit(Conjunction conjunction) {
    List<DependencyTree> children = new ArrayList<>();
    List<Formula> safety = new ArrayList<>();
    List<Formula> coSafety = new ArrayList<>();

    conjunction.forEach(x -> {
      if (Fragments.isSafety(x)) {
        safety.add(x);
        return;
      }

      if (Fragments.isCoSafety(x)) {
        coSafety.add(x);
        return;
      }

      children.add(x.accept(this));
    });

    if (!safety.isEmpty()) {
      children.add(defaultAction(Conjunction.create(safety)));
    }

    if (!coSafety.isEmpty()) {
      children.add(defaultAction(Conjunction.create(coSafety)));
    }

    return new And(children);
  }

  @Override
  public DependencyTree visit(Disjunction disjunction) {
    List<DependencyTree> children = new ArrayList<>();
    List<Formula> safety = new ArrayList<>();
    List<Formula> coSafety = new ArrayList<>();

    disjunction.forEach(x -> {
      if (Fragments.isSafety(x)) {
        safety.add(x);
        return;
      }

      if (Fragments.isCoSafety(x)) {
        coSafety.add(x);
        return;
      }

      children.add(x.accept(this));
    });

    if (!safety.isEmpty()) {
      children.add(defaultAction(Disjunction.create(safety)));
    }

    if (!coSafety.isEmpty()) {
      children.add(defaultAction(Disjunction.create(coSafety)));
    }

    return new Or(children);
  }
}
