#!/usr/bin/env bash
set -euo pipefail
# Fixed official release and asset digest, verified from GitHub release metadata.
# Do not use a moving third-party Action tag or pipe a downloaded installer to a shell.
version=0.74.0
digest=2ae6fe3ee734b7fdf11335663e18c75ea12dccc76062f09f164a3b0f8be4371a
destination="${RUNNER_TEMP:?RUNNER_TEMP is required}/flowora-trivy-${version}"
mkdir -p "$destination"
curl --fail --location --retry 3 --output "$destination/trivy.tar.gz" \
  "https://github.com/aquasecurity/trivy/releases/download/v${version}/trivy_${version}_Linux-64bit.tar.gz"
printf '%s  %s\n' "$digest" "$destination/trivy.tar.gz" | sha256sum --check
tar -xzf "$destination/trivy.tar.gz" -C "$destination" trivy
"$destination/trivy" --version
printf '%s\n' "$destination" >> "${GITHUB_PATH:?GITHUB_PATH is required}"
