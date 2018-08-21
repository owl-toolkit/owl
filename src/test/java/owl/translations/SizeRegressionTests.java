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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.EAGER_UNFOLD;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.FORCE_JUMPS;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS;
import static owl.util.Assertions.assertThat;

import com.google.common.collect.Streams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import owl.automaton.Automaton;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrinter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.run.DefaultEnvironment;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;
import owl.translations.ltl2dra.LTL2DRAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

abstract class SizeRegressionTests<T> {
  private static final String BASE_PATH = "data/formulas";
  private static final Pattern DATA_SPLIT_PATTERN = Pattern.compile("[();]");

  private final ToIntFunction<T> getAcceptanceSets;
  private final ToIntFunction<T> getStateCount;
  private final String tool;
  private final Function<LabelledFormula, ? extends T> translator;

  SizeRegressionTests(Function<LabelledFormula, ? extends T> translator,
    ToIntFunction<T> getStateCount, ToIntFunction<T> getAcceptanceSets, String tool) {
    this.translator = translator;
    this.getStateCount = getStateCount;
    this.getAcceptanceSets = getAcceptanceSets;
    this.tool = tool;
  }

  private static String formatSpecification(int posStateCount, int posAccSize, int negStateCount,
    int negAccSize) {
    return String.format("%s (%s); %s (%s)", posStateCount, posAccSize, negStateCount, negAccSize);
  }

  private static int getAcceptanceSetsSize(Automaton<?, ?> automaton) {
    return automaton.acceptance().acceptanceSets();
  }

  private static int getAcceptanceSetsSize(LimitDeterministicAutomaton<?, ?, ?, ?> automaton) {
    return getAcceptanceSetsSize(automaton.acceptingComponent());
  }

  private static int[] readSpecification(String specificationString) {
    int[] specification = new int[4];
    String[] split = DATA_SPLIT_PATTERN.split(specificationString);
    specification[0] = Integer.parseInt(split[0].trim());
    specification[1] = Integer.parseInt(split[1].trim());
    specification[2] = Integer.parseInt(split[3].trim());
    specification[3] = Integer.parseInt(split[4].trim());
    return specification;
  }

  private void assertSizes(LabelledFormula formula, T automaton, int expectedStateCount,
    int expectedAcceptanceSets) {
    int actualStateCount = getStateCount.applyAsInt(automaton);
    int actualAcceptanceSets = getAcceptanceSets.applyAsInt(automaton);

    // Check well-formed
    HoaPrinter.feedTo(automaton, new HOAIntermediateCheckValidity(new HOAConsumerNull()),
      EnumSet.noneOf(HoaPrinter.HoaOption.class));

    assertEquals(expectedStateCount, actualStateCount, () -> String.format(
      "Formula %s: Expected %d states, got %d", formula, expectedStateCount, actualStateCount));

    assertEquals(expectedAcceptanceSets, actualAcceptanceSets, () ->
      String.format("Formula %s: Expected %d acceptance sets, got %d",
        formula, expectedAcceptanceSets, actualAcceptanceSets));
  }

  @ParameterizedTest
  @EnumSource(FormulaSet.class)
  void test(FormulaSet selectedClass) throws IOException {
    try (BufferedReader formulasFile = Files.newBufferedReader(selectedClass.getFile());
         BufferedReader sizesFile = Files.newBufferedReader(selectedClass.getSizes(tool))) {
      Stream<Executable> executableStream = Streams.zip(formulasFile.lines(), sizesFile.lines(),
        (formulaString, sizeString) -> () -> {
          if (formulaString.trim().isEmpty()) {
            assertThat(sizeString.trim(), String::isEmpty);
            return;
          }

          var formula = SimplifierFactory
            .apply(LtlParser.parse(formulaString), Mode.SYNTACTIC_FIXPOINT);
          int[] sizes = readSpecification(sizeString);
          assertSizes(formula, translator.apply(formula), sizes[0], sizes[1]);
          formula = formula.not();
          assertSizes(formula, translator.apply(formula), sizes[2], sizes[3]);
        });

      assertAll(getClass().getSimpleName() + ' ' + selectedClass, executableStream);
    }
  }

  @Disabled
  @ParameterizedTest
  @EnumSource(FormulaSet.class)
  void train(FormulaSet selectedClass) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader formulasFile = Files.newBufferedReader(selectedClass.getFile())) {
      formulasFile.lines().forEach((formulaString) -> {
        String trimmedString = formulaString.trim();
        if (trimmedString.isEmpty()) {
          lines.add("");
          return;
        }

        LabelledFormula formula = LtlParser.parse(trimmedString);
        T posAutomaton = translator.apply(formula);
        T notAutomaton = translator.apply(formula.not());
        lines.add(formatSpecification(
          getStateCount.applyAsInt(posAutomaton),
          getAcceptanceSets.applyAsInt(posAutomaton),
          getStateCount.applyAsInt(notAutomaton),
          getAcceptanceSets.applyAsInt(notAutomaton)));
      });
    }
    Path sizeFile = selectedClass.getSizes(tool);
    try (PrintWriter sizeWriter = new PrintWriter(Files.newBufferedWriter(sizeFile))) {
      lines.forEach(sizeWriter::println);
    }
  }

  protected enum FormulaSet {
    BASE("base"), SIZE("size"), SIZE_FGGF("size-fggf");
    private final String name;

    FormulaSet(String name) {
      this.name = name;
    }

    Path getFile() {
      return Paths.get(String.format("%s/%s.ltl", BASE_PATH, name));
    }

    Path getSizes(String tool) {
      return Paths.get(String.format("%s/sizes/%s.%s.sizes", BASE_PATH, name, tool));
    }
  }

  abstract static class DPA extends SizeRegressionTests<Automaton<?, ?>> {

    DPA(LTL2DPAFunction translator, String configuration) {
      super(translator, Automaton::size,
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2dpa." + configuration);
    }

    static class Breakpoint extends DPA {
      static final EnumSet<LTL2DPAFunction.Configuration> DPA_ALL = EnumSet.of(
        COMPRESS_COLOURS,
        OPTIMISE_INITIAL_STATE,
        Configuration.OPTIMISED_STATE_STRUCTURE, // NOPMD
        EXISTS_SAFETY_CORE);

      Breakpoint() {
        super(new LTL2DPAFunction(DefaultEnvironment.annotated(), DPA_ALL), "breakpoint");
      }

      @ParameterizedTest
      @EnumSource(FormulaSet.class)
      @Override
      void test(FormulaSet selectedClass) throws IOException {
        assumeTrue(selectedClass == FormulaSet.BASE || selectedClass == FormulaSet.SIZE);
        super.test(selectedClass);
      }
    }

    static class BreakpointFree extends DPA {
      static final EnumSet<LTL2DPAFunction.Configuration> DPA_ALL = EnumSet.of(GUESS_F,
        COMPRESS_COLOURS,
        OPTIMISE_INITIAL_STATE,
        Configuration.OPTIMISED_STATE_STRUCTURE, // NOPMD
        EXISTS_SAFETY_CORE);

      BreakpointFree() {
        super(new LTL2DPAFunction(DefaultEnvironment.annotated(), DPA_ALL), "breakpointfree");
      }

      @ParameterizedTest
      @EnumSource(FormulaSet.class)
      @Override
      void test(FormulaSet selectedClass) throws IOException {
        assumeTrue(selectedClass == FormulaSet.BASE || selectedClass == FormulaSet.SIZE);
        super.test(selectedClass);
      }
    }
  }


  static class Delag extends SizeRegressionTests<Automaton<?, ?>> {

    @SuppressWarnings({"unchecked", "rawtypes", "PMD.UnnecessaryFullyQualifiedName"})
    Delag() {
      super(formula -> new DelagBuilder(DefaultEnvironment.annotated(),
          new LTL2DRAFunction(DefaultEnvironment.annotated(), EnumSet.of(
            LTL2DRAFunction.Configuration.OPTIMISE_INITIAL_STATE,
            LTL2DRAFunction.Configuration.OPTIMISED_STATE_STRUCTURE,
            LTL2DRAFunction.Configuration.EXISTS_SAFETY_CORE)))
          .apply(SimplifierFactory.apply(formula, Mode.SYNTACTIC_FIXPOINT)),
        Automaton::size,
        SizeRegressionTests::getAcceptanceSetsSize, "delag");
    }
  }

  abstract static class LDBA extends SizeRegressionTests<LimitDeterministicAutomaton<?, ?, ?, ?>> {

    static final EnumSet<LTL2LDBAFunction.Configuration> LDBA_ALL =
      EnumSet.of(EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS);

    LDBA(LTL2LDBAFunction<?, ?, ?> translator, String configuration) {
      super(translator, LimitDeterministicAutomaton::size,
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2ldba." + configuration);
    }

    static class Breakpoint extends LDBA {
      Breakpoint() {
        super((LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createDegeneralizedBreakpointLDBABuilder(DefaultEnvironment.annotated(), LDBA_ALL),
          "breakpoint");
      }
    }

    static class BreakpointFree extends LDBA {
      BreakpointFree() {
        super((LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createDegeneralizedBreakpointFreeLDBABuilder(DefaultEnvironment.annotated(), LDBA_ALL),
          "breakpointfree");
      }
    }
  }

  abstract static class LDGBA extends SizeRegressionTests<LimitDeterministicAutomaton<?, ?, ?, ?>> {

    LDGBA(LTL2LDBAFunction<?, ?, ?> translator, String configuration) {
      super(translator, LimitDeterministicAutomaton::size,
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2ldgba." + configuration);
    }

    static class Breakpoint extends LDGBA {
      Breakpoint() {
        super((LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createGeneralizedBreakpointLDBABuilder(DefaultEnvironment.annotated(), LDBA.LDBA_ALL),
          "breakpoint");
      }
    }

    static class BreakpointFree extends LDGBA {
      BreakpointFree() {
        super((LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createGeneralizedBreakpointFreeLDBABuilder(DefaultEnvironment.annotated(),
              LDBA.LDBA_ALL), "breakpointfree");
      }
    }
  }
}
