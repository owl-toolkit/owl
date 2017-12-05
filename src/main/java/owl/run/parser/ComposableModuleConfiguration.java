package owl.run.parser;

import java.util.List;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import owl.run.ModuleSettings;
import owl.run.ModuleSettings.ReaderSettings;
import owl.run.ModuleSettings.WriterSettings;
import owl.run.Transformer;
import owl.run.coordinator.SingleStreamCoordinator;
import owl.run.env.DefaultEnvironmentSettings;
import owl.run.env.EnvironmentSettings;

@Value.Immutable
public abstract class ComposableModuleConfiguration {
  @Value.Default
  public ModuleSettings.CoordinatorSettings coordinatorSettings() {
    return SingleStreamCoordinator.settings;
  }

  @Value.Default
  public EnvironmentSettings environmentSettings() {
    return new DefaultEnvironmentSettings();
  }

  @Nullable
  @Value.Default
  public ReaderSettings readerModule() {
    return null;
  }

  @Nullable
  @Value.Default
  public WriterSettings writerModule() {
    return null;
  }

  @Value.Default
  public boolean passNonOptionToCoordinator() {
    return true;
  }

  @Value.Default
  public List<Transformer> postProcessors() {
    return List.of();
  }

  @Value.Default
  public List<Transformer> preProcessors() {
    return List.of();
  }

  public abstract ModuleSettings.TransformerSettings transformer();
}
