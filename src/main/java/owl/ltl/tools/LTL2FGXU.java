package owl.ltl.tools;

import com.google.common.collect.ImmutableList;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
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
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.parser.LtlParseResult;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParserException;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.UnabbreviateVisitor;

public final class LTL2FGXU {
  private LTL2FGXU() {
  }

  public static void main(String[] argv) throws ParserException {
    LtlParseResult ltlParseResult = LtlParser.parse(argv[0]);
    Formula formula = ltlParseResult.getFormula().accept(
      new UnabbreviateVisitor(ROperator.class, MOperator.class, WOperator.class));
    Printer printer = new Printer(System.out, ltlParseResult.getVariableMapping());
    formula.accept(printer);
    printer.flush();
  }

  private static final class Printer implements IntVisitor {
    private final List<String> mapping;
    private final PrintStream printer;

    Printer(PrintStream printer, List<String> mapping) {
      this.printer = printer;
      this.mapping = ImmutableList.copyOf(mapping);
    }

    public void flush() {
      this.printer.flush();
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      printer.print(booleanConstant);
      return 0;
    }

    @Override
    public int visit(Conjunction conjunction) {
      printer.print("(");
      Iterator<Formula> formulaIterator = conjunction.children.iterator();
      formulaIterator.next().accept(this);
      formulaIterator.forEachRemaining(f -> {
        printer.print(" & ");
        f.accept(this);
      });
      printer.print(")");
      return 0;
    }

    @Override
    public int visit(Disjunction disjunction) {
      printer.print("(");
      Iterator<Formula> formulaIterator = disjunction.children.iterator();
      formulaIterator.next().accept(this);
      formulaIterator.forEachRemaining(f -> {
        printer.print(" | ");
        f.accept(this);
      });
      printer.print(")");
      return 0;
    }

    @Override
    public int visit(FOperator fOperator) {
      return visit((UnaryModalOperator) fOperator);
    }

    @Override
    public int visit(FrequencyG freq) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int visit(GOperator gOperator) {
      return visit((UnaryModalOperator) gOperator);
    }

    @Override
    public int visit(Literal literal) {
      if (literal.isNegated()) {
        printer.print("! ");
      }

      printer.print(mapping.get(literal.getAtom()));
      return 0;
    }

    @Override
    public int visit(MOperator mOperator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int visit(ROperator rOperator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int visit(UOperator uOperator) {
      return visit((BinaryModalOperator) uOperator);
    }

    @Override
    public int visit(WOperator wOperator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int visit(XOperator xOperator) {
      return visit((UnaryModalOperator) xOperator);
    }

    private int visit(UnaryModalOperator operator) {
      printer.print(operator.getOperator() + " (");
      operator.operand.accept(this);
      printer.print(')');
      return 0;
    }

    private int visit(BinaryModalOperator operator) {
      printer.print('(');
      operator.left.accept(this);
      printer.print(") " + operator.getOperator() + " (");
      operator.right.accept(this);
      printer.print(')');
      return 0;
    }
  }
}
