(ns com.unbounce.dogstatsd.core-test
  (:require [clojure.spec.alpha :as s]
            [com.unbounce.dogstatsd.core :as sut]
            [clojure.test :as t]
            [clojure.spec.test.alpha :as stest]))

(s/def ::sample-rate number?)
(s/def ::metric string?)
(s/def ::tags (s/coll-of string? :kind vector?))
(s/def ::opts (s/keys :req-un [::tags ::sample-rate]))
(s/def ::opts* (s/keys* :req-un [::tags ::sample-rate]))

(s/fdef sut/str-array
        :args (s/or :tags (s/cat :tags ::tags)
                    :no-tag (s/cat :tags nil?))
        :ret #(instance? java.lang.Object %))

(s/fdef sut/increment
        :args (s/cat :metric ::metric
                     :opts ::opts*)
        :ret nil?)

(s/fdef sut/decrement
        :args (s/cat :metric ::metric
                     :opts ::opts*)
        :ret nil?)

(s/fdef sut/gauge
        :args (s/cat :metric string?
                     :value number?
                     :opts ::opts)
        :ret nil?)

(s/fdef sut/histogram
        :args (s/cat :metric string?
                     :value number?
                     :opts ::opts)
        :ret nil?)

(t/deftest datadog-statsd-metrics
  (t/testing "datadog monitoring functions"
    (stest/instrument `sut/str-array)
    (stest/instrument `sut/increment)
    (stest/instrument `sut/decrement)
    (stest/instrument `sut/gauge)
    (stest/instrument `sut/histogram)

    (stest/check `sut/str-array)
    (stest/check `sut/increment)
    (stest/check `sut/decrement)
    (stest/check `sut/gauge)
    (stest/check `sut/histogram)

    (t/are [x y] (= x y)
      nil (sut/increment "asdf" :tags ["asdf"] :sample-rate 1)
      nil (sut/decrement "asdf" :tags ["asdf"] :sample-rate 1)
      nil (sut/gauge "asdf" 20 {:tags ["asdf" "asdf"] :sample-rate 1})
      nil (sut/histogram "asdf" 20 {:tags ["asdf" "asdf"] :sample-rate 1}))))
