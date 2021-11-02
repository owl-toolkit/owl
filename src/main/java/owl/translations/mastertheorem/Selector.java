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

package owl.translations.mastertheorem;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Fixpoint;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragments;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.visitors.Visitor;

public final class Selector {

  private Selector() {}

  public static Set<Fixpoints> selectAsymmetric(Formula formula, boolean all) {
    if (all) {
      return Sets.powerSet(selectGreatestFixpoints(formula))
        .stream()
        .map(x -> Fixpoints.of(Set.of(), x))
        .collect(toSet());
    } else {
      return NormalForms
        .toDnf(formula, NormalForms.SYNTHETIC_CO_SAFETY_LITERAL)
        .stream()
        .flatMap(Selector::selectAsymmetricFromClause)
        .collect(toSet());
    }
  }

  public static Set<Fixpoints> selectSymmetric(Formula formula, boolean all) {
    if (all) {
      return Sets.powerSet(selectAllFixpoints(formula))
        .stream()
        .map(Fixpoints::of)
        .collect(toSet());
    } else {
      return NormalForms
        .toDnf(formula, NormalForms.SYNTHETIC_CO_SAFETY_LITERAL)
        .stream()
        .flatMap(Selector::selectSymmetricFromClause)
        .collect(toSet());
    }
  }

  private static Stream<Fixpoints> selectAsymmetricFromClause(Set<Formula> clause) {
    List<Set<Set<Fixpoint.GreatestFixpoint>>> elementSets = new ArrayList<>();

    for (Formula element : clause) {
      assert isClauseElement(element);

      var fixpoints = selectGreatestFixpoints(element);

      if (!fixpoints.isEmpty()) {
        elementSets.add(Sets.powerSet(fixpoints));
      }
    }

    List<Fixpoints> fixpointsList = new ArrayList<>();

    for (List<Set<Fixpoint.GreatestFixpoint>> combination : Sets.cartesianProduct(elementSets)) {
      Set<Fixpoint.GreatestFixpoint> union = new HashSet<>();
      combination.forEach(union::addAll);
      fixpointsList.add(Fixpoints.of(Set.of(), union));
    }

    return fixpointsList.stream();
  }

  private static Stream<Fixpoints> selectSymmetricFromClause(Set<Formula> clause) {
    List<Fixpoints> fixpointsList = new ArrayList<>();
    List<Set<Set<Formula.TemporalOperator>>> elementSets = new ArrayList<>();

    for (Formula element : clause) {
      assert isClauseElement(element);

      if (SyntacticFragments.isCoSafety(element)) {
        continue;
      }

      Set<Formula.TemporalOperator> fixpoints = new HashSet<>();
      UnscopedVisitor visitor = new UnscopedVisitor(fixpoints);
      element.accept(visitor);
      elementSets.add(Sets.powerSet(fixpoints));
    }

    for (List<Set<Formula.TemporalOperator>> combination : Sets.cartesianProduct(elementSets)) {
      Set<Formula.TemporalOperator> union = new HashSet<>();
      combination.forEach(union::addAll);
      fixpointsList.add(Fixpoints.of(union));
    }

    return fixpointsList.stream();
  }

  private static boolean isClauseElement(Formula formula) {
    return SyntacticFragments.isCoSafety(formula)
      || formula instanceof Formula.TemporalOperator;
  }

  private static Set<Formula.TemporalOperator> selectAllFixpoints(
    Formula formula) {
    return formula.subformulas(Fixpoint.class::isInstance,
      Formula.TemporalOperator.class::cast);
  }

  private static Set<Fixpoint.GreatestFixpoint> selectGreatestFixpoints(
    Formula formula) {
    return formula.subformulas(
      Fixpoint.GreatestFixpoint.class::isInstance,
      Fixpoint.GreatestFixpoint.class::cast);
  }

  private abstract static class AbstractSymmetricVisitor implements Visitor<Void> {
    @Override
    public Void visit(Conjunction conjunction) {
      conjunction.operands.forEach(x -> x.accept(this));
      return null;
    }

    @Override
    public Void visit(Disjunction disjunction) {
      disjunction.operands.forEach(x -> x.accept(this));
      return null;
    }

    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    @Override
    public final Void visit(Literal literal) {
      return null;
    }

    @Override
    public final Void visit(XOperator xOperator) {
      return xOperator.operand().accept(this);
    }
  }

  private static final class UnscopedVisitor extends AbstractSymmetricVisitor {
    private final GScopedVisitor gScopedVisitor;

    private UnscopedVisitor(Set<Formula.TemporalOperator> fixpoints) {
      gScopedVisitor = new GScopedVisitor(fixpoints);
    }

    @Override
    public Void visit(FOperator fOperator) {
      return fOperator.operand().accept(this);
    }

    @Override
    public Void visit(GOperator gOperator) {
      return gOperator.operand().accept(gScopedVisitor);
    }

    @Override
    public Void visit(MOperator mOperator) {
      mOperator.leftOperand().accept(this);
      mOperator.rightOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(ROperator rOperator) {
      rOperator.leftOperand().accept(this);
      rOperator.rightOperand().accept(gScopedVisitor);
      return null;
    }

    @Override
    public Void visit(UOperator uOperator) {
      uOperator.leftOperand().accept(this);
      uOperator.rightOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(WOperator wOperator) {
      wOperator.leftOperand().accept(gScopedVisitor);
      wOperator.rightOperand().accept(this);
      return null;
    }
  }

  private static class GScopedVisitor extends AbstractSymmetricVisitor {
    private final Set<Formula.TemporalOperator> fixpoints;

    private GScopedVisitor(Set<Formula.TemporalOperator> fixpoints) {
      this.fixpoints = fixpoints;
    }

    @Override
    public Void visit(FOperator fOperator) {
      fixpoints.addAll(selectAllFixpoints(fOperator));
      return null;
    }

    @Override
    public Void visit(GOperator gOperator) {
      return gOperator.operand().accept(this);
    }

    @Override
    public Void visit(MOperator mOperator) {
      fixpoints.addAll(selectAllFixpoints(mOperator));
      return null;
    }

    @Override
    public Void visit(ROperator rOperator) {
      rOperator.leftOperand().accept(this);
      rOperator.rightOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(UOperator uOperator) {
      fixpoints.addAll(selectAllFixpoints(uOperator));
      return null;
    }

    @Override
    public Void visit(WOperator wOperator) {
      wOperator.leftOperand().accept(this);
      wOperator.rightOperand().accept(this);
      return null;
    }
  }
}
