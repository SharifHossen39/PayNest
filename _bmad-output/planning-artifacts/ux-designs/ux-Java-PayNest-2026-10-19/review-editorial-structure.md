# Editorial structure check

These documents help human designers and developers build the approved PayNest interface with consistent appearance, behavior and recovery states. Reader calibration: humans. Style guide: Microsoft Writing Style Guide. Structure model: Reference/Database, with required UX section order retained.

Exact metrics from `uv run --no-cache .agents/skills/bmad-review/scripts/word_metrics.py`:

| Document | Total words | Section counts |
|---|---:|---|
| DESIGN.md | 1,530 | Preamble 257; Brand & Style 121; Colors 237; Typography 89; Layout & Spacing 277; Elevation & Depth 36; Shapes 37; Components 346; Do's and Don'ts 106 |
| EXPERIENCE.md | 3,126 | Preamble 20; Foundation 151; Information Architecture 245; Voice and Tone 197; Component Patterns 436; State Patterns 573; Transfer completion and recovery 213; Interaction Primitives 199; Accessibility Floor 120; Responsive & Platform 154; FE-UJ-1 123; FE-UJ-2 126; FE-UJ-3 116; FE-UJ-4 162; Handoff and verification boundaries 125 |

No findings.

The canonical sections provide predictable reference access; token, layout and component tables separate visual decisions from behavioral rules. The per-screen states and named journeys repeat important transfer/authentication constraints for distinct lookup and walkthrough uses, rather than duplicating an unnecessary overview. The recovery subsection provides useful detail after the state overview. Mock links name what each reference illustrates. No structural change is needed for proportionate polish.

Recommendations: 0. Word reduction: 0 words (0% of the combined 4,656 words). No length target was supplied for this pass. No comprehension trade-offs or spine edits proposed.
