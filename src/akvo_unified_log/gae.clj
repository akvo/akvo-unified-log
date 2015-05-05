(ns akvo-unified-log.gae
  (:import [com.google.appengine.tools.remoteapi RemoteApiInstaller RemoteApiOptions]
           [com.google.appengine.api.datastore DatastoreServiceFactory
            Query Query$FilterPredicate Query$FilterOperator
            Query$SortDirection FetchOptions$Builder]))

(defn fetch-data-query [date]
  (-> (Query. "EventQueue")
      (.setFilter (Query$FilterPredicate. "createdDateTime"
                                          Query$FilterOperator/GREATER_THAN
                                          date))
      (.addSort "createdDateTime"
                (Query$SortDirection/ASCENDING))))

(defn fetch-data-iterator [ds since limit]
  (.asIterator (.prepare ds (fetch-data-query since))
               (FetchOptions$Builder/withLimit limit)))
