package owl.ltl.ltlf;

import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import owl.ltl.Formula;
import owl.ltl.PropositionalFormula;
import owl.ltl.UnaryModalOperator;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

// This Operator only exists so all LTLf Formulas can be represented,
// it does not contain any functionality
public class NegOperator extends PropositionalFormula {
  @Override
  public Set<Formula> children() {
    return super.children();
  }

  @Override
  public boolean isPureEventual() {
    return super.isPureEventual();
  }

  @Override
  public boolean isPureUniversal() {
    return super.isPureUniversal();
  }

  @Override
  public <T> Stream<T> map(Function<? super Formula, ? extends T> mapper) {
    return super.map(mapper);
  }

  @Override
  public String toString() {
    return super.toString();
  }

  @Override
  protected String operatorSymbol() {
    return null;
  }

  @Override
  public int accept(IntVisitor visitor) {
    return 0;
  }

  @Override
  public <R> R accept(Visitor<R> visitor) {
    return null;
  }

  @Override
  public <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter) {
    return null;
  }

  @Override
  public Formula nnf() {
    return null;
  }

  @Override
  public Formula not() {
    return null;
  }

  @Override
  public Formula substitute(Function<? super TemporalOperator, ? extends Formula> substitution) {
    return null;
  }

  public NegOperator(Formula operand) {

  }
}
