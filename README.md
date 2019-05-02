# clojure-statsd-client

[![Clojars Project](https://img.shields.io/clojars/v/com.unbounce/clojure-dogstatsd-client.svg)](https://clojars.org/com.unbounce/clojure-dogstatsd-client) [![CircleCI](https://circleci.com/gh/unbounce/clojure-dogstatsd-client/tree/master.svg?style=svg)](https://circleci.com/gh/unbounce/clojure-dogstatsd-client/tree/master)

A thin veneer over the officia Java dogstatsd
[client](https://github.com/DataDog/java-dogstatsd-client). This library favours
pragmatism where possible.

Instrumenting your code should be easy, you shouldn't be forced to thread a
statsd client in your code. This library keeps a global client around so your
application doesn't need to know about it.

## Usage

Somewhere in your code, you should setup the client:

``` clojure
(require '[com.unbounce.dogstatsd.core :as statsd])

;; Do this once in your code
;; Or statd calls will default to use NoOpStatsDClient to avoid nullpointer exception
(statsd/setup! :host "127.0.0.1" :port 8125 :prefix "my.app")

;; Increment or derement a counter
(statsd/increment "counter")
(statsd/decrement "another.counter")

;; Records a value at given time
(statsd/gauge "a.gauge" 10)

;; Record a histogram value (i.e for measuring percentiles)
(statsd/histogram "a.histogram" 10)

;; Time how long body takes and records it to the metric
(statsd/time! ["a.timed.body" {}]
  (Thread/sleep 100)
  (Thread/sleep 100))

;; Shutdown client to ensure all messages are emitted to statsd and resources are cleaned up
(statsd/shutdown!)
```

### Ring Middleware

This library also has comes with a ring middleware to capture HTTP requests.
See `com.unbounce.dogstatsd.ring` for more information.

The middleware provides these metrics:

- http.1xx  counter of 1xx responses
- http.2xx  counter of 2xx responses
- http.3xx  counter of 3xx responses
- http.4xx  counter of 4xx responses
- http.5xx  counter of 5xx responses
- http.count     counter for total requests
- http.exception counter for exceptions raised
- http.duration  histogram of request duration

Usage:

```
(require '[com.unbounce.dogstatsd.ring :as dogstatsd.ring])

;; by default instrument all requests
(def handler (-> (constantly {:status 200})
                 (dogstatsd.ring/wrap-http-metrics)))

;; when sample-rate is set, only 20% of requests will be instrumented
(def handler (-> (constantly {:status 200})
                 (dogstatsd.ring/wrap-http-metrics {:sample-rate 0.2})))
                 
```



## License

Copyright © 2018 Unbounce Marketing Solutions Inc.

Distributed under the MIT License.
