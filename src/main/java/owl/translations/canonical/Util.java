/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.canonical;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.immutables.value.Value;
import owl.collections.ValuationTree;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.ltl.UnaryModalOperator;
import owl.util.annotation.Tuple;

class Util {

  private Util() {}

  static ValuationTree<BitSet> singleStepTree(List<Formula> singleSteps) {
    return singleStepTreeRecursive(singleSteps, new HashMap<>());
  }

  private static ValuationTree<BitSet> singleStepTreeRecursive(List<Formula> singleSteps,
    Map<List<Formula>, ValuationTree<BitSet>> cache) {
    ValuationTree<BitSet> result = cache.get(singleSteps);

    if (result != null) {
      return result;
    }

    int nextVariable = -1;

    for (Formula x : singleSteps) {
      int i = x.atomicPropositions(false).nextSetBit(0);
      if (0 <= i && (nextVariable < 0 || i < nextVariable)) {
        nextVariable = i;
      }
    }

    if (nextVariable == -1) {
      BitSet acceptance = new BitSet();

      for (int i = 0; i < singleSteps.size(); i++) {
        if (((BooleanConstant) singleSteps.get(i)).value) {
          acceptance.set(i + 1);
        }
      }

      result = ValuationTree.of(Set.of(acceptance));
    } else {
      int variable = nextVariable;

      Formula[] trueSingleSteps = new Formula[singleSteps.size()];
      Formula[] falseSingleSteps = new Formula[singleSteps.size()];

      Arrays.setAll(trueSingleSteps, i -> singleSteps.get(i).temporalStep(variable, true));
      Arrays.setAll(falseSingleSteps, i -> singleSteps.get(i).temporalStep(variable, false));

      var trueChild = singleStepTreeRecursive(Arrays.asList(trueSingleSteps), cache);
      var falseChild = singleStepTreeRecursive(Arrays.asList(falseSingleSteps), cache);

      result = ValuationTree.of(variable, trueChild, falseChild);
    }

    cache.put(singleSteps, result);
    return result;
  }

  static Formula unwrap(Formula formula) {
    return ((UnaryModalOperator) formula).operand;
  }

  @Value.Immutable
  @Tuple
  abstract static class Pair<A, B> {
    public abstract A a();

    public abstract B b();

    public static <A, B> Pair<A, B> of(A a, B b) {
      return PairTuple.create(a, b);
    }
  }
}
