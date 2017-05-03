package owl.collections;

import java.util.BitSet;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.factories.ValuationSetFactory;

public final class ValuationSetUtil {
  private ValuationSetUtil() {
  }

  public static ValuationSet toValuationSet(ValuationSetFactory valuationSetFactory,
    BooleanExpression<AtomLabel> expression, @Nullable IntUnaryOperator mapping) {
    if (expression.isFALSE()) {
      return valuationSetFactory.createEmptyValuationSet();
    }

    if (expression.isTRUE()) {
      return valuationSetFactory.createUniverseValuationSet();
    }

    if (expression.isAtom()) {
      AtomLabel label = expression.getAtom();
      BitSet bs = new BitSet();

      if (mapping != null) {
        bs.set(mapping.applyAsInt(label.getAPIndex()));
      } else {
        bs.set(label.getAPIndex());
      }

      return valuationSetFactory.createValuationSet(bs, bs);
    }

    if (expression.isNOT()) {
      return toValuationSet(valuationSetFactory, expression.getLeft(), mapping).complement();
    }

    if (expression.isAND()) {
      ValuationSet left = toValuationSet(valuationSetFactory, expression.getLeft(), mapping);
      ValuationSet right = toValuationSet(valuationSetFactory, expression.getRight(), mapping);
      left.retainAll(right);
      right.free();
      return left;
    }

    if (expression.isOR()) {
      ValuationSet left = toValuationSet(valuationSetFactory, expression.getLeft(), mapping);
      left.addAllWith(toValuationSet(valuationSetFactory, expression.getRight(), mapping));
      return left;
    }

    throw new IllegalArgumentException("Unsupported Case: " + expression);
  }
}
