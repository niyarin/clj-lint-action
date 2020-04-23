#!/bin/sh -l

cd /lint-action-clj

echo "??"
echo ${GITHUB_BASE_REF}
echo ${GITHUB_HEAD_REF}

clojure -m lint-action "{:linters $1 :cwd \"/github/workspace/\" :mode :github-action :relative-dir $2  :file-target :git :runner $3}"
