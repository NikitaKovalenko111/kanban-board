package profile_handler

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"path/filepath"

	"kanban-board/internal/objectstorage"
	profile_service "kanban-board/internal/services/profile"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

const maxPhotoSize = 5 << 20

type Handler struct {
	service *profile_service.Service
	objects objectstorage.Storage
}

func Init(service *profile_service.Service, objects ...objectstorage.Storage) *Handler {
	handler := &Handler{service: service}
	if len(objects) > 0 {
		handler.objects = objects[0]
	}
	return handler
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /me", h.getMe)
	mux.HandleFunc("PATCH /me", h.updateMe)
	mux.HandleFunc("PUT /me/photo", h.updatePhoto)
	mux.HandleFunc("DELETE /me/photo", h.deletePhoto)
	mux.HandleFunc("GET /team", h.getTeam)
	mux.HandleFunc("GET /users", h.listUsers)
	mux.HandleFunc("POST /users", h.createUser)
	mux.HandleFunc("GET /users/{userId}", h.getUser)
	mux.HandleFunc("GET /users/{userId}/photo", h.getPhoto)
	mux.HandleFunc("PATCH /users/{userId}/status", h.updateStatus)
	mux.HandleFunc("POST /users/{userId}/reset-password", h.resetPassword)
	mux.HandleFunc("PATCH /users/{userId}/role", h.updateRole)
	mux.HandleFunc("DELETE /users/{userId}", h.deleteUser)
}

type profileRequest struct {
	FullName        *string `json:"fullName"`
	StudentGroup    *string `json:"studentGroup"`
	StudyGroup      *string `json:"studyGroup"`
	SchedulePrivacy *string `json:"schedulePrivacy"`
}

type createUserRequest struct {
	FullName          string `json:"fullName"`
	Username          string `json:"username"`
	Login             string `json:"login"`
	TemporaryPassword string `json:"temporaryPassword"`
	StudentGroup      string `json:"studentGroup"`
	StudyGroup        string `json:"studyGroup"`
	Role              string `json:"role"`
}

type statusRequest struct {
	Status string `json:"status"`
}

type roleRequest struct {
	Role string `json:"role"`
}

func (h *Handler) getMe(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	h.writeProfile(w, r, userID)
}

func (h *Handler) getUser(w http.ResponseWriter, r *http.Request) {
	if _, ok := handlercommon.UserID(r); !ok {
		return
	}
	h.writeProfile(w, r, r.PathValue("userId"))
}

func (h *Handler) writeProfile(w http.ResponseWriter, r *http.Request, userID string) {
	profile, err := h.service.GetProfile(r.Context(), userID)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, profile)
}

func (h *Handler) updateMe(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request profileRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	studentGroup := request.StudentGroup
	if studentGroup == nil {
		studentGroup = request.StudyGroup
	}
	profile, err := h.service.UpdateProfile(r.Context(), userID, request.FullName, studentGroup, request.SchedulePrivacy)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, profile)
}

func (h *Handler) listUsers(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	users, err := h.service.ListUsers(r.Context(), userID, r.URL.Query().Get("search"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, users)
}

func (h *Handler) createUser(w http.ResponseWriter, r *http.Request) {
	actorID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request createUserRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	username := request.Username
	if username == "" {
		username = request.Login
	}
	studentGroup := request.StudentGroup
	if studentGroup == "" {
		studentGroup = request.StudyGroup
	}
	profile, err := h.service.CreateUser(
		r.Context(), actorID, request.FullName, username,
		request.TemporaryPassword, studentGroup, request.Role,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, profile)
}

func (h *Handler) updateStatus(w http.ResponseWriter, r *http.Request) {
	actorID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request statusRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	profile, err := h.service.SetStatus(r.Context(), actorID, r.PathValue("userId"), request.Status)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, profile)
}

func (h *Handler) updateRole(w http.ResponseWriter, r *http.Request) {
	actorID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request roleRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	profile, err := h.service.SetRole(r.Context(), actorID, r.PathValue("userId"), request.Role)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, profile)
}

func (h *Handler) resetPassword(w http.ResponseWriter, r *http.Request) {
	actorID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	password, err := h.service.ResetPassword(r.Context(), actorID, r.PathValue("userId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, map[string]string{"temporaryPassword": password})
}

func (h *Handler) deleteUser(w http.ResponseWriter, r *http.Request) {
	actorID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteUser(r.Context(), actorID, r.PathValue("userId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) updatePhoto(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxPhotoSize+(1<<20))
	file, header, err := r.FormFile("file")
	if err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Выберите изображение")
		return
	}
	defer file.Close()

	mimeType := header.Header.Get("Content-Type")
	if mimeType != "image/jpeg" && mimeType != "image/png" && mimeType != "image/webp" {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Поддерживаются JPG, PNG и WEBP")
		return
	}
	storageKey := filepath.ToSlash(filepath.Join("users", userID, "photo"))
	data, err := io.ReadAll(io.LimitReader(file, maxPhotoSize+1))
	if err != nil || len(data) > maxPhotoSize {
		pkg.WriteError(w, http.StatusRequestEntityTooLarge, "FILE_TOO_LARGE", "Файл превышает допустимый размер")
		return
	}
	if err := h.objects.Put(r.Context(), storageKey, bytes.NewReader(data), int64(len(data)), mimeType); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	imageURL := "/api/v1/users/" + userID + "/photo"
	profile, err := h.service.SetPhoto(r.Context(), userID, imageURL)
	if err != nil {
		_ = h.objects.Delete(r.Context(), storageKey)
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, profile)
}

func (h *Handler) deletePhoto(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	profile, err := h.service.SetPhoto(r.Context(), userID, "")
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	_ = h.objects.Delete(r.Context(), filepath.ToSlash(filepath.Join("users", userID, "photo")))
	pkg.WriteJSON(w, http.StatusOK, profile)
}

func (h *Handler) getPhoto(w http.ResponseWriter, r *http.Request) {
	if _, ok := handlercommon.UserID(r); !ok {
		return
	}
	storageKey := filepath.ToSlash(filepath.Join("users", r.PathValue("userId"), "photo"))
	object, err := h.objects.Get(r.Context(), storageKey)
	if err != nil {
		pkg.WriteError(w, http.StatusNotFound, "NOT_FOUND", "Фотография не найдена")
		return
	}
	defer object.Reader.Close()
	if object.ContentType != "" {
		w.Header().Set("Content-Type", object.ContentType)
	}
	w.Header().Set("Content-Length", fmt.Sprint(object.Size))
	_, _ = io.Copy(w, object.Reader)
}

func (h *Handler) getTeam(w http.ResponseWriter, r *http.Request) {
	if _, ok := handlercommon.UserID(r); !ok {
		return
	}
	pkg.WriteJSON(w, http.StatusOK, map[string]string{
		"name":        "Kanban Board",
		"description": "Студенческая команда",
	})
}
