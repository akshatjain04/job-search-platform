.PHONY: preflight test build demo start stop smoke
preflight:
	node scripts/platform.mjs preflight
test:
	node scripts/platform.mjs test --demo
build:
	node scripts/platform.mjs build
demo:
	node scripts/platform.mjs bootstrap --demo
start:
	node scripts/platform.mjs start
stop:
	node scripts/platform.mjs stop
smoke:
	node scripts/platform.mjs smoke
