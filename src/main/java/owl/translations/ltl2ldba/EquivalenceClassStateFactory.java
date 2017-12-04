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

import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.rewriter.NormalForms;
import owl.translations.Optimisation;

public class EquivalenceClassStateFactory {

  private final boolean eagerUnfold;
  private final EquivalenceClassFactory factory;
  private final boolean removeRedundantObligations;

  public EquivalenceClassStateFactory(EquivalenceClassFactory factory,
    Set<Optimisation> optimisations) {
    this(factory,
      optimisations.contains(Optimisation.EAGER_UNFOLD),
      optimisations.contains(Optimisation.OPTIMISED_STATE_STRUCTURE));
  }

  public EquivalenceClassStateFactory(EquivalenceClassFactory factory, boolean eagerUnfold,
    boolean removeRedundantObligations) {
    this.factory = factory;
    this.eagerUnfold = eagerUnfold;
    this.removeRedundantObligations = removeRedundantObligations;
  }

  private EquivalenceClass and(Iterable<EquivalenceClass> equivalenceClasses) {
    EquivalenceClass conjunction = factory.getTrue().duplicate();

    for (EquivalenceClass equivalenceClass : equivalenceClasses) {
      conjunction = conjunction.andWith(equivalenceClass);
    }

    return conjunction;
  }

  public EquivalenceClass getInitial(Formula... formulas) {
    return getInitial(List.of(formulas));
  }

  private EquivalenceClass getInitial(Iterable<Formula> formulas) {
    EquivalenceClass clazz = factory.createEquivalenceClass(formulas);
    EquivalenceClass initial = getInitial(clazz);
    clazz.free();
    return initial;
  }

  public EquivalenceClass getInitial(EquivalenceClass clazz, EquivalenceClass... environmentArray) {
    EquivalenceClass initial = eagerUnfold ? clazz.unfold() : clazz.duplicate();
    return removeRedundantObligations(initial, environmentArray);
  }

  public EquivalenceClass getNondetSuccessor(EquivalenceClass clazz, BitSet valuation) {
    return eagerUnfold ? clazz.temporalStep(valuation) : clazz.unfoldTemporalStep(valuation);
  }

  public BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
    if (eagerUnfold) {
      return clazz.getAtoms();
    } else {
      EquivalenceClass unfold = clazz.unfold();
      BitSet atoms = unfold.getAtoms();
      unfold.free();
      return atoms;
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
        EquivalenceClassUtil.free(successors);
        return null;
      }
    }

    return successors;
  }

  private EquivalenceClass removeRedundantObligations(EquivalenceClass state,
    EquivalenceClass... environmentArray) {
    if (removeRedundantObligations && environmentArray.length > 0) {
      EquivalenceClass environment = and(List.of(environmentArray));

      if (environment.implies(state)) {
        state.free();
        environment.free();
        return factory.getTrue();
      }

      environment.free();
    }

    return state;
  }

  public List<EquivalenceClass> splitEquivalenceClass(EquivalenceClass clazz) {
    assert clazz.getRepresentative() != null;
    List<EquivalenceClass> successors = NormalForms
      .toDnf(clazz.getRepresentative())
      .stream()
      .map(this::getInitial)
      .collect(Collectors.toCollection(LinkedList::new));

    if (removeRedundantObligations) {
      //noinspection ObjectEquality
      successors.removeIf(x -> successors.stream().anyMatch(y -> x != y && x.implies(y)));
    }

    return successors;
  }
}
