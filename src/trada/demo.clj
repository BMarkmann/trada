(ns trada.demo
  (:require
   [clojure.core.async :refer [go chan <! <!! >! >!! timeout go-loop]]
   [trada.market :as market]
   [trada.market.gdax :as gdax]
   [trada.market.binance :as binance]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [jutsu.core :as j]))

;; (defn -main [& args]
;;   ;; (def gdax (gdax/get-market))
;;   ;; (market/orderbook gdax gdax-orderbook-chan "ETH-USD")
;;   (let [order-chan (chan 1000)
;;         close-fn (gdax/subscribe-l2market order-chan "ETH-USD")
;;         program-start-time (. System (currentTimeMillis))]
;;     (go-loop [msg-count 1]
;;       (<! order-chan)
;;       (when (= (rem msg-count 1000) 0)
;;         (println (str "processed " msg-count " messages in "
;;                       (- (. System (currentTimeMillis)) program-start-time) "ms")))
;;       (recur (inc msg-count)))))

;; (defn -main [& args]
;;   (let [ticker-chan (chan 1000)
;;         close-fn (gdax/subscribe-ticker ticker-chan "ETH-USD")
;;         program-start-time (. System (currentTimeMillis))]
;;     (go-loop [msg-count 1]
;;       (println (<! ticker-chan))
;;       (recur (inc msg-count)))))

;; (def gdax (gdax/get-market))
;; (def gdax-eth-orderbook-chan (chan))
;; (def gdax-eth-ticker-chan (chan))

;; (defn -main [& args]
;;   (let [eth-orderbook-closer (market/orderbook gdax "ETH-USD" gdax-eth-orderbook-chan)
;;         eth-ticker-closer (market/ticker gdax "ETH-USD" gdax-eth-ticker-chan)]
;;     (go-loop [ob-msg-count 1]
;;       (let [ob (<! gdax-eth-orderbook-chan)]
;;         (when (= (rem ob-msg-count 1000) 0)
;;           (println (str "processed " ob-msg-count " orderbook messages (ETH / USD)"))))
;;       (recur (inc ob-msg-count)))
;;     (go-loop [t-msg-count 1]
;;       (<! gdax-eth-ticker-chan)
;;       (when (= (rem t-msg-count 10) 0)
;;         (println (str "processed " t-msg-count " ticker messages (ETH / GDAX)")))
;;       (recur (inc t-msg-count)))))
            

;; (def binance-orderbook-chan (chan))

(def gdax-orderbook-chan (chan))
(def gdax-ticker-chan (chan))

(def binance-orderbook-chan (chan))

;; (defn -main [& args]
;;   (let [binance-closer (binance/subscribe-l2market binance-orderbook-chan "ethbtc")
;;         gdax-closer (gdax/subscribe-l2market gdax-orderbook-chan "ETH-BTC")]
;;     (go-loop [msg-count-b 1]
;;       (let [msg-b (<! binance-orderbook-chan)]
;;         (println (str "binance -> " msg-count-b " -> " (:timestampPretty msg-b)))
;;         (when (= (rem msg-count-b 10) 0)
;;           (with-open [writer (io/writer (str "/tmp/b-" msg-count-b ".csv"))]
;;             (->> (map #(let [price (first %)
;;                              volume (second %)]
;;                          ["buy" price volume]) (:buy msg-b))
;;                  (csv/write-csv writer))
;;             (->> (map #(let [price (first %)
;;                              volume (second %)]
;;                          ["sell" price volume]) (:sell msg-b))
;;                  (csv/write-csv writer))))
;;         (recur (inc msg-count-b))))
;;     (go-loop [msg-count-g 1]
;;       (let [msg-g (<! gdax-orderbook-chan)]
;;         (println (str "gdax -> " msg-count-g " -> " (:timestampPretty msg-g)))
;;         (recur (inc msg-count-g))))))

;; (def test-dataset (read-dataset "/tmp/b-10.csv"))
;; (view (scatter-plot
;;         (sel test-dataset :cols 1)
;;         (sel test-dataset :cols 2)
;;         :group-by (sel test-dataset :cols 0)))



(defn jutsu-gdax-orderbook []
  (go-loop [msg-count 1]
    (let [msg (<! gdax-orderbook-chan)
          bids (:buy msg)
          max-bid (reduce #(max %1 %2) (bigdec 0) (map first bids))
          filtered-bids (filter #(> (first %) (* 0.99 max-bid)) bids)
          asks (:sell msg)
          min-ask (reduce #(min %1 %2) (bigdec 9999999) (map first asks))
          filtered-asks (filter #(< (first %) (* 1.01 min-ask)) asks)]
      (println (str "-main handling iteration " msg-count))
      (when (= msg-count 100)
        (j/graph!
         "eth-btc-bid"
         [{:x (map #(first %) filtered-bids)
           :y (map #(second %) filtered-bids)
           :mode "markers"
           :type "bar"}
          {:x (map #(first %) filtered-asks)
           :y (map #(second %) filtered-asks)
           :mode "markers"
           :type "bar"}]))
      (println (str "max bid: " max-bid " / displaying " (count filtered-bids)))
      (println (str "min ask: " min-ask " / displaying " (count filtered-asks)))
      (when (and (= (rem msg-count 10) 0) (> msg-count 101))
        (j/update-graph!
         "eth-btc-bid"
         {:data {:x [(map #(first %) filtered-bids) (map #(first %) filtered-asks)]
                 :y [(map #(second %) filtered-bids) (map #(second %) filtered-asks)]}
          :traces [0 1]}))
      (recur (inc msg-count)))))

(defn handle-gdax-ticker []
  (go-loop [msg-count 1]
    (let [msg (<! gdax-ticker-chan)]
      (println (str msg)))
    (recur (inc msg-count))))



(defn demo-jutsu-orderbook []
  (j/start-jutsu! 4002 true)
  (let [gdax (gdax/get-market)
        gdax-ob-closer (market/orderbook gdax gdax-orderbook-chan "ETH-BTC")]
    (jutsu-gdax-orderbook)
    gdax-ob-closer))


;; (def gdax-ob-closer (demo-jutsu-orderbook))
;; (gdax-ob-closer)


(defn demo-gdax-ticker []
  (let [gdax (gdax/get-market)
        gdax-ticker-closer (market/ticker gdax gdax-ticker-chan "ETH-USD")]
    (go-loop [t-msg-count 1]
      (let [ticker-msg (<! gdax-ticker-chan)]
        (println (str "processed " t-msg-count " ticker messages (ETH / GDAX)"))
        (println (str ticker-msg))
        (recur (inc t-msg-count))))
    gdax-ticker-closer))


;; (def gdax-ticker-closer (demo-gdax-ticker))
;; (gdax-ticker-closer)


(defn demo-gdax-snapshot []
  (let [gdax (gdax/get-market)]
    (println (str (market/orderbooksnapshot gdax "ETH-USD")))))


;; (demo-gdax-snapshot)


(defn demo-gdax-24hr-stats []
  (let [gdax (gdax/get-market)]
    (println (str (gdax/get-product-24hr-stats "ETH-USD")))))

;; (demo-gdax-24hr-stats)


(defn demo-binanace-orderbook []
  (let [binance (binance/get-market)
        binance-ob-closer (market/orderbook binance binance-orderbook-chan "adaeth")]
    (go-loop [msg (<! binance-orderbook-chan)]
      (println (str msg))
      (recur (<! binance-orderbook-chan)))
    binance-ob-closer))



