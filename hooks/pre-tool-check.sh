#!/usr/bin/env bash
# Claude Code pre-tool hook: blocks dangerous Bash commands.
# Receives tool input as first argument.
INPUT="${1:-}"

# Block git push --force to main
if echo "$INPUT" | grep -qE "git push.*(--force|-f).*main"; then
  echo "BLOCKED: Force-push to main is not allowed. Create a PR instead." >&2
  exit 1
fi

# Block rm -rf on repository paths
if echo "$INPUT" | grep -qE "rm -rf (src|tests|\.claude|hooks|skills)/"; then
  echo "BLOCKED: Destructive removal of repository directories requires explicit human approval." >&2
  exit 1
fi

# Block git reset --hard
if echo "$INPUT" | grep -qE "git reset --hard"; then
  echo "BLOCKED: Hard reset requires explicit human approval." >&2
  exit 1
fi

exit 0
