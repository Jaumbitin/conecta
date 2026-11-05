# Verificação da primeira versão

Verificado em 5 de novembro de 2026, Windows, JDK Eclipse Temurin 21.0.11, Maven integrado ao IntelliJ.

`mvn verify`: **BUILD SUCCESS**, 4 testes, nenhuma falha ou erro.

Os testes abrem sockets TCP reais em loopback e porta dinâmica, cobrindo:

- Conversa em grupo com dois clientes e remetente definido pelo servidor.
- Transferência binária de 100.000 bytes, comparando os bytes recebidos.
- Isolamento de mensagens e arquivos entre salas e troca de sala.
- Nome duplicado, nome de arquivo com caminho e mensagem vazia rejeitados.
- Quadro com tamanho inválido e quadro truncado rejeitados.
- Recepção de eventos pelo `ChatClient` e notificação quando o servidor encerra.

Conferência visual: a janela JavaFX abriu com tema escuro, tela de boas-vindas, formulário de conexão, lista de salas, área de conversa e lista de participantes. O servidor foi iniciado pelo botão da interface e o cliente conectou em `localhost:5050`, exibindo o participante e o estado Online.

O projeto foi aberto no IntelliJ, com SDK `temurin-21` e nível de linguagem Java 21. A configuração **Conecta — Cliente** foi selecionada e executada pelo IntelliJ, abrindo uma segunda janela do aplicativo.

Ainda não foi validada a comunicação entre computadores físicos diferentes. O envio e a integridade de arquivos foram validados por testes de rede automatizados; os diálogos nativos de escolher/salvar arquivo não foram percorridos manualmente.
