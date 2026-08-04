import {
  createHash,
  pbkdf2Sync,
  timingSafeEqual,
} from "node:crypto";

const CURRENT_PREFIX = "v2";
const LEGACY_HASH_PREFIX = "v1";
const MAX_ACCEPTED_ITERATIONS = 2_000_000;
const KEY_LENGTH_BYTES = 32;
const BASE64_PATTERN = /^[A-Za-z0-9+/]+={0,2}$/;

export interface IdentityClaims {
  appUserId: string;
  role: string;
  company: string;
  location: string;
  active: true;
}

export function normalizeNick(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim().toLowerCase();
  return /^[a-z0-9]{2,40}$/.test(normalized) ? normalized : null;
}

export function normalizePin(value: unknown): string | null {
  if (typeof value !== "string") return null;
  return /^\d{6}$/.test(value) ? value : null;
}

export function stableFirebaseUid(documentId: string): string {
  const id = documentId.trim();
  if (!/^\d{1,20}$/.test(id)) {
    throw new Error("Identificador de usuario inválido");
  }
  return `cc-user-${id}`;
}

export function buildIdentityClaims(
  documentId: string,
  data: Record<string, unknown>,
): IdentityClaims {
  return {
    appUserId: documentId,
    role: claimText(data.role, "Viewer"),
    company: claimText(data.company, ""),
    location: claimText(data.location, ""),
    active: true,
  };
}

export function verifyStoredPin(pin: string, stored: unknown): boolean {
  if (!/^\d{6}$/.test(pin) || typeof stored !== "string") return false;

  if (stored.startsWith(`${CURRENT_PREFIX}:`)) {
    return verifyV2(pin, stored);
  }
  if (stored.startsWith(`${LEGACY_HASH_PREFIX}:`)) {
    return verifyV1(pin, stored);
  }

  return constantTimeTextEquals(pin, stored);
}

function verifyV2(pin: string, stored: string): boolean {
  const parts = stored.split(":");
  if (parts.length !== 4) return false;

  const iterations = Number(parts[1]);
  if (!Number.isInteger(iterations) || iterations < 1 || iterations > MAX_ACCEPTED_ITERATIONS) {
    return false;
  }

  const salt = decodeBase64(parts[2]);
  const expected = decodeBase64(parts[3]);
  if (salt === null || expected === null || expected.length !== KEY_LENGTH_BYTES) {
    return false;
  }

  const actual = pbkdf2Sync(pin, salt, iterations, KEY_LENGTH_BYTES, "sha256");
  return constantTimeBufferEquals(actual, expected);
}

function verifyV1(pin: string, stored: string): boolean {
  const parts = stored.split(":");
  if (parts.length !== 3) return false;

  const salt = decodeBase64(parts[1]);
  const expected = decodeBase64(parts[2]);
  if (salt === null || expected === null || expected.length !== KEY_LENGTH_BYTES) {
    return false;
  }

  const actual = createHash("sha256")
    .update(salt)
    .update(Buffer.from(pin, "utf8"))
    .digest();
  return constantTimeBufferEquals(actual, expected);
}

function decodeBase64(value: string | undefined): Buffer | null {
  if (
    value === undefined ||
    value.length === 0 ||
    value.length % 4 !== 0 ||
    !BASE64_PATTERN.test(value)
  ) {
    return null;
  }

  const decoded = Buffer.from(value, "base64");
  return decoded.length > 0 ? decoded : null;
}

function constantTimeTextEquals(left: string, right: string): boolean {
  return constantTimeBufferEquals(
    Buffer.from(left, "utf8"),
    Buffer.from(right, "utf8"),
  );
}

function constantTimeBufferEquals(left: Buffer, right: Buffer): boolean {
  return left.length === right.length && timingSafeEqual(left, right);
}

function claimText(value: unknown, fallback: string): string {
  if (typeof value !== "string") return fallback;
  return value.trim().slice(0, 120);
}
