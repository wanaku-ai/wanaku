package ai.wanaku.core.persistence.api;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import ai.wanaku.capabilities.sdk.api.types.WanakuEntity;

public interface WanakuRepository<A extends WanakuEntity<K>, K, C> {

    A persist(A entity);

    List<A> listAll();

    boolean deleteById(C id);

    A findById(C id);

    boolean update(C id, A entity);

    boolean remove(Predicate<A> matching);

    int size();

    int removeByField(String fieldName, Object fieldValue);

    int removeByFields(Map<String, Object> fields);

    int removeAll();

    boolean exists(C key);
}
