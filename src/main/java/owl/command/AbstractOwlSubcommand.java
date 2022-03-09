/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.command;

import static owl.thirdparty.picocli.CommandLine.Option;
import static owl.thirdparty.picocli.CommandLine.ParentCommand;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.graalvm.nativeimage.ImageInfo;

abstract class AbstractOwlSubcommand extends AbstractOwlCommand {

  private static final String NON_NATIVE_MODE_NAME = "--run-in-non-native-mode";

  private static final String NON_NATIVE_MODE_DESCRIPTION =
    "By default Owl only runs when compiled into a native executable. The flag '"
      + NON_NATIVE_MODE_NAME + "' overrides this default and runs Owl in a compatibility mode such "
      + "that it can be executed on any JVM. If Owl is run in non-native mode, the startup time is "
      + "increased and native libraries are replaced by slower Java-only libraries. This option is "
      + "intended for debugging only and if you want to use Owl in non-native mode please "
      + "contact the maintainers of Owl.";

  @ParentCommand
  private AbstractOwlCommand parentCommand;

  @Option(
    names = NON_NATIVE_MODE_NAME,
    description = NON_NATIVE_MODE_DESCRIPTION
  )
  @SuppressWarnings("PMD.ImmutableField")
  private boolean nonNativeMode = false;

  @Override
  @SuppressWarnings("PMD.SystemPrintln")
  public final Integer call() throws Exception {
    boolean enforceNativeMode = !nonNativeMode;

    if (enforceNativeMode && !ImageInfo.inImageCode()) {
      System.err.println(
        "Owl has detected that it is executed in non-native mode. " + NON_NATIVE_MODE_DESCRIPTION);
      return -1;
    }

    if (ImageInfo.inImageCode()) {
      // Workaround for https://github.com/oracle/graal/issues/3398
      var executor = Executors.newSingleThreadExecutor(
        runnable -> new Thread(null, runnable, "main-with-larger-stack"));
      var future = executor.submit(this::run);

      try {
        return Uninterruptibles.getUninterruptibly(future);
      } catch (ExecutionException ex) {
        var cause = ex.getCause();

        // Unpack exceptions and errors.
        if (cause instanceof Exception) {
          throw (Exception) cause;
        }

        if (cause instanceof Error) {
          throw (Error) cause;
        }

        throw ex;
      }
    }

    return run();
  }


  @Override
  protected List<String> rawArgs() {
    return parentCommand.rawArgs();
  }

  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  protected abstract int run() throws Exception;
}
