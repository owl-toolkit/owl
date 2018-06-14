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

package owl.run.parser;

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
import owl.run.modules.OwlModuleParser;
import owl.run.modules.OwlModuleRegistry;
import owl.util.GuardedStream;

final class ParseUtil {
  public static final Comparator<OwlModuleParser<?>> MODULE_COMPARATOR =
    Comparator.comparing(OwlModuleParser::getKey);
  private static final HelpFormatter formatter;
  private static final String[] EMPTY = new String[0];

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

  static Collection<OwlModuleParser<?>> getSortedSettings(OwlModuleRegistry registry, Type type) {
    return registry.getAllOfType(type).stream()
      .sorted(MODULE_COMPARATOR)
      .collect(Collectors.toList());
  }

  static void printList(Collection<OwlModuleParser<?>> settingsCollection,
    @Nullable Type type, @Nullable String invalidName) {
    printGuarded(writer -> {
      if (invalidName == null) {
        if (type == null) {
          formatter.printWrapped(writer, formatter.getWidth(), "All modules: ");
        } else {
          formatter.printWrapped(writer, formatter.getWidth(), "All " + type.name + "s: ");
        }
      } else {
        if (type == null) {
          formatter.printWrapped(writer, formatter.getWidth(), "No module with name "
            + invalidName + " found. Available:");
        } else {
          formatter.printWrapped(writer, formatter.getWidth(), "No " + type.name + " with name "
            + invalidName + " found. Available:");
        }
      }

      for (OwlModuleParser<?> settings : settingsCollection) {
        String name = settings.getKey();
        String description = settings.getDescription();
        if (Strings.isNullOrEmpty(description)) {
          formatter.printWrapped(writer, formatter.getWidth(), name);
        } else {
          formatter.printWrapped(writer, formatter.getWidth(), 4, name + ": " + description);
        }
      }
    });
  }

  static void printModuleHelp(OwlModuleParser<?> settings, @Nullable String reason) {
    Type type = Type.of(settings);
    printGuarded(writer -> {
      String moduleName = settings.getKey();
      if (reason != null) {
        formatter.printWrapped(writer, formatter.getWidth(), "Failed to parse settings for the "
          + type.name + ' ' + moduleName + ". Reason: " + reason);
      }

      Options options = settings.getOptions();
      if (options.getOptions().isEmpty()) {
        formatter.printWrapped(writer, formatter.getWidth(), "The " + type.name + ' ' + moduleName
          + " has no settings.");
      } else {
        formatter.printWrapped(writer, formatter.getWidth(),
          "Available settings for the " + type.name + ' ' + moduleName + " are:");
        formatter.printHelp(writer, formatter.getWidth(), moduleName, "    "
          + settings.getDescription(), options, 4, 2, null, true);
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

  static String[] toArray(List<String> list) {
    return list.toArray(EMPTY);
  }
}
