/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.translations.ltl2ldba;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.ltl.EquivalenceClass;
import owl.ltl.LtlLanguageExpressible;
import owl.translations.mastertheorem.AsymmetricEvaluatedFixpoints;

@SuppressWarnings("PMD.DataClass")
public final class AsymmetricProductState implements LtlLanguageExpressible {

  // G-formulas that have to hold infinitely often.
  public final AsymmetricEvaluatedFixpoints evaluatedFixpoints;

  // Round-robin counter of the current checked G(cosafety)-formula
  //
  // [0, |gCoSafety| - 1] -> gCoSafety
  // [-|gfCoSafety|, -1] -> gfCoSafety
  //
  // If the value is negative the current formula is G(FCoSafety)-formula. If it is positive
  // then the formula is a G(CoSafety)-formula.
  public final int index;

  // A single G formula whose operand is in the syntactic co-safety fragment.
  public final EquivalenceClass currentCoSafety;

  // All other G formulas whose operands that are in the syntactic co-safety fragment.
  public final List<EquivalenceClass> nextCoSafety;

  // Conjunction of all G formulas that lie in the syntactic safety fragment.
  public final EquivalenceClass safety;

  @Nullable
  public final AsymmetricEvaluatedFixpoints.DeterministicAutomata automata;

  // Precomputed, derived values.

  // The language of the states, expressed as LTL formula.
  private final EquivalenceClass precomputedLanguage;
  private final int precomputedHashCode;

  public AsymmetricProductState(int index, EquivalenceClass safety,
    EquivalenceClass currentCoSafety, List<EquivalenceClass> nextCoSafety,
    AsymmetricEvaluatedFixpoints evaluatedFixpoints,
    @Nullable
    AsymmetricEvaluatedFixpoints.DeterministicAutomata automata) {

    Preconditions.checkArgument(automata == null
      || (0 <= index && index < automata.coSafety.size())
      || (index < 0 && -index <= automata.fCoSafety.size())
      || (automata.coSafety.isEmpty() && automata.fCoSafety.isEmpty() && index == 0));

    this.index = index;
    this.currentCoSafety = requireNonNull(currentCoSafety);
    this.evaluatedFixpoints = requireNonNull(evaluatedFixpoints);
    this.safety = requireNonNull(safety);
    this.nextCoSafety = List.copyOf(nextCoSafety);
    this.automata = automata;

    precomputedHashCode = hash(currentCoSafety, evaluatedFixpoints, safety, index, nextCoSafety);
    precomputedLanguage = Stream
      .concat(
        Stream.of(safety, currentCoSafety, evaluatedFixpoints.language()),
        nextCoSafety.stream())
      .reduce(EquivalenceClass::and).orElseThrow();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof AsymmetricProductState)) {
      return false;
    }

    AsymmetricProductState other = (AsymmetricProductState) o;
    return precomputedHashCode == other.precomputedHashCode
      && index == other.index
      && safety.equals(other.safety)
      && currentCoSafety.equals(other.currentCoSafety)
      && nextCoSafety.equals(other.nextCoSafety)
      && evaluatedFixpoints.equals(other.evaluatedFixpoints);
  }

  @Override
  public int hashCode() {
    return precomputedHashCode;
  }

  @Override
  public EquivalenceClass language() {
    return precomputedLanguage;
  }

  @Override
  public String toString() {
    String[] pieces = {
      safety.isTrue() ? null : "GWR=" + safety,
      index == 0 ? null : "i=" + index,
      currentCoSafety.isTrue() ? null : "C=" + currentCoSafety,
      nextCoSafety.isEmpty() ? null : "N=" + nextCoSafety
    };

    return evaluatedFixpoints + Arrays.stream(pieces)
      .filter(Objects::nonNull)
      .collect(Collectors.joining(", ", " [", "]"));
  }
}
