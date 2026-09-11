(ns dodcompliance.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [dodcompliance.phase :as phase]))

(deftest filing-submit-never-auto
  (doseq [ph [0 1 2 3]]
    (let [cfg (get phase/phases ph)]
      (is (not (contains? (:auto cfg) :filing/submit))
          (str "phase " ph " must never auto-commit filing/submit"))
      (is (not (contains? (:auto cfg) :filing/draft))
          (str "phase " ph " must never auto-commit filing/draft")))))

(deftest phase-3-intake-auto-eligible
  (is (contains? (:auto (get phase/phases 3)) :engagement/intake)))

(deftest gate-escalates-non-auto-writes
  (let [r (phase/gate 3 {:op :filing/submit} :commit)]
    (is (= :escalate (:disposition r)))
    (is (= :phase-approval (:reason r)))))

(deftest gate-holds-disabled-ops
  (let [r (phase/gate 0 {:op :engagement/intake} :commit)]
    (is (= :hold (:disposition r)))
    (is (= :phase-disabled (:reason r)))))
