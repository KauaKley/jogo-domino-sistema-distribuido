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

    private Resultado erro(String s) {
        return new Resultado(false, s, "{}");
    }

    private Resultado ok(String s, Sala sala, String id) {
        return new Resultado(true, s, gson.toJson(visao(sala, id)));
    }

    public Resultado executar(String acao, String salaId, String id, String nome, int a, int b, String lado,
            String op) {
        if ("CRIAR".equals(acao)) {
            Sala s = new Sala();
            s.id = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            estado.salas.put(s.id, s);
            return entrar(s, id, nome);
        }
        Sala s = estado.salas.get(salaId.toUpperCase(Locale.ROOT));
        if (s == null)
            return erro("Sala inexistente");
        if ("ENTRAR".equals(acao))
            return entrar(s, id, nome);
        Jogador j = s.porId(id);
        if (j == null)
            return erro("Jogador não pertence à sala");
        j.ultimoSinal = System.currentTimeMillis();
        if ("ESTADO".equals(acao))
            return ok("Estado atualizado", s, id);
        if ("SAIR".equals(acao)) {
            if (s.fase.equals("AGUARDANDO")) {
                s.jogadores.remove(j);
                s.evento(j.nome + " saiu");
            } else if (!s.fase.equals("FINALIZADA"))
                encerrar(s, 1 - (j.assento % 2), "Abandono de " + j.nome);
            return ok("Saída registrada", s, id);
        }
        if (s.fase.equals("FINALIZADA"))
            return erro("Partida finalizada");
        if ("PRONTO".equals(acao)) {
            if (!s.fase.equals("AGUARDANDO"))
                return erro("Partida já começou");
            j.pronto = true;
            s.evento(j.nome + " está pronto");
            if (s.jogadores.size() == 4 && s.jogadores.stream().allMatch(x -> x.pronto))
                iniciar(s);
            return ok("Pronto confirmado", s, id);
        }
        if ("JOGAR".equals(acao)) {
            if (op == null || op.isBlank())
                return erro("Identificador da operação obrigatório");

            if (s.operacoes.containsKey(id + ":" + op))
                return ok("Operação já processada", s, id);

            if (!s.fase.equals("JOGANDO"))
                return erro("A partida está pausada ou não começou");

            if (j.assento != s.vez)
                return erro("Não é a sua vez");

            if (a < 0 || a > 6 || b < 0 || b > 6)
                return erro("Peça inválida");
            Peca p = j.mao.stream().filter(x -> (x.a == a && x.b == b) || (x.a == b && x.b == a)).findFirst()
                    .orElse(null);
            if (p == null)
                return erro("Peça não pertence à mão");
            int antigaEsq = s.esquerda, antigaDir = s.direita;
            if (s.mesa.isEmpty()) {
                if (s.rodada == 1 && !(a == 1 && b == 1))
                    return erro("A primeira peça deve ser [1|1]");
                s.mesa.add(new Peca(p.a, p.b));
                s.esquerda = p.a;
                s.direita = p.b;
            } else if ("ESQUERDA".equals(lado)) {
                if (!p.contem(s.esquerda))
                    return erro("Peça não encaixa na esquerda");
                int externo = p.a == s.esquerda ? p.b : p.a;
                s.mesa.add(0, new Peca(externo, s.esquerda));
                s.esquerda = externo;
            } else if ("DIREITA".equals(lado)) {
                if (!p.contem(s.direita))
                    return erro("Peça não encaixa na direita");
                int externo = p.a == s.direita ? p.b : p.a;
                s.mesa.add(new Peca(s.direita, externo));
                s.direita = externo;
            } else
                return erro("Informe ESQUERDA ou DIREITA");
            j.mao.remove(p);
            s.operacoes.put(id + ":" + op, true);
            s.evento(j.nome + " jogou " + p + " na " + (s.mesa.size() == 1 ? "abertura" : lado));
            if (j.mao.isEmpty()) {
                String tipo;
                int pontos;
                if (p.bucha() && antigaEsq == p.a && antigaDir == p.a) {
                    tipo = "PRESO";
                    pontos = 3;
                } else if (p.bucha()) {
                    tipo = "BUCHADA";
                    pontos = 2;
                } else if ((p.a == antigaEsq && p.b == antigaDir) || (p.b == antigaEsq && p.a == antigaDir)) {
                    tipo = "LASQUINÊ";
                    pontos = 2;
                } else {
                    tipo = "BATIDA COMUM";
                    pontos = 1;
                }
                s.evento(j.nome + " venceu a rodada: " + tipo + " (" + pontos + " pontos)");
                int dupla = j.assento % 2;
                s.pontos[dupla] = Math.min(3, s.pontos[dupla] + pontos);
                if (pontos == 3 || s.pontos[dupla] == 3)
                    encerrar(s, dupla, tipo);
                else
                    novaRodada(s, j.assento);
            } else if (!haJogadaDeNinguem(s))
                fechada(s, j.assento);
            else {
                s.vez = (s.vez + 1) % 4;
                passarAutomaticamente(s);
            }
            return ok("Jogada aceita", s, id);
        }
        return erro("Ação desconhecida");
    }

    private Resultado entrar(Sala s, String id, String nome) {
        if (id == null || id.isBlank() || nome == null || nome.isBlank())
            return erro("Nome e identificador obrigatórios");
        Jogador existente = s.porId(id);
        if (existente != null) {
            existente.ultimoSinal = System.currentTimeMillis();
            return ok("Reconectado", s, id);
        }
        if (!s.fase.equals("AGUARDANDO") || s.jogadores.size() >= 4)
            return erro("Sala indisponível");
        Jogador j = new Jogador();
        j.id = id;
        j.nome = nome.substring(0, Math.min(32, nome.length()));
        j.ultimoSinal = System.currentTimeMillis();
        s.jogadores.add(j);
        s.evento(j.nome + " entrou");
        return ok("Entrada confirmada", s, id);
    }

    private void iniciar(Sala s) {
        List<Integer> assentos = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(assentos, sorteio);
        for (int i = 0; i < 4; i++)
            s.jogadores.get(i).assento = assentos.get(i);
        s.pontos = new int[] { 0, 0 };
        s.fase = "JOGANDO";
        novaRodada(s, -1);
    }

    private void novaRodada(Sala s, int abridor) {
        s.rodada++;
        s.mesa.clear();
        s.esquerda = -1;
        s.direita = -1;
        s.operacoes.clear();
        List<Peca> baralho = new ArrayList<>();
        for (int a = 0; a <= 6; a++)
            for (int b = a; b <= 6; b++)
                baralho.add(new Peca(a, b));
        boolean distribuicaoValida;
        do {
            Collections.shuffle(baralho, sorteio);
            int[] buchasPorAssento = new int[4];
            distribuicaoValida = true;
            for (int i = 0; i < baralho.size(); i++) {
                if (baralho.get(i).bucha() && ++buchasPorAssento[i % 4] == 5) {
                    distribuicaoValida = false;
                    break;
                }
            }
        } while (!distribuicaoValida);
        for (Jogador j : s.jogadores)
            j.mao.clear();
        for (int i = 0; i < baralho.size(); i++)
            s.porAssento(i % 4).mao.add(baralho.get(i));
        if (abridor == -1) {
            s.vez = s.jogadores.stream().filter(j -> j.mao.stream().anyMatch(p -> p.a == 1 && p.b == 1)).findFirst()
                    .orElseThrow().assento;
        } else
            s.vez = abridor;
        s.evento("Rodada " + s.rodada + " iniciada. Vez do assento " + s.vez);
    }

    private boolean jogavel(Sala s, Jogador j) {
        return s.mesa.isEmpty() || j.mao.stream().anyMatch(p -> p.contem(s.esquerda) || p.contem(s.direita));
    }

    private boolean haJogadaDeNinguem(Sala s) {
        return s.jogadores.stream().anyMatch(j -> jogavel(s, j));
    }

    private void passarAutomaticamente(Sala s) {
        for (int i = 0; i < 4 && !jogavel(s, s.porAssento(s.vez)); i++) {
            s.evento(s.porAssento(s.vez).nome + " passou automaticamente");
            s.vez = (s.vez + 1) % 4;
        }
    }

    private void fechada(Sala s, int fechador) {
        int menor = s.jogadores.stream().mapToInt(j -> j.mao.stream().mapToInt(p -> p.a + p.b).sum()).min()
                .orElseThrow();
        Set<Integer> duplas = new HashSet<>();
        for (Jogador j : s.jogadores) {
            int soma = j.mao.stream().mapToInt(p -> p.a + p.b).sum();
            s.evento(j.nome + ": " + soma + " pontos nas peças");
            if (soma == menor)
                duplas.add(j.assento % 2);
        }
        int vencedora = duplas.size() == 2 ? 1 - (fechador % 2) : duplas.iterator().next();
        s.pontos[vencedora] = 3;
        encerrar(s, vencedora, "JOGO FECHADO (menor soma individual)");
    }

    private void encerrar(Sala s, int dupla, String motivo) {
        s.fase = "FINALIZADA";
        s.vencedor = dupla;
        s.resultado = motivo;
        s.evento("Partida encerrada: dupla " + (dupla == 0 ? "A" : "B") + " venceu. " + motivo);
    }

    public boolean verificarAusencias() {
        boolean mudou = false;
        long agora = System.currentTimeMillis();
        for (Sala s : estado.salas.values()) {
            if (s.fase.equals("FINALIZADA"))
                continue;
            if (s.fase.equals("AGUARDANDO")) {
                mudou |= s.jogadores.removeIf(j -> agora - j.ultimoSinal > 60000);
            } else {
                Jogador expirado = s.jogadores.stream().filter(j -> agora - j.ultimoSinal > 60000).findFirst()
                        .orElse(null);
                if (expirado != null) {
                    encerrar(s, 1 - (expirado.assento % 2), "Desconexão de " + expirado.nome);
                    mudou = true;
                } else {
                    boolean pausa = s.jogadores.stream().anyMatch(j -> agora - j.ultimoSinal > 5000);
                    String nova = pausa ? "PAUSADA" : "JOGANDO";
                    if (!s.fase.equals(nova)) {
                        s.fase = nova;
                        s.evento(pausa ? "Partida pausada por desconexão" : "Todos reconectados. Partida retomada");
                        mudou = true;
                    }
                }
            }
        }
        return mudou;
    }

    public void reiniciarRelogios() {
        long agora = System.currentTimeMillis();
        for (Sala s : estado.salas.values())
            for (Jogador j : s.jogadores)
                j.ultimoSinal = agora;
    }

    public Object visao(Sala s, String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sala", s.id);
        m.put("fase", s.fase);
        m.put("rodada", s.rodada);
        m.put("vez", s.vez);
        m.put("mesa", s.mesa);
        m.put("esquerda", s.esquerda);
        m.put("direita", s.direita);
        m.put("pontos", s.pontos);
        m.put("vencedor", s.vencedor);
        m.put("resultado", s.resultado);
        m.put("eventos", s.eventos);
        List<Object> jogadores = new ArrayList<>();
        for (Jogador j : s.jogadores) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nome", j.nome);
            p.put("assento", j.assento);
            p.put("pronto", j.pronto);
            p.put("quantidade", j.mao.size());
            p.put("conectado", System.currentTimeMillis() - j.ultimoSinal <= 5000);
            if (j.id.equals(id) || s.resultado.startsWith("JOGO FECHADO"))
                p.put("mao", j.mao);
            jogadores.add(p);
        }
        m.put("jogadores", jogadores);
        Jogador eu = s.porId(id);
        m.put("meuAssento", eu == null ? -1 : eu.assento);
        List<Object> possibilidades = new ArrayList<>();
        if (eu != null && s.fase.equals("JOGANDO") && s.vez == eu.assento)
            for (Peca p : eu.mao) {
                if (s.mesa.isEmpty()) {
                    if (s.rodada != 1 || (p.a == 1 && p.b == 1))
                        possibilidades.add(Map.of("a", p.a, "b", p.b, "lados", List.of("DIREITA")));
                } else {
                    List<String> lados = new ArrayList<>();
                    if (p.contem(s.esquerda))
                        lados.add("ESQUERDA");
                    if (p.contem(s.direita))
                        lados.add("DIREITA");
                    if (!lados.isEmpty())
                        possibilidades.add(Map.of("a", p.a, "b", p.b, "lados", lados));
                }
            }
        m.put("possibilidades", possibilidades);
        return m;
    }
}
