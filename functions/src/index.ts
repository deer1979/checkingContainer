import { createHash } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  buildIdentityClaims,
  normalizeNick,
  normalizePin,
  stableFirebaseUid,
  verifyStoredPin,
} from "./credentials";

initializeApp();

const REGION = "us-central1";
const MAX_FAILURES = 5;
const ATTEMPT_WINDOW_MS = 15 * 60 * 1000;
const ATTEMPT_TTL_MS = 24 * 60 * 60 * 1000;

/**
 * Intercambia las credenciales internas por un custom token de Firebase.
 *
 * Requiere App Check válido y una sesión Firebase previa. Durante la migración
 * esa sesión puede ser anónima; el token emitido la eleva a un UID estable con
 * claims mínimos de autorización.
 */
export const exchangePinForToken = onCall(
  {
    region: REGION,
    enforceAppCheck: true,
    timeoutSeconds: 15,
    memory: "256MiB",
    maxInstances: 10,
  },
  async (request) => {
    if (request.auth === undefined) {
      throw new HttpsError("unauthenticated", "Sesión Firebase requerida");
    }

    const input = asRecord(request.data);
    const nick = normalizeNick(input?.nick);
    const pin = normalizePin(input?.pin);
    if (nick === null || pin === null) {
      throw invalidCredentials();
    }

    const firestore = getFirestore();
    const attemptRef = firestore
      .collection("_auth_attempts")
      .doc(attemptKey(request.auth.uid, nick));

    await assertAttemptAllowed(attemptRef);

    try {
      const snapshot = await firestore
        .collection("users")
        .where("nick", "==", nick)
        .limit(2)
        .get();

      if (snapshot.size !== 1) {
        await recordFailedAttempt(attemptRef);
        throw invalidCredentials();
      }

      const userDocument = snapshot.docs[0];
      if (userDocument === undefined) {
        await recordFailedAttempt(attemptRef);
        throw invalidCredentials();
      }

      const user = userDocument.data();
      if (user.isActive !== true || !verifyStoredPin(pin, user.pin)) {
        await recordFailedAttempt(attemptRef);
        throw invalidCredentials();
      }

      const uid = stableFirebaseUid(userDocument.id);
      const claims = buildIdentityClaims(userDocument.id, user);
      const customToken = await getAuth().createCustomToken(uid, claims);

      // La limpieza no debe convertir un login correcto en error.
      await attemptRef.delete().catch(() => undefined);

      return {
        customToken,
        userId: userDocument.id,
        role: claims.role,
      };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logger.error("Falló el intercambio de identidad", error);
      throw new HttpsError("internal", "No se pudo iniciar la sesión segura");
    }
  },
);

type AttemptReference = FirebaseFirestore.DocumentReference;

async function assertAttemptAllowed(reference: AttemptReference): Promise<void> {
  const snapshot = await reference.get();
  if (!snapshot.exists) return;

  const data = snapshot.data();
  const windowStartedAt = numericValue(data?.windowStartedAt);
  const failures = numericValue(data?.failures);
  const withinWindow = Date.now() - windowStartedAt < ATTEMPT_WINDOW_MS;

  if (withinWindow && failures >= MAX_FAILURES) {
    throw new HttpsError(
      "resource-exhausted",
      "Demasiados intentos. Intenta nuevamente más tarde.",
    );
  }
}

async function recordFailedAttempt(reference: AttemptReference): Promise<void> {
  const now = Date.now();
  await getFirestore().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    const data = snapshot.data();
    const previousStart = numericValue(data?.windowStartedAt);
    const previousFailures = numericValue(data?.failures);
    const sameWindow = snapshot.exists && now - previousStart < ATTEMPT_WINDOW_MS;

    transaction.set(
      reference,
      {
        windowStartedAt: sameWindow ? previousStart : now,
        failures: sameWindow ? previousFailures + 1 : 1,
        updatedAt: now,
        expiresAt: Timestamp.fromMillis(now + ATTEMPT_TTL_MS),
      },
      { merge: true },
    );
  });
}

function attemptKey(firebaseUid: string, nick: string): string {
  return createHash("sha256")
    .update(`${firebaseUid}:${nick}`, "utf8")
    .digest("hex");
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function numericValue(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function invalidCredentials(): HttpsError {
  return new HttpsError("unauthenticated", "Credenciales inválidas");
}
