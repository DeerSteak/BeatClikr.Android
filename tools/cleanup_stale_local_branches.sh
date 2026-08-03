#!/usr/bin/env bash

set -euo pipefail

remote="origin"
remote_set=false
force=false

for argument in "$@"; do
    case "$argument" in
        --force)
            force=true
            ;;
        --help|-h)
            printf 'Usage: %s [remote] [--force]\n' "${0##*/}"
            printf 'Deletes local branches absent from the remote; --force permits squash-merged branches.\n'
            exit 0
            ;;
        --*)
            printf 'Unknown option: %s\n' "$argument" >&2
            exit 1
            ;;
        *)
            if [[ "$remote_set" == true ]]; then
                printf 'Only one remote may be specified.\n' >&2
                exit 1
            fi
            remote="$argument"
            remote_set=true
            ;;
    esac
done

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

git remote get-url "$remote" >/dev/null
git fetch "$remote" --prune

current_branch="$(git branch --show-current)"
if [[ -z "$current_branch" ]]; then
    printf 'Refusing cleanup while HEAD is detached. Check out a retained branch first.\n' >&2
    exit 1
fi

found=false
failed=false
delete_option="-d"
if [[ "$force" == true ]]; then
    delete_option="-D"
    printf 'Force mode enabled for branches absent from %s.\n' "$remote"
fi

while IFS= read -r branch; do
    if git show-ref --verify --quiet "refs/remotes/$remote/$branch"; then
        continue
    fi

    found=true
    if [[ "$branch" == "$current_branch" ]]; then
        printf 'Skipping currently checked-out stale branch: %s\n' "$branch"
        continue
    fi

    if ! git branch "$delete_option" "$branch"; then
        printf 'Kept unmerged branch: %s\n' "$branch" >&2
        failed=true
    fi
done < <(git for-each-ref --format='%(refname:short)' refs/heads)

if [[ "$found" == false ]]; then
    printf 'No stale local branches found.\n'
fi

printf 'Cleanup complete. Untracked files were not modified.\n'
if [[ "$failed" == true ]]; then
    printf 'Review retained branches manually.\n' >&2
    exit 2
fi
