package owl.factories.jdd.bdd;

public final class BddFactory {
  private BddFactory() {
  }

  public static Bdd buildBdd(int nodeSize) {
    return new BddImpl(nodeSize);
  }

  public static Bdd buildBdd(int nodeSize, BddConfiguration configuration) {
    return new BddImpl(nodeSize, configuration);
  }
}
