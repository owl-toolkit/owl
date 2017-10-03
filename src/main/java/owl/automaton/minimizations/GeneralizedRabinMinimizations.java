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

package owl.automaton.minimizations;

import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.GeneralizedRabinPair;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Lists2;

public final class GeneralizedRabinMinimizations {
  private static final Logger logger = Logger.getLogger(GeneralizedRabinAcceptance.class.getName());

  private GeneralizedRabinMinimizations() {
  }

  /**
   * Delete all Inf sets which are the complement of their corresponding Fin set.
   */
  public static <S> void minimizeComplementaryInf(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();

    List<GeneralizedRabinPair> pairs = acceptance.getPairs().stream()
      .filter(pair -> pair.hasFinite() && pair.hasInfinite())
      .collect(Collectors.toList());
    int pairCount = pairs.size();

    List<IntSet> pairComplementaryInfSets = new ArrayList<>(pairCount);

    for (GeneralizedRabinPair pair : pairs) {
      IntSet pairInfSets = new IntAVLTreeSet();
      pair.forEachInfiniteIndex(pairInfSets::add);
      pairComplementaryInfSets.add(pairInfSets);
    }

    AutomatonUtil.forEachNonTransientEdge(automaton, (state, edge) -> {
      ListIterator<GeneralizedRabinPair> iterator = pairs.listIterator();
      while (iterator.hasNext()) {
        int pairIndex = iterator.nextIndex();
        GeneralizedRabinPair pair = iterator.next();
        boolean isEdgeFin = pair.containsFinite(edge);

        IntSet pairComplementary = pairComplementaryInfSets.get(pairIndex);
        assert !pairComplementary.isEmpty();
        pairComplementary.removeIf(i -> isEdgeFin == edge.inSet(i));

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

    logger.log(Level.FINE, "Removing complementary indices {0}", indicesToRemove);

    MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
    acceptance.removeIndices(indicesToRemove::contains);
  }

  /**
   * Remove all Inf edges which are implied by another Inf index of the same pair.
   */
  public static <S> void minimizeEdgeImplications(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    int acceptanceSets = acceptance.getAcceptanceSets();
    Collection<GeneralizedRabinPair> pairs = acceptance.getPairs();

    // For each index, store which indices are implied by this index. First, we assume that
    // all pairs imply each other and successively refine this.
    BitSet defaultConsequent = new BitSet(acceptanceSets);
    defaultConsequent.set(0, acceptanceSets);
    BitSet[] impliesMap = new BitSet[acceptanceSets];
    //noinspection UseOfClone
    Arrays.setAll(impliesMap, i -> (BitSet) defaultConsequent.clone());

    AutomatonUtil.forEachNonTransientEdge(automaton, (state, edge) ->
      edge.acceptanceSetIterator().forEachRemaining((int index) -> {
        BitSet consequences = impliesMap[index];
        IntConsumer consumer = consequent -> {
          if (!edge.inSet(consequent)) {
            consequences.clear(consequent);
          }
        };
        BitSets.forEach(consequences, consumer);
      }));

    if (logger.isLoggable(Level.FINER)) {
      StringBuilder builder = new StringBuilder(30 * impliesMap.length);
      builder.append("Implication map:");
      IntIterators.fromTo(0, acceptanceSets).forEachRemaining((int index) -> {
        builder.append("\n  ").append(index).append(" => ");
        BitSet antecedent = impliesMap[index];
        IntConsumer consumer = otherIndex -> {
          if (index != otherIndex) {
            builder.append(otherIndex).append(' ');
          }
        };
        BitSets.forEach(antecedent, consumer);
      });
      logger.log(Level.FINER, builder.toString());
    }

    IntSet indicesToRemove = new IntAVLTreeSet();
    for (GeneralizedRabinPair pair : pairs) {
      pair.forEachInfiniteIndex(index -> {
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
    MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
    acceptance.removeIndices(indicesToRemove::contains);
  }

  /**
   * Remove pairs which cannot accept globally (e.g. an Inf set does not occur)
   */
  public static <S> void minimizeGloballyIrrelevant(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    int acceptanceSets = acceptance.getAcceptanceSets();
    Collection<GeneralizedRabinPair> pairs = acceptance.getPairs();
    int pairCount = pairs.size();

    IntSet indicesOnEveryEdge = new IntOpenHashSet(acceptanceSets);
    IntIterators.fromTo(0, acceptanceSets)
      .forEachRemaining((IntConsumer) indicesOnEveryEdge::add);
    IntSet occurringIndices = new IntOpenHashSet();

    AutomatonUtil.forEachNonTransientEdge(automaton, (state, edge) -> {
      edge.acceptanceSetIterator().forEachRemaining((IntConsumer) occurringIndices::add);
      indicesOnEveryEdge.removeIf(index -> !edge.inSet(index));
    });

    Set<GeneralizedRabinPair> impossiblePairs = new HashSet<>(pairCount);
    Set<GeneralizedRabinPair> irrelevantFinSets = new HashSet<>(pairCount);
    for (GeneralizedRabinPair pair : pairs) {
      if (pair.hasFinite() && indicesOnEveryEdge.contains(pair.getFiniteIndex())) {
        impossiblePairs.add(pair);
        continue;
      }

      boolean anyInfOccurring = false;
      boolean impossibleInfFound = false;
      for (int number = 0; number < pair.getInfiniteIndexCount()
        && !(anyInfOccurring && impossibleInfFound); number++) {
        int infiniteIndex = pair.getInfiniteIndex(number);
        if (!occurringIndices.contains(infiniteIndex)) {
          impossibleInfFound = true;
          impossiblePairs.add(pair);
        } else {
          anyInfOccurring = true;
        }
      }
      if (impossibleInfFound) {
        continue;
      }

      if (anyInfOccurring && pair.hasFinite()) {
        int finiteIndex = pair.getFiniteIndex();
        if (!occurringIndices.contains(finiteIndex)) {
          irrelevantFinSets.add(pair);
        }
      }
    }
    assert Sets.intersection(impossiblePairs, irrelevantFinSets).isEmpty();
    logger.log(Level.FINER, "Removing impossible pairs {0} and irrelevant fin sets {1}",
      new Object[] {impossiblePairs, irrelevantFinSets});

    IntSet indicesToRemove = new IntOpenHashSet();
    impossiblePairs.forEach(pair -> pair.forEachIndex(indicesToRemove::add));
    irrelevantFinSets.forEach(pair -> pair.forFiniteIndex(indicesToRemove::add));

    MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
    acceptance.removeIndices(indicesToRemove::contains);
  }

  /**
   * Merge independent pairs.
   */
  public static <S> void minimizeMergePairs(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    List<GeneralizedRabinPair> pairs = acceptance.getPairs().stream()
      .filter(GeneralizedRabinPair::hasInfinite).collect(Collectors.toList());
    if (pairs.isEmpty()) {
      return;
    }

    Map<GeneralizedRabinPair, IntSet> pairActiveSccs = new HashMap<>();

    Lists2.forEachIndexed(SccDecomposition.computeSccs(automaton, false), (sccIndex, scc) -> {
      Set<GeneralizedRabinPair> pairsInScc = new HashSet<>();
      AutomatonFactory.filter(automaton, scc)
        .forEachLabelledEdge((x, y, z) -> pairs.forEach(pair -> {
          if (pair.contains(y)) {
            pairsInScc.add(pair);
          }
        }));
      pairsInScc.forEach(pair -> pairActiveSccs.computeIfAbsent(pair, k -> new IntAVLTreeSet())
        .add(sccIndex));
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

      int representativeFin = mergeClass.representativeFin;
      IntSet representativeInf = mergeClass.representativeInf;
      int representativeInfCount = representativeInf.size();

      for (GeneralizedRabinPair mergedPair : mergeClass.pairs) {
        assert mergedPair.hasInfinite();
        if (mergedPair.hasFinite()) {
          assert representativeFin != -1;
          remapping.put(mergedPair.getFiniteIndex(), IntSets.singleton(representativeFin));
        }

        int mergedInfiniteCount = mergedPair.getInfiniteIndexCount();
        assert mergedInfiniteCount <= representativeInfCount;
        IntIterator infIterator = representativeInf.iterator();
        for (int infiniteNumber = 0; infiniteNumber < mergedInfiniteCount - 1; infiniteNumber++) {
          remapping.put(mergedPair.getInfiniteIndex(infiniteNumber),
            IntSets.singleton(infIterator.nextInt()));
        }
        int finalIndex = mergedPair.getInfiniteIndex(mergedInfiniteCount - 1);
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

    automaton.remapEdges((state, edge) -> {
      if (!edge.hasAcceptanceSets()) {
        return edge;
      }

      BitSet newAcceptance = new BitSet(edge.largestAcceptanceSet());
      edge.acceptanceSetIterator().forEachRemaining(
        (int index) -> {
          IntSet indexRemapping = remapping.get(index);
          if (indexRemapping == null) {
            newAcceptance.set(index);
          } else {
            indexRemapping.forEach((IntConsumer) newAcceptance::set);
          }
        });

      return Edges.create(edge.getSuccessor(), newAcceptance);
    });

    // Delete the now superfluous indices
    IntSet indicesToRemove = new IntAVLTreeSet(remapping.keySet());
    remapping.values().forEach(indicesToRemove::removeAll);
    MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
    acceptance.removeIndices(indicesToRemove::contains);
  }

  /**
   * Remove edges in a Fin set from all corresponding Inf sets.
   */
  public static <S> void minimizeOverlap(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    List<GeneralizedRabinPair> pairs = acceptance.getPairs().stream()
      .filter(pair -> pair.hasFinite() && pair.hasInfinite())
      .collect(ImmutableList.toImmutableList());
    if (pairs.isEmpty()) {
      return;
    }

    automaton.remapEdges((state, edge) -> {
      if (!edge.hasAcceptanceSets()) {
        return edge;
      }

      int overlapIndex = -1;
      for (int index = 0; index < pairs.size(); index++) {
        GeneralizedRabinPair pair = pairs.get(index);
        if (pair.containsFinite(edge) && pair.containsInfinite(edge)) {
          overlapIndex = index;
          break;
        }
      }

      if (overlapIndex == -1) {
        return edge;
      }

      BitSet modifiedAcceptance = new BitSet(edge.largestAcceptanceSet());
      edge.acceptanceSetIterator().forEachRemaining((IntConsumer) modifiedAcceptance::set);
      pairs.get(overlapIndex).forEachInfiniteIndex(modifiedAcceptance::clear);

      for (int index = overlapIndex + 1; index < pairs.size(); index++) {
        GeneralizedRabinPair pair = pairs.get(index);
        if (pair.containsFinite(edge) && pair.containsInfinite(edge)) {
          pair.forEachInfiniteIndex(modifiedAcceptance::clear);
        }
      }

      return Edges.create(edge.getSuccessor(), modifiedAcceptance);
    });
  }

  /**
   * Delete all pairs which only accept if another pair accepts.
   */
  public static <S> void minimizePairImplications(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    int acceptanceSets = acceptance.getAcceptanceSets();
    Collection<GeneralizedRabinPair> pairs = acceptance.getPairs()
      .stream().filter(pair -> !pair.isEmpty()).collect(Collectors.toList());

    List<Set<S>> sccs = SccDecomposition.computeSccs(automaton, false);

    List<Multimap<GeneralizedRabinPair, GeneralizedRabinPair>> sccImplicationList =
      new ArrayList<>(sccs.size());

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
      //noinspection UseOfClone
      Arrays.setAll(impliesMap, i -> (BitSet) defaultConsequent.clone());

      // Build implication matrix on this SCC, including vacuous implications (!)
      BiConsumer<S, Edge<S>> action = (state, edge) ->
        edge.acceptanceSetIterator().forEachRemaining((int index) -> {
          BitSet consequences = impliesMap[index];
          IntConsumer consumer = consequent -> {
            if (!edge.inSet(consequent)) {
              consequences.clear(consequent);
            }
          };
          BitSets.forEach(consequences, consumer);
        });

      Automaton<S, ?> filtered = AutomatonFactory.filter(automaton, scc);
      scc.forEach(x -> filtered.getEdges(x).forEach(edge -> action.accept(x, edge)));

      Multimap<GeneralizedRabinPair, GeneralizedRabinPair> sccImplications =
        HashMultimap.create(pairs.size(), pairs.size() / 2 + 1);

      // Search for pairs where one implies the other (in terms of acceptance)
      for (GeneralizedRabinPair antecedent : pairs) {
        if (antecedent.isEmpty()) {
          sccImplications.putAll(antecedent, pairs);
          continue;
        }

        for (GeneralizedRabinPair consequent : pairs) {
          if (consequent.isEmpty() || antecedent.equals(consequent)) {
            continue;
          }

          // The consequent's Fin set has to be smaller than the antecedents, i.e. it has
          // to be implied by the antecedent's Fin set.
          if (consequent.hasFinite() && (!antecedent.hasFinite()
            || !impliesMap[consequent.getFiniteIndex()].get(antecedent.getFiniteIndex()))) {
            continue;
          }

          boolean infImplied = true;
          if (consequent.hasInfinite()) {
            // For each Inf set of the consequent, we have to find a corresponding Inf set in the
            // antecedent.
            int consequentInfIndices = consequent.getInfiniteIndexCount();
            int antecedentInfIndices = antecedent.getInfiniteIndexCount();
            for (int consequentNumber = 0; consequentNumber < consequentInfIndices;
                 consequentNumber++) {
              boolean foundImplication = false;
              int consequentIndex = consequent.getInfiniteIndex(consequentNumber);
              for (int antecedentNumber = 0; antecedentNumber < antecedentInfIndices;
                   antecedentNumber++) {
                int antecedentIndex = antecedent.getInfiniteIndex(antecedentNumber);
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
            infImplied = !antecedent.hasInfinite();
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

    Set<GeneralizedRabinPair> toRemove = new HashSet<>();

    // See if we can find for each SCC a pair which is implied by this one - then this pair is
    // completely superfluous
    pairs.stream()
      .filter(pair -> sccImplicationList.stream()
        .allMatch(sccImplications -> sccImplications.get(pair)
          .stream().anyMatch(consequent -> !toRemove.contains(consequent))))
      .forEach(toRemove::add);

    List<Set<GeneralizedRabinPair>> pairsToRemoveInSccs = new ArrayList<>(sccs.size());
    for (int sccIndex = 0; sccIndex < sccs.size(); sccIndex++) {
      Multimap<GeneralizedRabinPair, GeneralizedRabinPair> sccImplications =
        sccImplicationList.get(sccIndex);
      Set<GeneralizedRabinPair> toRemoveInScc =
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
      Set<GeneralizedRabinPair> pairsToRemoveInScc = pairsToRemoveInSccs.get(sccIndex);
      if (pairsToRemoveInScc.isEmpty()) {
        continue;
      }
      // In the case of SCC implication, we can only remove the Inf sets. Consider, for example,
      // a single state SCC with a single transition and acceptance Fin(0) | Fin(1). Even though
      // the pairs (trivially) imply each other on this SCC, we can't remove either index.
      IntSet indicesToRemoveInScc = new IntAVLTreeSet();
      pairsToRemoveInScc.forEach(pair -> pair.forEachInfiniteIndex(indicesToRemoveInScc::add));
      MinimizationUtil.removeIndices(automaton, scc, indicesToRemoveInScc);
    }

    IntSet indicesToRemove = new IntAVLTreeSet();
    toRemove.forEach(pair -> pair.forEachIndex(indicesToRemove::add));
    MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
    acceptance.removeIndices(indicesToRemove::contains);
  }

  /**
   * Remove all indices from edges of an SCC which can't accept (e.g. if the SCC does not contain
   * all Inf sets of the pair).
   */
  public static <S> void minimizeSccIrrelevant(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    for (Set<S> scc : SccDecomposition.computeSccs(automaton, false)) {
      IntSet indicesInScc = new IntAVLTreeSet();
      BiConsumer<S, Edge<S>> action = (state, edge) ->
        edge.acceptanceSetIterator().forEachRemaining((IntConsumer) indicesInScc::add);
      Automaton<S, ?> filtered = AutomatonFactory.filter(automaton, scc);
      scc.forEach(x -> filtered.getEdges(x).forEach(edge -> action.accept(x, edge)));

      IntSet indicesToRemove = new IntAVLTreeSet();
      for (GeneralizedRabinPair pair : acceptance.getPairs()) {
        boolean finOccurring = pair.hasFinite() && indicesInScc.contains(pair.getFiniteIndex());
        boolean infOccurring = false;
        boolean impossibleIndexFound = false;

        for (int number = 0; number < pair.getInfiniteIndexCount()
          && !(impossibleIndexFound && infOccurring); number++) {
          if (indicesInScc.contains(pair.getInfiniteIndex(number))) {
            infOccurring = true;
          } else {
            impossibleIndexFound = true;
          }
        }

        if (infOccurring || finOccurring) {
          if (impossibleIndexFound) {
            pair.forEachIndex(indicesToRemove::add);
          }

          if (!finOccurring && pair.hasFinite()) {
            indicesToRemove.add(pair.getFiniteIndex());
          }
        }
      }

      MinimizationUtil.removeIndices(automaton, scc, indicesToRemove);
    }
  }

  /**
   * Identify Fin-only pairs and SCCs which trivially accept with them.
   */
  public static <S> void minimizeTrivial(
    MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) {
    Collection<GeneralizedRabinPair> pairs = Collections2
      .filter(automaton.getAcceptance().getPairs(),
        pair -> pair.hasFinite() && !pair.hasInfinite());

    if (pairs.isEmpty()) {
      return;
    }

    SccDecomposition.computeSccs(automaton, false).forEach(scc -> {
      IntSet usedIndices = new IntAVLTreeSet();
      BiConsumer<S, Edge<S>> action = (state, edge) ->
        edge.acceptanceSetIterator().forEachRemaining((IntConsumer) usedIndices::add);
      Automaton<S, ?> filtered = AutomatonFactory.filter(automaton, scc);
      scc.forEach(x -> filtered.getEdges(x).forEach(edge -> action.accept(x, edge)));
      pairs.stream()
        .filter(pair -> !usedIndices.contains(pair.getFiniteIndex()))
        .findAny()
        .ifPresent(generalizedRabinPair -> {
          usedIndices.remove(generalizedRabinPair.getFiniteIndex());
          MinimizationUtil.removeIndices(automaton, scc, usedIndices);
        });
    });
  }

  private static final class MergeClass {
    final IntSet activeSccIndices;
    final Set<GeneralizedRabinPair> pairs;
    final int representativeFin;
    final IntSet representativeInf;

    MergeClass(GeneralizedRabinPair pair, IntSet activeIndices) {
      this.pairs = Sets.newHashSet(pair);
      this.activeSccIndices = new IntAVLTreeSet(activeIndices);
      representativeFin = pair.hasFinite() ? pair.getFiniteIndex() : -1;
      representativeInf = new IntAVLTreeSet();
      pair.forEachInfiniteIndex(representativeInf::add);
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

      if (representativeFin == -1 && other.representativeFin != -1
        || representativeInf.size() < other.representativeInf.size()) {
        return false;
      }

      assert other.pairs.stream().allMatch(pair ->
        pair.getInfiniteIndexCount() <= representativeInf.size()
          && !(representativeFin == -1 && pair.hasFinite()));

      this.activeSccIndices.addAll(other.activeSccIndices);
      this.pairs.addAll(other.pairs);
      return true;
    }
  }
}
