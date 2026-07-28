#!/usr/bin/env sh
set -eu

# Run on the VPS from the repository root after installing ImageMagick.
# The command is idempotent and only creates missing thumbnails.
UPLOAD_DIR="${UPLOAD_DIR:-./uploads}"
THUMBS_DIR="$UPLOAD_DIR/thumbs"

command -v magick >/dev/null 2>&1 || {
  echo "ImageMagick is required: install it with your OS package manager." >&2
  exit 1
}

mkdir -p "$THUMBS_DIR"
export THUMBS_DIR
find "$UPLOAD_DIR" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' \) \
  -exec sh -c '
    for image do
      name=$(basename "$image")
      target="$THUMBS_DIR/${name%.*}.jpg"
      [ -f "$target" ] || magick "$image" -auto-orient -resize "480x480>" -background white -alpha remove -quality 82 "$target"
    done
  ' sh {} +

docker compose exec -T db psql -U "${POSTGRES_USER:-shlyapoff_user}" -d "${POSTGRES_DB:-shlyapoff_db}" \
  -c "UPDATE products SET image_thumbnail_url = '/images/thumbs/' || regexp_replace(image_url, '^.*/', '') WHERE image_url LIKE '/images/%' AND image_thumbnail_url IS NULL;"
