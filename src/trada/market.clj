(ns trada.market)

(defprotocol Market
  (ticker [this product c])
  (orderbook [this product c])
  (orderbooksnapshot [this product])
  (ohlc [this product timestamp divisions]))
