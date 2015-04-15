(ns akvo-unified-log.gae
  (:require [akvo-unified-log.json :as json]
            [environ.core :refer (env)])
  (:import [com.google.appengine.tools.remoteapi RemoteApiInstaller RemoteApiOptions]
           [com.google.appengine.api.datastore DatastoreServiceFactory
            Query Query$FilterPredicate Query$FilterOperator Query$SortDirection FetchOptions$Builder]))

(defn remote-api-options
  ([server]
   (remote-api-options server 443))
  ([server port]
   (doto (RemoteApiOptions.)
     (.server server port)
     (.credentials (:remote-api-email env)
                   (:remote-api-password env)))))

(defn install [options]
  (let [installer (RemoteApiInstaller.)]
    (.install installer options)
    installer))

(defn datastore []
  (DatastoreServiceFactory/getDatastoreService))

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
