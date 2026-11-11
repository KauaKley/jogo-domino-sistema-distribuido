package br.edu.domino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.util.*;

class MotorTest {
    @Test
    void quatroJogadoresEPrimeiraBucha() {
        Motor m = new Motor();
        String sala = JsonParser.parseString(m.executar("CRIAR", "", "a", "Ana", 0, 0, "", "").json()).getAsJsonObject()
                .get("sala").getAsString();
        for (String id : List.of("b", "c", "d"))
            assertTrue(m.executar("ENTRAR", sala, id, id, 0, 0, "", "").ok());
        for (String id : List.of("a", "b", "c", "d"))
            assertTrue(m.executar("PRONTO", sala, id, "", 0, 0, "", "").ok());
        Modelo.Sala s = m.estado.salas.get(sala);
        assertEquals("JOGANDO", s.fase);
        assertEquals(4, s.jogadores.size());
        assertEquals(28, s.jogadores.stream().mapToInt(j -> j.mao.size()).sum());
        Modelo.Jogador abridor = s.porAssento(s.vez);
        assertTrue(abridor.mao.stream().anyMatch(p -> p.a == 1 && p.b == 1));
        assertFalse(m.executar("JOGAR", sala, abridor.id, "", 0, 0, "DIREITA", "teste").ok());
        assertTrue(m.executar("JOGAR", sala, abridor.id, "", 1, 1, "DIREITA", "teste").ok());
        assertEquals(1, s.mesa.size());
        assertEquals(1, s.esquerda);
        assertEquals(1, s.direita);
        assertEquals(27, s.jogadores.stream().mapToInt(j -> j.mao.size()).sum());
        assertEquals(1, s.mesa.size());
        assertTrue(m.executar("JOGAR", sala, abridor.id, "", 1, 1, "DIREITA", "teste").ok());
        assertEquals(1, s.mesa.size());
    }

    @Test
    void naoRevelaMaoDosOutros() {
        Motor m = new Motor();
        String sala = JsonParser.parseString(m.executar("CRIAR", "", "a", "Ana", 0, 0, "", "").json()).getAsJsonObject()
                .get("sala").getAsString();
        for (String id : List.of("b", "c", "d"))
            m.executar("ENTRAR", sala, id, id, 0, 0, "", "");
        for (String id : List.of("a", "b", "c", "d"))
            m.executar("PRONTO", sala, id, "", 0, 0, "", "");
        JsonArray js = JsonParser.parseString(m.executar("ESTADO", sala, "a", "", 0, 0, "", "").json())
                .getAsJsonObject().getAsJsonArray("jogadores");
        assertEquals(1, java.util.stream.IntStream.range(0, js.size())
                .filter(i -> js.get(i).getAsJsonObject().has("mao")).count());
    }
}
