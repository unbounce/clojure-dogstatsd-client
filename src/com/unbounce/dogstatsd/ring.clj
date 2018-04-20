(ns com.unbounce.dogstatsd.ring
  "Ring middleware for instrumenting HTTP request/responses.

  The middleware provides the following dogstatsd metrics:

  - http.1xx  - counter of 1xx responses
  - http.2xx  - counter of 2xx responses
  - http.3xx  - counter of 3xx responses
  - http.4xx  - counter of 4xx responses
  - http.5xx  - counter of 5xx responses
  - http.count     - counter for total requests
  - http.exception - counter for exceptions raised
  - http.duration  - histogram of request duration

  See `wrap-ring-metric` for more information.
  "
  (:require [com.unbounce.dogstatsd.core :as statsd])
  (:import [java.util.concurrent ThreadLocalRandom]))

(defn healthcheck-request?
  "Returns true if the healthcheck request is for a known health-check.

  This function is aware of the following health-checks
  - AWS ELB HealthChecks
  - Pingdom
  "
  [request]
  (let [user-agent (get-in request [:headers "user-agent"] "-")]
    (re-matches #"(?i).*(ELB-HealthChecker|Pingdom).*" user-agent)))

(defn status-code-metric [status]
  (cond (<= 100 status 199)
        "http.1xx"

        (<= 200 status 299)
        "http.2xx"

        (<= 300 status 399)
        "http.3xx"

        (<= 400 status 499)
        "http.4xx"

        (<= 500 status 599)
        "http.5xx"))

(defn wrap-http-metrics
  "Wraps HTTP status-code-metrics for your application.

  You should put this middleware at the top of your middleware stack to get the
  most visibility.

  The middleware provides the following dogstatsd metrics:

  - http.1xx  - counter of 1xx responses
  - http.2xx  - counter of 2xx responses
  - http.3xx  - counter of 3xx responses
  - http.4xx  - counter of 4xx responses
  - http.5xx  - counter of 5xx responses
  - http.count     - counter for total requests
  - http.exception - counter for exceptions raised
  - http.duration  - histogram of request duration

  Options is a map of:
  - `loggable?` A predicate that filters a request from being measured. By default, this is `(component healthcheck-request?)`
  - `sample-rate` A float between 0 and 1. Sets the rate of requests to sample.
  - `tags` vector of additional tags to add to the metrics
  "
  ([handler]
   (wrap-http-metrics handler nil))
  ([handler {:keys [sample-rate tags loggable?] :as options}]
   (let [loggable? (or loggable? (complement healthcheck-request?))]
     (fn
       ([request]
        (if-not (loggable? request)
          (handler request)
          (let [start (System/currentTimeMillis)]
            (statsd/increment "http.count" options)
            (try
              (let [response (handler request)]
                (statsd/increment (status-code-metric (:status response)) options)
                response)
              (catch Exception ex
                (statsd/increment "http.exception" options)
                (throw ex))
              (finally
                (let [elapsed (- (System/currentTimeMillis) start)]
                  (statsd/histogram "http.duration" elapsed options)))))))

       ([request respond raise]
        (if-not (loggable? request)
          (let [start (System/currentTimeMillis)]
            (statsd/increment "http.count" options)
            (handler request
                     #(do (statsd/increment (status-code-metric (:status %)) options)
                          (respond %)
                          (let [elapsed (- (System/currentTimeMillis) start)]
                            (statsd/histogram "http.duration" elapsed options)))
                     #(do (statsd/increment "http.exception" options)
                          (raise %))))))))))
