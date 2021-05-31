/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.logic.propositional.sat;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Sets;
import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.collections.Collections3;
import owl.logic.propositional.ConjunctiveNormalForm;
import owl.logic.propositional.PropositionalFormula;

/**
 * Interface for SAT-solver for propositional formulas.
 */
public final class Solver {

  private static final Engine DEFAULT_ENGINE = Engine.JBDD;

  private Solver() {}

  public static IncrementalSolver incrementalSolver() {
    return incrementalSolver(DEFAULT_ENGINE);
  }

  public static IncrementalSolver incrementalSolver(Engine engine) {
    assert engine == Engine.JBDD;
    return new JbddIncrementalSolver();
  }

  /**
   * Determine if the the given conjunctiveNormalForm is satisfiable and if this is the case return
   * a satisfying assignment. Note that variables are shifted by 1. Thus {@code bs.get(0)} retrieves
   * the assigend value to variable 1 in the given conjunctiveNormalForm.
   *
   * @param clauses in conjunctive normal form.
   * @return {@link Optional#empty()} if the given conjunctiveNormalForm is not satisfiable.
   *     Otherwise a satisfying assignment.
   */
  public static Optional<BitSet> model(ImmutableIntArray clauses) {
    return model(clauses, DEFAULT_ENGINE);
  }

  public static Optional<BitSet> model(ImmutableIntArray clauses, Engine engine) {
    var incrementalSolver = incrementalSolver(engine);
    incrementalSolver.pushClauses(clauses);
    return incrementalSolver.model();
  }

  public static <V> Optional<Set<V>> model(PropositionalFormula<V> formula) {
    return model(formula, DEFAULT_ENGINE);
  }

  public static <V> Optional<Set<V>> model(PropositionalFormula<V> preFormula, Engine engine) {
    var formula = preFormula.nnf();

    if (formula instanceof PropositionalFormula.Disjunction) {
      for (var disjunct : ((PropositionalFormula.Disjunction<V>) formula).disjuncts) {
        Optional<Set<V>> satisfiable = model(disjunct, engine);

        if (satisfiable.isPresent()) {
          return satisfiable;
        }
      }

      return Optional.empty();
    }

    // Pre-process and replace single polarity with fixed value in formula.
    var polarity = formula.polarity();

    var simplifiedFormula = formula.substitute(variable -> {
      switch (polarity.get(variable)) {
        case POSITIVE:
          return PropositionalFormula.trueConstant();

        case NEGATIVE:
          return PropositionalFormula.falseConstant();

        default:
          return PropositionalFormula.Variable.of(variable);
      }
    });

    // Translate into equisatisfiable CNF.
    var conjunctiveNormalForm = new ConjunctiveNormalForm<>(simplifiedFormula);
    var modelSimplifiedFormula = model(conjunctiveNormalForm.clauses, engine)
      .map(bitSet -> bitSet.stream()
        .map(x -> x + 1) // shift indices
        .filter(conjunctiveNormalForm.variableMapping::containsValue) // skip Tsetin variables
        .mapToObj(i -> conjunctiveNormalForm.variableMapping.inverse().get(i))
      .collect(Collectors.toSet()));

    if (modelSimplifiedFormula.isEmpty()) {
      return modelSimplifiedFormula;
    }

    polarity.forEach((variable, value) -> {
      if (value == PropositionalFormula.Polarity.POSITIVE) {
        modelSimplifiedFormula.ifPresent(x -> x.add(variable));
      }
    });

    return modelSimplifiedFormula;
  }

  public static <V> List<Set<V>> maximalModels(
    PropositionalFormula<V> formula, Set<V> upperBound) {

    return maximalModels(formula, upperBound, DEFAULT_ENGINE);
  }

  @SuppressWarnings("PMD.AssignmentInOperand")
  public static <V> List<Set<V>> maximalModels(
    PropositionalFormula<V> formula, Set<V> upperBound, Engine engine) {

    PropositionalFormula<V> normalisedFormula = formula.substitute(
      variable -> upperBound.contains(variable)
        ? PropositionalFormula.Variable.of(variable)
        : PropositionalFormula.falseConstant())
      .nnf();

    // Preprocessing to reduce enumeration of models using the SAT solver.

    // 1. Check trivial model.
    if (normalisedFormula.evaluate(upperBound)) {
      return List.of(upperBound);
    }

    // 2. Compute lower-bound and replace positive variables by true.
    {
      Set<V> lowerBound = new HashSet<>(upperBound);

      normalisedFormula.polarity().forEach((variable, variablePolarity) -> {
        if (variablePolarity != PropositionalFormula.Polarity.POSITIVE) {
          lowerBound.remove(variable);
        }
      });

      if (!lowerBound.isEmpty()) {
        PropositionalFormula<V> restrictedFormula = normalisedFormula.substitute(
          variable -> lowerBound.contains(variable)
            ? PropositionalFormula.trueConstant()
            : PropositionalFormula.Variable.of(variable));

        List<Set<V>> restrictedMaximalModels = maximalModels(
          restrictedFormula, new HashSet<>(Sets.difference(upperBound, lowerBound)));

        restrictedMaximalModels.forEach(model -> model.addAll(lowerBound));
        return restrictedMaximalModels;
      }
    }

    // 3. If the formula is in DNF, extract information
    List<Set<V>> maximalModels = new ArrayList<>();

    for (var disjunct : PropositionalFormula.disjuncts(normalisedFormula)) {
      var potentialModel = new HashSet<>(upperBound);

      for (var conjunct : PropositionalFormula.conjuncts(disjunct)) {
        if ((conjunct instanceof PropositionalFormula.Negation)) {
          var negation = (PropositionalFormula.Negation<V>) conjunct;
          potentialModel.remove(((PropositionalFormula.Variable<V>) (negation.operand)).variable);
        }
      }

      if (normalisedFormula.evaluate(potentialModel)) {
        assert !potentialModel.equals(upperBound);
        assert upperBound.containsAll(potentialModel);
        maximalModels.add(potentialModel);
      }
    }

    maximalModels = Collections3.maximalElements(maximalModels, (x, y) -> y.containsAll(x));

    // Enumerate models using a sat solver.
    var conjunctiveNormalForm = new ConjunctiveNormalForm<>(normalisedFormula);
    var incrementalSolver = incrementalSolver(engine);

    incrementalSolver.pushClauses(conjunctiveNormalForm.clauses);
    maximalModels
      .forEach(x -> blockModelAndAllSubsets(incrementalSolver, conjunctiveNormalForm, x));

    // single subset optimisation
    Optional<BitSet> model;

    while ((model = incrementalSolver.model()).isPresent()) {
      // Model
      BitSet prunedModel = model.get();
      prunedModel.clear(conjunctiveNormalForm.tsetinVariablesLowerBound - 1, prunedModel.length());

      // Map model to Set<V>.
      Set<V> mappedModel = prunedModel.stream()
        .mapToObj(i -> conjunctiveNormalForm.variableMapping.inverse().get(i + 1))
        .collect(Collectors.toSet());

      assert normalisedFormula.evaluate(mappedModel);
      maximalModels.add(mappedModel);
      maximalModels = Collections3.maximalElements(maximalModels, (x, y) -> y.containsAll(x));

      // Block and continue.
      blockModelAndAllSubsets(incrementalSolver, conjunctiveNormalForm, mappedModel);
    }

    return maximalModels;
  }

  private static <V> void blockModelAndAllSubsets(
    IncrementalSolver solver, ConjunctiveNormalForm<V> encoding, Set<V> model) {

    int[] blockingClause = IntStream.range(1, encoding.tsetinVariablesLowerBound)
      .filter(i -> !model.contains(requireNonNull(encoding.variableMapping.inverse().get(i))))
      .toArray();

    solver.pushClauses(blockingClause);
  }

  enum Engine {
    JBDD
  }
}
