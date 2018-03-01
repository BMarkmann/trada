(ns trada.market.gdax
  (:require
   [trada.market :as m]
   [trada.util :as u]
   [clojure.core.async :refer [go chan <! <!! >! >!! timeout go-loop]]
   [gniazdo.core :as ws]
   [cheshire.core :as json]))


;; GDAX API endpoints
(def ^:private rest-uri "https://api.gdax.com")
(def ^:private ws-uri "ws://localhost:65432/")


(defn get-products []
  (u/simple-json-get (str rest-uri "/products")))

;; (for [x (get-products)]
;;   (println x))

;; [...
;; {:base_min_size 0.0001, :margin_enabled false, :id BTC-USD,
;;  :base_max_size 250, :status online, :display_name BTC/USD,
;;  :quote_increment 0.01, :status_message nil, :quote_currency USD,
;;  :base_currency BTC} ...]

(defn get-product-order-book [product-id level]
  (u/simple-json-get  (str rest-uri "/products/" product-id "/book?level=" level)))

;; {:sequence 4732157210,
;;  :bids [["13370" "10.75299462" 15]],
;;  :asks [["13370.01" "0.64068146" 3]]}

(defn get-product-ticker [product-id]
  (u/simple-json-get (str rest-uri "/products/" product-id "/ticker")))

;; {:trade_id 31517309,
;;  :price "13423.01000000",
;;  :size "0.00036393",
;;  :bid "13423",
;;  :ask "13423.01",
;;  :volume "17780.12133376",
;;  :time "2018-01-01T02:26:51.807000Z"}

(defn get-product-trades [product-id]
  (u/simple-json-get (str rest-uri "/products/" product-id "/trades")))

;; [...
;; {:time "2018-01-01T02:29:13.92Z", :trade_id 31517341,
;;  :price "13423.01000000", :size "0.00222939", :side "sell"}...]

(defn get-product-candles [product-id start end granularity]
  (u/simple-json-get (str rest-uri
                    "/products/" product-id
                    "/candles?start=" start
                    "&end=" end
                    "&granularity=" granularity)))

;; [[1514774160 13412 13413.33 13413.33 13412.01 0.41449548]
;;  [1514774100 13405.57 13425 13405.57 13410.01 2.5799760700000003]
;;  [1514774040 13398.97 13404.53 13398.97 13404.53 1.4314305600000004]...]

(defn get-product-24hr-stats [product-id]
  (u/simple-json-get (str rest-uri "/products/" product-id "/stats")))

;; {:open "13281.00000000",
;;  :high "14280.26000000",
;;  :low "12701.00000000",
;;  :volume "17503.86169144",
;;  :last "13345.73000000",
;;  :volume_30day "1042774.13533071"}


;; GDAX websocket methods


;; (def subscribe-req (json/generate-string {
;;                                           :type "subscribe"
;;                                           :product_ids ["ETH-USD"]
;;                                           :channels ["level2", "heartbeat"]}))

(defn create-ws-subscribe-req [product-ids channels]
  (json/generate-string {
                         :type "subscribe"
                         :product_ids product-ids
                         :channels channels}))


(defn gdax-ws-subscribe [subscription-req ch]
  (let [client (u/get-ws-client true)
        conn (ws/connect "wss://ws-feed.gdax.com"
                          :client client
                          :on-receive #(>!! ch (json/parse-string % (fn [k] (keyword k)))))]
    (ws/send-msg conn subscription-req)
    conn))


(defn l2-process-snapshot [snapshot-message]
  (let [snapshot
        {:buy (u/sort-depth-arrays true
               (reduce #(conj % [(bigdec (first %2)) (bigdec (second %2))]) [] (:bids snapshot-message)))
         :sell (u/sort-depth-arrays false
                (reduce #(conj % [(bigdec (first %2)) (bigdec (second %2))]) [] (:asks snapshot-message)))}]
    snapshot))  


(defn l2-remove-price-point [depth-array price-point]
  (remove #(= (bigdec (first %)) (bigdec price-point)) depth-array))


(defn l2-handle-single-update [depth-array update]
  (if (= (nth update 2) (bigdec 0))
    (l2-remove-price-point depth-array (second update))
    (conj (l2-remove-price-point depth-array (second update)) [(bigdec (second update)) (bigdec (nth update 2))])))
    

(defn l2-process-update [update-message current-market]
  (let [market current-market
         updates (:changes update-message)]
    (if (empty? updates)
      ;; return the sorted price / depth arrays
      {:buy (u/sort-depth-arrays true (:buy market))
       :sell (u/sort-depth-arrays false (:sell market))}
      ;; process the remaining updates
      (let [update (first updates)]
        (cond (= "buy" (first update))
              (let [market-update {:buy (l2-handle-single-update (:buy current-market) update)
                                   :sell (:sell current-market)}]
                (recur {:changes (rest updates)} market-update))
              (= "sell" (first update))
              (let [market-update {:buy (:buy current-market)
                                   :sell (l2-handle-single-update (:sell current-market) update)}]
                (recur {:changes (rest updates)} market-update))
              :else (recur {:changes (rest updates)} current-market))))))
                                     

(defn stamp-market-msg [market-msg product]
  (assoc market-msg
         :market "gdax"
         :product product
         :timestamp (quot (System/currentTimeMillis) 1000)
         :timestampPretty (str (new java.util.Date))))


              
(defn subscribe-l2market [ch product]
  (let [market (hash-map :buy [] :sell [])
        update-chan (chan)
        conn (gdax-ws-subscribe (create-ws-subscribe-req [product] ["level2"]) update-chan)]
    (go-loop [current-market market
           update (<! update-chan)]
      (cond (= (:type update) "snapshot")
            (let [updated-market (l2-process-snapshot update)]
              (>! ch (stamp-market-msg updated-market product))
              (recur updated-market (<! update-chan)))
            (= (:type update) "l2update")
            (let [updated-market (l2-process-update update current-market)]
              (>! ch (stamp-market-msg updated-market product))
              (recur updated-market (<! update-chan)))
            :else (recur current-market (<! update-chan))))
    (fn []
      (ws/close conn))))



(defn subscribe-ticker [ch product]
  (let [update-chan (chan)
        conn (gdax-ws-subscribe (create-ws-subscribe-req [product] ["ticker"]) update-chan)]
    (go-loop []
      (let [update (<! update-chan)]
        (cond (= (:type update) "ticker")
              (do
                (>! ch update)
                (recur))
              :else (recur))))
    (fn []
      (ws/close conn))))



(defn get-market []
  (reify m/Market
    (ticker [_ c product]
      (subscribe-ticker c product))
    (orderbook [_ c product]
      (subscribe-l2market c product))
    (orderbooksnapshot [_ product]
      (get-product-order-book product 2))
    (ohlc [_ product timestamp divisions]
      (println "returning gdax ohcl"))))
