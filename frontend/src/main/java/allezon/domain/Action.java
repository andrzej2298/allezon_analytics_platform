package allezon.domain;

public enum Action {
    VIEW, BUY;

    @Override
    public String toString() {
      switch(this) {
        case VIEW: return "views";
        case BUY: return "buys";
        default: throw new IllegalArgumentException();
      }
    }

    public String toStringUpper() {
      switch(this) {
        case VIEW: return "VIEW";
        case BUY: return "BUY";
        default: throw new IllegalArgumentException();
      }
    }
}
