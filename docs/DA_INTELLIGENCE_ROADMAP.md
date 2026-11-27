# DA Intelligence — owner-authorized roadmap (2026-11-26)
Owner explicitly authorized implementation on 2026-11-26 after receiving the 0.14.0 tested debug APK. Keep 0.14 finishing and permanent signing independent of AI changes.
- Hebrew/English natural language dialing, contact/business search, missed calls, call history queries, reminders and settings commands.
- Offline speech recognition, optional local model bundled or imported from local file; no INTERNET permission. Start with deterministic lightweight command routing, optional compact language model for complex queries. Measure Hebrew accuracy, latency, RAM and battery on Jelly Star (8 GB RAM); Lite / Smart / Pro sizes are hypotheses, not requirements.
- If supported and explicitly authorized, local call recording/transcription and summaries, extracted tasks, calendar events and reminders; recording and both-side audio require actual device validation and user controls.
- Per-contact memory, notes, commitments, searchable transcripts, daily brief, unanswered-call follow-up, local spam heuristics, duplicate contacts, usage statistics, SMS drafting and habit-based suggestions.
- Assistant tab/button and optional dynamic island integration, privacy-first local storage, opt-in retention and deletion; explicit confirmation for dialing, sending, destructive actions and calendar writes. Never fabricate contact matches or transcript facts.
- Keep app identity, offline behavior, archive, backups, business data, call screens, navigation and all signing/publication safeguards. No changes to NEX, DA PUSH or other products.

## Phase 1 in progress

Latest continuation: the offline text dialog and actual permission-aware provider/business reads are implemented in PR8. Matching and stale-confirmation/AppOps corrections plus dedicated native coverage are being validated for0.15.0/code24. They are not yet a verified APK. Full native/visual work precedes local model/speech integration; no model/speech/summary capability is claimed.

Historical initial foundation checkpoint:
Branch feature/da-intelligence-0.15 from 1a4926785a72bec25c188f9c5b74116c5b498c31. LocalAssistant.java is a deterministic bilingual proposal-only parser, with LocalAssistantTest.java coverage. No UI hookup, AI model, speech, database access, action execution or new APK yet. Next: read-only permission-aware actual contact/history/business query integration; explicit disambiguation and confirmation before calls; NEX assistant UI; test native behavior and offline performance. Do not log queries or add network permission. Keep unsupported commands explicit, no invented results. Choose next version only after test/release contract updates. Rebase or merge latest approved redesign corrections without overwriting concurrent work.
