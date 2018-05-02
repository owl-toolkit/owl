package owl.ltl.rewriter;

import java.util.Arrays;
import java.util.BitSet;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.Converter;

public final class LiteralMapper {
  private static final int UNDEFINED = -1;

  private LiteralMapper() {}

  public static ShiftedFormula shiftLiterals(Formula formula) {
    BitSet atoms = Collector.collectAtoms(formula);

    int[] mapping = new int[atoms.length()];
    Arrays.fill(mapping, UNDEFINED);

    int nextAtom = 0;

    for (int i = atoms.nextSetBit(0); i >= 0; i = atoms.nextSetBit(i + 1)) {
      mapping[i] = nextAtom;
      nextAtom++;
    }

    return new ShiftedFormula(formula.accept(new LiteralShifter(mapping)), mapping);
  }

  private static class LiteralShifter extends Converter {
    private final int[] mapping;

    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    LiteralShifter(int[] mapping) { // NOPMD
      super(SyntacticFragment.ALL.classes());
      this.mapping = mapping;
    }

    @Override
    public Formula visit(Literal literal) {
      assert mapping[literal.getAtom()] != UNDEFINED;
      assert mapping[literal.getAtom()] <= literal.getAtom();
      return Literal.of(mapping[literal.getAtom()], literal.isNegated());
    }
  }

  public static final class ShiftedFormula {
    public final Formula formula;
    public final int[] mapping;

    @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "AssignmentOrReturnOfFieldWithMutableType"})
    ShiftedFormula(Formula formula, int[] mapping) {
      this.formula = formula;
      this.mapping = mapping;
    }
  }
}
