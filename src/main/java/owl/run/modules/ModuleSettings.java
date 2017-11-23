package owl.run.modules;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface ModuleSettings<M extends OwlModule> {
  default String getDescription() {
    return "";
  }

  String getKey();

  default Options getOptions() {
    return new Options();
  }

  M parse(CommandLine settings) throws ParseException;

  interface ReaderSettings extends ModuleSettings<InputReader> {
  }

  interface TransformerSettings extends ModuleSettings<Transformer> {
  }

  interface WriterSettings extends ModuleSettings<OutputWriter> {
  }
}
