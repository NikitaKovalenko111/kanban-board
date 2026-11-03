APP_NAME=backend
MAIN_PATH=./cmd/kanban-board/main.go
CONFIG_PATH=./config/config.yaml

run:
	go run $(MAIN_PATH) -config=$(CONFIG_PATH)

build:
	go build -o bin/$(APP_NAME) $(MAIN_PATH)

test:
	go test ./...

vet:
	go vet ./...

fmt:
	go fmt ./...

clean:
	rm -rf bin