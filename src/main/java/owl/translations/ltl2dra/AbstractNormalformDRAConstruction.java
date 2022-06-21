/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

package owl.translations.ltl2dra;

import static owl.ltl.SyntacticFragments.DELTA_2;
import static owl.ltl.SyntacticFragments.FormulaClass;
import static owl.ltl.SyntacticFragments.PI_2;
import static owl.ltl.SyntacticFragments.SIGMA_2;
import static owl.ltl.SyntacticFragments.Type;
import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_PI_2_AND_FG_PI_1;
import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_SIGMA_2_AND_GF_SIGMA_1;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.rewriter.SimplifierRepository;
import owl.translations.mastertheorem.Normalisation;

class AbstractNormalformDRAConstruction {

  private static final Normalisation NORMALISATION
      = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, false);

  private static final Normalisation DUAL_NORMALISATION
      = Normalisation.of(SE20_PI_2_AND_FG_PI_1, false);

  private final boolean useDualConstruction;

  AbstractNormalformDRAConstruction(boolean useDualConstruction) {
    this.useDualConstruction = useDualConstruction;
  }

  List<Sigma2Pi2Pair> group(LabelledFormula labelledFormula) {

    // Step 1: Convert formula to DNF and transform every clause into Δ₂.
    List<Formula> delta2disjuncts = new ArrayList<>();

    for (Set<Formula> clause : NormalForms.toDnf(labelledFormula.formula())) {
      Formula conjunction = Conjunction.of(clause);

      if (DELTA_2.contains(conjunction)) {
        delta2disjuncts.add(conjunction);
        continue;
      }

      Formula normalForm = NormalForms.toDnfFormula(NORMALISATION.apply(conjunction));

      if (!useDualConstruction) {
        delta2disjuncts.add(normalForm);
        continue;
      }

      Formula dualNormalForm = NormalForms.toDnfFormula(DUAL_NORMALISATION.apply(conjunction));

      Predicate<Formula> relevantSubformulas = (Formula formula) ->
          formula instanceof Formula.TemporalOperator
              && !(formula instanceof XOperator)
              && FormulaClass.classify(formula).level() == 2;

      if (normalForm.subformulas(relevantSubformulas).size()
          <= dualNormalForm.subformulas(relevantSubformulas).size()) {
        delta2disjuncts.add(normalForm);
      } else {
        delta2disjuncts.add(dualNormalForm);
      }
    }

    Formula delta2Formula = Disjunction.of(delta2disjuncts);

    // Step 2: Group by \Sigma_2 and \Pi_2

    // (/\ \Sigma_2, /\ \Pi_2, \/ /\ \Delta_1)
    Table<Set<Formula.TemporalOperator>, Set<Formula.TemporalOperator>, Formula> table
        = HashBasedTable.create();

    if (delta2Formula.anyMatch(x -> x instanceof XOperator
        && FormulaClass.classify(x).equals(DELTA_2))) {

      delta2Formula = delta2Formula.substitute(x -> {
        if (x instanceof XOperator && FormulaClass.classify(x).equals(DELTA_2)) {
          return SimplifierRepository.PUSH_DOWN_X.apply(x);
        } else {
          return x;
        }
      });
    }

    for (Set<Formula> clause : NormalForms.toDnf(delta2Formula)) {
      // Found a contradiction, skipping clause.
      if (clause.stream().anyMatch(formula -> clause.contains(formula.not()))) {
        continue;
      }

      Set<Formula> delta1 = new HashSet<>();
      Set<Formula.TemporalOperator> sigma2 = new HashSet<>();
      Set<Formula.TemporalOperator> pi2 = new HashSet<>();

      for (Formula formula : clause) {
        FormulaClass formulaClass = FormulaClass.classify(formula);

        if (formulaClass.level() <= 1) {
          delta1.add(formula);
          continue;
        }

        assert formulaClass.level() == 2;
        assert formulaClass.type() != Type.DELTA;
        assert formula instanceof Formula.TemporalOperator;

        if (formulaClass.type() == Type.SIGMA) {
          sigma2.add((Formula.TemporalOperator) formula);
        } else {
          assert formulaClass.type() == Type.PI : formula;
          pi2.add((Formula.TemporalOperator) formula);
        }
      }

      Formula oldDelta1 = table.get(sigma2, pi2);

      if (oldDelta1 == null) {
        oldDelta1 = BooleanConstant.FALSE;
      }

      table.put(sigma2, pi2, Disjunction.of(oldDelta1, Conjunction.of(delta1)));
    }

    // Step 3: We collect clauses that are in Pi2, since a disjunction over Büchi conditions does
    // not need a round-robin counter.
    Formula globalSigma2 = BooleanConstant.FALSE;
    Formula globalPi2 = BooleanConstant.FALSE;
    Formula globalDelta1 = BooleanConstant.FALSE;

    List<String> atomicPropositions = labelledFormula.atomicPropositions();
    List<Sigma2Pi2Pair> pairs = new ArrayList<>();

    for (var cell : table.cellSet()) {
      Set<Formula.TemporalOperator> sigma2 = Objects.requireNonNull(cell.getRowKey());
      Set<Formula.TemporalOperator> pi2 = Objects.requireNonNull(cell.getColumnKey());
      Formula delta1 = Objects.requireNonNull(cell.getValue());

      if (pi2.isEmpty() && sigma2.isEmpty()) {
        assert globalDelta1.equals(BooleanConstant.FALSE);
        globalDelta1 = delta1;
        continue;
      }

      if (pi2.isEmpty()) {
        globalSigma2 = Disjunction.of(globalSigma2, Conjunction.of(Conjunction.of(sigma2), delta1));
        continue;
      }

      if (sigma2.isEmpty()) {
        globalPi2 = Disjunction.of(globalPi2, Conjunction.of(Conjunction.of(pi2), delta1));
        continue;
      }

      Formula sigma2Formula = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(
          Conjunction.of(Conjunction.of(sigma2), delta1));
      Formula pi2Formula = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(
          Conjunction.of(pi2));

      pairs.add(Sigma2Pi2Pair.of(atomicPropositions, sigma2Formula, pi2Formula));
    }

    // TODO: rewrite simplifier such that Pi2 stays Pi2, Sigma2 stays Sigma2, ...
    if (!globalSigma2.equals(BooleanConstant.FALSE)) {
      Formula sigma2 = Disjunction.of(globalSigma2, globalDelta1);
      Formula simplifiedSigma2 = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(sigma2);

      pairs.add(Sigma2Pi2Pair.of(
          atomicPropositions,
          SIGMA_2.contains(simplifiedSigma2) ? simplifiedSigma2 : sigma2,
          BooleanConstant.TRUE));
      globalDelta1 = BooleanConstant.FALSE;
    }

    // TODO: rewrite simplifier such that Pi2 stays Pi2, Sigma2 stays Sigma2, ...
    if (!globalPi2.equals(BooleanConstant.FALSE)) {
      Formula pi2 = Disjunction.of(globalPi2, globalDelta1);
      Formula simplifiedPi2 = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(pi2);

      pairs.add(Sigma2Pi2Pair.of(
          atomicPropositions,
          BooleanConstant.TRUE,
          PI_2.contains(simplifiedPi2) ? simplifiedPi2 : pi2));
      globalDelta1 = BooleanConstant.FALSE;
    }

    if (!globalDelta1.equals(BooleanConstant.FALSE)) {
      pairs.add(Sigma2Pi2Pair.of(
          atomicPropositions,
          globalDelta1,
          BooleanConstant.TRUE));
    }

    return pairs;
  }

  @AutoValue
  abstract static class Sigma2Pi2Pair {

    abstract LabelledFormula sigma2();

    abstract LabelledFormula pi2();

    static Sigma2Pi2Pair of(List<String> atomicPropositions, Formula sigma2, Formula pi2) {
      Preconditions.checkArgument(SIGMA_2.contains(sigma2),
          "Formula (%s) not in Sigma_2.".formatted(sigma2));
      Preconditions.checkArgument(PI_2.contains(pi2),
          "Formula (%s) not in Pi_2.".formatted(pi2));

      return new AutoValue_AbstractNormalformDRAConstruction_Sigma2Pi2Pair(
          LabelledFormula.of(sigma2, atomicPropositions),
          LabelledFormula.of(pi2, atomicPropositions)
      );
    }
  }
}
