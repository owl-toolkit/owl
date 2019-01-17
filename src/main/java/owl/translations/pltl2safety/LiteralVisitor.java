package owl.translations.pltl2safety;

import owl.ltl.Literal;
import owl.ltl.visitors.Visitor;

public class LiteralVisitor implements Visitor<Literal> {

  LiteralVisitor() {}

  @Override
  public Literal visit(Literal literal) {
    if (literal.isNegated()) {
      return literal.not();
    }
    return literal;
  }
}
