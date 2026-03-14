# Hubitat

## Testing

Run the test suite with:

```
./gradlew test
```

This executes the unit tests locally and is the same command used in CI.

> Note: automated sandbox environments may not have a Git remote configured, so validation should stop at local test execution (no `git push` required).
