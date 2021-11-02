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

package owl.ltl.rewriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.algorithms.LanguageAnalysis;
import owl.ltl.visitors.Converter;
import owl.ltl.visitors.PropositionalVisitor;

public class PropositionalSimplifier extends Converter {

  public static final PropositionalSimplifier INSTANCE = new PropositionalSimplifier();

  private static final boolean DISABLE_EXPENSIVE_ASSERT = true;

  // TODO: Construct BDD and check support. If elements are not present, replace them by false.
  protected PropositionalSimplifier() {
    super(SyntacticFragment.ALL);
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    var newConjunction = super.visit(conjunction);

    if (!(newConjunction instanceof Conjunction)) {
      return newConjunction;
    }

    // Propagate units.
    List<Formula> combined = new ArrayList<>();
    Set<Formula> trueUnits = new HashSet<>();
    Set<Formula> falseUnits = new HashSet<>();

    for (Formula formula : newConjunction.operands) {
      if (formula instanceof Literal || formula instanceof Formula.TemporalOperator) {
        if (falseUnits.contains(formula)) {
          return BooleanConstant.FALSE;
        }

        trueUnits.add(formula);
        falseUnits.add(formula.not());
      } else {
        combined.add(formula);
      }
    }

    combined.replaceAll(
      combinedFormula -> combinedFormula.accept(new ConjunctionVisitor(trueUnits, falseUnits)));
    combined.addAll(trueUnits);
    newConjunction = Conjunction.of(combined);

    // Pull up operators that are shared by all conjuncts.
    if (newConjunction instanceof Conjunction
      && newConjunction.operands.stream().allMatch(Disjunction.class::isInstance)) {
      List<Formula> candidates = null;

      for (Formula operand : newConjunction.operands) {
        assert operand instanceof Disjunction;

        if (candidates == null) {
          candidates = new ArrayList<>(operand.operands);
        } else {
          candidates.removeIf(x -> !operand.operands.contains(x));
        }

        if (candidates.isEmpty()) {
          candidates = null;
          break;
        }
      }

      if (candidates != null) {
        var finalCandidates = candidates;

        List<Formula> prunedDisjuncts = newConjunction.operands.stream()
          .map(x -> Disjunction.of(x.operands.stream().filter(y -> !finalCandidates.contains(y))))
          .toList();

        finalCandidates.add(Conjunction.of(prunedDisjuncts));

        newConjunction = Disjunction.of(finalCandidates);
        assert DISABLE_EXPENSIVE_ASSERT || LanguageAnalysis.isEqual(conjunction, newConjunction);
        return newConjunction;
      }
    }

    assert DISABLE_EXPENSIVE_ASSERT || LanguageAnalysis.isEqual(conjunction, newConjunction);
    return newConjunction;
  }

  @Override
  public Formula visit(Disjunction disjunction) {
    var newDisjunction = super.visit(disjunction);

    if (!(newDisjunction instanceof Disjunction)) {
      return newDisjunction;
    }

    // Propagate units.
    List<Formula> combined = new ArrayList<>();
    Set<Formula> trueUnits = new HashSet<>();
    Set<Formula> falseUnits = new HashSet<>();

    for (Formula formula : newDisjunction.operands) {
      if (formula instanceof Literal || formula instanceof Formula.TemporalOperator) {
        if (falseUnits.contains(formula)) {
          return BooleanConstant.TRUE;
        }

        trueUnits.add(formula);
        falseUnits.add(formula.not());
      } else {
        combined.add(formula);
      }
    }

    combined.replaceAll(
      combinedFormula -> combinedFormula.accept(new DisjunctionVisitor(trueUnits, falseUnits)));
    combined.addAll(trueUnits);
    newDisjunction = Disjunction.of(combined);

    if (newDisjunction instanceof Disjunction
      && newDisjunction.operands.stream().allMatch(Conjunction.class::isInstance)) {
      List<Formula> candidates = null;

      for (Formula operand : newDisjunction.operands) {
        assert operand instanceof Conjunction;

        if (candidates == null) {
          candidates = new ArrayList<>(operand.operands);
        } else {
          candidates.removeIf(x -> !operand.operands.contains(x));
        }

        if (candidates.isEmpty()) {
          candidates = null;
          break;
        }
      }

      if (candidates != null) {
        var finalCandidates = candidates;

        List<Formula> prunedConjuncts = newDisjunction.operands.stream()
          .map(x -> Conjunction.of(x.operands.stream().filter(y -> !finalCandidates.contains(y))))
          .toList();

        finalCandidates.add(Disjunction.of(prunedConjuncts));

        newDisjunction = Conjunction.of(finalCandidates);
        assert DISABLE_EXPENSIVE_ASSERT || LanguageAnalysis.isEqual(disjunction, newDisjunction);
        return newDisjunction;
      }
    }

    assert DISABLE_EXPENSIVE_ASSERT || LanguageAnalysis.isEqual(disjunction, newDisjunction);
    return newDisjunction;
  }

  private static class ConjunctionVisitor extends PropositionalVisitor<Formula> {

    private final Set<Formula> trueUnits;
    private final Set<Formula> falseUnits;

    public ConjunctionVisitor(Set<Formula> trueUnits, Set<Formula> falseUnits) {
      this.trueUnits = trueUnits;
      this.falseUnits = falseUnits;
    }

    @Override
    protected Formula visit(Formula.TemporalOperator formula) {
      if (trueUnits.contains(formula)
        || (formula instanceof FOperator
          && trueUnits.contains(((FOperator) formula).operand()))
        || (formula instanceof UOperator
          && trueUnits.contains(((UOperator) formula).rightOperand()))
        || (formula instanceof WOperator
          && trueUnits.contains(((WOperator) formula).rightOperand()))) {
        return BooleanConstant.TRUE;
      }

      if (falseUnits.contains(formula)
        || (formula instanceof GOperator)
          && falseUnits.contains(((GOperator) formula).operand())
        || (formula instanceof ROperator)
          && falseUnits.contains(((ROperator) formula).rightOperand())
        || (formula instanceof MOperator)
          && falseUnits.contains(((MOperator) formula).rightOperand())) {
        return BooleanConstant.FALSE;
      }

      return formula;
    }

    @Override
    public Formula visit(Literal literal) {
      if (trueUnits.contains(literal)) {
        return BooleanConstant.TRUE;
      }

      if (falseUnits.contains(literal)) {
        return BooleanConstant.FALSE;
      }

      return literal;
    }

    @Override
    public Formula visit(Biconditional biconditional) {
      return Biconditional.of(
        biconditional.leftOperand().accept(this),
        biconditional.rightOperand().accept(this));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Negation negation) {
      return new Negation(negation.operand().accept(this));
    }
  }

  private static class DisjunctionVisitor extends PropositionalVisitor<Formula> {

    private final Set<Formula> trueUnits;
    private final Set<Formula> falseUnits;

    public DisjunctionVisitor(Set<Formula> trueUnits, Set<Formula> falseUnits) {
      this.trueUnits = trueUnits;
      this.falseUnits = falseUnits;
    }

    @Override
    protected Formula visit(Formula.TemporalOperator formula) {
      if (trueUnits.contains(formula)
        || (formula instanceof GOperator
        && trueUnits.contains(((GOperator) formula).operand()))
        || (formula instanceof ROperator
        && trueUnits.contains(((ROperator) formula).rightOperand()))
        || (formula instanceof MOperator
        && trueUnits.contains(((MOperator) formula).rightOperand()))) {
        return BooleanConstant.FALSE;
      }

      if (falseUnits.contains(formula)
        || (formula instanceof FOperator)
        && falseUnits.contains(((FOperator) formula).operand())
        || (formula instanceof UOperator)
        && falseUnits.contains(((UOperator) formula).rightOperand())
        || (formula instanceof WOperator)
        && falseUnits.contains(((WOperator) formula).rightOperand())) {
        return BooleanConstant.TRUE;
      }

      return formula;
    }

    @Override
    public Formula visit(Literal literal) {
      if (trueUnits.contains(literal)) {
        return BooleanConstant.FALSE;
      }

      if (falseUnits.contains(literal)) {
        return BooleanConstant.TRUE;
      }

      return literal;
    }

    @Override
    public Formula visit(Biconditional biconditional) {
      return Biconditional.of(
        biconditional.leftOperand().accept(this),
        biconditional.rightOperand().accept(this));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Negation negation) {
      return new Negation(negation.operand().accept(this));
    }
  }
}
