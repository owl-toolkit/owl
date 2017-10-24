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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.function.Function;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.transformations.ToStateAcceptance;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.automaton.output.HoaPrintable;

public abstract class AbstractCommandLineTool<T> {
  private static final String ANNOTATIONS = "--annotations";
  private static final String OPTIMISATIONS = "--optimisations=";

  @SuppressWarnings("PMD.PrematureDeclaration")
  void execute(Deque<String> args) {
    // Read input.
    EnumSet<HoaPrintable.Option> options = parseHoaOutputOptions(args);
    EnumSet<Optimisation> optimisations = parseOptimisationOptions(args);
    Function<T, ? extends HoaPrintable> translation = getTranslation(optimisations);
    boolean stateAcceptance = args.remove("--state-acceptance");
    boolean readStdIn = args.isEmpty();
    Collection<T> inputs;

    try {
      if (readStdIn) {
        inputs = parseInput(System.in);
      } else if (args.getFirst().equals("-F")) {
        args.pollFirst();
        try (InputStream input = Files.newInputStream(Paths.get(args.pollFirst()))) {
          inputs = parseInput(input);
        }
      } else {
        inputs = parseInput(new ByteArrayInputStream(args.getFirst().getBytes("UTF-8")));
      }
    } catch (ParseCancellationException | ParseException | IOException ex) {
      System.err.println("Failed to read input. Reason: " + ex);
      ex.printStackTrace();
      return;
    }

    for (T input : inputs) {
      // Apply translation.
      HoaPrintable result = translation.apply(input);
      // Write output.
      HOAConsumer consumer = new HOAConsumerPrint(System.out);

      if (stateAcceptance) {
        consumer = new HOAIntermediateStoreAndManipulate(consumer, new ToStateAcceptance());
      }

      result.toHoa(consumer, options);
    }
  }

  protected abstract Function<T, ? extends HoaPrintable> getTranslation(
    EnumSet<Optimisation> optimisations);

  private EnumSet<HoaPrintable.Option> parseHoaOutputOptions(Deque<String> args) {
    if (args.remove(ANNOTATIONS)) {
      return EnumSet.of(HoaPrintable.Option.ANNOTATIONS);
    } else {
      return EnumSet.noneOf(HoaPrintable.Option.class);
    }
  }

  protected abstract Collection<T> parseInput(InputStream stream)
    throws IOException, ParseException;

  private EnumSet<Optimisation> parseOptimisationOptions(Deque<String> args) {
    EnumSet<Optimisation> set = EnumSet.noneOf(Optimisation.class);
    Iterator<String> iterator = args.iterator();

    while (iterator.hasNext()) {
      String argument = iterator.next();

      if (!argument.startsWith(OPTIMISATIONS)) {
        continue;
      }

      // Remove element from collection and remove prefix from string.
      argument = argument.substring(OPTIMISATIONS.length());
      iterator.remove();

      switch (argument) {
        case "all":
          return EnumSet.complementOf(set);

        case "none":
          return set;

        default:
          for (String optimisation : argument.split(",")) {
            set.add(Optimisation.valueOf(optimisation));
          }

          return set;
      }
    }

    return EnumSet.complementOf(set);
  }
}
