package owl.ltl.parser.spectra.types;

import java.util.Arrays;
import java.util.stream.IntStream;

import owl.ltl.parser.spectra.expressios.HigherOrderExpression;

public class SpectraArray implements SpectraType {
  private final int width;
  private final int[] dimensions;
  private final int[] dimEnc;
  private final SpectraType component;

  public SpectraArray(SpectraType component, int[] dims) {
    if (component instanceof SpectraArray) {
      SpectraArray origin = (SpectraArray) component;
      this.dimEnc = Arrays.copyOf(origin.dimEnc, origin.dimEnc.length + dims.length);
      constructDimEnc(origin.dimEnc.length, dims);
      this.dimensions = IntStream.concat(Arrays.stream(origin.dimensions),
        Arrays.stream(dims)).toArray();
      this.width = dimEnc[origin.dimensions.length] * origin.width();
      this.component = origin.component;
    } else {
      this.dimEnc = new int[dims.length];
      constructDimEnc(0, dims);
      this.dimensions = Arrays.copyOf(dims, dims.length);
      this.width = dimEnc[0] * component.width();
      this.component = component;
    }
  }

  private void constructDimEnc(int start, int[] dims) {
    Arrays.fill(dimEnc, start, dimEnc.length, 1);
    for (int i = start; i < dimEnc.length; i++) {
      int dim = dims[i];
      for (int j = 0; j <= i; j++) {
        dimEnc[j] *= dim;
      }
    }
  }

  public int getEnc(int[] indices) {
    int index = 0;
    for (int i = 0; i < indices.length; i++) {
      index += (dimEnc[i] * indices[i]);
    }
    return index;
  }

  public SpectraType getComponent() {
    return component;
  }

  public int[] getDimensions() {
    return Arrays.copyOf(dimensions, dimensions.length);
  }

  @Override
  public HigherOrderExpression of(String value) {
    return component.of(value);
  }

  @Override
  public int width() {
    return width;
  }
}
