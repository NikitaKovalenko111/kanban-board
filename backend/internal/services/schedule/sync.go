package schedule_service

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"kanban-board/internal/config"
	schedule_repo "kanban-board/internal/storage/repositories/schedule"

	"github.com/PuerkitoBio/goquery"
)

func (s *Service) Sync(ctx context.Context, cfg config.ScheduleSync) (int, error) {
	groups, err := s.repo.ListGroupCodes(ctx)
	if err != nil {
		return 0, err
	}
	client := &http.Client{Timeout: cfg.HTTPTimeout}
	total := 0
	for _, group := range groups {
		sourceURL := strings.ReplaceAll(cfg.SourceURL, "{group}", url.QueryEscape(group))
		request, err := http.NewRequestWithContext(ctx, http.MethodGet, sourceURL, nil)
		if err != nil {
			return total, err
		}
		response, err := client.Do(request)
		if err != nil {
			_ = s.repo.MarkGroupCached(ctx, group)
			continue
		}
		lessons, parseErr := parseScheduleHTML(response.Body, group)
		response.Body.Close()
		if parseErr != nil || response.StatusCode != http.StatusOK {
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

func parseScheduleHTML(reader io.Reader, group string) ([]schedule_repo.SyncLesson, error) {
	document, err := goquery.NewDocumentFromReader(reader)
	if err != nil {
		return nil, err
	}
	location, _ := time.LoadLocation("Asia/Yekaterinburg")
	lessons := make([]schedule_repo.SyncLesson, 0)
	document.Find("table tbody tr").Each(func(_ int, row *goquery.Selection) {
		cells := row.Find("td")
		if cells.Length() < 7 {
			return
		}
		value := func(index int) string { return strings.TrimSpace(cells.Eq(index).Text()) }
		date, dateErr := time.ParseInLocation("02.01.2006 15:04", value(0)+" "+value(1), location)
		end, endErr := time.ParseInLocation("02.01.2006 15:04", value(0)+" "+value(2), location)
		if dateErr != nil || endErr != nil {
			return
		}
		kind := strings.ToUpper(value(6))
		switch {
		case strings.Contains(kind, "ЛАБ"):
			kind = "LAB"
		case strings.Contains(kind, "ПРАК"):
			kind = "PRACTICE"
		default:
			kind = "LECTURE"
		}
		lessons = append(lessons, schedule_repo.SyncLesson{
			ExternalID: fmt.Sprintf("%s-%d", group, date.Unix()), Title: value(3),
			Room: value(4), Teacher: value(5), Kind: kind, StartsAt: date, EndsAt: end,
		})
	})
	return lessons, nil
}
