(ns remit.events
  "This namespace contains three functions: emit, 
  subscribe, and unsubscribe. These functions 
  implement message routing via core.async pub/sub.
  Differences from re-frame:

  - instead of a single go routine, we have a go
  routine per event handler. 

  - the handle function is conceptually rolled 
  into the same go routine

  - keeping track of event handler subscriptions
  explicitly via an atom is no longer needed
  
  - we have not ported middleware 

  - we have not ported the event metadata dom flushing"
  (:require [reagent.core :as reagent :refer [atom]]           
            [cljs.core.async :as async
             :refer [<! >! chan close! put! pub sub unsub unsub-all]])
  (:require-macros [cljs.core.async.macros :as m :refer [go go-loop alt!]]
                   [reagent.debug :refer [log prn warn]]))

;; --- core.async pub/sub channels for events ---

(def ^:private event-channel
  "This channel is used by the emit function
  to publish event notifications."
  (chan))

(def ^:private event-publication
  "This publication of the event-channel
  enforces a convention for the event 
  emitter where all event notifications must
  take the form of a map with an :event key
  that designates the publication topic"
  (pub event-channel :event))

;; --- Public API --- 

;; --- event emitting (publication) ---

(defn emit
  "This function emits events by publishing
  them via core.async's pub/sub mechanism.

  Expects an event-key and (optionally)
  event data.  

  Returns the message data structure
  that was emitted."
  ([event-key]
   (emit event-key nil))
  
  ([event-key event-data]
   (emit event-channel event-key event-data))

  ([event-chan event-key event-data]
   (let [message {:event event-key :data event-data}]
     (put! event-chan message)
     message)))

;; --- event subscription ---

(defn subscribe
  "This function registers an event sink for
  a given topic. It does this by establishing
  a core.async subscription on a new channel.

  Expects an event-key and handler function.
  Optionally, a pre-existing subscription
  channel can be passed in. This makes it 
  easy to re-connect a listener dynamically.

  If no subscription channel is supplied, a
  new one will be created.

  Returns the subscription channel (which)
  can later be used to unsubscribe.

  Received events will be delegated to the 
  supplied function in a go loop. The go-loop
  will exit if the channel is closed.

  usage:

  (subscribe :click (fn [data] ...))
  
  (let [my-chan (chan)]
    ... more work here ...
    (subscribe my-chan :click (fn [data] ...)))
  "
  ([event-key f]
   (subscribe event-publication (chan) event-key f))
  
  ([sub-chan event-key f]
   (subscribe event-publication sub-chan event-key f))

  ([pub-chan sub-chan event-key f]
   (let [_ (sub pub-chan event-key sub-chan)]
     (go-loop []
       (when-let [event (<! sub-chan)]
         (try
           (f event)
           (catch :default e
             (warn e)))
         (recur)))
     sub-chan)))

(defn unsubscribe
  "Unsubscribes a channel from an event publication. "
  ([sub-chan topic]
   (unsubscribe event-publication sub-chan topic))

  ([pub-chan sub-chan topic]
   (unsub pub-chan topic sub-chan)))

(defn unsubscribe-all
  "Unsubscribes all listeners from an event topic"
  ([topic]
   (unsubscribe-all event-publication topic))
  
  ([pub-chan topic]
   (unsub pub-chan topic)))
