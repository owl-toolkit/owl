package owl.translations.frequency;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.output.HoaPrinter;

final class HoaUtil {
  private HoaUtil() {}

  public static String toHoa(Automaton<?, ?> printable) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HOAConsumerPrint hoa = new HOAConsumerPrint(writer);
    printable.toHoa(hoa, EnumSet.of(HoaPrinter.HoaOption.ANNOTATIONS));
    return new String(writer.toByteArray(), StandardCharsets.UTF_8);
  }
}
