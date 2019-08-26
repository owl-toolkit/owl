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

package owl.run.parser;

import static owl.run.modules.OwlModuleRegistry.DEFAULT_REGISTRY;
import static owl.run.modules.OwlModuleRegistry.Type;

import com.google.common.base.Strings;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModuleRegistry;
import owl.util.GuardedStream;

final class ParseUtil {
  private static final HelpFormatter formatter;

  static {
    formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    formatter.setWidth(80);
  }

  private ParseUtil() {}

  static boolean isHelp(String[] args) {
    return args.length == 1 && List.of("help", "--help", "-h").contains(args[0]);
  }

  @Nullable
  static String isSpecificHelp(String[] args) {
    if (args.length == 2 && List.of("help", "--help", "-h").contains(args[0])) {
      return args[1];
    }
    return null;
  }

  static List<OwlModule<?>> getSortedSettings(OwlModuleRegistry registry, Type type) {
    return registry.get(type).stream()
      .sorted(Comparator.comparing(OwlModule::key))
      .collect(Collectors.toList());
  }

  static void printList(Collection<OwlModule<?>> settingsCollection,
    @Nullable Type type, @Nullable String invalidName) {
    printGuarded(writer -> {
      if (invalidName == null) {
        if (type == null) {
          formatter.printWrapped(writer, formatter.getWidth(), "All modules: ");
        } else {
          formatter.printWrapped(writer, formatter.getWidth(),
            "All " + type.toString().toLowerCase() + "s: ");
        }
      } else {
        if (type == null) {
          formatter.printWrapped(writer, formatter.getWidth(), "No module with name "
            + invalidName + " found. Available:");
        } else {
          formatter.printWrapped(writer, formatter.getWidth(),
            "No " + type.toString().toLowerCase() + " with name "
            + invalidName + " found. Available:");
        }
      }

      for (OwlModule<?> settings : settingsCollection) {
        String name = settings.key();
        String description = settings.description();
        if (Strings.isNullOrEmpty(description)) {
          formatter.printWrapped(writer, formatter.getWidth(), name);
        } else {
          formatter.printWrapped(writer, formatter.getWidth(), 4, name + ": " + description);
        }
      }
    });
  }

  static void printModuleHelp(OwlModule<?> settings, @Nullable String reason) {
    Type type = DEFAULT_REGISTRY.type(settings);
    printGuarded(writer -> {
      String moduleName = settings.key();
      if (reason != null) {
        formatter.printWrapped(writer, formatter.getWidth(), "Failed to parse settings for the "
          + type.toString().toLowerCase() + ' ' + moduleName + ". Reason: " + reason);
      }

      Options options = settings.options();
      if (options.getOptions().isEmpty()) {
        formatter.printWrapped(writer, formatter.getWidth(),
          "The " + type.toString().toLowerCase() + ' ' + moduleName
          + " has no settings.");
      } else {
        formatter.printWrapped(writer, formatter.getWidth(),
          "Available settings for the " + type.toString().toLowerCase() + ' ' + moduleName
            + " are:");
        formatter.printHelp(writer, formatter.getWidth(), moduleName, "    "
          + settings.description(), options, 4, 2, null, true);
      }
    });
  }

  static void printHelp(String name, Options options, @Nullable String reason) {
    printGuarded(writer -> {
      if (reason != null) {
        formatter.printWrapped(writer, formatter.getWidth(), "Failed to parse " + name
          + " options. Reason: " + reason);
      }
      if (!options.getOptions().isEmpty()) {
        formatter.printHelp(writer, formatter.getWidth(), "Available options: ", null, options,
          4, 2, null, true);
      }
    });
  }

  static void printHelp(String name, Options options) {
    printHelp(name, options, null);
  }

  @SuppressWarnings("PMD.SystemPrintln")
  static void println() {
    System.err.println();
  }

  static void println(String text) {
    printGuarded(writer -> {
      formatter.printWrapped(writer, formatter.getWidth(), text);
      writer.println();
    });
  }

  private static void printGuarded(Consumer<PrintWriter> print) {
    try (PrintWriter pw = new PrintWriter(GuardedStream.syserr)) {
      print.accept(pw);
    }
  }
}
