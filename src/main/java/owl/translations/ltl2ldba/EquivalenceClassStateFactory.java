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

package owl.translations.ltl2ldba;

import com.google.common.collect.ImmutableSet;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.rewriter.NormalForms;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public class EquivalenceClassStateFactory {

  private final boolean eagerUnfold;
  private final EquivalenceClassFactory factory;
  private final boolean removeRedundantObligations;

  EquivalenceClassStateFactory(EquivalenceClassFactory factory,
    ImmutableSet<Configuration> configuration) {
    this(factory, configuration.contains(Configuration.EAGER_UNFOLD),
      configuration.contains(Configuration.OPTIMISED_STATE_STRUCTURE));
  }

  public EquivalenceClassStateFactory(EquivalenceClassFactory factory, boolean eagerUnfold,
    boolean removeRedundantObligations) {
    this.factory = factory;
    this.eagerUnfold = eagerUnfold;
    this.removeRedundantObligations = removeRedundantObligations;
  }

  public EquivalenceClass getInitial(Formula... formulas) {
    return getInitial(List.of(formulas));
  }

  private EquivalenceClass getInitial(Iterable<Formula> formulas) {
    return getInitial(factory.of(Conjunction.of(formulas)));
  }

  public EquivalenceClass getInitial(EquivalenceClass clazz, EquivalenceClass... environmentArray) {
    EquivalenceClass initial = eagerUnfold ? clazz.unfold() : clazz;
    return removeRedundantObligations(initial, environmentArray);
  }

  public EquivalenceClass getNondetSuccessor(EquivalenceClass clazz, BitSet valuation) {
    return eagerUnfold ? clazz.temporalStep(valuation) : clazz.unfoldTemporalStep(valuation);
  }

  public BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
    if (eagerUnfold) {
      return clazz.getAtoms();
    } else {
      return clazz.unfold().getAtoms();
    }
  }

  public EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation,
    EquivalenceClass... environmentArray) {
    EquivalenceClass successor = eagerUnfold
      ? clazz.temporalStepUnfold(valuation)
      : clazz.unfoldTemporalStep(valuation);
    return removeRedundantObligations(successor, environmentArray);
  }

  @Nullable
  public EquivalenceClass[] getSuccessors(EquivalenceClass[] clazz, BitSet valuation,
    @Nullable EquivalenceClass environment) {
    EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

    for (int i = clazz.length - 1; i >= 0; i--) {
      successors[i] = getSuccessor(clazz[i], valuation, environment);

      if (successors[i].isFalse()) {
        return null;
      }
    }

    return successors;
  }

  private EquivalenceClass removeRedundantObligations(EquivalenceClass state,
    EquivalenceClass... environmentArray) {
    if (removeRedundantObligations && environmentArray.length > 0) {
      EquivalenceClass environment = factory.conjunction(environmentArray);

      if (environment.implies(state)) {
        return factory.getTrue();
      }
    }

    return state;
  }

  public List<EquivalenceClass> splitEquivalenceClass(EquivalenceClass clazz) {
    assert clazz.getRepresentative() != null;
    List<EquivalenceClass> successors = NormalForms.toDnf(clazz.getRepresentative())
      .stream()
      .map(this::getInitial)
      .collect(Collectors.toList());

    if (removeRedundantObligations) {
      // TODO Check if this actually is allowed. Maybe rather make successor immutable and create
      // a filtered version?
      //noinspection ObjectEquality
      successors.removeIf(x -> successors.stream().anyMatch(y -> x != y && x.implies(y)));
    }

    return successors;
  }
}
