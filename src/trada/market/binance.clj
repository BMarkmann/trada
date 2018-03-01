(ns trada.market.binance
  (:require
   [trada.market :as m]
   [trada.util :as u]
   [clojure.core.async :refer [go chan <! <!! >! >!! timeout go-loop]]
   [gniazdo.core :as ws]
   [cheshire.core :as json]))


;; binance API endpoints
(def ^:private rest-uri "https://api.binance.com")
(def ^:private ws-uri "wss://stream.binance.com:9443")


(defn get-products []
  (u/simple-json-get (str rest-uri "/api/v1/exchangeInfo")))


(defn get-product-order-book [product-id limit]
  (u/simple-json-get (str rest-uri "/api/v1/depth?symbol=" product-id "&limit=" limit)))


(defn get-product-ticker [product-id]
  (u/simple-json-get (str rest-uri "/api/v3/ticker/bookTicker?symbol=" product-id)))


(defn get-product-trades
  ([product-id limit] (u/simple-json-get (str rest-uri "/api/v1/trades?symbol=" product-id "&limit=" limit)))
  ([product-id] (u/simple-json-get (str rest-uri "/api/v1/trades?symbol=" product-id))))


(defn get-product-candles
  ([product-id interval limit start end]
   (u/simple-json-get
    (str rest-uri
         "/api/v1/klines?symbol=" product-id
         "&interval=" interval
         "&limit=" limit
         "&startTime=" start
         "&endTime=" end)))
  ([product-id interval start end]
   (u/simple-json-get
    (str rest-uri
         "/api/v1/klines?symbol=" product-id
         "&interval=" interval
         "&startTime=" start
         "&endTime=" end))))


(defn get-product-24hr-stats [product-id]
  (u/simple-json-get (str rest-uri "/api/v1/ticker/24hr?symbol=" product-id)))


(defn binance-ws-subscribe [subscription-req ch]
  (let [client (u/get-ws-client true)
        conn (ws/connect (str ws-uri "/ws/" subscription-req)
                          :client client
                          :on-receive #(>!! ch (json/parse-string % (fn [k] (keyword k)))))]
    conn))


(defn l2-process-snapshot [snapshot-message]
  {:buy (u/sort-depth-arrays
         true (reduce #(conj % [(bigdec (first %2)) (bigdec (second %2))])
                      []
                      (:bids snapshot-message)))
   :sell (u/sort-depth-arrays
          false (reduce #(conj % [(bigdec (first %2)) (bigdec (second %2))])
                        []
                        (:asks snapshot-message)))
   :lastUpdateId (:lastUpdateId snapshot-message)})

(defn l2-remove-price-point [depth-array price-point]
  (remove #(= (bigdec (first %)) (bigdec price-point)) depth-array))


(defn l2-handle-single-update [depth-array update]
  (if (= (bigdec (second update)) (bigdec 0))
    (l2-remove-price-point depth-array (first update))
    (conj (l2-remove-price-point depth-array (first update))
          [(bigdec (first update)) (bigdec (second update))])))


(defn l2-update-side [depth-array updates]
  (if (empty? updates)
    depth-array
    (recur (l2-handle-single-update depth-array (first updates)) (rest updates))))
    


(defn l2-process-updates [update-message current-market]
  (let [bid-updates (:b update-message)
        ask-updates (:a update-message)]
    {:buy (l2-update-side (:buy current-market) bid-updates)
     :sell (l2-update-side (:sell current-market) ask-updates)}))


(defn stamp-market-msg [market-msg product]
  (assoc market-msg
         :market "binance"
         :product product
         :timestamp (quot (System/currentTimeMillis) 1000)
         :timestampPretty (str (new java.util.Date))))

(defn subscribe-l2market [ch product]
  (let [market (hash-map :buy [] :sell [])
        update-chan (chan)
        conn (binance-ws-subscribe (str product "@depth") update-chan)
        initial-market (l2-process-snapshot
                        (get-product-order-book
                         (clojure.string/upper-case product)
                         1000))
        snapshot-last-update-id (:lastUpdateId initial-market)]
    (go-loop [current-market initial-market
              update (<! update-chan)]
      (cond (<= (:u update) snapshot-last-update-id)
            (let [next-update (<! update-chan)]
              (recur initial-market next-update))
            (= (:e update) "depthUpdate")
            (let [updated-market (l2-process-updates update current-market)]
              (>! ch (stamp-market-msg updated-market product))
              (recur updated-market (<! update-chan)))
            :else (recur current-market (<! update-chan))))
    (fn []
      (ws/close conn))))


(defn subscribe-ticker [ch product]
  (let [update-chan (chan)
        conn (binance-ws-subscribe (str product "@trade") update-chan)]
    (go-loop [update (<! update-chan)]
      (cond (= (:e update) "trade")
            (do
              (>! ch update)
              (recur (<! update-chan)))
            :else (recur (<! update-chan))))
    (fn []
      (ws/close conn))))
            

(defn get-market []
  (reify m/Market
    (ticker [_ c product]
      (subscribe-ticker c product))
    (orderbook [_ c product]
      (subscribe-l2market c product))
    (orderbooksnapshot [_ product]
      (get-product-order-book
       (clojure.string/upper-case product)
       1000))
    (ohlc [_ product timestamp divisions]
      (println "returning binance ohcl"))))



