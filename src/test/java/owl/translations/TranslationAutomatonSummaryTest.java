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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static owl.translations.LTL2DAFunction.Constructions.BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_BUCHI;
import static owl.translations.LTL2DAFunction.Constructions.CO_SAFETY;
import static owl.translations.LTL2DAFunction.Constructions.SAFETY;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.EAGER_UNFOLD;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.FORCE_JUMPS;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS;

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
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrinter;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.run.DefaultEnvironment;
import owl.run.Environment;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnnecessaryFullyQualifiedName"})
class TranslationAutomatonSummaryTest {

  private static final String BASE_PATH = "data/formulas";
  private static final List<String> COMMON_ALPHABET = List.of("a", "b", "c", "d", "e", "f", "g",
    "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z");
  private static final List<Translator> TRANSLATORS;

  static {
    var dpaSymmetricAll = EnumSet.of(COMPRESS_COLOURS, OPTIMISE_INITIAL_STATE,
      Configuration.OPTIMISED_STATE_STRUCTURE, EXISTS_SAFETY_CORE);
    var dpaAsymmetricAll = EnumSet.of(GUESS_F, COMPRESS_COLOURS, OPTIMISE_INITIAL_STATE,
      Configuration.OPTIMISED_STATE_STRUCTURE, EXISTS_SAFETY_CORE);
    var ldbaAll = EnumSet.of(EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS);

    TRANSLATORS = List.of(
      new Translator("safety", LTL2DAFunction::safety),
      new Translator("safety.nondeterministic", LTL2NAFunction::safety),

      new Translator("coSafety", LTL2DAFunction::coSafety),
      new Translator("coSafety.nondeterministic", LTL2NAFunction::coSafety),

      new Translator("fgSafety", LTL2DAFunction::fgSafety),
      new Translator("fgSafety.nondeterministic", LTL2NAFunction::fgSafety),

      new Translator("gfCoSafety", LTL2DAFunction::gfCoSafety),
      new Translator("gfCoSafety.nondeterministic", LTL2NAFunction::gfCoSafety),

      new Translator("fSafety", LTL2DAFunction::fSafety),
      new Translator("gCoSafety", LTL2DAFunction::gCoSafety),

      new Translator("ltl2da", environment -> new LTL2DAFunction(environment, true,
        EnumSet.of(SAFETY, CO_SAFETY, BUCHI, CO_BUCHI))),
      new Translator("ltl2na", environment -> new LTL2NAFunction(environment,
        EnumSet.of(LTL2NAFunction.Constructions.SAFETY, LTL2NAFunction.Constructions.CO_SAFETY,
          LTL2NAFunction.Constructions.BUCHI))),
      
      new Translator("ldba.asymmetric", environment -> LTL2LDBAFunction
        .createDegeneralizedBreakpointLDBABuilder(environment, ldbaAll)),
      new Translator("ldba.symmetric", environment -> LTL2LDBAFunction
        .createDegeneralizedBreakpointFreeLDBABuilder(environment, ldbaAll),
        EnumSet.of(FormulaSet.BASE, FormulaSet.SIZE)),
      new Translator("ldgba.asymmetric", environment -> LTL2LDBAFunction
        .createGeneralizedBreakpointLDBABuilder(environment, ldbaAll)),
      new Translator("ldgba.symmetric", environment -> LTL2LDBAFunction
        .createGeneralizedBreakpointFreeLDBABuilder(environment, ldbaAll)),

      new Translator("dpa.asymmetric", environment ->
        new LTL2DPAFunction(environment, dpaAsymmetricAll),
        EnumSet.of(FormulaSet.BASE, FormulaSet.BEEM, FormulaSet.CHECK,
          FormulaSet.REGRESSIONS, FormulaSet.SIZE)),
      new Translator("dpa.symmetric", environment ->
        new LTL2DPAFunction(environment, dpaSymmetricAll),
        EnumSet.of(FormulaSet.BASE, FormulaSet.SIZE)),

      new Translator("delag", environment -> new DelagBuilder(environment, false),
        EnumSet.of(FormulaSet.BASE, FormulaSet.SIZE))
    );
  }

  private static void addNormalized(Set<LabelledFormula> testCases, Formula... formulas) {
    for (Formula formula : formulas) {
      var shiftedFormula = LiteralMapper.shiftLiterals(formula);
      int variables = (int) Arrays.stream(shiftedFormula.mapping).filter(x -> x != -1).count();
      var restrictedAlphabet = COMMON_ALPHABET.subList(0, variables);
      testCases.add(LabelledFormula.of(shiftedFormula.formula, restrictedAlphabet));
    }
  }

  private static List<Translator> translatorProvider() {
    return TRANSLATORS;
  }

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

  @Disabled
  @ParameterizedTest
  @MethodSource("translatorProvider")
  void train(Translator translator) {
    var formulaSet = new LinkedHashSet<LabelledFormula>();

    for (FormulaSet set : translator.selectedSets) {
      try (BufferedReader reader = Files.newBufferedReader(set.file())) {
        reader.lines().forEach(line -> {
          var formulaString = line.trim();

          if (formulaString.isEmpty()) {
            return;
          }

          var formula1 = LtlParser.syntax(formulaString);
          var formula2 = formula1.not();
          var formula3 = formula1.nnf();
          var formula4 = formula2.nnf();
          var formula5 = SimplifierFactory.apply(formula3, Mode.SYNTACTIC_FIXPOINT);
          var formula6 = SimplifierFactory.apply(formula4, Mode.SYNTACTIC_FIXPOINT);

          addNormalized(formulaSet, formula1, formula2, formula3, formula4, formula5, formula6);
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
    BASE("base"), BEEM("beem"), CHECK("check"), FGGF("fggf"), FGX("fgx"), LIBE_SPEC("libe-spec"),
    REGRESSIONS("regressions"), SIZE_FGGF("size-fggf"), SIZE("size");
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
    final Function<Environment, Function<LabelledFormula, ?>> constructor;
    final List<FormulaSet> selectedSets;

    Translator(String name, BiFunction<Environment, LabelledFormula, ?> function) {
      this(name, (x) -> (y) -> function.apply(x, y));
    }

    Translator(String name, Function<Environment, Function<LabelledFormula, ?>> constructor) {
      this(name, constructor, EnumSet.allOf(FormulaSet.class));
    }

    Translator(String name, Function<Environment, Function<LabelledFormula, ?>> constructor,
      Set<FormulaSet> selectedSets) {
      this.name = name;
      this.constructor = constructor;
      this.selectedSets = List.copyOf(selectedSets);
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

    static TestCase of(LabelledFormula formula, Function<LabelledFormula, ?> translation) {
      var properties = AutomatonSummary.of(() -> translation.apply(formula));
      return new TestCase(formula.toString(), properties);
    }

    DynamicTest test(Function<LabelledFormula, ?> translation) {
      return DynamicTest.dynamicTest(formula, () -> {
        var labelledFormula = LtlParser.parse(formula);

        try {
          var automaton = translation.apply(labelledFormula);
          assertNotNull(properties);
          properties.test(automaton);
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
          assertNull(properties);
        }
      });
    }
  }

  static class AutomatonSummary {
    final int size;
    final String acceptanceName;
    final int acceptanceSets;
    final boolean complete;
    final boolean deterministic;

    private AutomatonSummary(int size, OmegaAcceptance acceptance, boolean deterministic,
      boolean complete) {
      this.acceptanceSets = acceptance.acceptanceSets();
      this.acceptanceName = acceptance.getClass().getSimpleName();
      this.complete = complete;
      this.deterministic = deterministic;
      this.size = size;
    }

    @Nullable
    static AutomatonSummary of(Supplier<Object> supplier) {
      try {
        return of(supplier.get());
      } catch (IllegalArgumentException | UnsupportedOperationException ex) {
        return null;
      }
    }

    static AutomatonSummary of(Object object) {
      if (object instanceof Automaton) {
        return of((Automaton) object);
      } else {
        return of((LimitDeterministicAutomaton) object);
      }
    }

    static AutomatonSummary of(Automaton<?, ?> automaton) {
      return new AutomatonSummary(automaton.size(),
        automaton.acceptance(),
        automaton.is(Automaton.Property.DETERMINISTIC),
        automaton.is(Automaton.Property.COMPLETE));
    }

    static AutomatonSummary of(LimitDeterministicAutomaton<?, ?, ?, ?> automaton) {
      return new AutomatonSummary(automaton.size(),
        automaton.acceptingComponent().acceptance(),
        false,
        automaton.initialComponent().is(Automaton.Property.COMPLETE)
          && automaton.acceptingComponent().is(Automaton.Property.COMPLETE));
    }

    void test(Object object) {
      if (object instanceof Automaton) {
        test((Automaton<?, ?>) object);
      } else {
        test((LimitDeterministicAutomaton<?, ?, ?, ?>) object);
      }
    }

    void test(Automaton<?, ?> automaton) {
      assertEquals(size, automaton.size(), () -> String.format(
        "Expected %d states, got %d\nAutomaton:\n %s",
        size, automaton.size(), HoaPrinter.toString(automaton)));
      test(automaton.acceptance());
      assertEquals(deterministic, automaton.is(Automaton.Property.DETERMINISTIC));
      assertEquals(complete, automaton.is(Automaton.Property.COMPLETE));
    }

    void test(LimitDeterministicAutomaton<?, ?, ?, ?> automaton) {
      assertEquals(size, automaton.size(), () -> String.format(
        "Expected %d states, got %d\nAutomaton:\n %s",
        size, automaton.size(), HoaPrinter.toString(automaton)));
      test(automaton.acceptingComponent().acceptance());
      assertFalse(deterministic);
      assertEquals(complete, automaton.initialComponent().is(Automaton.Property.COMPLETE)
        && automaton.acceptingComponent().is(Automaton.Property.COMPLETE));
    }

    void test(OmegaAcceptance acceptance) {
      assertEquals(acceptanceSets, acceptance.acceptanceSets());
      assertEquals(acceptanceName, acceptance.getClass().getSimpleName());
    }
  }
}
