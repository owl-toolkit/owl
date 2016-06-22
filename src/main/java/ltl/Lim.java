package ltl;

public enum Lim {
    SUP, INF;

    public Lim theOther() {
        if (this == SUP)
            return INF;
        return SUP;
    }

    @Override
    public String toString() {
        if (this == SUP) {
            return "sup";
        }
        return "inf";
    }
}
