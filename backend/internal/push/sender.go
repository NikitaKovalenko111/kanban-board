package push

import (
	"context"
	"fmt"

	"kanban-board/internal/config"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"google.golang.org/api/option"
)

type Message struct {
	Token string
	Title string
	Body  string
	Data  map[string]string
}

type Sender interface {
	Send(context.Context, Message) error
}

func New(ctx context.Context, cfg config.FCM) (Sender, error) {
	if !cfg.Enabled {
		return nil, nil
	}
	app, err := firebase.NewApp(ctx, &firebase.Config{ProjectID: cfg.ProjectID}, option.WithCredentialsFile(cfg.CredentialsFile))
	if err != nil {
		return nil, fmt.Errorf("initialize firebase: %w", err)
	}
	client, err := app.Messaging(ctx)
	if err != nil {
		return nil, fmt.Errorf("initialize firebase messaging: %w", err)
	}
	return &firebaseSender{client: client}, nil
}

type firebaseSender struct {
	client *messaging.Client
}

func (s *firebaseSender) Send(ctx context.Context, message Message) error {
	_, err := s.client.Send(ctx, &messaging.Message{
		Token:        message.Token,
		Notification: &messaging.Notification{Title: message.Title, Body: message.Body},
		Data:         message.Data,
	})
	return err
}
