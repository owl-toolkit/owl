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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.collections.Lists2;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.visitors.XDepthVisitor;

abstract class DependencyTree {

  abstract BooleanExpression<AtomAcceptance> getAcceptanceExpression();

  abstract BitSet getEdgeAcceptance(ProductState state, BitSet valuation);

  abstract int getMaxRequiredHistoryLength();

  abstract List<BitSet> getRequiredHistory(Map<Formula, EquivalenceClass> safetyStates);

  enum Type {
    SAFETY, COSAFETY, LIMIT_FG, LIMIT_GF
  }

  static class And extends Node {
    And(List<DependencyTree> children) {
      super(children);
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      return getAcceptanceExpressionStream().reduce(BooleanExpression::and)
        .orElse(new BooleanExpression<>(true));
    }

    @Override
    boolean shortCircuit(Leaf leaf, Map<Formula, EquivalenceClass> safetyStates) {
      EquivalenceClass clazz = safetyStates.get(leaf.formula);
      return Fragments.isX(leaf.formula) && !clazz.isFalse() && !clazz.isTrue()
        || leaf.type == Type.SAFETY && clazz.isFalse()
        || leaf.type == Type.COSAFETY && !clazz.isTrue();
    }
  }

  static class Leaf extends DependencyTree {
    final int acceptanceSet;
    final Formula formula;
    final Type type;

    Leaf(Formula formula, int acceptanceSet) {
      this.formula = formula;
      this.acceptanceSet = acceptanceSet;

      if (Fragments.isSafety(formula)) {
        type = Type.SAFETY;
      } else if (Fragments.isCoSafety(formula)) {
        type = Type.COSAFETY;
      } else if (Fragments.isAlmostAll(formula)) {
        assert Fragments.isFgx(formula);
        type = Type.LIMIT_FG;
      } else {
        assert Fragments.isInfinitelyOften(formula);
        assert Fragments.isFgx(formula);
        type = Type.LIMIT_GF;
      }
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      AtomAcceptance acceptance;

      if (type == Type.LIMIT_GF) {
        acceptance = AtomAcceptance.Inf(acceptanceSet);
      } else {
        acceptance = AtomAcceptance.Fin(acceptanceSet);
      }

      return new BooleanExpression<>(acceptance);
    }

    @Override
    BitSet getEdgeAcceptance(ProductState state, BitSet valuation) {
      BitSet acceptance = new BitSet();
      boolean inSet = false;
      Formula unwrapped;

      switch (type) {
        case SAFETY:
          inSet = state.safetyStates.get(formula).isFalse();
          break;

        case COSAFETY:
          inSet = !state.safetyStates.get(formula).isTrue();
          break;

        case LIMIT_GF:
          unwrapped = Util.unwrap(formula);
          inSet = SatisfactionRelation.models(Lists2.cons(valuation, state.history), unwrapped);
          break;

        case LIMIT_FG:
          unwrapped = Util.unwrap(formula);
          inSet = !SatisfactionRelation.models(Lists2.cons(valuation, state.history), unwrapped);
          break;

        default:
          assert false;
          break;
      }

      if (inSet) {
        acceptance.set(acceptanceSet);
      }

      return acceptance;
    }

    @Override
    int getMaxRequiredHistoryLength() {
      if (type == Type.COSAFETY || type == Type.SAFETY) {
        return 0;
      }

      return XDepthVisitor.getDepth(formula);
    }

    @Override
    List<BitSet> getRequiredHistory(Map<Formula, EquivalenceClass> safetyStates) {
      if (type == Type.COSAFETY || type == Type.SAFETY || XDepthVisitor.getDepth(formula) == 0) {
        return new ArrayList<>();
      }

      return RequiredHistory.getRequiredHistory(Util.unwrap(formula));
    }
  }

  abstract static class Node extends DependencyTree {
    final ImmutableList<DependencyTree> children;

    Node(List<DependencyTree> children) {
      this.children = ImmutableList.copyOf(children);
    }

    Stream<BooleanExpression<AtomAcceptance>> getAcceptanceExpressionStream() {
      return children.stream().map(DependencyTree::getAcceptanceExpression);
    }

    @Override
    BitSet getEdgeAcceptance(ProductState state, BitSet valuation) {
      BitSet acceptance = new BitSet();
      children.forEach(x -> acceptance.or(x.getEdgeAcceptance(state, valuation)));
      return acceptance;
    }

    @Override
    int getMaxRequiredHistoryLength() {
      return children.stream().mapToInt(DependencyTree::getMaxRequiredHistoryLength).max()
        .orElse(0);
    }

    @Override
    List<BitSet> getRequiredHistory(Map<Formula, EquivalenceClass> safetyStates) {
      List<BitSet> requiredHistory = new ArrayList<>();

      for (DependencyTree child : children) {
        if (child instanceof Leaf && shortCircuit((Leaf) child, safetyStates)) {
          return new ArrayList<>();
        }

        Util.union(requiredHistory, child.getRequiredHistory(safetyStates));
      }

      return requiredHistory;
    }

    abstract boolean shortCircuit(Leaf leaf, Map<Formula, EquivalenceClass> safetyState);
  }

  static class Or extends Node {
    Or(List<DependencyTree> children) {
      super(children);
    }

    @Override
    BooleanExpression<AtomAcceptance> getAcceptanceExpression() {
      return getAcceptanceExpressionStream().reduce(BooleanExpression::or)
        .orElse(new BooleanExpression<>(false));
    }

    @Override
    boolean shortCircuit(Leaf leaf, Map<Formula, EquivalenceClass> safetyStates) {
      EquivalenceClass clazz = safetyStates.get(leaf.formula);
      return Fragments.isX(leaf.formula) && !clazz.isFalse() && !clazz.isTrue()
        || leaf.type == Type.SAFETY && !clazz.isFalse()
        || leaf.type == Type.COSAFETY && clazz.isTrue();
    }
  }
}
