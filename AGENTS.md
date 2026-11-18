# Repository working rules

## Scope and current authorization

- This repository is the Java solution for the Ikigai time-deposit take-home challenge.
- Work in `java/`; preserve the other language examples.
- Build and test this solution with JDK 25; keep the Maven Java release set to 25.
- The user authorized Java implementation on 17 November 2026. Keep the solution proportionate to a roughly one-hour exercise: small, direct, extensible, and with meaningful database concurrency protection.
- Do not create commits, push, create branches, or open pull requests without the user's instruction. Never write to the original ikigai-digital repository.
- Read `README.md` and `IMPLEMENTATION_PLAN.md` before implementation; distinguish implemented behavior from proposed extensions.

## Mandatory repository identity

The only permitted GitHub account for this repository is **jambo67**.

New commits must have BOTH of these identities:

- Author name: `jambo67`
- Author email: `309137753+jambo67@users.noreply.github.com`
- Committer name: `jambo67`
- Committer email: `309137753+jambo67@users.noreply.github.com`

These are repository-local settings. Never change global Git configuration or use a corporate account, email, signing key, or another person's identity for this repository. Preserve inherited upstream history; do not rewrite historical commits to change their authors.

Before any commit:

1. Verify `git var GIT_AUTHOR_IDENT` and `git var GIT_COMMITTER_IDENT` match the required name and email.
2. Verify the effective identity settings are supplied by this repository's local configuration.
3. Verify `core.hooksPath` points to this clone's `.git/identity-hooks` and its identity guards are installed.
4. Keep automatic commit signing disabled unless the user explicitly configures signing for the permitted account.
5. Do not bypass the guards with `--no-verify`, alternate hook paths, identity overrides, or low-level commit creation.

Before any authenticated GitHub write or push, verify `gh api user --jq '.login'` returns `jambo67` and the destination is `https://github.com/jambo67/time-deposit-take-home-kata.git`. Do not switch to a corporate account to work around authentication problems. Stop and ask the user if the permitted account is unavailable.

The local credential-helper configuration resets inherited helpers and uses GitHub CLI. Local pre-commit and prepare-commit-msg guards check the effective author and committer. A local pre-push guard checks the destination and authenticated GitHub account. Hooks are local safeguards and are not automatically installed by a fresh clone; verify or restore them before committing in another clone. This file remains the rule even if local hooks are absent.

## Challenge constraints

- Preserve the public `org.ikigaidigital.TimeDeposit` class and the signature and existing behavior of `TimeDepositCalculator.updateBalance(List<TimeDeposit>)`. Preserve the existing no-argument calculator constructor.
- Add exactly two challenge API operations: list all deposits and update all balances.
- Persist deposits and withdrawals using the required schema and response field names.
- Characterize the existing calculator before refactoring. Its existing test asserts a constant and does not verify balance changes.
- Preserve the implemented annual-rate-divided-by-12 calculation, eligibility boundaries, rounding, and unchanged day values unless the user explicitly authorizes a behavioral change.
- Keep domain logic independent of web and persistence frameworks. Add abstractions only for concrete extension points.
- Optional independent balance adjustments use the same immutable opening-balance context and apply separately from base-interest eligibility. Enable no promotion or deduction without supplied business rules; keep the default adjustment list empty.
- Document assumptions about withdrawals, update frequency, and the API contract before coding them.
- Use a reproducible Java/Maven build, meaningful tests, database integration checks, an OpenAPI contract, and concise AI-workflow documentation.
- Keep personal files, credentials, local machine configuration, and unrelated private project material out of this public repository.
