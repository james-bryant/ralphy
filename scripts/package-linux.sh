#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

rm -rf "${repo_root}/target/dist/linux" "${repo_root}/target/jpackage/linux"

"${repo_root}/mvnw" clean verify -Ppackage-native,package-linux "$@"
