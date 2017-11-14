package owl.translations.rabinizer;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
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

  private static void findSupportingSubFormulas(EquivalenceClass equivalenceClass,
    Consumer<GOperator> action) {
    // Due to the BDD representation, we have to do a somewhat weird construction. The problem is
    // that we can't simply do a class.getSupport(G) to determine the relevant G operators in the
    // formula. For example, to the BDD "X G a" and "G a" have no relation, hence the G-support
    // of "X G a" is empty, although "G a" certainly is important for the formula. So, instead,
    // we determine all relevant temporal operators in the support and for all of those collect the
    // G operators.

    // TODO Can we optimize for eager?

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
          findSupportingSubFormulas(leftClass, action);
          leftClass.free();

          EquivalenceClass rightClass = eqFactory.createEquivalenceClass(binaryOperator.right);
          findSupportingSubFormulas(rightClass, action);
          rightClass.free();
        } else {
          EquivalenceClass unwrappedClass =
            equivalenceClass.getFactory().createEquivalenceClass(unwrapped);
          findSupportingSubFormulas(unwrappedClass, action);
          unwrappedClass.free();
        }
      }
    }
  }

  public static Set<GOperator> getRelevantSubFormulas(EquivalenceClass equivalenceClass) {
    Formula representative = equivalenceClass.getRepresentative();
    Set<GOperator> operators;
    if (representative == null) {
      operators = new HashSet<>();
      for (Formula formula : equivalenceClass.getSupport()) {
        operators.addAll(Collector.collectGOperators(formula));
      }
    } else {
      operators = Collector.collectGOperators(representative);
    }
    return operators;
  }

  public static Set<GOperator> getSupportSubFormulas(EquivalenceClass equivalenceClass) {
    if (equivalenceClass.isTrue() || equivalenceClass.isFalse()) {
      return ImmutableSet.of();
    }
    Set<GOperator> operators = new HashSet<>();
    findSupportingSubFormulas(equivalenceClass, operators::add);
    return operators;
  }

  static String printRanking(int[] ranking) {
    if (ranking.length == 0) {
      return "[]";
    }
    StringBuilder builder = new StringBuilder(ranking.length * 3 + 2);
    builder.append('[').append(ranking[0]);
    for (int i = 1; i < ranking.length; i++) {
      builder.append(',').append(ranking[i]);
    }
    builder.append(']');
    return builder.toString();
  }
}