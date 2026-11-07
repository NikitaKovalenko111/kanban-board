package schedule_service

import (
	"testing"
	"time"
)

func TestConvertUUSTLessons(t *testing.T) {
	location := yekaterinburgLocation()
	semesterStart := time.Date(2026, time.August, 31, 0, 0, 0, 0, location)
	items := []uustLesson{
		{
			ScheduleID:        249603,
			Weeks:             []string{"2", "3"},
			Subject:           "Математический анализ",
			Weekday:           5,
			RoomTitleShort:    "1-409",
			Type:              "Лекция",
			TeacherFullName:   "Кужаев Арсен Фанилевич",
			ScheduleTimeTitle: "08:00-09:20",
		},
	}

	lessons, err := convertUUSTLessons(items, semesterStart)
	if err != nil {
		t.Fatalf("convert schedule: %v", err)
	}
	if len(lessons) != 2 {
		t.Fatalf("expected two lesson occurrences, got %d", len(lessons))
	}

	lesson := lessons[0]
	if lesson.ExternalID != "uust:249603:2" {
		t.Fatalf("unexpected external ID: %s", lesson.ExternalID)
	}
	if lesson.Title != "Математический анализ" || lesson.Kind != "LECTURE" {
		t.Fatalf("unexpected lesson: %+v", lesson)
	}
	if lesson.Room != "1-409" || lesson.Teacher != "Кужаев Арсен Фанилевич" {
		t.Fatalf("unexpected lesson details: %+v", lesson)
	}
	if lesson.StartsAt.Format("2006-01-02 15:04") != "2026-09-11 08:00" {
		t.Fatalf("unexpected start time: %s", lesson.StartsAt)
	}
	if lesson.EndsAt.Format("2006-01-02 15:04") != "2026-09-11 09:20" {
		t.Fatalf("unexpected end time: %s", lesson.EndsAt)
	}
}

func TestNormalizeGroupCode(t *testing.T) {
	if got := normalizeGroupCode(" топ-204б "); got != "ТОП-204Б" {
		t.Fatalf("normalizeGroupCode() = %q", got)
	}
}
