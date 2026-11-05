package br.com.conecta.client;

import br.com.conecta.net.Packet;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Toda leitura/escrita deve ocorrer fora da thread JavaFX. */
public final class ChatClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final DataInputStream in;
    private final DataOutputStream out;
    public ChatClient(String host, int port, String name) throws IOException {
        try {
            socket.connect(new InetSocketAddress(host, port), 5000); socket.setTcpNoDelay(true); socket.setSoTimeout(10000);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            Packet.of("HELLO", "", name, "").write(out);
            Packet hello = Packet.read(in);
            if (!hello.type().equals("WELCOME")) throw new IOException(hello.text());
            socket.setSoTimeout(0);
        } catch (IOException | RuntimeException e) { socket.close(); throw e; }
    }
    public void listen(Consumer<Packet> received, Consumer<String> disconnected) {
        Thread.ofVirtual().name("conecta-reader").start(() -> {
            String reason = "Conexão encerrada.";
            try { while (!closed.get()) received.accept(Packet.read(in)); }
            catch (IOException e) { reason = "Conexão perdida. Conecte novamente para continuar."; }
            finally { boolean notify = !closed.get(); close(); if (notify) disconnected.accept(reason); }
        });
    }
    public synchronized void send(Packet packet) throws IOException {
        if (closed.get()) throw new IOException("Você está desconectado.");
        packet.write(out);
    }
    @Override public void close() {
        closed.set(true); try { socket.close(); } catch (IOException ignored) { }
    }
}
