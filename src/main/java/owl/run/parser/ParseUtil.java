package owl.run.parser;

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
import owl.run.modules.ModuleRegistry;
import owl.run.modules.ModuleSettings;
import owl.util.UncloseableWriter;

final class ParseUtil {
  public static final Comparator<ModuleSettings<?>> MODULE_COMPARATOR =
    Comparator.comparing(ModuleSettings::getKey);
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

  static Collection<ModuleSettings<?>> getSortedSettings(ModuleRegistry registry,
    ModuleRegistry.Type type) {
    return registry.getSettings(type).stream()
      .sorted(MODULE_COMPARATOR)
      .collect(Collectors.toList());
  }

  static void printModuleHelp(ModuleSettings<?> settings, @Nullable String reason) {
    printGuarded(writer -> {
      if (reason != null) {
        formatter.printWrapped(writer, formatter.getWidth(), "Failed to parse settings for "
          + "module " + settings.getKey() + ". Reason: " + reason);
      }
      formatter.printWrapped(writer, formatter.getWidth(),
        "Available settings for " + settings.getKey() + " are:");
      formatter.printHelp(writer, formatter.getWidth(), settings.getKey(), "    "
        + settings.getDescription(), settings.getOptions(), 4, 2, null, true);
    });
  }

  static void printList(ModuleRegistry.Type type, Collection<ModuleSettings<?>> settingsCollection,
    @Nullable String invalidName) {
    printGuarded(writer -> {
      if (invalidName != null) {
        formatter.printWrapped(writer, formatter.getWidth(), "No module of type "
          + type.name + " with name " + invalidName + " found. Available:");
      } else {
        formatter.printWrapped(writer, formatter.getWidth(), "All "
          + type.name + " modules: ");
      }

      for (ModuleSettings<?> settings : settingsCollection) {
        String description = settings.getDescription();
        if (Strings.isNullOrEmpty(description)) {
          formatter.printWrapped(writer, formatter.getWidth(), settings.getKey());
        } else {
          formatter.printWrapped(writer, formatter.getWidth(), 4,
            settings.getKey() + ": " + description);
        }
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
    try (PrintWriter pw = new PrintWriter(UncloseableWriter.syserr)) {
      print.accept(pw);
    }
  }

  static String[] toArray(List<String> list) {
    return list.toArray(new String[list.size()]);
  }
}
