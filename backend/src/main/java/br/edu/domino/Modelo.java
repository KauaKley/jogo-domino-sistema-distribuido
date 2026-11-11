package br.edu.domino;

import java.util.*;

public final class Modelo {
    private Modelo() {
    }

    public static class Peca {
        public int a, b;

        public Peca() {
        }

        public Peca(int a, int b) {
            this.a = a;
            this.b = b;
        }

        public boolean bucha() {
            return a == b;
        }

        public boolean contem(int n) {
            return a == n || b == n;
        }

        public String toString() {
            return "[" + a + "|" + b + "]";
        }
    }

    public static class Jogador {
        public String id, nome;
        public int assento = -1;
        public boolean pronto;
        public long ultimoSinal;
        public List<Peca> mao = new ArrayList<>();
    }

    public static class Sala {
        public String id, fase = "AGUARDANDO", resultado = "";
        public List<Jogador> jogadores = new ArrayList<>();
        public List<Peca> mesa = new ArrayList<>();
        public List<String> eventos = new ArrayList<>();
        public int esquerda = -1, direita = -1, vez = -1, rodada = 0;
        public int[] pontos = { 0, 0 };
        public int vencedor = -1;
        public long versao = 0;
        public Map<String, Boolean> operacoes = new HashMap<>();

        public Jogador porId(String id) {
            return jogadores.stream().filter(j -> j.id.equals(id)).findFirst().orElse(null);
        }

        public Jogador porAssento(int a) {
            return jogadores.stream().filter(j -> j.assento == a).findFirst().orElse(null);
        }

        public void evento(String e) {
            eventos.add(e);
            if (eventos.size() > 80)
                eventos.remove(0);
        }
    }

    public static class Estado {
        public Map<String, Sala> salas = new HashMap<>();
        public long versao;
    }
}
