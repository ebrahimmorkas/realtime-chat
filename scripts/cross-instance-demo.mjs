// Proves horizontal scaling against the docker-compose stack:
// Alice connects to instance chat-1 (port 8081), Bob to chat-2 (port 8082),
// and a message sent by Alice must reach Bob via Redis fan-out.
// Usage: docker compose up -d --build && node scripts/cross-instance-demo.mjs   (Node 22+)
// Through the load balancer: A=http://localhost:8080 B=http://localhost:8080 node scripts/cross-instance-demo.mjs

const A = process.env.A || 'http://localhost:8081';
const B = process.env.B || 'http://localhost:8082';
const run = Date.now();

async function api(base, path, body, token) {
  const res = await fetch(base + path, {
    method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${path} -> ${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json();
}

async function user(name) {
  const email = `${name}-${run}@example.com`;
  await api(A, '/api/auth/register', { email, password: 'Secret123', displayName: name });
  return { email, token: (await api(A, '/api/auth/login', { email, password: 'Secret123' })).accessToken };
}

// Minimal STOMP 1.2 client over the built-in WebSocket
function stomp(base, token) {
  const ws = new WebSocket(base.replace('http', 'ws') + '/ws');
  const handlers = new Map();
  let buffer = '';
  const frame = (command, headers = {}, body = '') =>
    ws.send(`${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}\0`);
  const connected = new Promise((resolve, reject) => {
    ws.onopen = () => frame('CONNECT', { 'accept-version': '1.2', host: 'localhost', Authorization: `Bearer ${token}` });
    ws.onmessage = (event) => {
      buffer += event.data;
      let end;
      while ((end = buffer.indexOf('\0')) >= 0) {
        const raw = buffer.slice(0, end).replace(/^\n+/, '');
        buffer = buffer.slice(end + 1);
        const [head, ...rest] = raw.split('\n\n');
        const [command, ...headerLines] = head.split('\n');
        const headers = Object.fromEntries(headerLines.map((l) => [l.slice(0, l.indexOf(':')), l.slice(l.indexOf(':') + 1)]));
        if (command === 'CONNECTED') resolve();
        if (command === 'ERROR') reject(new Error(headers.message));
        if (command === 'MESSAGE') handlers.get(headers.subscription)?.(JSON.parse(rest.join('\n\n')));
      }
    };
  });
  return {
    connected,
    subscribe(destination, handler) {
      const id = `sub-${handlers.size}`;
      handlers.set(id, handler);
      frame('SUBSCRIBE', { id, destination });
    },
    send(destination, payload) {
      frame('SEND', { destination, 'content-type': 'application/json' }, JSON.stringify(payload));
    },
    close: () => ws.close(),
  };
}

const alice = await user('Alice');
const bob = await user('Bob');
const room = await api(A, '/api/rooms', { name: 'Scaling demo', memberEmails: [bob.email] }, alice.token);

const aliceWs = stomp(A, alice.token);
const bobWs = stomp(B, bob.token);
await Promise.all([aliceWs.connected, bobWs.connected]);

const received = new Promise((resolve, reject) => {
  bobWs.subscribe(`/topic/rooms/${room.id}`, resolve);
  setTimeout(() => reject(new Error('Bob did not receive the message within 5s')), 5000);
});
await new Promise((r) => setTimeout(r, 500));
aliceWs.send(`/app/rooms/${room.id}/send`, { content: 'Hello from instance 1!' });

const message = await received;
console.log(`Alice via ${A} sent, Bob via ${B} received: "${message.content}" from ${message.senderName}`);
const presence = await api(B, `/api/rooms/${room.id}/presence`, null, bob.token);
console.log(`Presence seen via ${B}: ${presence.online.length} of 2 members online`);
aliceWs.close();
bobWs.close();
