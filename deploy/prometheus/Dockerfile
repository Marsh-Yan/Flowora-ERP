FROM golang:1.27.2-bookworm AS build
ENV GOTOOLCHAIN=local GOWORK=off CGO_ENABLED=0
WORKDIR /src
# Rebuild the same official 3.15.0 source and published UI assets with patched
# Go and x/net. Verify both archives before compiling; retain builtinassets.
# Keep symbol tables, as upstream does, for the existing binary/package analysis.
RUN curl --fail --location --output source.tar.gz \
      https://github.com/prometheus/prometheus/archive/5241a27fe3c6983549fccc32f6e65917408c63cd.tar.gz \
    && echo 'eee7ec029ffdba7d9ee59171cad5ddaff10e71514b0bae1a6fde6dc1ae99e181  source.tar.gz' | sha256sum --check \
    && tar -xzf source.tar.gz --strip-components=1 \
    && curl --fail --location --output ui.tar.gz \
      https://github.com/prometheus/prometheus/releases/download/v3.15.0/prometheus-web-ui-3.15.0.tar.gz \
    && echo 'fa8c918ed05e3f89e232e9b69eccd6ed074602cd9e8a2cab7a92bec9651974ae  ui.tar.gz' | sha256sum --check \
    && tar -xzf ui.tar.gz -C web/ui \
    && PREBUILT_ASSETS_STATIC_DIR=/src/web/ui/static bash scripts/compress_assets.sh
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    go get golang.org/x/net@v0.60.0 \
    && mkdir -p /out \
    && go build -trimpath -tags netgo,builtinassets \
      -ldflags='-X github.com/prometheus/common/version.Version=3.15.0 -X github.com/prometheus/common/version.Revision=5241a27fe3c6983549fccc32f6e65917408c63cd -X github.com/prometheus/common/version.Branch=HEAD -X github.com/prometheus/common/version.BuildUser=flowora-runtime' \
      -o /out/ ./cmd/prometheus ./cmd/promtool

FROM prom/prometheus:v3.15.0
COPY --from=build /out/prometheus /bin/prometheus
COPY --from=build /out/promtool /bin/promtool
RUN /bin/prometheus --version && /bin/promtool --version
# Preserve the upstream nobody user, entrypoint, configuration and data volume.
