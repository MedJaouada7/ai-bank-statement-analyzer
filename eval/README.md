# These rules will be enforced by the DatasetValidator
* IDs are unique, and every category is in the taxonomy version in the manifest.
* A merchant_group appears in only one of validation or test. Seed groups may also appear in test, and those are the "seen merchant" items. Seen vs unseen is derived at run time and not stored.
* Amount sign agrees with the category's direction (INCOME is a credit).
* Seed items are the only ones usable as few-shot or kNN examples. The taxonomy's examples must be from seed groups.
* Test items are frozen. Tune on validation, run test once per configuration, and don't edit items after seeing results. If you must change it, create the next version so the old results stay honest.