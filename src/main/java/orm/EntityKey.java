package orm;

public record EntityKey<T extends BaseEntity>(Object id, Class<T> type) {
}
