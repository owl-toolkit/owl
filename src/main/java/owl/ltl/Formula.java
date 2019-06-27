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

package owl.ltl;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import owl.ltl.ltlf.NegOperator;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.PropositionalVisitor;
import owl.ltl.visitors.Visitor;

public abstract class Formula implements Comparable<Formula> {

  private static final List<Class<? extends Formula>> ORDER = List.of(
    BooleanConstant.class,
    Literal.class,
    NegOperator.class,
    Conjunction.class,
    Disjunction.class,
    Biconditional.class,
    FOperator.class,
    FrequencyG.class,
    GOperator.class,
    XOperator.class,
    MOperator.class,
    ROperator.class,
    UOperator.class,
    WOperator.class);

  private final int hashCode;

  Formula(int hashCode) {
    this.hashCode = hashCode;
  }

  public abstract int accept(IntVisitor visitor);

  public abstract <R> R accept(Visitor<R> visitor);

  public abstract <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter);

  public final BitSet atomicPropositions(boolean includeNested) {
    BitSet atomicPropositions = new BitSet();

    accept(new PropositionalVisitor<Void>() {
      @Override
      public Void visit(Biconditional biconditional) {
        biconditional.left.accept(this);
        biconditional.right.accept(this);
        return null;
      }

      @Override
      public Void visit(BooleanConstant booleanConstant) {
        return null;
      }

      @Override
      public Void visit(Conjunction conjunction) {
        conjunction.children.forEach(x -> x.accept(this));
        return null;
      }

      @Override
      public Void visit(Disjunction disjunction) {
        disjunction.children.forEach(x -> x.accept(this));
        return null;
      }

      @Override
      protected Void visit(TemporalOperator formula) {
        if (formula instanceof Literal) {
          atomicPropositions.set(((Literal) formula).getAtom());
        } else if (includeNested) {
          formula.children().forEach(x -> x.accept(this));
        }

        return null;
      }
    });

    return atomicPropositions;
  }

  public final boolean allMatch(Predicate<Formula> predicate) {
    if (!predicate.test(this)) {
      return false;
    }

    for (Formula child : children()) {
      if (!child.allMatch(predicate)) {
        return false;
      }
    }

    return true;
  }

  public final boolean anyMatch(Predicate<Formula> predicate) {
    if (predicate.test(this)) {
      return true;
    }

    for (Formula child : children()) {
      if (child.anyMatch(predicate)) {
        return true;
      }
    }

    return false;
  }

  public abstract Set<Formula> children();

  @Override
  public final int compareTo(Formula o) {
    int heightComparison = Integer.compare(height(), o.height());

    if (heightComparison != 0) {
      return heightComparison;
    }

    int classComparison = Integer.compare(classIndex(this), classIndex(o));

    if (classComparison != 0) {
      return classComparison;
    }

    return compareToImpl(o);
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }

    Formula other = (Formula) o;
    return other.hashCode == hashCode && equalsImpl(other);
  }

  @Override
  public final int hashCode() {
    return hashCode;
  }

  public final int height() {
    return Formulas.height(children()) + 1;
  }

  // Temporal Properties of an LTL Formula
  public abstract boolean isPureEventual();

  public abstract boolean isPureUniversal();

  public final boolean isSuspendable() {
    return isPureEventual() && isPureUniversal();
  }

  public abstract Formula nnf();

  /**
   * Syntactically negate this formula.
   *
   * <p>If this formula is in NNF, the returned negation will also be in NNF.</p>
   *
   * @return the negation of this formula.
   */
  public abstract Formula not();

  public final <E extends TemporalOperator> Set<E> subformulas(Class<E> clazz) {
    return subformulas(clazz::isInstance, clazz::cast);
  }

  public final Set<TemporalOperator> subformulas(Predicate<? super TemporalOperator> predicate) {
    return subformulas(predicate, x -> x);
  }

  public final <E extends TemporalOperator> Set<E> subformulas(
    Predicate<? super TemporalOperator> predicate,
    Function<? super TemporalOperator, E> cast) {
    Set<E> subformulas = new HashSet<>();

    accept(new PropositionalVisitor<Void>() {
      @Override
      public Void visit(Biconditional biconditional) {
        biconditional.left.accept(this);
        biconditional.right.accept(this);
        return null;
      }

      @Override
      public Void visit(BooleanConstant booleanConstant) {
        return null;
      }

      @Override
      public Void visit(Conjunction conjunction) {
        conjunction.children.forEach(c -> c.accept(this));
        return null;
      }

      @Override
      public Void visit(Disjunction disjunction) {
        disjunction.children.forEach(c -> c.accept(this));
        return null;
      }

      @Override
      protected Void visit(TemporalOperator formula) {
        if (predicate.test(formula)) {
          subformulas.add(cast.apply(formula));
        }

        formula.children().forEach(x -> x.accept(this));
        return null;
      }
    });

    return subformulas;
  }

  // temporal formulas
  public abstract Formula substitute(
    Function<? super TemporalOperator, ? extends Formula> substitution);

  public abstract Formula temporalStep();

  public abstract Formula temporalStep(int atom, boolean valuation);

  /**
   * Do a single temporal step. This means that one layer of X-operators is removed and literals are
   * replaced by their valuations.
   */
  public abstract Formula temporalStep(BitSet valuation);

  /**
   * Short-cut operation to avoid intermediate construction of formula ASTs.
   */
  public abstract Formula temporalStepUnfold(BitSet valuation);

  public abstract Formula unfold();

  /**
   * Short-cut operation to avoid intermediate construction of formula ASTs.
   */
  public abstract Formula unfoldTemporalStep(BitSet valuation);

  protected abstract int compareToImpl(Formula o);

  protected abstract boolean equalsImpl(Formula o);

  private static int classIndex(Formula formula) {
    int index = ORDER.indexOf(formula.getClass());
    assert index >= 0;
    return index;
  }

  public abstract static class ModalOperator extends TemporalOperator {
    ModalOperator(int hashCode) {
      super(hashCode);
    }
  }

  public abstract static class LogicalOperator extends Formula {
    LogicalOperator(int hashCode) {
      super(hashCode);
    }

    @Override
    public final Formula temporalStep() {
      return substitute(Formula::temporalStep);
    }

    @Override
    public final Formula temporalStep(int atom, boolean valuation) {
      return substitute(x -> x.temporalStep(atom, valuation));
    }

    @Override
    public final Formula temporalStep(BitSet valuation) {
      return substitute(x -> x.temporalStep(valuation));
    }

    @Override
    public final Formula temporalStepUnfold(BitSet valuation) {
      return substitute(x -> x.temporalStepUnfold(valuation));
    }

    @Override
    public final Formula unfold() {
      return substitute(Formula::unfold);
    }

    @Override
    public final Formula unfoldTemporalStep(BitSet valuation) {
      return substitute(x -> x.unfoldTemporalStep(valuation));
    }
  }

  // TODO: Fix hierarchy naming.
  public abstract static class TemporalOperator extends Formula {
    TemporalOperator(int hashCode) {
      super(hashCode);
    }

    @Override
    public final Formula substitute(
      Function<? super TemporalOperator, ? extends Formula> substitution) {
      return substitution.apply(this);
    }

    @Override
    public final Formula temporalStep() {
      return this instanceof XOperator ? ((XOperator) this).operand : this;
    }

    @Override
    public final Formula temporalStep(int atom, boolean valuation) {
      if (this instanceof Literal) {
        Literal literal = (Literal) this;

        if (literal.getAtom() == atom) {
          return BooleanConstant.of(valuation ^ literal.isNegated());
        }
      }

      return this;
    }

    @Override
    public final Formula temporalStep(BitSet valuation) {
      if (this instanceof Literal) {
        Literal literal = (Literal) this;
        return BooleanConstant.of(valuation.get(literal.getAtom()) ^ literal.isNegated());
      }

      return temporalStep();
    }

    @Override
    public final Formula temporalStepUnfold(BitSet valuation) {
      return temporalStep(valuation).unfold();
    }
  }
}
