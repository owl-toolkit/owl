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

package owl.cinterface;

import static owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import owl.collections.ImmutableBitSet;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Literal;

final class EquivalenceClassEncoder {

  private final Map<BreakpointStateRejecting, Integer> states = new HashMap<>();

  @Nullable
  private Map<EquivalenceClass, ImmutableBitSet> allProfiles;
  @Nullable
  private Map<EquivalenceClass, ImmutableBitSet> rejectingProfiles;

  void put(BreakpointStateRejecting state) {
    if (allProfiles != null) {
      throw new IllegalStateException("profiles already computed.");
    }

    states.put(state, -1);
  }

  void putAll(Collection<? extends BreakpointStateRejecting> newStates) {
    newStates.forEach(this::put);
  }

  ImmutableBitSet getAllProfile(BreakpointStateRejecting state) {
    initialise();
    assert allProfiles != null;
    return allProfiles.get(state.all());
  }

  ImmutableBitSet getRejectingProfile(BreakpointStateRejecting state) {
    initialise();
    assert rejectingProfiles != null;
    return rejectingProfiles.get(state.rejecting());
  }

  int disambiguation(BreakpointStateRejecting state) {
    initialise();
    int id = states.get(state);
    assert id >= 0;
    return id;
  }

  private static Map<EquivalenceClass, ImmutableBitSet>
    computeProfiles(Set<EquivalenceClass> classes) {

    // We re-use the map and thus specify only Set<?> as value type.
    Map<EquivalenceClass, Set<?>> profiles = new IdentityHashMap<>(classes.size());
    NavigableMap<Formula, Integer> numbering = new TreeMap<>(Comparator.reverseOrder());

    // Populate maps.
    for (EquivalenceClass clazz : classes) {
      var profile = new HashSet<Formula>(clazz.temporalOperators(false));

      // Project temporalOperators and determine relevant atomicPropositions with different signs
      // for different projections.
      clazz.substitute(x2 -> BooleanConstant.TRUE).atomicPropositions(false).stream()
        .forEach((int x) -> profile.add(Literal.of(x, false)));
      clazz.substitute(x1 -> BooleanConstant.FALSE).atomicPropositions(false).stream()
        .forEach((int x) -> profile.add(Literal.of(x, true)));

      profiles.put(clazz, profile);

      for (Formula formula : profile) {
        assert formula instanceof Literal || formula instanceof Formula.TemporalOperator;
        numbering.compute(formula, (y, counter) -> counter == null ? 1 : counter + 1);
      }
    }

    // Generate numbering and remove temporal operators that appear in every profile.
    {
      int size = profiles.size();
      int counter = 0;
      var iterator = numbering.entrySet().iterator();

      while (iterator.hasNext()) {
        var entry = iterator.next();
        int value = entry.getValue();

        if (value == size) {
          iterator.remove();
        } else {
          entry.setValue(counter);
          counter++;
        }
      }
    }

    // Update profiles and replace with compact representation.
    {
      for (var entry : profiles.entrySet()) {
        BitSet bitsetProfile = new BitSet();
        Set<Formula> profile = (Set) entry.getValue();

        for (Formula formula : profile) {
          assert formula instanceof Literal || formula instanceof Formula.TemporalOperator;
          Integer value = numbering.get(formula);

          if (value != null) {
            bitsetProfile.set(value);
          }
        }

        entry.setValue(ImmutableBitSet.copyOf(bitsetProfile));
      }
    }

    // Scan for redundant variables and remove them.
    {
      int variables = numbering.size();
      BitSet redundantVariables = new BitSet();

      for (int i = 0; i < variables; i++) {
        for (int j = i + 1; j < variables; j++) {
          if (redundantVariables.get(j)) {
            continue;
          }

          boolean ijEquivalent = true;

          for (Set<?> profile : profiles.values()) {
            ImmutableBitSet castedProfile = (ImmutableBitSet) profile;

            if (castedProfile.contains(i) != castedProfile.contains(j)) {
              ijEquivalent = false;
              break;
            }
          }

          if (ijEquivalent) {
            assert !redundantVariables.get(j);
            redundantVariables.set(j);
          }
        }
      }

      int[] mapping = new int[variables];
      int offset = 0;

      for (int i = 0; i < variables; i++) {
        if (redundantVariables.get(i)) {
          offset++;
          mapping[i] = -1;
        } else {
          mapping[i] = i - offset;
          assert mapping[i] >= 0;
        }
      }

      if (!redundantVariables.isEmpty()) {
        profiles.entrySet().forEach(entry -> {
          ImmutableBitSet profile = ((ImmutableBitSet) entry.getValue());
          BitSet mappedProfile = new BitSet();
          profile.forEach((int i) -> {
            int j = mapping[i];

            if (j >= 0) {
              mappedProfile.set(j);
            }
          });
          entry.setValue(ImmutableBitSet.copyOf(mappedProfile));
        });
      }
    }

    return (Map) profiles;
  }

  private void initialise() {
    if (allProfiles != null) {
      assert rejectingProfiles != null;
      return;
    }

    Set<EquivalenceClass> allClasses = new HashSet<>(states.size());
    Set<EquivalenceClass> rejectingClasses = new HashSet<>(states.size());

    for (BreakpointStateRejecting state : states.keySet()) {
      allClasses.add(state.all());
      rejectingClasses.add(state.rejecting());
    }

    allProfiles = computeProfiles(allClasses);
    rejectingProfiles = computeProfiles(rejectingClasses);

    Table<ImmutableBitSet, ImmutableBitSet, List<BreakpointStateRejecting>>
      ambiguousProfiles = HashBasedTable.create(allProfiles.size(), rejectingProfiles.size());

    for (BreakpointStateRejecting state : states.keySet()) {
      var allProfile = allProfiles.get(state.all());
      var rejectingProfile = rejectingProfiles.get(state.rejecting());
      var ambiguous = ambiguousProfiles.get(allProfile, rejectingProfile);

      if (ambiguous == null) {
        ambiguous = new ArrayList<>();
        ambiguousProfiles.put(allProfile, rejectingProfile, ambiguous);
      }

      ambiguous.add(state);
    }

    for (List<BreakpointStateRejecting> ambiguous : ambiguousProfiles.values()) {
      int counter = 0;

      for (BreakpointStateRejecting state : ambiguous) {
        var oldValue = states.put(state, counter);
        assert oldValue != null && oldValue == -1;
        counter++;
      }
    }
  }
}
