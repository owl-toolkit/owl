package owl.bdd;

public final class BddFactory {
  private BddFactory() {
  }

  public static Bdd buildBdd(final int nodeSize) {
    return new BddImpl(nodeSize);
  }
}
