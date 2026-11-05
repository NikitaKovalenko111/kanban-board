package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"strings"

	"kanban-board/internal/config"
	auth_service "kanban-board/internal/services/auth"
	"kanban-board/internal/storage"
)

func main() {
	configPath := flag.String("config", "./config/config.yaml", "path to config")
	login := flag.String("login", "", "captain login")
	password := flag.String("password", "", "captain password")
	fullName := flag.String("name", "", "captain full name")
	group := flag.String("group", "", "student group")
	flag.Parse()
	if *login == "" || *password == "" || *fullName == "" || *group == "" {
		fmt.Fprintln(os.Stderr, "login, password, name, and group are required")
		os.Exit(2)
	}
	cfg, err := config.Load(*configPath)
	if err != nil {
		fatal(err)
	}
	store, err := storage.Connect(cfg.Postgres)
	if err != nil {
		fatal(err)
	}
	defer store.Db.Close()
	hash, err := auth_service.HashPassword(*password)
	if err != nil {
		fatal(err)
	}
	_, err = store.Db.ExecContext(context.Background(), `
		WITH selected_group AS (
			INSERT INTO student_groups (student_group_id, group_code, name)
			VALUES (gen_random_uuid(), $4, $4)
			ON CONFLICT (group_code) DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		)
		INSERT INTO users (user_id, username, display_name, student_group_id, system_role, password_hash)
		SELECT gen_random_uuid(), LOWER($1), $2, student_group_id, 'CAPTAIN', $3 FROM selected_group
		ON CONFLICT DO NOTHING
	`, strings.TrimSpace(*login), strings.TrimSpace(*fullName), hash, strings.TrimSpace(*group))
	if err != nil {
		fatal(err)
	}
	if _, err := store.Db.ExecContext(context.Background(), `
		UPDATE users
		SET system_role = 'CAPTAIN', is_active = TRUE, updated_at = CURRENT_TIMESTAMP
		WHERE LOWER(username) = LOWER($1)
	`, strings.TrimSpace(*login)); err != nil {
		fatal(err)
	}
	fmt.Println("captain account is ready")
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
