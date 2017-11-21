package owl.run;

import org.apache.commons.cli.ParseException;

@FunctionalInterface
public interface BiParseFunction<K, K2, V> {
  V parse(K input, K2 input2) throws ParseException;
}
