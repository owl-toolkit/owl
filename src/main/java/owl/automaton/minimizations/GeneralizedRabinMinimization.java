package owl.automaton.minimizations;

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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.algorithms.SccAnalyser;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.TransitionUtil;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.GeneralizedRabinPair;

public final class GeneralizedRabinMinimization {
  private static final Logger logger = Logger.getLogger(GeneralizedRabinAcceptance.class.getName());

  private GeneralizedRabinMinimization() {}

  /**
   * Delete all Inf sets which are the complement of their corresponding Fin set.
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizeComplementaryInf() {
    return (MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) -> {
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
        if (pairs.isEmpty()) {
          return;
        }
        ListIterator<GeneralizedRabinPair> pairIterator = pairs.listIterator();
        while (pairIterator.hasNext()) {
          int pairIndex = pairIterator.nextIndex();
          GeneralizedRabinPair pair = pairIterator.next();
          boolean isEdgeFin = pair.containsFinite(edge);

          IntSet pairComplementary = pairComplementaryInfSets.get(pairIndex);
          assert !pairComplementary.isEmpty();

          if (pairComplementary.isEmpty()) {
            continue;
          }

          IntIterator indexIterator = pairComplementary.iterator();
          while (indexIterator.hasNext()) {
            int complementaryIndex = indexIterator.nextInt();
            if (isEdgeFin == edge.inSet(complementaryIndex)) {
              indexIterator.remove();
            }
          }

          if (pairComplementary.isEmpty()) {
            pairIterator.remove();
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
    };
  }

  /**
   * Remove all Inf edges which are implied by another Inf index of the same pair.
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizeEdgeImplications() {
    return automaton -> {
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
            if (consequenceIndex == index) {
              continue;
            }
            if (!indicesToRemove.contains(consequenceIndex) && pair.isInfinite(consequenceIndex)) {
              indicesToRemove.add(consequenceIndex);
            }
          }
        });
      }

      logger.log(Level.FINEST, "Implication removal: {0}", indicesToRemove);
      MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
      acceptance.removeIndices(indicesToRemove::contains);
    };
  }

  /**
   * Remove pairs which cannot accept globally (e.g. an Inf set does not occur)
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizeGloballyIrrelevant() {
    return automaton -> {
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
        IntIterator iterator = indicesOnEveryEdge.iterator();
        while (iterator.hasNext()) {
          int index = iterator.nextInt();
          if (!edge.inSet(index)) {
            iterator.remove();
          }
        }
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
    };
  }

  /**
   * Merge independent pairs.
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizeMergePairs() {
    return (MutableAutomaton<S, GeneralizedRabinAcceptance> automaton) -> {
      GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
      Collection<GeneralizedRabinPair> pairs = acceptance.getPairs().stream()
        .filter(GeneralizedRabinPair::hasInfinite).collect(Collectors.toList());
      if (pairs.isEmpty()) {
        return;
      }

      List<Set<S>> sccs = SccAnalyser.computeSccs(automaton, false);
      int sccCount = sccs.size();
      Map<GeneralizedRabinPair, IntSet> pairActiveSccs = new HashMap<>();

      for (int sccIndex = 0; sccIndex < sccCount; sccIndex++) {
        Set<S> scc = sccs.get(sccIndex);
        Set<GeneralizedRabinPair> pairsInScc = new HashSet<>();
        TransitionUtil.forEachEdgeInSet(automaton::getEdges, scc, (state, edge) ->
          pairs.forEach(pair -> {
            if (pair.contains(edge)) {
              pairsInScc.add(pair);
            }
          }));
        int finalSccIndex = sccIndex;
        pairsInScc.forEach(pair -> pairActiveSccs.computeIfAbsent(pair, k -> new IntAVLTreeSet())
          .add(finalSccIndex));
      }

      Collection<MergeClass> mergeClasses = new ArrayList<>(pairs.size());
      pairActiveSccs.forEach((pair, activeSccs) ->
        mergeClasses.add(new MergeClass(pair, activeSccs)));

      while (true) {
        boolean someMerge = false;
        for (MergeClass mergeClass : mergeClasses) {
          Iterator<MergeClass> candidateIterator = mergeClasses.iterator();
          while (candidateIterator.hasNext()) {
            MergeClass other = candidateIterator.next();
            if (mergeClass.tryMerge(other)) {
              someMerge = true;
              candidateIterator.remove();
            }
          }

          if (someMerge) {
            // Modification to list - restart
            break;
          }
        }
        if (!someMerge) {
          break;
        }
      }

      Int2ObjectMap<IntSet> remapping = new Int2ObjectAVLTreeMap<>();
      mergeClasses.forEach(mergeClass -> {
        Set<GeneralizedRabinPair> mergedPairs = mergeClass.getPairs();
        if (mergedPairs.size() == 1) {
          Iterables.getOnlyElement(mergedPairs).forEachIndex(index ->
            remapping.put(index, IntSets.singleton(index)));
        }

        int representativeFin = mergeClass.getRepresentativeFin();
        IntSet representativeInf = mergeClass.getRepresentativeInf();
        int representativeInfCount = representativeInf.size();

        for (GeneralizedRabinPair mergedPair : mergedPairs) {
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
          .filter(mergeClass -> mergeClass.getPairs().size() > 1)
          .collect(Collectors.toList());
        logger.log(Level.FINER, "Merge classes {0}, indices {1}",
          new Object[] {trueMerges, remapping});
      }

      automaton.remapAcceptance((state, edge) -> {
        if (!edge.hasAcceptanceSets()) {
          return null;
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
        return newAcceptance;
      });

      // Delete the now superfluous indices
      IntSet indicesToRemove = new IntAVLTreeSet(remapping.keySet());
      remapping.values().forEach(indicesToRemove::removeAll);
      MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
      acceptance.removeIndices(indicesToRemove::contains);
    };
  }

  /**
   * Remove edges in a Fin set from all corresponding Inf sets.
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizeOverlap() {
    return automaton -> {
      GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
      List<GeneralizedRabinPair> pairs = acceptance.getPairs().stream()
        .filter(pair -> pair.hasFinite() && pair.hasInfinite())
        .collect(ImmutableList.toImmutableList());
      if (pairs.isEmpty()) {
        return;
      }

      automaton.remapAcceptance((state, edge) -> {
        if (!edge.hasAcceptanceSets()) {
          return null;
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
          return null;
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
        return modifiedAcceptance;
      });
    };
  }

  /**
   * Delete all pairs which only accept if another pair accepts.
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizePairImplications() {
    // TODO If we have bi-implication between pairs, prefer the larger for removal

    return automaton -> {
      GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
      int acceptanceSets = acceptance.getAcceptanceSets();
      Collection<GeneralizedRabinPair> pairs = acceptance.getPairs()
        .stream().filter(pair -> !pair.isEmpty()).collect(Collectors.toList());

      List<Set<S>> sccs = SccAnalyser.computeSccs(automaton, false);

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
        TransitionUtil.forEachEdgeInSet(automaton::getEdges, scc, (state, edge) ->
          edge.acceptanceSetIterator().forEachRemaining((int index) -> {
            BitSet consequences = impliesMap[index];
            IntConsumer consumer = consequent -> {
              if (!edge.inSet(consequent)) {
                consequences.clear(consequent);
              }
            };
            BitSets.forEach(consequences, consumer);
          }));

        Multimap<GeneralizedRabinPair, GeneralizedRabinPair> sccImplications =
          HashMultimap.create(pairs.size(), pairs.size() / 2 + 1);

        // Search for pairs where one implies the other (in terms of acceptance)
        for (GeneralizedRabinPair antecedent : pairs) {
          if (antecedent.isEmpty()) {
            sccImplications.putAll(antecedent, pairs);
            continue;
          }
          for (GeneralizedRabinPair consequent : pairs) {
            if (consequent.isEmpty()) {
              // empty set can never accept
              continue;
            }
            if (antecedent.equals(consequent)) {
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

      Set<GeneralizedRabinPair> pairsToRemove = new HashSet<>();
      for (GeneralizedRabinPair pair : pairs) {
        // See if we can find for each SCC a pair which is implied by this one - then this pair is
        // completely superfluous
        if (sccImplicationList.stream().allMatch(sccImplications -> sccImplications.get(pair)
          .stream().anyMatch(consequent -> !pairsToRemove.contains(consequent)))) {
          pairsToRemove.add(pair);
        }
      }

      List<Set<GeneralizedRabinPair>> pairsToRemoveInSccs = new ArrayList<>(sccs.size());
      for (int sccIndex = 0; sccIndex < sccs.size(); sccIndex++) {
        Multimap<GeneralizedRabinPair, GeneralizedRabinPair> sccImplications =
          sccImplicationList.get(sccIndex);
        Set<GeneralizedRabinPair> pairsToRemoveInScc =
          new HashSet<>(sccImplications.keySet().size());

        // Find pairs to remove in this SCC
        sccImplications.asMap().forEach((antecedent, consequences) -> {
          if (pairsToRemove.contains(antecedent)) {
            // This set will be removed globally - don't consider it
            return;
          }
          // See if we find any pair which accepts more than this pair
          if (consequences.stream().anyMatch(consequent ->
            !pairsToRemove.contains(consequent) && !pairsToRemoveInScc.contains(consequent))) {
            pairsToRemoveInScc.add(antecedent);
          }
        });
        pairsToRemoveInSccs.add(pairsToRemoveInScc);
      }

      if (logBuilder != null) {
        logBuilder.append("\nRemovals:\n  Global: ").append(pairsToRemove);
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
      pairsToRemove.forEach(pair -> pair.forEachIndex(indicesToRemove::add));
      MinimizationUtil.removeAndRemapIndices(automaton, indicesToRemove);
      acceptance.removeIndices(indicesToRemove::contains);

    };
  }

  /**
   * Remove all indices from edges of an SCC which can't accept (e.g. if the SCC does not contain
   * all Inf sets of the pair).
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizeSccIrrelevant() {
    return automaton -> {
      GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
      List<Set<S>> sccs = SccAnalyser.computeSccs(automaton, false);
      Collection<GeneralizedRabinPair> pairs = acceptance.getPairs();

      sccs.forEach(scc -> {
        IntSet indicesInScc = new IntAVLTreeSet();
        TransitionUtil.forEachEdgeInSet(automaton::getEdges, scc, (state, edge) ->
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) indicesInScc::add));

        IntSet indicesToRemove = new IntAVLTreeSet();
        for (GeneralizedRabinPair pair : pairs) {
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
      });
    };
  }

  /**
   * Identify Fin-only pairs and SCCs which trivially accept with them.
   */
  public static <S> Minimization<S, GeneralizedRabinAcceptance> minimizeTrivial() {
    return automaton -> {
      GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
      Collection<GeneralizedRabinPair> pairs = acceptance.getPairs().stream()
        .filter(pair -> pair.hasFinite() && !pair.hasInfinite()).collect(Collectors.toList());
      if (pairs.isEmpty()) {
        return;
      }

      List<Set<S>> sccs = SccAnalyser.computeSccs(automaton, false);
      for (Set<S> scc : sccs) {
        IntSet usedIndices = new IntAVLTreeSet();
        TransitionUtil.forEachEdgeInSet(automaton::getEdges, scc, (state, edge) ->
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) usedIndices::add));
        Optional<GeneralizedRabinPair> trivialAcceptingPair = pairs.stream()
          .filter(pair -> !usedIndices.contains(pair.getFiniteIndex())).findAny();

        if (trivialAcceptingPair.isPresent()) {
          GeneralizedRabinPair pair = trivialAcceptingPair.get();
          usedIndices.remove(pair.getFiniteIndex());
          MinimizationUtil.removeIndices(automaton, scc, usedIndices);
        }
      }
    };
  }

  private static final class MergeClass {
    private final IntSet activeSccIndices;
    private final Set<GeneralizedRabinPair> pairs;
    private final int representativeFin;
    private final IntSet representativeInf;

    MergeClass(GeneralizedRabinPair pair, IntSet activeIndices) {
      this.pairs = new HashSet<>();
      this.pairs.add(pair);
      this.activeSccIndices = new IntAVLTreeSet(activeIndices);

      representativeFin = pair.hasFinite() ? pair.getFiniteIndex() : -1;
      representativeInf = new IntAVLTreeSet();
      pair.forEachInfiniteIndex(representativeInf::add);
    }

    public Set<GeneralizedRabinPair> getPairs() {
      return pairs;
    }

    public int getRepresentativeFin() {
      return representativeFin;
    }

    public IntSet getRepresentativeInf() {
      return representativeInf;
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
