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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

@Deprecated
public class GeneralizedRabinAcceptance2<S extends AutomatonState<?>> extends OmegaAcceptance {
  protected final List<RabinPair2<TranSet<S>, List<TranSet<S>>>> acceptanceCondition;
  protected final IdentityHashMap<TranSet<S>, Integer> acceptanceNumbers;

  public GeneralizedRabinAcceptance2(
    List<RabinPair2<TranSet<S>, List<TranSet<S>>>> acceptanceCondition) {
    this.acceptanceCondition = acceptanceCondition;
    for (int j = 0; j < this.acceptanceCondition.size(); j++) {
      RabinPair2<TranSet<S>, List<TranSet<S>>> pair = this.acceptanceCondition.get(j);
      for (int i = 0; i < pair.right.size(); i++) {
        pair.right.set(i, pair.right.get(i).copy());
      }
      this.acceptanceCondition.set(j, new RabinPair2<>(pair.left.copy(), pair.right));
    }
    this.acceptanceNumbers = new IdentityHashMap<>();
  }

  // to be overriden by GeneralisedRabinWithMeanPayoffAcceptance
  protected BooleanExpression<AtomAcceptance> addInfiniteSetsToConjunction(
    BooleanExpression<AtomAcceptance> conjunction, int offset) {
    RabinPair2<TranSet<S>, List<TranSet<S>>> pair = acceptanceCondition.get(offset);
    BooleanExpression<AtomAcceptance> finalConjunct = conjunction;
    for (TranSet<S> inf : pair.right) {
      finalConjunct = finalConjunct.and(HoaConsumerExtended.mkInf(
        acceptanceNumbers.compute(inf, (x, y) -> acceptanceNumbers.size())));
    }
    return finalConjunct;
  }

  public void addPair(RabinPair2<TranSet<S>, List<TranSet<S>>> rabinPair) {
    acceptanceCondition.add(rabinPair);
  }

  public void clear() {
    acceptanceCondition.clear();
  }

  public List<RabinPair2<TranSet<S>, List<TranSet<S>>>> getAcceptanceCondition() {
    return acceptanceCondition;
  }

  @Override
  public int acceptanceSets() {
    return acceptanceCondition.stream().mapToInt(pair -> pair.right.size() + 1).sum();
  }

  @Override
  public BooleanExpression<AtomAcceptance> booleanExpression() {
    BooleanExpression<AtomAcceptance> disjunction = null;

    for (int offset = 0; offset < acceptanceCondition.size(); offset++) {
      RabinPair2<TranSet<S>, List<TranSet<S>>> pair = acceptanceCondition.get(offset);
      BooleanExpression<AtomAcceptance> conjunction = HoaConsumerExtended
        .mkFin(acceptanceNumbers.compute(pair.left, (x, y) -> acceptanceNumbers.size()));

      conjunction = addInfiniteSetsToConjunction(conjunction, offset);

      if (disjunction == null) {
        disjunction = conjunction;
      } else {
        disjunction = disjunction.or(conjunction);
      }
    }

    return disjunction == null ? new BooleanExpression<>(false) : disjunction;
  }

  public IntList getInvolvedAcceptanceNumbers(AutomatonState<?> currentState,
    ValuationSet edgeKey) {
    IntList result = new IntArrayList();
    acceptanceNumbers.keySet().stream().filter(set -> set.containsAll(currentState, edgeKey))
      .forEach(set -> result.add(acceptanceNumbers.get(set).intValue()));
    return result;
  }

  public Set<ValuationSet> getMaximallyMergedEdgesOfEdge(AutomatonState<?> currentState,
    ValuationSet initialValuation) {
    Set<ValuationSet> result = new HashSet<>();
    result.add(initialValuation);

    for (TranSet<S> acceptanceCondition : acceptanceNumbers.keySet()) {
      result = splitAccordingToAcceptanceSet(currentState, result, acceptanceCondition);
    }

    return result;
  }

  @Override
  public String name() {
    return "generalized-Rabin";
  }

  @Override
  public List<Object> nameExtra() {
    List<Object> extra = new ArrayList<>(acceptanceCondition.size() + 1);
    extra.add(acceptanceCondition.size());

    for (RabinPair2<TranSet<S>, List<TranSet<S>>> pair : acceptanceCondition) {
      extra.add(pair.right.size());
    }

    return extra;
  }

  @Override
  public BitSet acceptingSet() {
    throw new UnsupportedOperationException();
  }

  @Override
  public BitSet rejectingSet() {
    throw new UnsupportedOperationException();
  }

  public boolean implies(int premiseIndex, int conclusionIndex) {
    RabinPair2<TranSet<S>, List<TranSet<S>>> premise = acceptanceCondition.get(premiseIndex);
    RabinPair2<TranSet<S>, List<TranSet<S>>> conclusion = acceptanceCondition.get(conclusionIndex);
    return premise.left.containsAll(conclusion.left) && conclusion.right.stream()
      .allMatch(inf2 -> premise.right.stream().anyMatch(inf2::containsAll));
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return true;
  }

  public Map<String, List<Object>> miscellaneousAnnotations() {
    return Collections.emptyMap();
  }

  public void removeIndices(Set<Integer> toRemove) {
    toRemove.stream().sorted(Collections.reverseOrder())
      .forEachOrdered(index -> acceptanceCondition.remove(index.intValue()));
  }

  protected Set<ValuationSet> splitAccordingToAcceptanceSet(AutomatonState<?> currentState,
    Set<ValuationSet> result, TranSet<S> acceptanceCondition) {
    Set<ValuationSet> toRemove = new HashSet<>();
    Set<ValuationSet> toAdd = new HashSet<>();

    for (ValuationSet edge : result) {
      ValuationSet interestingValuationSet = acceptanceCondition.asMap().get(currentState);
      ValuationSetFactory vsFactory = edge.getFactory();
      if (interestingValuationSet != null
        && !vsFactory.intersection(interestingValuationSet, edge).isEmpty()
        && !interestingValuationSet.contains(edge)) {
        toRemove.add(edge);
        toAdd.add(vsFactory.intersection(edge, interestingValuationSet));
        toAdd.add(vsFactory.intersection(edge, vsFactory.complement(interestingValuationSet)));
      }
    }

    result.removeAll(toRemove);
    result.addAll(toAdd);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("GeneralisedRabinAcceptance\n");
    for (RabinPair2<TranSet<S>, List<TranSet<S>>> pair : acceptanceCondition) {
      builder.append("\nPair: ");
      builder.append('\n');
      builder.append('\t');
      builder.append("Fin: ");
      builder.append(pair.left);
      for (TranSet<S> inf : pair.right) {
        builder.append("\n\tInf: ");
        builder.append(inf);
      }
    }
    return builder.toString();
  }

  public List<RabinPair2<TranSet<S>, List<TranSet<S>>>> unmodifiableCopyOfAcceptanceCondition() {
    return Collections.unmodifiableList(acceptanceCondition);
  }
}
