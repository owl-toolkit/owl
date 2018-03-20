package owl.ltl.util;

import com.google.common.collect.Collections2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.collections.Collections3;
import owl.ltl.BinaryModalOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.PropositionalFormula;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.Collector;

public final class FormulaIsomorphism {

  private FormulaIsomorphism() {
  }

  @SuppressWarnings("PMD.ReturnEmptyArrayRatherThanNull")
  @Nullable
  public static int[] compute(Formula formula1, Formula formula2) {
    BitSet atoms1 = Collector.collectAtoms(formula1);
    BitSet atoms2 = Collector.collectAtoms(formula2);

    if (atoms1.cardinality() != atoms2.cardinality()) {
      return null;
    }

    List<Integer> atomsList1 = atoms1.stream().boxed().collect(Collectors.toList());
    List<Integer> atomsList2 = atoms2.stream().boxed().collect(Collectors.toList());

    int[] mapping = new int[atoms1.length()];
    Arrays.fill(mapping, -1);
    ValidationVisitor validationVisitor = new ValidationVisitor(mapping);

    for (List<Integer> atomsList2Perm : Collections2.permutations(atomsList2)) {
      // Initialise Mapping
      Collections3.zip(atomsList1, atomsList2Perm, (x, y) -> mapping[x] = y);

      // Check mapping
      if (formula1.accept(validationVisitor, formula2)) {
        return mapping;
      }
    }

    return null;
  }

  private static class ValidationVisitor implements BinaryVisitor<Formula, Boolean> {
    private final int[] mapping;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    ValidationVisitor(int[] mapping) {
      this.mapping = mapping;
    }

    @Override
    public Boolean visit(BooleanConstant booleanConstant, Formula formula) {
      return booleanConstant.equals(formula);
    }

    @Override
    public Boolean visit(Conjunction conjunction, Formula formula) {
      return visitPropositionalOperator(conjunction, formula);
    }

    @Override
    public Boolean visit(Disjunction disjunction, Formula formula) {
      return visitPropositionalOperator(disjunction, formula);
    }

    @Override
    public Boolean visit(FOperator fOperator, Formula formula) {
      return visitUnaryOperator(fOperator, formula);
    }

    @Override
    public Boolean visit(GOperator gOperator, Formula formula) {
      return visitUnaryOperator(gOperator, formula);
    }

    @Override
    public Boolean visit(Literal literal, Formula formula) {
      if (!(formula instanceof Literal)) {
        return Boolean.FALSE;
      }

      Literal otherLiteral = (Literal) formula;

      if (literal.isNegated() ^ otherLiteral.isNegated()) {
        return Boolean.FALSE;
      }

      return mapping[literal.getAtom()] == otherLiteral.getAtom();
    }

    @Override
    public Boolean visit(MOperator mOperator, Formula formula) {
      return visitBinaryOperator(mOperator, formula);
    }

    @Override
    public Boolean visit(UOperator uOperator, Formula formula) {
      return visitBinaryOperator(uOperator, formula);
    }

    @Override
    public Boolean visit(ROperator rOperator, Formula formula) {
      return visitBinaryOperator(rOperator, formula);
    }

    @Override
    public Boolean visit(WOperator wOperator, Formula formula) {
      return visitBinaryOperator(wOperator, formula);
    }

    @Override
    public Boolean visit(XOperator xOperator, Formula formula) {
      return visitUnaryOperator(xOperator, formula);
    }

    private Boolean visitUnaryOperator(UnaryModalOperator operator, Formula formula) {
      if (!operator.getClass().isInstance(formula)) {
        return Boolean.FALSE;
      }

      return operator.operand.accept(this, ((UnaryModalOperator) formula).operand);
    }

    private Boolean visitBinaryOperator(BinaryModalOperator operator, Formula formula) {
      if (!operator.getClass().isInstance(formula)) {
        return Boolean.FALSE;
      }

      return operator.left.accept(this, ((BinaryModalOperator) formula).left)
        && operator.right.accept(this, ((BinaryModalOperator) formula).right);
    }

    private Boolean visitPropositionalOperator(PropositionalFormula formula1, Formula formula2) {
      if (!formula1.getClass().isInstance(formula2)) {
        return Boolean.FALSE;
      }

      PropositionalFormula formula2Casted = (PropositionalFormula) formula2;

      if (formula1.children.size() != formula2Casted.children.size()) {
        return Boolean.FALSE;
      }

      List<Formula> children1 = new ArrayList<>(formula1.children);
      List<Formula> children2 = new ArrayList<>(formula2Casted.children);

      for (List<Formula> children2Permutation : Collections2.permutations(children2)) {
        int i = 0;
        for (; i < children1.size(); i++) {
          if (!children1.get(i).accept(this, children2Permutation.get(i))) {
            break;
          }
        }

        if (i == children1.size()) {
          return Boolean.TRUE;
        }
      }

      return Boolean.FALSE;
    }
  }
}