const http = require("http");
const { Server } = require("socket.io");

const PORT = process.env.SOCKET_PORT || 3001;

const server = http.createServer((req, res) => {
  if (req.url === "/health" && req.method === "GET") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", service: "ech-realtime" }));
    return;
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ message: "Not Found" }));
});
const io = new Server(server, {
  cors: { origin: "*" },
});

io.on("connection", (socket) => {
  socket.on("channel:join", (channelId) => {
    socket.join(channelId);
  });

  socket.on("message:send", ({ channelId, user, text }) => {
    io.to(channelId).emit("message:new", {
      channelId,
      user,
      text,
      createdAt: new Date().toISOString(),
    });
  });
});

server.listen(PORT, () => {
  console.log(`ECH realtime server running on :${PORT}`);
});
