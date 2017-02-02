package owl.ltl.tools;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map;
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
import owl.ltl.parser.Parser;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.RestrictToFGXU;

public class Ltl2FGXU {

  public static void main(String[] argv) throws FileNotFoundException {
    BiMap<String, Integer> mapping = HashBiMap.create();
    Formula formula = Parser.formula(argv[0], mapping).accept(new RestrictToFGXU());
    Printer printer = new Printer(System.out, mapping.inverse());
    formula.accept(printer);
    printer.printer.flush();
  }

  private static class Printer implements IntVisitor {

    Map<Integer, String> mapping;
    PrintStream printer;

    private Printer(PrintStream printer, Map<Integer, String> mapping) {
      this.printer = printer;
      this.mapping = mapping;
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
