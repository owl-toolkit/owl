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

package owl.automaton.acceptance.optimizations;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import de.tum.in.naturals.Indices;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;

public final class GeneralizedRabinAcceptanceOptimizations {
  private static final Logger logger = Logger.getLogger(GeneralizedRabinAcceptance.class.getName());

  private GeneralizedRabinAcceptanceOptimizations() {}

  /**
   * Delete all Inf sets which are the complement of their corresponding Fin set.
   */
  public static <S> void removeComplementaryInfSets(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.acceptance();

    List<RabinPair> pairs = acceptance.pairs().stream()
      .filter(RabinPair::hasInfSet)
      .collect(Collectors.toList());
    List<IntSet> pairComplementaryInfSets = new ArrayList<>(pairs.size());

    for (RabinPair pair : pairs) {
      IntSet pairInfSets = new IntAVLTreeSet();
      pair.forEachInfSet(pairInfSets::add);
      pairComplementaryInfSets.add(pairInfSets);
    }

    AutomatonUtil.forEachNonTransientEdge(automaton, (state, edge) -> {
      ListIterator<RabinPair> iterator = pairs.listIterator();
      while (iterator.hasNext()) {
        int pairIndex = iterator.nextIndex();
        RabinPair pair = iterator.next();

        IntSet pairComplementary = pairComplementaryInfSets.get(pairIndex);
        assert !pairComplementary.isEmpty();
        boolean finEdge = edge.inSet(pair.finSet());
        pairComplementary.removeIf((int i) -> finEdge == edge.inSet(i));

        if (pairComplementary.isEmpty()) {
          iterator.remove();
          pairComplementaryInfSets.remove(pairIndex);
        }
      }
    });

    IntSet indicesToRemove = new IntAVLTreeSet();
    pairComplementaryInfSets.forEach(indicesToRemove::addAll);

    if (indicesToRemove.isEmpty()) {
      return;
    }

    logger.log(Level.FINER, "Removing complementary indices {0}", indicesToRemove);

    AcceptanceOptimizations.removeAndRemapIndices(automaton, indicesToRemove);
    automaton.acceptance(acceptance.filter(indicesToRemove::contains));
    assert automaton.acceptance().isWellFormedAutomaton(automaton);
  }

  /**
   * Remove all Inf edges which are implied by another Inf index of the same pair.
   */
  public static <S> void minimizeEdgeImplications(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.acceptance();
    int acceptanceSets = acceptance.acceptanceSets();

    // For each index, store which indices are implied by this index. First, we assume that
    // all pairs imply each other and successively refine this.
    BitSet defaultConsequent = new BitSet(acceptanceSets);
    defaultConsequent.set(0, acceptanceSets);
    BitSet[] impliesMap = new BitSet[acceptanceSets];
    Arrays.setAll(impliesMap, i -> BitSets.copyOf(defaultConsequent));

    AutomatonUtil.forEachNonTransientEdge(automaton, (state, edge) ->
      edge.acceptanceSetIterator().forEachRemaining((int index) -> {
        BitSet consequences = impliesMap[index];
        BitSets.forEach(consequences, consequent -> {
          if (!edge.inSet(consequent)) {
            consequences.clear(consequent);
          }
        });
      }));

    if (logger.isLoggable(Level.FINER)) {
      StringBuilder builder = new StringBuilder(30 * impliesMap.length);
      builder.append("Implication map:");

      for (int index = 0; index < acceptanceSets; index++) {
        builder.append("\n  ").append(index).append(" => ");
        BitSet antecedent = impliesMap[index];
        int i = index;
        BitSets.forEach(antecedent, otherIndex -> {
          if (i != otherIndex) {
            builder.append(otherIndex).append(' ');
          }
        });
      }

      logger.log(Level.FINER, builder.toString());
    }

    IntSet indicesToRemove = new IntAVLTreeSet();

    for (RabinPair pair : acceptance.pairs()) {
      pair.forEachInfSet(index -> {
        if (indicesToRemove.contains(index)) {
          // Avoid removing both sides of a bi-implication
          return;
        }

        BitSet consequences = impliesMap[index];
        for (int consequenceIndex = consequences.nextSetBit(0); consequenceIndex >= 0;
             consequenceIndex = consequences.nextSetBit(consequenceIndex + 1)) {
          if (consequenceIndex != index && !indicesToRemove.contains(consequenceIndex) && pair
            .isInfinite(consequenceIndex)) {
            indicesToRemove.add(consequenceIndex);
          }
        }
      });
    }

    logger.log(Level.FINEST, "Implication removal: {0}", indicesToRemove);

    AcceptanceOptimizations.removeAndRemapIndices(automaton, indicesToRemove);
    automaton.acceptance(acceptance.filter(indicesToRemove::contains));
    assert automaton.acceptance().isWellFormedAutomaton(automaton);
  }

  /**
   * Merge independent pairs.
   */
  public static <S> void minimizeMergePairs(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    List<RabinPair> pairs = automaton.acceptance().pairs().stream()
      .filter(RabinPair::hasInfSet)
      .collect(Collectors.toList());

    if (pairs.isEmpty()) {
      return;
    }

    SortedMap<RabinPair, IntSet> pairActiveSccs = new TreeMap<>();
    Indices.forEachIndexed(SccDecomposition.computeSccs(automaton, false), (sccIndex, scc) -> {
      BitSet indices = AutomatonUtil.getAcceptanceSets(automaton, scc);
      pairs.forEach(pair -> {
        if (pair.contains(indices)) {
          pairActiveSccs.computeIfAbsent(pair, k -> new IntOpenHashSet()).add(sccIndex);
        }
      });
    });

    List<MergeClass> mergeClasses = new ArrayList<>(pairs.size());
    pairActiveSccs.forEach((pair, activeSccs) ->
      mergeClasses.add(new MergeClass(pair, activeSccs)));

    while (true) {
      boolean someMerge = false;
      for (MergeClass mergeClass : mergeClasses) {
        someMerge = mergeClasses.removeIf(mergeClass::tryMerge);

        // Modification to list - restart
        if (someMerge) {
          break;
        }
      }

      if (!someMerge) {
        break;
      }
    }

    Int2ObjectMap<IntSet> remapping = new Int2ObjectAVLTreeMap<>();
    mergeClasses.forEach(mergeClass -> {
      if (mergeClass.pairs.size() == 1) {
        Iterables.getOnlyElement(mergeClass.pairs).forEachIndex(index ->
          remapping.put(index, IntSets.singleton(index)));
      }

      @Nonnegative
      int representativeFin = mergeClass.representativeFin;
      IntSet representativeInf = mergeClass.representativeInf;
      int representativeInfCount = representativeInf.size();

      for (RabinPair mergedPair : mergeClass.pairs) {
        assert mergedPair.hasInfSet();
        remapping.put(mergedPair.finSet(), IntSets.singleton(representativeFin));

        int mergedInfiniteCount = mergedPair.infSetCount();
        assert mergedInfiniteCount <= representativeInfCount;
        IntIterator infIterator = representativeInf.iterator();
        for (int infiniteNumber = 0; infiniteNumber < mergedInfiniteCount - 1; infiniteNumber++) {
          remapping.put(mergedPair.infSet(infiniteNumber),
            IntSets.singleton(infIterator.nextInt()));
        }
        int finalIndex = mergedPair.infSet(mergedInfiniteCount - 1);
        IntSet finalSet = new IntAVLTreeSet();
        infIterator.forEachRemaining((IntConsumer) finalSet::add);
        assert !finalSet.isEmpty();
        remapping.put(finalIndex, finalSet);
      }
    });

    if (logger.isLoggable(Level.FINER)) {
      List<MergeClass> trueMerges = mergeClasses.stream()
        .filter(mergeClass -> mergeClass.pairs.size() > 1)
        .collect(Collectors.toList());
      logger.log(Level.FINER, "Merge classes {0}, indices {1}",
        new Object[] {trueMerges, remapping});
    }

    automaton.updateEdges((state, edge) -> {
      BitSet newAcceptance = new BitSet();

      edge.acceptanceSetIterator().forEachRemaining(
        (int index) -> {
          IntSet indexRemapping = remapping.get(index);
          if (indexRemapping == null) {
            newAcceptance.set(index);
          } else {
            indexRemapping.forEach((IntConsumer) newAcceptance::set);
          }
        });

      return Edge.of(edge.successor(), newAcceptance);
    });

    // Delete the now superfluous indices
    IntSet indicesToRemove = new IntAVLTreeSet(remapping.keySet());
    remapping.values().forEach(indicesToRemove::removeAll);
    AcceptanceOptimizations.removeAndRemapIndices(automaton, indicesToRemove);
    automaton.acceptance(automaton.acceptance().filter(indicesToRemove::contains));
    assert automaton.acceptance().isWellFormedAutomaton(automaton);
  }

  /**
   * Remove edges in a Fin set from all corresponding Inf sets.
   */
  public static <S> void minimizeOverlap(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.acceptance();
    List<RabinPair> pairs = acceptance.pairs().stream()
      .filter(RabinPair::hasInfSet)
      .collect(Collectors.toUnmodifiableList());
    if (pairs.isEmpty()) {
      return;
    }

    automaton.updateEdges((state, edge) -> {
      if (!edge.hasAcceptanceSets()) {
        return edge;
      }

      int overlapIndex = -1;
      for (int index = 0; index < pairs.size(); index++) {
        RabinPair pair = pairs.get(index);
        if (edge.inSet(pair.finSet()) && pair.containsInfinite(edge)) {
          overlapIndex = index;
          break;
        }
      }

      if (overlapIndex == -1) {
        return edge;
      }

      BitSet modifiedAcceptance = new BitSet(edge.largestAcceptanceSet());
      edge.acceptanceSetIterator().forEachRemaining((IntConsumer) modifiedAcceptance::set);
      pairs.get(overlapIndex).forEachInfSet(modifiedAcceptance::clear);

      for (int index = overlapIndex + 1; index < pairs.size(); index++) {
        RabinPair pair = pairs.get(index);
        if (edge.inSet(pair.finSet()) && pair.containsInfinite(edge)) {
          pair.forEachInfSet(modifiedAcceptance::clear);
        }
      }

      return Edge.of(edge.successor(), modifiedAcceptance);
    });
  }

  /**
   * Delete all pairs which only accept if another pair accepts.
   */
  public static <S> void minimizePairImplications(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.acceptance();
    int acceptanceSets = acceptance.acceptanceSets();
    List<RabinPair> pairs = new ArrayList<>(acceptance.pairs());
    List<Set<S>> sccs = SccDecomposition.computeSccs(automaton, false);
    List<SortedSetMultimap<RabinPair, RabinPair>> sccImplicationList = new ArrayList<>(sccs.size());

    StringBuilder logBuilder = logger.isLoggable(Level.FINEST)
      ? new StringBuilder(200 + sccs.size() * 50) : null;
    if (logBuilder != null) {
      logBuilder.append("Implications:");
    }

    BitSet defaultConsequent = new BitSet(acceptanceSets);
    defaultConsequent.set(0, acceptanceSets);
    BitSet[] impliesMap = new BitSet[acceptanceSets];

    for (int sccIndex = 0; sccIndex < sccs.size(); sccIndex++) {
      Set<S> scc = sccs.get(sccIndex);
      Arrays.setAll(impliesMap, i -> BitSets.copyOf(defaultConsequent));

      // Build implication matrix on this SCC, including vacuous implications (!)
      for (S state : scc) {
        for (Edge<S> edge : automaton.edges(state)) {
          if (scc.contains(edge.successor())) {
            edge.acceptanceSetIterator().forEachRemaining((int index) ->
              BitSets.forEach(impliesMap[index], consequent -> {
                if (!edge.inSet(consequent)) {
                  impliesMap[index].clear(consequent);
                }
              }));
          }
        }
      }

      SortedSetMultimap<RabinPair, RabinPair> sccImplications = TreeMultimap.create();

      // Search for pairs where one implies the other (in terms of acceptance)
      for (RabinPair antecedent : pairs) {
        for (RabinPair consequent : pairs) {
          if (antecedent.equals(consequent)) {
            continue;
          }

          // The consequent's Fin set has to be smaller than the antecedents, i.e. it has
          // to be implied by the antecedent's Fin set.
          if (!impliesMap[consequent.finSet()].get(antecedent.finSet())) {
            continue;
          }

          boolean infImplied = true;
          if (consequent.hasInfSet()) {
            // For each Inf set of the consequent, we have to find a corresponding Inf set in the
            // antecedent.
            int consequentInfIndices = consequent.infSetCount();
            int antecedentInfIndices = antecedent.infSetCount();
            for (int consequentNumber = 0; consequentNumber < consequentInfIndices;
                 consequentNumber++) {
              boolean foundImplication = false;
              int consequentIndex = consequent.infSet(consequentNumber);
              for (int antecedentNumber = 0; antecedentNumber < antecedentInfIndices;
                   antecedentNumber++) {
                int antecedentIndex = antecedent.infSet(antecedentNumber);
                if (impliesMap[antecedentIndex].get(consequentIndex)) {
                  foundImplication = true;
                  break;
                }
              }
              if (!foundImplication) {
                infImplied = false;
                break;
              }
            }
          } else {
            // If the consequent has no Inf set but the antecedent has some, there can be no
            // implication
            infImplied = !antecedent.hasInfSet();
          }
          if (infImplied) {
            sccImplications.put(antecedent, consequent);
          }
        }
      }
      sccImplicationList.add(sccImplications);

      if (logBuilder != null) {
        logBuilder.append("\n ").append(sccIndex).append(" - ").append(sccs.get(sccIndex))
          .append("\n  Indices:");
        IntIterators.fromTo(0, acceptanceSets).forEachRemaining((int index) -> {
          logBuilder.append("\n   ").append(index).append(" => ");
          BitSet antecedent = impliesMap[index];
          IntConsumer consumer = otherIndex -> {
            if (index != otherIndex) {
              logBuilder.append(otherIndex).append(' ');
            }
          };
          BitSets.forEach(antecedent, consumer);
        });
        logBuilder.append("\n  Pairs:");
        if (sccImplications.isEmpty()) {
          logBuilder.append("\n   ").append("None");
        } else {
          sccImplications.asMap().forEach((pair, consequences) ->
            logBuilder.append("\n   ").append(pair).append(" => ").append(consequences));
        }
      }
    }

    Set<RabinPair> toRemove = new HashSet<>();

    // See if we can find for each SCC a pair which is implied by this one - then this pair is
    // completely superfluous
    pairs.stream()
      .filter(pair -> sccImplicationList.stream()
        .allMatch(sccImplications -> sccImplications.get(pair)
          .stream().anyMatch(consequent -> !toRemove.contains(consequent))))
      .forEach(toRemove::add);

    List<Set<RabinPair>> pairsToRemoveInSccs = new ArrayList<>(sccs.size());
    for (int sccIndex = 0; sccIndex < sccs.size(); sccIndex++) {
      Multimap<RabinPair, RabinPair> sccImplications =
        sccImplicationList.get(sccIndex);
      Set<RabinPair> toRemoveInScc =
        new HashSet<>(sccImplications.keySet().size());

      // Find pairs to remove in this SCC
      sccImplications.forEach((antecedent, consequent) -> {
        // See if we find any pair which accepts more than this pair
        if (!toRemove.contains(antecedent) && !toRemove.contains(consequent)
          && !toRemoveInScc.contains(consequent)) {
          toRemoveInScc.add(antecedent);
        }
      });

      pairsToRemoveInSccs.add(toRemoveInScc);
    }

    if (logBuilder != null) {
      logBuilder.append("\nRemovals:\n  Global: ").append(toRemove);
      for (int sccIndex = 0; sccIndex < sccs.size(); sccIndex++) {
        logBuilder.append("\n  ").append(sccIndex).append(": ")
          .append(pairsToRemoveInSccs.get(sccIndex));
      }
      logger.log(Level.FINEST, logBuilder.toString());
    }

    for (int sccIndex = 0; sccIndex < sccs.size(); sccIndex++) {
      Set<S> scc = sccs.get(sccIndex);
      Set<RabinPair> pairsToRemoveInScc = pairsToRemoveInSccs.get(sccIndex);
      if (pairsToRemoveInScc.isEmpty()) {
        continue;
      }
      // In the case of SCC implication, we can only remove the Inf sets. Consider, for example,
      // a single state SCC with a single transition and acceptance Fin(0) | Fin(1). Even though
      // the pairs (trivially) imply each other on this SCC, we can't remove either index.
      BitSet indicesToRemoveInScc = new BitSet();
      pairsToRemoveInScc.forEach(pair -> pair.forEachInfSet(indicesToRemoveInScc::set));
      AcceptanceOptimizations.removeIndices(automaton, scc, indicesToRemoveInScc);
    }

    IntSet indicesToRemove = new IntAVLTreeSet();
    toRemove.forEach(pair -> pair.forEachIndex(indicesToRemove::add));

    AcceptanceOptimizations.removeAndRemapIndices(automaton, indicesToRemove);
    automaton.acceptance(acceptance.filter(indicesToRemove::contains));
    assert automaton.acceptance().isWellFormedAutomaton(automaton);
  }

  /**
   * - Remove all indices from edges of an SCC which can't accept (e.g. if the SCC does not contain
   *     all Inf sets of the pair).
   * - Identify Fin-only pairs and SCCs which trivially accept with them.
   * - Remove pairs which cannot accept globally (e.g. an Inf set does not occur)
   */
  public static <S> void minimizeSccIrrelevant(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    var acceptance = automaton.acceptance();
    var finOnlyPairs = acceptance.pairs().stream()
      .filter(x -> !x.hasInfSet())
      .collect(Collectors.toUnmodifiableList());

    for (Set<S> scc : SccDecomposition.computeSccs(automaton, false)) {
      BitSet indicesInScc = AutomatonUtil.getAcceptanceSets(automaton, scc);
      BitSet indicesToRemove = new BitSet();

      for (RabinPair pair : acceptance.pairs()) {
        boolean finOccurring = indicesInScc.get(pair.finSet());
        boolean infOccurring = false;
        boolean impossibleIndexFound = false;

        for (int number = 0; number < pair.infSetCount()
          && !(impossibleIndexFound && infOccurring); number++) {
          if (indicesInScc.get(pair.infSet(number))) {
            infOccurring = true;
          } else {
            impossibleIndexFound = true;
          }
        }

        if (infOccurring || finOccurring) {
          if (impossibleIndexFound) {
            pair.forEachIndex(indicesToRemove::set);
          }

          if (!finOccurring) {
            indicesToRemove.set(pair.finSet());
          }
        }
      }

      finOnlyPairs.stream()
        .filter(pair -> !indicesInScc.get(pair.finSet()))
        .findAny()
        .ifPresent(pair -> {
          indicesInScc.clear(pair.finSet());
          AcceptanceOptimizations.removeIndices(automaton, scc, indicesInScc);
        });

      AcceptanceOptimizations.removeIndices(automaton, scc, indicesToRemove);
    }

    IntSet indicesOnEveryEdge = new IntOpenHashSet(
      IntStream.range(0, acceptance.acceptanceSets()).iterator());
    IntSet occurringIndices = new IntOpenHashSet();

    AutomatonUtil.forEachNonTransientEdge(automaton, (state, edge) -> {
      edge.acceptanceSetIterator().forEachRemaining((IntConsumer) occurringIndices::add);
      indicesOnEveryEdge.removeIf((int index) -> !edge.inSet(index));
    });

    Set<RabinPair> impossiblePairs = new HashSet<>();

    for (RabinPair pair : acceptance.pairs()) {
      if (indicesOnEveryEdge.contains(pair.finSet())) {
        impossiblePairs.add(pair);
        continue;
      }

      boolean anyInfOccurring = false;
      boolean impossibleInfFound = false;

      for (int i = 0; i < pair.infSetCount() && (!anyInfOccurring || !impossibleInfFound); i++) {
        int infiniteIndex = pair.infSet(i);
        if (occurringIndices.contains(infiniteIndex)) {
          anyInfOccurring = true;
        } else {
          impossibleInfFound = true;
          impossiblePairs.add(pair);
        }
      }
    }

    logger.log(Level.FINER, "Removing impossible pairs {0}", new Object[] {impossiblePairs});

    IntSet indicesToRemove = new IntOpenHashSet();
    impossiblePairs.forEach(pair -> pair.forEachIndex(indicesToRemove::add));

    AcceptanceOptimizations.removeAndRemapIndices(automaton, indicesToRemove);
    automaton.updateAcceptance(x -> x.filter(indicesToRemove::contains));
    assert automaton.acceptance().isWellFormedAutomaton(automaton);
  }

  public static <S> void mergeBuchiTypePairs(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    BitSet usedIndices = AutomatonUtil.getAcceptanceSets(automaton);
    List<RabinPair> buchiTypePairs = automaton.acceptance().pairs().stream()
      .filter(x -> x.infSetCount() == 1 && !usedIndices.get(x.finSet()))
      .collect(Collectors.toList());

    if (buchiTypePairs.size() < 2) {
      return;
    }

    // Select one Rabin pair and mark everything else to be removed.
    var pairsIterator = buchiTypePairs.iterator();
    var representativePair = pairsIterator.next();
    var indicesToRemoveFin = new BitSet();
    var indicesToRemoveInf = new BitSet();

    pairsIterator.forEachRemaining(x -> {
      indicesToRemoveFin.set(x.finSet());
      indicesToRemoveInf.set(x.infSet());
    });

    // Remap Rabin pairs to be removed to the representativePair.
    automaton.updateEdges((state, edge) ->
      edge.withAcceptance(x -> {
        Preconditions.checkState(!indicesToRemoveFin.get(x));
        return indicesToRemoveInf.get(x) ? representativePair.infSet() : x;
      }));

    // Remap and update acceptance condition.
    var indicesToRemove = new IntOpenHashSet();
    indicesToRemoveFin.stream().forEach(indicesToRemove::add);
    indicesToRemoveInf.stream().forEach(indicesToRemove::add);
    AcceptanceOptimizations.removeAndRemapIndices(automaton, indicesToRemove);
    automaton.updateAcceptance(x -> x.filter(indicesToRemove::contains));
    assert automaton.acceptance().isWellFormedAutomaton(automaton);
  }

  private static final class MergeClass {
    final IntSet activeSccIndices;
    final SortedSet<RabinPair> pairs;

    @Nonnegative
    final int representativeFin;
    final IntSet representativeInf;

    MergeClass(RabinPair pair, IntSet activeIndices) {
      this.pairs = new TreeSet<>();
      this.pairs.add(pair);
      this.activeSccIndices = new IntAVLTreeSet(activeIndices);
      representativeFin = pair.finSet();
      representativeInf = new IntAVLTreeSet();
      pair.forEachInfSet(representativeInf::add);
    }

    @Override
    public String toString() {
      return String.format("%s (%s)", pairs, activeSccIndices);
    }

    boolean tryMerge(MergeClass other) {
      if (this.equals(other)) {
        return false;
      }

      assert Sets.intersection(pairs, other.pairs).isEmpty();

      if (IntIterators.any(activeSccIndices.iterator(), other.activeSccIndices::contains)) {
        return false;
      }

      if (representativeInf.size() < other.representativeInf.size()) {
        return false;
      }

      assert other.pairs.stream().allMatch(pair ->
        pair.infSetCount() <= representativeInf.size());

      this.activeSccIndices.addAll(other.activeSccIndices);
      this.pairs.addAll(other.pairs);
      return true;
    }
  }
}
