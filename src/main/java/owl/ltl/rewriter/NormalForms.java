/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.ltl.rewriter;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import owl.collections.Collections3;
import owl.collections.UpwardClosedSet;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.visitors.PropositionalVisitor;

public final class NormalForms {
  private NormalForms() {}

  public static Formula toCnfFormula(Formula formula) {
    return toCnf(formula).stream()
      .map(Disjunction::of)
      .reduce(BooleanConstant.TRUE, Conjunction::of);
  }

  public static Set<Set<Formula>> toCnf(Formula formula) {
    var visitor = new ConjunctiveNormalFormVisitor();
    var cnf = formula.accept(visitor).representatives();
    return new ClausesView(cnf, visitor.literals());
  }

  public static Formula toDnfFormula(Formula formula) {
    return toDnf(formula).stream()
      .map(Conjunction::of)
      .reduce(BooleanConstant.FALSE, Disjunction::of);
  }

  public static Set<Set<Formula>> toDnf(Formula formula) {
    var visitor = new DisjunctiveNormalFormVisitor();
    var dnf = formula.accept(visitor).representatives();
    return new ClausesView(dnf, visitor.literals());
  }

  private abstract static class AbstractNormalFormVisitor
    extends PropositionalVisitor<UpwardClosedSet> {
    private final Map<Formula, Integer> literals;

    private AbstractNormalFormVisitor() {
      this.literals = new LinkedHashMap<>();
    }

    List<Formula> literals() {
      return List.copyOf(literals.keySet());
    }

    @Override
    protected UpwardClosedSet visit(Formula.TemporalOperator literal) {
      BitSet bitSet = new BitSet();
      bitSet.set(literals.computeIfAbsent(literal, x -> literals.size()));
      return UpwardClosedSet.of(bitSet);
    }
  }

  private static final class ConjunctiveNormalFormVisitor extends AbstractNormalFormVisitor {
    @Override
    public UpwardClosedSet visit(BooleanConstant booleanConstant) {
      return booleanConstant.value
        ? UpwardClosedSet.of()
        : UpwardClosedSet.of(new BitSet());
    }

    @Override
    public UpwardClosedSet visit(Conjunction conjunction) {
      UpwardClosedSet set = UpwardClosedSet.of();

      for (Formula x : conjunction.children) {
        set = set.union(x.accept(this));
      }

      return set;
    }

    @Override
    public UpwardClosedSet visit(Disjunction disjunction) {
      UpwardClosedSet set = UpwardClosedSet.of(new BitSet());

      for (Formula x : disjunction.children) {
        set = set.intersection(x.accept(this));
      }

      return set;
    }
  }

  private static final class DisjunctiveNormalFormVisitor extends AbstractNormalFormVisitor {
    @Override
    public UpwardClosedSet visit(BooleanConstant booleanConstant) {
      return booleanConstant.value
        ? UpwardClosedSet.of(new BitSet())
        : UpwardClosedSet.of();
    }

    @Override
    public UpwardClosedSet visit(Conjunction conjunction) {
      UpwardClosedSet set = UpwardClosedSet.of(new BitSet());

      for (Formula x : conjunction.children) {
        set = set.intersection(x.accept(this));
      }

      return set;
    }

    @Override
    public UpwardClosedSet visit(Disjunction disjunction) {
      UpwardClosedSet set = UpwardClosedSet.of();

      for (Formula x : disjunction.children) {
        set = set.union(x.accept(this));
      }

      return set;
    }
  }

  private static final class ClausesView extends AbstractSet<Set<Formula>> {
    private final List<BitSet> clauses;
    private final List<Formula> literals;

    private ClausesView(List<BitSet> clauses, List<Formula> literals) {
      this.clauses = List.copyOf(clauses);
      this.literals = List.copyOf(literals);
      assert Collections3.isDistinct(this.clauses);
    }

    @Override
    public Iterator<Set<Formula>> iterator() {
      return new Iterator<>() {
        Iterator<BitSet> internalIterator = clauses.iterator();

        @Override
        public boolean hasNext() {
          return internalIterator.hasNext();
        }

        @Override
        public Set<Formula> next() {
          return new ClauseView(internalIterator.next());
        }
      };
    }

    @Override
    public int size() {
      return clauses.size();
    }

    @Override
    public Stream<Set<Formula>> stream() {
      return clauses.stream().map(ClauseView::new);
    }

    private final class ClauseView extends AbstractSet<Formula> {
      private final BitSet clause;

      private ClauseView(BitSet clause) {
        this.clause = clause;
      }

      @Override
      public Iterator<Formula> iterator() {
        return stream().iterator();
      }

      @Override
      public int size() {
        return clause.cardinality();
      }

      @Override
      public Stream<Formula> stream() {
        return clause.stream().mapToObj(literals::get);
      }
    }
  }
}
