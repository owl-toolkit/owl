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

import com.google.common.collect.Streams;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;
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
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

public abstract class SizeRegressionTests<T extends HoaPrintable> {
  private static final EnumSet<Optimisation> ALL = EnumSet.allOf(Optimisation.class);
  private static final String BASE_PATH = "data/formulas/";
  private final ToIntFunction<T> getAcceptanceSets;
  private final ToIntFunction<T> getStateCount;
  private final FormulaSet selectedClass;
  private final String tool;
  private final Function<LabelledFormula, ? extends T> translator;

  SizeRegressionTests(FormulaSet selectedClass, Function<LabelledFormula, ? extends T> translator,
    ToIntFunction<T> getStateCount, ToIntFunction<T> getAcceptanceSets, String tool) {
    this.selectedClass = selectedClass;
    this.translator = translator;
    this.getStateCount = getStateCount;
    this.getAcceptanceSets = getAcceptanceSets;
    this.tool = tool;
  }

  @Parameterized.Parameters(name = "Group: {0}")
  public static Iterable<?> data() {
    return EnumSet.allOf(FormulaSet.class);
  }

  static int getAcceptanceSetsSize(LimitDeterministicAutomaton<?, ?, ?, ?> automaton) {
    return getAcceptanceSetsSize(automaton.getAcceptingComponent());
  }

  static int getAcceptanceSetsSize(Automaton<?, ?> automaton) {
    return automaton.getAcceptance().getAcceptanceSets();
  }

  private static int[] readSpecification(String specificationString) {
    int[] specification = new int[4];
    String[] split = specificationString.split("[();]");
    specification[0] = Integer.parseInt(split[0].trim());
    specification[1] = Integer.parseInt(split[1].trim());
    specification[2] = Integer.parseInt(split[3].trim());
    specification[3] = Integer.parseInt(split[4].trim());
    return specification;
  }

  private static String writeSpecification(int posStateCount, int posAccSize, int negStateCount,
    int negAccSize) {
    return String.format("%s (%s); %s (%s)", posStateCount, posAccSize, negStateCount, negAccSize);
  }

  private void assertSizes(LabelledFormula formula, T automaton, int expectedStateCount,
    int expectedAcceptanceSets, List<String> errorMessages) {
    int actualStateCount = getStateCount.applyAsInt(automaton);
    int actualAcceptanceSets = getAcceptanceSets.applyAsInt(automaton);

    // Check wellformedness
    automaton.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));

    // Check size of the state space
    if (actualStateCount != expectedStateCount) {
      if (selectedClass == FormulaSet.SIZE && this instanceof DPA
        && actualStateCount < expectedStateCount) {
        // Do nothing; unstable translation.
        System.err.println("Warning: Unstable DPA translation.");
        System.err.println(String.format("Formula %s: Expected %s states. "
            + "Actual number of states: %s", formula, expectedStateCount, actualStateCount));
      } else {
        errorMessages.add(
          String.format("Formula %s: Expected %s states. Actual number of states: %s",
            formula, expectedStateCount, actualStateCount));
      }
    }

    if (actualAcceptanceSets != expectedAcceptanceSets) {
      if (selectedClass == FormulaSet.SIZE && this instanceof DPA
        && actualAcceptanceSets < expectedAcceptanceSets) {
        // Do nothing; unstable translation.
        System.err.println("Warning: Unstable DPA translation.");
      } else {
        errorMessages.add(
          String.format("Formula %s: Expected %s acceptance sets. Actual number of sets: %s",
            formula, expectedAcceptanceSets, actualAcceptanceSets));
      }
    }
  }

  @Test
  public void test() throws IOException {
    List<String> errorMessages = Collections.synchronizedList(new ArrayList<>());

    try (BufferedReader formulasFile = new BufferedReader(new FileReader(selectedClass.getFile()));
      BufferedReader sizesFile = new BufferedReader(new FileReader(selectedClass.getSizes(tool)))) {

      Streams.forEachPair(formulasFile.lines(), sizesFile.lines(), (formulaString, sizeString) -> {
        if (formulaString.trim().isEmpty()) {
          assertThat(sizeString.trim(), Matchers.isEmptyString());
          return;
        }

        LabelledFormula formula = LtlParser.parse(formulaString);
        int[] sizes = readSpecification(sizeString);
        assertSizes(formula, translator.apply(formula), sizes[0], sizes[1], errorMessages);
        formula = formula.not();
        assertSizes(formula, translator.apply(formula), sizes[2], sizes[3], errorMessages);
      });
    }

    assertThat(selectedClass.toString(), errorMessages, Matchers.emptyCollectionOf(String.class));
  }

  // @Test
  public void train() throws IOException {
    try (BufferedReader formulasFile = new BufferedReader(new FileReader(selectedClass.getFile()));
      BufferedWriter sizesFile = new BufferedWriter(new FileWriter(selectedClass.getSizes(tool)))) {

      formulasFile.lines().forEach((formulaString) -> {
        try {
          if (formulaString.trim().isEmpty()) {
            sizesFile.newLine();
            return;
          }

          LabelledFormula formula = LtlParser.parse(formulaString);
          T posAutomaton = translator.apply(formula);
          T notAutomaton = translator.apply(formula.not());
          sizesFile.write(writeSpecification(
            getStateCount.applyAsInt(posAutomaton),
            getAcceptanceSets.applyAsInt(posAutomaton),
            getStateCount.applyAsInt(notAutomaton),
            getAcceptanceSets.applyAsInt(notAutomaton)));
          sizesFile.newLine();
        } catch (IOException ex) {
          throw new RuntimeException(ex); // NOPMD
        }
      });
    }
  }

  protected enum FormulaSet {
    BASE, SIZE;

    File getFile() {
      return new File(BASE_PATH + this.toString().toLowerCase(Locale.UK) + ".ltl");
    }

    public File getSizes(String tool) {
      return new File(
        BASE_PATH + "sizes/" + this.toString().toLowerCase(Locale.UK) + "." + tool + ".sizes");
    }
  }

  public abstract static class DPA extends SizeRegressionTests<Automaton<?, ?>> {

    static final EnumSet<Optimisation> DPA_ALL;

    static {
      DPA_ALL = EnumSet.allOf(Optimisation.class);
      DPA_ALL.remove(Optimisation.PARALLEL);
      DPA_ALL.remove(Optimisation.COMPLETE);
    }

    DPA(FormulaSet selectedClass, LTL2DPAFunction translator, String configuration) {
      super(selectedClass, translator, Automaton::stateCount,
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2dpa." + configuration);
    }

    @RunWith(Parameterized.class)
    public static class Breakpoint extends DPA {
      public Breakpoint(FormulaSet selectedClass) {
        super(selectedClass, new LTL2DPAFunction(DPA_ALL, false), "breakpoint");
      }
    }

    @RunWith(Parameterized.class)
    public static class BreakpointFree extends DPA {
      public BreakpointFree(FormulaSet selectedClass) {
        super(selectedClass, new LTL2DPAFunction(DPA_ALL, true), "breakpointfree");
      }
    }
  }

  @RunWith(Parameterized.class)
  public static class Delag extends SizeRegressionTests<Automaton<?, ?>> {

    static final EnumSet<Optimisation> DPA_ALL;

    static {
      DPA_ALL = EnumSet.allOf(Optimisation.class);
      DPA_ALL.remove(Optimisation.COMPLETE);
    }

    public Delag(FormulaSet selectedClass) {
      super(selectedClass, new owl.translations.Delag(false,
        (Function) new LTL2DPAFunction(DPA_ALL))::translateWithFallback,
        Automaton::stateCount,
        SizeRegressionTests::getAcceptanceSetsSize, "delag");
    }
  }

  public abstract static class LDBA
    extends SizeRegressionTests<LimitDeterministicAutomaton<?, ?, ?, ?>> {

    LDBA(FormulaSet selectedClass, LTL2LDBAFunction<?, ?, ?> translator, String configuration) {
      super(selectedClass, translator, LimitDeterministicAutomaton::size,
        SizeRegressionTests::getAcceptanceSetsSize, "ltl2ldba." + configuration);
    }

    @RunWith(Parameterized.class)
    public static class Breakpoint extends LDBA {
      public Breakpoint(FormulaSet selectedClass) {
        super(selectedClass, (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
          .createDegeneralizedBreakpointLDBABuilder(ALL), "breakpoint");
      }
    }

    @RunWith(Parameterized.class)
    public static class BreakpointFree extends LDBA {
      public BreakpointFree(FormulaSet selectedClass) {
        super(selectedClass, (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
          .createDegeneralizedBreakpointFreeLDBABuilder(ALL), "breakpointfree");
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
        super(selectedClass,
          (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(ALL),
          "breakpoint");
      }
    }

    @RunWith(Parameterized.class)
    public static class BreakpointFree extends LDGBA {
      public BreakpointFree(FormulaSet selectedClass) {
        super(selectedClass, (LTL2LDBAFunction<?, ?, ?>) LTL2LDBAFunction
          .createGeneralizedBreakpointFreeLDBABuilder(ALL), "breakpointfree");
      }
    }
  }
}
