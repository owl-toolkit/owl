/*
 * Copyright (C) 2021, 2022  (Salomon Sickert)
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

package owl.automaton.acceptance.transformer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.sat.Solver;

public final class ZielonkaDag {

  private final PropositionalFormula<Integer> alpha;
  private final Map<ImmutableBitSet, List<ImmutableBitSet>> dag;

  public ZielonkaDag(PropositionalFormula<Integer> alpha) {
    this.alpha = alpha;
    this.dag = new HashMap<>();
    this.dag.put(ImmutableBitSet.of(), List.of());
  }

  public PropositionalFormula<Integer> alpha() {
    return alpha;
  }

  public List<ImmutableBitSet> children(ImmutableBitSet node) {
    var children = dag.get(node);

    if (children != null) {
      return children;
    }

    // Invert acceptance condition (alpha) in order to obtain alternation in DAG.
    var maximalModels = Solver.DEFAULT_MAXIMAL_MODELS.maximalModels(
        alpha.evaluate(node) ? PropositionalFormula.Negation.of(alpha) : alpha, node);
    var maximalModelsAsImmutableBitSets = new ImmutableBitSet[maximalModels.size()];

    for (int i = 0, s = maximalModels.size(); i < s; i++) {
      maximalModelsAsImmutableBitSets[i] = ImmutableBitSet.copyOf(maximalModels.get(i));
    }

    assert Stream.of(maximalModelsAsImmutableBitSets)
        .allMatch(successor -> alpha.evaluate(node) != alpha.evaluate(successor));

    // Sort colour sets lexicographically. This ensures that we always compute
    // the same Zielonka dag for a given acceptance condition.
    Arrays.sort(maximalModelsAsImmutableBitSets);

    // Make immutable copy.
    var successors = List.of(maximalModelsAsImmutableBitSets);

    dag.put(node, successors);
    return successors;
  }
}
