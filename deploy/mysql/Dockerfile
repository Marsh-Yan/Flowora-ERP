FROM golang:1.27.2-bookworm AS gosu-build
WORKDIR /src
# Rebuild the same upstream gosu 1.19 source with patched Go and x/sys.
# Immutable commit and archive checksum verified from the official repository.
RUN curl --fail --location --output gosu.tar.gz \
      https://github.com/tianon/gosu/archive/6456aaa0f3c854d199d0f037f068eb97515b7513.tar.gz \
    && echo '33d7537d588ea49458b9509bcf4554bdf5ceacc66da71e5caa1058ea3b689c3b  gosu.tar.gz' | sha256sum --check \
    && tar -xzf gosu.tar.gz --strip-components=1 \
    && go get golang.org/x/sys@v0.44.0 \
    && CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/gosu .

FROM mysql:8.4.11
# mysqlsh is not used by the entrypoint, migrations, backups or application.
# Remove its bundled Python dependency tree; retain mysql/mysqladmin/mysqldump.
RUN microdnf remove -y mysql-shell && microdnf update -y && microdnf clean all
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
RUN gosu --version && test "$(gosu nobody id -u)" = "$(id -u nobody)"
# Preserve upstream entrypoint, root-to-mysql privilege drop, CMD and volumes.
