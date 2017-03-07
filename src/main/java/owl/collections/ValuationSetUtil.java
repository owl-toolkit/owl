package owl.collections;

import java.util.BitSet;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.factories.ValuationSetFactory;

public final class ValuationSetUtil {
  private ValuationSetUtil() {
  }

  public static ValuationSet toValuationSet(ValuationSetFactory valuationSetFactory,
    BooleanExpression<AtomLabel> expression) {
    if (expression.isFALSE()) {
      return valuationSetFactory.createEmptyValuationSet();
    }
    if (expression.isTRUE()) {
      return valuationSetFactory.createUniverseValuationSet();
    }
    if (expression.isAtom()) {
      BitSet bs = new BitSet();
      bs.set(expression.getAtom().getAPIndex());
      return valuationSetFactory.createValuationSet(bs, bs);
    }

    if (expression.isNOT()) {
      return toValuationSet(valuationSetFactory, expression.getLeft()).complement();
    }

    if (expression.isAND()) {
      ValuationSet left = toValuationSet(valuationSetFactory, expression.getLeft());
      ValuationSet right = toValuationSet(valuationSetFactory, expression.getRight());
      left.retainAll(right);
      right.free();
      return left;
    }

    if (expression.isOR()) {
      ValuationSet left = toValuationSet(valuationSetFactory, expression.getLeft());
      left.addAllWith(toValuationSet(valuationSetFactory, expression.getRight()));
      return left;
    }

    throw new IllegalArgumentException("Unsupported Case: " + expression);
  }
}
