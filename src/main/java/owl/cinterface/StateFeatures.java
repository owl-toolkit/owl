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

package owl.cinterface;

import static owl.cinterface.StateFeatures.Feature.Type;
import static owl.cinterface.StateFeatures.Feature.roundRobinCounter;
import static owl.cinterface.StateFeatures.Feature.temporalOperatorsProfileFromBitset;
import static owl.cinterface.StateFeatures.TemporalOperatorsProfileNormalForm.CNF;
import static owl.cinterface.StateFeatures.TemporalOperatorsProfileNormalForm.DNF;
import static owl.translations.canonical.DeterministicConstructions.BreakpointStateAccepting;
import static owl.translations.canonical.DeterministicConstructions.BreakpointStateAcceptingRoundRobin;

import com.google.auto.value.AutoOneOf;
import com.google.common.base.Preconditions;
import com.google.common.collect.Comparators;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Function;
import owl.collections.BitSet2;
import owl.collections.Collections3;
import owl.collections.HashTrieMap;
import owl.collections.HashTrieSet;
import owl.collections.TrieMap;
import owl.collections.TrieSet;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateRejectingRoundRobin;
import owl.translations.canonical.RoundRobinState;
import owl.translations.ltl2dpa.AsymmetricRankingState;
import owl.translations.ltl2ldba.AsymmetricProductState;

public final class StateFeatures {

  private static final Comparator<List<Formula>> SET_COMPARATOR = Comparator
      .<List<Formula>>comparingInt(List::size)
      .thenComparing(Comparators.lexicographical(Formula::compareTo));

  private StateFeatures() {
  }

  static <S> Map<S, List<Feature>> extract(Set<S> states) {

    if (states.isEmpty()) {
      return Map.of();
    }

    // We use a single element of the set to guess the type of all elements of the set.
    S sentinel = states.iterator().next();
    HashMap<?, List<Feature>> featuresMap;

    if (sentinel instanceof EquivalenceClass) {

      @SuppressWarnings("unchecked")
      var castedStates = (Set<EquivalenceClass>) states;
      featuresMap = extract(castedStates, List::of);

    } else if (sentinel instanceof RoundRobinState) {

      @SuppressWarnings("unchecked")
      var castedStates = (Set<RoundRobinState<EquivalenceClass>>) states;
      featuresMap = extract(castedStates,
          x -> List.of(roundRobinCounter(x.index()), x.state()));

    } else if (sentinel instanceof BreakpointStateAccepting) {

      @SuppressWarnings("unchecked")
      var castedStates = (Set<BreakpointStateAccepting>) states;
      featuresMap = extract(castedStates, x -> List.of(x.all(), x.accepting()));

    } else if (sentinel instanceof BreakpointStateAcceptingRoundRobin) {

      @SuppressWarnings("unchecked")
      var castedStates = (Set<BreakpointStateAcceptingRoundRobin>) states;
      featuresMap = extract(castedStates, x -> List.of(x.all(), x.accepting()));

    } else if (sentinel instanceof BreakpointStateRejecting) {

      @SuppressWarnings("unchecked")
      var castedStates = (Set<BreakpointStateRejecting>) states;
      featuresMap = extract(castedStates, x -> List.of(x.all(), x.rejecting()));

    } else if (sentinel instanceof BreakpointStateRejectingRoundRobin) {

      @SuppressWarnings("unchecked")
      var castedStates = (Set<BreakpointStateRejectingRoundRobin>) states;
      featuresMap = extract(castedStates, x -> List.of(x.all(), x.rejecting()));

    } else if (sentinel instanceof AsymmetricRankingState) {

      @SuppressWarnings("unchecked")
      var castedStates = (Set<AsymmetricRankingState>) states;
      var ranking = castedStates.stream()
          .flatMap(x -> x.ranking().stream().map(y -> y.evaluatedFixpoints))
          .distinct()
          .sorted()
          .toList();

      Function<AsymmetricRankingState, List<Object>> deconstructor = state -> {
        List<Object> deconstructedState = new ArrayList<>(4 * state.ranking().size() + 3);

        List<Integer> permutation = new ArrayList<>();
        List<AsymmetricProductState> subStates = new ArrayList<>(state.ranking());
        Iterator<AsymmetricProductState> subStatesIterator = subStates.iterator();

        while (subStatesIterator.hasNext()) {
          AsymmetricProductState subState = subStatesIterator.next();
          int index = ranking.indexOf(subState.evaluatedFixpoints);

          if (permutation.contains(index)) {
            subStatesIterator.remove();
          } else {
            permutation.add(index);
          }
        }

        // Extract the permutation encoded in the ranking.
        deconstructedState.add(Feature.permutation(permutation));

        // Extract the safety round-robin counter.
        deconstructedState.add(roundRobinCounter(state.safetyIndex()));

        // Extract the language of the state.
        deconstructedState.add(state.state());

        subStates.sort(Comparator.comparing(y -> y.evaluatedFixpoints));

        EquivalenceClass trueClass = state.state().factory().of(true);
        EquivalenceClass falseClass = state.state().factory().of(false);

        for (int i = 0; i < subStates.size(); i++) {

          var fixpoint = ranking.get(i);
          var subState = subStates.get(i);

          // Add padding.
          if (!fixpoint.equals(subState.evaluatedFixpoints)) {
            subStates.add(i,
                new AsymmetricProductState(0, falseClass, trueClass, List.of(), fixpoint, null));
          }
        }

        for (AsymmetricProductState subState : subStates) {
          deconstructedState.add(roundRobinCounter(subState.index));
          deconstructedState.add(subState.currentCoSafety);
          deconstructedState.add(subState.safety);
          deconstructedState.add(subState.nextCoSafety.stream()
              .reduce(trueClass, EquivalenceClass::and));
        }

        return deconstructedState;
      };

      featuresMap = extract(castedStates, deconstructor);

    } else {

      throw new IllegalArgumentException("Unsupported: " + sentinel.getClass());

    }

    @SuppressWarnings("unchecked")
    Map<S, List<Feature>> castedMap = (Map<S, List<Feature>>) removeCommonFeatures(featuresMap);
    return castedMap;
  }

  // Deconstruct all states into basic components and extract features from them.
  @SuppressWarnings("PMD.LooseCoupling")
  private static <S> HashMap<S, List<Feature>> extract(
      Set<? extends S> states, Function<? super S, ? extends List<Object>> deconstructor) {

    Map<S, List<Object>> deconstructorMapping = new HashMap<>(states.size());
    TrieSet<Object> deconstructedStates = new HashTrieSet<>();

    // Deconstruct all states.
    states.forEach(state -> {
      var deconstructedState = List.copyOf(deconstructor.apply(state));
      deconstructedStates.add(deconstructedState);
      deconstructorMapping.put(state, deconstructedState);
    });

    // Extract features from deconstructed states.
    TrieMap<Object, List<Feature>> deconstructedStatesFeatures
        = extractFeatures(deconstructedStates);

    // Combine mappings into a single HashMap and drop intermediate results.
    HashMap<S, List<Feature>> featuresMap = new HashMap<>(states.size());
    deconstructorMapping.forEach(
        (state, key) -> featuresMap.put(state, List.copyOf(deconstructedStatesFeatures.get(key))));
    return featuresMap;
  }

  private static TrieMap<Object, List<Feature>> extractFeatures(TrieSet<Object> states) {

    TrieMap<Object, List<Feature>> combinedFeatures = new HashTrieMap<>();

    if (states.subTries().isEmpty()) {
      if (states.contains(List.of())) {
        combinedFeatures.put(List.of(), List.of());
      }

      return combinedFeatures;
    }

    // Decompose lower levels.
    HashMap<Object, TrieMap<Object, List<Feature>>> subFeatures = new HashMap<>();
    states.subTries().forEach((key, value) -> subFeatures.put(key, extractFeatures(value)));

    // Decompose this level.
    Function<Object, Feature> nodeFeatures;
    Object sentinel = states.subTries().keySet().iterator().next();

    if (sentinel instanceof Feature) {
      nodeFeatures = Feature.class::cast;
    } else if (sentinel instanceof EquivalenceClass) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Set<EquivalenceClass> castedKeySet = (Set) states.subTries().keySet();
      Map<EquivalenceClass, Feature> featureMap
          = extractFeaturesFromEquivalenceClass(castedKeySet);
      nodeFeatures = featureMap::get;
    } else {
      throw new IllegalArgumentException("Unsupported: " + sentinel.getClass());
    }

    // Combine features.
    subFeatures.forEach((key1, subFeatureTrie) -> {
      subFeatureTrie.forEach((key2, subFeatureList) -> {
        List<Object> key = new ArrayList<>(key2.size() + 1);
        key.add(key1);
        key.addAll(key2);

        List<Feature> features = new ArrayList<>(subFeatureList.size() + 1);
        features.add(nodeFeatures.apply(key1));
        features.addAll(subFeatureList);
        combinedFeatures.put(key, List.copyOf(features));
      });
    });

    if (states.contains(List.of())) {
      combinedFeatures.put(List.of(), List.of());
    }

    return combinedFeatures;
  }

  @SuppressWarnings("PMD.LooseCoupling")
  private static <S> HashMap<S, List<Feature>> removeCommonFeatures(HashMap<S, List<Feature>> map) {
    if (map.isEmpty()) {
      return map;
    }

    List<Feature> commonFeatures = new ArrayList<>(map.values().iterator().next());

    map.entrySet().forEach(entry -> {
      List<Feature> features = entry.getValue();

      // Ensure that we can write to the values.
      entry.setValue(new ArrayList<>(features));

      // Shrink the list if too long.
      if (commonFeatures.size() > features.size()) {
        commonFeatures.subList(features.size(), commonFeatures.size()).clear();
      }

      assert commonFeatures.size() <= features.size() : "Failed to shrink list.";

      for (int i = 0, s = commonFeatures.size(); i < s; i++) {
        if (!features.get(i).equals(commonFeatures.get(i))) {
          commonFeatures.set(i, null);
        }
      }
    });

    assert map.values().stream().allMatch(x -> x.size() >= commonFeatures.size());

    if (commonFeatures.stream().anyMatch(Objects::nonNull)) {
      map.values().forEach(features -> {
        // Reverse iteration order to ensure that indices are not shifted.
        for (int i = commonFeatures.size() - 1; i >= 0; i--) {
          var featureToBeRemoved = commonFeatures.get(i);

          if (featureToBeRemoved == null) {
            continue;
          }

          var removedFeature = features.remove(i);
          assert featureToBeRemoved.equals(removedFeature);
        }
      });
    }

    return map;
  }

  static Map<EquivalenceClass, Feature> extractFeaturesFromEquivalenceClass(
      Set<? extends EquivalenceClass> equivalenceClasses) {

    var featuresMapCnf
        = extractFeaturesFromEquivalenceClass(equivalenceClasses, CNF, false);
    boolean unambiguousCnf = Collections3.hasDistinctValues(featuresMapCnf);

    if (!unambiguousCnf) {
      featuresMapCnf = extractFeaturesFromEquivalenceClass(equivalenceClasses, CNF, true);
      unambiguousCnf = Collections3.hasDistinctValues(featuresMapCnf);
    }

    var featuresMapDnf
        = extractFeaturesFromEquivalenceClass(equivalenceClasses, DNF, false);
    boolean unambiguousDnf = Collections3.hasDistinctValues(featuresMapDnf);

    if (!unambiguousDnf) {
      featuresMapDnf = extractFeaturesFromEquivalenceClass(equivalenceClasses, DNF, true);
      unambiguousDnf = Collections3.hasDistinctValues(featuresMapCnf);
    }

    if (unambiguousCnf && !unambiguousDnf) {
      return featuresMapCnf;
    }

    if (unambiguousDnf && !unambiguousCnf) {
      return featuresMapDnf;
    }

    int valuesCnf = 0;

    for (Feature feature : featuresMapCnf.values()) {
      if (!feature.temporalOperatorsProfile().isEmpty()) {
        valuesCnf = Math.max(valuesCnf, feature.temporalOperatorsProfile().last());
      }
    }

    int valuesDnf = 0;

    for (Feature feature : featuresMapDnf.values()) {
      if (!feature.temporalOperatorsProfile().isEmpty()) {
        valuesDnf = Math.max(valuesDnf, feature.temporalOperatorsProfile().last());
      }
    }

    if (valuesDnf <= valuesCnf) {
      return featuresMapDnf;
    } else {
      return featuresMapCnf;
    }
  }

  static Map<EquivalenceClass, Feature> extractFeaturesFromEquivalenceClass(
      Set<? extends EquivalenceClass> equivalenceClasses,
      TemporalOperatorsProfileNormalForm type,
      boolean includeLiterals) {

    if (equivalenceClasses.isEmpty()) {
      return Map.of();
    }

    // Profiles of each equivalence class, before mapping to integers.
    Map<EquivalenceClass, Set<List<Formula>>> profiles = new HashMap<>();

    // Ranking function for temporal operator sets.
    SortedSet<List<Formula>> ranks = new TreeSet<>(SET_COMPARATOR);

    for (EquivalenceClass clazz : equivalenceClasses) {
      profiles.put(clazz, new HashSet<>());

      for (Set<Formula> clause
          : type == CNF ? clazz.conjunctiveNormalForm() : clazz.disjunctiveNormalForm()) {

        List<Formula> temporalOperators = new ArrayList<>();

        for (Formula literal : clause) {
          if (includeLiterals || literal instanceof Formula.TemporalOperator) {
            // Only keep maximal elements.
            temporalOperators.add(literal);

            BiPredicate<Formula, Formula> isLessThan = (Formula x, Formula y) -> {
              if (includeLiterals && (x instanceof Literal || x instanceof XOperator)) {
                return false;
              }

              return y.anyMatch(x::equals);
            };

            temporalOperators = Collections3.maximalElements(temporalOperators, isLessThan);
          }
        }

        // Sort the list to ensure stable iteration order.
        temporalOperators.sort(Formula::compareTo);
        profiles.get(clazz).add(temporalOperators);
        ranks.add(temporalOperators);
      }
    }

    // Profiles of each equivalence class, mapped to integers.
    Map<EquivalenceClass, Feature> featuresMap = new HashMap<>();

    profiles.forEach((clazz, preProfile) -> {
      BitSet profile = new BitSet();

      int i = 0;
      for (List<Formula> rank : ranks) {
        if (preProfile.remove(rank)) {
          profile.set(i);
        }
        i++;
      }

      assert preProfile.isEmpty();
      featuresMap.put(clazz, temporalOperatorsProfileFromBitset(profile));
    });

    return featuresMap;
  }

  public enum TemporalOperatorsProfileNormalForm {
    CNF,
    DNF
  }

  @AutoOneOf(Type.class)
  public abstract static class Feature implements Comparable<Feature> {

    private static final Comparator<Iterable<Integer>> ITERABLE_COMPARATOR
        = Comparators.lexicographical(Integer::compare);

    Feature() {
      // Constructor should only be visible to package.
    }

    public enum Type {
      PERMUTATION,
      ROUND_ROBIN_COUNTER,
      TEMPORAL_OPERATORS_PROFILE;
    }

    public abstract Type type();

    public abstract List<Integer> permutation();

    public abstract int roundRobinCounter();

    public abstract SortedSet<Integer> temporalOperatorsProfile();

    public static Feature permutation(List<Integer> permutation) {
      var permutationCopy = List.copyOf(permutation);
      Preconditions.checkArgument(permutationCopy.stream().allMatch(x -> x >= 0));
      return AutoOneOf_StateFeatures_Feature.permutation(permutationCopy);
    }

    public static Feature roundRobinCounter(int roundRobinCounter) {
      return AutoOneOf_StateFeatures_Feature.roundRobinCounter(roundRobinCounter);
    }

    public static Feature temporalOperatorsProfile(SortedSet<Integer> temporalOperatorsProfile) {
      return AutoOneOf_StateFeatures_Feature.temporalOperatorsProfile(
          BitSet2.asSet(BitSet2.copyOf(temporalOperatorsProfile)));
    }

    public static Feature temporalOperatorsProfileFromBitset(BitSet temporalOperatorsProfile) {
      return AutoOneOf_StateFeatures_Feature.temporalOperatorsProfile(
          BitSet2.asSet(BitSet2.copyOf(temporalOperatorsProfile)));
    }

    public static Feature temporalOperatorsProfileFromBitset(int... temporalOperatorsProfile) {
      return AutoOneOf_StateFeatures_Feature.temporalOperatorsProfile(
          BitSet2.asSet(BitSet2.of(temporalOperatorsProfile)));
    }

    @Override
    public int compareTo(Feature o) {
      int comparison = type().compareTo(o.type());

      if (comparison != 0) {
        return comparison;
      }

      return switch (type()) {
        case PERMUTATION -> ITERABLE_COMPARATOR.compare(permutation(), o.permutation());
        case ROUND_ROBIN_COUNTER -> Integer.compare(roundRobinCounter(), o.roundRobinCounter());
        case TEMPORAL_OPERATORS_PROFILE -> ITERABLE_COMPARATOR.compare(
            temporalOperatorsProfile(), o.temporalOperatorsProfile());
      };
    }
  }
}
