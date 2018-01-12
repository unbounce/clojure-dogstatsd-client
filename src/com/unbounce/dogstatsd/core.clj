(ns com.unbounce.dogstatsd.core
  (:import [com.timgroup.statsd StatsDClient NonBlockingStatsDClient ServiceCheck]))

(defonce ^:private * ^StatsDClient client nil)

(defn setup!
  "Sets up the statsd client.

  By default, assumes localhost and port 8125. You can change the host and port with:

    (setup! :host \"some-other-host\" :port 1234)

  To setup the client once and avoid reloading it, use :once?

    (setup! :once? true)

  "
  [& {:keys [^String host ^long port ^String prefix #^"[Ljava.lang.String;" tags once?]}]
  (when-not (and client once?)
    (when client
      (.stop client))
    (alter-var-root #'client (constantly
                              (com.timgroup.statsd.NonBlockingStatsDClient.
                               prefix
                               (or host "localhost")
                               (or port 8125)
                               tags)))))

(defn increment
  [metric & {:keys [tags sample-rate]}]
  (if sample-rate
    (.incrementCounter client metric sample-rate tags)
    (.incrementCounter client metric tags)))

(defn decrement
  [metric & {:keys [tags sample-rate]}]
  (if sample-rate
    (.decrementCounter client metric sample-rate tags)
    (.decrementCounter client metric tags)))

(defn gauge
  [^String metric ^double value {:keys [^double sample-rate #^"[Ljava.lang.String;" tags]}]
  (if sample-rate
    (.recordGaugeValue client metric value sample-rate tags)
    (.recordGaugeValue client metric value tags)))

(defn histogram
  [^String metric ^double value {:keys [^double sample-rate  #^"[Ljava.lang.String;" tags]}]
  (if sample-rate
    (.recordHistogramValue client metric value sample-rate tags)
    (.recordHistogramValue client metric value tags)))

(defmacro time!
  "Times the body and submits the execution time to metric"
  [metric opts & body]
  `(let [t0#  (System/currentTimeMillis)
         res# (do ~@body)]
     (histogram ~metric (- (System/currentTimeMillis) t0#) ~opts)
     res#))

;; (defn event
;;   [event {:keys [tags]}]
;;   (.recordEvent event tags))

;; (defn service-check [{:keys [name hostname message check-run-id timestamp status tags]}]
;;   (let [service-check
;;         (-> (com.timgroup.statsd.ServiceCheck/builder)
;;             (.withName name)
;;             (.withHostname hostname)
;;             (.withMessage message)
;;             (.withCheckRunId check-run-id)
;;             (.withTimestamp timestamp)
;;             (.withStatus (when status
;;                            (com.timgroup.statsd.ServiceCheck$Status/valueOf
;;                             (clojure.core/name status))))
;;             (.withTags tags)
;;             (.build))]
;;     (.recordServiceCheckRun client service-check)))

(defn set-value
  [metric value {:keys [tags]}]
  (.recordSetValue client metric value tags))

(comment
  (setup!)
  )
