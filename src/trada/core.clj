(ns trada.core
  (:require
   [clojure.core.async :refer [go chan <! <!! >! >!! timeout go-loop]]
   [trada.market :as market]
   [trada.market.gdax :as gdax]
   [trada.market.binance :as binance]))


(def gdax-ticker-chan (chan))
(def binance-ticker-chan (chan))

(def gdax-eth-btc (atom 0))
(def binance-eth-btc (atom 0))


(defn compare-prices []
  (println (str "Binance -> " @binance-eth-btc))
  (println (str "GDAX    -> " @gdax-eth-btc))
  (println (str "   diff -> "
                (format "%.6f" (Math/abs (- @binance-eth-btc @gdax-eth-btc)))
                (cond (< @binance-eth-btc @gdax-eth-btc)
                      " (gdax)"
                      (> @binance-eth-btc @gdax-eth-btc)
                      " (binance)"
                      :else " (equal)")
                "\n\n")))


(defn -main [& args]
  (let [gdax (gdax/get-market)
        gdax-ticker-closer (market/ticker gdax gdax-ticker-chan "ETH-BTC")
        binance (binance/get-market)
        binance-ticker-closer (market/ticker binance binance-ticker-chan "ethbtc")]
    (go-loop [msg-count 1]
      (let [msg (<! gdax-ticker-chan)]
        (reset! gdax-eth-btc (Double/valueOf (:price msg)))
        (compare-prices))
      (recur (inc msg-count)))
    (go-loop [msg-count 1]
      (let [msg (<! binance-ticker-chan)]
        (reset! binance-eth-btc (Double/valueOf (:p msg)))
        (compare-prices))
      (recur (inc msg-count)))))

      



