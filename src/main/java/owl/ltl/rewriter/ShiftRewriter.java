package owl.ltl.rewriter;

import java.util.Arrays;
import java.util.BitSet;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.DefaultConverter;

public class ShiftRewriter {

  static final int UNDEFINED = -1;

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

  static class LiteralShifter extends DefaultConverter {
    private final int[] mapping;

    LiteralShifter(int[] mapping) {
      this.mapping = mapping;
    }

    @Override
    public Formula visit(Literal literal) {
      assert mapping[literal.getAtom()] != UNDEFINED;
      assert mapping[literal.getAtom()] <= literal.getAtom();
      return Literal.of(mapping[literal.getAtom()], literal.isNegated());
    }
  }

  public static class ShiftedFormula {
    public final Formula formula;
    public final int[] mapping;

    private ShiftedFormula(Formula formula, int[] mapping) {
      this.formula = formula;
      this.mapping = mapping;
    }
  }
}
