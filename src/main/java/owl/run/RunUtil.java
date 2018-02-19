package owl.run;

import com.google.common.base.Strings;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
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

  public static Option getDefaultParallelOption() {
    return new Option("p", "parallel", false, "Enable parallel processing (where supported)");
  }

  public static boolean checkDefaultParallelOption(CommandLine settings) {
    return settings.hasOption("parallel");
  }

  /**
   * Prints given {@code message} on standard error and calls {@link System#exit(int)} with 1.
   * An exception is returned to allow for one-line statements like
   * {@code throw failWithMessage("error!")}. This approximates the actual control flow as precise
   * as possible, since {@link System#exit(int)} does not return, but the compiler doesn't know
   * about this.
   */
  public static AssertionError failWithMessage(String message, @Nullable Throwable cause) {
    System.err.println(message); // NOPMD
    if (cause != null) {
      logger.log(Level.FINE, "Stacktrace:", cause);
    }

    System.exit(1);
    return new AssertionError("Unreachable", cause);
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
