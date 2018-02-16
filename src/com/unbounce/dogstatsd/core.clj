(ns com.unbounce.dogstatsd.core
  (:import [com.timgroup.statsd
            StatsDClient NonBlockingStatsDClient NoOpStatsDClient
            Event Event$Priority Event$AlertType
            ServiceCheck ServiceCheck$Status]))

;; In case setup! is not called, this prevents nullpointer exceptions i.e. Unit tests
(defonce ^:private ^StatsDClient client
  (NoOpStatsDClient.))

(defn str-array [tags]
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
  [metric & {:keys [tags sample-rate]}]
  (let [tags (str-array tags)]
    (if sample-rate
      (.incrementCounter client metric sample-rate tags)
      (.incrementCounter client metric tags))))

(defn decrement
  [metric & {:keys [tags sample-rate]}]
  (let [tags (str-array tags)]
  (if sample-rate
    (.decrementCounter client metric sample-rate tags)
    (.decrementCounter client metric tags))))

(defn gauge
  [metric value {:keys [sample-rate tags]}]
  (let [value       (double value)
        tags        (str-array tags)
        sample-rate (when sample-rate (double sample-rate))
        f           (fn [^String metric ^double value {:keys [^double sample-rate #^"[Ljava.lang.String;" tags]}]
                      (if sample-rate
                        (.recordGaugeValue client metric value sample-rate tags)
                        (.recordGaugeValue client metric value tags)))]
    (f metric value {:sample-rate sample-rate
                     :tags tags})))

(defn histogram
  [metric value {:keys [sample-rate tags]}]
  (let [value       (double value)
        tags        (str-array tags)
        sample-rate (when sample-rate (double sample-rate))
        f           (fn [^String metric ^double value {:keys [^double sample-rate  #^"[Ljava.lang.String;" tags]}]
                      (if sample-rate
                        (.recordHistogramValue client metric value sample-rate tags)
                        (.recordHistogramValue client metric value tags)))]
    (f metric value {:sample-rate sample-rate
                     :tags        tags})))

(defmacro time!
  "Times the body and submits the execution time to metric"
  [metric opts & body]
  `(let [t0#  (System/currentTimeMillis)
         res# (do ~@body)]
     (histogram ~metric (- (System/currentTimeMillis) t0#) ~opts)
     res#))

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
  (setup!)

  (event {:title "foo" :text "things are bad\nfoo"} nil)

  (service-check {:name "hi" :status :warning} nil)
  (service-check {:name "hi" :status :ok :timestamp 10 :check-run-id 123 :message "foo" :hostname "blah"} nil)

  )
