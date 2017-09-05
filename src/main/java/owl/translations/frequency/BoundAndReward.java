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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.ltl.FrequencyG;
import owl.translations.frequency.ProductControllerSynthesis.State;

/**
 * This class aims to store a bound and the transition, which have a reward
 * regarding this bound. Note that some transitions may have a reward larger
 * than one. An invariant: the TranSets as values of the Map must not intersect.
 */
public class BoundAndReward implements BoundAndRewardForPrism {
  protected final FrequencyG frequencyG;
  private final Map<Integer, TranSet<State>> reward;
  private final ValuationSetFactory vsFactory;

  public BoundAndReward(FrequencyG frequencyG, ValuationSetFactory vsFactory) {
    this.frequencyG = frequencyG;
    this.reward = new HashMap<>();
    this.vsFactory = vsFactory;
  }

  /**
   * Increases the reward of the input-transitions by amount.
   */
  private void addRewards(TranSet<State> trans, int amount) {
    Integer zero = 0;
    Set<TranSet<State>> transitionSplitted = splitIntoRelevantJunks(trans);
    Map<Integer, TranSet<State>> toRemove = new HashMap<>();
    Map<Integer, TranSet<State>> toAdd = new HashMap<>();

    // find out the new rewards
    for (TranSet<State> singleSet : transitionSplitted) {
      reward.put(zero, singleSet);
      for (Entry<Integer, TranSet<State>> entry : reward
        .entrySet()) {
        if (entry.getValue().containsAll(singleSet)) {
          if (!entry.getKey().equals(zero)) {
            toRemove.put(entry.getKey(), singleSet);
          }
          toAdd.put(entry.getKey() + amount, singleSet);
          break;
        }
      }
      reward.remove(zero);
    }

    // adjust the rewards
    for (Entry<Integer, TranSet<State>> entry : toRemove
      .entrySet()) {
      TranSet<State> temporary = reward.get(entry.getKey())
        .copy();
      temporary.removeAll(entry.getValue());
      reward.put(entry.getKey(), temporary);
    }
    for (Entry<Integer, TranSet<State>> entry : toAdd
      .entrySet()) {
      TranSet<State> temporary = reward.get(entry.getKey());
      if (temporary == null) {
        reward.put(entry.getKey(), entry.getValue());
      } else {
        temporary.addAll(entry.getValue());
        reward.put(entry.getKey(), temporary);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof BoundAndReward) {
      BoundAndReward that = (BoundAndReward) obj;
      return Objects.equals(this.frequencyG, that.frequencyG)
        && Objects.equals(this.reward, that.reward);
    }
    return false;

  }

  @Override
  public FrequencyG getFreqG() {
    return frequencyG;
  }

  public int getNumberOfRewardSets() {
    return reward.keySet().size();
  }

  @Override
  public int hashCode() {
    return Objects.hash(frequencyG, reward);
  }

  public void increaseRewards(
    Map<TranSet<State>, Integer> transitionRewards) {
    transitionRewards.entrySet().forEach(entry -> {
      if (!entry.getValue().equals(0)) {
        addRewards(entry.getKey(), entry.getValue());
      }
    });
  }

  @Override
  public Set<Entry<Integer, TranSet<State>>> relevantEntries() {
    return new HashMap<>(reward).entrySet();
  }

  private Set<TranSet<State>> splitIntoRelevantJunks(TranSet<State> trans) {
    Set<TranSet<State>> result = new HashSet<>();
    for (Entry<Integer, TranSet<State>> entry :
      reward.entrySet()) {
      if (entry.getValue().intersects(trans)) {
        TranSet<State> temp = new TranSet<>(vsFactory);
        entry.getValue().forEach(singleState -> {
          Map<State, ValuationSet> transitionMap = trans.asMap();
          if (transitionMap.containsKey(singleState)) {
            temp.addAll(singleState.getKey(), vsFactory.intersection(singleState.getValue(),
              transitionMap.get(singleState.getKey())));
          }
        });
        if (!temp.isEmpty()) {
          result.add(temp);
          trans.removeAll(temp);
        }
      }
    }
    result.forEach(set -> trans.removeAll(set));
    if (!trans.isEmpty()) {
      result.add(trans);
    }
    return result;
  }
}
