package br.edu.domino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.util.*;

class MotorTest {
    @Test
    void quatroJogadoresEPrimeiraBucha() {
        Motor motor = new Motor();
        String sala = JsonParser.parseString(motor.executar("CRIAR", "", "a", "Ana", 0, 0, "", "").json()).getAsJsonObject()
                .get("sala").getAsString();
        for (String id : List.of("b", "c", "d"))
            assertTrue(motor.executar("ENTRAR", sala, id, id, 0, 0, "", "").ok());
        for (String id : List.of("a", "b", "c", "d"))
            assertTrue(motor.executar("PRONTO", sala, id, "", 0, 0, "", "").ok());
        Modelo.Sala salaCriada = motor.estado.salas.get(sala);
        assertEquals("JOGANDO", salaCriada.fase);
        assertEquals(4, salaCriada.jogadores.size());
        assertEquals(28, salaCriada.jogadores.stream().mapToInt(jogador -> jogador.mao.size()).sum());
        Modelo.Jogador abridor = salaCriada.porAssento(salaCriada.vez);
        assertTrue(abridor.mao.stream().anyMatch(peca -> peca.ladoEsquerdoPeca == 1 && peca.ladoDireitoPeca == 1));
        assertFalse(motor.executar("JOGAR", sala, abridor.id, "", 0, 0, "DIREITA", "teste").ok());
        assertTrue(motor.executar("JOGAR", sala, abridor.id, "", 1, 1, "DIREITA", "teste").ok());
        assertEquals(1, salaCriada.mesa.size());
        assertEquals(1, salaCriada.esquerda);
        assertEquals(1, salaCriada.direita);
        assertEquals(27, salaCriada.jogadores.stream().mapToInt(jogador -> jogador.mao.size()).sum());
        assertEquals(1, salaCriada.mesa.size());
        assertTrue(motor.executar("JOGAR", sala, abridor.id, "", 1, 1, "DIREITA", "teste").ok());
        assertEquals(1, salaCriada.mesa.size());
    }

    @Test
    void naoRevelaMaoDosOutros() {
        Motor motor = new Motor();
        String sala = JsonParser.parseString(motor.executar("CRIAR", "", "a", "Ana", 0, 0, "", "").json()).getAsJsonObject()
                .get("sala").getAsString();
        for (String id : List.of("b", "c", "d"))
            motor.executar("ENTRAR", sala, id, id, 0, 0, "", "");
        for (String id : List.of("a", "b", "c", "d"))
            motor.executar("PRONTO", sala, id, "", 0, 0, "", "");
        JsonArray js = JsonParser.parseString(motor.executar("ESTADO", sala, "a", "", 0, 0, "", "").json())
                .getAsJsonObject().getAsJsonArray("jogadores");
        assertEquals(1, java.util.stream.IntStream.range(0, js.size())
                .filter(indiceJogador -> js.get(indiceJogador).getAsJsonObject().has("mao")).count());
    }
}
