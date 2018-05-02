package owl.ltl;

public abstract class AbstractFormula implements Formula {
  private int hashCode = 0;

  @SuppressWarnings("NonFinalFieldReferenceInEquals")
  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }

    AbstractFormula other = (AbstractFormula) o;
    return (hashCode == 0 || other.hashCode == 0 || other.hashCode == hashCode) && equals2(other);
  }

  @Override
  public final int hashCode() {
    if (hashCode == 0) {
      hashCode = hashCodeOnce();
    }

    return hashCode;
  }

  protected abstract boolean equals2(AbstractFormula o);

  protected abstract int hashCodeOnce();
}
