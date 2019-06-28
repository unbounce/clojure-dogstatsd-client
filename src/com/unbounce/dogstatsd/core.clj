(ns com.unbounce.dogstatsd.core
  (:import [com.timgroup.statsd
            StatsDClient NonBlockingStatsDClient NoOpStatsDClient
            Event Event$Priority Event$AlertType
            ServiceCheck ServiceCheck$Status]))

;; In case setup! is not called, this prevents nullpointer exceptions i.e. Unit tests
(defonce ^:private ^StatsDClient client (NoOpStatsDClient.))

(def ^:private ^"[Ljava.lang.String;" -empty-array (into-array String []))

(defn ^"[Ljava.lang.String;" str-array [tags]
  (into-array String tags))

(defn shutdown!
  "Cleanly stops the statsd client.

  May throw an exception if the socket cannot be closed."
  []
  (when client
    (.stop client)))

(defn setup!
  "Sets up the statsd client.

  If :host is not explicitly specified, it will defer to the environment variable DD_AGENT_HOST.
  If :port is not explicitly specified, it will first try to read it from the env var DD_DOGSTATSD_PORT, and then fallback to the default port 8125.

  Examples:

  To setup the client on host \"some-other-post\" and port 1234

    (setup! :host \"some-other-host\" :port 1234)

  To setup the client once and avoid reloading it, use :once?

    (setup! :host \"some-other-host\" :once? true)
  "
  [& {:keys [^String host ^long port ^String prefix tags once?]}]
  (when-not (and client once?)
    (shutdown!)
    (alter-var-root #'client (constantly
                              (com.timgroup.statsd.NonBlockingStatsDClient.
                               prefix
                               host
                               (or port 0)
                               (str-array tags))))))

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

  (statsd/time! [\"my.metric.with.tags\" {:tags #{\"foo\"} :sample-rate 0.3}]
    (Thread/sleep 1000))

  "
  {:style/indent 1}
  [[metric opts] & body]
  `(let [t0#  (System/currentTimeMillis)]
     (try
       ~@body
       (finally
         (histogram ~metric (- (System/currentTimeMillis) t0#) ~opts)))))

(defn event
  "Records an Event.

  This is a datadog extension, and may not work with other servers.

  See http://docs.datadoghq.com/guides/dogstatsd/#events-1

  Note:
  :date can either be inst? or msecs
  :priority can be either :normal or :low
  "
  [{:keys [title text timestamp hostname aggregation-key priority source-type-name alert-type]} tags]
  {:pre [(not (nil? title)) (not (nil? text))]}
  (let [timestamp (or timestamp -1)
        event (-> (Event/builder)
                  (.withTitle title)
                  (.withText text)
                  (.withHostname hostname)
                  (.withAggregationKey aggregation-key)
                  (.withPriority (if priority
                                   (Event$Priority/valueOf
                                    (name priority))
                                   Event$Priority/NORMAL))
                  (.withSourceTypeName source-type-name)
                  (.withAlertType (if alert-type
                                    (Event$AlertType/valueOf
                                     (name source-type-name))
                                    Event$AlertType/INFO))
                  (.withDate ^long timestamp)
                  (.build))]
    (.recordEvent client event (str-array tags))))

(defn service-check
  "Records a ServiceCheck. This is a datadog extension, and may not work with
  other servers.

  See https://docs.datadoghq.com/agent/agent_checks/#sending-service-checks

  At minimum, the name and status is required in the payload.
  "
  [{:keys [name status hostname message check-run-id timestamp]} tags]
  {:pre [(not (nil? name)) (not (nil? status))]}
  (let [service-check
        (-> (com.timgroup.statsd.ServiceCheck/builder)
            (.withName name)
            (.withStatus (case status
                           :ok       ServiceCheck$Status/OK
                           :warning  ServiceCheck$Status/WARNING
                           :critical ServiceCheck$Status/CRITICAL
                           :unknown  ServiceCheck$Status/UNKNOWN))
            (.withHostname hostname)
            (.withMessage message)
            (.withCheckRunId (or check-run-id 0))
            (.withTimestamp (or timestamp 0))
            (.withTags (str-array tags))
            (.build))]
    (.recordServiceCheckRun client service-check)))

(defn set-value
  [metric value {:keys [tags]}]
  (.recordSetValue client metric value tags))

(comment
  (setup! :host "localhost" :port 8125 :tags #{"hello" "world"})

  ;; In a terminal, setup `nc -u -l 8125` to watch for events
  (event {:title "foo" :text "things are bad\nfoo"} nil)
  (increment "foo.bar")

  (service-check {:name "hi" :status :warning} nil)
  (service-check {:name "hi" :status :ok :timestamp 10 :check-run-id 123 :message "foo" :hostname "blah"} nil)

  )
