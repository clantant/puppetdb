(ns com.puppetlabs.puppetdb.test.reports
  (:use [clojure.test]
        [clj-time.coerce :only [to-timestamp]]
        [com.puppetlabs.puppetdb.examples.reports]
        [com.puppetlabs.puppetdb.reports]
        [com.puppetlabs.puppetdb.testutils.reports
          :only [munge-example-report-for-storage
                 munge-v2-example-report-to-v1
                 munge-v1-example-report-to-v2]])
  (:require [puppetlabs.kitchensink.core :as kitchensink]
            [cheshire.core :as json]))

(let [report (munge-example-report-for-storage (:basic reports))]

  (deftest test-validate!

    (testing "should accept a valid v1 report"
      (let [v1-report (munge-v2-example-report-to-v1 report)
            v2-report (munge-v1-example-report-to-v2 v1-report)]
        (is (= v2-report (validate! 1 v1-report)))))

    (testing "should fail when a v1 report has a v2 key"
      (let [add-key-fn              (fn [event] (assoc event :file "/tmp/foo"))
            v1-report               (munge-v2-example-report-to-v1 report)
            v1-report-with-v2-key   (update-in
                                      v1-report
                                      [:resource-events]
                                      #(mapv add-key-fn %))]
        (is (thrown-with-msg?
            IllegalArgumentException #"ResourceEvent has unknown keys: :file.*version 1"
            (validate! 1 v1-report-with-v2-key)))))

    (testing "should accept a valid v2 report"
      (is (= report (validate! 2 report))))

    (testing "should fail when a report is missing a key"
      (is (thrown-with-msg?
            IllegalArgumentException #"Report is missing keys: :certname$"
            (validate! 2 (dissoc report :certname)))))

    (testing "should fail when a resource event has the wrong data type for a key"
      (is (thrown-with-msg?
            IllegalArgumentException #":timestamp should be Datetime"
            (validate! 2 (assoc-in report [:resource-events 0 :timestamp] "foo")))))))

(deftest test-sanitize-events
  (testing "ensure extraneous keys are removed"
    (let [test-data {"containment-path"
                     ["Stage[main]"
                      "My_pg"
                      "My_pg::Extension[puppetdb:pg_stat_statements]"
                      "Postgresql_psql[create extension pg_stat_statements on puppetdb]"],
                     "new-value" "CREATE EXTENSION pg_stat_statements",
                     "message"
                     "command changed '' to 'CREATE EXTENSION pg_stat_statements'",
                     "old-value" nil,
                     "status" "success",
                     "line" 16,
                     "property" "command",
                     "timestamp" "2014-01-09T17:52:56.795Z",
                     "resource-type" "Postgresql_psql",
                     "resource-title" "create extension pg_stat_statements on puppetdb",
                     "file" "/etc/puppet/modules/my_pg/manifests/extension.pp"
                     "extradata" "foo"}
          santized (sanitize-events [test-data])
          expected [(dissoc test-data "extradata")]]
      (= santized expected))))

(deftest test-sanitize-report
  (testing "no action on valid reports"
    (let [test-data (clojure.walk/stringify-keys (:basic reports))]
      (= (sanitize-report test-data) test-data))))
