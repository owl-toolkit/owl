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

package owl.automaton.symbolic;

import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.COLOUR;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.STATE;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.SUCCESSOR_STATE;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

@AutoValue
public abstract class SymbolicDRA2DPAConstruction {

  public abstract SymbolicAutomaton<?> automaton();

  public static SymbolicDRA2DPAConstruction of(SymbolicAutomaton<?> automaton) {
    Preconditions.checkArgument(automaton.is(Automaton.Property.DETERMINISTIC));
    return new AutoValue_SymbolicDRA2DPAConstruction(automaton);
  }

  private List<RabinPair> toRabin() {
    return PropositionalFormula.disjuncts(automaton().acceptance().booleanExpression()).stream()
      .map(disjunct -> {
        var conjuncts = ((PropositionalFormula.Conjunction<Integer>) disjunct).conjuncts();
        assert conjuncts.size() == 2;
        int finSet = -1;
        int infSet = -1;
        if (conjuncts.get(0) instanceof PropositionalFormula.Variable<Integer> variable) {
          infSet = variable.variable();
        } else {
          finSet =
            ((PropositionalFormula.Variable<Integer>)
              ((PropositionalFormula.Negation<Integer>) conjuncts
            .get(0)).operand()
            ).variable();
        }
        if (conjuncts.get(1) instanceof PropositionalFormula.Variable<Integer> variable) {
          infSet = variable.variable();
        } else {
          finSet =
            ((PropositionalFormula.Variable<Integer>)
              ((PropositionalFormula.Negation<Integer>) conjuncts
            .get(1)).operand()).variable();
        }
        return new RabinPair(finSet, infSet);
      }
    ).toList();
  }

  /**
   * Converts the acceptance condition {@code acceptance()} to a parity condition
   * for the automaton {@code automaton()} or returns {@code Optional.Empty} if not possible.
   * It is only possible to do this conversion if there exists an equivalent Streett acceptance
   * condition on this same automaton.
   *
   * @return a symbolic parity automaton with the structure of {@code automaton()}, or {@code
   * Optional.Empty} if {@code automaton()} has no equivalent Streett acceptance condition.
   */
  public Optional<SymbolicAutomaton<ParityAcceptance>> tryToParity() {
    var paritySets = getParitySets(automaton().reachableStates(), toRabin());
    return paritySets.map(this::updateAcceptanceConditionWithParitySets);
  }

  /**
   * Like {@link #tryToParity} but expects that there is an equivalent Streett acceptance condition.
   * Only use if an equivalent Streett acceptance condition is guaranteed for {@code automaton()}
   *
   * @return a symbolic parity automaton with the structure of {@code automaton()}
   */
  public SymbolicAutomaton<ParityAcceptance> toParity() {
    return tryToParity().orElseThrow();
  }

  /**
   * Constructs a new symbolic automaton with the structure of {@code automaton()}
   * based on the given parity condition.
   *
   * @param paritySets
   *   the parity condition
   *
   * @return a symbolic parity automaton.
   */
  private SymbolicAutomaton<ParityAcceptance> updateAcceptanceConditionWithParitySets(
    List<BddSet> paritySets) {
    // All (reachable) states are in at least one parity set
    assert paritySets.stream().reduce(paritySets.get(0).factory().of(false), BddSet::union)
      .equals(automaton().reachableStates());
    // All states are in at most one parity set
    assert paritySetsDisjoint(paritySets);
    int coloursNeeded = paritySets.size();
    int parityColoursOffset = automaton().variableAllocation().numberOfVariables();

    BitSet parityColours = new BitSet();
    parityColours.set(parityColoursOffset, parityColoursOffset + coloursNeeded);
    BddSet parityColoursBdd = automaton().factory().of(false);
    for (int i = 0; i < coloursNeeded; i++) {
      BitSet parityColour = new BitSet();
      parityColour.set(parityColoursOffset + i);
      BddSet parityColourBdd = automaton().factory().of(parityColour, parityColours);
      parityColoursBdd = parityColoursBdd.union(
        parityColourBdd.intersection(paritySets.get(i).relabel(variable -> {
          var variableType = automaton().variableAllocation().typeOf(variable);
          assert variableType == STATE || variableType == COLOUR;
          if (variableType == STATE) {
            return automaton().variableAllocation().localToGlobal(
              automaton().variableAllocation().globalToLocal(variable, STATE),
              SUCCESSOR_STATE
            );
          }
          return variable;
        }))
      );
    }

    return SymbolicAutomaton.of(
      automaton().atomicPropositions(),
      automaton().initialStates(),
      automaton().transitionRelation().intersection(parityColoursBdd),
      new ParityAcceptance(coloursNeeded, ParityAcceptance.Parity.MIN_EVEN),
      new ParityVariableAllocation(automaton().variableAllocation(), coloursNeeded),
      automaton().properties(),
      automaton().variableAllocation().variables(COLOUR).size()
    );
  }

  private static boolean paritySetsDisjoint(List<BddSet> paritySets) {
    for (int i = 0; i < paritySets.size(); i++) {
      for (int j = i + 1; j < paritySets.size(); j++) {
        if (!paritySets.get(i).intersection(paritySets.get(j)).isEmpty()) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Converts a rabin condition to a parity condition.
   *
   * @param restrictedTo
   *   the set of states to consider
   * @param rabinPairs
   *   the rabin condition
   *
   * @return a parity condition or {@code Optional.Empty} if no such parity condition can be found.
   */
  private Optional<List<BddSet>> getParitySets(BddSet restrictedTo, List<RabinPair> rabinPairs) {
    BddSet hopelessStates = hopelessStates(restrictedTo, rabinPairs);
    assert restrictedTo.containsAll(hopelessStates);
    List<BddSet> hopelessFreeSccs = SymbolicSccDecomposition.of(automaton())
      .sccs(restrictedTo.intersection(hopelessStates.complement()));
    // Recursively compute the parity condition for each SCC
    List<List<BddSet>> sccParitySets = new ArrayList<>(hopelessFreeSccs.size());
    for (BddSet scc : hopelessFreeSccs) {
      Optional<List<BddSet>> paritySet = getParitySetsForHopelessFreeSCC(scc, rabinPairs);
      if (paritySet.isEmpty()) {
        return Optional.empty();
      }
      sccParitySets.add(paritySet.get());
    }
    List<BddSet> paritySets = new ArrayList<>();
    paritySets.add(automaton().factory().of(false));
    paritySets.add(hopelessStates);
    paritySets.addAll(mergeParitySets(sccParitySets));
    return Optional.of(paritySets);
  }

  /**
   * Converts a rabin condition to a parity condition for a hopeless free SCC.
   *
   * @param hopelessFreeScc
   *   the hopeless free SCC
   * @param rabinPairs
   *   the rabin condition
   *
   * @return a parity condition or {@code Optional.Empty} if no such parity condition can be found.
   */
  private Optional<List<BddSet>> getParitySetsForHopelessFreeSCC(BddSet hopelessFreeScc,
    List<RabinPair> rabinPairs) {
    if (rabinPairs.size() == 1) {
      return Optional.of(singleRabinPairToParity(hopelessFreeScc, rabinPairs.get(0)));
    }
    Optional<RabinPair> emptyFinSetPair = findEmptyFinSetPair(
      hopelessFreeScc,
      rabinPairs);
    if (emptyFinSetPair.isEmpty()) {
      // Apparently there is no equivalent Streett condition on this automaton,
      // so we cannot construct an equivalent parity condition
      return Optional.empty();
    }
    // Split the acceptance condition such that the language of the automaton is exactly
    // the language of the automaton with acceptance condition emptyFinSetPair and the language
    // of the automaton with the acceptance condition newPairs. Both languages are disjoint.
    List<RabinPair> newPairs = splitOnPair(rabinPairs, emptyFinSetPair.orElseThrow());
    // Compute the non-hopeless SCCs
    BddSet hopelessStates = hopelessStates(hopelessFreeScc, newPairs);
    assert hopelessFreeScc.containsAll(hopelessStates);
    List<BddSet> nonHopelessSccs = SymbolicSccDecomposition.of(automaton())
      .sccs(hopelessFreeScc.intersection(hopelessStates.complement()));
    assert nonHopelessSccs.stream()
      .allMatch(scc -> hopelessFreeScc.containsAll(scc)
        && hopelessStates.intersection(scc).isEmpty()
      );
    // Recursively compute the parity condition for each SCC
    List<List<BddSet>> sccParitySets = new ArrayList<>(nonHopelessSccs.size());
    for (BddSet scc : nonHopelessSccs) {
      Optional<List<BddSet>> paritySet = getParitySetsForHopelessFreeSCC(scc, newPairs);
      if (paritySet.isEmpty()) {
        return Optional.empty();
      }
      sccParitySets.add(paritySet.get());
    }
    // Merge the parity conditions of all the SCCs
    List<BddSet> mergedParitySets = mergeParitySets(sccParitySets);
    // Merge the parity condition for emptyFinSetPair and the parity condition for the sccs
    BddSet infBdd = emptyFinSetPair.get().infBdd();
    List<BddSet> paritySets = new ArrayList<>();
    paritySets.add(infBdd.intersection(hopelessFreeScc));
    paritySets.add(infBdd.complement().intersection(hopelessStates).intersection(hopelessFreeScc));
    paritySets.addAll(mergedParitySets);
    return Optional.of(paritySets);
  }

  /**
   * Converts a single Rabin pair to a parity condition.
   *
   * @param restrictedTo
   *   the set of states that should be considered
   * @param pair
   *   the Rabin pair
   *
   * @return the parity condition
   */
  private List<BddSet> singleRabinPairToParity(BddSet restrictedTo, RabinPair pair) {
    BddSet finVariables = pair.finBdd();
    BddSet infVariables = pair.infBdd();
    return List.of(
      automaton().factory().of(false),
      finVariables.intersection(restrictedTo),
      infVariables.intersection(finVariables.complement()).intersection(restrictedTo),
      finVariables.complement().intersection(infVariables.complement()).intersection(restrictedTo)
    );
  }

  /**
   * Merges a list of parity conditions into a single parity condition by combining all
   * parity sets on the same index.
   *
   * @param paritySets
   *   The parity conditions to merge
   * @param exclude
   *   A set of states that should be excluded from the final result
   *
   * @return The merged parity condition
   */
  private List<BddSet> mergeParitySets(List<List<BddSet>> paritySets, BddSet exclude) {
    // Get the maximal number of parity sets of all the parity conditions
    int maxParity = paritySets.stream().map(List::size).reduce(0, Integer::max);
    List<BddSet> mergedParitySets = new ArrayList<>(maxParity);
    for (int i = 0; i < maxParity; i++) {

      // Get the ith set of each parity condition (or false if there is no ith set)
      // and merge them
      BddSet acc = automaton().factory().of(false);

      for (List<BddSet> sets : paritySets) {
        if (i < sets.size()) {
          acc = acc.union(sets.get(i));
        }
      }

      mergedParitySets.add(exclude.complement().intersection(acc));
    }

    return mergedParitySets;
  }

  private List<BddSet> mergeParitySets(List<List<BddSet>> paritySets) {
    return mergeParitySets(paritySets, automaton().factory().of(false));
  }

  /**
   * Returns a Rabin pair where the finset is empty with respect to the states in {@code
   * restrictedTo}.
   *
   * @return The pair where the finset is empty or {@code Optional.Empty} if there is no such pair.
   */
  private Optional<RabinPair> findEmptyFinSetPair(BddSet restrictedTo, List<RabinPair> rabinPairs) {
    BddSetFactory factory = automaton().factory();
    var allocation = automaton().variableAllocation();
    for (var pair : rabinPairs) {
      if (pair.finIndices.stream().allMatch(finSet ->
        restrictedTo.intersection(factory.of(allocation.localToGlobal(finSet, COLOUR)))
          .isEmpty())) {
        return Optional.of(pair);
      }
    }
    return Optional.empty();
  }

  /**
   * Splits the given acceptance condition on the given pair. The pair is removed from the list
   * of pairs and each pair is adapted such that any accepting word is either accepted by the
   * given pair
   * or accepted by one of the pairs in the list, but never by both.
   *
   * @param pairs
   *   the list of Rabin pairs, including {@code pair}
   * @param pair
   *   the pair to split on
   *
   * @return the acceptance condition that matches all accepting words that are not accepted by
   * {@code pair}
   */
  private List<RabinPair> splitOnPair(List<RabinPair> pairs, RabinPair pair) {
    return pairs
      .stream()
      .filter(pair2 -> !pair2.equals(pair))
      .map(pair2 -> {
        Set<Integer> newFinSets = new HashSet<>(pair2.finIndices);
        newFinSets.addAll(pair.infIndices);
        return new RabinPair(newFinSets, pair2.infIndices);
      })
      .toList();
  }

  /**
   * Computes the hopeless states in {@code restrictedTo} with respect to the {@code rabinPairs}.
   * A state is hopeless if every run visiting the state infinitely often is rejecting.
   *
   * @param restrictedTo
   *   the set of states to consider
   * @param rabinPairs
   *   a set of rabin pairs where the first element of the pair are the colours
   *     representing the finset and the second element are the colours representing the infset
   *
   * @return the hopeless states in {@code restrictedTo}
   */
  private BddSet hopelessStates(BddSet restrictedTo, List<RabinPair> rabinPairs) {
    BddSet hopelessStates = restrictedTo;
    SymbolicSccDecomposition sccDecomposition = SymbolicSccDecomposition.of(automaton());
    for (var pair : rabinPairs) {
      List<BddSet> sccs = sccDecomposition
        .sccs(restrictedTo.intersection(pair.finBdd().complement()));
      BddSet hopelessStatesForPair = pair.finBdd();
      for (BddSet scc : sccs) {
        if (sccDecomposition.isTrivialScc(scc) || scc.intersection(pair.infBdd()).isEmpty()) {
          hopelessStatesForPair = hopelessStatesForPair.union(scc);
        }
      }
      hopelessStates = hopelessStates.intersection(hopelessStatesForPair);
    }
    return hopelessStates;
  }

  private class RabinPair {

    private final Set<Integer> finIndices;
    private final Set<Integer> infIndices;

    private BddSet finBdd() {
      return asBdd(finIndices);
    }

    private BddSet infBdd() {
      return asBdd(infIndices);
    }

    private RabinPair(Set<Integer> finIndices, Set<Integer> infIndices) {
      this.finIndices = Set.copyOf(finIndices);
      this.infIndices = Set.copyOf(infIndices);
    }

    private RabinPair(int finIndex, int infIndex) {
      this.finIndices = Set.of(finIndex);
      this.infIndices = Set.of(infIndex);
    }

    /**
     * Converts a set of colours to a BDD holding all states that have at least one of the colours.
     *
     * @param colours
     *   a set of colours
     *
     * @return a BDD holding all states that have at least one of the colours
     */
    private BddSet asBdd(Set<Integer> colours) {
      return colours
        .stream()
        .map(variable -> automaton()
          .factory()
          .of(
            automaton()
              .variableAllocation()
              .localToGlobal(variable, COLOUR)
          ))
        .reduce(automaton().factory().of(false), BddSet::union);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      RabinPair rabinPair = (RabinPair) o;
      return finIndices.equals(rabinPair.finIndices) && infIndices.equals(rabinPair.infIndices);
    }

    @Override
    public int hashCode() {
      return Objects.hash(finIndices, infIndices);
    }
  }

  private static class ParityVariableAllocation implements SymbolicAutomaton.VariableAllocation {
    private final SymbolicAutomaton.VariableAllocation rabinAllocation;
    private final ImmutableBitSet parityColourVariables;
    private final int numberOfRabinColours;

    ParityVariableAllocation(SymbolicAutomaton.VariableAllocation rabinAllocation, int paritySets) {
      this.rabinAllocation = rabinAllocation;
      this.numberOfRabinColours = rabinAllocation.variables(COLOUR).size();
      int numberOfRabinVariables = rabinAllocation.numberOfVariables();
      BitSet parityColourVariableBitset = new BitSet();
      parityColourVariableBitset.set(numberOfRabinVariables, numberOfRabinVariables + paritySets);
      parityColourVariables = ImmutableBitSet.copyOf(parityColourVariableBitset);
    }


    @Override
    public ImmutableBitSet variables(SymbolicAutomaton.VariableType... types) {
      var typeSet = Set.of(types);
      if (typeSet.contains(COLOUR)) {
        return rabinAllocation.variables(types).union(parityColourVariables);
      }
      return rabinAllocation.variables(types);
    }

    @Override
    public int numberOfVariables() {
      return rabinAllocation.numberOfVariables() + parityColourVariables.size();
    }

    @Override
    public List<String> variableNames() {
      List<String> variables = new ArrayList<>(numberOfVariables());
      variables.addAll(rabinAllocation.variableNames());
      int numberOfRabinColours = rabinAllocation.variables(COLOUR).size();
      variables.addAll(IntStream.range(0, parityColourVariables.size()).mapToObj(i ->
        "c_" + (i + numberOfRabinColours)).toList());
      return variables;
    }

    @Override
    public int localToGlobal(int variable, SymbolicAutomaton.VariableType type) {
      if (type == COLOUR && variable >= numberOfRabinColours) {
        return rabinAllocation.numberOfVariables() + (variable - numberOfRabinColours);
      }
      return rabinAllocation.localToGlobal(variable, type);
    }

    @Override
    public int globalToLocal(int variable, SymbolicAutomaton.VariableType type) {
      if (type == COLOUR && variable >= rabinAllocation.numberOfVariables()) {
        return numberOfRabinColours + variable - rabinAllocation.numberOfVariables();
      }
      return rabinAllocation.globalToLocal(variable, type);
    }
  }
}
