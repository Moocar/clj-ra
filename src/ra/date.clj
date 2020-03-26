(ns ra.date
  (:import (java.time ZoneId LocalDate LocalTime ZonedDateTime DayOfWeek Duration Instant LocalDateTime ZoneOffset)
           (java.time.temporal ChronoUnit)
           (java.time.format DateTimeFormatter)
           (java.util GregorianCalendar)))


(def zone (ZoneId/of "Australia/Sydney"))

(defn format-zdt [d pattern]
  (.format d (DateTimeFormatter/ofPattern pattern)))

(defn zdt
  ([]
   (ZonedDateTime/now zone))
  ([d]
   (.atZone (.toInstant d) zone)))

(defn zdt->date [zdt]
  (java.util.Date/from (.toInstant zdt)))

(defn zdt->calendar [zdt]
  (GregorianCalendar/from zdt))

(defn tagged-value->zdt [[date timezone]]
  (ZonedDateTime/ofInstant (.toInstant date)
                           (if timezone
                             (throw (ex-info "TZ unimplemented" {:timezone timezone}))
                             zone)))

(defn duration-since [start]
  (Duration/between start (zdt)))

(defn duration-seconds [duration]
  (.getSeconds duration))

(defn secs-since-epoch->zdt [s]
  (ZonedDateTime/ofInstant (Instant/ofEpochSecond s) zone))
