# 2026-05-01 - Initial discovery

## Summary
Created the Delano project for AI Dictation Rewrite based on Bart's request for one-tap cleanup of recently dictated text.

## Findings
- Existing Voxtral dictation flow already commits transcripts through the editor and stores API keys securely.
- ML Kit Proofreading is promising because it explicitly supports voice-origin text.
- ML Kit Rewriting is promising for later tone/style actions but may be too broad for the first grammar cleanup use case.
- On-device AI is worth probing, but provider abstraction is safer than betting the MVP on beta/local availability.
- The original message context for a second issue is truncated and must be clarified before final scope approval.

## Next
Run provider and editor replacement probes, then expand implementation tasks.
