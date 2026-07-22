# Codecks Artifact Builder Skill

Use this skill when the user prompts Codecks AI to create or refine a deck button, deck, or rule.

## Inputs
- User request in natural language.
- Draft kind requested by the app: `action`, `deck`, or `automation`.
- Supported capabilities are supplied by the app for the current phone and Mac.
- Target selector from the app: any connected Mac, current device, device id, or group id.

## Artifact Types

### Button
Create a single `definition` with a short title, clear description, safety metadata, and one or more steps. Good buttons do one obvious thing.

### Deck
Create a deck with 4-12 actions. Use consistent naming and button intent. Good templates include Browser, Media, Meetings, Window Manager, Developer, Presentation, System Controls, Finder, Terminal, and Focus.

### Automation
Create one rule with trigger intent in the title/description and steps that reuse the same action definition model. Rules are manual-testable; background triggers can be described but must not be assumed active unless the schema supports them.

### Clock Or Background Panel
If the user asks for an app-only visual or setting, explain that it needs an app feature instead of substituting a no-op command.

## Step Types
- `open_url`: requires `url`.
- `clipboard_text`: requires `text`.
- `delay`: requires `delayMs`.
- `template`: requires one built-in `templateId`.
- `command`: requires readable shell, script, SSH-tool, or AppleScript text in `command`.

## Built-in Mac Templates
Use supplied IDs when one exactly matches. Otherwise generate a `command` step. The app blocks destructive/exfiltration patterns, displays the command for review, and saves accepted work disabled until testing.

## Quality Rules
- Titles: 2-4 words, button-ready.
- Descriptions: one sentence, direct.
- IDs: lowercase stable slugs with dots or underscores.
- Commands must fail clearly when prerequisites are missing.
- Do not generate more than 12 deck actions.
- If an action might close, overwrite, purchase, send, share, expose private data, or change settings, set `safety.requiresConfirmation` to true and `safety.level` to `Dangerous`.
- Dangerous proposals must explain the concrete consequence in `safety.confirmationBody`.
- Return `unsupported` only when neither a typed step nor a command can provide the behavior.

## Response
Return only the schema JSON requested by the app.
