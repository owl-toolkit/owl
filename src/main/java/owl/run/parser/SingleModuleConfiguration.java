package owl.run.parser;

import java.util.List;
import org.immutables.value.Value;
import owl.run.ModuleSettings;
import owl.run.ModuleSettings.ReaderSettings;
import owl.run.ModuleSettings.WriterSettings;
import owl.run.Transformer;
import owl.run.env.EnvironmentSettings;

@Value.Immutable
public abstract class SingleModuleConfiguration {
  @Value.Default
  public EnvironmentSettings environmentSettings() {
    return new EnvironmentSettings();
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

  public abstract ReaderSettings readerModule();

  public abstract ModuleSettings.TransformerSettings transformer();

  public abstract WriterSettings writerModule();
}
