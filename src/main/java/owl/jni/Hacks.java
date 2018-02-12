package owl.jni;

import com.google.common.collect.ImmutableList;
import java.util.stream.IntStream;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.Collector;

final class Hacks {
  private Hacks() {}

  static LabelledFormula attachDummyAlphabet(Formula formula) {
    int largestAtom = Collector.collectAtoms(formula).stream().max().orElse(0);
    return LabelledFormula.of(formula, IntStream
      .range(0, largestAtom + 1)
      .mapToObj(i -> "p" + i)
      .collect(ImmutableList.toImmutableList()));
  }
}
