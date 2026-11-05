package schedule_service

import (
	"strings"
	"testing"
)

func TestParseScheduleHTML(t *testing.T) {
	html := `
		<table>
			<tbody>
				<tr>
					<td>04.11.2026</td>
					<td>09:40</td>
					<td>11:10</td>
					<td>Базы данных</td>
					<td>Р-210</td>
					<td>Иванов И. И.</td>
					<td>Лабораторная</td>
				</tr>
			</tbody>
		</table>
	`

	lessons, err := parseScheduleHTML(strings.NewReader(html), "РИ-230950")
	if err != nil {
		t.Fatalf("parse schedule: %v", err)
	}
	if len(lessons) != 1 {
		t.Fatalf("expected one lesson, got %d", len(lessons))
	}

	lesson := lessons[0]
	if lesson.Title != "Базы данных" || lesson.Kind != "LAB" {
		t.Fatalf("unexpected lesson: %+v", lesson)
	}
	if lesson.Room != "Р-210" || lesson.Teacher != "Иванов И. И." {
		t.Fatalf("unexpected lesson details: %+v", lesson)
	}
	if lesson.StartsAt.Format("2006-01-02 15:04") != "2026-11-04 09:40" {
		t.Fatalf("unexpected start time: %s", lesson.StartsAt)
	}
	if lesson.EndsAt.Format("2006-01-02 15:04") != "2026-11-04 11:10" {
		t.Fatalf("unexpected end time: %s", lesson.EndsAt)
	}
}
