#!/bin/sh -l

cd /lint-action-clj

echo "??"
echo ${GITHUB_SHA}
echo ${GITHUB_WORKSPACE}

clojure -m lint-action "{:linters $1 :cwd \"${GITHUB_WORKSPACE}\" :mode :github-action :relative-dir $2  :file-target :git :runner $3 :git-sha \"${GITHUB_SHA}\" }"
