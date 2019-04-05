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

package owl.translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static owl.translations.LTL2DAFunction.Constructions.BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_SAFETY;
import static owl.translations.LTL2DAFunction.Constructions.GENERALIZED_BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.SAFETY;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.BASE;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.FGGF;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.LIBEROUTER;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.PARAMETRISED_HARDNESS;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.SIZE;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.SIZE_FGGF;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.SYMMETRIC;

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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.output.HoaPrinter;
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
import owl.run.DefaultEnvironment;
import owl.run.Environment;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;
import owl.translations.ltl2nba.SymmetricNBAConstruction;

@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnnecessaryFullyQualifiedName"})
class TranslationAutomatonSummaryTest {

  private static final String BASE_PATH = "data/formulas";
  private static final List<String> COMMON_ALPHABET = List.of("a", "b", "c", "d", "e", "f", "g",
    "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z");
  private static final List<Translator> TRANSLATORS;

  static {
    TRANSLATORS = List.of(
      new Translator("safety", LTL2DAFunction::safety),
      new Translator("safety.nondeterministic", LTL2NAFunction::safety),

      new Translator("coSafety", LTL2DAFunction::coSafety),
      new Translator("coSafety.nondeterministic", LTL2NAFunction::coSafety),

      new Translator("fgSafety", LTL2DAFunction::fgSafety),
      new Translator("fgSafety.nondeterministic", LTL2NAFunction::fgSafety),

      new Translator("gfCoSafety",
      x -> y -> LTL2DAFunction.gfCoSafety(x, y, false)),
      new Translator("gfCoSafety.generalized",
      x -> y -> LTL2DAFunction.gfCoSafety(x, y, true)),
      new Translator("gfCoSafety.nondeterministic",
      x -> y -> LTL2NAFunction.gfCoSafety(x, y, false)),
      new Translator("gfCoSafety.nondeterministic.generalized",
      x -> y -> LTL2NAFunction.gfCoSafety(x, y, true)),

      new Translator("fSafety", LTL2DAFunction::fSafety),
      new Translator("gCoSafety", LTL2DAFunction::gCoSafety),

      new Translator("ldba.asymmetric", environment ->
        AsymmetricLDBAConstruction.of(environment, BuchiAcceptance.class)
          .andThen(AnnotatedLDBA::copyAsMutable)),
      new Translator("ldgba.asymmetric", environment ->
        AsymmetricLDBAConstruction.of(environment, GeneralizedBuchiAcceptance.class)
          .andThen(AnnotatedLDBA::copyAsMutable)),

      new Translator("ldba.symmetric", environment ->
        SymmetricLDBAConstruction.of(environment, BuchiAcceptance.class)
          .andThen(AnnotatedLDBA::copyAsMutable)),
      new Translator("ldgba.symmetric", environment ->
        SymmetricLDBAConstruction.of(environment, GeneralizedBuchiAcceptance.class)
          .andThen(AnnotatedLDBA::copyAsMutable)),

      new Translator("dpa.asymmetric", environment ->
        new LTL2DPAFunction(environment,
          EnumSet.of(COMPRESS_COLOURS, OPTIMISE_INITIAL_STATE)),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),

      new Translator("dpa.symmetric", environment ->
        new LTL2DPAFunction(environment,
          EnumSet.of(SYMMETRIC, COMPRESS_COLOURS, OPTIMISE_INITIAL_STATE)),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),

      new Translator("dra.symmetric", environment ->
        SymmetricDRAConstruction.of(environment, RabinAcceptance.class, true),
        EnumSet.complementOf(EnumSet.of(LIBEROUTER, PARAMETRISED_HARDNESS))),
      new Translator("dgra.symmetric", environment ->
        SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, true),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),

      new Translator("nba.symmetric", environment ->
        SymmetricNBAConstruction.of(environment, BuchiAcceptance.class),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),
      new Translator("ngba.symmetric", environment ->
        SymmetricNBAConstruction.of(environment, GeneralizedBuchiAcceptance.class),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),

      new Translator("delag", DelagBuilder::new,
        EnumSet.complementOf(EnumSet.of(BASE, SIZE))),
      new Translator("ltl2da", environment -> new LTL2DAFunction(environment, true,
        EnumSet.of(SAFETY, CO_SAFETY, BUCHI, GENERALIZED_BUCHI, CO_BUCHI)),
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF)),
      new Translator("ltl2na",
        (Function<Environment, Function<LabelledFormula, Automaton<?, ?>>>) LTL2NAFunction::new,
        EnumSet.of(LIBEROUTER, FGGF, SIZE_FGGF))
    );
  }

  private static void addNormalized(Set<LabelledFormula> testCases, Formula... formulas) {
    outer:
    for (Formula formula : formulas) {
      // Find literals that only occur negated.
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
      int variables = (int) Arrays.stream(shiftedFormula.mapping).filter(x -> x != -1).count();
      var restrictedAlphabet = COMMON_ALPHABET.subList(0, variables);
      var labelledFormula = LabelledFormula.of(shiftedFormula.formula, restrictedAlphabet);

      if (testCases.contains(labelledFormula)) {
        continue;
      }

      for (var testCase : testCases) {
        if (FormulaIsomorphism.compute(testCase.formula(), labelledFormula.formula()) != null) {
          continue outer;
        }
      }

      // Write and read formula to ensure that during test the literal to index mapping is the same.
      LabelledFormula oldLabelledFormula;
      LabelledFormula newLabelledFormula = labelledFormula;

      do {
        oldLabelledFormula = newLabelledFormula;
        newLabelledFormula = LabelledFormula.of(
          LtlParser.syntax(oldLabelledFormula.toString()), restrictedAlphabet);
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
      var translatorFunction = translator.constructor.apply(DefaultEnvironment.standard());

      try (Reader sizesFile = Files.newBufferedReader(translator.referenceFile())) {
        var testCases = Arrays
          .stream(gson.fromJson(sizesFile, TestCase[].class))
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

    var gson = new GsonBuilder().setPrettyPrinting().create();
    var translatorFunction = translator.constructor.apply(DefaultEnvironment.standard());
    var testCases = formulaSet.stream().map(x -> TestCase.of(x, translatorFunction)).toArray();

    try (BufferedWriter writer = Files.newBufferedWriter(translator.referenceFile())) {
      gson.toJson(testCases, writer);
    } catch (IOException exception) {
      fail(exception);
    }
  }

  enum FormulaSet {
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

    private final String name;

    FormulaSet(String name) {
      this.name = name;
    }

    Path file() {
      return Paths.get(String.format("%s/%s.ltl", BASE_PATH, name));
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
        var labelledFormula = LtlParser.parse(formula);

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
    final String acceptanceName;
    final int acceptanceSets;
    final boolean complete;
    final boolean deterministic;

    private AutomatonSummary(int size, int initialStatesSize, OmegaAcceptance acceptance,
      boolean deterministic, boolean complete) {
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
        var automaton = supplier.get();
        return new AutomatonSummary(automaton.size(),
          automaton.initialStates().size(),
          automaton.acceptance(),
          automaton.is(Automaton.Property.DETERMINISTIC),
          automaton.is(Automaton.Property.COMPLETE));
      } catch (IllegalArgumentException | UnsupportedOperationException ex) {
        return null;
      }
    }

    void test(Automaton<?, ?> automaton) {
      assertEquals(size, automaton.size(),
        () -> String.format("Expected %d states, got %d.\n%s",
        size, automaton.size(), HoaPrinter.toString(automaton)));
      assertEquals(acceptanceSets, automaton.acceptance().acceptanceSets(),
        () -> String.format("Expected %d acceptance sets, got %d.\n%s",
        acceptanceSets, automaton.acceptance().acceptanceSets(), HoaPrinter.toString(automaton)));
      assertEquals(acceptanceName, automaton.acceptance().getClass().getSimpleName());
      assertEquals(deterministic, automaton.is(Automaton.Property.DETERMINISTIC));
      assertEquals(complete, automaton.is(Automaton.Property.COMPLETE));
    }
  }
}
