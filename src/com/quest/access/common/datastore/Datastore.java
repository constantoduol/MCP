package com.quest.access.common.datastore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.QueryResultList;
import com.quest.access.common.io;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;

/**
 *
 * @author constant oduol
 * @version 1.0(2/7/2014)
 */
//update,select, insert,delete
public class Datastore {

    private static DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    public static Key insert(String entityName, String primaryValue, HashMap<String, Object> values) {
        Key key = KeyFactory.createKey(entityName, primaryValue);
        Entity entity = new Entity(entityName, key);
        for (String propertyName : values.keySet()) {
            Object propertValue = values.get(propertyName);
            entity.setProperty(propertyName, propertValue);
        }
        return datastore.put(entity);
    }

    public static Key insert(Entity en) {
        return datastore.put(en);
    }

    public static Key insert(String entityName, String[] props, Object[] values) {
        Entity entity = new Entity(entityName);
        for (int x = 0; x < props.length; x++) {
            entity.setProperty(props[x], values[x]);
        }
        return datastore.put(entity);
    }

    public static Key insert(String entityName, List<String> props, List<Object> values) {
        Entity entity = new Entity(entityName);
        for (int x = 0; x < props.size(); x++) {
            entity.setProperty(props.get(x), values.get(x));
        }
        return datastore.put(entity);
    }

    public static Key insert(String entityName, String primaryValue, String[] props, Object[] values) {
        Key key = KeyFactory.createKey(entityName, primaryValue);
        Entity entity = new Entity(entityName, key);
        for (int x = 0; x < props.length; x++) {
            entity.setProperty(props[x], values[x]);
        }
        return datastore.put(entity);
    }

    public static Key insert(String entityName, Key key, String[] props, Object[] values) {
        Entity entity = new Entity(entityName, key);
        for (int x = 0; x < props.length; x++) {
            entity.setProperty(props[x], values[x]);
        }
        return datastore.put(entity);
    }

    public static Key insert(String entityName, String primaryValue, JSONObject values) {
        Key key = KeyFactory.createKey(entityName, primaryValue);
        Entity entity = new Entity(entityName, key);
        Iterator iter = values.keys();
        try {
            while (iter.hasNext()) {
                String propertyName = (String) iter.next();
                Object propertValue = values.get(propertyName);
                entity.setProperty(propertyName, propertValue);
            }
        } catch (Exception e) {

        }
        return datastore.put(entity);
    }

    public static Key insert(String entityName, HashMap<String, Object> values) {
        Entity entity = new Entity(entityName);
        for (String propertyName : values.keySet()) {
            Object propertValue = values.get(propertyName);
            entity.setProperty(propertyName, propertValue);
        }
        return datastore.put(entity);
    }

    public static Key insert(String entityName, JSONObject values) {
        Entity entity = new Entity(entityName);
        Iterator iter = values.keys();
        try {
            while (iter.hasNext()) {
                String propertyName = (String) iter.next();
                Object propertValue = values.get(propertyName);
                entity.setProperty(propertyName, propertValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datastore.put(entity);
    }

    public static Entity getSingleEntity(String entityName, String propertyName, Object value, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(propertyName, filterOperator, value);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        try {
            return pq.asSingleEntity();
        } catch (Exception e) {
            return null;
        }
    }

    public static Entity getSingleEntity(String entityName, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        try {
            return pq.asSingleEntity();
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject getSingleEntity(String entityName, Object[][] filters) {
        Query query = new Query(entityName);
        Filter[] filterz = new Filter[filters.length];
        for (int x = 0; x < filters.length; x++) {
            Object[] filter = filters[x];
            filterz[x] = new FilterPredicate(filter[0].toString(), getFilterOperator(filter[1].toString()), filter[2]);
        }
        query = setFilters(filterz, query);
        PreparedQuery pq = datastore.prepare(query);
        try {
            return entityToJSON(pq.asSingleEntity());
        } catch (Exception e) {
            return null;
        }
    }
    
    public static JSONObject getMultipleEntities(String entityName, Object[][] filters) {
        Query query = new Query(entityName);
        Filter[] filterz = new Filter[filters.length];
        for (int x = 0; x < filters.length; x++) {
            Object[] filter = filters[x];
            filterz[x] = new FilterPredicate(filter[0].toString(), getFilterOperator(filter[1].toString()), filter[2]);
        }
        query = setFilters(filterz, query);
        PreparedQuery pq = datastore.prepare(query);
        try {
            return entityToJSON(pq.asIterable());
        } catch (Exception e) {
            return null;
        }
    }

    public static Iterable<Entity> getMultipleEntities(String entityName, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable();
    }

    public static Iterable<Entity> getMultipleEntities(String entityName, PropertyProjection[] projs, Filter... filters) {
        Query query = new Query(entityName);
        for (PropertyProjection proj : projs) {
            if (proj.getName().equals("*")) {
                continue;
            }
            query.addProjection(proj);
        }
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable();
    }

    public static Iterable<Entity> getMultipleEntities(String entityName, Key key, Filter... filters) {
        Query query = new Query(entityName);
        query.setAncestor(key);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable();
    }

    private static Query setFilters(Filter[] filters, Query q) {
        if (filters.length == 1) {
            q.setFilter(filters[0]);
        } else if (filters.length > 1) {
            q.setFilter(CompositeFilterOperator.and(filters));
        }
        return q;
    }

    public static List<Entity> getMultipleEntitiesAsList(String entityName, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asList(FetchOptions.Builder.withDefaults());
    }

    public static List<Entity> getMultipleEntitiesAsList(String entityName, String propertyName, String value, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(propertyName, filterOperator, value);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asList(FetchOptions.Builder.withDefaults());
    }

    public static Iterable<Entity> getMultipleEntities(String entityName, String sortProperty, SortDirection direction, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        query.addSort(sortProperty, direction);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable();
    }

    public static List<Entity> getMultipleEntitiesAsList(String entityName, String sortProperty, SortDirection direction, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        query.addSort(sortProperty, direction);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asList(FetchOptions.Builder.withDefaults());
    }

    public static QueryResultList<Entity> getMultipleEntities(String entityName, String sortProperty, SortDirection direction, FetchOptions options, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        query.addSort(sortProperty, direction);
        PreparedQuery pq = datastore.prepare(query);
        return options == null ? pq.asQueryResultList(FetchOptions.Builder.withDefaults()) : pq.asQueryResultList(options);
    }

    public static JSONObject getPaginatedEntities(String entityName, String sortProperty, SortDirection direction, FetchOptions options, Filter... filters) {
        try {
            Query query = new Query(entityName);
            query = setFilters(filters, query);
            query.addSort(sortProperty, direction);
            PreparedQuery pq = datastore.prepare(query);
            QueryResultList<Entity> resultList = pq.asQueryResultList(options);
            JSONObject data = new JSONObject();
            data.put("cursor", resultList.getCursor().toWebSafeString());
            data.put("data", entityToJSON(resultList));
            return data;
        } catch (JSONException ex) {
            Logger.getLogger(Datastore.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static QueryResultList<Entity> getMultipleEntities(String entityName, FetchOptions options, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        return options == null ? pq.asQueryResultList(FetchOptions.Builder.withDefaults()) : pq.asQueryResultList(options);
    }

    public static Iterable<Entity> getMultipleEntities(String entityName, String propertyName, String value, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(propertyName, filterOperator, value);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable();
    }

    public static Iterable<Entity> getEntities(Query q) {
        PreparedQuery pq = datastore.prepare(q);
        return pq.asIterable();
    }

    public static JSONObject entityToJSON(Entity en) {
        JSONObject obj = new JSONObject();
        try {
            Map map = en.getProperties();
            Iterator<String> iter = map.keySet().iterator();
            while (iter.hasNext()) {
                String propertyName = iter.next();
                Object value = map.get(propertyName);
                obj.put(propertyName, value);
            }
        } catch (Exception e) {

        }
        return obj;
    }

    public static JSONObject entityToJSONArray(Entity en) {
        JSONObject obj = new JSONObject();
        try {
            Map map = en.getProperties();
            Iterator<String> iter = map.keySet().iterator();
            while (iter.hasNext()) {
                String propertyName = iter.next();
                Object value = map.get(propertyName);
                obj.put(propertyName, new JSONArray().put(value));
            }
        } catch (Exception e) {

        }
        return obj;
    }

    public static JSONObject entityToJSON(Iterable<Entity> iterable) {
        JSONObject obj = new JSONObject();
        try {
            for (Entity en : iterable) {
                Map map = en.getProperties();
                Iterator<String> iter = map.keySet().iterator();
                while (iter.hasNext()) {
                    String propertyName = iter.next();
                    Object value = map.get(propertyName);
                    if (obj.opt(propertyName) == null) {
                        obj.put(propertyName, new JSONArray().put(value));
                    } else {
                        ((JSONArray) obj.opt(propertyName)).put(value);
                    }
                }
            }
        } catch (Exception e) {

        }
        return obj;
    }

    public static Iterable<Entity> getAllEntities(String entityName, FetchOptions options) {
        Query query = new Query(entityName);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable(options);
    }

    public static List<Entity> getAllEntitiesAsList(String entityName, FetchOptions options) {
        Query query = new Query(entityName);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asList(options);
    }

    public static List<Entity> getAllEntitiesAsList(String entityName, FetchOptions options, String sortProperty, SortDirection direction) {
        Query query = new Query(entityName);
        query.addSort(sortProperty, direction);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asList(options);
    }

    public static Iterable<Entity> getAllEntities(String entityName) {
        Query query = new Query(entityName);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable();
    }

    public static Iterable<Entity> getAllEntities(String entityName, String sortProperty, SortDirection direction) {
        Query query = new Query(entityName);
        query.addSort(sortProperty, direction);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asIterable();
    }

    public static List<Entity> getAllEntitiesAsList(String entityName) {
        Query query = new Query(entityName);
        PreparedQuery pq = datastore.prepare(query);
        return pq.asList(FetchOptions.Builder.withDefaults());
    }

    public static void updateSingleEntity(String entityName, String primaryKey, String primaryKeyValue, String propertyName, String propertyValue, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(primaryKey, filterOperator, primaryKeyValue);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        Entity en = pq.asSingleEntity();
        en.setProperty(propertyName, propertyValue);
        datastore.put(en);
    }

    public static void updateSingleEntity(String entityName, String primaryKey, String primaryKeyValue, String[] propertyNames, Object[] propertyValues, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(primaryKey, filterOperator, primaryKeyValue);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        Entity en = pq.asSingleEntity();
        for (int x = 0; x < propertyNames.length; x++) {
            en.setProperty(propertyNames[x], propertyValues[x]);
        }
        datastore.put(en);
    }

    public static void updateSingleEntity(String entityName, String[] propertyNames, Object[] propertyValues, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        Entity en = pq.asSingleEntity();
        for (int x = 0; x < propertyNames.length; x++) {
            en.setProperty(propertyNames[x], propertyValues[x]);
        }
        datastore.put(en);
    }

    public static void updateMultipeEntities(String entityName, String[] propertyNames, Object[] propertyValues, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        Iterable<Entity> iter = pq.asIterable();
        for (Entity en : iter) {
            for (int x = 0; x < propertyNames.length; x++) {
                en.setProperty(propertyNames[x], propertyValues[x]);
            }
            datastore.put(en);
        }
    }

    public static void updateMultipeEntities(String entityName, String primaryKey, String primaryKeyValue, String propertyName, Object propertyValue, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(primaryKey, filterOperator, primaryKeyValue);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        for (Entity en : pq.asIterable()) {
            en.setProperty(propertyName, propertyValue);
            datastore.put(en);
        }
    }

    public static void updateAllEntities(String entityName, String propertyName, Object newValue) {
        Query query = new Query(entityName);
        PreparedQuery pq = datastore.prepare(query);
        for (Entity en : pq.asIterable()) {
            en.setProperty(propertyName, newValue);
            datastore.put(en);
        }
    }

    public static void deleteSingleEntity(String entityName, String propertyName, Object value, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(propertyName, filterOperator, value);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        Entity en = pq.asSingleEntity();
        if (en != null) {
            datastore.delete(en.getKey());
        }
    }

    public static void deleteSingleEntity(String entityName, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        Entity en = pq.asSingleEntity();
        if (en != null) {
            datastore.delete(en.getKey());
        }
    }

    public static void deleteMultipleEntities(String entityName, Filter... filters) {
        Query query = new Query(entityName);
        query = setFilters(filters, query);
        PreparedQuery pq = datastore.prepare(query);
        for (Entity en : pq.asIterable()) {
            datastore.delete(en.getKey());
        }
    }

    public static void deleteMultipleEntities(String entityName, String propertyName, Object value, FilterOperator filterOperator) {
        Filter filter = new FilterPredicate(propertyName, filterOperator, value);
        Query query = new Query(entityName).setFilter(filter);
        PreparedQuery pq = datastore.prepare(query);
        for (Entity en : pq.asIterable()) {
            datastore.delete(en.getKey());
        }
    }

    public static void deleteAllEntities(String entityName) {
        Query query = new Query(entityName);
        PreparedQuery pq = datastore.prepare(query);
        for (Entity en : pq.asIterable()) {
            datastore.delete(en.getKey());
        }
    }

    public static Long getCount(String entity, Filter... filters) {
        Query query = new Query("__Stat_" + entity + "__");
        query = setFilters(filters, query);
        Entity entityStat = datastore.prepare(query).asSingleEntity();
        return (Long) entityStat.getProperty("count");
    }

    public static ArrayList<String> getEntityPropNames(String entityName) {
        List<Entity> list = Datastore.getAllEntitiesAsList(entityName, FetchOptions.Builder.withLimit(1));
        if (list != null && list.size() > 0) {
            Entity en = list.get(0);
            Map<String, Object> properties = en.getProperties();
            return new ArrayList(properties.keySet());
        } else {
            return new ArrayList<>();
        }
    }

    public static FilterOperator getFilterOperator(String operator) {
        FilterOperator op = null;
        switch (operator) {
            case "=":
                op = FilterOperator.EQUAL;
                break;
            case ">":
                op = FilterOperator.GREATER_THAN;
                break;
            case ">=":
                op = FilterOperator.GREATER_THAN_OR_EQUAL;
                break;
            case "<":
                op = FilterOperator.LESS_THAN;
                break;
            case "<=":
                op = FilterOperator.LESS_THAN_OR_EQUAL;
                break;
            case "!=":
                op = FilterOperator.NOT_EQUAL;
                break;
        }
        return op;
    }

    public static List<Entity> twoWayJoin(String[] entityNames, String[] joinProps, String[] sortProps, SortDirection[] dirs, Filter[] filters1, Filter[] filters2) {
//        List<Entity> joined = new ArrayList();
//        List<Entity> entitiesOne = sortProps != null && sortProps[0] != null
//                ? Datastore.getMultipleEntitiesAsList(entityNames[0], sortProps[0], dirs[0], filters1)
//                : Datastore.getMultipleEntitiesAsList(entityNames[0], filters1);
//
//        List<Entity> entitiesTwo = sortProps != null && sortProps[1] != null
//                ? Datastore.getMultipleEntitiesAsList(entityNames[1], sortProps[1], dirs[1], filters2)
//                : Datastore.getMultipleEntitiesAsList(entityNames[1], filters2);
//        
//        //no of comparisons made = maxLength * minLength; for a basic nested loop join
//        //get longer list
//        //go one by one getting value of join prop on one and compare with that of two
//        //if they are equal join them
//        List<Entity> longerList = entitiesOne.size() > entitiesTwo.size() ? entitiesOne : entitiesTwo;
//        List<Entity> shorterList = entitiesOne.size() < entitiesTwo.size() ? entitiesOne : entitiesTwo;
//        for (Entity longerList1 : longerList) {
//            for (Entity shorterList1 : shorterList) {
//                Entity en1 = longerList1;
//                Entity en2 = shorterList1;
//                if (en1.getProperty(joinProps[0]).equals(en2.getProperty(joinProps[1]))) {
//                    en1.setPropertiesFrom(en2);
//                    Entity en3 = en1.clone();
//                    joined.add(en3);
//                }
//            }
//        }
//        
//        //join the two entities one and two
//        //strategy is to iterate on the larger one while doing the join
//        //[Entity1, Entity2, Entity3, Entity4, Entity5]
//        //[Entity6, Entity7, Entity8]
//        //
//        return joined;
        List<Entity> joined = new ArrayList();
        Iterable<Entity> entitiesOne = sortProps != null && sortProps[0] != null
                ? Datastore.getMultipleEntities(entityNames[0], sortProps[0], dirs[0], filters1)
                : Datastore.getMultipleEntities(entityNames[0], filters1);

        Iterable<Entity> entitiesTwo = sortProps != null && sortProps[1] != null
                ? Datastore.getMultipleEntities(entityNames[1], sortProps[1], dirs[1], filters2)
                : Datastore.getMultipleEntities(entityNames[1], filters2);
        //no of comparisons made = maxLength * minLength; for a basic nested loop join
        for (Entity en1 : entitiesOne) {
            for (Entity en2 : entitiesTwo) {
                if (en1.getProperty(joinProps[0]).equals(en2.getProperty(joinProps[1]))) {
                    //en1.setPropertiesFrom(en2); //copy properties of en2
                    en1.setPropertiesFrom(en2);
                    joined.add(en1);
                }
            }
        }
        //join the two entities one and two
        //strategy is to iterate on the larger one while doing the join
        //[Entity1, Entity2, Entity3, Entity4, Entity5]
        //[Entity6, Entity7, Entity8]
        //
        return joined;
    }

    public static void main(String[] args) {
        ArrayList a = new ArrayList();
        a.add(1);
        a.add(2);
        a.add(3);
        ArrayList b = (ArrayList) a.clone();
        b.set(2, 7);
        io.out(a);
        io.out(b);

    }

    //this is a two way join
    public static JSONObject twoWayJoin(String[] entityNames, String[] joinProps, String[] sortProps, SortDirection[] dirs, FetchOptions options1, FetchOptions options2, Filter[] filters1, Filter[] filters2) {
        try {
            List<Entity> joined = new ArrayList();
            QueryResultList<Entity> entitiesOne = sortProps != null && sortProps[0] != null
                    ? Datastore.getMultipleEntities(entityNames[0], sortProps[0], dirs[0], options1, filters1)
                    : Datastore.getMultipleEntities(entityNames[0], options1, filters1);

            QueryResultList<Entity> entitiesTwo = sortProps != null && sortProps[1] != null
                    ? Datastore.getMultipleEntities(entityNames[1], sortProps[1], dirs[1], options2, filters2)
                    : Datastore.getMultipleEntities(entityNames[1], options2, filters2);
            //no of comparisons made = maxLength * minLength; for a basic nested loop join
            List<Entity> longerList = entitiesOne.size() > entitiesTwo.size() ? entitiesOne : entitiesTwo;
            List<Entity> shorterList = entitiesOne.size() < entitiesTwo.size() ? entitiesOne : entitiesTwo;
            for (Entity longerList1 : longerList) {
                for (Entity shorterList1 : shorterList) {
                    Entity en1 = longerList1;
                    Entity en2 = shorterList1;
                    if (en1.getProperty(joinProps[0]).equals(en2.getProperty(joinProps[1]))) {
                        en1.setPropertiesFrom(en2);
                        Entity en3 = en1.clone();
                        joined.add(en3);
                    }
                }
            }
            //join the two entities one and two
            //strategy is to iterate on the larger one while doing the join
            //[Entity1, Entity2, Entity3, Entity4, Entity5]
            //[Entity6, Entity7, Entity8]
            //
            String cursor = options1 == null ? entitiesTwo.getCursor().toWebSafeString() : entitiesOne.getCursor().toWebSafeString();
            JSONObject response = new JSONObject();
            response.put("cursor", cursor);
            response.put("data", entityToJSON(joined));
            response.put("joined", joined);
            return response;
        } catch (JSONException ex) {
            Logger.getLogger(Datastore.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    //we compare two columns/properties on a single entity
    //fetch the 
    public static List<Entity> compareQuery(String entityName, String propertyOne, String operator, String propertyTwo, Filter... filters) {
        List<Entity> entities = Datastore.getMultipleEntitiesAsList(entityName, filters);
        ArrayList list = new ArrayList();
        for (Entity en : entities) {
            Object one = en.getProperty(propertyOne);
            Object two = en.getProperty(propertyTwo);
            // we can compare
            try {
                // cast as a double to first if fail cast as object
                Double first = Double.parseDouble(one.toString());
                Double second = Double.parseDouble(two.toString());
                if (operator.equals("=")) {
                    if (first == second) {
                        list.add(en);
                    }
                } else if (operator.equals("<")) {
                    if (first < second) {
                        list.add(en);
                    }
                } else if (operator.equals("<=")) {
                    if (first <= second) {
                        list.add(en);
                    }
                } else if (operator.equals(">")) {
                    if (first > second) {
                        list.add(en);
                    }
                } else if (operator.equals(">=")) {
                    if (first >= second) {
                        list.add(en);
                    }
                } else if (operator.equals("!=")) {
                    if (first != second) {
                        list.add(en);
                    }
                }

            } catch (Exception e) {
                // these are other objects apart from numbers
                if (operator.equals("=")) {
                    if (one.equals(two)) {
                        list.add(en);
                    }
                } else if (operator.equals("!=")) {
                    if (!one.equals(two)) {
                        list.add(en);
                    }
                }
                // ignore other operators because they dont apply
            }

        }
        return list;

    }

    public static boolean exists(String entityName, String[] columns, String[] values) {
        ArrayList<Filter> filters = new ArrayList();
        for (int x = 0; x < columns.length; x++) {
            Filter filter = new FilterPredicate(columns[x], FilterOperator.EQUAL, values[x]);
            filters.add(filter);
        }
        List<Entity> all = Datastore.getMultipleEntitiesAsList(entityName, filters.toArray(new Filter[filters.size()]));
        if (all != null && all.size() > 0) {
            return true;
        }
        return false;
    }

}
