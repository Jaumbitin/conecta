package br.com.conecta.server;

import br.com.conecta.net.Packet;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Salas públicas em memória. Uma sala ativa por participante. */
public final class ChatServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Set<Peer> connections = ConcurrentHashMap.newKeySet();
    private final Map<String, Peer> users = new LinkedHashMap<>();
    private final Set<String> rooms = new TreeSet<>(Set.of("geral"));
    private volatile boolean running = true;

    public ChatServer(String host, int port) throws IOException {
        listener = new ServerSocket(); listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(host, port));
        Thread.ofVirtual().name("conecta-accept").start(this::accept);
    }
    public int port() { return listener.getLocalPort(); }
    private void accept() {
        while (running) try {
            Socket socket = listener.accept();
            synchronized (this) {
                if (connections.size() >= 100) { socket.close(); continue; }
                Peer peer = new Peer(socket); connections.add(peer);
                Thread.ofVirtual().start(peer::read);
            }
        } catch (IOException e) { if (running) System.err.println(e.getMessage()); }
    }
    private static String label(String value) throws IOException {
        String s = value.strip();
        if (!s.matches("[\\p{L}\\p{N}_ -]{1,32}")) throw new IOException("Use de 1 a 32 letras, números, espaços, _ ou -.");
        return s;
    }
    private synchronized void handle(Peer p, Packet packet) throws IOException {
        if (p.name == null) {
            if (!packet.type().equals("HELLO")) throw new IOException("Identificação obrigatória.");
            String name = label(packet.sender());
            if (users.keySet().stream().anyMatch(n -> n.equalsIgnoreCase(name))) throw new IOException("Este nome já está em uso.");
            p.name = name; users.put(name, p); p.room = "geral";
            p.send(Packet.of("WELCOME", p.room, name, "Conectado"));
            roomList(); members(p.room); broadcast(Packet.of("SYSTEM", p.room, "Conecta", name + " entrou na sala."));
            return;
        }
        switch (packet.type()) {
            case "JOIN" -> {
                String room = label(packet.room()).toLowerCase(Locale.ROOT);
                if (!rooms.contains(room) && rooms.size() >= 50) throw new IOException("Limite de 50 salas atingido.");
                String old = p.room; rooms.add(room); p.room = room;
                p.send(Packet.of("JOINED", room, p.name, "")); roomList();
                members(old); members(room);
                if (!old.equals(room)) {
                    broadcast(Packet.of("SYSTEM", old, "Conecta", p.name + " saiu da sala."));
                    broadcast(Packet.of("SYSTEM", room, "Conecta", p.name + " entrou na sala."));
                }
            }
            case "TEXT", "FILE" -> {
                if (!packet.room().equals(p.room)) throw new IOException("Selecione a sala antes de enviar.");
                String text = packet.text();
                if (packet.type().equals("TEXT")) {
                    if (text.isBlank() || text.length() > 4000) throw new IOException("A mensagem deve ter de 1 a 4000 caracteres.");
                } else {
                    if (text.isBlank() || text.length() > 180 || text.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") || text.equals(".") || text.equals(".."))
                        throw new IOException("Nome de arquivo inválido.");
                }
                broadcast(new Packet(packet.type(), p.room, p.name, text, System.currentTimeMillis(), packet.data()));
            }
            default -> throw new IOException("Comando desconhecido.");
        }
    }
    private void broadcast(Packet packet) {
        for (Peer peer : List.copyOf(users.values())) if (packet.room().equals(peer.room)) peer.send(packet);
    }
    private void roomList() {
        Packet packet = Packet.of("ROOMS", "", "", String.join("\n", rooms));
        for (Peer peer : List.copyOf(users.values())) peer.send(packet);
    }
    private void members(String room) {
        broadcast(Packet.of("MEMBERS", room, "", String.join("\n", users.values().stream().filter(p -> room.equals(p.room)).map(p -> p.name).sorted().toList())));
    }
    private synchronized void remove(Peer p) {
        connections.remove(p);
        if (p.name != null && users.remove(p.name, p)) {
            members(p.room); broadcast(Packet.of("SYSTEM", p.room, "Conecta", p.name + " desconectou."));
        }
    }
    private final class Peer {
        final Socket socket;
        final BlockingQueue<Packet> outgoing = new ArrayBlockingQueue<>(16);
        volatile boolean open = true;
        String name, room;
        Thread writer;
        Peer(Socket socket) { this.socket = socket; }
        void send(Packet packet) { if (open && !outgoing.offer(packet)) close(); }
        void read() {
            try (socket) {
                socket.setTcpNoDelay(true); socket.setSoTimeout(15000);
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                try { handle(this, Packet.read(in)); }
                catch (IOException e) { Packet.of("ERROR", "", "", e.getMessage()).write(out); return; }
                socket.setSoTimeout(0);
                writer = Thread.ofVirtual().start(() -> {
                    try { while (open) outgoing.take().write(out); }
                    catch (IOException | InterruptedException ignored) { close(); }
                });
                while (open) {
                    Packet packet = Packet.read(in);
                    try { handle(this, packet); }
                    catch (IOException e) { send(Packet.of("ERROR", room, "", e.getMessage())); }
                }
            } catch (IOException ignored) { } finally { close(); }
        }
        void close() {
            open = false;
            try { socket.close(); } catch (IOException ignored) { }
            if (writer != null) writer.interrupt();
            remove(this);
        }
    }
    @Override public void close() throws IOException {
        running = false; listener.close();
        for (Peer p : List.copyOf(connections)) p.close();
    }
    public static void main(String[] args) throws Exception {
        String host = args.length > 1 ? args[1] : "0.0.0.0";
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5050;
        ChatServer server = new ChatServer(host, port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.close(); } catch (IOException ignored) { } }));
        System.out.println("Conecta ouvindo em " + host + ":" + server.port());
        new CountDownLatch(1).await();
    }
}
