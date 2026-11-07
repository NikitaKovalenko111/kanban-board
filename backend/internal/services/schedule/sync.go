package schedule_service

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"kanban-board/internal/config"
	schedule_repo "kanban-board/internal/storage/repositories/schedule"
)

type uustGroup struct {
	ID    int    `json:"group_id"`
	Title string `json:"group_title"`
}

type uustLesson struct {
	ScheduleID         int      `json:"schedule_id"`
	Weeks              []string `json:"schedule_weeks"`
	Subject            string   `json:"schedule_subject_title"`
	Weekday            int      `json:"schedule_weekday_id"`
	BuildingShortTitle string   `json:"building_short_title"`
	RoomTitle          string   `json:"room_title"`
	RoomTitleShort     string   `json:"room_title_short"`
	Type               string   `json:"type"`
	Teacher            string   `json:"teacher"`
	TeacherFullName    string   `json:"teacher_fullname"`
	ScheduleTimeTitle  string   `json:"schedule_time_title"`
}

func (s *Service) Sync(ctx context.Context, cfg config.ScheduleSync) (int, error) {
	semesterStart, err := time.ParseInLocation("2006-01-02", cfg.SemesterStart, yekaterinburgLocation())
	if err != nil {
		return 0, fmt.Errorf("parse semester start: %w", err)
	}
	semesterStart = semesterMonday(semesterStart)

	client := &http.Client{Timeout: cfg.HTTPTimeout}
	groups, err := loadUUSTGroups(ctx, client, cfg.SourceURL)
	if err != nil {
		return 0, fmt.Errorf("load UUST groups: %w", err)
	}
	groupIDs := make(map[string]int, len(groups))
	for _, group := range groups {
		groupIDs[normalizeGroupCode(group.Title)] = group.ID
	}

	localGroups, err := s.repo.ListGroupCodes(ctx)
	if err != nil {
		return 0, err
	}
	total := 0
	for _, group := range localGroups {
		groupID, ok := groupIDs[normalizeGroupCode(group)]
		if !ok {
			_ = s.repo.MarkGroupCached(ctx, group)
			continue
		}
		items, err := loadUUSTSchedule(ctx, client, cfg.SourceURL, groupID, cfg.SemesterID)
		if err != nil {
			_ = s.repo.MarkGroupCached(ctx, group)
			continue
		}
		lessons, err := convertUUSTLessons(items, semesterStart)
		if err != nil {
			_ = s.repo.MarkGroupCached(ctx, group)
			continue
		}
		if err := s.repo.ReplaceLessons(ctx, group, lessons); err != nil {
			return total, err
		}
		total += len(lessons)
	}

	return total, nil
}

func loadUUSTGroups(ctx context.Context, client *http.Client, baseURL string) ([]uustGroup, error) {
	var groups []uustGroup
	if err := getUUSTJSON(ctx, client, uustURL(baseURL, "groups"), &groups); err != nil {
		return nil, err
	}
	return groups, nil
}

func loadUUSTSchedule(
	ctx context.Context,
	client *http.Client,
	baseURL string,
	groupID int,
	semesterID int,
) ([]uustLesson, error) {
	path := fmt.Sprintf("schedule/0/%d/semester/%d", groupID, semesterID)
	var lessons []uustLesson
	if err := getUUSTJSON(ctx, client, uustURL(baseURL, path), &lessons); err != nil {
		return nil, err
	}
	return lessons, nil
}

func getUUSTJSON(ctx context.Context, client *http.Client, endpoint string, destination any) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("User-Agent", "kanban-board-schedule-sync/1.0")

	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("UUST API returned %s", response.Status)
	}
	if err := json.NewDecoder(response.Body).Decode(destination); err != nil {
		return fmt.Errorf("decode UUST response: %w", err)
	}
	return nil
}

func convertUUSTLessons(items []uustLesson, semesterStart time.Time) ([]schedule_repo.SyncLesson, error) {
	lessons := make([]schedule_repo.SyncLesson, 0)
	for _, item := range items {
		if item.ScheduleID <= 0 || item.Weekday < 1 || item.Weekday > 7 || strings.TrimSpace(item.Subject) == "" {
			continue
		}
		startClock, endClock, ok := strings.Cut(item.ScheduleTimeTitle, "-")
		if !ok {
			continue
		}
		startHour, startMinute, err := parseClock(startClock)
		if err != nil {
			continue
		}
		endHour, endMinute, err := parseClock(endClock)
		if err != nil {
			continue
		}

		for _, rawWeek := range item.Weeks {
			week, err := strconv.Atoi(rawWeek)
			if err != nil || week < 1 {
				continue
			}
			date := semesterStart.AddDate(0, 0, (week-1)*7+item.Weekday-1)
			startsAt := time.Date(
				date.Year(), date.Month(), date.Day(), startHour, startMinute, 0, 0, date.Location(),
			)
			endsAt := time.Date(
				date.Year(), date.Month(), date.Day(), endHour, endMinute, 0, 0, date.Location(),
			)
			if !endsAt.After(startsAt) {
				continue
			}
			lessons = append(lessons, schedule_repo.SyncLesson{
				ExternalID: fmt.Sprintf("uust:%d:%d", item.ScheduleID, week),
				Title:      strings.TrimSpace(item.Subject),
				Room:       uustRoom(item),
				Teacher:    uustTeacher(item),
				Kind:       uustLessonKind(item.Type),
				StartsAt:   startsAt,
				EndsAt:     endsAt,
			})
		}
	}
	return lessons, nil
}

func uustURL(baseURL, path string) string {
	return strings.TrimRight(baseURL, "/") + "/" + strings.TrimLeft(path, "/") + "?site=schedule"
}

func normalizeGroupCode(value string) string {
	return strings.ToUpper(strings.Join(strings.Fields(value), ""))
}

func semesterMonday(value time.Time) time.Time {
	daysSinceMonday := (int(value.Weekday()) + 6) % 7
	return value.AddDate(0, 0, -daysSinceMonday)
}

func parseClock(value string) (int, int, error) {
	parsed, err := time.Parse("15:04", strings.TrimSpace(value))
	if err != nil {
		return 0, 0, err
	}
	return parsed.Hour(), parsed.Minute(), nil
}

func uustRoom(item uustLesson) string {
	if value := strings.TrimSpace(item.RoomTitleShort); value != "" {
		return value
	}
	parts := make([]string, 0, 2)
	if value := strings.TrimSpace(item.BuildingShortTitle); value != "" {
		parts = append(parts, value)
	}
	if value := strings.TrimSpace(item.RoomTitle); value != "" {
		parts = append(parts, value)
	}
	return strings.Join(parts, " ")
}

func uustTeacher(item uustLesson) string {
	if value := strings.TrimSpace(item.TeacherFullName); value != "" {
		return value
	}
	return strings.TrimSpace(item.Teacher)
}

func uustLessonKind(value string) string {
	value = strings.ToUpper(value)
	switch {
	case strings.Contains(value, "ЛАБ"):
		return "LAB"
	case strings.Contains(value, "ПРАК"), strings.Contains(value, "СЕМИНАР"):
		return "PRACTICE"
	default:
		return "LECTURE"
	}
}

func yekaterinburgLocation() *time.Location {
	location, err := time.LoadLocation("Asia/Yekaterinburg")
	if err == nil {
		return location
	}
	return time.FixedZone("Asia/Yekaterinburg", 5*60*60)
}
