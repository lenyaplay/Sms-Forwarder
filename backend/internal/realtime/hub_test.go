package realtime

import (
	"sync"
	"testing"
	"time"

	"sms_forwarder/backend/internal/storage"
)

func recvOrTimeout(t *testing.T, ch chan storage.Message) (storage.Message, bool) {
	t.Helper()
	select {
	case msg := <-ch:
		return msg, true
	case <-time.After(time.Second):
		return storage.Message{}, false
	}
}

func TestHub_PublishDeliversToSubscribedDeviceOnly(t *testing.T) {
	h := NewHub()
	chA, unsubA := h.Subscribe([]int64{1})
	defer unsubA()
	chB, unsubB := h.Subscribe([]int64{2})
	defer unsubB()

	h.Publish(1, storage.Message{ID: 100, DeviceID: 1})

	msg, ok := recvOrTimeout(t, chA)
	if !ok || msg.ID != 100 {
		t.Fatalf("chA received %+v, ok=%v, want ID=100", msg, ok)
	}

	select {
	case msg := <-chB:
		t.Fatalf("chB unexpectedly received %+v", msg)
	case <-time.After(50 * time.Millisecond):
	}
}

func TestHub_MultipleSubscribersToSameDeviceAllReceive(t *testing.T) {
	h := NewHub()
	ch1, unsub1 := h.Subscribe([]int64{5})
	defer unsub1()
	ch2, unsub2 := h.Subscribe([]int64{5})
	defer unsub2()

	h.Publish(5, storage.Message{ID: 1})

	if _, ok := recvOrTimeout(t, ch1); !ok {
		t.Error("ch1 did not receive published message")
	}
	if _, ok := recvOrTimeout(t, ch2); !ok {
		t.Error("ch2 did not receive published message")
	}
}

func TestHub_SubscribeMultipleDeviceIDs(t *testing.T) {
	h := NewHub()
	ch, unsub := h.Subscribe([]int64{1, 2})
	defer unsub()

	h.Publish(1, storage.Message{ID: 1, DeviceID: 1})
	h.Publish(2, storage.Message{ID: 2, DeviceID: 2})

	first, ok := recvOrTimeout(t, ch)
	if !ok {
		t.Fatal("did not receive first message")
	}
	second, ok := recvOrTimeout(t, ch)
	if !ok {
		t.Fatal("did not receive second message")
	}
	got := map[int64]bool{first.DeviceID: true, second.DeviceID: true}
	if !got[1] || !got[2] {
		t.Errorf("got device ids %v, want both 1 and 2", got)
	}
}

func TestHub_UnsubscribeRemovesChannel(t *testing.T) {
	h := NewHub()
	_, unsub := h.Subscribe([]int64{9})
	unsub()

	h.mu.Lock()
	_, stillPresent := h.subs[9]
	h.mu.Unlock()
	if stillPresent {
		t.Error("device entry still present in hub after unsubscribe of its only subscriber")
	}

	// Publishing after unsubscribe must not panic or block.
	h.Publish(9, storage.Message{ID: 1})
}

func TestHub_UnsubscribeIsIdempotent(t *testing.T) {
	h := NewHub()
	_, unsub := h.Subscribe([]int64{1})
	unsub()
	unsub() // must not panic (double close-like semantics via sync.Once)
}

func TestHub_PublishToDeviceWithNoSubscribersDoesNotPanic(t *testing.T) {
	h := NewHub()
	h.Publish(999, storage.Message{ID: 1})
}

func TestHub_PublishDoesNotBlockOnFullSubscriberBuffer(t *testing.T) {
	h := NewHub()
	ch, unsub := h.Subscribe([]int64{1})
	defer unsub()

	done := make(chan struct{})
	go func() {
		defer close(done)
		for i := 0; i < subscriberBufferSize+10; i++ {
			h.Publish(1, storage.Message{ID: int64(i)})
		}
	}()

	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("Publish blocked on a full subscriber buffer instead of dropping")
	}

	// Drain to confirm the channel still works and got at least the buffer's worth.
	drained := 0
	for {
		select {
		case <-ch:
			drained++
		default:
			if drained == 0 {
				t.Error("expected at least some delivered messages before buffer filled")
			}
			return
		}
	}
}

func TestHub_ConcurrentSubscribePublishUnsubscribe(t *testing.T) {
	h := NewHub()
	var wg sync.WaitGroup

	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			ch, unsub := h.Subscribe([]int64{int64(n % 3)})
			defer unsub()
			for j := 0; j < 5; j++ {
				h.Publish(int64(n%3), storage.Message{ID: int64(j)})
				select {
				case <-ch:
				case <-time.After(10 * time.Millisecond):
				}
			}
		}(i)
	}

	wg.Wait()
}
