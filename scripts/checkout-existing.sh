#!/bin/bash

# Usage: ./checkout_existing.sh <commit/branch> <directory>

if [ $# -ne 2 ]; then
    echo "Usage: $0 <commit/branch> <directory>"
    echo "Example: $0 abc123def src/components"
    exit 1
fi

SOURCE="$1"
DIRECTORY="$2"

# Verify the source commit/branch exists
if ! git cat-file -e "$SOURCE" 2>/dev/null; then
    echo "Error: '$SOURCE' is not a valid commit or branch"
    exit 1
fi

# Counter for checked out files
count=0

# Iterate through files in the source
for file in $(git ls-tree -r --name-only "$SOURCE" "$DIRECTORY"); do
    # Check if file exists in current branch
    if git cat-file -e HEAD:"$file" 2>/dev/null; then
        echo "Checking out: $file"
        git checkout "$SOURCE" -- "$file"
        ((count++))
    fi
done

if [ $count -eq 0 ]; then
    echo "No matching files found in current branch"
else
    echo "Checked out $count file(s) from $SOURCE"
    echo "Don't forget to commit the changes:"
    echo "  git commit -m 'Update files from $SOURCE'"
fi