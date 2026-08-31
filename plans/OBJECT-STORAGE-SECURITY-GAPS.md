# Object Storage Security Gaps

Date: 2026-08-30

Scope: Static review of `src/main/kotlin/com/docuhyphen/app/api/service/storage/`, the audit
archive storage under `service/audit/archive/`, and their callers.

Status: **Identified, not scheduled.** This document records two gaps for later remediation.
Neither is a live incident and neither has an owner or target release yet. Deliberately not an
implementation plan — the open questions in each section must be answered before design work
starts, because the answers change the shape of the fix.

## Summary

| # | Gap | Severity | Currently exploitable |
|---|-----|----------|-----------------------|
| 1 | Application-layer document encryption is dead code with no key management | Low today, high if enabled | No |
| 2 | Object storage has no tenant partitioning | Medium | No |

Both are latent rather than active. Gap 1 is inert behind a compile-time flag; Gap 2 is held
closed by application-layer authorization with no independent backstop underneath it.

---

## 1. Application-layer document encryption is dead code with no key management

`AwsS3FileStorageService` carries a complete-looking envelope-encryption path that is disabled by
a compile-time constant and could not work if it were enabled.

Evidence:

- `AwsS3FileStorageService.kt:35` — `private const val ENABLE_ENCRYPTION = false`
- `AwsS3FileStorageService.kt:60-85` — `generateKey`, `encryptFile`, `decryptFile`, all unreachable
- `AwsS3FileStorageService.kt:113-115` — `uploadDocument` returns the base64-encoded per-object key
- `AwsS3FileStorageService.kt:141-144` — the download path, commented `// Future decryption path`,
  base64-decodes the literal string `"REPLACE_WITH_REAL_KEY"`

The key returned by `uploadDocument` is discarded by every caller:

- `ExchangeDocumentService.kt:246`
- `ExchangeDocumentService.kt:331`
- `DocumentLibraryService.kt:145`
- `ExchangeInitiationService.kt:243`

No schema column stores a per-object encryption key. A search across all 73 migrations in
`src/main/resources/db/migration/` for an encryption-key column returns nothing.

Impact: setting `ENABLE_ENCRYPTION = true` today would break every document download. Uploads would
encrypt with a key that is generated, returned, and immediately dropped; downloads would fail
decoding the placeholder string. The flag reads as a feature toggle but is a trap.

Two secondary observations, relevant only if this path is ever revived:

- `Cipher.getInstance("AES")` (`AwsS3FileStorageService.kt:69`, `:79`) resolves to
  `AES/ECB/PKCS5Padding`. ECB is not appropriate for document content, and the path carries no IV
  and no authentication tag.
- `BouncyCastleProvider` is registered at class-init (`:39`) solely for this dead path.

Not a gap in encryption at rest: the S3 buckets have bucket-level encryption enabled
(`INFRA-SECURITY-ANALYSIS.md`, "Positive controls observed"). Documents are encrypted at rest today.
What is missing is the application-layer envelope encryption this code gestures at.

Open questions to resolve before implementation:

1. Is application-managed encryption actually required on top of S3 SSE? It only earns its
   complexity if a specific requirement demands it — per-tenant key custody, customer-managed keys,
   or crypto-shredding as a deletion mechanism.
2. If yes: this should be KMS envelope encryption with a persisted key reference (key ARN plus
   encrypted data key per object), not the hand-rolled AES path. That requires a schema change and
   a decision on where the key reference lives for each of the storage-backed entities.
3. If no: delete the flag, the three crypto helpers, the BouncyCastle registration, and the
   placeholder download branch. Leaving disabled crypto in place invites someone to flip the flag.
4. Either way, decide what `uploadDocument` should return. Its current `String` return value is
   meaningful only under encryption and is ignored everywhere.

## 2. Object storage has no tenant partitioning

Every object key is derived from a resource UUID with no organization segment, and each content
class uses a single bucket shared by all tenants.

Current key layout:

| Content | Key pattern | Constructed at | Tenant segment |
|---------|-------------|----------------|----------------|
| Exchange documents | `{documentId}.{ext}` | `ExchangeDocumentService.kt:246`, `:331` | none |
| Document library | `lib/{entryId}.{ext}` | `DocumentLibraryService.kt:144` | none |
| Thumbnails | `{documentId}/{hash}/page-1.png` | `DocumentThumbnailService.kt:178` | none |
| Avatars | `avatars/{userId}.{ext}` | `AppUserAvatarService.kt:69` | user, not org |
| Audit archive | `archive/{streamId}/...` | `AuditArchiveVerifier.kt:374` | stream, not org-derived |

The ECS task role grants `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject` and `s3:ListBucket` on
the whole bucket with no prefix condition (`infra/cloudformation.yml:407-438`).

Impact: tenant isolation for blob content rests entirely on the application resolving authorization
before it resolves a key. There is no prefix condition, bucket policy, or per-tenant key that would
contain an IDOR, a path-traversal, or a missing service-level check — the same absence of a
defence-in-depth layer that the database has, where there is no row-level security either. A
compromised or confused task role reaches every tenant's objects.

Mitigating factor today: keys are UUID-derived, so they are not enumerable by guessing. An attacker
needs a leaked or inferred document ID, which narrows this to a second-order gap rather than a
direct exposure.

Beyond isolation, the flat layout also blocks per-tenant deletion and crypto-shredding, per-tenant
retention or residency guarantees, and per-tenant storage cost attribution — each of which is
likely to surface as an enterprise customer requirement before it surfaces as a security finding.

Open questions to resolve before implementation:

1. Prefix-per-organization within the shared bucket, or bucket-per-tenant? Prefixes are far cheaper
   to operate; separate buckets give harder isolation and per-tenant lifecycle policy. Expected
   tenant count decides this.
2. What is the key shape for `OwnerContext.Personal` resources, which have no owning organization?
   The three-way `Platform`/`Organization`/`Personal` ownership model in `OwnerContext.kt` has to
   map onto the prefix scheme without a null segment.
3. Existing objects need re-keying, and keys are persisted in `Document.storagePath`,
   `DocumentLibraryEntry.storagePath`, `AppUser.avatarStorageKey`, and the thumbnail descriptors.
   Migration is a coordinated copy plus column rewrite, not a rename. Decide whether to migrate or
   to dual-read old and new layouts during a transition window.
4. Should IAM enforce the prefix, or is the application-side key builder sufficient? IAM conditions
   are what make this defence in depth rather than a naming convention — but they require the
   request principal to carry the tenant, which the shared task role does not.
5. Does the audit archive stay on its own `archive/{streamId}/` scheme, or fold into the same
   convention? Its Object Lock and retention posture differ from the document buckets.

## Adjacent observation

Not part of either gap, but noted during the review: the IAM statements at
`infra/cloudformation.yml:411-412`, `:424-425`, and `:437-438` grant object-level actions
(`s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`) while listing only the bucket ARN as `Resource`,
without the `${Bucket.Arn}/*` object ARN. The audit bucket statement at `:454-456` includes both.
Worth confirming against the deployed policy — if the object ARN is genuinely absent, these grants
do not cover the object operations the application performs.
