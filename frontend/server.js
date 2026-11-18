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
  (enderecoServidor) => new Jogo(enderecoServidor, grpc.credentials.createInsecure()),
);
const app = express();
app.use(express.json());
app.use(express.static(path.join(dir, "public")));
let preferido = 0;
function chamada(cliente, pedido) {
  return new Promise((resolve, reject) =>
    cliente.Executar(pedido, { deadline: Date.now() + 2000 }, (erro, respostaGrpc) =>
      erro ? reject(erro) : resolve(respostaGrpc),
    ),
  );
}
app.post("/api", async (req, res) => {
  const pedido = req.body || {};
  for (let tentativa = 0; tentativa < clientes.length; tentativa++) {
    const indiceServidor = (preferido + tentativa) % clientes.length;
    try {
      const respostaGrpc = await chamada(clientes[indiceServidor], pedido);
      if (respostaGrpc.mensagem === "SERVIDOR_RESERVA") continue;
      preferido = indiceServidor;
      return res.json({
        ok: respostaGrpc.sucesso,
        mensagem: respostaGrpc.mensagem,
        estado: respostaGrpc.dadosJson ? JSON.parse(respostaGrpc.dadosJson) : null,
        versao: respostaGrpc.versao,
      });
    } catch (erro) {
      console.error("Servidor", enderecos[indiceServidor], erro.message);
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
