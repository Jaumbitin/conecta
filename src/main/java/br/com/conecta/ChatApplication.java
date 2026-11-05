package br.com.conecta;

import br.com.conecta.client.ChatClient;
import br.com.conecta.net.Packet;
import br.com.conecta.server.ChatServer;
import javafx.application.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class ChatApplication extends Application {
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final ListView<String> rooms = new ListView<>(), members = new ListView<>();
    private final VBox feed = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(feed);
    private final Label status = new Label("Desconectado"), roomTitle = new Label("# geral"), identity = new Label("Seu espaço de conversa"), notice = new Label();
    private final TextField input = new TextField(), host = new TextField("localhost"), port = new TextField("5050"), name = new TextField();
    private final Button connect = new Button("Conectar"), attach = new Button("Anexar arquivo"), send = new Button("Enviar →"), newRoom = new Button("+ Nova sala"), serverButton = new Button("Iniciar servidor local");
    private Stage stage;
    private ChatClient client;
    private ChatServer localServer;
    private String activeRoom = "geral", username;
    private boolean updating;
    private final Set<Path> tempFiles = ConcurrentHashMap.newKeySet();

    @Override public void start(Stage stage) {
        this.stage = stage; stage.setTitle("Conecta • Conversas em rede");
        Label brand = new Label("◉  conecta"); brand.getStyleClass().add("brand");
        Label subtitle = new Label("PESSOAS. IDEIAS. CONEXÕES."); subtitle.getStyleClass().add("eyebrow");
        VBox logo = new VBox(8, brand, subtitle); logo.setPadding(new Insets(8, 0, 24, 0));
        Label roomLabel = new Label("SUAS SALAS"); roomLabel.getStyleClass().add("eyebrow");
        rooms.getItems().add("geral"); rooms.getSelectionModel().selectFirst();
        rooms.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(String s, boolean empty) { super.updateItem(s, empty); setText(empty ? null : "#  " + s); } });
        rooms.getSelectionModel().selectedItemProperty().addListener((o, old, room) -> {
            if (!updating && client != null && room != null && !room.equals(activeRoom)) {
                input.setDisable(true); send.setDisable(true); attach.setDisable(true);
                submit(Packet.of("JOIN", room, "", ""));
            }
        });
        newRoom.setMaxWidth(Double.MAX_VALUE); newRoom.setOnAction(e -> createRoom());
        Label tip = new Label("Um lugar para conversar\ne compartilhar com seu grupo."); tip.getStyleClass().add("muted");
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox sidebar = new VBox(16, logo, roomLabel, rooms, newRoom, spacer, identity, status, tip);
        sidebar.getStyleClass().add("sidebar"); sidebar.setPrefWidth(240); sidebar.setMinWidth(220); VBox.setVgrow(rooms, Priority.ALWAYS);

        roomTitle.getStyleClass().add("room-title");
        Label desc = new Label("Converse, troque ideias e compartilhe arquivos."); desc.getStyleClass().add("muted");
        VBox heading = new VBox(5, roomTitle, desc);
        HBox header = new HBox(heading); header.getStyleClass().add("chat-header");
        feed.setPadding(new Insets(26)); scroll.setFitToWidth(true); scroll.getStyleClass().add("feed");
        feed.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1));
        input.setPromptText("Escreva uma mensagem…"); input.setOnAction(e -> sendText()); HBox.setHgrow(input, Priority.ALWAYS);
        send.getStyleClass().add("primary"); send.setOnAction(e -> sendText()); attach.setOnAction(e -> sendFile());
        HBox composer = new HBox(10, attach, input, send); composer.setAlignment(Pos.CENTER);
        notice.getStyleClass().add("muted"); notice.setWrapText(true);
        VBox bottom = new VBox(10, notice, composer, new Label("Enter para enviar  •  Arquivos de até 10 MB  •  Salas públicas")); bottom.getStyleClass().add("composer");
        BorderPane chat = new BorderPane(scroll, header, null, bottom, null);
        Label people = new Label("NESTA SALA"); people.getStyleClass().add("eyebrow");
        members.setPlaceholder(new Label("Ninguém conectado"));
        VBox right = new VBox(18, people, members); right.getStyleClass().add("people"); right.setPrefWidth(190); VBox.setVgrow(members, Priority.ALWAYS);

        host.setPrefColumnCount(13); port.setPrefColumnCount(4); name.setPrefColumnCount(11); name.setPromptText("Seu nome");
        connect.getStyleClass().add("primary"); connect.setOnAction(e -> { if (client == null) connect(); else disconnect(); });
        serverButton.setOnAction(e -> toggleServer());
        HBox connection = new HBox(10, new Label("Servidor"), host, new Label("Porta"), port, name, connect, serverButton);
        connection.setAlignment(Pos.CENTER_LEFT); connection.getStyleClass().add("connection");
        BorderPane root = new BorderPane(chat, connection, right, null, sidebar);
        Scene scene = new Scene(root, 1190, 780);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/br/com/conecta/style.css")).toExternalForm());
        stage.setScene(scene); stage.setMinWidth(1050); stage.setMinHeight(650); stage.show();
        enabled(false); welcome();
    }
    private void welcome() {
        Label title = new Label("Boas conversas começam aqui."); title.getStyleClass().add("welcome-title");
        Label body = new Label("Escolha um nome e conecte-se ao servidor.\nPara testar neste computador, clique em Iniciar servidor local.\nDepois, abra outra instância do Conecta com um nome diferente."); body.setWrapText(true); body.getStyleClass().add("muted");
        VBox card = new VBox(14, new Label("BEM-VINDO AO CONECTA"), title, body); card.getStyleClass().add("welcome"); feed.getChildren().setAll(card);
    }
    private void enabled(boolean connected) {
        input.setDisable(!connected); send.setDisable(!connected); attach.setDisable(!connected); newRoom.setDisable(!connected); rooms.setDisable(!connected);
        host.setDisable(connected); port.setDisable(connected); name.setDisable(connected);
        connect.setText(connected ? "Desconectar" : "Conectar"); status.setText(connected ? "● Online" : "○ Desconectado");
    }
    private int selectedPort() { int p = Integer.parseInt(port.getText().strip()); if (p < 1 || p > 65535) throw new IllegalArgumentException("Porta deve estar entre 1 e 65535."); return p; }
    private void connect() {
        String n = name.getText().strip(), h = host.getText().strip(); final int p;
        try { p = selectedPort(); if (!n.matches("[\\p{L}\\p{N}_ -]{1,32}") || h.isEmpty()) throw new IllegalArgumentException("Informe servidor e nome de até 32 letras ou números."); }
        catch (IllegalArgumentException e) { error(e.getMessage()); return; }
        connect.setDisable(true); notice.setText("Conectando a " + h + "…");
        io.execute(() -> {
            try {
                ChatClient c = new ChatClient(h, p, n);
                Platform.runLater(() -> {
                    client = c; username = n; activeRoom = "geral"; feed.getChildren().clear(); roomTitle.setText("# geral"); identity.setText(n); enabled(true); connect.setDisable(false); notice.setText("Conectado a " + h + ":" + p);
                    c.listen(packet -> Platform.runLater(() -> { if (client == c) receive(packet); }), reason -> Platform.runLater(() -> { if (client == c) { disconnect(); error(reason); } }));
                });
            } catch (Exception e) { Platform.runLater(() -> { connect.setDisable(false); error("Não foi possível conectar: " + e.getMessage()); }); }
        });
    }
    private void disconnect() { if (client != null) client.close(); client = null; members.getItems().clear(); enabled(false); notice.setText("Desconectado. Você pode se conectar novamente."); }
    private void toggleServer() {
        try {
            if (localServer == null) { localServer = new ChatServer("0.0.0.0", selectedPort()); serverButton.setText("Parar servidor local"); notice.setText("Servidor local ativo na porta " + localServer.port()); }
            else { localServer.close(); localServer = null; serverButton.setText("Iniciar servidor local"); notice.setText("Servidor local encerrado."); }
        } catch (Exception e) { error("Servidor: " + e.getMessage()); }
    }
    private void createRoom() {
        TextInputDialog dialog = new TextInputDialog(); dialog.initOwner(stage); dialog.setTitle("Nova sala"); dialog.setHeaderText("Qual é o nome do grupo?"); dialog.setContentText("Nome da sala:");
        dialog.showAndWait().filter(s -> !s.isBlank()).ifPresent(s -> submit(Packet.of("JOIN", s, "", "")));
    }
    private void sendText() {
        String text = input.getText().strip(); if (text.isEmpty()) return;
        if (text.length() > 4000) { error("Limite de 4000 caracteres por mensagem."); return; }
        submit(Packet.of("TEXT", activeRoom, "", text)); input.clear();
    }
    private void submit(Packet packet) {
        ChatClient c = client; if (c == null) return;
        io.execute(() -> { try { c.send(packet); } catch (IOException e) { Platform.runLater(() -> { if (client == c) { disconnect(); error(e.getMessage()); } }); } });
    }
    private void sendFile() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Compartilhar arquivo na sala " + activeRoom); File file = chooser.showOpenDialog(stage); if (file == null) return;
        ChatClient c = client; String room = activeRoom; attach.setDisable(true); notice.setText("Preparando " + file.getName() + "…");
        io.execute(() -> {
            try (InputStream in = Files.newInputStream(file.toPath())) {
                byte[] bytes = in.readNBytes(Packet.MAX_FILE + 1);
                if (bytes.length > Packet.MAX_FILE) throw new IOException("O arquivo excede 10 MB.");
                c.send(new Packet("FILE", room, "", file.getName(), 0, bytes));
                Platform.runLater(() -> { if (client == c) notice.setText("Arquivo enviado para # " + room); });
            } catch (Exception e) { Platform.runLater(() -> error("Falha ao enviar: " + e.getMessage())); }
            finally { Platform.runLater(() -> attach.setDisable(client == null)); }
        });
    }
    private void receive(Packet p) {
        switch (p.type()) {
            case "ROOMS" -> { updating = true; rooms.getItems().setAll(p.text().split("\n")); rooms.getSelectionModel().select(activeRoom); updating = false; }
            case "JOINED" -> { activeRoom = p.room(); roomTitle.setText("# " + activeRoom); feed.getChildren().clear(); enabled(true); updating = true; rooms.getSelectionModel().select(activeRoom); updating = false; notice.setText("Você está em # " + activeRoom); }
            case "MEMBERS" -> { if (activeRoom.equals(p.room())) members.getItems().setAll(p.text().isEmpty() ? java.util.List.of() : Arrays.asList(p.text().split("\n"))); }
            case "ERROR" -> { error(p.text()); enabled(true); updating = true; rooms.getSelectionModel().select(activeRoom); updating = false; }
            case "TEXT", "FILE", "SYSTEM" -> { if (activeRoom.equals(p.room())) addMessage(p); }
        }
    }
    private void addMessage(Packet p) {
        if (p.type().equals("SYSTEM")) { Label l = new Label(p.text()); l.getStyleClass().add("system-message"); feed.getChildren().add(l); trim(); return; }
        String time = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(p.time()).atZone(ZoneId.systemDefault()));
        Label author = new Label(p.sender() + "   ·   " + time); author.getStyleClass().add("author");
        VBox bubble = new VBox(9, author); bubble.setMaxWidth(550); bubble.getStyleClass().add(p.sender().equals(username) ? "bubble-own" : "bubble");
        if (p.type().equals("TEXT")) { Label text = new Label(p.text()); text.setWrapText(true); text.setMaxWidth(510); bubble.getChildren().add(text); }
        else {
            Label file = new Label("▤  " + p.text() + "  ·  " + String.format(Locale.ROOT, "%.1f KB", p.data().length / 1024.0)); file.setWrapText(true);
            Button save = new Button("Preparando arquivo…"); save.setDisable(true); bubble.getChildren().addAll(file, save);
            io.execute(() -> {
                try {
                    Path temp = Files.createTempFile("conecta-", ".attachment"); tempFiles.add(temp); Files.write(temp, p.data());
                    Platform.runLater(() -> { save.setText("Salvar arquivo…"); save.setDisable(false); save.setOnAction(e -> saveFile(temp, p.text())); });
                } catch (IOException e) { Platform.runLater(() -> save.setText("Falha ao receber arquivo")); }
            });
        }
        HBox row = new HBox(bubble); row.setAlignment(p.sender().equals(username) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); feed.getChildren().add(row); trim();
    }
    private void trim() { if (feed.getChildren().size() > 200) feed.getChildren().remove(0); }
    private void saveFile(Path source, String filename) {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Salvar arquivo recebido"); chooser.setInitialFileName(filename); File dest = chooser.showSaveDialog(stage); if (dest == null) return;
        io.execute(() -> { try { Files.copy(source, dest.toPath(), StandardCopyOption.REPLACE_EXISTING); Platform.runLater(() -> notice.setText("Arquivo salvo em " + dest)); }
        catch (IOException e) { Platform.runLater(() -> error("Falha ao salvar: " + e.getMessage())); } });
    }
    private void error(String message) { notice.setText(message); }
    @Override public void stop() throws Exception {
        if (client != null) client.close(); if (localServer != null) localServer.close();
        io.shutdownNow(); io.awaitTermination(2, TimeUnit.SECONDS);
        for (Path temp : tempFiles) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
    }
    public static void main(String[] args) { launch(args); }
}
