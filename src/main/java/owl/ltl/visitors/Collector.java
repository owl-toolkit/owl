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

package owl.ltl.visitors;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.ltl.Biconditional;
import owl.ltl.BinaryModalOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.PropositionalFormula;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public final class Collector {

  private Collector() {
  }

  public static <T> Set<T> collect(Function<Formula, T> function, Formula formula) {
    CollectorVisitor<T> visitor = new CollectorVisitor<>(function);
    formula.accept(visitor);
    return visitor.collection;
  }

  public static <T> Set<T> collect(Function<Formula, T> function,
    Iterable<? extends Formula> formulas) {
    CollectorVisitor<T> visitor = new CollectorVisitor<>(function);
    formulas.forEach(x -> x.accept(visitor));
    return visitor.collection;
  }

  public static <T> Set<T> collect(Function<Formula, T> function, Formula... formulas) {
    return collect(function, List.of(formulas));
  }

  public static Set<Formula> collect(Predicate<Formula> predicate, Formula... formulas) {
    return collect(toFunction(predicate), List.of(formulas));
  }

  public static Set<Formula> collect(Predicate<Formula> predicate,
    Iterable<? extends Formula> formulas) {
    return collect(toFunction(predicate), formulas);
  }

  public static BitSet collectAtoms(Formula formula) {
    return collectAtoms(Set.of(formula));
  }

  public static BitSet collectAtoms(Iterable<? extends Formula> formulas) {
    BitSet atoms = new BitSet();

    collect((Predicate<Formula>) (x) -> {
      if (x instanceof Literal) {
        atoms.set(((Literal) x).getAtom());
      }
      return false;
    }, formulas);

    return atoms;
  }

  public static BitSet collectAtoms(Formula formula, boolean negated) {
    return collectAtoms(Set.of(formula), negated);
  }

  public static BitSet collectAtoms(Iterable<? extends Formula> formulas, boolean negated) {
    BitSet atoms = new BitSet();

    collect((Predicate<Formula>) (x) -> {
      if (x instanceof Literal) {
        Literal literal = (Literal) x;

        if (negated && literal.isNegated()) {
          atoms.set(literal.getAtom());
        } else if (!negated && !literal.isNegated()) {
          atoms.set(literal.getAtom());
        }
      }
      return false;
    }, formulas);

    return atoms;
  }

  public static Set<FOperator> collectFOperators(Formula formula) {
    return collect(x -> x instanceof FOperator ? (FOperator) x : null, formula);
  }

  public static Set<GOperator> collectGOperators(Formula formula) {
    return collect(x -> x instanceof GOperator ? (GOperator) x : null, formula);
  }

  public static Set<FOperator> collectTransformedFOperators(Formula formula) {
    TransformedFVisitor visitor = new TransformedFVisitor(false);
    formula.accept(visitor);
    return visitor.collection;
  }

  public static Set<FOperator> collectTransformedFOperators(Iterable<? extends Formula> formulas) {
    TransformedFVisitor visitor = new TransformedFVisitor(false);
    formulas.forEach(x -> x.accept(visitor));
    return visitor.collection;
  }

  public static Set<GOperator> collectTransformedGOperators(Formula formula) {
    TransformedGVisitor visitor = new TransformedGVisitor(false);
    formula.accept(visitor);
    return visitor.collection;
  }

  public static Set<GOperator> collectTransformedGOperators(Iterable<? extends Formula> formulas) {
    TransformedGVisitor visitor = new TransformedGVisitor(false);
    formulas.forEach(x -> x.accept(visitor));
    return visitor.collection;
  }

  private static Function<Formula, Formula> toFunction(Predicate<Formula> predicate) {
    return x -> predicate.test(x) ? x : null;
  }

  @Nullable
  private static FOperator transformToFOperator(@Nullable Formula formula) {
    if (formula instanceof FOperator) {
      return (FOperator) formula;
    }

    if (formula instanceof MOperator) {
      return new FOperator(((MOperator) formula).left);
    }

    if (formula instanceof UOperator) {
      return new FOperator(((UOperator) formula).right);
    }

    return null;
  }

  @Nullable
  private static GOperator transformToGOperator(@Nullable Formula formula) {
    if (formula instanceof GOperator) {
      return (GOperator) formula;
    }

    if (formula instanceof ROperator) {
      return new GOperator(((ROperator) formula).right);
    }

    if (formula instanceof WOperator) {
      return new GOperator(((WOperator) formula).left);
    }

    return null;
  }

  static class CollectorVisitor<T> implements IntVisitor {

    protected final Set<T> collection;
    private final Function<Formula, T> collectFunction;
    private final boolean onlyTopmost;

    CollectorVisitor(Function<Formula, T> function) {
      this(function, false);
    }

    CollectorVisitor(Function<Formula, T> function, boolean onlyTopmost) {
      collectFunction = function;
      collection = new HashSet<>();
      this.onlyTopmost = onlyTopmost;
    }

    private boolean collect(Formula formula) {
      T result = collectFunction.apply(formula);

      if (result != null) {
        collection.add(result);
        return true;
      }

      return false;
    }

    @Override
    public int visit(Biconditional biconditional) {
      biconditional.left.accept(this);
      biconditional.right.accept(this);
      return 0;
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return 0;
    }

    @Override
    public int visit(Conjunction conjunction) {
      return visit((PropositionalFormula) conjunction);
    }

    @Override
    public int visit(Disjunction disjunction) {
      return visit((PropositionalFormula) disjunction);
    }

    @Override
    public int visit(FOperator fOperator) {
      return visit((UnaryModalOperator) fOperator);
    }

    @Override
    public int visit(FrequencyG freq) {
      return visit((UnaryModalOperator) freq);
    }

    @Override
    public int visit(GOperator gOperator) {
      return visit((UnaryModalOperator) gOperator);
    }

    @Override
    public int visit(Literal literal) {
      collect(literal);
      return 0;
    }

    @Override
    public int visit(MOperator mOperator) {
      return visit((BinaryModalOperator) mOperator);
    }

    @Override
    public int visit(ROperator rOperator) {
      return visit((BinaryModalOperator) rOperator);
    }

    @Override
    public int visit(UOperator uOperator) {
      return visit((BinaryModalOperator) uOperator);
    }

    @Override
    public int visit(WOperator wOperator) {
      return visit((BinaryModalOperator) wOperator);
    }

    @Override
    public int visit(XOperator xOperator) {
      return visit((UnaryModalOperator) xOperator);
    }

    protected int visit(BinaryModalOperator operator) {
      if (collect(operator) && onlyTopmost) {
        return 1;
      }

      operator.left.accept(this);
      operator.right.accept(this);
      return 0;
    }

    protected int visit(PropositionalFormula formula) {
      formula.forEach(c -> c.accept(this));
      return 0;
    }

    protected int visit(UnaryModalOperator operator) {
      if (collect(operator) && onlyTopmost) {
        return 1;
      }

      operator.operand.accept(this);
      return 0;
    }
  }

  static class TransformedFVisitor extends CollectorVisitor<FOperator> {

    TransformedFVisitor(boolean onlyTopmost) {
      super(Collector::transformToFOperator, onlyTopmost);
    }

    @Override
    public int visit(MOperator mOperator) {
      if (visit((BinaryModalOperator) mOperator) == 1) {
        mOperator.right.accept(this);
      }

      return 0;
    }

    @Override
    public int visit(UOperator uOperator) {
      if (visit((BinaryModalOperator) uOperator) == 1) {
        uOperator.left.accept(this);
      }

      return 0;
    }
  }

  static class TransformedGVisitor extends CollectorVisitor<GOperator> {

    TransformedGVisitor(boolean onlyTopmost) {
      super(Collector::transformToGOperator, onlyTopmost);
    }

    @Override
    public int visit(ROperator rOperator) {
      if (visit((BinaryModalOperator) rOperator) == 1) {
        rOperator.left.accept(this);
      }

      return 0;
    }

    @Override
    public int visit(WOperator wOperator) {
      if (visit((BinaryModalOperator) wOperator) == 1) {
        wOperator.right.accept(this);
      }

      return 0;
    }
  }
}
