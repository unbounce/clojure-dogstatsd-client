(ns com.unbounce.dogstatsd.core-test
  (:require [clojure.spec.alpha :as s]
            [com.unbounce.dogstatsd.core :as sut]
            [clojure.test :refer [deftest testing is are] :as t]
            [clojure.spec.test.alpha :as stest]))

(s/def ::sample-rate number?)
(s/def ::tags (s/coll-of string?))
(s/def ::opts (s/keys :opt-un [::tags ::sample-rate]))

(s/fdef sut/str-array
        :args (s/cat :tags ::tags)
        :ret #(instance? java.lang.Object %))

(s/fdef sut/increment
        :args (s/cat :metric string?
                     :opts (s/? ::opts))
        :ret nil?)

(s/fdef sut/decrement
        :args (s/cat :metric string?
                     :opts (s/? ::opts))
        :ret nil?)


(s/fdef sut/gauge
        :args (s/cat :metric string?
                     :value number?
                     :opts (s/? ::opts))
        :ret nil?)


(s/fdef sut/histogram
        :args (s/cat :metric string?
                     :value number?
                     :opts (s/? ::opts))
        :ret nil?)

(deftest datadog-statsd-metrics
  (testing "datadog monitoring functions"
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

    (are [x y] (= x y)
      nil (sut/increment "asdf")
      nil (sut/increment "asdf" {:tags ["asdf"] :sample-rate 1})

      nil (sut/decrement "asdf")
      nil (sut/decrement "asdf" {:tags ["asdf"] :sample-rate 1})

      nil (sut/gauge "asdf" 20)
      nil (sut/gauge "asdf" 20 {:tags ["asdf" "asdf"] :sample-rate 1})

      nil (sut/histogram "asdf" 20)
      nil (sut/histogram "asdf" 20 {:tags ["asdf" "asdf"] :sample-rate 1}))))

(deftest time!
  (let [result (atom nil)]
    (with-redefs [sut/histogram (fn [m v & opt] (reset! result m))]
      (testing "time! records duration of body"
        (sut/time! ["foo"]
          (Thread/sleep 100))
        (is (= "foo" @result)))

      (testing "time! records when exception is raised"
        (is (thrown? RuntimeException
                     (sut/time! ["bar"]
                       (Thread/sleep 100)
                       (throw (RuntimeException. "bar")))))
        (is (= "bar" @result))))))
