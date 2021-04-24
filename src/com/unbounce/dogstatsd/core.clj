(ns com.unbounce.dogstatsd.core
  (:require [clojure.string :as string])
  (:import [com.timgroup.statsd
            StatsDClient NoOpStatsDClient
            Event Event$Priority Event$AlertType
            ServiceCheck ServiceCheck$Status NonBlockingStatsDClientBuilder]))

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
                               (-> (NonBlockingStatsDClientBuilder.)
                                   (.prefix prefix)
                                   (.hostname host)
                                   (.port (or port 0))
                                   (.constantTags (str-array tags))
                                   (.build))))))

(defn increment
  ([metric]
   (increment metric {}))
  ([metric {:keys [tags sample-rate by] :or {by 1.0}}]
   (let [tags (str-array tags)]
     (if sample-rate
       (.count client ^String metric ^double by ^double sample-rate tags)
       (.count client ^String metric ^double by tags)))))

(defn decrement
  ([metric]
   (decrement metric {}))
  ([metric {:keys [tags sample-rate by] :or {by 1.0}}]
   (let [tags (str-array tags)]
     (if sample-rate
       (.count client ^String metric ^double (- by) ^double sample-rate tags)
       (.count client ^String metric ^double (- by) tags)))))

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

(defn- enum-value [s]
  (string/upper-case (name s)))

(defn event
  "Records an Event.

  This is a datadog extension, and may not work with other servers.

  See http://docs.datadoghq.com/guides/dogstatsd/#events-1

  :date can either be inst? or msecs
  :priority    one of :normal or low. If unset, defaults to :normal
  :alert-type  one of :error, :warning, :info, or :success. If unset, defaults to :info
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
                                    (enum-value priority))
                                   Event$Priority/NORMAL))
                  (.withSourceTypeName source-type-name)
                  (.withAlertType (if alert-type
                                    (Event$AlertType/valueOf
                                     (enum-value alert-type))
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

  ;; With alert-type
  (event {:title "foo" :text "things are bad\nfoo" :alert-type :warning} nil)
  (event {:title "foo" :text "things are bad\nfoo" :alert-type :WARNING} nil)

  ;; With priority
  (event {:title "foo" :text "things are bad\nfoo" :priority :low} nil)
  (event {:title "foo" :text "things are bad\nfoo"} nil)

  (increment "foo.bar")

  (service-check {:name "hi" :status :warning} nil)
  (service-check {:name "hi" :status :ok :timestamp 10 :check-run-id 123 :message "foo" :hostname "blah"} nil)

  )
