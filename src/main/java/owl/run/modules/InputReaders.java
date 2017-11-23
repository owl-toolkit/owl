package owl.run.modules;

import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import java.io.Reader;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jhoafparser.consumer.HOAConsumerFactory;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.HOAFParserSettings;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.transformations.ToTransitionAcceptance;
import owl.automaton.AutomatonReader;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;
import owl.run.Environment;
import owl.run.modules.ModuleSettings.ReaderSettings;

public final class InputReaders {
  private static final Logger logger = Logger.getLogger(InputReaders.class.getName());

  public static final InputReader HOA = HoaReader.DEFAULT;
  public static final ReaderSettings HOA_SETTINGS = ImmutableReaderSettings.builder()
    .key("hoa")
    .description("Parses automata given in HOA format, converting them to transition based "
      + "acceptance if necessary")
    .inputSettingsParser(settings -> {
      HOAFParserSettings hoafParserSettings = new HOAFParserSettings();
      hoafParserSettings.setFlagValidate(false);
      return new HoaReader(hoafParserSettings);
    }).build();

  public static final InputReader TLSF = (reader, callback, env) -> {
    Tlsf tlsf = TlsfParser.parse(reader);
    LabelledFormula formula = tlsf.toFormula();
    callback.accept(formula);
  };
  public static final ReaderSettings TLSF_SETTINGS = ImmutableReaderSettings.builder()
    .key("tlsf")
    .description("Parses a single TLSF instance and converts it to an LTL formula")
    .inputSettingsParser(settings -> TLSF).build();

  public static final InputReader LTL = (reader, callback, env) ->
    CharStreams.readLines(reader, new LineProcessor<Void>() {
      @Override
      public boolean processLine(String line) {
        if (env.isShutdown()) {
          return false;
        }
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
  public static final ReaderSettings LTL_SETTINGS = ImmutableReaderSettings.builder()
    .key("ltl")
    .description("Parses LTL formulas and converts them into NNF")
    .inputSettingsParser(settings -> LTL).build();

  private InputReaders() {}

  public static final class HoaReader implements InputReader {
    public static final HoaReader DEFAULT = new HoaReader();

    private final HOAFParserSettings hoafParserSettings;

    public HoaReader() {
      this(new HOAFParserSettings());
    }

    public HoaReader(HOAFParserSettings parserSettings) {
      this.hoafParserSettings = parserSettings;
    }

    @Override
    public void run(Reader reader, Consumer<Object> callback, Environment env)
      throws ParseException {
      HOAConsumerFactory factory = () ->
        new ToTransitionAcceptance(AutomatonReader.getConsumer(callback, env.factorySupplier()));
      HOAFParser.parseHOA(reader, factory, hoafParserSettings);
    }
  }
}
