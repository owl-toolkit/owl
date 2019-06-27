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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.minimizations.MinimizationUtil;
import owl.automaton.output.HoaPrinter;
import owl.automaton.transformations.RabinDegeneralization;
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
import owl.ltl.visitors.LatexPrintVisitor;
import owl.run.DefaultEnvironment;
import owl.run.Environment;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;
import owl.translations.ltl2nba.SymmetricNBAConstruction;
import owl.translations.rabinizer.ImmutableRabinizerConfiguration;
import owl.translations.rabinizer.RabinizerBuilder;

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
      new Translator("fgSafety.interleaved", LTL2DAFunction::fgSafetyInterleaved),
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
      int variables = (int) Arrays.stream(shiftedFormula.mapping).filter(x -> x != -1).count();
      var restrictedAlphabet = COMMON_ALPHABET.subList(0, variables);
      var labelledFormula = LabelledFormula.of(shiftedFormula.formula, restrictedAlphabet);

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

    var translatorFunction = translator.constructor.apply(DefaultEnvironment.standard());
    var testCases = formulaSet.stream().map(x -> TestCase.of(x, translatorFunction)).toArray();

    try (BufferedWriter writer = Files.newBufferedWriter(translator.referenceFile())) {
      var gson = new GsonBuilder().setPrettyPrinting().create();
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

  @Tag("size-report")
  @ParameterizedTest
  @SuppressWarnings("PMD.SystemPrintln")
  @EnumSource(
    value = FormulaSet.class,
    names = {"DWYER", "PELANEK", "PARAMETRISED", "ETESSAMI", "SOMENZI"})
  void generateLatexReport(FormulaSet set) {
    var formulaSet = new LinkedHashSet<LabelledFormula>();

    try (BufferedReader reader = Files.newBufferedReader(set.file())) {
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
    } catch (IOException exception) {
      fail(exception);
    }

    var configuration = ImmutableRabinizerConfiguration.builder()
      .eager(true)
      .supportBasedRelevantFormulaAnalysis(true)
      .suspendableFormulaDetection(true)
      .build();

    var draAsymmetric = new Translator("DRA (Rab. 4)", (env) -> (formula) ->
      MinimizationUtil.minimizeDefault(
        MutableAutomatonFactory.copy(
         RabinDegeneralization.degeneralize(
           MinimizationUtil.minimizeDefault(
             RabinizerBuilder.build(formula, env, configuration),
             MinimizationUtil.MinimizationLevel.ALL))),
        MinimizationUtil.MinimizationLevel.ALL));

    var dgraAsymmetric = new Translator("DGRA (Rab. 4)", (env) -> (formula) ->
      MinimizationUtil.minimizeDefault(
        RabinizerBuilder.build(formula, env, configuration),
        MinimizationUtil.MinimizationLevel.ALL));

    var draSymmetric = new Translator("DRA", environment -> formula ->
      MinimizationUtil.minimizeDefault(
        MutableAutomatonFactory.copy(
          SymmetricDRAConstruction.of(environment, RabinAcceptance.class, true).apply(formula)),
        MinimizationUtil.MinimizationLevel.ALL));

    var dgraSymmetric = new Translator("DGRA", environment -> formula ->
      MinimizationUtil.minimizeDefault(
        MutableAutomatonFactory.copy(
          SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, true)
            .apply(formula)),
        MinimizationUtil.MinimizationLevel.ALL));

    List<List<Translator>> translators = new ArrayList<>();
    List<List<List<TestCase>>> results = new ArrayList<>();

    var nbaGroup = TRANSLATORS.stream()
      .filter(x -> x.name.contains("nba"))
      .collect(Collectors.toList());

    var ngbaGroup = List.of(
      new Translator("NGBA (Spot)", environment -> formula
      -> new ExternalTranslator(environment, "ltl2tgba --any --low -H").apply(formula)),
      TRANSLATORS.stream().filter(x -> x.name.contains("ngba")).findFirst().orElseThrow()
    );

    var ldbaGroup = TRANSLATORS.stream()
      .filter(x -> x.name.contains("ldba"))
      .collect(Collectors.toList());

    var ldgbaGroup = TRANSLATORS.stream()
      .filter(x -> x.name.contains("ldgba"))
      .collect(Collectors.toList());

    var draGroup = List.of(draAsymmetric, draSymmetric);
    var dgraGroup = List.of(dgraAsymmetric, dgraSymmetric);

    translators.add(nbaGroup);
    results.add(computeResults(nbaGroup, formulaSet));

    translators.add(ngbaGroup);
    results.add(computeResults(ngbaGroup, formulaSet));

    translators.add(ldbaGroup);
    results.add(computeResults(ldbaGroup, formulaSet));

    translators.add(ldgbaGroup);
    results.add(computeResults(ldgbaGroup, formulaSet));

    translators.add(draGroup);
    results.add(computeResults(draGroup, formulaSet));

    translators.add(dgraGroup);
    results.add(computeResults(dgraGroup, formulaSet));

    Map<String, String> readableNames = Map.of(
      "nba.symmetric",   "NBA",
      "ngba.symmetric",  "NGBA",
      "ldba.symmetric",  "LDBA",
      "ldgba.symmetric", "LDGBA",
      "ldba.asymmetric", "LDBA (Rab. 4)",
      "ldgba.asymmetric", "LDGBA (Rab. 4)"
    );

    System.out.print(new LatexReport(set, translators, readableNames,
      Set.of(translators.get(1), translators.get(3), translators.get(4), translators.get(5)),
      results
    ).report());
  }

  private static List<List<TestCase>> computeResults(List<Translator> translators,
    Set<LabelledFormula> formulaSet) {
    List<List<TestCase>> results = new ArrayList<>();

    for (Translator translator : translators) {
      var translatorFunction = translator.constructor.apply(DefaultEnvironment.standard());
      var testCases = formulaSet.stream()
        .flatMap(x -> Stream.of(
          TestCase.of(x, translatorFunction), TestCase.of(x.not(), translatorFunction)))
        .collect(Collectors.toUnmodifiableList());
      results.add(testCases);
    }

    return results;
  }

  @SuppressWarnings("PMD.ConsecutiveAppendsShouldReuse")
  private static class LatexReport {

    private static final LatexPrintVisitor PRINT_VISITOR = new LatexPrintVisitor(COMMON_ALPHABET);

    private final List<List<Translator>> groupedTranslators;
    private final List<List<List<TestCase>>> groupedResults;
    private final Map<String, String> translatorNames;
    private final Set<List<Translator>> twoColumnGroup;
    private final Map<Formula, Integer> formulaEnumeration;
    private final FormulaSet formulaSet;
    private int tableCounter = 0;

    private LatexReport(
      FormulaSet formulaSet, List<List<Translator>> groupedTranslators,
      Map<String, String> translatorNames, Set<List<Translator>> twoColumnGroup,
      List<List<List<TestCase>>> groupedResults) {
      assert groupedTranslators.size() == groupedResults.size();

      this.groupedTranslators = groupedTranslators;
      this.groupedResults = groupedResults;
      this.translatorNames = translatorNames;
      this.twoColumnGroup = twoColumnGroup;

      this.formulaEnumeration = new HashMap<>();

      for (var results : this.groupedResults.get(0).get(0)) {
        var formula = LtlParser.syntax(results.formula, COMMON_ALPHABET);

        if (!formulaEnumeration.containsKey(formula.not())) {
          assert !formulaEnumeration.containsKey(formula);
          formulaEnumeration.put(formula, formulaEnumeration.size() + 1);
        }
      }

      this.formulaSet = formulaSet;
    }

    private String report() {
      IntPredicate interestingRow = row -> {
        for (List<List<TestCase>> groupResults : this.groupedResults) {
          int lowerBound = -1;
          int upperBound = -1;

          for (List<TestCase> toolResults : groupResults) {
            var toolResult = toolResults.get(row).properties;

            if (toolResult == null) {
              return true;
            }

            if (lowerBound == -1) {
              assert upperBound == -1;
              lowerBound = toolResult.size;
              upperBound = toolResult.size;
            } else {
              lowerBound = Math.min(lowerBound, toolResult.size);
              upperBound = Math.max(upperBound, toolResult.size);
            }

            if (upperBound - lowerBound > 3) {
              return true;
            }
          }
        }

        return false;
      };

      IntPredicate moveToAppendix = firstRow -> {
        assert firstRow % 2 == 0;
        return !interestingRow.test(firstRow) && !interestingRow.test(firstRow + 1);
      };

      return String.format("%s\n\\newcommand{\\%sAppendix}{%s}",
        renderLatexReport(moveToAppendix.negate()),
        formulaSet.name().toLowerCase(),
        renderLatexReport(moveToAppendix));
    }

    private String renderLatexReport(IntPredicate interestingRow) {
      int groups = groupedTranslators.size();
      int rows = groupedResults.get(0).get(0).size();
      int columns = 1 + groupedTranslators.stream()
        .mapToInt(x -> (twoColumnGroup.contains(x) ? 2 : 1) *  x.size()).sum();

      StringBuilder report = new StringBuilder(tableHeader());
      StringBuilder formulaRows = new StringBuilder(report.capacity());

      int printedRows = 0;

      for (int row = 0; row < rows; row++) {
        if (row % 2 == 0 && !interestingRow.test(row)) {
          //noinspection AssignmentToForLoopParameter
          row += 1; // NOPMD
          continue;
        }

        String formula = groupedResults.get(0).get(0).get(row).formula;
        // String latexFormula = renderLatexFormula(formula);

        Formula labelledFormula = LtlParser.syntax(formula, COMMON_ALPHABET);
        String latexFormula = labelledFormula.accept(PRINT_VISITOR);

        if (formulaEnumeration.containsKey(labelledFormula.not())) {
          int index = formulaEnumeration.get(labelledFormula.not());
          report.append("    $\\overline{\\varphi_{" + index + "}}$ \n");
          report.append("      ");
        } else {
          int index = formulaEnumeration.get(labelledFormula);
          formulaRows.append("    $\\varphi_{")
            .append(index)
            .append("}$ & \\multicolumn{")
            .append(columns)
            .append("}{p{0.89\\linewidth}}{\\scriptsize $")
            .append(latexFormula)
            .append("$} \\\\\n");
          report.append("    $\\varphi_{" + index + "}$ \n");
          report.append("      ");
        }

        for (int group = 0; group < groups; group++) {
          var groupResults = groupedResults.get(group);
          int groupColumns = groupResults.size();

          assert groupedTranslators.get(group).size() == groupColumns;

          int smallestSize = Integer.MAX_VALUE;
          int acceptanceSmallestSize = Integer.MAX_VALUE;

          for (List<TestCase> results : groupResults) {
            var properties = results.get(row).properties;

            if (properties != null) {
              smallestSize = Math.min(smallestSize, properties.size);
              acceptanceSmallestSize = Math.min(acceptanceSmallestSize, properties.acceptanceSets);
            }
          }

          for (int groupColumn = 0; groupColumn < groupColumns; groupColumn++) {
            var result = groupedResults.get(group).get(groupColumn).get(row);
            assert formula.equals(result.formula)
              : "Expected: " + formula + "\n Obtained: " + result.formula;

            if (result.properties == null) {
              report.append("& n/a & ");
              continue;
            }

            int size = result.properties.size;
            int acceptanceSize = result.properties.acceptanceSets;

            if (size == smallestSize) {
              report.append(String.format("& \\textbf{%d} ", size));
            } else {
              report.append(String.format("& %d ", size));
            }

            if (acceptanceSize > 1) {
              assert twoColumnGroup.contains(groupedTranslators.get(group));

              if (acceptanceSize == acceptanceSmallestSize) {
                report.append(String.format("& \\textbf{(%d)} ", acceptanceSize));
              } else {
                report.append(String.format("& (%d) ", acceptanceSize));
              }
            } else if (twoColumnGroup.contains(groupedTranslators.get(group))) {
              report.append("& ");
            }
          }
        }

        report.append("\\\\\n");
        printedRows = printedRows + 1;

        if (printedRows == 2 * 18) {
          report.append(tableFooter(formulaRows));
          printedRows = 0;
          formulaRows = new StringBuilder();
          report.append(tableHeader());
        }
      }

      report.append(tableFooter(formulaRows));

      return report.toString();
    }

    private StringBuilder tableHeader() {
      tableCounter++;

      StringBuilder header = new StringBuilder();

      header.append("\n\\begin{table}[hbt]\n");
      header.append("  \\centering\n");
      header.append("  \\scriptsize\n");
      header.append("  \\begin{tabularx}{\\linewidth}{p{\\widthof{$\\varphi_{99}$}}|");
      header.append(groupedTranslators.stream().map(group ->
        IntStream.range(0, group.size())
          .mapToObj(x -> twoColumnGroup.contains(group) ? "r@{\\hspace{0.33\\tabcolsep}}l" : "r")
          .collect(Collectors.joining("@{\\hspace{1.5\\tabcolsep}}")))
        .collect(Collectors.joining("|")));
      header.append("X}\n");
      header.append("    \\rot{LTL}");

      for (List<Translator> group : groupedTranslators) {
        for (Translator translator : group) {
          header.append("& \\rot");
          header.append(twoColumnGroup.contains(group) ? "Two" : "");
          header.append('{');
          header.append(translatorNames.getOrDefault(translator.name, translator.name));
          header.append("} ");
        }
      }

      header.append("\\\\\n");
      header.append("    \\toprule\n");
      return header;
    }

    private StringBuilder tableFooter(StringBuilder formulaRows) {
      StringBuilder footer = new StringBuilder(formulaRows.capacity() + 100);
      footer.append("    \\midrule\n");
      footer.append(formulaRows);
      footer.append("    \\bottomrule\n");
      footer.append("  \\end{tabularx}\n");
      footer.append(String.format("  \\caption{%s:%d}\n",
        formulaSet.name().toLowerCase(), tableCounter));
      footer.append(String.format("  \\label{exp:table:%s:%d}\n",
        formulaSet.name().toLowerCase(), tableCounter));
      footer.append("\\end{table}\n");
      return footer;
    }
  }
}
