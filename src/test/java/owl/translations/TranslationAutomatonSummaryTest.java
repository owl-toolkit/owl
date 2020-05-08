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

package owl.translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.BASE;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.FGGF;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.LIBEROUTER;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.PARAMETRISED_HARDNESS;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.SIZE;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.SIZE_FGGF;

import com.google.common.collect.Collections2;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.Automaton;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.ltl.util.FormulaIsomorphism;
import owl.ltl.visitors.Converter;
import owl.run.Environment;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.canonical.NonDeterministicConstructionsPortfolio;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.modules.LTL2DGRAModule;
import owl.translations.modules.LTL2DPAModule;
import owl.translations.modules.LTL2DRAModule;
import owl.translations.modules.LTL2LDBAModule;
import owl.translations.modules.LTL2LDGBAModule;
import owl.translations.modules.LTL2NBAModule;
import owl.translations.modules.LTL2NGBAModule;

@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnnecessaryFullyQualifiedName"})
public class TranslationAutomatonSummaryTest {

  private static final String BASE_PATH = "data/formulas";

  static final List<String> COMMON_ALPHABET = List.of("a", "b", "c", "d", "e", "f", "g",
    "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z");

  static final List<Translator> TRANSLATORS = List.of(
      new Translator("safety",
        DeterministicConstructionsPortfolio::safety),
      new Translator("safety.nondeterministic",
        NonDeterministicConstructionsPortfolio::safety),

      new Translator("coSafety",
        DeterministicConstructionsPortfolio::coSafety),
      new Translator("coSafety.nondeterministic",
        NonDeterministicConstructionsPortfolio::coSafety),

      new Translator("fgSafety",
      x -> y -> DeterministicConstructionsPortfolio.fgSafety(x, y, false)),
      new Translator("fgSafety.generalized",
      x -> y -> DeterministicConstructionsPortfolio.fgSafety(x, y, true)),
      new Translator("fgSafety.nondeterministic",
        NonDeterministicConstructionsPortfolio::fgSafety),

      new Translator("gfCoSafety",
      x -> y -> DeterministicConstructionsPortfolio.gfCoSafety(x, y, false)),
      new Translator("gfCoSafety.generalized",
      x -> y -> DeterministicConstructionsPortfolio.gfCoSafety(x, y, true)),
      new Translator("gfCoSafety.nondeterministic",
      x -> y -> NonDeterministicConstructionsPortfolio.gfCoSafety(x, y, false)),
      new Translator("gfCoSafety.nondeterministic.generalized",
      x -> y -> NonDeterministicConstructionsPortfolio.gfCoSafety(x, y, true)),

      new Translator("coSafetySafety", DeterministicConstructionsPortfolio::coSafetySafety),
      new Translator("safetyCoSafety", DeterministicConstructionsPortfolio::safetyCoSafety),

      new Translator("ldba.asymmetric", environment ->
        LTL2LDBAModule.translation(environment, false, false)),
      new Translator("ldba.asymmetric.portfolio", environment ->
        LTL2LDBAModule.translation(environment, false, true)),
      new Translator("ldgba.asymmetric", environment ->
        LTL2LDGBAModule.translation(environment, false, false)),
      new Translator("ldgba.asymmetric.portfolio", environment ->
        LTL2LDGBAModule.translation(environment, false, true)),

      new Translator("ldba.symmetric", environment ->
        LTL2LDBAModule.translation(environment, true, false)),
      new Translator("ldba.symmetric.portfolio", environment ->
        LTL2LDBAModule.translation(environment, true, true)),
      new Translator("ldgba.symmetric", environment ->
        LTL2LDGBAModule.translation(environment, true, false)),
      new Translator("ldgba.symmetric.portfolio", environment ->
        LTL2LDGBAModule.translation(environment, true, true)),

      new Translator("dpa.asymmetric", environment ->
        LTL2DPAModule.translation(environment, false, false, false),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),
      new Translator("dpa.asymmetric.portfolio", environment ->
        LTL2DPAModule.translation(environment, false, false, true),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),

      new Translator("dpa.symmetric", environment ->
        LTL2DPAModule.translation(environment, true, false, false),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),
      new Translator("dpa.symmetric.portfolio", environment ->
        LTL2DPAModule.translation(environment, true, false, true),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),

      // TODO: Investigate one minute difference
      new Translator("dra.symmetric", environment ->
        LTL2DRAModule.translation(environment, true, false, null)),
      new Translator("dra.symmetric.portfolio", environment ->
        LTL2DRAModule.translation(environment, true, true, null)),
      new Translator("dra.symmetric.optimizations", environment -> x ->
        AcceptanceOptimizations.optimize(
          SymmetricDRAConstruction.of(environment, RabinAcceptance.class, true)
            .apply(x))),

      new Translator("dgra.symmetric", environment ->
        LTL2DGRAModule.translation(environment, true, false, null)),
      new Translator("dgra.symmetric.portfolio", environment ->
        LTL2DGRAModule.translation(environment, true, true, null)),
      new Translator("dgra.symmetric.optimizations", environment -> x ->
        AcceptanceOptimizations.optimize(
          SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, true)
            .apply(x))),

      new Translator("nba.symmetric", environment ->
        LTL2NBAModule.translation(environment, false)),
      new Translator("nba.symmetric.portfolio", environment ->
        LTL2NBAModule.translation(environment, true)),
      new Translator("ngba.symmetric", environment ->
        LTL2NGBAModule.translation(environment, false)),
      new Translator("ngba.symmetric.portfolio", environment ->
        LTL2NGBAModule.translation(environment, true)),

      new Translator("delag",
        DelagBuilder::new,
        EnumSet.complementOf(EnumSet.of(BASE, SIZE))),
      new Translator("ltl2da",
        LTL2DAFunction::new,
        EnumSet.of(LIBEROUTER)),
      new Translator("ltl2na",
        LTL2NAFunction::new,
        EnumSet.of(LIBEROUTER))
    );

  private static boolean containsIsomorphic(Collection<Formula> formulas, Formula formula) {
    for (Formula existingFormula : formulas) {
      if (existingFormula.equals(formula)) {
        return true;
      }

      if (FormulaIsomorphism.compute(existingFormula, formula) != null) {
        return true;
      }
    }

    return false;
  }

  private static void addNormalized(Set<LabelledFormula> testCases, Formula... formulas) {
    for (Formula formula : formulas) {
      BitSet literalsToFlip = new BitSet();
      Set<Literal> literals = formula.subformulas(Literal.class);
      literals.stream()
        .filter(x -> x.isNegated() && !literals.contains(x.not()))
        .forEach(x -> literalsToFlip.set(x.getAtom()));

      // Replace these literals by positive form.
      var literalNormalizedFormula = formula.accept(new Converter(SyntacticFragment.ALL) {
        @Override
        public Formula visit(Literal literal) {
          if (literalsToFlip.get(literal.getAtom())) {
            assert literal.isNegated();
            return literal.not();
          }

          return literal;
        }
      });

      // Close literal gaps.
      var shiftedFormula = LiteralMapper.shiftLiterals(literalNormalizedFormula);
      var labelledFormula = LabelledFormula.of(shiftedFormula.formula, COMMON_ALPHABET);

      if (containsIsomorphic(
        Collections2.transform(testCases, LabelledFormula::formula), labelledFormula.formula())) {
        continue;
      }

      // Write and read formula to ensure that during test the literal to index mapping is the same.
      LabelledFormula oldLabelledFormula;
      LabelledFormula newLabelledFormula = labelledFormula;

      do {
        oldLabelledFormula = newLabelledFormula;
        newLabelledFormula = LabelledFormula.of(
          LtlParser.syntax(oldLabelledFormula.toString()), COMMON_ALPHABET);
      } while (!newLabelledFormula.equals(oldLabelledFormula));

      testCases.add(newLabelledFormula);
    }
  }

  private static List<Translator> translatorProvider() {
    return TRANSLATORS;
  }

  @Tag("size-regression-test")
  @TestFactory
  List<DynamicContainer> test() {
    var containers = new ArrayList<DynamicContainer>();
    var gson = new Gson();

    for (Translator translator : TRANSLATORS) {
      var translatorFunction = translator.constructor.apply(Environment.standard());

      try (Reader sizesFile = Files.newBufferedReader(translator.referenceFile())) {
        var testCases = Arrays.stream(gson.fromJson(sizesFile, TestCase[].class))
          .map(testCase -> testCase.test(translatorFunction));
        containers.add(DynamicContainer.dynamicContainer(translator.name, testCases));
      } catch (IOException exception) {
        fail(exception);
      }
    }

    return containers;
  }

  @Tag("size-regression-train")
  @ParameterizedTest
  @MethodSource("translatorProvider")
  void train(Translator translator) {
    var formulaSet = new TreeSet<>(Comparator.comparing(LabelledFormula::formula));

    for (FormulaSet set : translator.selectedSets) {
      try (BufferedReader reader = Files.newBufferedReader(set.file())) {
        reader.lines().forEach(line -> {
          var formulaString = line.trim();

          if (formulaString.isEmpty()) {
            return;
          }

          var formula = LtlParser.syntax(formulaString).nnf();
          var simplifiedFormula
            = SimplifierFactory.apply(formula, Mode.SYNTACTIC_FIXPOINT);
          var simplifiedFormulaNegated
            = SimplifierFactory.apply(formula.not(), Mode.SYNTACTIC_FIXPOINT);

          addNormalized(formulaSet, simplifiedFormula, simplifiedFormulaNegated);
        });
      } catch (IOException exception) {
        fail(exception);
      }
    }

    var translatorFunction = translator.constructor.apply(Environment.standard());
    var testCases = formulaSet.stream().map(x -> TestCase.of(x, translatorFunction)).toArray();

    try (BufferedWriter writer = Files.newBufferedWriter(translator.referenceFile())) {
      var gson = new GsonBuilder().setPrettyPrinting().create();
      gson.toJson(testCases, writer);
    } catch (IOException exception) {
      fail(exception);
    }
  }

  public enum FormulaSet {
    BASE("base"),
    CHECK("check"),
    FGGF("fggf"),
    FGX("fgx"),
    REGRESSIONS("regressions"),
    SIZE_FGGF("size-fggf"),
    SIZE("size"),

    // Literature Patterns
    DWYER("literature/DwyerAC98"),
    ETESSAMI("literature/EtessamiH00"),
    LIBEROUTER("literature/Liberouter04"),
    PARAMETRISED("literature/Parametrised"),
    PARAMETRISED_HARDNESS("literature/Parametrised-Hardness"),
    PELANEK("literature/Pelanek07"),
    SICKERT("literature/SickertEJK16"),
    SOMENZI("literature/SomenziB00");

    final String path;

    FormulaSet(String path) {
      this.path = path;
    }

    Path file() {
      return Paths.get(String.format("%s/%s.ltl", BASE_PATH, path));
    }

    @SuppressWarnings("PMD.LooseCoupling")
    public LinkedHashSet<LabelledFormula> loadAndDeduplicateFormulaSet() throws IOException {
      var formulaSet = new LinkedHashSet<LabelledFormula>();

      try (BufferedReader reader = Files.newBufferedReader(file())) {
        reader.lines().forEach(line -> {
          var formulaString = line.trim();

          if (formulaString.isEmpty()) {
            return;
          }

          var formula = SimplifierFactory.apply(
            LtlParser.syntax(formulaString).nnf(), Mode.SYNTACTIC_FIXPOINT);
          var negatedFormula = formula.not();
          var invertedFormula = negatedFormula.accept(new Converter(SyntacticFragment.NNF) {
            @Override
            public Formula visit(Literal literal) {
              return literal.not();
            }
          });

          var transformedSet = Collections2.transform(formulaSet, LabelledFormula::formula);

          if (!containsIsomorphic(transformedSet, formula)
            && !containsIsomorphic(transformedSet, negatedFormula)
            && !containsIsomorphic(transformedSet, invertedFormula)) {
            addNormalized(formulaSet, formula);
          }
        });
      }

      return formulaSet;
    }
  }

  static class Translator {
    final String name;
    final Function<Environment, ? extends Function<LabelledFormula, ? extends Automaton<?, ?>>>
      constructor;
    private final Set<FormulaSet> selectedSets;

    Translator(String name,
      BiFunction<Environment, LabelledFormula, ? extends Automaton<?, ?>> constructor) {
      this(name, x -> y -> constructor.apply(x, y));
    }

    Translator(String name,
      Function<Environment, ? extends Function<LabelledFormula, ? extends Automaton<?, ?>>>
        constructor) {
      this(name, constructor, Set.of());
    }

    Translator(String name,
      Function<Environment, ? extends Function<LabelledFormula, ? extends Automaton<?, ?>>>
        constructor,
      Collection<FormulaSet> blacklistedSets) {
      this.name = name;
      this.constructor = constructor;
      this.selectedSets = EnumSet.allOf(FormulaSet.class);
      this.selectedSets.remove(PARAMETRISED_HARDNESS);
      this.selectedSets.removeAll(blacklistedSets);
    }

    Path referenceFile() {
      return Paths.get(String.format("%s/sizes/%s.json", BASE_PATH, name));
    }

    @Override
    public String toString() {
      return name;
    }
  }

  static class TestCase {
    final String formula;
    @Nullable
    final AutomatonSummary properties;

    private TestCase(String formula, @Nullable AutomatonSummary properties) {
      this.formula = formula;
      this.properties = properties;
    }

    static TestCase of(LabelledFormula formula,
      Function<LabelledFormula, ? extends Automaton<?, ?>> translation) {
      var properties = AutomatonSummary.of(() -> translation.apply(formula));
      return new TestCase(formula.toString(), properties);
    }

    DynamicTest test(Function<LabelledFormula, ? extends Automaton<?, ?>> translation) {
      return DynamicTest.dynamicTest(formula, () -> {
        var labelledFormula = LtlParser.parse(formula, COMMON_ALPHABET);

        try {
          var automaton = translation.apply(labelledFormula);
          assertNotNull(properties);
          properties.test(automaton);
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
          assertNull(properties, ex.getMessage());
        }
      });
    }
  }

  static class AutomatonSummary {
    final int size;
    final int initialStatesSize;
    final transient OmegaAcceptance acceptance;
    final String acceptanceName;
    final int acceptanceSets;
    final boolean complete;
    final boolean deterministic;

    AutomatonSummary(int size, int initialStatesSize, OmegaAcceptance acceptance,
      boolean deterministic, boolean complete) {
      this.acceptance = acceptance;
      this.acceptanceSets = acceptance.acceptanceSets();
      this.acceptanceName = acceptance.getClass().getSimpleName();
      this.complete = complete;
      this.deterministic = deterministic;
      this.initialStatesSize = initialStatesSize;
      this.size = size;
    }

    @Nullable
    static AutomatonSummary of(Supplier<Automaton<?, ?>> supplier) {
      try {
        var automaton = Objects.requireNonNull(supplier.get());
        return new AutomatonSummary(automaton.size(),
          automaton.initialStates().size(),
          automaton.acceptance(),
          automaton.is(Automaton.Property.DETERMINISTIC),
          automaton.is(Automaton.Property.COMPLETE));
      } catch (IllegalArgumentException | UnsupportedOperationException | CompletionException ex) {
        return null;
      }
    }

    void test(Automaton<?, ?> automaton) {
      assertEquals(size, automaton.size(),
        () -> String.format("Expected %d states, got %d.\n%s",
        size, automaton.size(), HoaWriter.toString(automaton)));
      assertEquals(initialStatesSize, automaton.initialStates().size(),
        () -> String.format("Expected %d intial states, got %d.\n%s",
        initialStatesSize, automaton.initialStates().size(), HoaWriter.toString(automaton)));
      assertEquals(acceptanceSets, automaton.acceptance().acceptanceSets(),
        () -> String.format("Expected %d acceptance sets, got %d.\n%s",
        acceptanceSets, automaton.acceptance().acceptanceSets(), HoaWriter.toString(automaton)));
      assertEquals(acceptanceName, automaton.acceptance().getClass().getSimpleName());
      assertEquals(deterministic, automaton.is(Automaton.Property.DETERMINISTIC));
      assertEquals(complete, automaton.is(Automaton.Property.COMPLETE));
    }
  }
}
