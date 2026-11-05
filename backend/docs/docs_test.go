package docs

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/goccy/go-yaml"
)

func TestOpenAPISpecificationIsValidYAML(t *testing.T) {
	var specification map[string]any
	if err := yaml.Unmarshal(openAPISpecification, &specification); err != nil {
		t.Fatalf("parse OpenAPI specification: %v", err)
	}
	if specification["openapi"] != "3.0.3" {
		t.Fatalf("openapi version = %v", specification["openapi"])
	}
	paths, ok := specification["paths"].(map[string]any)
	if !ok || len(paths) == 0 {
		t.Fatal("OpenAPI specification has no paths")
	}
}

func TestDocumentationRoutes(t *testing.T) {
	mux := http.NewServeMux()
	Start(mux)

	for _, test := range []struct {
		path        string
		contentType string
	}{
		{path: "/api/v1/docs/", contentType: "text/html; charset=utf-8"},
		{path: "/api/v1/docs/openapi.yaml", contentType: "application/yaml; charset=utf-8"},
	} {
		request := httptest.NewRequest(http.MethodGet, test.path, nil)
		response := httptest.NewRecorder()
		mux.ServeHTTP(response, request)
		if response.Code != http.StatusOK {
			t.Fatalf("GET %s status = %d", test.path, response.Code)
		}
		if response.Header().Get("Content-Type") != test.contentType {
			t.Fatalf("GET %s Content-Type = %q", test.path, response.Header().Get("Content-Type"))
		}
	}
}
