package ltl;

public enum CompOperator {
    LEQ, LT, GEQ, GT;

    protected CompOperator negate() {
        switch (this) {
            case GEQ:
                return CompOperator.LT;
            case GT:
                return CompOperator.LEQ;
            case LEQ:
                return CompOperator.GT;
            case LT:
                return CompOperator.GEQ;
            default:
                throw new AssertionError();

        }
    }

    protected CompOperatorFrequencyG toCompOperatorFrequencyG() {
        switch (this) {
            case GEQ:
                return CompOperatorFrequencyG.GEQ;
            case GT:
                return CompOperatorFrequencyG.GT;
            default:
                throw new AssertionError();
        }
    }
}