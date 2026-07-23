(ns dodcompliance.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [dodcompliance.facts :as facts]))

(deftest dfars-has-spec-basis
  (let [sb (facts/spec-basis :dfars)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (= "https://www.acquisition.gov/dfars" (:provenance sb)))
    (is (= "U.S. Department of Defense (DFARS)" (:owner-authority sb)))))

(deftest cmmc-has-spec-basis
  (let [sb (facts/spec-basis :cmmc)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (map? (:levels sb)))
    (is (= 14 (get-in sb [:levels :l1 :practices])))
    (is (= 110 (get-in sb [:levels :l2 :practices])))
    (is (string? (:enforcement-status-caveat sb))
        "the catalog must carry an honest hedge -- never a bare enforcement-is-live claim")))

(deftest cmmc-does-not-assert-enforcement-is-live
  (testing "the CMMC entry must never claim a specific enforcement-is-live date"
    (let [sb (facts/spec-basis :cmmc)
          blob (pr-str sb)]
      (is (re-find #"(?i)NOT independently confirmed" (:enforcement-status-caveat sb)))
      ;; dated rulemaking-status facts ARE allowed; a bare
      ;; "CMMC is fully enforced as of <date>" style claim is not
      ;; present anywhere in the entry.
      (is (not (re-find #"(?i)fully enforced as of \d{4}-\d{2}-\d{2}" blob))))))

(deftest facility-clearance-has-spec-basis
  (let [sb (facts/spec-basis :facility-clearance)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (= "Defense Counterintelligence and Security Agency (DCSA)" (:owner-authority sb)))
    (is (= "2019-07" (:formed sb)))))

(deftest facility-clearance-never-uses-stale-dss-name
  (testing "DSS (Defense Security Service) is the retired pre-2019 name -- must never be cited as current authority"
    (let [sb (facts/spec-basis :facility-clearance)]
      (is (not= "Defense Security Service (DSS)" (:owner-authority sb)))
      (is (re-find #"(?i)DCSA" (:owner-authority sb)))
      (is (string? (:stale-name-trap sb)))
      (is (re-find #"RETIRED" (:stale-name-trap sb))))))

(deftest facility-clearance-cross-agency-note-present
  (let [sb (facts/spec-basis :facility-clearance)]
    (is (string? (:cross-agency-note sb)))
    (is (re-find #"35 other federal agencies" (:cross-agency-note sb)))))

(deftest sam-registration-is-citation-only-and-honest-about-missing-provenance
  (let [sb (facts/spec-basis :sam-registration)]
    (is (some? sb))
    (is (seq (:required-evidence sb)))
    (is (nil? (:provenance sb))
        "no live URL was independently verified for this fact this session -- must not be fabricated")
    (is (nil? (:official-portal sb)))
    (is (string? (:clause-number-caveat sb)))
    (is (not (re-find #"252\.204" (pr-str sb)))
        "must never cite a specific unverified DFARS clause number")))

(deftest unknown-track-has-no-spec-basis
  (is (nil? (facts/spec-basis :unknown-track)))
  (is (nil? (facts/spec-basis :zzz))))

(deftest required-evidence-satisfied
  (let [sb (facts/spec-basis :dfars)
        all (:required-evidence sb)]
    (is (true? (facts/required-evidence-satisfied? :dfars all)))
    (is (not (facts/required-evidence-satisfied? :dfars (take 1 all))))
    (is (nil? (facts/required-evidence-satisfied? :unknown-track all)))))

(deftest coverage-is-honest
  (let [c (facts/coverage [:dfars :cmmc :unknown-track])]
    (is (= 3 (:requested c)))
    (is (= 2 (:covered c)))
    (is (= ["unknown-track"] (:missing-tracks c)))))

(deftest cmmc-level-of-lookup
  (is (= 14 (:practices (facts/cmmc-level-of :l1))))
  (is (= "NIST SP 800-171" (:aligned-to (facts/cmmc-level-of :l2))))
  (is (nil? (facts/cmmc-level-of :l4))))

(deftest all-four-tracks-present
  (is (= #{:dfars :cmmc :facility-clearance :sam-registration} facts/valid-tracks)))
