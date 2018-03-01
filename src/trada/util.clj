(ns trada.util
  (:require
   [clj-http.client :as client]
   [clojure.data.json :as json])
  (:import [org.eclipse.jetty.websocket.client WebSocketClient]
           [org.eclipse.jetty.util.ssl SslContextFactory]))

(def ^:private ws-incoming-buffer-size (* 2 1024 1024))

;; make a synchronous GET to endpoint, convert response JSON to map
(defn simple-json-get [endpoint]
  (-> (client/get endpoint {:accept :json})
      :body
      (json/read-str :key-fn keyword)))


;; create a websocket client, with ssl if needed
(defn get-ws-client [is-ssl]
  (let [client (if is-ssl
                 (new WebSocketClient (new SslContextFactory))
                 (new WebSocketClient))]
    (.setMaxTextMessageSize (.getPolicy client) ws-incoming-buffer-size)
    (.start client)
    client))


;; util for printing to repl out from async process
(def repl-out *out*)
(defn prn-to-repl [& args]
 (binding [*out* repl-out]
   (apply prn args)))



;; sort orderbook depth arrays in format [[1 2][6 10][3 5]] by first value
;; in nested arrays
(defn sort-depth-arrays [reverse-flag price-levels]
  (if reverse-flag
    (reverse (sort-by first price-levels))
    (sort-by first price-levels)))

