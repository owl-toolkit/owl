package owl.bdd;

public final class BDDFactory {
  private BDDFactory() {
  }

  public static BDD buildBDD(int nodeSize) {
    return new BDDImpl(nodeSize);
  }
}
