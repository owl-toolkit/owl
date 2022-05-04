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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.bdd.Factories;
import owl.collections.Collections3;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Fixpoint;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LtlLanguageExpressible;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.translations.canonical.DeterministicConstructions;

// TODO: migrate to record / AutoValue.
public final class AsymmetricEvaluatedFixpoints
    implements Comparable<AsymmetricEvaluatedFixpoints>, LtlLanguageExpressible {

  public final Fixpoints fixpoints;

  public final Set<GOperator> gSafety;
  public final Set<GOperator> gCoSafety;
  public final Set<GOperator> gfCoSafety;

  public final EquivalenceClass language;

  private AsymmetricEvaluatedFixpoints(Fixpoints fixpoints, Set<GOperator> gSafety,
      Set<GOperator> gCoSafety, Set<GOperator> gfCoSafety, EquivalenceClass language) {

    this.fixpoints = fixpoints;

    this.gSafety = Set.copyOf(gSafety);
    this.gCoSafety = Set.copyOf(gCoSafety);
    this.gfCoSafety = Set.copyOf(gfCoSafety);

    this.language = language;

    assert this.gSafety.stream().allMatch(SyntacticFragments::isSafety);
    assert this.gCoSafety.stream().allMatch(SyntacticFragments::isGCoSafety);
    assert this.gfCoSafety.stream().allMatch(SyntacticFragments::isGfCoSafety);
  }

  /**
   * Construct the recurring gCosafety for a G-set.
   *
   * @return This methods returns null, if the G-set is inconsistent.
   */
  @Nullable
  public static AsymmetricEvaluatedFixpoints build(Fixpoints fixpoints, Factories factories) {
    Preconditions.checkArgument(fixpoints.leastFixpoints().isEmpty());
    Rewriter.ToCoSafety toCoSafety = new Rewriter.ToCoSafety(fixpoints.greatestFixpoints());
    Set<GOperator> gOperatorsRewritten = new HashSet<>();

    for (Fixpoint.GreatestFixpoint greatestFixpoint : fixpoints.greatestFixpoints()) {
      GOperator gOperator = (GOperator) greatestFixpoint;
      Formula operand = gOperator.operand().substitute(toCoSafety);

      // Skip trivial formulas
      if (BooleanConstant.FALSE.equals(operand)) {
        return null;
      }

      if (BooleanConstant.TRUE.equals(operand)) {
        continue;
      }

      Formula gOperatorRewritten = GOperator.of(operand);

      if (gOperatorRewritten instanceof Conjunction) {
        gOperatorRewritten.operands.forEach(x -> gOperatorsRewritten.add((GOperator) x));
      } else {
        gOperatorsRewritten.add((GOperator) gOperatorRewritten);
      }
    }

    Set<GOperator> gSafety = new HashSet<>();
    Set<GOperator> gCoSafety = new HashSet<>();
    Set<GOperator> gfCoSafety = new HashSet<>();

    for (GOperator gOperator : gOperatorsRewritten) {
      if (SyntacticFragments.isSafety(gOperator)) {
        gSafety.add(gOperator);
      } else if (gOperator.operand() instanceof FOperator) {
        gfCoSafety.add(gOperator);
      } else {
        gCoSafety.add(gOperator);
      }
    }

    var language = factories.eqFactory.of(
            Conjunction.of(
                Stream.of(gSafety, gCoSafety, gfCoSafety).flatMap(Collection::stream)))
        .unfold();

    if (language.isFalse() || gOperatorsRewritten.isEmpty()) {
      return null;
    }

    return new AsymmetricEvaluatedFixpoints(fixpoints, gSafety, gCoSafety, gfCoSafety, language);
  }

  @Override
  public int compareTo(AsymmetricEvaluatedFixpoints that) {
    int comparison = fixpoints.compareTo(that.fixpoints);

    if (comparison != 0) {
      return comparison;
    }

    comparison = Collections3.compare(gSafety, that.gSafety);

    if (comparison != 0) {
      return comparison;
    }

    comparison = Collections3.compare(gCoSafety, that.gCoSafety);

    if (comparison != 0) {
      return comparison;
    }

    return Collections3.compare(gfCoSafety, that.gfCoSafety);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof AsymmetricEvaluatedFixpoints)) {
      return false;
    }

    AsymmetricEvaluatedFixpoints that = (AsymmetricEvaluatedFixpoints) o;
    return fixpoints.equals(that.fixpoints)
        && gSafety.equals(that.gSafety)
        && gCoSafety.equals(that.gCoSafety)
        && gfCoSafety.equals(that.gfCoSafety);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fixpoints, gSafety, gCoSafety, gfCoSafety);
  }

  public boolean isSafety() {
    return gCoSafety.isEmpty() && gfCoSafety.isEmpty();
  }

  public boolean isLiveness() {
    return gSafety.isEmpty() && gCoSafety.isEmpty();
  }

  @Override
  public EquivalenceClass language() {
    return language;
  }

  @Override
  public String toString() {
    return String.format("(%s, %s, %s, %s)",
        new TreeSet<>(gSafety), new TreeSet<>(gCoSafety), new TreeSet<>(gfCoSafety), fixpoints);
  }

  // Automata Classes

  public AsymmetricEvaluatedFixpoints.DeterministicAutomata deterministicAutomata(
      Factories factories, boolean generalized) {

    var safetyAutomaton = DeterministicConstructions.Safety.of(
        factories, Conjunction.of(gSafety), false);

    var coSafety = gCoSafety.stream()
        .sorted()
        .map(x -> factories.eqFactory.of(x.operand().unfold()))
        .toList();

    var fCoSafety = new ArrayList<EquivalenceClass>();
    var gfCoSafetyAutomaton = (DeterministicConstructions.GfCoSafety) null;

    if (generalized) {
      var fCoSafetySingleStep = new ArrayList<GOperator>();

      gfCoSafety.stream().sorted().forEachOrdered(x -> {
        if (SyntacticFragment.SINGLE_STEP.contains(((FOperator) x.operand()).operand())) {
          fCoSafetySingleStep.add(x);
        } else {
          fCoSafety.add(factories.eqFactory.of(x.operand().unfold()));
        }
      });

      if (coSafety.isEmpty() && fCoSafety.isEmpty() && !fCoSafetySingleStep.isEmpty()) {
        fCoSafety.add(factories.eqFactory.of(fCoSafetySingleStep.remove(0).operand()).unfold());
      }

      if (!fCoSafetySingleStep.isEmpty()) {
        gfCoSafetyAutomaton = DeterministicConstructions.GfCoSafety.of(factories,
            new HashSet<>(fCoSafetySingleStep), true);
      }
    } else {
      gfCoSafety.stream().sorted().forEachOrdered(
          x -> fCoSafety.add(factories.eqFactory.of(x.operand().unfold())));
    }

    assert !safetyAutomaton.initialState().isFalse();
    return new AsymmetricEvaluatedFixpoints
        .DeterministicAutomata(gfCoSafetyAutomaton, safetyAutomaton, coSafety, fCoSafety);
  }

  public static final class DeterministicAutomata {

    @Nullable
    public final DeterministicConstructions.GfCoSafety gfCoSafetyAutomaton;
    public final DeterministicConstructions.Safety safetyAutomaton;

    // Legacy
    public final List<EquivalenceClass> coSafety;
    public final List<EquivalenceClass> fCoSafety;

    private DeterministicAutomata(
        @Nullable DeterministicConstructions.GfCoSafety gfCoSafetyAutomaton,
        DeterministicConstructions.Safety safetyAutomaton,
        List<EquivalenceClass> coSafety,
        List<EquivalenceClass> fCoSafety) {
      this.gfCoSafetyAutomaton = gfCoSafetyAutomaton;
      this.safetyAutomaton = safetyAutomaton;
      this.coSafety = List.copyOf(coSafety);
      this.fCoSafety = List.copyOf(fCoSafety);
    }
  }
}
