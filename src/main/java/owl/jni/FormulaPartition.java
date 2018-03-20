package owl.jni;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.visitors.Collector;

final class FormulaPartition {
  final List<Set<Formula>> cosafety = new ArrayList<>();
  final List<Formula> dba = new ArrayList<>();
  final List<Formula> dca = new ArrayList<>();
  final List<Formula> mixed = new ArrayList<>();
  final List<Set<Formula>> safety = new ArrayList<>();
  final List<Formula> singleStepSafety = new ArrayList<>();

  Set<Formula> safety() {
    return Sets.newHashSet(Iterables.concat(singleStepSafety, Iterables.concat(safety)));
  }

  Set<Formula> cosafety() {
    return Sets.newHashSet(Iterables.concat(cosafety));
  }

  static FormulaPartition of(Set<Formula> input) {
    FormulaPartition partition = new FormulaPartition();

    input.forEach(x -> {
      if (Fragments.isSafety(x)) {
        if (x instanceof GOperator && Fragments.isSingleStep(((GOperator) x).operand)) {
          partition.singleStepSafety.add(x);
        } else {
          partition.safety.add(merge(partition.safety, x));
        }
      } else if (Fragments.isCoSafety(x)) {
        partition.cosafety.add(merge(partition.cosafety, x));
      } else if (Fragments.isDetBuchiRecognisable(x)) {
        partition.dba.add(x);
      } else if (Fragments.isDetCoBuchiRecognisable(x)) {
        partition.dca.add(x);
      } else {
        partition.mixed.add(x);
      }
    });

    return partition;
  }

  private static Set<Formula> merge(List<Set<Formula>> formulas, Formula formula) {
    Set<Formula> toBeMerged = new HashSet<>();
    toBeMerged.add(formula);

    formulas.removeIf(x -> {
      if (isIndependent(x, formula)) {
        return false;
      }

      toBeMerged.addAll(x);
      return true;
    });

    return toBeMerged;
  }

  private static boolean isIndependent(Iterable<Formula> x, Formula y) {
    return !Collector.collectAtoms(x).intersects(Collector.collectAtoms(y));
  }
}