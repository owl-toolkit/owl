/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.edge.Edge;
import owl.collections.ValuationTree;
import owl.factories.Factories;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.rewriter.NormalForms;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public class EquivalenceClassStateFactory {

  private final boolean eagerUnfold;
  private final Factories factories;
  private final boolean removeRedundantObligations;

  EquivalenceClassStateFactory(Factories factories, Set<Configuration> configuration) {
    this(factories, configuration.contains(Configuration.EAGER_UNFOLD),
      configuration.contains(Configuration.OPTIMISED_STATE_STRUCTURE));
  }

  public EquivalenceClassStateFactory(Factories factories, boolean eagerUnfold,
    boolean removeRedundantObligations) {
    this.factories = factories;
    this.eagerUnfold = eagerUnfold;
    this.removeRedundantObligations = removeRedundantObligations;
  }

  public EquivalenceClass getInitial(Formula... formulas) {
    return getInitial(Arrays.asList(formulas));
  }

  private EquivalenceClass getInitial(Collection<Formula> formulas) {
    return getInitial(factories.eqFactory.of(Conjunction.of(formulas)));
  }

  public EquivalenceClass getInitial(EquivalenceClass clazz, EquivalenceClass... environmentArray) {
    EquivalenceClass initial = eagerUnfold ? clazz.unfold() : clazz;
    return removeRedundantObligations(initial, environmentArray);
  }

  public EquivalenceClass nondeterministicPreSuccessor(EquivalenceClass clazz, BitSet valuation) {
    return eagerUnfold ? clazz.temporalStep(valuation) : clazz.unfoldTemporalStep(valuation);
  }

  public BitSet sensitiveAlphabet(EquivalenceClass clazz) {
    if (eagerUnfold) {
      return clazz.atomicPropositions();
    } else {
      return clazz.unfold().atomicPropositions();
    }
  }

  public ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass clazz) {
    return eagerUnfold
      ? clazz.temporalStepTree(this::successorToEdge)
      : clazz.unfold().temporalStepTree(this::successorToEdge);
  }

  public ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass clazz,
    Function<EquivalenceClass, OptionalInt> edgeLabel) {
    return eagerUnfold
      ? clazz.temporalStepTree(x -> successorToEdge(x, edgeLabel))
      : clazz.unfold().temporalStepTree(x -> successorToEdge(x, edgeLabel));
  }

  public EquivalenceClass successor(EquivalenceClass clazz, BitSet valuation,
    EquivalenceClass... environmentArray) {
    EquivalenceClass successor = eagerUnfold
      ? clazz.temporalStepUnfold(valuation)
      : clazz.unfoldTemporalStep(valuation);
    return removeRedundantObligations(successor, environmentArray);
  }

  private Set<Edge<EquivalenceClass>> successorToEdge(EquivalenceClass preSuccessor) {
    EquivalenceClass successor = eagerUnfold ? preSuccessor.unfold() : preSuccessor;
    return successor.isFalse() ? Set.of() : Set.of(Edge.of(successor));
  }

  private Set<Edge<EquivalenceClass>> successorToEdge(EquivalenceClass preSuccessor,
    Function<EquivalenceClass, OptionalInt> edgeLabel) {
    EquivalenceClass successor = eagerUnfold ? preSuccessor.unfold() : preSuccessor;

    if (successor.isFalse()) {
      return Set.of();
    }

    OptionalInt optional = edgeLabel.apply(successor);

    if (optional.isPresent()) {
      return Set.of(Edge.of(successor, optional.getAsInt()));
    } else {
      return Set.of(Edge.of(successor));
    }
  }

  @Nullable
  public EquivalenceClass[] successors(EquivalenceClass[] clazz, BitSet valuation,
    @Nullable EquivalenceClass environment) {
    EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

    for (int i = clazz.length - 1; i >= 0; i--) {
      successors[i] = successor(clazz[i], valuation, environment);

      if (successors[i].isFalse()) {
        return null;
      }
    }

    return successors;
  }

  private EquivalenceClass removeRedundantObligations(EquivalenceClass state,
    EquivalenceClass... environmentArray) {
    if (removeRedundantObligations && environmentArray.length > 0) {
      EquivalenceClass environment = factories.eqFactory.conjunction(environmentArray);

      if (environment.implies(state)) {
        return factories.eqFactory.getTrue();
      }
    }

    return state;
  }

  public List<EquivalenceClass> splitEquivalenceClass(EquivalenceClass clazz) {
    assert clazz.representative() != null;
    List<EquivalenceClass> successors = NormalForms.toDnf(clazz.representative())
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
