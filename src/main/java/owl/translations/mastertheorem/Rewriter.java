package owl.translations.mastertheorem;

import java.util.HashSet;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

public class Rewriter {

  // Converter is rewriting too much and this causes a blow-up in the BDDs later, since new
  // variables are created. We only propagate constants and try to minimise the context-dependent
  // creation of new variables.
  private static class ConstantPropagatingConverter implements Visitor<Formula> {
    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(XOperator xOperator) {
      var operand = xOperator.operand.accept(this);
      return operand instanceof BooleanConstant ? operand : new XOperator(operand);
    }
  }

  public static final class ToCoSafety extends ConstantPropagatingConverter {
    private final Set<GOperator> gOperators;
    private final Set<ROperator> rOperators;
    private final Set<WOperator> wOperators;

    public ToCoSafety(Fixpoints fixpoints) {
      this(fixpoints.greatestFixpoints());
    }

    public ToCoSafety(Iterable<? extends Formula.ModalOperator> y) {
      Set<GOperator> gOperators = new HashSet<>();
      Set<ROperator> rOperators = new HashSet<>();
      Set<WOperator> wOperators = new HashSet<>();

      y.forEach(formula -> {
        if (formula instanceof GOperator) {
          gOperators.add((GOperator) formula);
        } else if (formula instanceof ROperator) {
          rOperators.add((ROperator) formula);
        } else if (formula instanceof WOperator) {
          wOperators.add((WOperator) formula);
        } else {
          throw new IllegalArgumentException(
            formula + " is not a greatest fixpoint modal operator.");
        }
      });

      this.gOperators = Set.of(gOperators.toArray(GOperator[]::new));
      this.rOperators = Set.of(rOperators.toArray(ROperator[]::new));
      this.wOperators = Set.of(wOperators.toArray(WOperator[]::new));
    }

    @Override
    public Formula apply(Formula formula) {
      var coSafety = formula.accept(this);
      assert SyntacticFragment.CO_SAFETY.contains(coSafety) : formula + " -> " + coSafety;
      return coSafety;
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return fOperator(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return BooleanConstant.of(gOperators.contains(gOperator));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return mOperator(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (rOperators.contains(rOperator) || gOperators.contains(new GOperator(rOperator.right))) {
        return BooleanConstant.TRUE;
      }

      return mOperator(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return uOperator(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (wOperators.contains(wOperator) || gOperators.contains(new GOperator(wOperator.left))) {
        return BooleanConstant.TRUE;
      }

      return uOperator(wOperator.left.accept(this), wOperator.right.accept(this));
    }

    private static Formula fOperator(Formula operand) {
      if (operand instanceof BooleanConstant || operand instanceof FOperator) {
        return operand;
      }

      if (operand instanceof Disjunction) {
        return Disjunction.of(((Disjunction) operand).map(ToCoSafety::fOperator));
      }

      return new FOperator(operand);
    }

    private static Formula mOperator(Formula leftOperand, Formula rightOperand) {
      if (leftOperand instanceof BooleanConstant
        || leftOperand instanceof FOperator
        || leftOperand.equals(rightOperand)
        || rightOperand.equals(BooleanConstant.FALSE)) {
        return Conjunction.of(leftOperand, rightOperand);
      }

      if (rightOperand.equals(BooleanConstant.TRUE)) {
        return fOperator(leftOperand);
      }

      return new MOperator(leftOperand, rightOperand);
    }

    private static Formula uOperator(Formula leftOperand, Formula rightOperand) {
      if (rightOperand instanceof BooleanConstant
        || rightOperand instanceof FOperator
        || leftOperand.equals(rightOperand)
        || leftOperand.equals(BooleanConstant.FALSE)) {
        return rightOperand;
      }

      if (leftOperand.equals(BooleanConstant.TRUE)) {
        return fOperator(rightOperand);
      }

      return new UOperator(leftOperand, rightOperand);
    }
  }

  public static final class ToSafety extends ConstantPropagatingConverter {
    private final Set<FOperator> fOperators;
    private final Set<MOperator> mOperators;
    private final Set<UOperator> uOperators;

    public ToSafety(Fixpoints fixpoints) {
      this(fixpoints.leastFixpoints());
    }

    public ToSafety(Iterable<? extends Formula.ModalOperator> x) {
      Set<FOperator> fOperators = new HashSet<>();
      Set<MOperator> mOperators = new HashSet<>();
      Set<UOperator> uOperators = new HashSet<>();

      x.forEach(formula -> {
        if (formula instanceof FOperator) {
          fOperators.add((FOperator) formula);
        } else if (formula instanceof MOperator) {
          mOperators.add((MOperator) formula);
        } else if (formula instanceof UOperator) {
          uOperators.add((UOperator) formula);
        } else {
          throw new IllegalArgumentException(formula + " is not a least fixpoint modal operator.");
        }
      });

      this.fOperators = Set.of(fOperators.toArray(FOperator[]::new));
      this.mOperators = Set.of(mOperators.toArray(MOperator[]::new));
      this.uOperators = Set.of(uOperators.toArray(UOperator[]::new));
    }

    @Override
    public Formula apply(Formula formula) {
      var safety = formula.accept(this);
      assert SyntacticFragment.SAFETY.contains(safety) : formula + " -> " + safety;
      return safety;
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return BooleanConstant.of(fOperators.contains(fOperator));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return gOperator(gOperator.operand.accept(this));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (mOperators.contains(mOperator) || fOperators.contains(new FOperator(mOperator.left))) {
        return rOperator(mOperator.left.accept(this), mOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(ROperator rOperator) {
      return rOperator(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (uOperators.contains(uOperator) || fOperators.contains(new FOperator(uOperator.right))) {
        return wOperator(uOperator.left.accept(this), uOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(WOperator wOperator) {
      return wOperator(wOperator.left.accept(this), wOperator.right.accept(this));
    }

    private static Formula gOperator(Formula operand) {
      if (operand instanceof BooleanConstant || operand instanceof GOperator) {
        return operand;
      }

      if (operand instanceof Conjunction) {
        return Conjunction.of(((Conjunction) operand).map(ToSafety::gOperator));
      }

      return new GOperator(operand);
    }

    private static Formula rOperator(Formula leftOperand, Formula rightOperand) {
      if (rightOperand instanceof BooleanConstant
        || rightOperand instanceof GOperator
        || leftOperand.equals(rightOperand)
        || leftOperand.equals(BooleanConstant.TRUE)) {
        return rightOperand;
      }

      if (leftOperand.equals(BooleanConstant.FALSE)) {
        return gOperator(rightOperand);
      }

      return new ROperator(leftOperand, rightOperand);
    }

    private static Formula wOperator(Formula leftOperand, Formula rightOperand) {
      if (leftOperand instanceof BooleanConstant
        || leftOperand instanceof GOperator
        || leftOperand.equals(rightOperand)
        || rightOperand.equals(BooleanConstant.TRUE)) {
        return Disjunction.of(leftOperand, rightOperand);
      }

      if (rightOperand.equals(BooleanConstant.FALSE)) {
        return gOperator(leftOperand);
      }

      return new WOperator(leftOperand, rightOperand);
    }
  }
}
