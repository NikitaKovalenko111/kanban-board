FROM mcr.microsoft.com/oss/go/microsoft/golang:1.25-bookworm AS builder

ARG MINIO_RELEASE
ARG MINIO_COMMIT

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "${MINIO_RELEASE}" https://github.com/minio/minio.git /src \
    && test "$(git -C /src rev-parse HEAD)" = "${MINIO_COMMIT}"

WORKDIR /src

RUN MS_GO_NOSYSTEMCRYPTO=1 CGO_ENABLED=0 go build \
    -tags kqueue \
    -trimpath \
    -ldflags="-s -w" \
    -o /out/minio .

RUN mkdir -p /data /tmp \
    && chmod 0777 /data \
    && chmod 1777 /tmp

FROM scratch

COPY --from=builder /out/minio /usr/local/bin/minio
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=builder /usr/share/zoneinfo /usr/share/zoneinfo
COPY --from=builder --chown=1000:1000 /data /data
COPY --from=builder --chown=1000:1000 /tmp /tmp

USER 1000:1000

EXPOSE 9000 9001

VOLUME ["/data"]

ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
