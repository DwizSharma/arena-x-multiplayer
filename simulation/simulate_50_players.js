import { Client } from '@stomp/stompjs';
import WebSocket from 'ws';

const ROOM = process.env.ROOM_CODE || 'APT892';
const API = process.env.API_URL || 'http://localhost:8080';
const WS = process.env.WS_URL || 'ws://localhost:8080/ws-arena';
const COUNT = 50;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const random = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const summary = { accepted: 0, rejected: 0, latencies: [], clients: [] };

async function createIdentity(index) {
  const response = await fetch(`${API}/api/session`, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ name: `Student_${index}`, college: 'ArenaX Simulation College' }) });
  if (!response.ok) throw new Error(`Identity creation failed (${response.status}): ${await response.text()}`);
  return response.json();
}

function connect(identity, index) {
  return new Promise((resolve, reject) => {
    const url = new URL(WS); url.searchParams.set('playerId', identity.playerId); url.searchParams.set('token', identity.token);
    const client = new Client({ brokerURL: url.toString(), webSocketFactory: () => new WebSocket(url), reconnectDelay: 0,
      heartbeatIncoming: 10000, heartbeatOutgoing: 10000, connectionTimeout: 12000,
      onConnect: () => resolve(client), onStompError: frame => reject(new Error(frame.headers.message || 'STOMP connection rejected')),
      onWebSocketError: () => reject(new Error('WebSocket connection failed')) });
    client.activate();
  });
}

async function main() {
  console.log(`Creating ${COUNT} sessions and connecting to room ${ROOM} at ${WS}`);
  const identities = await Promise.all(Array.from({ length: COUNT }, (_, i) => createIdentity(i + 1)));
  const clients = await Promise.all(identities.map((identity, i) => connect(identity, i + 1)));
  let joined = 0;
  const ready = new Promise(resolve => {
    clients.forEach((client, i) => {
      const identity = identities[i];
      client.subscribe('/user/queue/joined', () => { joined++; if (joined === COUNT) resolve(); });
      client.subscribe('/user/queue/answer-result', message => {
        const received = JSON.parse(message.body);
        if (received.status === 'ACCEPTED') { summary.accepted++; summary.latencies.push(received.serverElapsedMs); }
        else summary.rejected++;
        summary.clients[i].lastStatus = received.status;
      });
      client.subscribe('/user/queue/question', message => {
        const question = JSON.parse(message.body);
        if (summary.clients[i]?.answeredQuestion === question.promptId) return;
        summary.clients[i].answeredQuestion = question.promptId;
        const option = question.options[random(0, question.options.length - 1)];
        const delay = random(1200, 7500);
        setTimeout(() => {
          summary.clients[i].sentAt = Date.now();
          client.publish({ destination: `/app/room/${ROOM}/answer`, body: JSON.stringify({ questionId: question.promptId, optionId: option.id }) });
        }, delay);
      });
      summary.clients[i] = { name: `Student_${i + 1}`, sentAt: null, answeredQuestion: null, lastStatus: 'PENDING' };
      client.publish({ destination: `/app/room/${ROOM}/join`, body: '{}' });
    });
  });
  await Promise.race([ready, sleep(15000).then(() => { throw new Error(`Only ${joined}/${COUNT} clients joined within 15 seconds`); })]);
  console.log(`All ${COUNT} players joined. Starting match…`);
  clients[0].publish({ destination: `/app/room/${ROOM}/start`, body: '{}' });
  await sleep(105000);
  clients.forEach(client => void client.deactivate());
  const latency = summary.latencies;
  const avg = latency.length ? Math.round(latency.reduce((a, b) => a + b, 0) / latency.length) : 0;
  const sorted = [...latency].sort((a, b) => a - b);
  const p95 = sorted.length ? sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * .95) - 1)] : 0;
  console.log('\nArenaX 50-player simulation summary');
  console.table([{ room: ROOM, clients: COUNT, accepted: summary.accepted, rejected: summary.rejected,
    'avg server answer time (ms)': avg, 'p95 server answer time (ms)': p95 }]);
  if (!summary.accepted) process.exitCode = 1;
}

main().catch(error => { console.error(error); process.exitCode = 1; });
