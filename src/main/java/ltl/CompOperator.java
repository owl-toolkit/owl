package ltl;

public enum CompOperator {
    LEQ, LT, GEQ, GT;

    @Override
    public String toString() {
        switch (this) {
            case GEQ:
                return ">=";
            case GT:
                return ">";
            case LEQ:
                return "<=";
            case LT:
                return "<";
            default:
                throw new AssertionError();

        }
    }

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

    protected CompOperator negate2() {
        switch (this) {
            case GEQ:
                return CompOperator.GT;
            case GT:
                return CompOperator.GEQ;
            case LEQ:
                return CompOperator.LT;
            case LT:
                return CompOperator.LEQ;
            default:
                throw new AssertionError();

        }
    }
}