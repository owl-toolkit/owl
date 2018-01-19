package owl.run.modules;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface OwlModuleParser<M extends OwlModule> {
  default String getDescription() {
    return "";
  }

  String getKey();

  default Options getOptions() {
    return new Options();
  }

  M parse(CommandLine commandLine) throws ParseException;

  interface ReaderParser extends OwlModuleParser<InputReader> {}

  interface TransformerParser extends OwlModuleParser<Transformer> {}

  interface WriterParser extends OwlModuleParser<OutputWriter> {}
}
