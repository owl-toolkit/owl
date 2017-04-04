package owl.translations;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class CommandLineInput<T> {
  public final T input;
  public final List<String> variables;

  public CommandLineInput(T input, List<String> variables) {
    this.input = input;
    this.variables = ImmutableList.copyOf(variables);
  }
}
