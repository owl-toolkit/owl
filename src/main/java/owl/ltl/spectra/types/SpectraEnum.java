package owl.ltl.spectra.types;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.spectra.expressions.constants.SpectraEnumConstant;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SpectraEnum spectraEnum = (SpectraEnum) o;

    return width == spectraEnum.width() && values.equals(spectraEnum.getValues());
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + width;
    result = 31 * result + values.hashCode();

    return result;
  }
}
