import assert from 'node:assert/strict';
import test from 'node:test';
import { createSignature, validateDepositEvent, verifySignature } from '../lib.mjs';

const validEvent = {
  eventId: 'event-1',
  deviceId: 'device-1',
  source: 'android_notification',
  bank: 'KB',
  receivedAt: '2026-08-18T00:00:00.000Z',
  direction: 'DEPOSIT',
  parseStatus: 'PARSED',
  amount: 30000,
  isAuthoritative: false,
};

test('valid deposit event passes validation', () => {
  assert.deepEqual(validateDepositEvent(validEvent), []);
});

test('notification event cannot be marked authoritative', () => {
  const errors = validateDepositEvent({ ...validEvent, isAuthoritative: true });
  assert.ok(errors.includes('isAuthoritative must be false for notification-derived data'));
});

test('HANA deposit event passes validation', () => {
  assert.deepEqual(validateDepositEvent({ ...validEvent, bank: 'HANA' }), []);
});

test('NH deposit event passes validation', () => {
  assert.deepEqual(validateDepositEvent({ ...validEvent, bank: 'NH' }), []);
});

test('unsupported bank is rejected', () => {
  const errors = validateDepositEvent({ ...validEvent, bank: 'UNKNOWN' });
  assert.ok(errors.includes('bank must be KB, HANA, or NH for this PoC'));
});

test('signature is accepted only for the matching body', () => {
  const secret = 'poc-secret';
  const timestamp = '1000';
  const body = JSON.stringify(validEvent);
  const signature = createSignature(secret, timestamp, body);
  assert.deepEqual(verifySignature(secret, timestamp, body, signature, 1000), { ok: true });
  assert.equal(verifySignature(secret, timestamp, `${body}x`, signature, 1000).ok, false);
});

test('stale signature timestamp is rejected', () => {
  const secret = 'poc-secret';
  const body = JSON.stringify(validEvent);
  const signature = createSignature(secret, '1000', body);
  assert.equal(verifySignature(secret, '1000', body, signature, 1401).ok, false);
});
