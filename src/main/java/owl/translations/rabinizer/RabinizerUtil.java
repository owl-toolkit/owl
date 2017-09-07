package owl.translations.rabinizer;

import java.util.function.Consumer;
import java.util.function.Predicate;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BinaryModalOperator;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.visitors.Collector;

final class RabinizerUtil {
  private static final Predicate<Formula> modalOperators = formula ->
    formula instanceof UnaryModalOperator || formula instanceof BinaryModalOperator;

  private RabinizerUtil() {}

  public static void forEachSubFormula(EquivalenceClass equivalenceClass,
    Consumer<GOperator> action) {
    Formula representative = equivalenceClass.getRepresentative();
    if (representative == null) {
      for (Formula formula : equivalenceClass.getSupport()) {
        Collector.collectGOperators(formula).forEach(action);
      }
    } else {
      Collector.collectGOperators(representative).forEach(action);
    }
  }

  public static void forEachSupportingSubFormula(EquivalenceClass equivalenceClass,
    Consumer<GOperator> action) {
    // TODO Can we optimize for eager? The stuff below does not work for G(a | (a M Gb))
    // equivalenceClass.getSupport(GOperator.class).forEach(action);

    // Due to the BDD representation, we have to do a somewhat weird construction. The problem is
    // that we can't simply do a class.getSupport(G) to determine the relevant G operators in the
    // formula. For example, to the BDD "X G a" and "G a" have no relation, hence the G-support
    // of "X G a" is empty, although "G a" certainly is important for the formula. So, instead,
    // we determine all relevant temporal operators in the support and for all of those collect the
    // G operators.

    for (Formula temporalOperator : equivalenceClass.getSupport(modalOperators)) {
      if (temporalOperator instanceof GOperator) {
        action.accept((GOperator) temporalOperator);
      } else {
        Formula unwrapped = temporalOperator;
        while (unwrapped instanceof UnaryModalOperator) {
          unwrapped = ((UnaryModalOperator) unwrapped).operand;
          if (unwrapped instanceof GOperator) {
            break;
          }
        }
        if (unwrapped instanceof GOperator) {
          action.accept((GOperator) unwrapped);
        } else if (unwrapped instanceof BinaryModalOperator) {
          BinaryModalOperator binaryOperator = (BinaryModalOperator) unwrapped;

          EquivalenceClassFactory eqFactory = equivalenceClass.getFactory();
          EquivalenceClass leftClass = eqFactory.createEquivalenceClass(binaryOperator.left);
          forEachSupportingSubFormula(leftClass, action);
          leftClass.free();

          EquivalenceClass rightClass = eqFactory.createEquivalenceClass(binaryOperator.right);
          forEachSupportingSubFormula(rightClass, action);
          rightClass.free();
        } else {
          EquivalenceClass unwrappedClass =
            equivalenceClass.getFactory().createEquivalenceClass(unwrapped);
          forEachSupportingSubFormula(unwrappedClass, action);
        }
      }
    }
  }

  static String printRanking(int[] ranking) {
    StringBuilder builder = new StringBuilder(ranking.length * 3 + 2);
    builder.append('[');
    boolean first = true;
    for (int rank : ranking) {
      if (!first) {
        builder.append(',');
      }
      first = false;
      builder.append(rank);
    }
    builder.append(']');
    return builder.toString();
  }
}