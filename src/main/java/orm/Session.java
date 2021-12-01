package orm;

import annotation.Column;
import annotation.Table;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class Session {

    private final DataSource dataSource;
    private Map<EntityKey<?>, BaseEntity> store = new HashMap<>();
    private final Map<EntityKey<?>, List<Object>> snapshot = new HashMap<>();

    @SneakyThrows
    public <T extends BaseEntity> T find(Class<T> entityType, Long id) {
        String tableName = entityType.getDeclaredAnnotation(Table.class).name();
        EntityKey<T> key = new EntityKey<>(id, entityType);

        if (store.get(key) == null) {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement preparedStatement =
                             connection.prepareStatement("select * from " + tableName + " where id = ?")) {
                    preparedStatement.setObject(1, id);

                    ResultSet resultSet = preparedStatement.executeQuery();
                    T entity = parseResultSet(entityType, resultSet);

                    store.put(key, entity);
                    snapshot.put(key, getFieldValues(entity));
                    return entity;
                }
            }
        } else {
            return loadFromCache(key);
        }
    }

    @SneakyThrows
    public void update(BaseEntity entity) {
        String tableName = entity.getClass().getDeclaredAnnotation(Table.class).name();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement preparedStatement =
                         connection.prepareStatement("update " + tableName + " set " + getFieldsToBeChangedAsPartOfQuery(entity) +
                                 " where id = ?")) {
                preparedStatement.setObject(1, entity.getId());
                preparedStatement.executeUpdate();
            }
        }
    }

    public void close() {
        collectChangedEntities().forEach(this::update);
        store.clear();
        snapshot.clear();
    }

    private List<BaseEntity> collectChangedEntities() {
        return store.entrySet().stream()
                    .filter(this::isChanged)
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
    }

    private String getFieldsToBeChangedAsPartOfQuery(BaseEntity entity) {
        return Arrays.stream(entity.getClass().getDeclaredFields())
                     .map(field -> {
                         try {
                             field.setAccessible(true);
                             return getColumnName(field).orElse(field.getName()) + "=" + "'" + field.get(entity) + "'";

                         } catch (IllegalAccessException e) {
                             e.printStackTrace();
                         }
                         return null;
                     }).filter(Objects::nonNull)
                     .collect(Collectors.joining(", "));
    }

    @SneakyThrows
    private List<Object> getFieldValues(BaseEntity entity) {
        List<Object> fieldValues = new ArrayList<>();

        List<Field> sortedFields = Arrays.stream(entity.getClass().getDeclaredFields())
                                         .sorted(Comparator.comparing(Field::getName)).collect(Collectors.toList());

        for (Field field : sortedFields) {
            field.setAccessible(true);
            fieldValues.add(field.get(entity));
        }

        return fieldValues;
    }

    private boolean isChanged(Map.Entry<EntityKey<?>, BaseEntity> cachedObject) {
        List<Object> currentFieldValues = getFieldValues(cachedObject.getValue());
        List<Object> snapshotFieldValues = snapshot.get(cachedObject.getKey());

        for (int i = 0; i < currentFieldValues.size(); i++) {
            if (!currentFieldValues.get(i).equals(snapshotFieldValues.get(i))) {
                return true;
            }
        }
        return false;
    }

    private <T extends BaseEntity> T loadFromCache(EntityKey<T> key) {
        return Optional.ofNullable(store.get(key)).filter(o -> o.getClass().isInstance(key.type()))
                       .map(key.type()::cast).orElseThrow(() -> new RuntimeException(String.format("Can`t get %s " +
                        "with id = %s from cache", key.type().getName(), key.id())));
    }

    @SneakyThrows
    private <T extends BaseEntity> T parseResultSet(Class<T> type, ResultSet resultSet) {
        resultSet.next();
        T entity = type.getDeclaredConstructor().newInstance();

        for (Field field : getFieldsFromObject(type)) {
            field.setAccessible(true);
            Optional<String> columnName = getColumnName(field);
            if (columnName.isPresent()) {
                field.set(entity, resultSet.getObject(columnName.get()));
            } else {
                field.set(entity, resultSet.getObject(field.getName()));
            }
        }
        return entity;
    }

    private List<Field> getFieldsFromObject(Class<?> objType) {
        return Stream.of(Arrays.asList(objType.getDeclaredFields()),
                             Arrays.asList(objType.getSuperclass().getDeclaredFields()))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toList());
    }

    private Optional<String> getColumnName(Field field) {
        return Optional.ofNullable(field.getAnnotation(Column.class))
                       .map(Column::name);
    }
}
