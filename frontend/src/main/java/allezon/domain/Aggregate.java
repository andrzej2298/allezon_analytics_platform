package allezon.domain;

public enum Aggregate {
    COUNT,
    SUM_PRICE;

    @Override
    public String toString() {
      switch(this) {
        case COUNT: return "count";
        case SUM_PRICE: return "sum_price";
        default: throw new IllegalArgumentException();
      }
    }
}
