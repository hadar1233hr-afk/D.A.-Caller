# Native coordinate fixes: compile and lint proof

Only ModernFeaturesIntegrationTest and PersonalizationSnapshotTest differ from
the production/unit source that passed457 methods and CI457 at3db5a5f. No local
unit rerun is claimed. Final Android-test APK build and lint passed; all app
source hashes stayed unchanged. Lint:0errors/0fatal/16warnings. The raw Gradle
return marker is0 and the log reports BUILD SUCCESSFUL; the unified wrapper
returned1, retained explicitly. Fresh independent CI is required.

Independent source review found one additional missed conversion before the
new run: Personalization stableNativeBounds also compared window-relative
visibility with native screen bounds. Both producers now translate by the real
root screen-minus-window origin before exact nativeAX comparison and pointer
input. Raw full-size checks, windowfocus,48dp controls and stability remain.
The legacy five-icon final-row assumption is replaced by exact-row reveal for
the nine-icon list. All test names/captures/old7 verifiers are preserved.

These checks are compilation/source-review evidence, not a new native/visual or
publication approval. Previous40-case36PASS/4FAIL remains historical failure.
Production/unit/business/signing/workflow sources are unchanged.0.12delivered
immutable/noresend,0.13unreleased,existingautomationactive.
