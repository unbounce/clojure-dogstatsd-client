(ns com.unbounce.dogstatsd.ring-test
  (:require [com.unbounce.dogstatsd.ring :as sut]
            [clojure.test :refer [deftest testing is are] :as t]
            [com.unbounce.dogstatsd.core :as dogstatsd.core]))

(deftest status-code-metric
  (are [metric status] (= metric (sut/status-code-metric status))
    "http.2xx" 200
    "http.2xx" 201
    "http.2xx" 299
    "http.3xx" 301
    "http.4xx" 404
    "http.5xx" 503))

(deftest sample?
  (dotimes [i 20]
    (is (false? (sut/sample? 0))))
  (dotimes [i 20]
    (is (true? (sut/sample? 100))))
  (is (true? (sut/sample? nil))))

(deftest wrap-http-metrics
  (let [response (atom nil)
        metrics  (atom {})
        handler  (sut/wrap-http-metrics (fn [_] @response))]
    (with-redefs [dogstatsd.core/increment (fn [metric & opts]
                                             (swap! metrics assoc metric 1))
                  dogstatsd.core/histogram (fn [metric value & opts]
                                             (swap! metrics assoc metric value))]
      (testing "on 2xx response"
        (reset! response {:status 201})
        (is (= {:status 201} (handler {:method :get})))
        (is (= #{"http.count" "http.duration" "http.2xx"}
               (set (keys @metrics)))))

      (testing "on 3xx response"
        (reset! metrics {})
        (reset! response {:status 301})
        (is (= {:status 301} (handler {:method :get})))
        (is (= #{"http.count" "http.duration" "http.3xx"}
               (set (keys @metrics)))))

      (testing "on 4xx response"
        (reset! metrics {})
        (reset! response {:status 401})
        (is (= {:status 401} (handler {:method :get})))
        (is (= #{"http.count" "http.duration" "http.4xx"}
               (set (keys @metrics)))))

      (testing "on 5xx response"
        (reset! metrics {})
        (reset! response {:status 503})
        (is (= {:status 503} (handler {:method :get})))
        (is (= #{"http.count" "http.duration" "http.5xx"}
               (set (keys @metrics)))))

      (testing "on healthcheck request"
        (reset! metrics {})
        (reset! response {:status 200})
        (is (= {:status 200}
               (handler {:method :get :headers {"user-agent" "ELB-HealthChecker"}})))
        (is (empty? @metrics)))

      (testing "captures exception metrics"
        (reset! metrics {})
        (let [handler (sut/wrap-http-metrics (fn [_] (throw (ex-info "Derp!" {}))))]
          (try (handler {:method :get})
               (catch Exception ex ))
          (is (= #{"http.count" "http.duration" "http.exception"}
                 (set (keys @metrics)))))))))
