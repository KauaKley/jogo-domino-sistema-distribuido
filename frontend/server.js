import express from "express";
import grpc from "@grpc/grpc-js";
import protoLoader from "@grpc/proto-loader";
import path from "node:path";
import { networkInterfaces } from "node:os";
import { fileURLToPath } from "node:url";
const dir = path.dirname(fileURLToPath(import.meta.url));
const proto = protoLoader.loadSync(
  path.join(dir, "../backend/src/main/proto/domino.proto"),
  { keepCase: false, longs: String, defaults: true },
);
const Jogo = grpc.loadPackageDefinition(proto).domino.Jogo;
const enderecos = (
  process.env.SERVIDORES || "localhost:50051,localhost:50052"
).split(",");
const clientes = enderecos.map(
  (e) => new Jogo(e, grpc.credentials.createInsecure()),
);
const app = express();
app.use(express.json());
app.use(express.static(path.join(dir, "public")));
let preferido = 0;
function chamada(cliente, p) {
  return new Promise((resolve, reject) =>
    cliente.Executar(p, { deadline: Date.now() + 2000 }, (e, r) =>
      e ? reject(e) : resolve(r),
    ),
  );
}
app.post("/api", async (req, res) => {
  const p = req.body || {};
  for (let t = 0; t < clientes.length; t++) {
    const i = (preferido + t) % clientes.length;
    try {
      const r = await chamada(clientes[i], p);
      if (r.mensagem === "SERVIDOR_RESERVA") continue;
      preferido = i;
      return res.json({
        ok: r.sucesso,
        mensagem: r.mensagem,
        estado: r.dadosJson ? JSON.parse(r.dadosJson) : null,
        versao: r.versao,
      });
    } catch (e) {
      console.error("Servidor", enderecos[i], e.message);
    }
  }
  res.status(503).json({
    ok: false,
    mensagem: "Servidores indisponíveis. Tentando reconectar...",
  });
});
const porta = Number(process.env.PORT || 3000);
app.listen(porta, "0.0.0.0", () => {
  console.log(`Neste PC: http://localhost:${porta}`);
  for (const [rede, enderecos] of Object.entries(networkInterfaces())) {
    for (const { address, family, internal } of enderecos) {
      if (family === "IPv4" && !internal) {
        console.log(`Rede ${rede}: http://${address}:${porta}`);
      }
    }
  }
});
