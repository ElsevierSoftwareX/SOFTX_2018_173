package io.georocket.index.elasticsearch;

import java.util.List;

import org.jooq.lambda.tuple.Tuple2;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import rx.Completable;
import rx.Single;

/**
 * An Elasticsearch client
 * @author Michel Kraemer
 */
public interface ElasticsearchClient {
  /**
   * Close the client and release all resources
   */
  void close();
  
  /**
   * Insert a number of documents in one bulk request
   * @param documents a list of document IDs and actual documents to insert
   * @return the parsed bulk response from the server
   * @see #bulkResponseHasErrors(JsonObject)
   * @see #bulkResponseGetErrorMessage(JsonObject)
   */
  Single<JsonObject> bulkInsert(List<Tuple2<String, JsonObject>> documents);
  
  /**
   * Perform a search and start scrolling over the result documents
   * @param query the query to send
   * @param parameters the elasticsearch parameters
   * @param timeout the time after which the returned scroll id becomes invalid
   * @return an object containing the search hits and a scroll id that can
   * be passed to {@link #continueScroll(String, String)} to get more results
   */
  default Single<JsonObject> beginScroll(JsonObject query,
      JsonObject parameters, String timeout) {
    return beginScroll(query, null, parameters, timeout);
  }
  
  /**
   * Perform a search and start scrolling over the result documents. You can
   * either specify a <code>query</code>, a <code>postFilter</code> or both,
   * but one of them is required.
   * @param query the query to send (may be <code>null</code>, in this case
   * <code>postFilter</code> must be set)
   * @param postFilter a filter to apply (may be <code>null</code>, in this case
   * <code>query</code> must be set)
   * @param parameters the elasticsearch parameters
   * @param timeout the time after which the returned scroll id becomes invalid
   * @return an object containing the search hits and a scroll id that can
   * be passed to {@link #continueScroll(String, String)} to get more results
   */
  default Single<JsonObject> beginScroll(JsonObject query,
      JsonObject postFilter, JsonObject parameters, String timeout) {
    return beginScroll(query, postFilter, null, parameters, timeout);
  }

  /**
   * Perform a search, apply an aggregation, and start scrolling over the
   * result documents. You can either specify a <code>query</code>, a
   * <code>postFilter</code> or both, but one of them is required.
   * @param query the query to send (may be <code>null</code>, in this case
   * <code>postFilter</code> must be set)
   * @param postFilter a filter to apply (may be <code>null</code>, in this case
   * <code>query</code> must be set)
   * @param aggregations the aggregations to apply. Can be <code>null</code>
   * @param parameters the elasticsearch parameters
   * @param timeout the time after which the returned scroll id becomes invalid
   * @return an object containing the search hits and a scroll id that can
   * be passed to {@link #continueScroll(String, String)} to get more results
   */
  Single<JsonObject> beginScroll(JsonObject query, JsonObject postFilter,
      JsonObject aggregations, JsonObject parameters, String timeout);
  
  /**
   * Continue scrolling through search results. Call
   * {@link #beginScroll(JsonObject, JsonObject, String)} to get a scroll id
   * @param scrollId the scroll id
   * @param timeout the time after which the scroll id becomes invalid
   * @return an object containing new search hits and possibly a new scroll id
   */
  Single<JsonObject> continueScroll(String scrollId, String timeout);
  
  /**
   * Perform a search. The result set might not contain all documents. If you
   * want to scroll over all results use
   * {@link #beginScroll(JsonObject, JsonObject, String)}
   * instead.
   * @param query the query to send
   * @param parameters the elasticsearch parameters
   * @return an object containing the search result as returned from Elasticsearch
   */
  default Single<JsonObject> search(JsonObject query, JsonObject parameters) {
    return search(query, null, null, parameters);
  }
  
  /**
   * Perform a search. The result set might not contain all documents. If you
   * want to scroll over all results use
   * {@link #beginScroll(JsonObject, JsonObject, JsonObject, String)}
   * instead.
   * @param query the query to send (may be <code>null</code>)
   * @param postFilter a filter to apply (may be <code>null</code>)
   * @param parameters the elasticsearch parameters
   * @return an object containing the search result as returned from Elasticsearch
   */
  default Single<JsonObject> search(JsonObject query, JsonObject postFilter,
      JsonObject parameters) {
    return search(query, postFilter, null, parameters);
  }
  
  /**
   * Perform a search. The result set might not contain all documents. If you
   * want to scroll over all results use
   * {@link #beginScroll(JsonObject, JsonObject, JsonObject, JsonObject, String)}
   * instead.
   * @param query the query to send (may be <code>null</code>)
   * @param postFilter a filter to apply (may be <code>null</code>)
   * @param aggregations the aggregations to apply (may be <code>null</code>)
   * @param parameters the elasticsearch parameters
   * @return an object containing the search result as returned from Elasticsearch
   */
  Single<JsonObject> search(JsonObject query, JsonObject postFilter,
      JsonObject aggregations, JsonObject parameters);

  /**
   * Perform a count operation. The result is the number of documents
   * matching the query (without the documents themselves). If no query
   * is given, the total number of documents is returned.
   * @param query the query to send (may be <code>null</code>)
   * @return the number of documents matching the query
   */
  Single<Long> count(JsonObject query);

  /**
   * Perform an update operation. The update script is applied to all
   * documents that match the post filter.
   * @param postFilter a filter to apply (may be <code>null</code>)
   * @param script the update script to apply
   * @return an object containing the search result as returned from Elasticsearch
   */
  Single<JsonObject> updateByQuery(JsonObject postFilter, JsonObject script);

  /**
   * Delete a number of documents in one bulk request
   * @param ids the IDs of the documents to delete
   * @return the parsed bulk response from the server
   * @see #bulkResponseHasErrors(JsonObject)
   * @see #bulkResponseGetErrorMessage(JsonObject)
   */
  Single<JsonObject> bulkDelete(JsonArray ids);

  /**
   * Check if the index exists
   * @return a single emitting <code>true</code> if the index exists or
   * <code>false</code> otherwise
   */
  Single<Boolean> indexExists();

  /**
   * Create the index
   * @return a single emitting <code>true</code> if the index creation
   * was acknowledged by Elasticsearch, <code>false</code> otherwise
   */
  Single<Boolean> createIndex();

  /**
   * Create the index with settings.
   * @param settings the settings to set for the index.
   * @return a single emitting <code>true</code> if the index creation
   * was acknowledged by Elasticsearch, <code>false</code> otherwise
   */
  Single<Boolean> createIndex(JsonObject settings);
  
  /**
   * Convenience method that makes sure the index exists. It first calls
   * {@link #indexExists()} and then {@link #createIndex()} if the index does
   * not exist yet.
   * @return a single that will emit a single item when the index has
   * been created or if it already exists
   */
  Completable ensureIndex();
  
  /**
   * Set mapping
   * @param mapping the mapping to set
   * @return a single emitting <code>true</code> if the operation
   * was acknowledged by Elasticsearch, <code>false</code> otherwise
   */
  Single<Boolean> putMapping(JsonObject mapping);
  
  /**
   * Convenience method that makes sure the given mapping exists.
   * @param mapping the mapping to set for the type
   * @return a single that will emit a single item when the mapping has
   * been created or if it already exists
   */
  Completable ensureMapping(JsonObject mapping);

  /**
   * Get mapping
   * @return the parsed mapping response from the server
   */
  Single<JsonObject> getMapping();

  /**
   * Get mapping for the given field
   * @param field the field
   * @return the parsed mapping response from the server
   */
  Single<JsonObject> getMapping(String field);

  /**
   * Check if Elasticsearch is running and if it answers to a simple request
   * @return <code>true</code> if Elasticsearch is running, <code>false</code>
   * otherwise
   */
  Single<Boolean> isRunning();

  /**
   * Check if the given bulk response contains errors
   * @param response the bulk response
   * @return <code>true</code> if the response has errors, <code>false</code>
   * otherwise
   * @see #bulkInsert(List)
   * @see #bulkDelete(JsonArray)
   * @see #bulkResponseGetErrorMessage(JsonObject)
   */
  default boolean bulkResponseHasErrors(JsonObject response) {
    return response.getBoolean("errors", false);
  }
  
  /**
   * Builds an error message from a bulk response containing errors
   * @param response the response containing errors
   * @return the error message or null if the bulk response does not contain
   * errors
   * @see #bulkInsert(List)
   * @see #bulkDelete(JsonArray)
   * @see #bulkResponseHasErrors(JsonObject)
   */
  default String bulkResponseGetErrorMessage(JsonObject response) {
    if (!bulkResponseHasErrors(response)) {
      return null;
    }
    
    StringBuilder res = new StringBuilder();
    res.append("Errors in bulk operation:");
    JsonArray items = response.getJsonArray("items", new JsonArray());
    for (Object o : items) {
      JsonObject jo = (JsonObject)o;
      for (String key : jo.fieldNames()) {
        JsonObject op = jo.getJsonObject(key);
        if (bulkResponseItemHasErrors(op)) {
          res.append(bulkResponseItemGetErrorMessage(op));
        }
      }
    }
    return res.toString();
  }
  
  /**
   * Check if an item of a bulk response has an error
   * @param item the item
   * @return <code>true</code> if the item has an error, <code>false</code>
   * otherwise
   */
  default boolean bulkResponseItemHasErrors(JsonObject item) {
    return item.getJsonObject("error") != null;
  }
  
  /**
   * Builds an error message from a bulk response item
   * @param item the item
   * @return the error message or null if the bulk response item does not
   * contain an error
   */
  default String bulkResponseItemGetErrorMessage(JsonObject item) {
    if (!bulkResponseItemHasErrors(item)) {
      return null;
    }
    
    StringBuilder res = new StringBuilder();
    JsonObject error = item.getJsonObject("error");
    if (error != null) {
      String id = item.getString("_id");
      String type = error.getString("type");
      String reason = error.getString("reason");
      res.append("\n[id: [");
      res.append(id);
      res.append("], type: [");
      res.append(type);
      res.append("], reason: [");
      res.append(reason);
      res.append("]]");
    }
    
    return res.toString();
  }
}
