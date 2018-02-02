# clojure-statsd-client

```
[com.unbounce/clojure-dogstatsd-client "0.1.0"]
```

[![Clojars Project](https://img.shields.io/clojars/v/com.unbounce/clojure-dogstatsd-client.svg)](https://clojars.org/com.unbounce/clojure-dogstatsd-client)

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
(statsd/time "a.timed.body" {}
  (Thread/sleep 100)
  (Thread/sleep 100))
```

## License

Copyright © 2018 Unbounce Marketing Solutions Inc.

Distributed under the MIT License.
