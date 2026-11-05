package docs

import (
	_ "embed"
	"net/http"
)

//go:embed openapi.yaml
var openAPISpecification []byte

const swaggerUI = `<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kanban Board API — Swagger UI</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css">
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
  <script>
    SwaggerUIBundle({
      url: "/api/v1/docs/openapi.yaml",
      dom_id: "#swagger-ui",
      deepLinking: true,
      persistAuthorization: true,
      displayRequestDuration: true
    });
  </script>
</body>
</html>`

func Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/v1/docs", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/api/v1/docs/", http.StatusPermanentRedirect)
	})
	mux.HandleFunc("GET /api/v1/docs/", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		_, _ = w.Write([]byte(swaggerUI))
	})
	mux.HandleFunc("GET /api/v1/docs/openapi.yaml", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/yaml; charset=utf-8")
		_, _ = w.Write(openAPISpecification)
	})
	mux.HandleFunc("GET /api/v1/swagger", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/api/v1/docs/", http.StatusPermanentRedirect)
	})
}
