package ratelimit

import (
	"strconv"
	"testing"
	"time"
)

func TestAllowWithinBurstSucceeds(t *testing.T) {
	l := New(1, 3)
	for i := 0; i < 3; i++ {
		if !l.Allow("key-a") {
			t.Fatalf("request %d within burst should be allowed", i)
		}
	}
}

func TestAllowRejectsBeyondBurst(t *testing.T) {
	l := New(1, 3)
	for i := 0; i < 3; i++ {
		l.Allow("key-a")
	}
	if l.Allow("key-a") {
		t.Fatal("request beyond burst should be rejected")
	}
}

func TestAllowRefillsAfterWindowElapses(t *testing.T) {
	// rps chosen high enough that the refill window is a handful of
	// milliseconds, not the real ~1s/60s window used in production - keeps
	// the test fast without faking time.Now, matching the spec's ban on a
	// real 60s sleep in tests.
	const rps = 200 // one token every 5ms
	l := New(rps, 1)

	if !l.Allow("key-a") {
		t.Fatal("first request should be allowed")
	}
	if l.Allow("key-a") {
		t.Fatal("immediate second request should be rejected - burst of 1 already spent")
	}

	time.Sleep(20 * time.Millisecond) // several refill windows

	if !l.Allow("key-a") {
		t.Fatal("request after the window elapsed should be allowed again - bucket should have refilled")
	}
}

func TestAllowEvictsLeastRecentlyUsedKeyOverCap(t *testing.T) {
	l := New(1, 1)
	// Fill exactly to the cap, then touch key-0 so it's most-recently-used
	// and shouldn't be the one evicted.
	for i := 0; i < maxTrackedKeys; i++ {
		l.Allow(keyName(i))
	}
	l.Allow(keyName(0))

	// One more distinct key pushes past the cap - the least-recently-used
	// key (key-1, never touched again after its first Allow) must be
	// evicted, freeing a fresh bucket for it if reused.
	l.Allow("overflow-key")

	if len(l.limiters) != maxTrackedKeys {
		t.Fatalf("tracked key count = %d, want capped at %d", len(l.limiters), maxTrackedKeys)
	}
	if _, ok := l.limiters[keyName(1)]; ok {
		t.Fatal("least-recently-used key should have been evicted")
	}
	if _, ok := l.limiters[keyName(0)]; !ok {
		t.Fatal("recently-touched key should not have been evicted")
	}
}

func keyName(i int) string {
	return "key-" + strconv.Itoa(i)
}

func TestAllowKeysAreIndependent(t *testing.T) {
	l := New(1, 1)
	if !l.Allow("key-a") {
		t.Fatal("first request for key-a should be allowed")
	}
	if !l.Allow("key-b") {
		t.Fatal("key-b has its own bucket and should be allowed even though key-a's is exhausted")
	}
	if l.Allow("key-a") {
		t.Fatal("key-a's bucket is exhausted, should still be rejected")
	}
}
