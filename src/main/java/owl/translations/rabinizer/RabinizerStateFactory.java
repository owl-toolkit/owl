/*
 * Copyright (C) 2017, 2022  (Tobias Meggendorfer, Salomon Sickert)
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

package owl.translations.rabinizer;

import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.edge.Edge;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Converter;
import owl.ltl.visitors.Visitor;

class RabinizerStateFactory {

  final boolean eager;

  RabinizerStateFactory(boolean eager) {
    this.eager = eager;
  }

  BitSet getClassSensitiveAlphabet(EquivalenceClass equivalenceClass) {
    return eager
        ? equivalenceClass.atomicPropositions(false)
        : equivalenceClass.unfold().atomicPropositions(false);
  }

  static final class ProductStateFactory extends RabinizerStateFactory {

    ProductStateFactory(boolean eager) {
      super(eager);
    }

    BitSet getSensitiveAlphabet(RabinizerState state) {
      BitSet sensitiveAlphabet = getClassSensitiveAlphabet(state.masterState());
      for (MonitorState monitorState : state.monitorStates()) {
        for (EquivalenceClass rankedFormula : monitorState.formulaRanking()) {
          sensitiveAlphabet.or(getClassSensitiveAlphabet(rankedFormula));
        }
      }
      return sensitiveAlphabet;
    }
  }

  static final class MasterStateFactory extends RabinizerStateFactory {

    private final boolean fairnessFragment;

    MasterStateFactory(boolean eager, boolean fairnessFragment) {
      super(eager);
      assert !fairnessFragment || eager;
      this.fairnessFragment = fairnessFragment;
    }

    EquivalenceClass initialState(EquivalenceClass formula) {
      return eager ? formula.unfold() : formula;
    }

    MtBdd<Edge<EquivalenceClass>> edgeTree(EquivalenceClass state) {
      MtBdd<EquivalenceClass> successorTree;

      if (eager) {
        if (fairnessFragment) {
          successorTree = MtBdd.of(state);
        } else {
          successorTree = state.temporalStepTree()
              .map(x -> Collections3.transformSet(x, EquivalenceClass::unfold));
        }
      } else {
        successorTree = state.unfold().temporalStepTree();
      }

      // If the master moves into false, there is no way of accepting, since the finite prefix
      // of the word already violates the formula. Hence, we refrain from creating this state.
      return successorTree.map(x -> {
        var element = Iterables.getOnlyElement(x);
        return element.isFalse() ? Set.of() : Set.of(Edge.of(element));
      });
    }
  }

  static final class MonitorStateFactory extends RabinizerStateFactory {

    private static final Visitor<Formula> substitutionVisitor = new MonitorUnfoldVisitor();
    private static final Function<Formula, Formula> unfolding = f -> f.accept(substitutionVisitor);
    private final boolean noSubFormula;

    MonitorStateFactory(boolean eager, boolean noSubFormula) {
      super(eager);
      this.noSubFormula = noSubFormula;
    }

    static boolean isAccepting(EquivalenceClass equivalenceClass, GSet context) {
      return context.conjunction().implies(equivalenceClass);
    }

    static boolean isSink(EquivalenceClass equivalenceClass) {
      // A class is a sink if all support elements are G operators. In this case, any unfold +
      // temporal step will not change the formula substantially. Note that if the equivalence class
      // is tt or ff, this also returns true, since the support is empty (hence the "for all"
      // trivially holds).
      return equivalenceClass.support(false).stream().allMatch(GOperator.class::isInstance);
    }

    EquivalenceClass getInitialState(EquivalenceClass formula) {
      return eager ? formula.substitute(unfolding) : formula;
    }

    EquivalenceClass getRankSuccessor(EquivalenceClass equivalenceClass, BitSet valuation) {
      if (noSubFormula) {
        return eager
            ? equivalenceClass.temporalStep(valuation).unfold()
            : equivalenceClass.unfold().temporalStep(valuation);
      }

      return eager
          ? equivalenceClass.temporalStep(valuation).substitute(unfolding)
          : equivalenceClass.substitute(unfolding).temporalStep(valuation);
    }

    BitSet getSensitiveAlphabet(MonitorState state) {
      List<EquivalenceClass> ranking = state.formulaRanking();
      if (ranking.isEmpty()) {
        return new BitSet(0);
      }

      Iterator<EquivalenceClass> iterator = ranking.iterator();
      BitSet sensitiveAlphabet = getClassSensitiveAlphabet(iterator.next());
      while (iterator.hasNext()) {
        sensitiveAlphabet.or(getClassSensitiveAlphabet(iterator.next()));
      }
      return sensitiveAlphabet;
    }

    private static final class MonitorUnfoldVisitor extends Converter {

      MonitorUnfoldVisitor() {
        super(SyntacticFragment.FGMU);
      }
      /*
       * This (currently) is needed to do monitor state unfolding. A substitution visitor akin to
       * f -> f instanceof GOperator ? f : f.unfold() is not sufficient, as the G operators get
       * unfolded if they are nested (for example (a U G b) is unfolded to (a & a U G b | G b & b))
       */

      @Override
      public Formula visit(GOperator gOperator) {
        return gOperator;
      }

      @Override
      public Formula visit(XOperator xOperator) {
        return xOperator;
      }

      @Override
      public Formula visit(FOperator fOperator) {
        return Disjunction.of(fOperator.operand().accept(this), fOperator);
      }

      @Override
      public Formula visit(UOperator uOperator) {
        return Disjunction.of(uOperator.rightOperand().accept(this),
            Conjunction.of(uOperator.leftOperand().accept(this), uOperator));
      }

      @Override
      public Formula visit(MOperator mOperator) {
        return Conjunction.of(mOperator.rightOperand().accept(this),
            Disjunction.of(mOperator.leftOperand().accept(this), mOperator));
      }
    }
  }
}
