# Measured native-window edge correction

Two authentic pre-cleanup probes in CI45766109135 prove matching resumed
components, Activity/Decor/native window IDs, focus, active state, visibility and
package. Android reports native windows as479x853 while their exact accessible
root and Decor are480x854. The same one-pixel representation difference appears
in independent SystemUI status/navigation windows. Raw probes remain in
third-native-ci; neither failed run is relabeled as passing.

The correction requires exact native-root/Decor equality, three nonempty onscreen
rectangles and at most one pixel between each native-window edge and Decor.
Passing proof includes all raw bounds and the computed maximum difference;
Python recomputes it independently. Two pixels, false summaries, wrong windowID,
focus or root/Decor identity still reject. Exact target AX geometry remains.

Actual full tooling109PASS, focused modern48PASS, Androidtest APK compile PASS
and lint zero errors/16warnings; build56seconds. All226tracked inputs remained
unchanged during checks. Production/host/debug/signing/workflows are unchanged
from authentic467host/45classCI PASS, so no local host rerun was needed.
Independent review exercised16valid cases,65two-pixel refusals,32invalid raw
bounds/claimed summaries and17full-gate window/focus/content refusals. All40native
method IDs, seven prior gates and27modern/272total captures remain required.

This is local code/contract proof only. Fresh native execution and full actual
272-image review remain pending; no publication exception or hardware/update
verification is established.
