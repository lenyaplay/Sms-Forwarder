// Package realtime provides an in-process pub/sub hub for pushing newly
// ingested messages to subscribed SSE connections, per
// docs/specs/0006-realtime-delivery.md. In-process only (no external
// broker) — matches the single-process deployment model.
package realtime

import (
	"sync"

	"sms_forwarder/backend/internal/storage"
)

// subscriberBufferSize bounds how many undelivered messages a slow
// subscriber can accumulate before further publishes to it are dropped
// (see Publish).
const subscriberBufferSize = 16

type Hub struct {
	mu   sync.Mutex
	subs map[int64]map[chan storage.Message]struct{}
}

func NewHub() *Hub {
	return &Hub{subs: make(map[int64]map[chan storage.Message]struct{})}
}

// Subscribe registers a new buffered channel for every device ID in
// deviceIDs. The caller must invoke unsubscribe exactly once (e.g. via
// defer) when done, to remove the channel from every device ID and avoid a
// leak. The channel is never closed by the hub (a closed channel could
// panic a concurrent Publish); once unsubscribed, it is simply no longer
// referenced and eligible for GC.
func (h *Hub) Subscribe(deviceIDs []int64) (ch chan storage.Message, unsubscribe func()) {
	ch = make(chan storage.Message, subscriberBufferSize)

	h.mu.Lock()
	for _, id := range deviceIDs {
		if h.subs[id] == nil {
			h.subs[id] = make(map[chan storage.Message]struct{})
		}
		h.subs[id][ch] = struct{}{}
	}
	h.mu.Unlock()

	var once sync.Once
	unsubscribe = func() {
		once.Do(func() {
			h.mu.Lock()
			for _, id := range deviceIDs {
				delete(h.subs[id], ch)
				if len(h.subs[id]) == 0 {
					delete(h.subs, id)
				}
			}
			h.mu.Unlock()
		})
	}
	return ch, unsubscribe
}

// Publish delivers msg to every current subscriber of deviceID. Non-blocking:
// if a subscriber's channel buffer is full (slow consumer), that delivery is
// dropped rather than blocking the caller — IngestWebhook calls this
// synchronously, so a stuck subscriber must never stall message ingestion.
// Push is best-effort; REST reads remain the source of truth.
func (h *Hub) Publish(deviceID int64, msg storage.Message) {
	h.mu.Lock()
	defer h.mu.Unlock()

	for ch := range h.subs[deviceID] {
		select {
		case ch <- msg:
		default:
		}
	}
}
