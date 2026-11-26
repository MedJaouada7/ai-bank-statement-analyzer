BANK STATEMENT ANALYZER — DATABASE TABLE SUMMARY
=================================================

This document summarizes the 7 tables in the UML class diagram.

**Legend:**
- PK = Primary Key
- FK = Foreign Key
- VECTOR = pgvector embedding
- Attributes are required unless marked "nullable".

**Conventions:**
- Amounts are signed: negative = debit (money out), positive = credit (money in).
There is no separate direction column, so the two can never contradict each other.
- Categories are not a table. Category columns hold a text value from the application's taxonomy (`taxonomy-v1.yml`, mirrored by the `Category` enum), enforced with a CHECK constraint.
- Merchants are not a table either. A merchant is only a display name (merchantName).
- Authentication is built into the application: it issues its own JWTs (Spring Security).


## User
**Purpose:**
Stores the application's registered users and their login identity.

**Attributes:**
- id (PK, BIGINT): Unique identifier for the user.
- email (unique): User's login email address.
- passwordHash: BCrypt or Argon2 hash of the password.


## BankAccount

**Purpose:**
Represents a bank account owned by a user. Statements and transactions belong to an account, so every financial query is scoped through it.

**Attributes:**
- id (PK, BIGINT): Unique identifier for the bank account.
- userId (FK -> User.id): Owner of the account.
- bankName: Name of the bank.
- currency: ISO 4217 code of the account (for example EUR), only EUR and USD will be supported for now.


## BankStatement

**Purpose:**
Represents one import: a PDF bank statement. It records which file was imported, what it contained and what happened. The original file is not kept; only its hash and the extracted data are stored (privacy).

**Attributes:**
- id (PK, BIGINT): Unique identifier for the statement.
- bankAccountId (FK -> BankAccount.id): Account to which the statement belongs.
- fileName: Original name of the imported file.
- fileHash: SHA-256 hash of the file content, used to detect re-uploads of the same file.
- periodStart (nullable): First date covered by the statement.
- periodEnd (nullable): Last date covered by the statement.
- openingBalance (nullable): Balance at the start of the period.
- closingBalance (nullable): Balance at the end of the period.
- reconciliationStatus (nullable): `VERIFIED` when : opening balance + credits - debits = the closing balance, `MISMATCH` otherwise.
- status: `PROCESSING`, `COMPLETED` or `FAILED`.
- importedCount: Number of new transactions inserted.
- duplicateCount: Number of rows skipped because the transaction already existed.
- rejectedCount: Number of rows that could not be read. Row-level error details are
  returned in the API response and are not stored.
- createdAt: Timestamp of the import.


## BankTransaction

**Purpose:**
Stores each transaction extracted from a statement. The table is named BankTransaction, not Transaction, to avoid confusion with SQL and JPA transactions.

**Attributes:**
- id (PK, BIGINT): Unique identifier for the transaction.
- bankAccountId (FK -> BankAccount.id): Account the transaction belongs to. Stored
  directly so that user-scoped queries and the duplicate check need no extra join.
- statementId (FK -> BankStatement.id): The import that inserted the transaction.
- transactionDate: Date on which the transaction occurred.
- originalDescription: Description exactly as provided by the bank; never modified.
- normalizedDescription: Cleaned description (noise removed, upper case, no accents)
  used for matching and categorization. Derived data.
- normalizerVersion: Version of the normalizer that produced normalizedDescription, so
  stale values can be found and recomputed from originalDescription after a change.
- amount: Signed NUMERIC amount (negative = debit, positive = credit).
- currency: ISO 4217 code of the transaction.
- mccCode (nullable): Merchant Category Code supplied by the bank, if available.
- dedupeKey: Hash of the account, date, amount, canonical description and the occurrence
  number of identical rows within the file. Two genuinely identical rows in one file are
  both kept. Importing the same or an overlapping file again adds nothing.


## TransactionCategorization

**Purpose:**
Stores the current categorization result for a transaction: the assigned category,
the display merchant, how confident the system is, and which stage decided it.
Keeping it separate from BankTransaction means re-categorizing never changes the
original data.

**Attributes:**
- id (PK, BIGINT): Unique identifier for the categorization record.
- bankTransactionId (FK -> BankTransaction.id, unique): Transaction being categorized.
- category: Category from the taxonomy.
- merchantName (nullable): Display name of the recognized merchant, if known.
- confidenceScore (nullable): Confidence produced by the deciding stage (1.0 for rules
  and manual choices).
- source: `RULE`, `KNN`, `LLM` or `MANUAL`. User corrections are stored as user rules, so a
  later match from one of them also shows as `RULE`.
- needsReview: true when the result is uncertain and should be validated by the user.
- model (nullable): Model that produced the result: the chat model for LLM, the
  embedding model for KNN, null for RULE and MANUAL. Needed to reproduce results.
- promptVersion (nullable): Version of the prompt used; set only for LLM results.


## CategorizationRule

**Purpose:**
Stores the rules used by the first (deterministic) stage of the categorization pipeline. Rules can be global (seed rules) or specific to one user. A category correction made by a user is stored here as an `EXACT` rule with top priority, so the same description is never asked about again and needs no LLM call.

**Attributes:**
- id (PK, BIGINT): Unique identifier for the rule.
- userId (FK -> User.id, nullable): Owner of a user-specific rule; null means global.
- ruleType: `MCC`, `KEYWORD`, `REGEX` or `EXACT` (`EXACT` matches the whole normalized description).
- matchValue: Value to match: an `MCC` code, a keyword, a regular expression or an exact normalized description.
- category: Category assigned when the rule matches (taxonomy value).
- merchantName (nullable): Merchant display name associated with the match, if known.
- priority: Precedence when several rules match. The LOWEST number wins. User
  corrections use 0 and global rules default to 100. On a tie, a user rule beats a
  global rule.
- isActive: Whether the rule is enabled.

## LabeledExample

**Purpose:**
Stores labeled transaction descriptions and their embeddings. The KNN stage retrieves
similar examples from this table, and they also serve as few-shot examples for the LLM
stage. Examples come from the seed data or from corrections confirmed by a user.
Evaluation test items must never be inserted here, to avoid leakage.

**Attributes:**
- id (PK, BIGINT): Unique identifier for the labeled example.
- userId (FK -> User.id, nullable): Owner of the example; null means a shared seed
  example. Examples created from user corrections are private to that user.
- category: Confirmed category (taxonomy value).
- merchantName (nullable): Display name of the merchant, if known.
- normalizedDescription: Normalized description used for similarity search.
- embedding (VECTOR): Embedding of the description, searched with pgvector.
- embeddingModel: Model that produced the embedding. Vectors from different models are
  not comparable, so changing the model requires re-embedding every example.
