package owl.cli;

import org.apache.commons.cli.ParseException;

@FunctionalInterface
public interface ParseFunction<K, V> {
  V parse(K input) throws ParseException;
}
