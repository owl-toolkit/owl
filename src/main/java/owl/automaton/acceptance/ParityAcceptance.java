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

package owl.automaton.acceptance;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnegative;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.algorithms.SccAnalyser;
import owl.automaton.AutomatonUtil;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.output.HoaConsumerExtended;

public final class ParityAcceptance implements OmegaAcceptance {
  @Nonnegative
  private int colours;
  private Priority priority;

  public ParityAcceptance(@Nonnegative int colours) {
    this(colours, Priority.ODD);
  }

  public ParityAcceptance(@Nonnegative int colours, Priority priority) {
    this.colours = colours;
    this.priority = priority;
  }

  public void complement() {
    priority = priority.not();
  }

  @Override
  public <S> boolean containsAcceptingRun(Set<S> scc,
    Function<S, Iterable<Edge<S>>> successorFunction) {
    assert AutomatonUtil.isScc(scc, successorFunction);
    assert scc.parallelStream()
      .map(successorFunction).flatMap(Streams::stream)
      .map(Edge::acceptanceSetIterator)
      .allMatch(iter -> Iterators.size(iter) <= 1) : "Not a proper parity successor function";

    return new AcceptanceAnalyser<>(this, scc, successorFunction).run();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ParityAcceptance that = (ParityAcceptance) o;
    return colours == that.colours && priority == that.priority;
  }

  @Override
  public int getAcceptanceSets() {
    return colours;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    if (colours == 0) {
      return new BooleanExpression<>(priority == Priority.EVEN);
    }

    int index = colours - 1;

    BooleanExpression<AtomAcceptance> exp = mkColor(index);

    for (index--; 0 <= index; index--) {
      if (index % 2 == 0 ^ priority == Priority.EVEN) {
        exp = mkColor(index).and(exp);
      } else {
        exp = mkColor(index).or(exp);
      }
    }

    return exp;
  }

  @Override
  public String getName() {
    return "parity";
  }

  @Override
  public List<Object> getNameExtra() {
    return Arrays.asList("min", priority.toString(), colours);
  }

  public Priority getPriority() {
    return priority;
  }

  @Override
  public int hashCode() {
    return 31 * colours + priority.hashCode();
  }

  private boolean isAccepting(int priority) {
    return priority % 2 == 0 ^ this.priority == Priority.ODD;
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    PrimitiveIterator.OfInt iterator = edge.acceptanceSetIterator();
    if (!iterator.hasNext()) {
      // TODO Is this true?
      return true;
    }
    int firstIndex = iterator.nextInt();
    return !iterator.hasNext() && firstIndex < colours;
  }

  private BooleanExpression<AtomAcceptance> mkColor(int i) {
    return (i % 2 == 0 ^ priority == Priority.EVEN)
      ? HoaConsumerExtended.mkFin(i)
      : HoaConsumerExtended.mkInf(i);
  }

  public void setAcceptanceSets(@Nonnegative int colors) {
    this.colours = colors;
  }

  @SuppressWarnings("MethodReturnAlwaysConstant")
  public enum Priority {
    EVEN {
      @Override
      public Priority not() {
        return ODD;
      }

      @Override
      public String toString() {
        return "even";
      }
    },

    ODD {
      @Override
      public Priority not() {
        return EVEN;
      }

      @Override
      public String toString() {
        return "odd";
      }
    };

    public abstract Priority not();
  }

  private static final class AcceptanceAnalyser<S> {
    private static final Comparator<AnalysisResult<?>> greedyPriority = (one, other) -> {
      // Prefer smaller SCCs
      int sizeCompare = Integer.compare(one.scc.size(), other.scc.size());
      if (sizeCompare != 0) {
        return sizeCompare;
      }

      // Prefer bigger priorities
      return -Integer.compare(one.minimalPriority, other.minimalPriority);
    };

    private final ParityAcceptance acceptance;
    private final Queue<AnalysisResult<S>> sccProcessingQueue;
    private final Function<S, Iterable<Edge<S>>> successorFunction;

    AcceptanceAnalyser(ParityAcceptance acceptance, Set<S> scc,
      Function<S, Iterable<Edge<S>>> successorFunction) {
      this.acceptance = acceptance;
      this.successorFunction = successorFunction;
      this.sccProcessingQueue = new PriorityQueue<>(greedyPriority);
      int initialPriority = acceptance.getPriority() == Priority.EVEN ? 0 : 1;
      sccProcessingQueue.add(new AnalysisResult<>(scc, initialPriority));
    }

    boolean run() {
      while (!sccProcessingQueue.isEmpty()) {
        AnalysisResult<S> result = sccProcessingQueue.poll();
        Set<S> scc = result.scc;
        int minimalPriorityInScc = result.minimalPriority;
        assert !acceptance.isAccepting(minimalPriorityInScc);

        Collection<Set<S>> subSccs;
        if (minimalPriorityInScc == -1) {
          // First run with EVEN acceptance - don't need to filter / refine SCC.
          subSccs = ImmutableList.of(scc);
        } else {
          // Remove all the edges rejecting at a higher priority - there can't be an accepting one
          // with priority less than minimalPriorityInScc, since otherwise the search would have
          // terminated before adding this sub-SCC.

          Function<S, Iterable<Edge<S>>> filteredSuccessorFunction =
            AcceptanceHelper.filterSuccessorFunction(successorFunction, edge -> {
              if (!scc.contains(edge.getSuccessor())) {
                return false;
              }
              PrimitiveIterator.OfInt acceptanceIterator = edge.acceptanceSetIterator();
              return !acceptanceIterator.hasNext()
                || acceptanceIterator.nextInt() > minimalPriorityInScc;
            });
          subSccs = SccAnalyser.computeSccs(scc,
            filteredSuccessorFunction.andThen(Edges::toSuccessors));
        }
        assert subSccs.stream().allMatch(scc::containsAll);

        for (Set<S> subScc : subSccs) {
          int min = Integer.MAX_VALUE;
          for (S state : subScc) {
            // For each state, get the lowest priority of all edges inside the scc
            Iterable<Edge<S>> successorEdges = successorFunction.apply(state);
            for (Edge<S> successorEdge : successorEdges) {
              if (!subScc.contains(successorEdge.getSuccessor())) {
                continue;
              }
              PrimitiveIterator.OfInt acceptanceIterator = successorEdge.acceptanceSetIterator();
              if (!acceptanceIterator.hasNext()) {
                continue;
              }
              if (acceptanceIterator.nextInt() <= minimalPriorityInScc) {
                continue;
              }
              min = Math.min(acceptanceIterator.nextInt(), min);
            }
            if (min == minimalPriorityInScc + 1) {
              // sccMinimalPriority is the minimal priority not filtered, there won't be anything
              // smaller than this. Furthermore, it is accepting by invariant.
              assert acceptance.isAccepting(min);
              return true;
            }
          }
          if (min == Integer.MAX_VALUE) {
            // No internal transitions with priorities
            continue;
          }
          if (acceptance.isAccepting(min)) {
            // This SCC contains an accepting cycle: Since each state has at least one cycle
            // containing it (by definition of SCC) and we found an accepting edge where a) the
            // successor is contained in the SCC (hence there is a cycle containing this edge)
            // and b) the cycle contains no edge with a priority smaller than the
            // minimalCyclePriority, since we pruned away all smaller ones.
            return true;
          }

          // The scc is not accepting, add it to the work stack
          sccProcessingQueue.add(new AnalysisResult<>(subScc, min));
        }
      }

      return false;
    }

    @SuppressWarnings("PackageVisibleField")
    private static final class AnalysisResult<S> {
      final int minimalPriority;
      final Set<S> scc;

      AnalysisResult(Set<S> scc, int minimalPriority) {
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        this.scc = scc;
        this.minimalPriority = minimalPriority;
      }
    }
  }
}
