package owl.run.modules;

import com.google.common.base.Throwables;
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
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.automaton.AutomatonReader;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;
import owl.run.Environment;
import owl.run.PipelineException;
import owl.run.modules.OwlModuleParser.ReaderParser;

public final class InputReaders {
  static final Logger logger = Logger.getLogger(InputReaders.class.getName());

  public static final InputReader HOA = HoaReader.DEFAULT;
  public static final ReaderParser HOA_CLI = ImmutableReaderParser.builder()
    .key("hoa")
    .description("Parses automata given in HOA format, converting them to transition based "
      + "acceptance if necessary")
    .parser(settings -> {
      HOAFParserSettings hoafParserSettings = new HOAFParserSettings();
      hoafParserSettings.setFlagValidate(false);
      return new HoaReader(hoafParserSettings);
    }).build();

  public static final InputReader TLSF = (reader, env, callback) -> {
    Tlsf tlsf = TlsfParser.parse(reader);
    LabelledFormula formula = tlsf.toFormula();
    callback.accept(formula);
  };

  public static final InputReader LTL = (reader, env, callback) ->
    CharStreams.readLines(reader, new LineProcessor<Void>() {
      @Override
      public boolean processLine(String line) {
        if (env.isShutdown()) {
          return false;
        }

        if (line.isEmpty()) {
          return true;
        }

        LabelledFormula formula;
        try {
          formula = LtlParser.parse(line);
        } catch (RecognitionException | ParseCancellationException e) {
          throw new PipelineException("Failed to parse LTL formula " + line, e);
        }
        logger.log(Level.FINE, "Read formula {0} from line {1}", new Object[] {formula, line});
        callback.accept(formula);
        return true;
      }

      @Override
      public Void getResult() {
        return null;
      }
    });

  public static final ReaderParser LTL_CLI = ImmutableReaderParser.builder()
    .key("ltl")
    .description("Parses LTL formulas and converts them into NNF")
    .parser(settings -> LTL).build();


  public static final ReaderParser TLSF_CLI = ImmutableReaderParser.builder()
    .key("tlsf")
    .description("Parses a single TLSF instance and converts it to an LTL formula")
    .parser(settings -> TLSF).build();

  @SuppressWarnings({"ProhibitedExceptionThrown","PMD.AvoidCatchingGenericException",
                      "PMD.AvoidThrowingRawExceptionTypes"})
  public static Consumer<Object> checkedCallback(CheckedCallback consumer) {
    return input -> {
      try {
        consumer.accept(input);
      } catch (Exception e) {
        Throwables.throwIfUnchecked(e);
        throw new RuntimeException(e);
      }
    };
  }

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
    public void run(Reader reader, Environment env, Consumer<Object> callback) {
      HOAConsumerFactory factory = () ->
        new ToTransitionAcceptance(AutomatonReader.getConsumer(callback, env.factorySupplier()));
      try {
        HOAFParser.parseHOA(reader, factory, hoafParserSettings);
      } catch (ParseException e) {
        throw new PipelineException("Failed to parse input automaton", e);
      }
    }
  }

  @SuppressWarnings({"ProhibitedExceptionDeclared", "PMD.SignatureDeclareThrowsException"})
  @FunctionalInterface
  public interface CheckedCallback {
    void accept(Object input) throws Exception;
  }
}
