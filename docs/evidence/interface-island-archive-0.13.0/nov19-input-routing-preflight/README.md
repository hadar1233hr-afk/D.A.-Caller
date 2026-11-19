# Native input routing and modest overflow polish

Current source passed457 host methods/44classes, zero failures/errors/skips;
debug+Android-test APK builds and lint passed (0errors/16warnings). Every app
source hash stayed unchanged. Five focused space cases also passed. One initial
new-decoration compile failed because a lambda name shadowed a local; it ran
zero tests and is retained separately. Local APK37cd0fc5... is not native-tested
and is not approved for publication.

Actual last CI44442075483 completed40cases,39PASS/1FAIL. All245 original captures
and13/24modern were present, plus2diagnostics;260rawPNGs is not269approval. Its
failed drag image shows expanded SystemUI shade. Historical first-DOWN routing
was not recorded; HUN/touch occlusion is a hypothesis, not a proved cause or a
proved production drag defect. Raw evidence is in fourth-native-ci.

The test now reads actual API33 InputDispatcher window order/flags/regions,
requires the actual first eligible input window at the point to be the owned
island with two stable geometries, and then checks the actual foreground touch
stream after crossing slop. One DOWN/eight MOVE/UP-or-CANCEL only; wrong routing
cancels and fails. It does not suppress OS notifications, weaken pointer/token/
position/movement checks or change the24-frame profile. Source format and rules
were independently reviewed against the pinned AOSP-derived source. Actual
runtime parsing/gesture success still require fresh CI.

Overflow decoration now adds transparent rounded clipping, subtle fading and
a thin visible scrollbar. No extra background paint, geometry, margins, tags,
call commands or gesture logic changed. Actual prior Main images motivated the
polish; its fresh native visual result remains pending. Frozen business data,
seven old verifiers, signing policy and CI mechanisms remain unchanged.

0.12alreadydeliveredimmutable/noresend;0.13unreleased;existingautomationactive.
