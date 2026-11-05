APP_NAME=backend
MAIN_PATH=./cmd/kanban-board/main.go
CONFIG_PATH=./config/config.yaml

CAPTAIN_LOGIN?=captain
CAPTAIN_PASSWORD?=ChangeMe123
CAPTAIN_NAME?=Капитан
CAPTAIN_GROUP?=ТОП-203Б

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

create-captain:
	go run ./cmd/create-captain -config=$(CONFIG_PATH) -login=$(CAPTAIN_LOGIN) -password=$(CAPTAIN_PASSWORD) -name="$(CAPTAIN_NAME)" -group=$(CAPTAIN_GROUP)

clean:
	rm -rf bin
