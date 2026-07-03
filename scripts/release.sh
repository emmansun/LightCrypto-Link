#!/usr/bin/env bash
# Release script for LightCrypto-Link
# Usage:
#   ./scripts/release.sh 0.1.0        # create and push v0.1.0
#   ./scripts/release.sh v0.1.0       # same (v prefix is optional)
#   ./scripts/release.sh 0.1.0 --recreate  # delete existing tag first, then recreate
set -euo pipefail

VERSION="${1:-}"
RECREATE=false

if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version> [--recreate]"
  echo "  version   : release version (e.g. 0.1.0 or v0.1.0)"
  echo "  --recreate: delete existing remote/local tag before creating"
  exit 1
fi

# Strip leading 'v' if present
VERSION="${VERSION#v}"
TAG="v${VERSION}"

# Check for --recreate flag
for arg in "$@"; do
  if [[ "$arg" == "--recreate" ]]; then
    RECREATE=true
  fi
done

echo "==> Preparing release ${TAG}"

if [[ "$RECREATE" == true ]]; then
  # Delete local tag if exists
  if git tag -l | grep -q "^${TAG}$"; then
    echo "  Deleting local tag ${TAG}..."
    git tag -d "$TAG"
  fi
  # Delete remote tag if exists
  if git ls-remote --tags origin | grep -q "refs/tags/${TAG}$"; then
    echo "  Deleting remote tag ${TAG}..."
    git push origin ":refs/tags/${TAG}"
  fi
fi

# Create tag
echo "  Creating tag ${TAG}..."
git tag "$TAG"

# Push tag
echo "  Pushing tag ${TAG} to origin..."
git push origin "$TAG"

echo ""
echo "==> Release ${TAG} triggered!"
echo "    Monitor: https://github.com/emmansun/LightCrypto-Link/actions/workflows/release.yml"
