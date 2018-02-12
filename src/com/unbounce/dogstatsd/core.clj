(ns com.unbounce.dogstatsd.core
  (:import [com.timgroup.statsd StatsDClient NonBlockingStatsDClient ServiceCheck NoOpStatsDClient]))

;; In case setup! is not called, this prevents nullpointer exceptions i.e. Unit tests
(defonce ^:private ^StatsDClient client (NoOpStatsDClient.))

(def ^:private ^"[Ljava.lang.String;" -empty-array (into-array String []))

(defn ^"[Ljava.lang.String;" str-array [tags]
  (into-array String tags))

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
  ([metric]
   (.incrementCounter client metric -empty-array))
  ([metric {:keys [tags sample-rate]}]
   (let [tags (str-array tags)]
     (if sample-rate
       (.incrementCounter client metric sample-rate tags)
       (.incrementCounter client metric tags)))))

(defn decrement
  ([metric]
   (.decrementCounter client metric -empty-array))
  ([metric {:keys [tags sample-rate]}]
   (let [tags (str-array tags)]
     (if sample-rate
       (.decrementCounter client metric sample-rate tags)
       (.decrementCounter client metric tags)))))

(defn gauge
  ([^String metric ^Double value]
   (.recordGaugeValue client metric value -empty-array))
  ([^String metric ^Double value {:keys [sample-rate tags]}]
   (let [tags        (str-array tags)
         sample-rate ^Double sample-rate]
     (if sample-rate
       (.recordGaugeValue client metric value sample-rate tags)
       (.recordGaugeValue client metric value tags)))))

(defn histogram
  ([^String metric ^Double value]
   (.recordHistogramValue client metric value -empty-array))
  ([^String metric ^Double value {:keys [sample-rate tags]}]
   (let [tags        (str-array tags)
         sample-rate ^Double sample-rate]
     (if sample-rate
       (.recordHistogramValue client metric value sample-rate tags)
       (.recordHistogramValue client metric value tags)))))

(defmacro time!
  "Times the body and records the execution time (in msecs) as a histogram.

  Takes opts is a map of :sample-rate and :tags to apply to the histogram

  Examples:

  (statsd/time! [\"my.metric\"]
    (Thread/sleep 1000))

  (statsd/time! [\"my.metric.with.tags\" {:tags #{\"foo\" :sample-rate 0.3}}]
    (Thread/sleep 1000))

  "
  [[metric opts] & body]
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
