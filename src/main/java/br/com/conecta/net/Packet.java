package br.com.conecta.net;

import java.io.*;

/** Protocolo binário versionado. Nunca desserializa objetos enviados pela rede. */
public record Packet(String type, String room, String sender, String text, long time, byte[] data) {
    public static final int MAX_FILE = 10 * 1024 * 1024;
    private static final int MAGIC = 0x434E4301;
    public static Packet of(String type, String room, String sender, String text) {
        return new Packet(type, room, sender, text, System.currentTimeMillis(), new byte[0]);
    }
    public void write(DataOutputStream out) throws IOException {
        if (data.length > MAX_FILE) throw new IOException("Arquivo excede 10 MB.");
        out.writeInt(MAGIC); out.writeUTF(type); out.writeUTF(room); out.writeUTF(sender);
        out.writeUTF(text); out.writeLong(time); out.writeInt(data.length); out.write(data); out.flush();
    }
    public static Packet read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Protocolo incompatível.");
        String type = in.readUTF(), room = in.readUTF(), sender = in.readUTF(), text = in.readUTF();
        long time = in.readLong(); int size = in.readInt();
        if (size < 0 || size > MAX_FILE || (!type.equals("FILE") && size != 0))
            throw new IOException("Tamanho de pacote inválido.");
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return new Packet(type, room, sender, text, time, bytes);
    }
}
