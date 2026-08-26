// Package ratelimit provides a per-key token-bucket rate limiter, used to
// throttle abuse-prone endpoints (webhook by upload_token, auth by client
// IP) per docs/specs/0008-security-and-ops.md.
package ratelimit

import (
	"container/list"
	"sync"

	"golang.org/x/time/rate"
)

// maxTrackedKeys bounds the number of per-key buckets kept in memory. The
// webhook route rate-limits by upload_token *before* the token is validated
// against the database, so an attacker can flood the limiter with an
// unbounded number of distinct, never-valid tokens; without a cap each one
// would permanently occupy memory as its own bucket. The least-recently-used
// key is evicted once the cap is reached - see access below.
const maxTrackedKeys = 100_000

// Limiter tracks an independent token bucket per key, bounded to
// maxTrackedKeys entries via LRU eviction.
type Limiter struct {
	mu       sync.Mutex
	limiters map[string]*list.Element
	order    *list.List // front = most recently used
	rps      rate.Limit
	burst    int
}

type entry struct {
	key string
	lim *rate.Limiter
}

// New creates a Limiter allowing rps requests/sec (sustained) with the given
// burst, per key.
func New(rps float64, burst int) *Limiter {
	return &Limiter{
		limiters: make(map[string]*list.Element),
		order:    list.New(),
		rps:      rate.Limit(rps),
		burst:    burst,
	}
}

// Allow reports whether a request for key is allowed right now, consuming a
// token if so. A new bucket is created (full) on first use of a key.
func (l *Limiter) Allow(key string) bool {
	l.mu.Lock()
	el, ok := l.limiters[key]
	var lim *rate.Limiter
	if ok {
		lim = el.Value.(*entry).lim
		l.order.MoveToFront(el)
	} else {
		lim = rate.NewLimiter(l.rps, l.burst)
		el = l.order.PushFront(&entry{key: key, lim: lim})
		l.limiters[key] = el
		l.evictIfOverCap()
	}
	l.mu.Unlock()
	return lim.Allow()
}

// evictIfOverCap removes the least-recently-used key once the tracked set
// exceeds maxTrackedKeys. Must be called with l.mu held.
func (l *Limiter) evictIfOverCap() {
	if l.order.Len() <= maxTrackedKeys {
		return
	}
	oldest := l.order.Back()
	if oldest == nil {
		return
	}
	l.order.Remove(oldest)
	delete(l.limiters, oldest.Value.(*entry).key)
}
