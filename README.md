# Clj-lint-action

Run some linters such as clj-kondo , kibit , eastwood and show results as warning.

# Usage


```yaml
    steps:
    - uses: actions/checkout@v1
    - uses: niyarin/clj-lint-action@v1.2
      with:
        linters: "\"all\""
        github_token: ${{ secrets.GITHUB_TOKEN }}
```

## about 'linters'

if you want to select linters,set variable 'linters' like this.

```yaml
        linters: "[\"clj-kondo\" \"kibit\" \"eastwood\" \"cljfmt\"]"
```
