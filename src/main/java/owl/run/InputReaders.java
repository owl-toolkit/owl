package owl.run;

import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jhoafparser.consumer.HOAConsumerFactory;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.HOAFParserSettings;
import jhoafparser.transformations.ToTransitionAcceptance;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import owl.automaton.AutomatonReader;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;
import owl.run.ModuleSettings.ReaderSettings;
import owl.run.env.Environment;

public final class InputReaders {

  public static final ReaderSettings HOA = new ReaderSettings() {
    @Override
    public InputReader create(CommandLine settings, Environment environment)
      throws ParseException {
      HOAFParserSettings hoafParserSettings = new HOAFParserSettings();
      hoafParserSettings.setFlagValidate(false);
      return (reader, callback) -> {
        HOAConsumerFactory factory = () -> new ToTransitionAcceptance(
          AutomatonReader.getConsumer(callback, environment.factorySupplier()));
        HOAFParser.parseHOA(reader, factory, hoafParserSettings);
      };
    }

    @Override
    public String getDescription() {
      return "Parses automata given in HOA format";
    }

    @Override
    public String getKey() {
      return "hoa";
    }
  };

  public static final ReaderSettings TLSF = new ReaderSettings() {
    @Override
    public InputReader create(CommandLine settings, Environment environment)
      throws ParseException {
      return this::read;
    }

    @Override
    public String getDescription() {
      return "Parses a single TLSF instance and converts it to an LTL formula";
    }

    @Override
    public String getKey() {
      return "tlsf";
    }

    private void read(Reader reader, Consumer<Object> callback) throws Exception {
      Tlsf tlsf = TlsfParser.parse(reader);
      LabelledFormula formula = tlsf.toFormula();
      callback.accept(formula);
    }
  };

  public static final ReaderSettings LTL = new ReaderSettings() {
    @Override
    public InputReader create(CommandLine settings, Environment environment)
      throws ParseException {
      return this::read;
    }

    @Override
    public String getDescription() {
      return "Parses LTL formulas";
    }

    @Override
    public String getKey() {
      return "ltl";
    }

    private void read(Reader reader, Consumer<Object> callback) throws Exception {
      CharStreams.readLines(reader, new LineProcessor<Void>() {
        @Override
        public boolean processLine(String line) throws IOException {
          if (line.isEmpty()) {
            return true;
          }

          LabelledFormula formula = LtlParser.parse(line);
          logger.log(Level.FINE, "Read formula {0} from line {1}", new Object[] {formula, line});
          callback.accept(formula);
          return true;
        }

        @Override
        public Void getResult() {
          return null;
        }
      });
    }
  };

  private static final Logger logger = Logger.getLogger(InputReaders.class.getName());

  InputReaders() {}
}
