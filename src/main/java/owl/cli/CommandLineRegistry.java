package owl.cli;

import static owl.cli.ModuleSettings.CoordinatorSettings;
import static owl.cli.ModuleSettings.InputSettings;
import static owl.cli.ModuleSettings.OutputSettings;
import static owl.cli.ModuleSettings.TransformerSettings;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.cli.env.EnvironmentSettings;

/**
 * A registry holding all modules used to parse the command line. These can be dynamically
 * registered to allow for flexible parsing of command lines.
 *
 * @see owl.cli.parser.CliParser
 */
public class CommandLineRegistry {
  private final EnvironmentSettings environmentSettings;
  private final Table<Type, String, ModuleSettings> registeredModules = HashBasedTable.create();

  protected CommandLineRegistry(EnvironmentSettings environmentSettings) {
    this.environmentSettings = environmentSettings;
  }

  public Collection<CoordinatorSettings> getAllCoordinatorSettings() {
    return registeredModules.row(Type.COORDINATOR).values().stream()
      .map(CoordinatorSettings.class::cast)
      .collect(Collectors.toSet());
  }

  public Collection<InputSettings> getAllInputSettings() {
    return registeredModules.row(Type.INPUT).values().stream()
      .map(InputSettings.class::cast)
      .collect(Collectors.toSet());
  }

  public Collection<OutputSettings> getAllOutputSettings() {
    return registeredModules.row(Type.OUTPUT).values().stream()
      .map(OutputSettings.class::cast)
      .collect(Collectors.toSet());
  }

  public Collection<TransformerSettings> getAllTransformerSettings() {
    return registeredModules.row(Type.TRANSFORMER).values().stream()
      .map(TransformerSettings.class::cast)
      .collect(Collectors.toSet());
  }

  @Nullable
  public CoordinatorSettings getCoordinatorSettings(String name) {
    return (CoordinatorSettings) getType(Type.COORDINATOR, name);
  }

  public EnvironmentSettings getEnvironmentSettings() {
    return environmentSettings;
  }

  @Nullable
  public InputSettings getInputSettings(String name) {
    return (InputSettings) getType(Type.INPUT, name);
  }

  @Nullable
  public OutputSettings getOutputSettings(String name) {
    return (OutputSettings) getType(Type.OUTPUT, name);
  }

  @Nullable
  public TransformerSettings getTransformerSettings(String name) {
    return (TransformerSettings) getType(Type.TRANSFORMER, name);
  }

  @Nullable
  private ModuleSettings getType(Type type, String name) {
    @Nullable
    ModuleSettings moduleSettings = registeredModules.get(type, name);
    assert moduleSettings == null || type.typeClass.isInstance(moduleSettings);
    return moduleSettings;
  }

  void register(ModuleSettings... settings) {
    register(Arrays.asList(settings));
  }

  public void register(ModuleSettings description) {
    Collection<Type> types = Type.getTypes(description);
    if (types.isEmpty()) {
      throw new IllegalArgumentException("Unknown settings type " + description.getClass());
    }

    String name = description.getKey();
    for (Type type : types) {
      if (registeredModules.contains(type, name)) {
        throw new IllegalArgumentException(
          String.format("Some module with name %s and type %s is already registered", name, type));
      }
    }

    types.forEach(type -> registeredModules.put(type, name, description));
  }

  public void register(Collection<ModuleSettings> settings) {
    settings.forEach(this::register);
  }

  public Set<ModuleSettings> remove(String name) {
    // Column map isn't really efficient but we have very few row keys anyway
    return new HashSet<>(registeredModules.columnMap().remove(name).values());
  }

  private enum Type {
    INPUT(InputSettings.class), OUTPUT(OutputSettings.class),
    TRANSFORMER(TransformerSettings.class), COORDINATOR(CoordinatorSettings.class);

    public final Class<?> typeClass;

    Type(Class<?> typeClass) {
      this.typeClass = typeClass;
    }

    public static Collection<Type> getTypes(ModuleSettings object) {
      return Arrays.stream(Type.values())
        .filter(type -> type.typeClass.isInstance(object))
        .collect(Collectors.toList());
    }
  }
}
