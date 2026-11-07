#!/bin/sh
# One-time GitHub setup for the fork. NOT run automatically: run it yourself after creating the repository
# and deciding to publish. Needs the GitHub CLI (gh) logged in.
#
#   sh tools/github_setup.sh OWNER/REPO            create labels and issues only
#   sh tools/github_setup.sh OWNER/REPO --release  also upload the jars as release v1.3-perf.2 (draft)
#
# It never pushes code: push the branch and tags yourself with
#   git remote add origin https://github.com/OWNER/REPO.git
#   git push -u origin main --tags
set -e
REPO="$1"
[ -n "$REPO" ] || { echo "usage: sh tools/github_setup.sh OWNER/REPO [--release]"; exit 1; }

echo "== labels"
python -I - "$REPO" <<'EOF'
import re, subprocess, sys
repo = sys.argv[1]
for line in open(".github/labels.yml", encoding="utf8"):
    m = re.search(r"name:\s*([\w-]+),\s*color:\s*(\w+),\s*description:\s*(.+?)\s*}", line)
    if m:
        name, color, desc = m.groups()
        subprocess.run(["gh", "label", "create", name, "--color", color, "--description", desc, "--force", "-R", repo], check=True)
EOF

echo "== issues"
for f in docs/issues/*.md; do
  title=$(sed -n 's/^title: "\(.*\)"$/\1/p' "$f")
  labels=$(sed -n 's/^labels: \[\(.*\)\]$/\1/p' "$f" | tr -d ' ')
  body=$(awk 'BEGIN{n=0} /^---$/{n++; next} n>=2' "$f")
  gh issue create -R "$REPO" --title "$title" --label "$labels" --body "$body"
done

if [ "$2" = "--release" ]; then
  echo "== draft release"
  gh release create v1.3-perf.2 -R "$REPO" --draft --title "1.3-perf.2" --notes-file docs/releases/v1.3-perf.2.md \
    realistic-water-1.3-tsunami.jar realistic-water-1.3-lowend.jar realistic-water-1.3-balanced.jar
fi
echo done
