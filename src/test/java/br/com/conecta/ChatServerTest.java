package br.com.conecta;

import br.com.conecta.net.Packet;
import br.com.conecta.server.ChatServer;
import br.com.conecta.client.ChatClient;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatServerTest {
    static final class User implements AutoCloseable {
        final Socket socket; final DataInputStream in; final DataOutputStream out;
        User(int port, String name) throws IOException {
            socket = new Socket("localhost", port); socket.setSoTimeout(2000);
            in = new DataInputStream(socket.getInputStream()); out = new DataOutputStream(socket.getOutputStream());
            send(Packet.of("HELLO", "", name, ""));
        }
        void send(Packet p) throws IOException { p.write(out); }
        Packet until(String type) throws IOException { for (int n = 0; n < 40; n++) { Packet p = Packet.read(in); if (p.type().equals(type)) return p; } throw new IOException("Evento não recebido: " + type); }
        public void close() throws IOException { socket.close(); }
    }
    @Test void groupChatFileIntegrityAndIsolation() throws Exception {
        try (ChatServer server = new ChatServer("127.0.0.1", 0);
             User ana = new User(server.port(), "Ana"); User bruno = new User(server.port(), "Bruno"); User carla = new User(server.port(), "Carla")) {
            assertEquals("WELCOME", ana.until("WELCOME").type()); bruno.until("WELCOME"); carla.until("WELCOME");
            ana.send(Packet.of("JOIN", "equipe", "", "")); ana.until("JOINED");
            bruno.send(Packet.of("JOIN", "equipe", "", "")); bruno.until("JOINED");
            ana.send(Packet.of("TEXT", "equipe", "impostor", "Olá, grupo!"));
            Packet a = ana.until("TEXT"), b = bruno.until("TEXT");
            assertEquals("Ana", b.sender()); assertEquals("Olá, grupo!", b.text()); assertEquals(a.time(), b.time());
            byte[] payload = new byte[100_000]; new java.util.Random(42).nextBytes(payload);
            ana.send(new Packet("FILE", "equipe", "", "documento.bin", 0, payload));
            assertArrayEquals(payload, bruno.until("FILE").data()); ana.until("FILE");
            carla.send(Packet.of("TEXT", "geral", "", "marcador"));
            assertEquals("marcador", carla.until("TEXT").text(), "Carla não deve receber texto/arquivo da equipe");
            ana.send(Packet.of("TEXT", "geral", "", "vazamento")); assertEquals("ERROR", ana.until("ERROR").type());
            ana.send(Packet.of("JOIN", "geral", "", "")); ana.until("JOINED");
            ana.send(Packet.of("TEXT", "geral", "", "Voltei")); assertEquals("Voltei", carla.until("TEXT").text());
        }
    }
    @Test void duplicateNameAndInvalidFileAreRejected() throws Exception {
        try (ChatServer server = new ChatServer("127.0.0.1", 0); User user = new User(server.port(), "Ana")) {
            user.until("WELCOME");
            IOException duplicate = assertThrows(IOException.class, () -> new ChatClient("localhost", server.port(), "ana"));
            assertTrue(duplicate.getMessage().contains("já está em uso"));
            user.send(new Packet("FILE", "geral", "", "../escape.txt", 0, new byte[0]));
            assertTrue(user.until("ERROR").text().contains("inválido"));
            user.send(Packet.of("TEXT", "geral", "", " ")); assertEquals("ERROR", user.until("ERROR").type());
        }
    }
    @Test void malformedLengthAndTruncatedFrameAreRejected() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(buffer);
        new Packet("FILE", "geral", "Ana", "x", 0, new byte[0]).write(out);
        byte[] frame = buffer.toByteArray(); Arrays.fill(frame, frame.length - 4, frame.length, (byte) 0x7f);
        assertThrows(IOException.class, () -> Packet.read(new DataInputStream(new ByteArrayInputStream(frame))));
        assertThrows(EOFException.class, () -> Packet.read(new DataInputStream(new ByteArrayInputStream(new byte[]{1, 2}))));
    }
    @Test void clientReceivesEventsAndDetectsServerShutdown() throws Exception {
        BlockingQueue<Packet> events = new LinkedBlockingQueue<>();
        CountDownLatch lost = new CountDownLatch(1);
        try (ChatServer server = new ChatServer("127.0.0.1", 0);
             ChatClient client = new ChatClient("127.0.0.1", server.port(), "Cliente")) {
            client.listen(events::add, reason -> lost.countDown());
            client.send(Packet.of("TEXT", "geral", "", "Olá por socket"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            Packet text = null;
            while (System.nanoTime() < deadline) {
                Packet p = events.poll(100, TimeUnit.MILLISECONDS);
                if (p != null && p.type().equals("TEXT")) { text = p; break; }
            }
            assertNotNull(text); assertEquals("Olá por socket", text.text());
            server.close(); assertTrue(lost.await(3, TimeUnit.SECONDS));
        }
    }
}
