# Conecta

Aplicativo desktop de bate-papo em **Java 21 + JavaFX**, com servidor TCP por sockets, salas de conversa em grupo e compartilhamento de arquivos de até 10 MB. Interface em português, tema escuro e operações de rede fora da thread gráfica.

## Executar no IntelliJ IDEA

1. Abra esta pasta como projeto Maven e aguarde a importação do `pom.xml`.
2. Selecione um JDK **21** como SDK do projeto e do Maven Runner.
3. Execute a configuração **Conecta — Cliente**, ou o objetivo Maven `compile javafx:run`.
4. Na tela, clique em **Iniciar servidor local**. Informe seu nome e clique em **Conectar**.
5. Execute outra instância do cliente, escolha outro nome e conecte-se a `localhost:5050`.
6. Use **+ Nova sala** para criar/entrar em um grupo. Os demais participantes selecionam a sala na lateral. Cada pessoa participa de uma sala por vez.
7. Use **Anexar arquivo** para compartilhar; os participantes presentes recebem um botão **Salvar arquivo…**. Os bytes são transmitidos pelo socket, e não por um link externo.

## Linha de comando

Com Maven instalado e JDK 21:

```text
mvn clean verify
mvn compile javafx:run
```

Servidor independente, sem interface (primeiro execute `mvn compile`):

```text
java -cp target/classes br.com.conecta.server.ChatServer 5050
```

O segundo argumento opcional define o endereço de bind; por exemplo, `5050 127.0.0.1` para aceitar somente conexões locais. A configuração **Conecta — Servidor** do IntelliJ também inicia o servidor independente.

## Usar em rede local

Inicie somente um servidor em um computador. Nos clientes, informe o IP desse computador e a mesma porta (padrão 5050). O servidor escuta em `0.0.0.0`; a conectividade depende da rede e da liberação da porta no firewall feita pelo responsável. `localhost` funciona somente no mesmo computador.

## Organização

- `ChatApplication`: interface JavaFX e ações de conexão, salas e arquivos.
- `client/ChatClient`: conexão TCP e leitura de eventos em thread virtual.
- `server/ChatServer`: participantes, validações e distribuição por sala, com fila de saída por cliente.
- `net/Packet`: protocolo binário versionado com tamanho validado, sem serialização de objetos Java.
- `src/test`: testes reais de sockets, isolamento entre salas, integridade de arquivo, validação e quadros inválidos.

## Escopo desta versão

As salas são públicas, sem contas ou senhas. A conexão TCP não possui TLS: esta versão é destinada a estudos e redes confiáveis, sem exposição direta à internet. Mensagens e salas não são persistidas; quem entra recebe somente os novos eventos. A tela mostra os últimos 200 eventos da sala ativa e é limpa ao trocar de sala. Os arquivos recebidos ficam temporariamente em disco até fechar o aplicativo; salvar uma cópia exige escolher um destino. Encerrar o servidor desconecta os clientes, que podem se reconectar manualmente.

Limites: 100 conexões, 50 salas por servidor, nomes de até 32 caracteres, mensagens de até 4000 caracteres e arquivos de até 10 MB. O arquivo completo é mantido em memória durante a transmissão; clientes lentos com a fila de saída cheia são desconectados. Arquivos temporários ocupam disco até encerrar o aplicativo. Não há histórico offline, transferência retomável ou conversas privadas nesta versão.

A configuração Maven do JavaFX segue a [documentação oficial do OpenJFX](https://openjfx.io/openjfx-docs/).
