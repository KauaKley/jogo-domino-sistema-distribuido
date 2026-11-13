# Dominó em Duplas

Projeto acadêmico de Sistemas Distribuídos. O jogo reúne quatro jogadores em duas duplas. Cada jogador entra pelo navegador com seu nome e o código da sala.

## Como funciona

- A interface usa HTML, CSS e JavaScript.
- Um gateway Node.js recebe as ações do navegador por HTTP e as encaminha ao backend Java via gRPC.
- O servidor Java valida as jogadas, controla os turnos e calcula o placar.
- O servidor principal replica o estado da partida para um servidor reserva, que pode assumir se o principal cair.

## Requisitos

- Java 17 ou superior
- Maven 3.9 ou superior
- Node.js 20 ou superior e npm

## Como rodar

Na pasta `backend`, compile o projeto:

```sh
mvn clean package
```

Ainda na pasta `backend`, inicie o servidor **reserva**:

```sh
mvn exec:java '-Dexec.args=reserva 50052 localhost:50051'
```

Em outro terminal, na mesma pasta, inicie o servidor **principal**:

```sh
mvn exec:java '-Dexec.args=principal 50051 localhost:50052'
```

Em um terceiro terminal, na pasta `frontend`, inicie a interface:

```sh
npm install
npm start
```

Abra o link disponibilizado no terminal da sua escolha no navegador.

## Como jogar

1. Digite seu nome e crie uma sala.
2. Compartilhe o código ou o link com os outros três jogadores.
3. Quando todos entrarem, cada jogador deve clicar em **Estou pronto**.
4. Quem tiver a bucha `[1|1]` começa a primeira rodada. Vence a dupla que chegar a três pontos.

## Como testar o jogo

1. Para testar sozinho, abra quatro abas no mesmo navegador e entre com um nome diferente em cada uma. 
2. Para jogar com outros dispositivos no mesmo Wi-Fi, use o endereço de rede exibido no terminal do Node.js. 
3. No GitHub Codespaces, encaminhe a porta `3000`, configure a visibilidade como **Public** e use o endereço HTTPS gerado para compartilhar a sala.
