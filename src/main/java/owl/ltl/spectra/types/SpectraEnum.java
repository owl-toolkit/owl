package owl.ltl.spectra.types;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.spectra.constants.SpectraEnumConstant;

public class SpectraEnum implements SpectraType {
  private final int width;
  private final List<String> values;

  public SpectraEnum(List<String> values) {
    this.width = (int) Math.ceil(Math.log(values.size()) / Math.log(2));
    this.values = new ArrayList<>(values);
  }

  public List<String> getValues() {
    return values;
  }

  @Override
  public SpectraEnumConstant of(String value) {
    int index = values.indexOf(value);
    if (index == -1) {
      throw new ParseCancellationException(value + " is not a valid value for this enum");
    } else {
      return new SpectraEnumConstant(this, index);
    }
  }

  @Override
  public int width() {
    return width;
  }
}
