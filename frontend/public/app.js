const $ = (id) => document.getElementById(id);
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
$("nome").value = nome;
$("codigo").value = new URLSearchParams(location.search).get("sala") || sala;
function aviso(m) {
  $("aviso").textContent = m;
}
async function api(acao, extra = {}) {
  const r = await fetch("/api", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ acao, sala, jogador: id, nome, ...extra }),
  });
  const d = await r.json();
  if (!d.ok) throw Error(d.mensagem);
  if (d.estado) {
    estado = d.estado;
    sala = estado.sala;
    sessionStorage.setItem("domino-sala", sala);
    sessionStorage.setItem("domino-nome", nome);
    desenhar();
  }
  return d;
}
async function agir(acao, extra = {}) {
  if (ocupado) return;
  ocupado = true;
  try {
    const d = await api(acao, extra);
    aviso(acao === "JOGAR" ? "" : d.mensagem);
  } catch (e) {
    aviso(e.message);
  } finally {
    ocupado = false;
  }
}
$("criar").onclick = () => {
  nome = $("nome").value.trim();
  if (!nome) return aviso("Informe seu nome");
  agir("CRIAR");
};
$("entrar").onclick = () => {
  nome = $("nome").value.trim();
  sala = $("codigo").value.trim().toUpperCase();
  if (!nome || !sala) return aviso("Informe nome e sala");
  agir("ENTRAR");
};
$("pronto").onclick = () => agir("PRONTO");
$("sair").onclick = async () => {
  await agir("SAIR");
  sessionStorage.removeItem("domino-sala");
  sala = "";
  estado = null;
  $("partida").hidden = true;
  $("acesso").hidden = false;
};
$("copiar").onclick = async () => {
  try {
    await navigator.clipboard.writeText(sala);
    aviso("Código copiado");
  } catch {
    const selecao = window.getSelection();
    selecao.selectAllChildren($("sala"));
    aviso(
      document.execCommand("copy")
        ? "Código copiado"
        : `Código da sala: ${sala}`,
    );
  }
};
$("compartilhar").onclick = async () => {
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
function peca(p, classe = "", interativa = false) {
  const el = document.createElement(interativa ? "button" : "div");
  el.className = "peca " + classe;
  el.setAttribute("aria-label", `${p.a} e ${p.b}`);
  if (!interativa) el.setAttribute("role", "img");
  for (const n of [p.a, p.b]) {
    const face = document.createElement("span");
    face.className = "face";
    face.setAttribute("aria-hidden", "true");
    for (const posicao of pontosPorFace[n]) {
      const ponto = document.createElement("i");
      ponto.style.gridArea = `${Math.ceil(posicao / 3)} / ${((posicao - 1) % 3) + 1}`;
      face.append(ponto);
    }
    el.append(face);
  }
  return el;
}
function desenharMesa() {
  const mesa = $("mesa");
  if (!estado || !mesa.clientWidth) {
    mesa.replaceChildren();
    return;
  }
  const unidade = parseFloat(
    getComputedStyle(mesa).getPropertyValue("--unidade"),
  );
  const largura = Math.max(3, Math.floor(mesa.clientWidth / unidade));
  let x = 0;
  let y = 1;
  let direcao = 1;
  let descidas = 0;
  const pecas = estado.mesa.map((p) => {
    const tamanho = p.a === p.b ? 1 : 2;
    const espaco = direcao > 0 ? largura - x : x + 1;
    const curva = descidas > 0 || espaco <= tamanho;
    const dupla = !curva && p.a === p.b;
    const el = peca(
      p,
      curva || dupla ? "vertical" : direcao < 0 ? "invertida" : "",
    );
    el.style.gridColumn = `${direcao < 0 && !curva && !dupla ? x : x + 1} / span ${curva || dupla ? 1 : 2}`;
    el.style.gridRow = `${dupla ? y : y + 1} / span ${curva || dupla ? 2 : 1}`;
    if (curva) {
      y += 2;
      if (++descidas === 2) {
        descidas = 0;
        direcao *= -1;
        x += direcao;
        y -= 1;
      }
    } else {
      x += direcao * (dupla ? 1 : 2);
    }
    return el;
  });
  mesa.replaceChildren(...pecas);
}
let larguraMesa = 0;
new ResizeObserver(([entrada]) => {
  if (entrada.contentRect.width !== larguraMesa) {
    larguraMesa = entrada.contentRect.width;
    desenharMesa();
  }
}).observe($("mesa"));
function desenhar() {
  $("acesso").hidden = true;
  $("partida").hidden = false;
  $("sala").textContent = sala;
  const salaCheia = estado.jogadores.length >= 4;
  $("copiar").hidden = salaCheia;
  $("compartilhar").hidden = salaCheia;
  $("fase").textContent = estado.fase;
  $("pa").textContent = estado.pontos[0];
  $("pb").textContent = estado.pontos[1];
  $("rodada").textContent = estado.rodada;
  $("jogadores").replaceChildren();
  for (const j of [...estado.jogadores].sort((a, b) => a.assento - b.assento)) {
    const el = document.createElement("div");
    el.className = "jogador " + (j.assento === estado.vez ? "ativo" : "");
    el.textContent = `${j.nome} ${j.assento >= 0 ? "(assento " + j.assento + ", dupla " + (j.assento % 2 ? "B" : "A") + ")" : ""} • ${j.quantidade} peças ${j.pronto ? "✓" : ""} ${j.conectado ? "●" : "○"}`;
    $("jogadores").append(el);
  }
  desenharMesa();
  $("pontas").textContent = estado.mesa.length
    ? `Pontas: ${estado.esquerda} e ${estado.direita}`
    : "Mesa vazia";
  const eu =
    estado.meuAssento >= 0
      ? estado.jogadores.find((j) => j.assento === estado.meuAssento)
      : estado.jogadores.find((j) => j.mao && j.nome === nome);
  $("nome-jogador").textContent = eu?.nome || nome;
  $("mao").replaceChildren();
  const possibilidades = estado.possibilidades || [];
  for (const p of eu?.mao || []) {
    const poss = possibilidades.some((x) => x.a === p.a && x.b === p.b);
    const el = peca(
      p,
      "vertical " +
        (poss ? "" : "impossivel ") +
        (selecionada?.a === p.a && selecionada?.b === p.b ? "selecionada" : ""),
      true,
    );
    el.onclick = () => {
      selecionada = p;
      desenhar();
    };
    $("mao").append(el);
  }
  $("vez").textContent =
    estado.fase === "FINALIZADA"
      ? `Fim! Dupla ${estado.vencedor ? "B" : "A"} venceu — ${estado.resultado}`
      : estado.fase === "PAUSADA"
        ? "Aguardando reconexão..."
        : estado.fase === "AGUARDANDO"
          ? "Aguardando os quatro jogadores confirmarem..."
          : estado.vez === estado.meuAssento
            ? "Sua vez!"
            : "Aguardando jogada do assento " + estado.vez;
  $("pronto").hidden = estado.fase !== "AGUARDANDO";
  for (const lado of ["esquerda", "direita"]) {
    const btn = $(lado);
    btn.hidden = estado.fase === "AGUARDANDO" || estado.fase === "FINALIZADA";
    const poss = possibilidades.find(
      (x) => selecionada && x.a === selecionada.a && x.b === selecionada.b,
    );
    btn.disabled = !poss || !poss.lados.includes(lado.toUpperCase());
  }
  $("eventos").replaceChildren(
    ...estado.eventos
      .slice(-15)
      .reverse()
      .map((e) => {
        const li = document.createElement("li");
        li.textContent = e;
        return li;
      }),
  );
}
for (const lado of ["esquerda", "direita"])
  $(lado).onclick = async () => {
    if (!selecionada) return;
    const p = selecionada;
    selecionada = null;
    await agir("JOGAR", {
      a: p.a,
      b: p.b,
      lado: lado.toUpperCase(),
      operacao: novoId(),
    });
  };
if (sala && nome) agir("ENTRAR");
setInterval(async () => {
  if (!sala || ocupado) return;
  try {
    await api("ESTADO");
  } catch (e) {
    aviso(e.message);
  }
}, 1500);
