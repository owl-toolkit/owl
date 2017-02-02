package owl.factories.jdd.bdd;

public final class BddFactory {
  private BddFactory() {
  }

  public static Bdd buildBdd(final int nodeSize) {
    return new BddImpl(nodeSize);
  }

  public static Bdd buildBdd(final int nodeSize, final BddConfiguration configuration) {
    return new BddImpl(nodeSize, configuration);
  }
}
