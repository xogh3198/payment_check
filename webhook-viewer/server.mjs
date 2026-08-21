import http from 'node:http';
import { appendFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateDepositEvent, verifySignature } from './lib.mjs';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const dataDirectory = path.join(currentDirectory, 'data');
const eventFile = path.join(dataDirectory, 'events.jsonl');
const publicDirectory = path.join(currentDirectory, 'public');
const port = Number(process.env.PORT || 8787);
const host = process.env.HOST || '0.0.0.0';
const enrollmentToken = process.env.ENROLLMENT_TOKEN || 'local-enrollment-token';
const agentId = process.env.AGENT_ID || 'agent_local_viewer';
const agentSecret = process.env.AGENT_SECRET || 'local-agent-secret-change-before-sharing';
const maxBodyBytes = 64 * 1024;

await mkdir(dataDirectory, { recursive: true });
let events = await loadEvents();

const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`);

    if (request.method === 'GET' && url.pathname === '/') {
      return sendFile(response, path.join(publicDirectory, 'index.html'), 'text/html; charset=utf-8');
    }
    if (request.method === 'GET' && url.pathname === '/app.js') {
      return sendFile(response, path.join(publicDirectory, 'app.js'), 'text/javascript; charset=utf-8');
    }
    if (request.method === 'GET' && url.pathname === '/styles.css') {
      return sendFile(response, path.join(publicDirectory, 'styles.css'), 'text/css; charset=utf-8');
    }
    if (request.method === 'GET' && url.pathname === '/health') {
      return sendJson(response, 200, {
        status: 'ok',
        events: events.length,
        signatureRequired: true,
        enrollmentToken,
      });
    }
    if (request.method === 'GET' && url.pathname === '/api/events') {
      return sendJson(response, 200, { events });
    }
    if (request.method === 'DELETE' && url.pathname === '/api/events') {
      events = [];
      await writeFile(eventFile, '', 'utf8');
      return sendJson(response, 200, { cleared: true });
    }
    if (request.method === 'POST' && url.pathname === '/api/v1/payment-agents/enroll') {
      const rawBody = await readBody(request, maxBodyBytes);
      let input;
      try {
        input = JSON.parse(rawBody);
      } catch {
        return sendJson(response, 400, { message: 'Request body is not valid JSON' });
      }
      if (input.enrollmentToken !== enrollmentToken || !input.installationId) {
        return sendJson(response, 401, { message: 'Invalid local enrollment token' });
      }
      return sendJson(response, 200, {
        agentId,
        agentSecret,
        eventPath: '/api/v1/payment-agents/events',
        heartbeatPath: '/api/v1/payment-agents/heartbeat',
      });
    }
    if (
      request.method === 'POST' &&
      ['/api/v1/payment-agents/events', '/api/v1/payment-agents/heartbeat'].includes(url.pathname)
    ) {
      const rawBody = await readBody(request, maxBodyBytes);
      if (request.headers['x-deposit-agent-id'] !== agentId) {
        return sendJson(response, 401, { message: 'Unknown local agent' });
      }
      const signatureResult = verifySignature(
        agentSecret,
        request.headers['x-deposit-agent-timestamp'],
        request.headers['x-deposit-agent-nonce'],
        rawBody,
        request.headers['x-deposit-agent-signature'],
      );
      if (!signatureResult.ok) {
        return sendJson(response, 401, { message: signatureResult.reason });
      }

      let event;
      try {
        event = JSON.parse(rawBody);
      } catch {
        return sendJson(response, 400, { message: 'Request body is not valid JSON' });
      }

      if (url.pathname.endsWith('/heartbeat')) {
        return sendJson(response, 200, { ok: true, serverTime: new Date().toISOString() });
      }

      const validationErrors = validateDepositEvent(event);
      if (validationErrors.length > 0) {
        return sendJson(response, 422, { message: 'Invalid deposit event', errors: validationErrors });
      }

      if (events.some((stored) => stored.eventId === event.eventId)) {
        return sendJson(response, 200, { accepted: true, duplicate: true, eventId: event.eventId });
      }

      const storedEvent = {
        ...event,
        serverReceivedAt: new Date().toISOString(),
      };
      events = [storedEvent, ...events].slice(0, 500);
      await appendFile(eventFile, `${JSON.stringify(storedEvent)}\n`, 'utf8');
      console.log(
        `[payment] ${storedEvent.provider} ${storedEvent.eventId} ${storedEvent.payerName || '-'} ${storedEvent.amount || '-'} KRW`,
      );
      return sendJson(response, 202, { accepted: true, duplicate: false, eventId: event.eventId });
    }

    return sendJson(response, 404, { message: 'Not found' });
  } catch (error) {
    const status = error.code === 'BODY_TOO_LARGE' ? 413 : 500;
    return sendJson(response, status, { message: error.message || 'Internal server error' });
  }
});

server.listen(port, host, () => {
  console.log(`TradLab Deposit Webhook Viewer: http://localhost:${port}`);
  console.log(`Agent server URL: http://<this-computer-ip>:${port}`);
  console.log(`Local enrollment token: ${enrollmentToken}`);
  console.log('Signature verification: enabled');
});

async function loadEvents() {
  try {
    const raw = await readFile(eventFile, 'utf8');
    return raw.split('\n')
      .filter(Boolean)
      .map((line) => JSON.parse(line))
      .reverse()
      .slice(0, 500);
  } catch (error) {
    if (error.code === 'ENOENT') return [];
    throw error;
  }
}

async function readBody(request, limit) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > limit) {
      const error = new Error('Request body is too large');
      error.code = 'BODY_TOO_LARGE';
      throw error;
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

async function sendFile(response, filePath, contentType) {
  const body = await readFile(filePath);
  response.writeHead(200, {
    'Content-Type': contentType,
    'Cache-Control': 'no-store',
  });
  response.end(body);
}

function sendJson(response, status, payload) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  response.end(JSON.stringify(payload));
}
