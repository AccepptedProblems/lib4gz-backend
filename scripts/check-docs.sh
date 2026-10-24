#!/bin/bash
# ============================================================
# Keeps the CLAUDE.md files true.
#
#   1. Every file or folder a CLAUDE.md names in backticks must exist.   -> exit 1
#   2. Code changed in a folder that owns a CLAUDE.md, the doc did not.  -> reminder only
#
#   scripts/check-docs.sh             # working tree against HEAD
#   scripts/check-docs.sh origin/main # everything since that ref
# ============================================================

# Where a path written without its full prefix may live, besides the doc's own folder and the root.
SOURCE_ROOTS="src/main/kotlin/com/example/lib4gz src/test/kotlin/com/example/lib4gz"

BASE="${1:-HEAD}"
cd "$(git rev-parse --show-toplevel)" || exit 2

ALL_FILES=$(git ls-files --cached --others --exclude-standard)
DOCS=$(echo "$ALL_FILES" | grep -E '(^|/)CLAUDE\.md$' | while read -r f; do [ -f "$f" ] && echo "$f"; done)

exists() {
  local dir="$1" path="$2" root
  [ -e "$dir/$path" ] || [ -e "$path" ] && return 0
  for root in $SOURCE_ROOTS; do
    [ -e "$root/$path" ] && return 0
  done
  return 1
}

# ---- 1. Paths that no longer exist ----
missing=0
for doc in $DOCS; do
  dir=$(dirname "$doc")
  for token in $(grep -o '`[^`]*`' "$doc" | tr -d '`' | grep -v '[] <>*{}$()|=[]' | sort -u); do
    case "$token" in
      /*|..*|~*|http*) continue ;;                          # URL paths, other repos, home, links
    esac
    path="${token#./}"
    path="${path%%:*}"                                      # file.kt:42
    case "$path" in
      */) ;;                                                # a folder
      *.kt|*.kts|*.ts|*.js|*.json|*.md|*.sql|*.sh|*.yml|*.yaml|*.scss|*.html) ;;
      *) continue ;;                                        # not a path
    esac
    case "$path" in
      */*) exists "$dir" "$path" && continue ;;
      *)   echo "$ALL_FILES" | grep -qE "(^|/)$(printf '%s' "$path" | sed 's/[.[\*^$]/\\&/g')$" && continue ;;
    esac
    echo "MISSING  $doc names \`$token\`, which does not exist"
    missing=1
  done
done

# ---- 2. Code changed, doc did not ----
changed=$( (git diff --name-only "$BASE"; git ls-files --others --exclude-standard) | sort -u)
owners=$(echo "$changed" | grep -vE '(^|/)CLAUDE\.md$' | while read -r file; do
  [ -z "$file" ] && continue
  dir=$(dirname "$file")
  while [ "$dir" != "." ]; do
    if [ -f "$dir/CLAUDE.md" ]; then echo "$dir"; break; fi
    dir=$(dirname "$dir")
  done
done | sort -u)

for dir in $owners; do
  echo "$changed" | grep -qxF "$dir/CLAUDE.md" && continue
  echo "REMINDER $dir/ changed but $dir/CLAUDE.md did not. Is it still true?"
done

[ "$missing" -eq 0 ] && echo "check-docs: every path named in a CLAUDE.md exists."
exit "$missing"
