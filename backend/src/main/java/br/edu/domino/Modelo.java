package br.edu.domino;

import com.google.gson.annotations.SerializedName;
import java.util.*;

public final class Modelo {
    private Modelo() {
    }

    public static class Peca {
        @SerializedName("a")
        public int ladoEsquerdoPeca;
        @SerializedName("b")
        public int ladoDireitoPeca;

        public Peca() {
        }

        public Peca(int ladoEsquerdoPeca, int ladoDireitoPeca) {
            this.ladoEsquerdoPeca = ladoEsquerdoPeca;
            this.ladoDireitoPeca = ladoDireitoPeca;
        }

        public boolean bucha() {
            return ladoEsquerdoPeca == ladoDireitoPeca;
        }

        public boolean contem(int valorProcurado) {
            return ladoEsquerdoPeca == valorProcurado || ladoDireitoPeca == valorProcurado;
        }

        public String toString() {
            return "[" + ladoEsquerdoPeca + "|" + ladoDireitoPeca + "]";
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
            return jogadores.stream().filter(jogador -> jogador.id.equals(id)).findFirst().orElse(null);
        }

        public Jogador porAssento(int numeroAssento) {
            return jogadores.stream().filter(jogador -> jogador.assento == numeroAssento).findFirst().orElse(null);
        }

        public void evento(String mensagemEvento) {
            eventos.add(mensagemEvento);
            if (eventos.size() > 80)
                eventos.remove(0);
        }
    }

    public static class Estado {
        public Map<String, Sala> salas = new HashMap<>();
        public long versao;
    }
}
