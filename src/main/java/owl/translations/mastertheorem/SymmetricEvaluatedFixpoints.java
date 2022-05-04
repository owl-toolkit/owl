/*
 * Copyright (C) 2018, 2022  (Salomon Sickert)
 *
 * This file is part of Owl.
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

package owl.translations.mastertheorem;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.bdd.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Fixpoint;
import owl.ltl.Formula;
import owl.ltl.Formulas;
import owl.ltl.GOperator;
import owl.ltl.LtlLanguageExpressible;
import owl.ltl.MOperator;
import owl.ltl.UOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.rewriter.SimplifierRepository;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.canonical.NonDeterministicConstructions;

public final class SymmetricEvaluatedFixpoints
    implements Comparable<SymmetricEvaluatedFixpoints>, LtlLanguageExpressible {

  public final Fixpoints fixpoints;

  /**
   * Corresponds to safetyAutomaton.
   */
  public final Set<FOperator> almostAlways;

  /**
   * Corresponds to gfCoSafetyAutomaton.
   */
  public final Set<GOperator> infinitelyOften;

  private final EquivalenceClass language;

  private SymmetricEvaluatedFixpoints(
      Fixpoints fixpoints,
      Collection<FOperator> almostAlways,
      Collection<GOperator> infinitelyOften,
      EquivalenceClass language) {
    this.fixpoints = fixpoints;
    this.almostAlways = Set.copyOf(almostAlways);
    this.infinitelyOften = Set.copyOf(infinitelyOften);
    this.language = language;
  }

  public static Set<SymmetricEvaluatedFixpoints> build(
      Formula formula, Fixpoints fixpoints, Factories factories) {
    var unusedFixpoints = new HashSet<>(fixpoints.fixpoints());
    var toCoSafety = new ExtendedRewriter.ToCoSafety(fixpoints, unusedFixpoints::remove);
    var toSafety = new ExtendedRewriter.ToSafety(fixpoints, unusedFixpoints::remove);

    Set<GOperator> infinitelyOftenFormulas = new HashSet<>();

    for (Fixpoint.LeastFixpoint leastFixpoint : fixpoints.leastFixpoints()) {
      Formula result = GOperator.of(FOperator.of(toCoSafety.apply(leastFixpoint.widen())));
      result = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(result);
      result = SimplifierRepository.PULL_UP_X.apply(result);
      Formula infinitelyOften = unwrapX(result);

      if (infinitelyOften.equals(BooleanConstant.TRUE)) {
        continue;
      }

      // Fixpoints are inconsistent.
      if (infinitelyOften.equals(BooleanConstant.FALSE)) {
        return Set.of();
      }

      for (Set<Formula> clause : NormalForms.toCnf(infinitelyOften)) {
        assert !clause.isEmpty();
        Formula disjunction = Disjunction.of(clause.stream()
            .map(SymmetricEvaluatedFixpoints::unwrapGf));
        assert !(disjunction instanceof BooleanConstant);
        infinitelyOftenFormulas.add(wrapGf(disjunction));
      }
    }

    {
      List<GOperator> sortedInfinitelyOftenFormulas = new ArrayList<>(infinitelyOftenFormulas);
      sortedInfinitelyOftenFormulas.sort(Comparator.reverseOrder());

      for (GOperator gOperator : sortedInfinitelyOftenFormulas) {
        var operand = unwrapGf(gOperator);

        if (operand instanceof Conjunction) {
          for (Formula conjunct : operand.operands) {
            Formula unwrappedConjunct = unwrapX(conjunct);

            if (unwrappedConjunct instanceof FOperator) {
              infinitelyOftenFormulas.removeIf(
                  y -> y.operand().equals(unwrappedConjunct));
            } else if (unwrappedConjunct instanceof UOperator) {
              infinitelyOftenFormulas.removeIf(
                  y -> unwrapGf(y).equals(unwrapX(((UOperator) unwrappedConjunct).rightOperand())));
            } else if (unwrappedConjunct instanceof MOperator) {
              infinitelyOftenFormulas.removeIf(
                  y -> unwrapGf(y).equals(unwrapX(((MOperator) unwrappedConjunct).leftOperand())));
            } else {
              infinitelyOftenFormulas.remove(wrapGf(unwrappedConjunct));
            }
          }
        }
      }
    }

    infinitelyOftenFormulas = Set.of(infinitelyOftenFormulas.toArray(GOperator[]::new));

    List<Set<FOperator>> almostAlwaysFormulasAlternatives = new ArrayList<>();

    for (Fixpoint.GreatestFixpoint greatestFixpoint : fixpoints.greatestFixpoints()) {
      Formula result = FOperator.of(GOperator.of(toSafety.apply(greatestFixpoint.widen())));
      result = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(result);
      result = SimplifierRepository.PULL_UP_X.apply(result);
      Formula almostAlways = unwrapX(result);

      if (almostAlways.equals(BooleanConstant.TRUE)) {
        continue;
      }

      // Fixpoints are inconsistent.
      if (almostAlways.equals(BooleanConstant.FALSE)) {
        return Set.of();
      }

      Set<FOperator> alternatives = new HashSet<>();

      for (Set<Formula> clause : NormalForms.toDnf(almostAlways)) {
        assert !clause.isEmpty();
        Formula conjunction = Conjunction.of(
            clause.stream().map(SymmetricEvaluatedFixpoints::unwrapFg));
        assert !(conjunction instanceof BooleanConstant);
        alternatives.add(wrapFg(conjunction));
      }

      assert !alternatives.isEmpty();
      almostAlwaysFormulasAlternatives.add(alternatives);
    }

    // Detect un-used fixpoint operators outside of a fixpoint scope by examining the top-level
    // formula.
    var toSafetyWithoutGreatestFixpoints
        = new ExtendedRewriter.ToSafety(fixpoints.leastFixpoints(), unusedFixpoints::remove);

    for (Formula.TemporalOperator greatestFixpoint :
        formula.subformulas(Fixpoint.GreatestFixpoint.class::isInstance,
            Formula.TemporalOperator.class::cast)) {
      toSafetyWithoutGreatestFixpoints.apply(greatestFixpoint);
    }

    if (!unusedFixpoints.isEmpty()) {
      return Set.of();
    }

    Set<SymmetricEvaluatedFixpoints> fixpointsSet = new HashSet<>();

    for (List<FOperator> almostAlwaysFormulas
        : Sets.cartesianProduct(almostAlwaysFormulasAlternatives)) {
      var language = Conjunction.of(Stream.concat(
          almostAlwaysFormulas.stream().map(Formula.UnaryTemporalOperator::operand),
          infinitelyOftenFormulas.stream()));
      var languageClazz = factories.eqFactory.of(language.unfold());

      if (languageClazz.isFalse()) {
        continue;
      }

      fixpointsSet.add(new SymmetricEvaluatedFixpoints(fixpoints,
          almostAlwaysFormulas, infinitelyOftenFormulas, languageClazz));
    }

    return fixpointsSet;
  }

  @Override
  public int compareTo(SymmetricEvaluatedFixpoints that) {
    // Order fixpoints with large infinitelyOften components early.
    int comparison = Formulas.compare(this.infinitelyOften, that.infinitelyOften);

    if (comparison != 0) {
      return -comparison;
    }

    // Order fixpoints with large almostAlways components later.
    comparison = Formulas.compare(this.almostAlways, that.almostAlways);

    if (comparison != 0) {
      return comparison;
    }

    // Original fixpoints are the tie-breaker.
    return this.fixpoints.compareTo(that.fixpoints);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof SymmetricEvaluatedFixpoints)) {
      return false;
    }

    SymmetricEvaluatedFixpoints that = (SymmetricEvaluatedFixpoints) o;
    return fixpoints.equals(that.fixpoints)
        && almostAlways.equals(that.almostAlways)
        && infinitelyOften.equals(that.infinitelyOften);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fixpoints, almostAlways, infinitelyOften);
  }

  public boolean isEmpty() {
    return almostAlways.isEmpty() && infinitelyOften.isEmpty();
  }

  public boolean isSafety() {
    return infinitelyOften.isEmpty();
  }

  public boolean isLiveness() {
    return almostAlways.isEmpty();
  }

  @Override
  public EquivalenceClass language() {
    return language;
  }

  @Override
  public String toString() {
    return "<" + almostAlways + ", " + infinitelyOften + '>';
  }

  // Automata Classes

  public DeterministicAutomata deterministicAutomata(Factories factories, boolean generalized) {
    var safetyAutomaton = DeterministicConstructions.Safety.of(factories,
        Conjunction.of(almostAlways.stream().map(Formula.UnaryTemporalOperator::operand)), false);

    var gfCoSafetyAutomaton = infinitelyOften.isEmpty()
        ? null
        : DeterministicConstructions.GfCoSafety
            .of(factories, new TreeSet<>(infinitelyOften), generalized);

    assert !safetyAutomaton.initialState().isFalse();
    return new DeterministicAutomata(gfCoSafetyAutomaton, safetyAutomaton);
  }

  public NonDeterministicAutomata nonDeterministicAutomata(
      Factories factories, boolean generalized) {

    var safetyAutomaton = NonDeterministicConstructions.Safety.of(factories,
        Conjunction.of(almostAlways.stream().map(Formula.UnaryTemporalOperator::operand)));

    var gfCoSafetyAutomaton = infinitelyOften.isEmpty()
        ? null
        : NonDeterministicConstructions.GfCoSafety
            .of(factories, new TreeSet<>(infinitelyOften), generalized);

    return new NonDeterministicAutomata(gfCoSafetyAutomaton, safetyAutomaton);
  }

  public static final class DeterministicAutomata {

    @Nullable
    public final DeterministicConstructions.GfCoSafety gfCoSafetyAutomaton;
    public final DeterministicConstructions.Safety safetyAutomaton;

    private DeterministicAutomata(
        @Nullable DeterministicConstructions.GfCoSafety gfCoSafetyAutomaton,
        DeterministicConstructions.Safety safetyAutomaton) {
      this.gfCoSafetyAutomaton = gfCoSafetyAutomaton;
      this.safetyAutomaton = safetyAutomaton;
    }
  }

  public static final class NonDeterministicAutomata {

    @Nullable
    public final NonDeterministicConstructions.GfCoSafety gfCoSafetyAutomaton;
    public final NonDeterministicConstructions.Safety safetyAutomaton;

    private NonDeterministicAutomata(
        @Nullable NonDeterministicConstructions.GfCoSafety gfCoSafetyAutomaton,
        NonDeterministicConstructions.Safety safetyAutomaton) {
      this.gfCoSafetyAutomaton = gfCoSafetyAutomaton;
      this.safetyAutomaton = safetyAutomaton;
    }
  }

  // Utility functions

  private static Formula unwrapFg(Formula formula) {
    return ((GOperator) ((FOperator) formula).operand()).operand();
  }

  private static Formula unwrapGf(Formula formula) {
    return ((FOperator) ((GOperator) formula).operand()).operand();
  }

  private static Formula unwrapX(Formula formula) {
    var unwrappedFormula = formula;

    while (unwrappedFormula instanceof XOperator) {
      unwrappedFormula = ((XOperator) unwrappedFormula).operand();
    }

    return unwrappedFormula;
  }

  private static FOperator wrapFg(Formula formula) {
    if (formula instanceof FOperator && ((FOperator) formula).operand() instanceof GOperator) {
      return (FOperator) formula;
    }

    return formula instanceof GOperator
        ? new FOperator(formula)
        : new FOperator(new GOperator(formula));
  }

  private static GOperator wrapGf(Formula formula) {
    if (formula instanceof GOperator && ((GOperator) formula).operand() instanceof FOperator) {
      return (GOperator) formula;
    }

    return formula instanceof FOperator
        ? new GOperator(formula)
        : new GOperator(new FOperator(formula));
  }
}