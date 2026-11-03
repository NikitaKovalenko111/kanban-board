package profile_service

import (
	"net/url"
	"strings"

	servicecommon "kanban-board/internal/services/common"
)

func validateProfile(displayName, imageURL string) error {
	if err := servicecommon.ValidateRequired(displayName, "displayName"); err != nil {
		return err
	}
	if imageURL == "" {
		return nil
	}

	parsedURL, err := url.ParseRequestURI(imageURL)
	if err != nil || parsedURL.Host == "" || (parsedURL.Scheme != "http" && parsedURL.Scheme != "https") {
		return servicecommon.InvalidInput("imageUrl must be a valid HTTP or HTTPS URL")
	}
	if strings.TrimSpace(imageURL) != imageURL {
		return servicecommon.InvalidInput("imageUrl must not contain leading or trailing spaces")
	}

	return nil
}
