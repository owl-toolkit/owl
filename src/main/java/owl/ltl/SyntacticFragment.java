/*
 * Copyright (C) 2016  (See AUTHORS)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.ltl;

import java.util.Set;
import java.util.function.Function;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.ltl.visitors.Visitor;

@SuppressWarnings("PMD.FieldDeclarationsShouldBeAtStartOfClass")
public enum SyntacticFragment {

  ALL(Set.of(
    // Boolean Operators
    Biconditional.class, BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class,
    FOperator.class, UOperator.class, MOperator.class,
    GOperator.class, WOperator.class, ROperator.class)),

  NNF(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class,
    FOperator.class, UOperator.class, MOperator.class,
    GOperator.class, WOperator.class, ROperator.class)),

  FGMU(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class,
    FOperator.class, UOperator.class, MOperator.class,
    GOperator.class)),

  FGX(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class, FOperator.class, GOperator.class)),

  SAFETY(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class, GOperator.class, WOperator.class, ROperator.class)),

  CO_SAFETY(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class, FOperator.class, UOperator.class, MOperator.class)),

  FINITE(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class, XOperator.class)),

  SINGLE_STEP(Set.of(
    // Boolean Operators
    BooleanConstant.class, Conjunction.class, Disjunction.class,

    // Temporal Operators
    Literal.class));

  private final Set<Class<? extends Formula>> clazzes;

  SyntacticFragment(Set<Class<? extends Formula>> clazzes) {
    this.clazzes = clazzes;
  }

  public Set<Class<? extends Formula>> classes() {
    return Set.copyOf(clazzes);
  }

  public boolean contains(Formula formula) {
    return formula.allMatch(x -> clazzes.contains(x.getClass()));
  }

  public static boolean isAlmostAll(Formula formula) {
    return formula instanceof FOperator && ((FOperator) formula).operand instanceof GOperator;
  }

  public static boolean isDetBuchiRecognisable(Formula formula) {
    return formula instanceof GOperator && CO_SAFETY.contains(((GOperator) formula).operand);
  }

  public static boolean isDetCoBuchiRecognisable(Formula formula) {
    return formula instanceof FOperator && SAFETY.contains(((FOperator) formula).operand);
  }

  public static boolean isInfinitelyOften(Formula formula) {
    return formula instanceof GOperator && ((GOperator) formula).operand instanceof FOperator;
  }

  private static Formula normalize(Formula formula, SyntacticFragment fragment,
    Function<Formula, Formula> normalizer) {
    Formula normalizedFormula = normalizer.apply(formula);

    if (!fragment.contains(normalizedFormula)) {
      throw new IllegalArgumentException("Unsupported formula object found in " + normalizedFormula
        + ". Supported classes are: " + fragment.classes());
    }

    return normalizedFormula;
  }

  private static final Visitor<Formula> UNABBREVIATE_VISITOR =
    new UnabbreviateVisitor(WOperator.class, ROperator.class);

  public static Formula normalize(Formula formula, SyntacticFragment fragment) {
    switch (fragment) {
      case ALL:
        return formula;

      case NNF:
        return normalize(formula, NNF, Formula::nnf);

      case FGMU:
        return normalize(formula, FGMU, x -> x.nnf().accept(UNABBREVIATE_VISITOR));

      default:
        throw new UnsupportedOperationException();
    }
  }

  public static LabelledFormula normalize(LabelledFormula formula, SyntacticFragment fragment) {
    return formula.wrap(normalize(formula.formula(), fragment));
  }
}
