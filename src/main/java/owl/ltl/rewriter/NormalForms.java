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

package owl.ltl.rewriter;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.collections.Collections3;
import owl.collections.UpwardClosedSet;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragments;
import owl.ltl.visitors.PropositionalVisitor;

public final class NormalForms {
  public static final Function<Formula.NaryPropositionalOperator, Set<Formula>>
    SYNTHETIC_CO_SAFETY_LITERAL = x -> x.operands.stream()
      .filter(SyntacticFragments::isCoSafety)
      .collect(Collectors.toUnmodifiableSet());

  public static final Function<Formula.NaryPropositionalOperator, Set<Formula>>
    SYNTHETIC_SAFETY_LITERAL = x -> x.operands.stream()
      .filter(SyntacticFragments::isSafety)
      .collect(Collectors.toUnmodifiableSet());

  public static final Function<Formula.NaryPropositionalOperator, Set<Formula>>
    SYNTHETIC_DELTA2_LITERAL = x -> x.operands.stream()
      .filter(SyntacticFragments.DELTA_2::contains)
      .collect(Collectors.toUnmodifiableSet());

  private NormalForms() {}

  public static Formula toCnfFormula(Formula formula) {
    return toCnf(formula).stream()
      .map(Disjunction::of)
      .reduce(BooleanConstant.TRUE, Conjunction::of);
  }

  public static Set<Set<Formula>> toCnf(Formula formula) {
    return toCnf(formula, x -> Set.of());
  }

  public static Set<Set<Formula>> toCnf(Formula formula,
    Function<? super Formula.NaryPropositionalOperator,
      ? extends Set<Formula>> syntheticLiteralFactory) {
    var visitor = new ConjunctiveNormalFormVisitor(syntheticLiteralFactory);
    var cnf = formula.accept(visitor).representatives();
    return new ClausesView(cnf, visitor.literals());
  }

  public static Formula toDnfFormula(Formula formula) {
    return toDnf(formula).stream()
      .map(Conjunction::of)
      .reduce(BooleanConstant.FALSE, Disjunction::of);
  }

  public static Set<Set<Formula>> toDnf(Formula formula) {
    return toDnf(formula, x -> Set.of());
  }

  public static Set<Set<Formula>> toDnf(Formula formula,
    Function<? super Formula.NaryPropositionalOperator,
      ? extends Set<Formula>> syntheticLiteralFactory) {
    var visitor = new DisjunctiveNormalFormVisitor(syntheticLiteralFactory);
    var dnf = formula.accept(visitor).representatives();
    return new ClausesView(dnf, visitor.literals());
  }

  private abstract static class AbstractNormalFormVisitor
    extends PropositionalVisitor<UpwardClosedSet> {

    final Function<? super Formula.NaryPropositionalOperator, ? extends Set<Formula>>
      syntheticLiteralFactory;
    private final Map<Formula, Integer> literals;

    private AbstractNormalFormVisitor(Function<? super Formula.NaryPropositionalOperator,
      ? extends Set<Formula>> syntheticLiteralFactory) {
      this.literals = new LinkedHashMap<>();
      this.syntheticLiteralFactory = syntheticLiteralFactory;
    }

    List<Formula> literals() {
      return new ArrayList<>(literals.keySet());
    }

    UpwardClosedSet singleton(Formula literal) {
      BitSet bitSet = new BitSet();
      bitSet.set(literals.computeIfAbsent(literal, x -> literals.size()));
      return UpwardClosedSet.of(bitSet);
    }

    @Override
    public UpwardClosedSet visit(Literal literal) {
      return singleton(literal);
    }

    @Override
    protected UpwardClosedSet visit(Formula.TemporalOperator temporalOperator) {
      return singleton(temporalOperator);
    }
  }

  private static final class ConjunctiveNormalFormVisitor extends AbstractNormalFormVisitor {

    private ConjunctiveNormalFormVisitor(Function<? super Formula.NaryPropositionalOperator,
      ? extends Set<Formula>> syntheticLiteralFactory) {
      super(syntheticLiteralFactory);
    }

    @Override
    public UpwardClosedSet visit(BooleanConstant booleanConstant) {
      return booleanConstant.value
        ? UpwardClosedSet.of()
        : UpwardClosedSet.of(new BitSet());
    }

    @Override
    public UpwardClosedSet visit(Conjunction conjunction) {
      Set<Formula> syntheticLiteral = syntheticLiteralFactory.apply(conjunction);

      UpwardClosedSet set = syntheticLiteral.isEmpty()
        ? UpwardClosedSet.of()
        : singleton(Conjunction.of(syntheticLiteral));

      for (Formula x : conjunction.operands) {
        if (!syntheticLiteral.contains(x)) {
          set = set.union(x.accept(this));
        }
      }

      return set;
    }

    @Override
    public UpwardClosedSet visit(Disjunction disjunction) {
      Collection<Formula> syntheticLiteral = syntheticLiteralFactory.apply(disjunction);

      UpwardClosedSet set = syntheticLiteral.isEmpty()
        ? UpwardClosedSet.of(new BitSet())
        : singleton(Disjunction.of(syntheticLiteral));

      for (Formula x : disjunction.operands) {
        if (!syntheticLiteral.contains(x)) {
          set = set.intersection(x.accept(this));
        }
      }

      return set;
    }
  }

  private static final class DisjunctiveNormalFormVisitor extends AbstractNormalFormVisitor {

    private DisjunctiveNormalFormVisitor(Function<? super Formula.NaryPropositionalOperator,
      ? extends Set<Formula>> syntheticLiteralFactory) {
      super(syntheticLiteralFactory);
    }

    @Override
    public UpwardClosedSet visit(BooleanConstant booleanConstant) {
      return booleanConstant.value
        ? UpwardClosedSet.of(new BitSet())
        : UpwardClosedSet.of();
    }

    @Override
    public UpwardClosedSet visit(Conjunction conjunction) {
      Set<Formula> syntheticLiteral = syntheticLiteralFactory.apply(conjunction);

      UpwardClosedSet set = syntheticLiteral.isEmpty()
        ? UpwardClosedSet.of(new BitSet())
        : singleton(Conjunction.of(syntheticLiteral));

      for (Formula x : conjunction.operands) {
        if (!syntheticLiteral.contains(x)) {
          set = set.intersection(x.accept(this));
        }
      }

      return set;
    }

    @Override
    public UpwardClosedSet visit(Disjunction disjunction) {
      Set<Formula> syntheticLiteral = syntheticLiteralFactory.apply(disjunction);

      UpwardClosedSet set = syntheticLiteral.isEmpty()
        ? UpwardClosedSet.of()
        : singleton(Disjunction.of(syntheticLiteral));

      for (Formula x : disjunction.operands) {
        if (!syntheticLiteral.contains(x)) {
          set = set.union(x.accept(this));
        }
      }

      return set;
    }
  }

  private static final class ClausesView extends AbstractSet<Set<Formula>> {
    private final List<BitSet> clauses;
    private final List<Formula> literals;

    private ClausesView(List<BitSet> clauses, List<Formula> literals) {
      this.clauses = clauses;
      this.literals = List.copyOf(literals);
      assert Collections3.isDistinct(this.clauses);
    }

    @Override
    public Iterator<Set<Formula>> iterator() {
      return new Iterator<>() {
        final Iterator<BitSet> internalIterator = clauses.iterator();

        @Override
        public boolean hasNext() {
          return internalIterator.hasNext();
        }

        @Override
        public Set<Formula> next() {
          return new ClauseView(internalIterator.next());
        }

        @Override
        public void remove() {
          internalIterator.remove();
        }

        @Override
        public void forEachRemaining(Consumer<? super Set<Formula>> action) {
          internalIterator.forEachRemaining(element -> action.accept(new ClauseView(element)));
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
