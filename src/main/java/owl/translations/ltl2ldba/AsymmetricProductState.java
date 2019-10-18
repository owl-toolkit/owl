/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import java.util.List;
import java.util.Objects;
import owl.ltl.EquivalenceClass;
import owl.ltl.LtlLanguageExpressible;
import owl.translations.mastertheorem.AsymmetricEvaluatedFixpoints;
import owl.util.StringUtil;

public final class AsymmetricProductState implements LtlLanguageExpressible {

  // Index of the current checked cosafety formula
  // [0, |gCoSafety| - 1] -> gCoSafety
  // [-|gfCoSafety|, -1] -> gfCoSafety
  public final int index;
  public final EquivalenceClass currentCoSafety;
  public final List<EquivalenceClass> nextCoSafety;

  public final EquivalenceClass safety;

  private final EquivalenceClass language;
  private final int hashCode;

  public final AsymmetricEvaluatedFixpoints evaluatedFixpoints;
  public final AsymmetricEvaluatedFixpoints.DeterministicAutomata automata;

  AsymmetricProductState(int index, EquivalenceClass safety,
    EquivalenceClass currentCoSafety, List<EquivalenceClass> nextCoSafety,
    AsymmetricEvaluatedFixpoints evaluatedFixpoints,
    AsymmetricEvaluatedFixpoints.DeterministicAutomata automata) {
    assert (0 <= index && index < automata.coSafety.size())
      || (index < 0 && -index <= automata.fCoSafety.size())
      || (automata.coSafety.isEmpty() && automata.fCoSafety.isEmpty() && index == 0);

    this.index = index;
    this.currentCoSafety = currentCoSafety;
    this.evaluatedFixpoints = Objects.requireNonNull(evaluatedFixpoints);
    this.safety = safety;
    this.nextCoSafety = List.copyOf(nextCoSafety);
    this.hashCode = Objects.hash(currentCoSafety, evaluatedFixpoints, safety, index, nextCoSafety);
    this.automata = automata;

    var language = safety.and(currentCoSafety).and(evaluatedFixpoints.language());

    for (EquivalenceClass clazz : nextCoSafety) {
      language = language.and(clazz);
    }

    this.language = language;
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
    return other.hashCode == hashCode
      && index == other.index
      && safety.equals(other.safety)
      && currentCoSafety.equals(other.currentCoSafety)
      && nextCoSafety.equals(other.nextCoSafety)
      && evaluatedFixpoints.equals(other.evaluatedFixpoints);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public EquivalenceClass language() {
    return language;
  }

  @Override
  public String toString() {
    return evaluatedFixpoints + StringUtil.join(safety.isTrue() ? null : "GWR=" + safety,
      index == 0 ? null : "i=" + index,
      currentCoSafety.isTrue() ? null : "C=" + currentCoSafety,
      nextCoSafety.isEmpty() ? null : "N=" + nextCoSafety);
  }
}
