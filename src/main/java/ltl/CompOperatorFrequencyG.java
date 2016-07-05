package ltl;

public enum CompOperatorFrequencyG {
    GEQ, GT;

    public CompOperatorFrequencyG negate() {
        switch (this) {
            case GEQ:
                return CompOperatorFrequencyG.GT;
            case GT:
                return CompOperatorFrequencyG.GEQ;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public String toString() {
        switch (this) {
            case GEQ:
                return ">=";
            case GT:
                return ">";
            default:
                throw new AssertionError();
        }
    }
}
