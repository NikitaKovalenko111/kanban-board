package notification_service

import (
	"testing"
	"time"
)

func TestPublishDeliversOnlyToTargetUser(t *testing.T) {
	service := Init(nil)
	firstUser := "11111111-1111-1111-1111-111111111111"
	secondUser := "22222222-2222-2222-2222-222222222222"

	firstEvents, unsubscribeFirst, err := service.Subscribe(firstUser)
	if err != nil {
		t.Fatalf("subscribe first user: %v", err)
	}
	defer unsubscribeFirst()
	secondEvents, unsubscribeSecond, err := service.Subscribe(secondUser)
	if err != nil {
		t.Fatalf("subscribe second user: %v", err)
	}
	defer unsubscribeSecond()

	service.publish(firstUser)

	select {
	case <-firstEvents:
	case <-time.After(time.Second):
		t.Fatal("target user did not receive the event")
	}

	select {
	case <-secondEvents:
		t.Fatal("another user received an event")
	default:
	}
}

func TestPublishCoalescesPendingEvents(t *testing.T) {
	service := Init(nil)
	userID := "11111111-1111-1111-1111-111111111111"
	events, unsubscribe, err := service.Subscribe(userID)
	if err != nil {
		t.Fatalf("subscribe: %v", err)
	}
	defer unsubscribe()

	service.publish(userID)
	service.publish(userID)

	select {
	case <-events:
	default:
		t.Fatal("subscriber did not receive the event")
	}
	select {
	case <-events:
		t.Fatal("pending events were not coalesced")
	default:
	}
}
