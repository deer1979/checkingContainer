import assert from "node:assert/strict";
import {
  createHash,
  pbkdf2Sync,
  randomBytes,
} from "node:crypto";
import { test } from "node:test";
import {
  buildIdentityClaims,
  normalizeNick,
  normalizePin,
  stableFirebaseUid,
  verifyStoredPin,
} from "./credentials";

const PIN = "123456";

test("normaliza nick y valida PIN de seis dígitos", () => {
  assert.equal(normalizeNick("  SAdmin "), "sadmin");
  assert.equal(normalizeNick("usuario con espacio"), null);
  assert.equal(normalizePin(PIN), PIN);
  assert.equal(normalizePin("12345"), null);
});

test("verifica PBKDF2 v2 compatible con Android", () => {
  const salt = randomBytes(16);
  const iterations = 600_000;
  const hash = pbkdf2Sync(PIN, salt, iterations, 32, "sha256");
  const stored = `v2:${iterations}:${salt.toString("base64")}:${hash.toString("base64")}`;

  assert.equal(verifyStoredPin(PIN, stored), true);
  assert.equal(verifyStoredPin("654321", stored), false);
});

test("verifica SHA-256 v1 y texto plano legado", () => {
  const salt = randomBytes(16);
  const hash = createHash("sha256")
    .update(salt)
    .update(Buffer.from(PIN, "utf8"))
    .digest();
  const stored = `v1:${salt.toString("base64")}:${hash.toString("base64")}`;

  assert.equal(verifyStoredPin(PIN, stored), true);
  assert.equal(verifyStoredPin(PIN, PIN), true);
  assert.equal(verifyStoredPin("654321", PIN), false);
});

test("rechaza hashes corruptos e iteraciones abusivas", () => {
  assert.equal(verifyStoredPin(PIN, "v2:600000:no-base64:no-base64"), false);
  assert.equal(verifyStoredPin(PIN, "v2:2000001:YWJjZA==:YWJjZA=="), false);
  assert.equal(verifyStoredPin(PIN, "v1:::"), false);
});

test("genera UID estable y claims mínimos", () => {
  assert.equal(stableFirebaseUid("42"), "cc-user-42");
  assert.throws(() => stableFirebaseUid("usuario-42"));

  assert.deepEqual(
    buildIdentityClaims("42", {
      role: "Tecnico",
      company: "CheckingContainer",
      location: "PBO",
    }),
    {
      appUserId: "42",
      role: "Tecnico",
      company: "CheckingContainer",
      location: "PBO",
      active: true,
    },
  );
});
