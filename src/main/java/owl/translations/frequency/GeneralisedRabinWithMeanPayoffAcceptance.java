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

package owl.translations.frequency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.translations.frequency.ProductControllerSynthesis.State;

public class GeneralisedRabinWithMeanPayoffAcceptance extends GeneralizedRabinAcceptance2<State> {
  /**
   * The difference between this class and Generalised Rabin Acceptance is
   * that each "Rabin pair" of this class has also a list of MDP-rewards,
   * which are to be fulfilled.
   */
  List<Collection<BoundAndReward>> acceptanceMdp;

  public GeneralisedRabinWithMeanPayoffAcceptance(
    List<RabinPair2<TranSet<State>,
      List<TranSet<State>>>> acceptance,
    List<Collection<BoundAndReward>> acceptanceMdp) {
    super(acceptance);
    this.acceptanceMdp = acceptanceMdp;
  }

  @Override
  protected BooleanExpression<AtomAcceptance> addInfiniteSetsToConjunction(
    BooleanExpression<AtomAcceptance> conjunction, int offset) {
    BooleanExpression<AtomAcceptance> testedConjunction =
      super.addInfiniteSetsToConjunction(conjunction, offset);
    // add some information about acceptanceMDP to the conjunction
    // the information, which we add here are necessary but not sufficient
    // for the real acceptance condition, which the hoa-Format does not
    // currently support.

    for (BoundAndReward reward : acceptanceMdp.get(offset)) {
      BooleanExpression<AtomAcceptance> disjunction = null;
      for (Entry<Integer, TranSet<State>> entry : reward
        .relevantEntries()) {
        BooleanExpression<AtomAcceptance> newSet = new BooleanExpression<>(
          new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_INF,
            acceptanceNumbers.compute(entry.getValue(), (x, y) -> acceptanceNumbers.size()),
            false));
        disjunction = (disjunction == null ? newSet : disjunction.or(newSet));
      }
      if (disjunction != null) {
        testedConjunction = testedConjunction.and(disjunction);
      }
    }
    return testedConjunction;

  }

  @Override
  public void clear() {
    super.clear();
    acceptanceMdp.clear();
  }

  @Override
  public int acceptanceSets() {
    int size = super.acceptanceSets();
    for (Collection<BoundAndReward> set : acceptanceMdp) {
      for (BoundAndReward rew : set) {
        size += rew.getNumberOfRewardSets();
      }
    }
    return size;
  }

  @Override
  public Set<ValuationSet> getMaximallyMergedEdgesOfEdge(AutomatonState<?> currentState,
    ValuationSet initialValuation) {
    Set<ValuationSet> result = super.getMaximallyMergedEdgesOfEdge(currentState, initialValuation);

    for (Collection<BoundAndReward> boundSet : this.acceptanceMdp) {
      for (BoundAndReward bound : boundSet) {
        for (Entry<Integer, TranSet<State>> entry : bound
          .relevantEntries()) {
          result = splitAccordingToAcceptanceSet(currentState, result, entry.getValue());
        }
      }
    }

    return result;
  }

  @Override
  public String name() {
    return null; // HOA does not support our acceptance type
  }

  @Override
  public List<Object> nameExtra() {
    return Collections.emptyList();
  }

  public List<Collection<BoundAndReward>> getUnmodifiableAcceptanceMdp() {
    return Collections.unmodifiableList(this.acceptanceMdp);
  }

  @Override
  public boolean implies(int premiseIndex, int conclusionIndex) {
    return super.implies(premiseIndex, conclusionIndex) && acceptanceMdp.get(premiseIndex)
      .containsAll(acceptanceMdp.get(conclusionIndex));
  }

  @Override
  public Map<String, List<Object>> miscellaneousAnnotations() {
    Map<String, List<Object>> result = new HashMap<>();
    int i = 0;
    for (Collection<BoundAndReward> set : acceptanceMdp) {
      for (BoundAndReward bound : set) {
        String name = "boundary" + i++;
        List<Object> attributes = new ArrayList<>();
        attributes.add(String.format("%s%s%s",
          bound.frequencyG.limes, bound.frequencyG.cmp, bound.frequencyG.bound));
        attributes.add("   In this context, the following sets have rewards. ");
        Set<Entry<Integer, TranSet<State>>> entries = bound
          .relevantEntries();
        for (Entry<Integer, TranSet<State>> entry : entries) {
          attributes.add(
            "set(" + acceptanceNumbers.compute(entry.getValue(), (x, y) -> acceptanceNumbers.size())
              + ")::" + entry.getKey());
        }
        result.put(name, attributes);
      }
    }
    return result;
  }

  @Override
  public void removeIndices(Set<Integer> indices) {
    super.removeIndices(indices);
    indices.stream().sorted(Collections.reverseOrder())
      .forEachOrdered(index -> acceptanceMdp.remove(index.intValue()));
  }

}
