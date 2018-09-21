/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.jni;

import static owl.ltl.SyntacticFragment.CO_SAFETY;
import static owl.ltl.SyntacticFragment.SAFETY;
import static owl.ltl.SyntacticFragment.SINGLE_STEP;
import static owl.ltl.SyntacticFragments.isDetBuchiRecognisable;
import static owl.ltl.SyntacticFragments.isDetCoBuchiRecognisable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.PullUpXVisitor;

final class FormulaPartition {
  final Clusters cosafetyClusters = new Clusters();
  final List<Formula> dba = new ArrayList<>();
  final List<Formula> dca = new ArrayList<>();
  final List<Formula> dpa = new ArrayList<>();
  final Clusters safetyClusters = new Clusters();
  final Map<Integer, Clusters> singleStepSafetyClusters = new HashMap<>();

  Set<Formula> safety() {
    Set<Formula> safety = new HashSet<>();
    safetyClusters.clusterList.forEach(safety::addAll);
    singleStepSafetyClusters.forEach((z, x) -> x.clusterList.forEach(safety::addAll));
    return safety;
  }

  Set<Formula> cosafety() {
    Set<Formula> cosafety = new HashSet<>();
    cosafetyClusters.clusterList.forEach(cosafety::addAll);
    return cosafety;
  }

  static FormulaPartition of(Collection<Formula> input) {
    FormulaPartition partition = new FormulaPartition();

    for (Formula x : input) {
      if (SAFETY.contains(x)) {
        PullUpXVisitor.XFormula rewrittenX = x.accept(PullUpXVisitor.INSTANCE);

        if (isSingleStep(rewrittenX.rawFormula())) {
          partition.singleStepSafetyClusters
            .computeIfAbsent(rewrittenX.depth(), ignore -> new Clusters())
            .insert(XOperator.of(rewrittenX.rawFormula(), rewrittenX.depth()));
        } else {
          partition.safetyClusters.insert(x);
        }
      } else if (CO_SAFETY.contains(x)) {
        partition.cosafetyClusters.insert(x);
      } else if (isDetBuchiRecognisable(x)) {
        partition.dba.add(x);
      } else if (isDetCoBuchiRecognisable(x)) {
        partition.dca.add(x);
      } else {
        partition.dpa.add(x);
      }
    }

    return partition;
  }

  private static boolean isSingleStep(Formula formula) {
    if (formula instanceof Conjunction) {
      return ((Conjunction) formula).children.stream().allMatch(FormulaPartition::isSingleStep);
    }

    return formula instanceof GOperator && SINGLE_STEP.contains(((GOperator) formula).operand);
  }

  static class Clusters {
    private static final Predicate<Formula> INTERESTING_OPERATOR =
    o -> o instanceof Formula.ModalOperator && !(o instanceof XOperator);

    List<Set<Formula>> clusterList = new ArrayList<>();

    void insert(Formula formula) {
      Set<Formula> cluster = new HashSet<>();
      cluster.add(formula);

      clusterList.removeIf(x -> {
        Set<Formula.TemporalOperator> modalOperators1 = formula.subformulas(INTERESTING_OPERATOR);
        Set<Formula.TemporalOperator> modalOperators2 = x.stream()
          .flatMap(y -> y.subformulas(INTERESTING_OPERATOR).stream()).collect(Collectors.toSet());

        if (!Collections.disjoint(modalOperators1, modalOperators2)) {
          cluster.addAll(x);
          return true;
        }

        return false;
      });

      clusterList.add(cluster);
    }
  }
}
