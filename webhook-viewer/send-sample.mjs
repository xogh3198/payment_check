import { randomUUID } from 'node:crypto';
import { createSignature } from './lib.mjs';

const url = process.env.WEBHOOK_URL || 'http://localhost:8787/webhook/deposits';
const secret = process.env.WEBHOOK_SECRET || '';
const requestedBank = String(process.env.SAMPLE_BANK || 'KB').toUpperCase();
const bank = ['KB', 'HANA', 'NH'].includes(requestedBank) ? requestedBank : 'KB';
const timestamp = Math.floor(Date.now() / 1000).toString();
const bankSample = {
  HANA: {
      notificationTitle: '하나원큐 입출금 알림',
      rawText: '[하나은행] 08/18 14:31\n123-9100-****\n입금 30,000원\n이태호4821\n잔액 120,000원',
      accountMasked: '123-9100-****',
  },
  NH: {
      notificationTitle: 'NH스마트뱅킹 입출금 알림',
      rawText: '[NH농협] 08/18 14:32\n302-****-1234-**\n30,000원 입금\n이태호4821\n잔액 120,000원',
      accountMasked: '302-****-1234-**',
  },
  KB: {
      notificationTitle: 'KB 입금 알림 샘플',
      rawText: '[KB]8/18 14:30\n498125****8895\n이태호4821\n30,000 입금\n1644-9999',
      accountMasked: '498125****8895',
  },
}[bank];
const event = {
  eventId: randomUUID(),
  deviceId: 'node-sample-device',
  source: 'manual_sample',
  bank,
  packageName: 'poc.sample',
  notificationTitle: bankSample.notificationTitle,
  rawText: bankSample.rawText,
  postedAt: Date.now(),
  receivedAt: new Date().toISOString(),
  transactionAt: new Date().toISOString(),
  accountMasked: bankSample.accountMasked,
  depositorName: '이태호4821',
  amount: 30000,
  currency: 'KRW',
  direction: 'DEPOSIT',
  parseStatus: 'PARSED',
  isAuthoritative: false,
};
const rawBody = JSON.stringify(event);
const headers = {
  'Content-Type': 'application/json; charset=utf-8',
  'X-Deposit-Agent-Id': event.deviceId,
  'X-Deposit-Agent-Timestamp': timestamp,
};
if (secret) {
  headers['X-Deposit-Agent-Signature'] = createSignature(secret, timestamp, rawBody);
}

const response = await fetch(url, { method: 'POST', headers, body: rawBody });
console.log(`${bank} ${response.status} ${await response.text()}`);
if (!response.ok) process.exitCode = 1;
