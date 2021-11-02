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

package owl.automaton.acceptance;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.Variable;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import javax.annotation.Nonnegative;
import owl.automaton.edge.Edge;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

/**
 * Generalized Rabin Acceptance - OR (Fin(i) and AND Inf(j)).
 *
 * <p>A generalized Rabin acceptance is formed by a disjunction of a conjunction between one Fin and
 * multiple Inf conditions.</p>
 *
 * <p>According to the HOA specifications, the indices are monotonically increasing and used for
 * exactly one Fin/Inf atom.</p>
 */
public sealed class GeneralizedRabinAcceptance extends EmersonLeiAcceptance
  permits RabinAcceptance {

  protected final List<RabinPair> pairs;

  GeneralizedRabinAcceptance(List<RabinPair> pairs) {
    super(validate(pairs));
    this.pairs = List.copyOf(pairs);
    assert pairs == this.pairs
      : "List.copyOf() created a copy, but pairs should have been already immutable.";
  }

  private static int validate(List<RabinPair> pairs) {
    int count = 0;

    // Count sets and check consistency.
    for (RabinPair pair : pairs) {
      checkArgument(count == pair.finIndex);
      checkArgument(pair.finIndex <= pair.infIndex);
      count += pair.infSetCount() + 1;
    }

    return count;
  }

  @Override
  public Optional<ImmutableBitSet> acceptingSet() {
    if (pairs.isEmpty()) {
      return Optional.empty();
    }

    BitSet set = new BitSet();
    pairs.get(0).forEachInfSet(set::set);
    return Optional.of(ImmutableBitSet.copyOf(set));
  }

  @Override
  public Optional<ImmutableBitSet> rejectingSet() {
    BitSet set = new BitSet();
    pairs.forEach(x -> set.set(x.finIndex));
    return Optional.of(ImmutableBitSet.copyOf(set));
  }

  public static GeneralizedRabinAcceptance of(RabinPair... pairs) {
    return of(List.of(pairs));
  }

  public static GeneralizedRabinAcceptance of(List<RabinPair> pairs) {
    return new GeneralizedRabinAcceptance(List.copyOf(pairs));
  }

  public static Optional<? extends GeneralizedRabinAcceptance> ofPartial(
    PropositionalFormula<Integer> expression) {

    SortedMap<Integer, Range<Integer>> rabinPairs = new TreeMap<>();

    for (PropositionalFormula<Integer> dis : PropositionalFormula.disjuncts(expression)) {
      int fin = -1;
      var infSets = new BitSet();

      for (PropositionalFormula<Integer> element : PropositionalFormula.conjuncts(dis)) {

        if (element instanceof Variable<Integer> variable) { // TEMPORAL_INF
          infSets.set(variable.variable());
        } else if (element instanceof Negation<Integer> negation) { // TEMPORAL_FIN
          if (fin != -1) {
            return Optional.empty();
          }

          fin = ((Variable<Integer>) negation.operand()).variable();
        } else {
          return Optional.empty();
        }
      }

      // There is either no fin or it is already used.
      if (fin < 0 || rabinPairs.containsKey(fin)) {
        return Optional.empty();
      }

      int lower = infSets.nextSetBit(0);
      int upper = infSets.previousSetBit(infSets.length());

      // Range is empty.
      if (lower == -1) {
        rabinPairs.put(fin, Range.closedOpen(fin + 1, fin + 1));
      } else { // Range contains at least one element.
        // Range not continuous.
        if (infSets.cardinality() != (upper + 1) - lower) {
          return Optional.empty();
        }

        rabinPairs.put(fin, Range.closedOpen(lower, upper + 1));
      }
    }

    Builder builder = new Builder();
    int setCount = 0;

    for (Map.Entry<Integer, Range<Integer>> entry : rabinPairs.entrySet()) {
      int fin = entry.getKey();
      Range<Integer> infs = entry.getValue();

      assert infs.lowerBoundType() == BoundType.CLOSED && infs.upperBoundType() == BoundType.OPEN;
      if (fin != setCount || infs.lowerEndpoint() != fin + 1) {
        return Optional.empty();
      }

      setCount = infs.upperEndpoint();
      builder.add(infs.upperEndpoint() - infs.lowerEndpoint());
    }

    return Optional.of(builder.build());
  }

  @Override
  public PropositionalFormula<Integer> lazyBooleanExpression() {
    return Disjunction.of(
      pairs.stream().map(RabinPair::booleanExpression).toList());
  }

  @Override
  public String name() {
    return "generalized-Rabin";
  }

  @Override
  public List<Object> nameExtra() {
    // <pair_count> <inf_pairs_of_1> <inf_pairs_of_2> <...>
    List<Object> extra = new ArrayList<>(pairs.size() + 1);
    extra.add(pairs.size());

    for (RabinPair pair : pairs) {
      extra.add(pair.infSetCount());
    }

    return extra;
  }

  /**
   * Returns an unmodifiable view of the pair collection.
   *
   * @return The rabin pairs of this acceptance condition
   */
  public List<RabinPair> pairs() {
    return pairs;
  }

  public GeneralizedRabinAcceptance filter(IntPredicate predicate) {
    List<RabinPair> newPairs = new ArrayList<>(pairs.size());
    int removedIndices = 0;

    for (RabinPair pair : pairs) {
      if (predicate.test(pair.finIndex)) {
        removedIndices += pair.infSetCount() + 1;
      } else {
        int removedInfIndices = 0;

        for (int i = pair.finIndex + 1; i <= pair.infIndex; i++) {
          if (predicate.test(i)) {
            removedInfIndices++;
          }
        }

        assert pair.finIndex >= removedIndices;
        assert pair.infIndex >= removedIndices + removedInfIndices;

        newPairs.add(new RabinPair(pair.finIndex - removedIndices,
          pair.infIndex - (removedIndices + removedInfIndices)));
        removedIndices += removedInfIndices;
      }
    }

    if (newPairs.stream().allMatch(x -> x.infSetCount() == 1)) {
      return RabinAcceptance.of(newPairs);
    }

    return GeneralizedRabinAcceptance.of(newPairs);
  }

  // All indices in the interval ]finIndex, infIndex] are considered inf.
  public record RabinPair(@Nonnegative int finIndex, @Nonnegative int infIndex)
    implements Comparable<RabinPair> {

    public RabinPair {
      checkArgument(finIndex >= 0);
      checkArgument(infIndex >= finIndex);
    }

    public static RabinPair of(@Nonnegative int finIndex) {
      return ofGeneralized(finIndex, 1);
    }

    public static RabinPair ofGeneralized(@Nonnegative int finIndex, @Nonnegative int infSets) {
      return new RabinPair(finIndex, finIndex + infSets);
    }

    public boolean contains(ImmutableBitSet indices) {
      return !indices.copyInto(new BitSet()).get(finIndex, infIndex + 1).isEmpty();
    }

    public boolean contains(Edge<?> edge) {
      return edge.colours().contains(finIndex) || containsInfinite(edge);
    }

    /**
     * Checks whether the given edge is contained in any <b>Inf</b> set of this pair.
     *
     * @param edge
     *     The edge to be tested.
     *
     * @return If {@code edge} is contained in any <b>Inf</b> set.
     *
     * @see Edge#colours()
     */
    public boolean containsInfinite(Edge<?> edge) {
      for (int i = finIndex + 1; i <= infIndex; i++) {
        if (edge.colours().contains(i)) {
          return true;
        }
      }

      return false;
    }

    public void forEachIndex(IntConsumer action) {
      action.accept(finIndex);
      forEachInfSet(action);
    }

    public void forEachInfSet(IntConsumer action) {
      for (int i = finIndex + 1; i <= infIndex; i++) {
        action.accept(i);
      }
    }

    private PropositionalFormula<Integer> booleanExpression() {
      List<PropositionalFormula<Integer>> conjuncts = new ArrayList<>();

      conjuncts.add(Negation.of(Variable.of(finIndex)));
      for (int index = finIndex + 1; index <= infIndex; index++) {
        conjuncts.add(Variable.of(index));
      }

      return Conjunction.of(conjuncts);
    }

    @Nonnegative
    public int finSet() {
      return finIndex;
    }

    @Nonnegative
    public int infSet(int number) {
      assert finIndex + number < infIndex;
      return finIndex + 1 + number;
    }

    @Nonnegative
    public int infSetCount() {
      return infIndex - finIndex;
    }

    public boolean hasInfSet() {
      return infSetCount() > 0;
    }

    public IntStream infSetStream() {
      return IntStream.rangeClosed(finIndex + 1, infIndex);
    }

    public boolean isInfinite(int i) {
      return finIndex < i && i <= infIndex;
    }

    public int infSet() {
      checkState(infSetCount() == 1);
      return infIndex;
    }

    @Override
    public String toString() {
      return "(" + finIndex + ", " + infIndex + ')';
    }

    @Override
    public int compareTo(RabinPair o) {
      return Comparator.comparingInt((RabinPair x) -> x.finIndex)
        .thenComparingInt((RabinPair x) -> x.infIndex)
        .compare(this, o);
    }
    
    @Override
    public boolean equals(Object o) {
      return this == o
        || o instanceof RabinPair that && finIndex == that.finIndex && infIndex == that.infIndex;
    }

    @Override
    public int hashCode() {
      return 31 * (31 + finIndex) + infIndex;
    }
  }

  public static final class Builder {
    private final List<RabinPair> pairs = new ArrayList<>();
    private int sets = 0;

    public RabinPair add(@Nonnegative int infSets) {
      RabinPair pair = new RabinPair(sets, sets + infSets);
      pairs.add(pair);
      sets += 1 + infSets;
      return pair;
    }

    public GeneralizedRabinAcceptance build() {
      return of(pairs);
    }
  }
}
