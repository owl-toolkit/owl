package owl.run;

import static owl.run.ModuleSettings.CoordinatorSettings;
import static owl.run.ModuleSettings.TransformerSettings;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.run.ModuleSettings.ReaderSettings;
import owl.run.ModuleSettings.WriterSettings;
import owl.run.env.EnvironmentSettings;

/**
 * A registry holding all modules used to parse the command line. These can be dynamically
 * registered to allow for flexible parsing of command lines.
 *
 * @see owl.run.parser.CliParser
 */
public class CommandLineRegistry {
  private final EnvironmentSettings environmentSettings;
  private final Table<Type, String, ModuleSettings> registeredModules = HashBasedTable.create();

  CommandLineRegistry(EnvironmentSettings environmentSettings) {
    this.environmentSettings = environmentSettings;
  }

  public Collection<CoordinatorSettings> getCoordinatorSettings() {
    return registeredModules.row(Type.COORDINATOR).values().stream()
      .map(CoordinatorSettings.class::cast)
      .collect(Collectors.toSet());
  }

  public Collection<ReaderSettings> getReaderSettings() {
    return registeredModules.row(Type.READER).values().stream()
      .map(ReaderSettings.class::cast)
      .collect(Collectors.toSet());
  }

  public Collection<WriterSettings> getWriterSettings() {
    return registeredModules.row(Type.WRITER).values().stream()
      .map(WriterSettings.class::cast)
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
  public ReaderSettings getReaderSettings(String name) {
    return (ReaderSettings) getType(Type.READER, name);
  }

  @Nullable
  public WriterSettings getWriterSettings(String name) {
    return (WriterSettings) getType(Type.WRITER, name);
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

  public void register(ModuleSettings... settings) {
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
    READER(ReaderSettings.class), WRITER(WriterSettings.class),
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
