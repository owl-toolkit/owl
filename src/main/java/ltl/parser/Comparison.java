package ltl.parser;

public enum Comparison {
    LEQ {
        @Override
        public String toString() {
            return "<=";
        }
    },
    LT {
        @Override
        public String toString() {
            return "<";
        }
    },
    GEQ {
        @Override
        public String toString() {
            return ">=";
        }
    },
    GT {
        @Override
        public String toString() {
            return ">";
        }
    };
}