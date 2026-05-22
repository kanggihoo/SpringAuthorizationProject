# Test Evidence as the Auth Security Boundary

The project is an auth security lab, not a generic login feature project. We decided that security scenarios, automated tests, coverage, and evidence are the boundary of done because the important behavior is whether failures such as logout reuse, token tampering, role misuse, and account lock bypass are actually rejected.

**Consequences**

Before starting or completing a Phase, we document the security claims and required tests that prove those claims. Claims like "Phase 2 is complete" require those tests or equivalent evidence to pass, not only service code.

Implementation plans remain in `docs/plans/`, while the project-level proof converges into `docs/evidence.md` rather than many phase-specific evidence files. Phase completion is gated by the Phase's required evidence. The JaCoCo 80% line coverage target is a separate quality gate checked against the current HEAD codebase as a whole.
