const buscarElementoPorId = (id) => document.getElementById(id);
function novoId() {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
}
const idSalvo = sessionStorage.getItem("domino-id");
let id = idSalvo || novoId();
const aba = novoId();
const canal = new BroadcastChannel("domino-abas");
let verificando = Boolean(idSalvo);
let duplicada = false;
canal.onmessage = ({ data }) => {
  if (data.id !== id || data.aba === aba) return;
  if (data.tipo === "consulta" && (!verificando || aba < data.aba)) {
    canal.postMessage({ tipo: "ocupado", id, aba, destino: data.aba });
  }
  if (data.tipo === "ocupado" && data.destino === aba) duplicada = true;
};
if (verificando) {
  canal.postMessage({ tipo: "consulta", id, aba });
  await new Promise((resolve) => setTimeout(resolve, 200));
  if (duplicada) {
    id = novoId();
    sessionStorage.removeItem("domino-sala");
    sessionStorage.removeItem("domino-nome");
  }
}
verificando = false;
sessionStorage.setItem("domino-id", id);
let sala = sessionStorage.getItem("domino-sala") || "",
  nome = sessionStorage.getItem("domino-nome") || "",
  estado = null,
  selecionada = null,
  ocupado = false;
buscarElementoPorId("nome").value = nome;
buscarElementoPorId("codigo").value = new URLSearchParams(location.search).get("sala") || sala;
function aviso(mensagem) {
  buscarElementoPorId("aviso").textContent = mensagem;
}
async function api(acao, extra = {}) {
  const respostaHttp = await fetch("/api", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ acao, sala, jogador: id, nome, ...extra }),
  });
  const dadosResposta = await respostaHttp.json();
  if (!dadosResposta.ok) throw Error(dadosResposta.mensagem);
  if (dadosResposta.estado) {
    estado = dadosResposta.estado;
    sala = estado.sala;
    sessionStorage.setItem("domino-sala", sala);
    sessionStorage.setItem("domino-nome", nome);
    desenhar();
  }
  return dadosResposta;
}
async function agir(acao, extra = {}) {
  if (ocupado) return;
  ocupado = true;
  try {
    const dadosResposta = await api(acao, extra);
    aviso(acao === "JOGAR" ? "" : dadosResposta.mensagem);
  } catch (erro) {
    aviso(erro.message);
  } finally {
    ocupado = false;
  }
}
buscarElementoPorId("criar").onclick = () => {
  nome = buscarElementoPorId("nome").value.trim();
  if (!nome) return aviso("Informe seu nome");
  agir("CRIAR");
};
buscarElementoPorId("entrar").onclick = () => {
  nome = buscarElementoPorId("nome").value.trim();
  sala = buscarElementoPorId("codigo").value.trim().toUpperCase();
  if (!nome || !sala) return aviso("Informe nome e sala");
  agir("ENTRAR");
};
buscarElementoPorId("pronto").onclick = () => agir("PRONTO");
buscarElementoPorId("sair").onclick = async () => {
  await agir("SAIR");
  sessionStorage.removeItem("domino-sala");
  sala = "";
  estado = null;
  buscarElementoPorId("partida").hidden = true;
  buscarElementoPorId("acesso").hidden = false;
};
buscarElementoPorId("copiar").onclick = async () => {
  try {
    await navigator.clipboard.writeText(sala);
    aviso("Código copiado");
  } catch {
    const selecao = window.getSelection();
    selecao.selectAllChildren(buscarElementoPorId("sala"));
    aviso(
      document.execCommand("copy")
        ? "Código copiado"
        : `Código da sala: ${sala}`,
    );
  }
};
buscarElementoPorId("compartilhar").onclick = async () => {
  const link = new URL(location.href);
  link.searchParams.set("sala", sala);
  try {
    await navigator.clipboard.writeText(link.href);
    aviso("Link da sala copiado");
  } catch {
    window.prompt("Copie o link da sala", link.href);
  }
};
const pontosPorFace = [
  [],
  [5],
  [1, 9],
  [1, 5, 9],
  [1, 3, 7, 9],
  [1, 3, 5, 7, 9],
  [1, 3, 4, 6, 7, 9],
];
function criarElementoPeca({ a: ladoEsquerdoPeca, b: ladoDireitoPeca }, classe = "", interativa = false) {
  const elemento = document.createElement(interativa ? "button" : "div");
  elemento.className = "peca " + classe;
  elemento.setAttribute("aria-label", `${ladoEsquerdoPeca} e ${ladoDireitoPeca}`);
  if (!interativa) elemento.setAttribute("role", "img");
  for (const valorMetade of [ladoEsquerdoPeca, ladoDireitoPeca]) {
    const face = document.createElement("span");
    face.className = "face";
    face.setAttribute("aria-hidden", "true");
    for (const posicao of pontosPorFace[valorMetade]) {
      const ponto = document.createElement("i");
      ponto.style.gridArea = `${Math.ceil(posicao / 3)} / ${((posicao - 1) % 3) + 1}`;
      face.append(ponto);
    }
    elemento.append(face);
  }
  return elemento;
}
function desenharMesa() {
  const mesa = buscarElementoPorId("mesa");
  if (!estado || !mesa.clientWidth) {
    mesa.replaceChildren();
    return;
  }
  const unidade = parseFloat(
    getComputedStyle(mesa).getPropertyValue("--unidade"),
  );
  const largura = Math.max(3, Math.floor(mesa.clientWidth / unidade));
  let colunaMesa = 0;
  let linhaMesa = 1;
  let direcao = 1;
  let descidas = 0;
  const pecas = estado.mesa.map((peca) => {
    const tamanho = peca.a === peca.b ? 1 : 2;
    const espaco = direcao > 0 ? largura - colunaMesa : colunaMesa + 1;
    const curva = descidas > 0 || espaco <= tamanho;
    const dupla = !curva && peca.a === peca.b;
    const elemento = criarElementoPeca(
      peca,
      curva || dupla ? "vertical" : direcao < 0 ? "invertida" : "",
    );
    elemento.style.gridColumn = `${direcao < 0 && !curva && !dupla ? colunaMesa : colunaMesa + 1} / span ${curva || dupla ? 1 : 2}`;
    elemento.style.gridRow = `${dupla ? linhaMesa : linhaMesa + 1} / span ${curva || dupla ? 2 : 1}`;
    if (curva) {
      linhaMesa += 2;
      if (++descidas === 2) {
        descidas = 0;
        direcao *= -1;
        colunaMesa += direcao;
        linhaMesa -= 1;
      }
    } else {
      colunaMesa += direcao * (dupla ? 1 : 2);
    }
    return elemento;
  });
  mesa.replaceChildren(...pecas);
}
let larguraMesa = 0;
new ResizeObserver(([entrada]) => {
  if (entrada.contentRect.width !== larguraMesa) {
    larguraMesa = entrada.contentRect.width;
    desenharMesa();
  }
}).observe(buscarElementoPorId("mesa"));
function desenhar() {
  buscarElementoPorId("acesso").hidden = true;
  buscarElementoPorId("partida").hidden = false;
  buscarElementoPorId("sala").textContent = sala;
  const salaCheia = estado.jogadores.length >= 4;
  buscarElementoPorId("copiar").hidden = salaCheia;
  buscarElementoPorId("compartilhar").hidden = salaCheia;
  buscarElementoPorId("fase").textContent = estado.fase;
  buscarElementoPorId("pa").textContent = estado.pontos[0];
  buscarElementoPorId("pb").textContent = estado.pontos[1];
  buscarElementoPorId("rodada").textContent = estado.rodada;
  buscarElementoPorId("jogadores").replaceChildren();
  for (const jogador of [...estado.jogadores].sort((primeiroJogador, segundoJogador) => primeiroJogador.assento - segundoJogador.assento)) {
    const elemento = document.createElement("div");
    elemento.className = "jogador " + (jogador.assento === estado.vez ? "ativo" : "");
    elemento.textContent = `${jogador.nome} ${jogador.assento >= 0 ? "(assento " + jogador.assento + ", dupla " + (jogador.assento % 2 ? "B" : "A") + ")" : ""} • ${jogador.quantidade} peças ${jogador.pronto ? "✓" : ""} ${jogador.conectado ? "●" : "○"}`;
    buscarElementoPorId("jogadores").append(elemento);
  }
  desenharMesa();
  buscarElementoPorId("pontas").textContent = estado.mesa.length
    ? `Pontas: ${estado.esquerda} e ${estado.direita}`
    : "Mesa vazia";
  const jogadorLocal =
    estado.meuAssento >= 0
      ? estado.jogadores.find((jogador) => jogador.assento === estado.meuAssento)
      : estado.jogadores.find((jogador) => jogador.mao && jogador.nome === nome);
  buscarElementoPorId("nome-jogador").textContent = jogadorLocal?.nome || nome;
  buscarElementoPorId("mao").replaceChildren();
  const possibilidades = estado.possibilidades || [];
  for (const peca of jogadorLocal?.mao || []) {
    const podeJogarPeca = possibilidades.some((jogadaPossivel) => jogadaPossivel.a === peca.a && jogadaPossivel.b === peca.b);
    const elemento = criarElementoPeca(
      peca,
      "vertical " +
        (podeJogarPeca ? "" : "impossivel ") +
        (selecionada?.a === peca.a && selecionada?.b === peca.b ? "selecionada" : ""),
      true,
    );
    elemento.onclick = () => {
      selecionada = peca;
      desenhar();
    };
    buscarElementoPorId("mao").append(elemento);
  }
  buscarElementoPorId("vez").textContent =
    estado.fase === "FINALIZADA"
      ? `Fim! Dupla ${estado.vencedor ? "B" : "A"} venceu — ${estado.resultado}`
      : estado.fase === "PAUSADA"
        ? "Aguardando reconexão..."
        : estado.fase === "AGUARDANDO"
          ? "Aguardando os quatro jogadores confirmarem..."
          : estado.vez === estado.meuAssento
            ? "Sua vez!"
            : "Aguardando jogada do assento " + estado.vez;
  buscarElementoPorId("pronto").hidden = estado.fase !== "AGUARDANDO";
  for (const lado of ["esquerda", "direita"]) {
    const botaoLado = buscarElementoPorId(lado);
    botaoLado.hidden = estado.fase === "AGUARDANDO" || estado.fase === "FINALIZADA";
    const jogadaDaPecaSelecionada = possibilidades.find(
      (jogadaPossivel) => selecionada && jogadaPossivel.a === selecionada.a && jogadaPossivel.b === selecionada.b,
    );
    botaoLado.disabled = !jogadaDaPecaSelecionada || !jogadaDaPecaSelecionada.lados.includes(lado.toUpperCase());
  }
  buscarElementoPorId("eventos").replaceChildren(
    ...estado.eventos
      .slice(-15)
      .reverse()
      .map((evento) => {
        const itemHistorico = document.createElement("li");
        itemHistorico.textContent = evento;
        return itemHistorico;
      }),
  );
}
for (const lado of ["esquerda", "direita"])
  buscarElementoPorId(lado).onclick = async () => {
    if (!selecionada) return;
    const pecaSelecionada = selecionada;
    selecionada = null;
    await agir("JOGAR", {
      a: pecaSelecionada.a,
      b: pecaSelecionada.b,
      lado: lado.toUpperCase(),
      operacao: novoId(),
    });
  };
if (sala && nome) agir("ENTRAR");
setInterval(async () => {
  if (!sala || ocupado) return;
  try {
    await api("ESTADO");
  } catch (erro) {
    aviso(erro.message);
  }
}, 1500);
