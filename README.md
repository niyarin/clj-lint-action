# Clj-lint-action

Run some linters such as clj-kondo , kibit , eastwood and show results as warning.

# Usage


```yaml
    steps:
    - uses: actions/checkout@v1
    - uses: niyarin/clj-lint-action@v1.1
      with:
        linters: "\"all\""
        github_token: ${{ secrets.GITHUB_TOKEN }}
```
