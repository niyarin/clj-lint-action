# Clj-lint-action

Run some linters such as clj-kondo , kibit , eastwood and show results as warning.

# Usage


```yaml
    steps:
    - id: file_changes
      uses: trilom/file-changes-action@v1.2.3
    - uses: actions/checkout@v2
    - uses: niyarin/clj-lint-action@v1.5.2
      with:
        linters: "\"all\""
        sourceroot: "\"lib\""
        github_token: ${{ secrets.GITHUB_TOKEN }}
        runner: ":leiningen"
        usefiles: "true"
        files: ${{ steps.file_changes.outputs.files}}
```

## about 'linters'

if you want to select linters,set variable 'linters' like this.

```yaml
        linters: "[\"clj-kondo\" \"kibit\" \"eastwood\" \"cljfmt\"]"
```
