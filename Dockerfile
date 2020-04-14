FROM alpine:3.10

FROM borkdude/clj-kondo AS clj-kondo

FROM clojure AS lein
FROM clojure:tools-deps-alpine

COPY --from=clj-kondo /usr/local/bin/clj-kondo /usr/local/bin/clj-kondo

RUN apk add git

COPY LICENSE README.md /

COPY entrypoint.sh /entrypoint.sh
COPY lib /lint-action-clj

ENTRYPOINT ["/entrypoint.sh"]
