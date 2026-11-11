package br.edu.domino;

import br.edu.domino.rpc.*;
import com.google.gson.Gson;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class Servidor {
    static final Gson JSON = new Gson();
    static final ReentrantLock LOCK = new ReentrantLock();
    static final Motor MOTOR = new Motor();
    static volatile boolean ativo;
    static volatile long ultimoPulso = System.currentTimeMillis();
    static volatile boolean recebeuPulso = false;
    static volatile String epoca = "";
    static ManagedChannel canal;
    static JogoGrpc.JogoBlockingStub par;
    static long versao = 0;
    private static boolean parIndisponivel;

    public static void main(String[] args) throws Exception {
        String papel = args.length > 0 ? args[0] : "principal";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 50051;
        String destino = args.length > 2 ? args[2] : "localhost:50052";
        ativo = papel.equalsIgnoreCase("principal");
        epoca = ativo ? java.util.UUID.randomUUID().toString() : "";
        canal = ManagedChannelBuilder.forTarget(destino).usePlaintext().build();
        par = JogoGrpc.newBlockingStub(canal);
        Server servidor = ServerBuilder.forPort(porta).addService(new Servico()).build().start();
        System.out.println("Servidor " + papel + " na porta " + porta + "; par=" + destino);
        ScheduledExecutorService tarefas = Executors.newSingleThreadScheduledExecutor();
        tarefas.scheduleAtFixedRate(() -> {
            LOCK.lock();
            try {
                if (ativo) {
                    if (MOTOR.verificarAusencias())
                        replicar();
                    try {
                        par.withDeadlineAfter(1200, TimeUnit.MILLISECONDS)
                                .pulso(PulsoPedido.newBuilder().setEpoca(epoca).build());
                        registrarParDisponivel();
                    } catch (StatusRuntimeException ex) {
                        registrarFalhaDoPar("pulso", ex);
                    }
                } else if (!papel.equalsIgnoreCase("principal") && recebeuPulso
                        && System.currentTimeMillis() - ultimoPulso > 4500) {
                    ativo = true;
                    epoca = java.util.UUID.randomUUID().toString();
                    MOTOR.reiniciarRelogios();
                    System.out.println("Reserva promovido para ativo. Época " + epoca);
                }
            } finally {
                LOCK.unlock();
            }
        }, 1, 1, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tarefas.shutdownNow();
            servidor.shutdownNow();
            canal.shutdownNow();
        }));
        servidor.awaitTermination();
    }

    private static void replicar() {
        MOTOR.estado.versao = ++versao;
        try {
            Confirmacao c = par.withDeadlineAfter(1800, TimeUnit.MILLISECONDS).replicar(Instantaneo.newBuilder()
                    .setJson(JSON.toJson(MOTOR.estado)).setVersao(versao).setEpoca(epoca).build());
            registrarParDisponivel();
            if (!c.getOk())
                System.err.println("Réplica rejeitada: " + c.getMensagem());
        } catch (StatusRuntimeException e) {
            registrarFalhaDoPar("replicação", e);
        }
    }

    private static void registrarFalhaDoPar(String operacao, StatusRuntimeException erro) {
        if (!parIndisponivel) {
            parIndisponivel = true;
            System.err.println("Par indisponível durante " + operacao + ": " + erro.getMessage()
                    + ". Servidor ativo segue atendendo sem redundância.");
        }
    }

    private static void registrarParDisponivel() {
        if (parIndisponivel) {
            parIndisponivel = false;
            System.out.println("Comunicação com o par restabelecida.");
        }
    }

    static class Servico extends JogoGrpc.JogoImplBase {
        @Override
        public void executar(Pedido p, StreamObserver<Resposta> obs) {
            LOCK.lock();
            try {
                if (!ativo) {
                    obs.onNext(Resposta.newBuilder().setSucesso(false).setMensagem("SERVIDOR_RESERVA").build());
                    obs.onCompleted();
                    return;
                }
                Motor.Resultado r = MOTOR.executar(p.getAcao(), p.getSala(), p.getJogador(), p.getNome(), p.getA(),
                        p.getB(), p.getLado(), p.getOperacao());

                if (r.ok())
                    Servidor.replicar();
                obs.onNext(Resposta.newBuilder().setSucesso(r.ok()).setMensagem(r.mensagem()).setDadosJson(r.json())
                        .setVersao(versao).build());
                obs.onCompleted();
            } catch (Exception e) {
                obs.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            } finally {
                LOCK.unlock();
            }
        }

        @Override
        public void replicar(Instantaneo i, StreamObserver<Confirmacao> obs) {
            LOCK.lock();
            try {
                boolean ok = !ativo && i.getVersao() >= versao;
                if (ok) {
                    Modelo.Estado novo = JSON.fromJson(i.getJson(), Modelo.Estado.class);
                    MOTOR.estado = novo;
                    versao = i.getVersao();
                    epoca = i.getEpoca();
                    ultimoPulso = System.currentTimeMillis();
                    recebeuPulso = true;
                }
                obs.onNext(Confirmacao.newBuilder().setOk(ok)
                        .setMensagem(ok ? "OK" : "Não aceito: ativo ou versão antiga").build());
                obs.onCompleted();
            } finally {
                LOCK.unlock();
            }
        }

        @Override
        public void pulso(PulsoPedido p, StreamObserver<Confirmacao> obs) {
            LOCK.lock();
            try {
                boolean ok = !ativo;
                if (ok) {
                    ultimoPulso = System.currentTimeMillis();
                    epoca = p.getEpoca();
                }
                obs.onNext(Confirmacao.newBuilder().setOk(ok).build());
                obs.onCompleted();
            } finally {
                LOCK.unlock();
            }
        }
    }
}
