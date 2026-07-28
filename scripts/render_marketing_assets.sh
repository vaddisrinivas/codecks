#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
framecraft_dir="${FRAMECRAFT_DIR:-$(cd "$repo_dir/.." && pwd)/framecraft}"
framecraft_cli="$framecraft_dir/framecraft.py"
config_path="$repo_dir/docs/marketing/framecraft-demo.json"
video_path="$repo_dir/docs/images/codecks-demo.mp4"
gif_path="$repo_dir/docs/images/codecks-demo.gif"
render_tmp_dir="$(mktemp -d /tmp/codecks-marketing-render.XXXXXX)"
teaser_path="$render_tmp_dir/codecks-demo-teaser.mp4"

cleanup() {
  rm -rf "$render_tmp_dir"
}
trap cleanup EXIT

if [[ ! -f "$framecraft_cli" ]]; then
  echo "Framecraft not found: $framecraft_cli" >&2
  echo "Set FRAMECRAFT_DIR to a vaddisrinivas/framecraft checkout." >&2
  exit 1
fi

cd "$repo_dir"

uv run --project "$framecraft_dir" \
  python "$framecraft_cli" render \
  "$config_path" \
  --output "$video_path" \
  --auto-duration

uv run --project "$framecraft_dir" \
  python "$framecraft_cli" validate "$video_path"

ffmpeg -y -i "$video_path" \
  -filter_complex \
  "[0:v]trim=start=2:end=7,setpts=PTS-STARTPTS[v0];\
[0:v]trim=start=19.3:end=24.3,setpts=PTS-STARTPTS[v1];\
[0:v]trim=start=25.2:end=30.2,setpts=PTS-STARTPTS[v2];\
[0:v]trim=start=32.2:end=37.2,setpts=PTS-STARTPTS[v3];\
[0:v]trim=start=63.5:end=68.5,setpts=PTS-STARTPTS[v4];\
[v0][v1][v2][v3][v4]concat=n=5:v=1:a=0[teaser]" \
  -map "[teaser]" \
  -an \
  -c:v libx264 \
  -crf 20 \
  -pix_fmt yuv420p \
  "$teaser_path"

ffmpeg -y -i "$teaser_path" \
  -vf \
  "fps=8,scale=640:360:flags=lanczos,split[s0][s1];\
[s0]palettegen=max_colors=128[p];\
[s1][p]paletteuse=dither=bayer:bayer_scale=3" \
  "$gif_path"

gif_size="$(wc -c < "$gif_path")"
if (( gif_size >= 5242880 )); then
  echo "README GIF exceeds 5 MiB: $gif_size bytes" >&2
  exit 1
fi

echo "Rendered: $video_path"
echo "Rendered: $gif_path ($gif_size bytes)"
