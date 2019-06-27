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

package owl.run;

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

public final class RunUtil {
  private static final Logger logger = Logger.getLogger(RunUtil.class.getName());

  private RunUtil() {}

  public static Option getDefaultAnnotationOption() {
    return new Option("a", "annotations", false, "Gather additional labels etc. (where supported)");
  }

  public static boolean checkDefaultAnnotationOption(CommandLine settings) {
    String annotationsEnv = System.getenv("OWL_ANNOTATIONS");
    boolean annotationsFromEnv = !Strings.isNullOrEmpty(annotationsEnv)
      && !"0".equals(annotationsEnv);
    return annotationsFromEnv || settings.hasOption("annotations");
  }

  /**
   * Prints given {@code message} on standard error and calls {@link System#exit(int)} with 1.
   * An exception is returned to allow for one-line statements like
   * {@code throw failWithMessage("error!")}. This approximates the actual control flow as precise
   * as possible, since {@link System#exit(int)} does not return, but the compiler doesn't know
   * about this.
   */
  public static AssertionError failWithMessage(String message) {
    System.err.println(message); // NOPMD
    System.exit(1);
    return new AssertionError("Unreachable");
  }

  /**
   * Prints given {@code message} on standard error and calls {@link System#exit(int)} with 1 and
   * logs the given {@code cause}.
   *
   * @see #failWithMessage(String)
   */
  public static AssertionError failWithMessage(String message, Throwable cause) {
    System.err.println(message); // NOPMD
    logger.log(Level.FINE, "Stacktrace:", cause);
    System.exit(1);
    return new AssertionError("Unreachable", cause);
  }

  @SuppressWarnings({"PMD.SystemPrintln"})
  public static void checkForVersion(String[] args) {
    if (Arrays.asList(args).contains("-v") || Arrays.asList(args).contains("--version")) {
      System.out.println("Name: " + RunUtil.class.getPackage().getImplementationTitle());
      System.out.println("Version: " + RunUtil.class.getPackage().getImplementationVersion());
      System.exit(0);
    }
  }

  /**
   * Executes the given given runner and logs any occurring error to the console.
   */
  @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.SystemPrintln"})
  public static void execute(Callable<Void> runner) {
    try {
      runner.call();
    } catch (PipelineException e) {
      logger.log(Level.FINE, "Error during execution", e);
      System.err.println(e.getMessage());
    } catch (Exception e) {
      // Only FINE since we explicitly log to System.err
      logger.log(Level.FINE, "Error during execution", e);
      if (e.getMessage() == null) {
        System.err.printf("An unexpected error of type %s occurred during execution%n",
          e.getClass().getSimpleName());
      } else {
        System.err.printf("An unexpected error occurred during execution: %s%n", e.getMessage());
      }

      e.printStackTrace(System.err);
    }
  }
}
