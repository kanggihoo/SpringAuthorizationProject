# Testcontainers for Integration Tests

We decided that integration tests and persistence-backed tests should use Testcontainers with the same infrastructure family as production, currently PostgreSQL and Redis, instead of H2 or other in-memory substitutes. The project treats security evidence as the boundary of done, so tests that prove token state, persistence constraints, and Redis-backed authentication behavior must run against infrastructure close enough to production to catch vendor-specific behavior.

**Consequences**

H2 should not be introduced as a shortcut for database or token-store tests. Tests may cost more to run, but they provide stronger evidence for authentication and authorization failure scenarios.
