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

package owl.automaton.acceptance;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static jhoafparser.extensions.BooleanExpressions.createDisjunction;
import static jhoafparser.extensions.BooleanExpressions.getConjuncts;
import static jhoafparser.extensions.BooleanExpressions.getDisjuncts;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import javax.annotation.Nonnegative;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;
import owl.automaton.edge.Edge;

/**
 * Generalized Rabin Acceptance - OR (Fin(i) and AND Inf(j)).
 *
 * <p>A generalized Rabin acceptance is formed by a disjunction of a conjunction between one Fin and
 * multiple Inf conditions.</p>
 *
 * <p>According to the HOA specifications, the indices are monotonically increasing and used for
 * exactly one Fin/Inf atom.</p>
 */
public class GeneralizedRabinAcceptance extends OmegaAcceptance {

  final List<RabinPair> pairs;
  @Nonnegative
  private final int setCount;

  GeneralizedRabinAcceptance(List<RabinPair> pairs) {
    this.pairs = List.copyOf(pairs);

    int count = 0;

    // Count sets and check consistency.
    for (RabinPair pair : this.pairs) {
      checkArgument(count == pair.finIndex);
      checkArgument(pair.finIndex <= pair.infIndex);
      count += pair.infSetCount() + 1;
    }

    this.setCount = count;
  }

  @Override
  public BitSet acceptingSet() {
    if (pairs.isEmpty()) {
      throw new NoSuchElementException();
    }

    BitSet set = new BitSet();
    pairs.get(0).forEachInfSet(set::set);
    return set;
  }

  @Override
  public BitSet rejectingSet() {
    BitSet set = new BitSet();
    pairs.forEach(x -> set.set(x.finIndex));
    return set;
  }

  public static GeneralizedRabinAcceptance of(RabinPair... pairs) {
    return of(List.of(pairs));
  }

  public static GeneralizedRabinAcceptance of(List<RabinPair> pairs) {
    return new GeneralizedRabinAcceptance(pairs);
  }

  public static GeneralizedRabinAcceptance of(BooleanExpression<AtomAcceptance> expression) {
    Builder builder = new Builder();
    int setCount = 0;

    for (BooleanExpression<AtomAcceptance> dis : getDisjuncts(expression)) {
      int fin = -1;
      int infSets = 0;

      for (BooleanExpression<AtomAcceptance> element : getConjuncts(dis)) {
        AtomAcceptance atom = element.getAtom();

        switch (atom.getType()) {
          case TEMPORAL_FIN:
            checkArgument(fin == -1);
            fin = atom.getAcceptanceSet();
            checkArgument(fin == setCount);
            break;

          case TEMPORAL_INF:
            checkArgument(fin + infSets + 1 == atom.getAcceptanceSet());
            infSets++;
            break;

          default:
            throw new IllegalArgumentException("Generalized-Rabin Acceptance not well-formed.");
        }

        setCount++;
      }

      checkArgument(fin != -1);
      builder.add(infSets);
    }

    return builder.build();
  }

  @Override
  public int acceptanceSets() {
    return setCount;
  }

  @Override
  public BooleanExpression<AtomAcceptance> booleanExpression() {
    return createDisjunction(pairs.stream().map(RabinPair::booleanExpression));
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

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.largestAcceptanceSet() < setCount;
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

  public static final class RabinPair implements Comparable<RabinPair> {
    @Nonnegative
    final int finIndex;

    // All indices in the interval ]finIndex, infIndex] are considered inf.
    @Nonnegative
    final int infIndex;

    RabinPair(@Nonnegative int finIndex, int infIndex) {
      assert infIndex >= finIndex;
      this.finIndex = finIndex;
      this.infIndex = infIndex;
    }

    public static RabinPair of(@Nonnegative int finIndex) {
      return ofGeneralized(finIndex, 1);
    }

    public static RabinPair ofGeneralized(@Nonnegative int finIndex, @Nonnegative int infSets) {
      return new RabinPair(finIndex, finIndex + infSets);
    }

    public boolean contains(BitSet indices) {
      return !indices.get(finIndex, infIndex + 1).isEmpty();
    }

    public boolean contains(Edge<?> edge) {
      return edge.inSet(finIndex) || containsInfinite(edge);
    }

    /**
     * Checks whether the given edge is contained in any <b>Inf</b> set of this pair.
     *
     * @param edge
     *     The edge to be tested.
     *
     * @return If {@code edge} is contained in any <b>Inf</b> set.
     *
     * @see Edge#inSet(int)
     */
    public boolean containsInfinite(Edge<?> edge) {
      for (int i = finIndex + 1; i <= infIndex; i++) {
        if (edge.inSet(i)) {
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

    private BooleanExpression<AtomAcceptance> booleanExpression() {
      BooleanExpression<AtomAcceptance> acceptance = BooleanExpressions.mkFin(finIndex);

      for (int index = finIndex + 1; index <= infIndex; index++) {
        acceptance = acceptance.and(BooleanExpressions.mkInf(index));
      }

      return acceptance;
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

    public IntIterator infSetIterator() {
      return IntIterators.fromTo(finIndex + 1, infIndex + 1);
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
      if (this == o) {
        return true;
      }

      if (!(o instanceof RabinPair)) {
        return false;
      }

      RabinPair rabinPair = (RabinPair) o;
      return finIndex == rabinPair.finIndex && infIndex == rabinPair.infIndex;
    }

    @Override
    public int hashCode() {
      return Objects.hash(finIndex, infIndex);
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
