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

package owl.translations;

import static org.junit.Assert.assertThat;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.EAGER_UNFOLD;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.FORCE_JUMPS;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS;

import com.google.common.collect.Streams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import owl.automaton.Automaton;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrintable;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.run.DefaultEnvironment;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

@SuppressWarnings("PMD.UseUtilityClass")
public abstract class SizeRegressionTests<T extends HoaPrintable> {
  static final String BASE_PATH = "data/formulas";
  static final Pattern DATA_SPLIT_PATTERN = Pattern.compile("[();]");

  private final ToIntFunction<T> getAcceptanceSets;
  private final ToIntFunction<T> getStateCount;
  private final FormulaSet selectedClass;
  private final String tool;
  private final Function<LabelledFormula, ? extends T> translator;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  SizeRegressionTests(FormulaSet selectedClass, Function<LabelledFormula, ? extends T> translator,
    ToIntFunction<T> getStateCount, ToIntFunction<T> getAcceptanceSets, String tool) {
    this.selectedClass = selectedClass;
    this.translator = translator;
    this.getStateCount = getStateCount;
    this.getAcceptanceSets = getAcceptanceSets;
    this.tool = tool;
  }

  private static String formatSpecification(int posStateCount, int posAccSize, int negStateCount,
    int negAccSize) {
    return String.format("%s (%s); %s (%s)", posStateCount, posAccSize, negStateCount, negAccSize);
  }

  static int getAcceptanceSetsSize(Automaton<?, ?> automaton) {
    return automaton.getAcceptance().getAcceptanceSets();
  }

  static int getAcceptanceSetsSize(LimitDeterministicAutomaton<?, ?, ?, ?> automaton) {
    return getAcceptanceSetsSize(automaton.getAcceptingComponent());
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
    int expectedAcceptanceSets, int index, List<String> errorMessages) {
    int actualStateCount = getStateCount.applyAsInt(automaton);
    int actualAcceptanceSets = getAcceptanceSets.applyAsInt(automaton);

    // Check well-formed
    automaton.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));

    // Check size of the state space
    if (actualStateCount != expectedStateCount) {
      String errorMessage = String.format("Formula %d: Expected %d states, got %d (%s)",
        index, expectedStateCount, actualStateCount, formula);
      System.err.println("Error: " + errorMessage); // NOPMD
      errorMessages.add(errorMessage);
    }

    if (actualAcceptanceSets != expectedAcceptanceSets) {
      String errorMessage = String.format("Formula %d: Expected %d acceptance sets, got %d (%s)",
        index, expectedAcceptanceSets, actualAcceptanceSets, formula);
      System.err.println("Error: " + errorMessage); // NOPMD
      errorMessages.add(errorMessage);
    }
  }

  @Test
  public void test() throws IOException {
    List<String> errorMessages = Collections.synchronizedList(new ArrayList<>());

    try (BufferedReader formulasFile = Files.newBufferedReader(selectedClass.getFile());
         BufferedReader sizesFile = Files.newBufferedReader(selectedClass.getSizes(tool))) {

      AtomicInteger lineIndex = new AtomicInteger(0);

      Streams.forEachPair(formulasFile.lines(), sizesFile.lines(), (formulaString, sizeString) -> {
        int index = lineIndex.incrementAndGet();
        if (formulaString.trim().isEmpty()) {
          assertThat(sizeString.trim(), Matchers.isEmptyString());
          return;
        }

        LabelledFormula formula = LtlParser.parse(formulaString);
        int[] sizes = readSpecification(sizeString);
        assertSizes(formula, translator.apply(formula), sizes[0], sizes[1], index, errorMessages);
        formula = formula.not();
        assertSizes(formula, translator.apply(formula), sizes[2], sizes[3], index, errorMessages);
      });
    }

    assertThat(getClass().getSimpleName() + ' ' + selectedClass, errorMessages,
      Matchers.emptyCollectionOf(String.class));
  }

  // @Test
  public void train() throws IOException {
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

    public Path getSizes(String tool) {
      return Paths.get(String.format("%s/sizes/%s.%s.sizes", BASE_PATH, name, tool));
    }
  }

  public abstract static class DPA extends SizeRegressionTests<Automaton<?, ?>> {


    DPA(FormulaSet selectedClass, LTL2DPAFunction translator, String configuration) {
      super(selectedClass, translator, automaton -> automaton.getStates().size(),
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2dpa." + configuration);
    }

    @RunWith(Parameterized.class)
    public static class Breakpoint extends DPA {
      static final EnumSet<LTL2DPAFunction.Configuration> DPA_ALL = EnumSet.of(
        OPTIMISE_INITIAL_STATE,
        Configuration.OPTIMISED_STATE_STRUCTURE, // NOPMD
        EXISTS_SAFETY_CORE);

      public Breakpoint(FormulaSet selectedClass) {
        super(selectedClass, new LTL2DPAFunction(DefaultEnvironment.annotated(), DPA_ALL),
          "breakpoint");
      }

      @Parameterized.Parameters(name = "Group: {0}")
      public static Collection<FormulaSet> data() {
        return EnumSet.of(FormulaSet.BASE, FormulaSet.SIZE);
      }
    }

    @RunWith(Parameterized.class)
    public static class BreakpointFree extends DPA {
      static final EnumSet<LTL2DPAFunction.Configuration> DPA_ALL = EnumSet.of(GUESS_F,
        OPTIMISE_INITIAL_STATE,
        Configuration.OPTIMISED_STATE_STRUCTURE, // NOPMD
        EXISTS_SAFETY_CORE);

      public BreakpointFree(FormulaSet selectedClass) {
        super(selectedClass, new LTL2DPAFunction(DefaultEnvironment.annotated(), DPA_ALL),
          "breakpointfree");
      }

      @Parameterized.Parameters(name = "Group: {0}")
      public static Collection<FormulaSet> data() {
        return EnumSet.of(FormulaSet.BASE, FormulaSet.SIZE);
      }
    }
  }

  @RunWith(Parameterized.class)
  public static class Delag extends SizeRegressionTests<Automaton<?, ?>> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Delag(FormulaSet selectedClass) {
      super(selectedClass, formula -> new DelagBuilder(DefaultEnvironment.annotated(),
          new LTL2DPAFunction(DefaultEnvironment.annotated(),
            LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG))
          .apply(RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula)),
      x -> x.getStates().size(),
        SizeRegressionTests::getAcceptanceSetsSize, "delag");
    }

    @Parameterized.Parameters(name = "Group: {0}")
    public static Collection<FormulaSet> data() {
      return EnumSet.allOf(FormulaSet.class);
    }
  }

  public abstract static class LDBA
    extends SizeRegressionTests<LimitDeterministicAutomaton<?, ?, ?, ?>> {

    static final EnumSet<LTL2LDBAFunction.Configuration> LDBA_ALL = EnumSet.of(
      EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS);

    LDBA(FormulaSet selectedClass, LTL2LDBAFunction<?, ?, ?> translator, String configuration) {
      super(selectedClass, translator, LimitDeterministicAutomaton::size,
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2ldba." + configuration);
    }

    @RunWith(Parameterized.class)
    public static class Breakpoint extends LDBA {
      public Breakpoint(FormulaSet selectedClass) {
        super(selectedClass, (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createDegeneralizedBreakpointLDBABuilder(DefaultEnvironment.annotated(), LDBA_ALL),
          "breakpoint");
      }

      @Parameterized.Parameters(name = "Group: {0}")
      public static Collection<FormulaSet> data() {
        return EnumSet.allOf(FormulaSet.class);
      }
    }

    @RunWith(Parameterized.class)
    public static class BreakpointFree extends LDBA {
      public BreakpointFree(FormulaSet selectedClass) {
        super(selectedClass, (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createDegeneralizedBreakpointFreeLDBABuilder(DefaultEnvironment.annotated(), LDBA_ALL),
          "breakpointfree");
      }

      @Parameterized.Parameters(name = "Group: {0}")
      public static Collection<FormulaSet> data() {
        return EnumSet.allOf(FormulaSet.class);
      }
    }
  }

  public abstract static class LDGBA
    extends SizeRegressionTests<LimitDeterministicAutomaton<?, ?, ?, ?>> {

    LDGBA(FormulaSet selectedClass, LTL2LDBAFunction<?, ?, ?> translator, String configuration) {
      super(selectedClass, translator, LimitDeterministicAutomaton::size,
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2ldgba." + configuration);
    }

    @RunWith(Parameterized.class)
    public static class Breakpoint extends LDGBA {
      public Breakpoint(FormulaSet selectedClass) {
        super(selectedClass, (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createGeneralizedBreakpointLDBABuilder(DefaultEnvironment.annotated(), LDBA.LDBA_ALL),
          "breakpoint");
      }

      @Parameterized.Parameters(name = "Group: {0}")
      public static Collection<FormulaSet> data() {
        return EnumSet.allOf(FormulaSet.class);
      }
    }

    @RunWith(Parameterized.class)
    public static class BreakpointFree extends LDGBA {
      public BreakpointFree(FormulaSet selectedClass) {
        super(selectedClass, (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
            .createGeneralizedBreakpointFreeLDBABuilder(DefaultEnvironment.annotated(),
              LDBA.LDBA_ALL), "breakpointfree");
      }

      @Parameterized.Parameters(name = "Group: {0}")
      public static Collection<FormulaSet> data() {
        return EnumSet.allOf(FormulaSet.class);
      }
    }
  }
}
