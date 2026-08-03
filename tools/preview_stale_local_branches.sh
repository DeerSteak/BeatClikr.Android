#!/usr/bin/env bash

set -euo pipefail

remote="${1:-origin}"
repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

git remote get-url "$remote" >/dev/null
git fetch "$remote" --prune

current_branch="$(git branch --show-current)"
found=false

printf 'Local branches absent from %s:\n' "$remote"
while IFS= read -r branch; do
    if ! git show-ref --verify --quiet "refs/remotes/$remote/$branch"; then
        if [[ "$branch" == "$current_branch" ]]; then
            printf '  %s (currently checked out; cleanup will skip it)\n' "$branch"
        else
            printf '  %s\n' "$branch"
        fi
        found=true
    fi
done < <(git for-each-ref --format='%(refname:short)' refs/heads)

if [[ "$found" == false ]]; then
    printf '  none\n'
fi

printf '\nUntracked files were not modified.\n'
