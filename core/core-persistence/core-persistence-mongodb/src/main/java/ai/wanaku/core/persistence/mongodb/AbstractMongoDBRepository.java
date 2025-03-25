package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.core.persistence.types.WanakuEntity;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMongoDBRepository<A, T extends WanakuEntity, K> implements WanakuRepository<A, T, K> {

    protected MongoClient mongoClient;
    protected WanakuMarshallerService wanakuMarshallerService;

    public AbstractMongoDBRepository(MongoClient mongoClient, WanakuMarshallerService wanakuMarshallerService) {
        this.mongoClient = mongoClient;
        this.wanakuMarshallerService = wanakuMarshallerService;
    }

    @Override
    public void persist(A model) {
        T entity = convertToEntity(model);
        String marshalled = wanakuMarshallerService.marshal(entity);
        Document document = Document.parse(marshalled);
        document.append("_id", entity.getId());

        mongoCollection().insertOne(document);
    }

    @Override
    public List<A> listAll() {
        MongoCursor<Document> cursor = mongoCollection().find().iterator();

        List<T> entities = new ArrayList<>();
        while (cursor.hasNext()) {
            Document document = cursor.next();
            T entity = wanakuMarshallerService.unmarshalOne(document.toJson(), getEntityClass());

            entities.add(entity);
        }

        return convertToModels(entities);
    }

    @Override
    public boolean deleteById(K id) {
        DeleteResult deleteResult = mongoCollection().deleteOne(Filters.eq("_id", id));
        return deleteResult.getDeletedCount() > 0;
    }

    @Override
    public A findById(K id) {
        Document document = mongoCollection().find(Filters.eq("_id", id)).first();
        if (document == null) {
            return null;
        }

        return convertToModel(wanakuMarshallerService.unmarshalOne(document.toJson(), getEntityClass()));
    }

    abstract String databaseName();

    abstract String collectionName();

    protected MongoCollection<Document> mongoCollection() {
        return mongoClient.getDatabase(databaseName()).getCollection(collectionName());
    }

    abstract Class<T> getEntityClass();
}
