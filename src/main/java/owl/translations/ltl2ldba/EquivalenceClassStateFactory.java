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

import de.tum.in.naturals.bitset.BitSets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.edge.Edge;
import owl.collections.LabelledTree;
import owl.collections.ValuationSet;
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

  public EquivalenceClass getNondeterministicSuccessor(EquivalenceClass clazz, BitSet valuation) {
    return eagerUnfold ? clazz.temporalStep(valuation) : clazz.unfoldTemporalStep(valuation);
  }

  public BitSet getSensitiveAlphabet(EquivalenceClass clazz) {
    if (eagerUnfold) {
      return clazz.atomicPropositions();
    } else {
      return clazz.unfold().atomicPropositions();
    }
  }

  public EquivalenceClass getSuccessor(EquivalenceClass clazz, BitSet valuation,
    EquivalenceClass... environmentArray) {
    EquivalenceClass successor = eagerUnfold
      ? clazz.temporalStepUnfold(valuation)
      : clazz.unfoldTemporalStep(valuation);
    return removeRedundantObligations(successor, environmentArray);
  }

  public Map<EquivalenceClass, ValuationSet> getSuccessors(EquivalenceClass clazz) {
    var tree = eagerUnfold ? clazz.temporalStepTree() : clazz.unfold().temporalStepTree();
    return getSuccessorsRecursive(tree, new HashMap<>(), x -> x);
  }

  public Map<Edge<EquivalenceClass>, ValuationSet> getEdges(EquivalenceClass clazz) {
    var tree = eagerUnfold ? clazz.temporalStepTree() : clazz.unfold().temporalStepTree();
    return getSuccessorsRecursive(tree, new HashMap<>(), Edge::of);
  }

  public Map<Edge<EquivalenceClass>, ValuationSet> getEdges(EquivalenceClass clazz,
    Function<EquivalenceClass, OptionalInt> edgeLabel) {
    var tree = eagerUnfold ? clazz.temporalStepTree() : clazz.unfold().temporalStepTree();

    Function<EquivalenceClass, Edge<EquivalenceClass>> constructor = x -> {
      var optional = edgeLabel.apply(x);
      return optional.isPresent() ? Edge.of(x, optional.getAsInt()) : Edge.of(x);
    };

    return getSuccessorsRecursive(tree, new HashMap<>(), constructor);
  }

  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private <T> Map<T, ValuationSet> getSuccessorsRecursive(
    LabelledTree<Integer, EquivalenceClass> tree,
    Map<LabelledTree<Integer, EquivalenceClass>, Map<T, ValuationSet>> cache,
    Function<EquivalenceClass, T> constructor) {
    var map = cache.get(tree);

    if (map != null) {
      return map;
    }

    if (tree instanceof LabelledTree.Leaf) {
      var label = ((LabelledTree.Leaf<?, EquivalenceClass>) tree).getLabel();
      var clazz = eagerUnfold ? label.unfold() : label;
      map = clazz.isFalse()
        ? Map.of()
        : Map.of(constructor.apply(clazz), factories.vsFactory.universe());
    } else {
      var literal = BitSets.of(((LabelledTree.Node<Integer, ?>) tree).getLabel());
      var posMask = factories.vsFactory.of(literal, literal);
      var negMask = posMask.complement();
      var children = ((LabelledTree.Node<Integer, EquivalenceClass>) tree).getChildren();
      var finalMap = new HashMap<T, ValuationSet>();

      getSuccessorsRecursive(children.get(0), cache, constructor).forEach(
        ((clazz, set) -> finalMap.merge(clazz, set.intersection(posMask), ValuationSet::union)));
      getSuccessorsRecursive(children.get(1), cache, constructor).forEach(
        ((clazz, set) -> finalMap.merge(clazz, set.intersection(negMask), ValuationSet::union)));

      map = finalMap;
    }

    cache.put(tree, map);
    return map;
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
