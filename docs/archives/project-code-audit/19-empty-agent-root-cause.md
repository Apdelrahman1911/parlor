# Empty Task agents — root cause (logs + DB)

Not a Parlor Gradle/module config problem.

## What happened

Four Task sessions returned empty `<task_result>` to the parent:

| Session | Title | Steps | Last step-finish | Tokens out | Files written |
|---|---|---|---|---|---|
| `ses_fa53d2712ffe810kgiRoOBlxYl` | Product features v1 | 25 | `reason=length` (3 out tokens) | 4256 | no |
| `ses_fa53d05ecffeiIF55v0DT2iFJP` | Architecture | 25 | `reason=stop` 0 tokens | 13960 | yes |
| `ses_fa500b8e7ffeDpQViwir8mYblo` | Product features v2 | 11 | `reason=stop` 0 tokens | 2568 | no |
| `ses_fa500a01fffepT7ILu13UBNB8L` | Performance v2 | 9 | `reason=stop` 0 tokens | 2147 | no |

Source: `~/.local/share/opencode/log/opencode.log` + `opencode.db` `part` / `session` tables. No `level=ERROR` on those child sessions. Parent only saw empty text.

## Causes (two, both silent)

1. **Step/length cap (v1 product-features)**  
   Built-in `general` subagent defaulted to **25 steps**. Last finish reason is `length`. OpenCode then `exiting loop`. Forced final answer was 3 tokens. Parent Task treated that as success with empty body.

2. **Empty final `stop` (v2 both)**  
   After tool-calls the model ended with `reason=stop` and **0 output tokens**. Mid-work chatter existed (“Next I’ll inspect…”). No write, no error. Parent Task returned `""`.

Concurrent fan-out made (2) more likely (Bedrock mantle stream ending without a final frame) but the **orchestration bug** is: empty Task output is not classified as failure and is not retried.

## Fix (OpenCode, not Parlor)

- `~/.config/opencode/opencode.jsonc`: `general.steps=80`, `explore.steps=40`, Bedrock `timeout`/`chunkTimeout`, `logLevel=INFO`
- `~/.config/opencode/plugins/task-resilience.ts`: empty/rate-limit/timeout/EOF → log + bounded retry + `TASK_FAILED` if exhausted
- Tests: `~/.config/opencode/plugins/task-resilience.test.ts`

Restart OpenCode to load the plugin.
