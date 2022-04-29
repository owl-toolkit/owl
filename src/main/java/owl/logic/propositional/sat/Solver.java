/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import owl.collections.Collections3;
import owl.collections.Numbering;
import owl.logic.propositional.ConjunctiveNormalForm;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Conjunction;
import owl.logic.propositional.PropositionalFormula.Disjunction;
import owl.logic.propositional.PropositionalFormula.Negation;
import owl.logic.propositional.PropositionalFormula.Variable;

/**
 * Repository of for SAT-solver implementations for propositional formulas.
 */
public enum Solver {

  DPLL {
    @Nullable
    @Override
    protected BitSet modelImpl(int[][] clauses) {
      return dpll(new ArrayList<>(Arrays.asList(clauses)), new BitSet(), new BitSet());
    }

    @Nullable
    private static BitSet dpll(
        ArrayList<int[]> clauses, BitSet partialAssignment, BitSet assignedVariables) {

      int largestSeenVariable = assignedVariables.length();

      // Unit-Clause Rule
      while (true) {
        BitSet positiveUnitClause = new BitSet(largestSeenVariable);
        BitSet negativeUnitClause = new BitSet(largestSeenVariable);

        // Search for unit clauses.
        for (int[] clause : clauses) {
          assert clause.length >= 1;

          if (clause.length == 1) {
            int literal = clause[0];

            assert literal != 0;
            assert !partialAssignment.get(Math.abs(literal));
            assert !assignedVariables.get(Math.abs(literal));

            if (literal > 0) {
              positiveUnitClause.set(literal);
            } else {
              negativeUnitClause.set(-literal);
            }
          }
        }

        if (positiveUnitClause.isEmpty() && negativeUnitClause.isEmpty()) {
          break;
        }

        // Contradiction
        if (positiveUnitClause.intersects(negativeUnitClause)) {
          return null;
        }

        // Propagate unit clauses.
        partialAssignment.or(positiveUnitClause);
        assignedVariables.or(positiveUnitClause);
        assignedVariables.or(negativeUnitClause);

        var newClauses = new ArrayList<int[]>(clauses.size());

        nextClause:
        for (int clauseIndex = 0, s = clauses.size(); clauseIndex < s; clauseIndex++) {
          int[] clause = clauses.get(clauseIndex);
          int[] newClause = new int[clause.length];

          int i = 0;

          for (int literal : clause) {
            int variable = Math.abs(literal);
            largestSeenVariable = Math.max(largestSeenVariable, variable);

            if (!assignedVariables.get(variable)) {
              newClause[i] = literal;
              i++;
            } else {
              assert positiveUnitClause.get(variable) || negativeUnitClause.get(variable);

              if (partialAssignment.get(variable) == literal > 0) {
                continue nextClause;
              }
            }
          }

          if (i == 0) {
            return null;
          } else if (i == clause.length) {
            // We did not change anything. Recycle old clause.
            newClauses.add(clause);
          } else {
            newClauses.add(Arrays.copyOf(newClause, i));
          }
        }

        clauses = newClauses;
      }

      // Pure-Literal Rule
      while (true) {
        BitSet positiveLiterals = new BitSet(largestSeenVariable);
        BitSet negativeLiterals = new BitSet(largestSeenVariable);

        // Search for pure literals.
        for (int[] clause : clauses) {
          for (int literal : clause) {
            if (literal > 0) {
              positiveLiterals.set(literal);
            } else {
              assert literal < 0;
              negativeLiterals.set(-literal);
            }
          }
        }

        BitSet purePositiveLiterals = (BitSet) positiveLiterals.clone();
        purePositiveLiterals.andNot(negativeLiterals);

        BitSet pureNegativeLiterals = (BitSet) negativeLiterals.clone();
        pureNegativeLiterals.andNot(positiveLiterals);

        if (purePositiveLiterals.isEmpty() && pureNegativeLiterals.isEmpty()) {
          break;
        }

        assert !purePositiveLiterals.intersects(pureNegativeLiterals);

        // Propagate pure literals.
        partialAssignment.or(purePositiveLiterals);
        assignedVariables.or(purePositiveLiterals);
        assignedVariables.or(pureNegativeLiterals);

        var newClauses = new ArrayList<int[]>(clauses.size());

        nextClause:
        for (int clauseIndex = 0, s = clauses.size(); clauseIndex < s; clauseIndex++) {
          int[] clause = clauses.get(clauseIndex);
          int[] newClause = new int[clause.length];

          int i = 0;

          for (int literal : clause) {
            int variable = Math.abs(literal);

            if (!assignedVariables.get(variable)) {
              newClause[i] = literal;
              i++;
            } else if (partialAssignment.get(variable) == literal > 0) {
              continue nextClause;
            }
          }

          if (i == 0) {
            return null;
          } else if (i == clause.length) {
            // We did not change anything. Recycle old clause.
            newClauses.add(clause);
          } else {
            newClauses.add(Arrays.copyOf(newClause, i));
          }
        }

        clauses = newClauses;
      }

      if (clauses.isEmpty()) {
        return partialAssignment;
      }

      // We make the first clause true and propagate the information.
      int pickedLiteral = clauses.get(0)[0];
      clauses.add(new int[]{pickedLiteral});
      var model = dpll(
          clauses, (BitSet) partialAssignment.clone(), (BitSet) assignedVariables.clone());

      if (model != null) {
        assert model.get(Math.abs(pickedLiteral)) == pickedLiteral > 0;
        return model;
      }

      clauses.get(clauses.size() - 1)[0] = -pickedLiteral;
      return dpll(clauses, partialAssignment, assignedVariables);
    }

    @Override
    protected <V> List<HashSet<V>> computeMaximalModelsImpl(
        PropositionalFormula<V> normalisedFormula,
        List<HashSet<V>> maximalModelsCandidates) {

      throw new UnsupportedOperationException();
    }
  },

  JBDD {
    @Nullable
    @Override
    protected BitSet modelImpl(int[][] clauses) {
      var solver = new Solver.JbddSolver();
      solver.pushClauses(clauses);
      return solver.model();
    }

    @Override
    protected <V> List<HashSet<V>> computeMaximalModelsImpl(
        PropositionalFormula<V> normalisedFormula, List<HashSet<V>> maximalModels) {

      // Enumerate models using a sat solver.
      var conjunctiveNormalForm = new ConjunctiveNormalForm<>(normalisedFormula);
      var solver = new Solver.JbddSolver();
      solver.pushClauses(conjunctiveNormalForm.clauses.toArray(int[][]::new));
      maximalModels
          .forEach(x -> blockModelAndAllSubsets(solver, conjunctiveNormalForm, x));

      // single subset optimisation
      BitSet model;

      while ((model = solver.model()) != null) {
        // Prune Tsetin variables from model
        if (conjunctiveNormalForm.tsetinVariablesLowerBound <= model.length()) {
          model.clear(conjunctiveNormalForm.tsetinVariablesLowerBound, model.length());
        }

        // Map model to HashSet<V>.
        HashSet<V> mappedModel = model.stream()
            .mapToObj(conjunctiveNormalForm.variableMapping.inverse()::get)
            .collect(Collectors.toCollection(HashSet::new));

        assert normalisedFormula.evaluate(mappedModel);
        maximalModels.add(mappedModel);
        maximalModels = Collections3.maximalElements(maximalModels, (x, y) -> y.containsAll(x));

        // Block and continue.
        blockModelAndAllSubsets(solver, conjunctiveNormalForm, mappedModel);
      }

      return maximalModels;
    }

    private static <V> void blockModelAndAllSubsets(
        Solver.JbddSolver solver, ConjunctiveNormalForm<V> encoding, Set<V> model) {

      int[] blockingClause = IntStream.range(1, encoding.tsetinVariablesLowerBound)
          .filter(i -> !model.contains(requireNonNull(encoding.variableMapping.inverse().get(i))))
          .toArray();

      solver.pushClauses(new int[][]{blockingClause});
    }
  },

  KISSAT_EXTERNAL {

    private static final ProcessBuilder KISSAT_GLOBAL
        = new ProcessBuilder(List.of("kissat", "-q"));

    private static final ProcessBuilder KISSAT_LOCAL
        = new ProcessBuilder(List.of("./kissat", "-q"));

    private static final ProcessBuilder KISSAT_JUNIT
        = new ProcessBuilder(List.of("./thirdparty/kissat/build/kissat", "-q"));

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\s+");

    @Nullable
    @Override
    protected BitSet modelImpl(int[][] clauses) {
      int largestVariable = 0;

      for (int[] clause : clauses) {
        for (int literal : clause) {
          largestVariable = Math.max(largestVariable, Math.abs(literal));
        }
      }

      try {
        Process kissat = null;
        IOException firstIoException = null;

        // Check if kissat is in the current directory.
        try {
          kissat = KISSAT_LOCAL.start();
        } catch (IOException ioException) {
          firstIoException = ioException;
        }

        assert (kissat == null) == (firstIoException != null);

        if (kissat == null) {
          // Check if kissat is in PATH.
          try {
            kissat = KISSAT_GLOBAL.start();
          } catch (IOException ignore) {
            // Ignore exception
          }
        }

        if (kissat == null) {
          // Check if kissat is in the build directory.
          try {
            kissat = KISSAT_JUNIT.start();
          } catch (IOException ignore) {
            // Ignore exception
          }
        }

        if (kissat == null) {
          throw firstIoException;
        }

        try (var reader = new BufferedReader(new InputStreamReader(kissat.getInputStream()))) {
          
          // Restrict lifetime of writer.
          try (var writer = new BufferedOutputStream(kissat.getOutputStream())) {
            Solver.writeCnf(largestVariable, clauses, writer);
          }

          BitSet assignment = new BitSet(largestVariable + 1);
          String resultLine = reader.readLine();

          if (resultLine != null && resultLine.startsWith("s UNSATISFIABLE")) {
            return null;
          }

          if (resultLine != null && resultLine.startsWith("s SATISFIABLE")) {
            reader.lines().forEach(x -> {
              String[] split = SPLIT_PATTERN.split(x);
              assert "v".equals(split[0]);

              for (int i = 1; i < split.length; i++) {
                int literal = Integer.parseInt(split[i]);

                if (literal > 0) {
                  assignment.set(literal);
                }
              }
            });

            return assignment;
          }

          throw new IOException("Could not parse answer (%s) from kissat.".formatted(resultLine));
        }
      } catch (IOException ioException) {
        throw new UncheckedIOException(ioException);
      }
    }

    @Override
    protected <V> List<HashSet<V>> computeMaximalModelsImpl(
        PropositionalFormula<V> normalisedFormula,
        List<HashSet<V>> maximalModelsCandidates) {

      throw new UnsupportedOperationException();
    }
  };

  private static void writeCnf(
      final int largestVariable, int[][] clauses, BufferedOutputStream writer)
      throws IOException {

    // String representation cache.

    byte[] newLine = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
    byte[] whiteSpace = " ".getBytes(StandardCharsets.UTF_8);

    {
      byte[] header = "p cnf %d %d"
          .formatted(largestVariable, clauses.length)
          .getBytes(StandardCharsets.UTF_8);
      writer.write(header);
      writer.write(newLine);
    }

    {
      // We store '0' at largestVariable
      byte[][] literals = new byte[2 * largestVariable + 1][];

      for (int i = -largestVariable; i <= largestVariable; i++) {
        literals[i + largestVariable] = Integer.toString(i).getBytes(StandardCharsets.UTF_8);
      }

      for (int[] clause : clauses) {
        for (int literal : clause) {
          writer.write(literals[literal + largestVariable]);
          writer.write(whiteSpace);
        }

        writer.write(literals[largestVariable]);
        writer.write(newLine);
      }
    }

    writer.flush();
  }

  public static final Solver DEFAULT_MODELS = KISSAT_EXTERNAL;

  public static final Solver DEFAULT_MAXIMAL_MODELS = JBDD;

  public <V> Optional<Set<V>> model(PropositionalFormula<V> formula) {
    return Optional.ofNullable(modelNnfFormula(formula.nnf()));
  }

  public <V> Optional<Set<V>> model(List<Clause<V>> clauses) {
    int clausesSize = clauses.size();
    var intClauses = new int[clausesSize][];
    var numbering = new Numbering<V>(clausesSize);

    for (int j = 0; j < clausesSize; j++) {
      Clause<V> clause = clauses.get(j);

      int[] intClause = new int[clause.literals()];
      int i = 0;

      for (V positiveLiteral : clause.positiveLiterals) {
        intClause[i++] = numbering.lookup(positiveLiteral) + 1;
      }

      for (V negativeLiteral : clause.negativeLiterals) {
        intClause[i++] = -(numbering.lookup(negativeLiteral) + 1);
      }

      intClauses[j] = intClause;
    }

    var model = modelImpl(intClauses);

    if (model == null) {
      return Optional.empty();
    }

    var mappedModel = new HashSet<V>(model.cardinality());
    model.stream().forEach(i -> mappedModel.add(numbering.lookup(i - 1)));
    return Optional.of(mappedModel);
  }

  @Nullable
  protected abstract BitSet modelImpl(int[][] clauses);

  public record Clause<V>(List<? extends V> positiveLiterals, List<? extends V> negativeLiterals) {

    public Clause {
      positiveLiterals = List.copyOf(positiveLiterals);
      negativeLiterals = List.copyOf(negativeLiterals);
    }

    public static <V> Clause<V> assertTrue(V variable) {
      return new Clause<>(List.of(variable), List.of());
    }

    public static <V> Clause<V> assertFalse(V variable) {
      return new Clause<>(List.of(), List.of(variable));
    }

    public static <V> Clause<V> implication(List<V> antecedents, V consequent) {
      return new Clause<>(List.of(consequent), antecedents);
    }

    public static <V> Clause<V> implication(V antecedent, V consequent) {
      return implication(List.of(antecedent), consequent);
    }

    public static <V> Clause<V> atLeastOneIs(List<? extends V> variables, boolean value) {
      return value
          ? new Clause<>(variables, List.of())
          : new Clause<>(List.of(), variables);
    }

    public static <V> List<Clause<V>> atMostOne(List<? extends V> variables) {
      var clauses = new ArrayList<Clause<V>>(variables.size() * variables.size());

      for (int i = 0, s = variables.size(); i < s; i++) {
        for (int j = i + 1; j < s; j++) {
          clauses.add(new Clause<>(List.of(), List.of(variables.get(i), variables.get(j))));
        }
      }

      return clauses;
    }

    public static <V> List<Clause<V>> exactlyOne(List<? extends V> variables) {
      var clauses = new ArrayList<Clause<V>>(variables.size() * variables.size() + 1);
      clauses.add(atLeastOneIs(variables, true));
      clauses.addAll(atMostOne(variables));
      return clauses;
    }

    public int literals() {
      return positiveLiterals.size() + negativeLiterals.size();
    }

    public boolean evaluate(Collection<V> model) {
      if (!Collections.disjoint(positiveLiterals, model)) {
        return true;
      }

      return !model.containsAll(negativeLiterals);
    }
  }

  @Nullable
  protected <V> HashSet<V> modelNnfFormula(PropositionalFormula<V> nnfFormula) {

    // Replace variables that occur only with positive or negative polarity by a constant.
    {
      var polarities = nnfFormula.polarities();
      polarities.values().removeIf(polarity -> polarity == PropositionalFormula.Polarity.MIXED);

      if (!polarities.isEmpty()) {

        HashSet<V> model = modelNnfFormula(nnfFormula.substitute(variable -> {
          var polarity = polarities.get(variable);

          if (polarity == null) {
            return Variable.of(variable);
          }

          return switch (polarity) {
            case POSITIVE -> PropositionalFormula.<V>trueConstant();
            case NEGATIVE -> PropositionalFormula.<V>falseConstant();
            case MIXED -> throw new AssertionError("should not be reached.");
          };
        }));

        if (model != null) {
          polarities.forEach((variable, value) -> {
            if (value == PropositionalFormula.Polarity.POSITIVE) {
              model.add(variable);
            }
          });
        }

        return model;
      }
    }

    // Unit-propagation.
    if (nnfFormula instanceof Conjunction<V> conjunction) {
      HashMap<V, Boolean> units = new HashMap<>();

      for (var conjunct : conjunction.conjuncts()) {
        if (conjunct instanceof Variable<V> variable) {

          // Avoid unboxing.
          Boolean oldValue = units.put(variable.variable(), Boolean.TRUE);

          // Found a contradiction.
          if (Boolean.FALSE.equals(oldValue)) {
            return null;
          }
        }

        if (conjunct instanceof Negation<V> negation
            && negation.operand() instanceof Variable<V> variable) {

          // Avoid unboxing.
          Boolean oldValue = units.put(variable.variable(), Boolean.FALSE);

          // Found a contradiction.
          if (Boolean.TRUE.equals(oldValue)) {
            return null;
          }
        }
      }

      if (!units.isEmpty()) {
        HashSet<V> model = modelNnfFormula(nnfFormula.substitute(variable -> {
          Boolean value = units.get(variable);

          if (value == null) {
            return Variable.of(variable);
          }

          return value
              ? PropositionalFormula.<V>trueConstant()
              : PropositionalFormula.<V>falseConstant();
        }));

        if (model != null) {
          units.forEach((variable, value) -> {
            if (value) {
              model.add(variable);
            }
          });
        }

        return model;
      }
    }

    // Split checking.
    if (nnfFormula instanceof Disjunction<V> disjunction) {
      for (PropositionalFormula<V> disjunct : disjunction.disjuncts()) {
        var model = modelNnfFormula(disjunct);

        if (model != null) {
          return model;
        }
      }

      return null;
    }

    ConjunctiveNormalForm<V> cnf = new ConjunctiveNormalForm<>(nnfFormula);
    @Nullable
    BitSet model = modelImpl(cnf.clauses.toArray(int[][]::new));

    if (model == null) {
      return null;
    }

    var mappedModel = model.stream()
        .filter(cnf.variableMapping::containsValue) // skip Tsetin variables
        .mapToObj(i -> cnf.variableMapping.inverse().get(i))
        .collect(Collectors.toCollection(HashSet::new));

    assert nnfFormula.evaluate(mappedModel);
    return mappedModel;
  }

  public final <V> List<Set<V>> maximalModels(PropositionalFormula<V> formula, Set<V> upperBound) {
    return List.copyOf(maximalModelsNnfFormula(formula.nnf(), upperBound));
  }

  private <V> List<HashSet<V>> maximalModelsNnfFormula(
      PropositionalFormula<V> nnfFormula, Set<V> upperBound) {

    PropositionalFormula<V> normalisedFormula = nnfFormula.substitute(
        variable -> upperBound.contains(variable)
            ? Variable.of(variable)
            : PropositionalFormula.falseConstant());

    // Preprocessing to reduce enumeration of models using the SAT solver.

    // 1. Check trivial cases.
    {
      if (normalisedFormula.evaluate(upperBound)) {
        return List.of(new HashSet<>(upperBound));
      }

      if (upperBound.size() == 1) {
        return normalisedFormula.evaluate(Set.of()) ? List.of(new HashSet<>()) : List.of();
      }
    }

    // 2. Compute lower-bound and replace positive variables by true.
    {
      Set<V> lowerBound = new HashSet<>(upperBound);

      normalisedFormula.polarities().forEach((variable, polarity) -> {
        if (polarity != PropositionalFormula.Polarity.POSITIVE) {
          lowerBound.remove(variable);
        }
      });

      if (!lowerBound.isEmpty()) {
        PropositionalFormula<V> restrictedFormula = normalisedFormula.substitute(
            variable -> lowerBound.contains(variable)
                ? PropositionalFormula.trueConstant()
                : Variable.of(variable));

        Set<V> newUpperBound = new HashSet<>(upperBound.size());

        for (var upperBoundElement : upperBound) {
          if (!lowerBound.contains(upperBoundElement)) {
            newUpperBound.add(upperBoundElement);
          }
        }

        List<HashSet<V>> restrictedMaximalModels
            = maximalModelsNnfFormula(restrictedFormula, newUpperBound);
        restrictedMaximalModels.forEach(model -> model.addAll(lowerBound));
        return restrictedMaximalModels;
      }
    }

    // 3. If the formula is a disjunction of negations, then the maximal models can be
    //    directly be computed.
    {
      if (upperBound.equals(nnfFormula.variables())
          && normalisedFormula instanceof Disjunction<V> disjunction) {

        boolean allDisjunctsAreNegatedVariables = true;
        var disjuncts = disjunction.disjuncts();

        for (int i = 0, s = disjuncts.size(); i < s; i++) {
          var disjunct = disjuncts.get(i);

          if (!(disjunct instanceof Negation)
              || !(((Negation<V>) disjunct).operand()
              instanceof Variable)) {
            allDisjunctsAreNegatedVariables = false;
            break;
          }
        }

        if (allDisjunctsAreNegatedVariables) {
          List<HashSet<V>> maximalModels = new ArrayList<>();

          for (V upperBoundElement : upperBound) {
            var model = new HashSet<>(upperBound);
            model.remove(upperBoundElement);
            assert normalisedFormula.evaluate(model);
            maximalModels.add(model);
          }

          return maximalModels;
        }
      }
    }

    // 4. If the formula is in DNF, extract additional information to speed up search.
    List<HashSet<V>> maximalModelsCandidates = new ArrayList<>();

    for (var disjunct : PropositionalFormula.disjuncts(normalisedFormula)) {
      var potentialModel = new HashSet<>(upperBound);

      for (var conjunct : PropositionalFormula.conjuncts(disjunct)) {
        if (conjunct instanceof Negation<V> negation) {
          potentialModel.remove(((Variable<V>) negation.operand()).variable());
        }
      }

      if (normalisedFormula.evaluate(potentialModel)) {
        assert !potentialModel.equals(upperBound);
        assert upperBound.containsAll(potentialModel);
        maximalModelsCandidates.add(potentialModel);
      }
    }

    return List.copyOf(computeMaximalModelsImpl(
        normalisedFormula,
        Collections3.maximalElements(maximalModelsCandidates, (x, y) -> y.containsAll(x))));
  }

  protected abstract <V> List<HashSet<V>> computeMaximalModelsImpl(
      PropositionalFormula<V> normalisedFormula, List<HashSet<V>> maximalModelsCandidates);

  private static class JbddSolver {

    private final Bdd bdd;
    private int clauseConjunction;

    private JbddSolver() {
      var configuration = ImmutableBddConfiguration.builder()
          .logStatisticsOnShutdown(false)
          .useGlobalComposeCache(false)
          .integrityDuplicatesMaximalSize(50)
          .cacheBinaryDivider(4)
          .cacheTernaryDivider(4)
          .growthFactor(4)
          .build();

      // Do not use buildBddIterative, since 'support(...)' is broken.
      bdd = BddFactory.buildBddRecursive(10_000, configuration);
      clauseConjunction = bdd.trueNode();
    }

    private void pushClauses(int[][] clauses) {
      int max = 0;

      for (int[] clause : clauses) {
        for (int literal : clause) {
          max = Math.max(Math.abs(literal), max);
        }
      }

      int requiredVariables = Math.max(max - bdd.numberOfVariables(), 0);

      if (requiredVariables > 0) {
        bdd.createVariables(requiredVariables);
      }

      int conjunction = bdd.trueNode();

      for (int[] clause : clauses) {
        int disjunction = bdd.falseNode();

        for (int literal : clause) {
          if (literal > 0) {
            disjunction = bdd.updateWith(
                bdd.or(disjunction, bdd.variableNode(literal - 1)),
                disjunction);
          } else {
            disjunction = bdd.updateWith(
                bdd.or(disjunction, bdd.not(bdd.variableNode((-literal) - 1))),
                disjunction);
          }
        }

        conjunction = bdd.consume(bdd.and(conjunction, disjunction), conjunction, disjunction);
      }

      clauseConjunction = bdd.consume(
          bdd.and(clauseConjunction, conjunction),
          conjunction,
          clauseConjunction);
    }

    @Nullable
    private BitSet model() {
      int conjunction = clauseConjunction;

      if (conjunction == bdd.falseNode()) {
        return null;
      }

      BitSet model = new BitSet();
      int currentNode = conjunction;

      while (currentNode != bdd.trueNode()) {
        int highNode = bdd.high(currentNode);

        if (highNode == bdd.falseNode()) {
          currentNode = bdd.low(currentNode);
        } else {
          model.set(bdd.variable(currentNode) + 1);
          currentNode = highNode;
        }
      }

      return model;
    }
  }

}
