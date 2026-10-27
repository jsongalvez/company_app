// A TCP link that behaves like a slow phone hotspot: every byte waits half the round trip each way and
// is paced to the link rate, like netem's delay + rate. Unlike Chromium's network emulation it also slows
// connection set-up, TLS handshakes and the service worker's own fetches.
//   startShaper({ target: "host:port", rtt: 300, down: 800, up: 300 }) -> { port, close() }
import net from "node:net";

function paced(rttMs, kbit) {
    const bytesPerMs = (kbit * 1000) / 8 / 1000;
    let free = 0;
    return (len) => {
        const now = Date.now();
        free = Math.max(free, now) + len / bytesPerMs;
        return free + rttMs / 2 - now;
    };
}

export function startShaper({ target, rtt = 300, down = 800, up = 300, listen = 0 }) {
    const [host, port] = target.split(":");
    const server = net.createServer((client) => {
        const remote = net.connect(Number(port), host);
        const toServer = paced(rtt, up);
        const toClient = paced(rtt, down);
        const pipe = (from, to, wait) => {
            from.on("data", (chunk) => {
                setTimeout(() => to.writable && to.write(chunk), wait(chunk.length));
            });
            from.on("end", () => setTimeout(() => to.end(), wait(0) + 1));
            from.on("error", () => to.destroy());
        };
        // The SYN/ACK round trip of a real connection.
        client.pause();
        remote.once("connect", () => setTimeout(() => client.resume(), rtt));
        pipe(client, remote, toServer);
        pipe(remote, client, toClient);
        client.on("close", () => remote.destroy());
        remote.on("close", () => client.destroy());
    });
    return new Promise((resolve) =>
        server.listen(listen, "127.0.0.1", () =>
            resolve({ port: server.address().port, close: () => new Promise((r) => server.close(r)) }),
        ),
    );
}
