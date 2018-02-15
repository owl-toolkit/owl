package owl.collections;

import java.util.BitSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import owl.factories.ValuationSetFactory;

public final class ValuationSetUtil {
  private ValuationSetUtil() {
  }

  public static ValuationSet toValuationSet(ValuationSetFactory factory,
    BooleanExpression<AtomLabel> expression, @Nullable IntUnaryOperator mapping) {
    if (expression.isFALSE()) {
      return factory.empty();
    }

    if (expression.isTRUE()) {
      return factory.universe();
    }

    if (expression.isAtom()) {
      AtomLabel label = expression.getAtom();
      BitSet bs = new BitSet();

      if (mapping == null) {
        bs.set(label.getAPIndex());
      } else {
        bs.set(mapping.applyAsInt(label.getAPIndex()));
      }

      return factory.of(bs, bs);
    }

    if (expression.isNOT()) {
      return toValuationSet(factory, expression.getLeft(), mapping).complement();
    }

    if (expression.isAND()) {
      ValuationSet left = toValuationSet(factory, expression.getLeft(), mapping);
      ValuationSet right = toValuationSet(factory, expression.getRight(), mapping);
      return left.intersection(right);
    }

    if (expression.isOR()) {
      ValuationSet left = toValuationSet(factory, expression.getLeft(), mapping);
      ValuationSet right = toValuationSet(factory, expression.getRight(), mapping);
      return left.union(right);
    }

    throw new IllegalArgumentException("Unsupported Case: " + expression);
  }

  public static Optional<ValuationSet> union(Iterable<ValuationSet> sets) {
    Iterator<ValuationSet> iterator = sets.iterator();
    if (!iterator.hasNext()) {
      return Optional.empty();
    }
    ValuationSetFactory vsFactory = iterator.next().getFactory();
    return Optional.of(vsFactory.union(sets));
  }
}
