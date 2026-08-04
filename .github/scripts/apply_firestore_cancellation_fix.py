from pathlib import Path

path = Path("core/data/src/main/kotlin/com/checkingcontainer/core/data/FirestoreService.kt")
lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
out: list[str] = []
inserted = 0

for line in lines:
    if line.strip() == "} catch (e: Exception) {":
        previous = next((item.strip() for item in reversed(out) if item.strip()), "")
        if previous != "throw e":
            indent = line[: len(line) - len(line.lstrip())]
            out.append(f"{indent}}} catch (e: CancellationException) {{\n")
            out.append(f"{indent}    throw e\n")
            inserted += 1
    out.append(line)

if inserted == 0:
    print("FirestoreService ya conserva las cancelaciones")
else:
    path.write_text("".join(out), encoding="utf-8")
    print(f"Bloques corregidos: {inserted}")
