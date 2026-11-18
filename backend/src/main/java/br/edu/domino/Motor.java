package br.edu.domino;

import br.edu.domino.Modelo.*;
import com.google.gson.Gson;
import java.util.*;

public class Motor {
    private final Random sorteio = new Random();
    private final Gson gson = new Gson();
    public Estado estado = new Estado();

    public record Resultado(boolean ok, String mensagem, String json) {
    }

    private Resultado erro(String mensagem) {
        return new Resultado(false, mensagem, "{}");
    }

    private Resultado ok(String mensagem, Sala sala, String id) {
        return new Resultado(true, mensagem, gson.toJson(visao(sala, id)));
    }

    public Resultado executar(String acao, String salaId, String id, String nome, int ladoEsquerdoPeca, int ladoDireitoPeca, String lado,
            String op) {
        if ("CRIAR".equals(acao)) {
            Sala sala = new Sala();
            sala.id = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            estado.salas.put(sala.id, sala);
            return entrar(sala, id, nome);
        }
        Sala sala = estado.salas.get(salaId.toUpperCase(Locale.ROOT));
        if (sala == null)
            return erro("Sala inexistente");
        if ("ENTRAR".equals(acao))
            return entrar(sala, id, nome);
        Jogador jogador = sala.porId(id);
        if (jogador == null)
            return erro("Jogador não pertence à sala");
        jogador.ultimoSinal = System.currentTimeMillis();
        if ("ESTADO".equals(acao))
            return ok("Estado atualizado", sala, id);
        if ("SAIR".equals(acao)) {
            sala.jogadores.remove(jogador);
            if (sala.fase.equals("AGUARDANDO")) {
                sala.evento(jogador.nome + " saiu");
            } else if (!sala.fase.equals("FINALIZADA"))
                encerrar(sala, 1 - (jogador.assento % 2), "Abandono de " + jogador.nome);
            return ok("Saída registrada", sala, id);
        }
        if (sala.fase.equals("FINALIZADA"))
            return erro("Partida finalizada");
        if ("PRONTO".equals(acao)) {
            if (!sala.fase.equals("AGUARDANDO"))
                return erro("Partida já começou");
            jogador.pronto = true;
            sala.evento(jogador.nome + " está pronto");
            if (sala.jogadores.size() == 4 && sala.jogadores.stream().allMatch(jogadorDaSala -> jogadorDaSala.pronto))
                iniciar(sala);
            return ok("Pronto confirmado", sala, id);
        }
        if ("JOGAR".equals(acao)) {
            if (op == null || op.isBlank())
                return erro("Identificador da operação obrigatório");

            if (sala.operacoes.containsKey(id + ":" + op))
                return ok("Operação já processada", sala, id);

            if (!sala.fase.equals("JOGANDO"))
                return erro("A partida está pausada ou não começou");

            if (jogador.assento != sala.vez)
                return erro("Não é a sua vez");

            if (ladoEsquerdoPeca < 0 || ladoEsquerdoPeca > 6 || ladoDireitoPeca < 0 || ladoDireitoPeca > 6)
                return erro("Peça inválida");
            Peca peca = jogador.mao.stream().filter(pecaDaMao -> (pecaDaMao.ladoEsquerdoPeca == ladoEsquerdoPeca && pecaDaMao.ladoDireitoPeca == ladoDireitoPeca) || (pecaDaMao.ladoEsquerdoPeca == ladoDireitoPeca && pecaDaMao.ladoDireitoPeca == ladoEsquerdoPeca)).findFirst()
                    .orElse(null);
            if (peca == null)
                return erro("Peça não pertence à mão");
            int antigaEsq = sala.esquerda, antigaDir = sala.direita;
            if (sala.mesa.isEmpty()) {
                if (sala.rodada == 1 && !(ladoEsquerdoPeca == 1 && ladoDireitoPeca == 1))
                    return erro("A primeira peça deve ser [1|1]");
                sala.mesa.add(new Peca(peca.ladoEsquerdoPeca, peca.ladoDireitoPeca));
                sala.esquerda = peca.ladoEsquerdoPeca;
                sala.direita = peca.ladoDireitoPeca;
            } else if ("ESQUERDA".equals(lado)) {
                if (!peca.contem(sala.esquerda))
                    return erro("Peça não encaixa na esquerda");
                int externo = peca.ladoEsquerdoPeca == sala.esquerda ? peca.ladoDireitoPeca : peca.ladoEsquerdoPeca;
                sala.mesa.add(0, new Peca(externo, sala.esquerda));
                sala.esquerda = externo;
            } else if ("DIREITA".equals(lado)) {
                if (!peca.contem(sala.direita))
                    return erro("Peça não encaixa na direita");
                int externo = peca.ladoEsquerdoPeca == sala.direita ? peca.ladoDireitoPeca : peca.ladoEsquerdoPeca;
                sala.mesa.add(new Peca(sala.direita, externo));
                sala.direita = externo;
            } else
                return erro("Informe ESQUERDA ou DIREITA");
            jogador.mao.remove(peca);
            sala.operacoes.put(id + ":" + op, true);
            sala.evento(jogador.nome + " jogou " + peca + " na " + (sala.mesa.size() == 1 ? "abertura" : lado));
            if (jogador.mao.isEmpty()) {
                String tipo;
                int pontos;
                if (peca.bucha() && antigaEsq == peca.ladoEsquerdoPeca && antigaDir == peca.ladoEsquerdoPeca) {
                    tipo = "PRESO";
                    pontos = 3;
                } else if (peca.bucha()) {
                    tipo = "BUCHADA";
                    pontos = 2;
                } else if ((peca.ladoEsquerdoPeca == antigaEsq && peca.ladoDireitoPeca == antigaDir) || (peca.ladoDireitoPeca == antigaEsq && peca.ladoEsquerdoPeca == antigaDir)) {
                    tipo = "LASQUINÊ";
                    pontos = 2;
                } else {
                    tipo = "BATIDA COMUM";
                    pontos = 1;
                }
                sala.evento(jogador.nome + " venceu a rodada: " + tipo + " (" + pontos + " pontos)");
                int dupla = jogador.assento % 2;
                sala.pontos[dupla] = Math.min(3, sala.pontos[dupla] + pontos);
                if (pontos == 3 || sala.pontos[dupla] == 3)
                    encerrar(sala, dupla, tipo);
                else
                    novaRodada(sala, jogador.assento);
            } else if (!haJogadaDeNinguem(sala))
                fechada(sala, jogador.assento);
            else {
                sala.vez = (sala.vez + 1) % 4;
                passarAutomaticamente(sala);
            }
            return ok("Jogada aceita", sala, id);
        }
        return erro("Ação desconhecida");
    }

    private Resultado entrar(Sala sala, String id, String nome) {
        if (id == null || id.isBlank() || nome == null || nome.isBlank())
            return erro("Nome e identificador obrigatórios");
        Jogador existente = sala.porId(id);
        if (existente != null) {
            existente.ultimoSinal = System.currentTimeMillis();
            return ok("Reconectado", sala, id);
        }
        if (!sala.fase.equals("AGUARDANDO") || sala.jogadores.size() >= 4)
            return erro("Sala indisponível");
        Jogador jogador = new Jogador();
        jogador.id = id;
        jogador.nome = nome.substring(0, Math.min(32, nome.length()));
        jogador.ultimoSinal = System.currentTimeMillis();
        sala.jogadores.add(jogador);
        sala.evento(jogador.nome + " entrou");
        return ok("Entrada confirmada", sala, id);
    }

    private void iniciar(Sala sala) {
        List<Integer> assentos = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(assentos, sorteio);
        for (int indiceJogador = 0; indiceJogador < 4; indiceJogador++)
            sala.jogadores.get(indiceJogador).assento = assentos.get(indiceJogador);
        sala.pontos = new int[] { 0, 0 };
        sala.fase = "JOGANDO";
        novaRodada(sala, -1);
    }

    private void novaRodada(Sala sala, int abridor) {
        sala.rodada++;
        sala.mesa.clear();
        sala.esquerda = -1;
        sala.direita = -1;
        sala.operacoes.clear();
        List<Peca> baralho = new ArrayList<>();
        for (int ladoEsquerdoPeca = 0; ladoEsquerdoPeca <= 6; ladoEsquerdoPeca++)
            for (int ladoDireitoPeca = ladoEsquerdoPeca; ladoDireitoPeca <= 6; ladoDireitoPeca++)
                baralho.add(new Peca(ladoEsquerdoPeca, ladoDireitoPeca));
        boolean distribuicaoValida;
        do {
            Collections.shuffle(baralho, sorteio);
            int[] buchasPorAssento = new int[4];
            distribuicaoValida = true;
            for (int indicePeca = 0; indicePeca < baralho.size(); indicePeca++) {
                if (baralho.get(indicePeca).bucha() && ++buchasPorAssento[indicePeca % 4] == 5) {
                    distribuicaoValida = false;
                    break;
                }
            }
        } while (!distribuicaoValida);
        for (Jogador jogador : sala.jogadores)
            jogador.mao.clear();
        for (int indicePeca = 0; indicePeca < baralho.size(); indicePeca++)
            sala.porAssento(indicePeca % 4).mao.add(baralho.get(indicePeca));
        if (abridor == -1) {
            sala.vez = sala.jogadores.stream().filter(jogador -> jogador.mao.stream().anyMatch(peca -> peca.ladoEsquerdoPeca == 1 && peca.ladoDireitoPeca == 1)).findFirst()
                    .orElseThrow().assento;
        } else
            sala.vez = abridor;
        sala.evento("Rodada " + sala.rodada + " iniciada. Vez do assento " + sala.vez);
    }

    private boolean jogavel(Sala sala, Jogador jogador) {
        return sala.mesa.isEmpty() || jogador.mao.stream().anyMatch(peca -> peca.contem(sala.esquerda) || peca.contem(sala.direita));
    }

    private boolean haJogadaDeNinguem(Sala sala) {
        return sala.jogadores.stream().anyMatch(jogador -> jogavel(sala, jogador));
    }

    private void passarAutomaticamente(Sala sala) {
        for (int passesVerificados = 0; passesVerificados < 4 && !jogavel(sala, sala.porAssento(sala.vez)); passesVerificados++) {
            sala.evento(sala.porAssento(sala.vez).nome + " passou automaticamente");
            sala.vez = (sala.vez + 1) % 4;
        }
    }

    private void fechada(Sala sala, int fechador) {
        int menor = sala.jogadores.stream().mapToInt(jogador -> jogador.mao.stream().mapToInt(peca -> peca.ladoEsquerdoPeca + peca.ladoDireitoPeca).sum()).min()
                .orElseThrow();
        Set<Integer> duplas = new HashSet<>();
        for (Jogador jogador : sala.jogadores) {
            int soma = jogador.mao.stream().mapToInt(peca -> peca.ladoEsquerdoPeca + peca.ladoDireitoPeca).sum();
            sala.evento(jogador.nome + ": " + soma + " pontos nas peças");
            if (soma == menor)
                duplas.add(jogador.assento % 2);
        }
        int vencedora = duplas.size() == 2 ? 1 - (fechador % 2) : duplas.iterator().next();
        sala.pontos[vencedora] = 3;
        encerrar(sala, vencedora, "JOGO FECHADO (menor soma individual)");
    }

    private void encerrar(Sala sala, int dupla, String motivo) {
        sala.fase = "FINALIZADA";
        sala.vencedor = dupla;
        sala.resultado = motivo;
        sala.evento("Partida encerrada: dupla " + (dupla == 0 ? "A" : "B") + " venceu. " + motivo);
    }

    public boolean verificarAusencias() {
        boolean mudou = false;
        long agora = System.currentTimeMillis();
        for (Sala sala : estado.salas.values()) {
            if (sala.fase.equals("FINALIZADA"))
                continue;
            if (sala.fase.equals("AGUARDANDO")) {
                mudou |= sala.jogadores.removeIf(jogador -> agora - jogador.ultimoSinal > 60000);
            } else {
                Jogador expirado = sala.jogadores.stream().filter(jogador -> agora - jogador.ultimoSinal > 60000).findFirst()
                        .orElse(null);
                if (expirado != null) {
                    encerrar(sala, 1 - (expirado.assento % 2), "Desconexão de " + expirado.nome);
                    mudou = true;
                } else {
                    boolean pausa = sala.jogadores.stream().anyMatch(jogador -> agora - jogador.ultimoSinal > 5000);
                    String nova = pausa ? "PAUSADA" : "JOGANDO";
                    if (!sala.fase.equals(nova)) {
                        sala.fase = nova;
                        sala.evento(pausa ? "Partida pausada por desconexão" : "Todos reconectados. Partida retomada");
                        mudou = true;
                    }
                }
            }
        }
        return mudou;
    }

    public void reiniciarRelogios() {
        long agora = System.currentTimeMillis();
        for (Sala sala : estado.salas.values())
            for (Jogador jogador : sala.jogadores)
                jogador.ultimoSinal = agora;
    }

    public Object visao(Sala sala, String id) {
        Map<String, Object> estadoParaJogador = new LinkedHashMap<>();
        estadoParaJogador.put("sala", sala.id);
        estadoParaJogador.put("fase", sala.fase);
        estadoParaJogador.put("rodada", sala.rodada);
        estadoParaJogador.put("vez", sala.vez);
        estadoParaJogador.put("mesa", sala.mesa);
        estadoParaJogador.put("esquerda", sala.esquerda);
        estadoParaJogador.put("direita", sala.direita);
        estadoParaJogador.put("pontos", sala.pontos);
        estadoParaJogador.put("vencedor", sala.vencedor);
        estadoParaJogador.put("resultado", sala.resultado);
        estadoParaJogador.put("eventos", sala.eventos);
        List<Object> jogadores = new ArrayList<>();
        for (Jogador jogador : sala.jogadores) {
            Map<String, Object> dadosJogador = new LinkedHashMap<>();
            dadosJogador.put("nome", jogador.nome);
            dadosJogador.put("assento", jogador.assento);
            dadosJogador.put("pronto", jogador.pronto);
            dadosJogador.put("quantidade", jogador.mao.size());
            dadosJogador.put("conectado", System.currentTimeMillis() - jogador.ultimoSinal <= 5000);
            if (jogador.id.equals(id) || sala.resultado.startsWith("JOGO FECHADO"))
                dadosJogador.put("mao", jogador.mao);
            jogadores.add(dadosJogador);
        }
        estadoParaJogador.put("jogadores", jogadores);
        Jogador eu = sala.porId(id);
        estadoParaJogador.put("meuAssento", eu == null ? -1 : eu.assento);
        List<Object> possibilidades = new ArrayList<>();
        if (eu != null && sala.fase.equals("JOGANDO") && sala.vez == eu.assento)
            for (Peca peca : eu.mao) {
                if (sala.mesa.isEmpty()) {
                    if (sala.rodada != 1 || (peca.ladoEsquerdoPeca == 1 && peca.ladoDireitoPeca == 1))
                        possibilidades.add(Map.of("a", peca.ladoEsquerdoPeca, "b", peca.ladoDireitoPeca, "lados", List.of("DIREITA")));
                } else {
                    List<String> lados = new ArrayList<>();
                    if (peca.contem(sala.esquerda))
                        lados.add("ESQUERDA");
                    if (peca.contem(sala.direita))
                        lados.add("DIREITA");
                    if (!lados.isEmpty())
                        possibilidades.add(Map.of("a", peca.ladoEsquerdoPeca, "b", peca.ladoDireitoPeca, "lados", lados));
                }
            }
        estadoParaJogador.put("possibilidades", possibilidades);
        return estadoParaJogador;
    }
}
