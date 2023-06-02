Samay Amiga Virtual: aplicativo para Android de chatbot de saúde mental
=======================================================
# O que eu preciso?
* Uma [conta de desenvolvedor do Google Play](https://developer.android.com/distribute/console/) 
para publicar o aplicativo e gerenciar assinaturas

* Um [projeto Firebase](https://firebase.google.com/) para implantar as funções analíticas, de 
autenticação e dados

   * Observação: o projeto Firebase tem um projeto Google Cloud Console correspondente que será
     configurado para notificações do desenvolvedor em tempo real

* Um [projeto do Console do Google Cloud](https://developers.google.com/android-publisher/getting_started#linking_your_api_project)
  para usar as APIs do Google Play

   * Observação: geralmente é um projeto do Console do Google Cloud diferente daquele usado com o Firebase,
  mas nesse caso, usaremos o mesmo

* Uma [conta Qonversion](https://qonversion.io/) para lidar com o lado da transação financeira do aplicativo

* Uma [Conta OpenAi](https://platform.openai.com/) para que você possa ter acesso à API GPT e 
poder usar o serviço de chat

# Resumo: Em que ordem devo fazer as coisas?

1. Inicie a **Configuração do projeto do Firebase**

   * A configuração do Firebase requer que você saiba o nome do pacote Android

   * Você pode adicionar as informações de assinatura SHA1 ao console do Firebase posteriormente

2. Conclua a **Configuração do aplicativo Android**

   * O aplicativo Android não será compilado sem `google-services.json` do Firebase

3. Conclua a **configuração do Firebase Auth e Firestore**

   * O login só funcionará com esta etapa concluída

4. Conclua a **Configuração do aplicativo Qonversion**

   * O lado financeiro do aplicativo não funcionará sem ele

5. Conclua a **Configuração da API OpenAI**

   * Você precisará de uma conta OpenAI para poder usar as funções de bate-papo corretamente

6. **Carregue um APK de compilação de lançamento** assinado com chaves de lançamento para o Google Play

   * Para usar as APIs Qonversion, você precisa de um APK para publicar em uma faixa no Google Play
     (teste interno, teste fechado, teste aberto ou faixa de produção)

      * [https://support.google.com/googleplay/android-developer/answer/3131213](https://support.google.com/googleplay/android-developer/answer/3131213)

7. Conclua a **Configuração do projeto do Firebase**

   * Se você pulou a etapa de configuração do Firebase para a impressão digital SHA1, pode concluí-la
     passo agora reconstruindo com o `google-services.json` atualizado
   * Se você estiver usando o Google Play Signing, poderá obter o SHA1 no Google Play Console em
     **Configuração > Integridade do aplicativo**

8. Conclua a **Configuração do Play Developer Console**

   * Vincule seu projeto do Google Cloud Console à conta de desenvolvedor do Google Play

      * Observação: uma conta do Google Play Console só pode ser vinculada a um único Google Cloud Console
        projeto, então **recomendamos criar um projeto dedicado do Console do Google Cloud que seja
        compartilhado por todos os aplicativos em seu Google Play Console**

      * Configure uma conta de serviço no Google Cloud Console

9. Conclua a **Configuração das notificações do desenvolvedor em tempo real**

10. Conclua **Segurança de dados do Google Play**

11. Conclua a configuração do Firestore Cloud Functions

12. Adicione um link para seus termos de uso e privacidade
    * Substitua "PRIVACY_LINK_HERE" e "TERMS_OF_USE_LINK_HERE" com links para o seus termos

# Configuração do projeto Firebase

Este aplicativo usa Firebase (Performance, Crashlytics, Analytics, Auth, Firestore).

1. Acesse **Firebase Console** e crie um projeto Firebase

   * [https://console.firebase.google.com/](https://console.firebase.google.com/)

2. Configure o Firebase Performance

   * No Firebase Console, vá para a guia "Desempenho" e clique em "Iniciar".
   * Siga as instruções para adicionar o Firebase Performance SDK ao seu projeto Android.
   * Assim que o SDK for adicionado, você poderá começar a usar o Firebase Performance para monitorar 
   o desempenho do seu aplicativo. Você pode acompanhar a hora de início do aplicativo, 
   solicitações de rede e rastreamentos personalizados.

3. Configure o Firebase Crashlytics

   * No Firebase Console, vá para a guia "Crashlytics" e clique em "Começar".
   * Siga as instruções para adicionar o Firebase Crashlytics SDK ao seu projeto Android.
   * Assim que o SDK for adicionado, você poderá começar a usar o Firebase Crashlytics para 
   monitorar as falhas do seu aplicativo. O Crashlytics relatará automaticamente todas as 
   falhas que ocorrerem em seu aplicativo.

4. Configure o Firebase Analytics

   * No Firebase Console, vá para a guia "Analytics" e clique em "Get started".
   * Siga as instruções para adicionar o Firebase Analytics SDK ao seu projeto Android.
   * Assim que o SDK for adicionado, você poderá começar a usar o Firebase Analytics para 
   rastrear o comportamento do usuário em seu aplicativo. Você pode rastrear eventos, 
   propriedades do usuário e envolvimento do usuário.

5. Configure o Firebase Auth

   * No Firebase Console, vá para a guia "Authentication" e clique em "Configurar método de login".
   * Selecione os métodos de login que deseja permitir em seu aplicativo (por exemplo, email/senha, 
   Google, Facebook).
   * Siga as instruções para configurar cada método de login escolhido.
   * Ele irá criar um cliente Oauth. Vá para o console GCP --> Credenciais.
   * Copie e cole o ID do cliente e coloque-o em GOOGLE_SIGN_IN_REQUEST_ID_TOKEN em gradle.properties

6. Configure o Firestore

   * No Firebase Console, vá para a guia "Firestore" e clique em "Criar banco de dados".
   * Escolha a opção "Iniciar no modo de teste" e selecione a região mais próxima de você.
   * Defina as regras de segurança para o seu banco de dados e clique em "Concluir".

7. Verifique a configuração

   * Crie e execute seu aplicativo Android em um emulador ou dispositivo físico.
   * Execute algumas ações no aplicativo para gerar alguns dados.
   * Acesse o Console do Firebase e verifique se os dados estão sendo recebidos pelo Firebase.

8. Configure o Firebase Cloud Functions

   1. Se ainda não o fez, instale o Firebase CLI seguindo as instruções aqui:
      https://firebase.google.com/docs/cli#install_the_firebase_cli
   2. Execute `npm install` na pasta `functions` para instalar as novas dependências.
   3. Copie e cole seu service-account-key.json na pasta functions/keys e atualize index.js
   4. Implante suas funções no Firebase executando o seguinte comando na pasta `functions`:

   ```
   firebase deploy --only functions
   ```

Depois de concluir essas etapas, seu Firebase Cloud Functions será configurado para lidar 
com o resgate de código promocional. 
Isso vai atualizar o documento Firestore do usuário com o novo saldo de crédito quando o mesmo
inserir o código promocional

Para obter o arquivo de chave da conta de serviço (`service-account-key.json`), siga estas etapas:

1. Abra o [Console do Google Cloud](https://console.cloud.google.com/).
2. Selecione seu projeto no menu suspenso na parte superior da página.
3. Clique no menu hambúrguer (três linhas horizontais) no canto superior esquerdo e navegue
   para **IAM e administrador > Contas de serviço**.
4. Encontre a conta de serviço que deseja usar ou crie uma nova conta de serviço, se necessário.
   Para criar uma nova, clique no botão `+ CRIAR CONTA DE SERVIÇO` na parte superior da página,
   preencha as informações necessárias e conceda a ela as funções apropriadas (como
   "Usuário de gerenciamento do Android" e "Visualizador do projeto"). Em seguida, clique em `Concluído`.
5. Na lista de contas de serviço, clique no endereço de e-mail da conta de serviço que deseja usar.
6. Na página de detalhes da conta de serviço, clique na guia `Chaves`.
7. Clique no botão `Adicionar chave` e escolha `JSON` no menu suspenso.

O arquivo de chave JSON será gerado e baixado em seu computador. Isto é o
arquivo `service-account-key.json` que você precisa usar em seu Firebase Cloud Functions para validar a compra.

Importante: certifique-se de manter o arquivo `service-account-key.json` seguro e não o 
compartilhe publicamente ou incluí-lo em seu aplicativo Android. Armazene-o com segurança em seu 
servidor ou em seu Ambiente Firebase Cloud Functions.

Assim que tiver o arquivo `service-account-key.json`, substitua o
`"path/to/your/service-account-key.json"` no `validatePurchase` com o caminho real para o arquivo 
de chave JSON.

Se estiver usando o Firebase Cloud Functions, você pode armazenar o arquivo de chave JSON no
pasta `functions` e use um caminho relativo como `"./service-account-key.json"`.

# Configuração de aplicativo Android

Para usar o Oauth, você deve escolher um nome de pacote exclusivo e fazer upload de um
[APK de lançamento assinado](https://developer.android.com/studio/publish/app-signing.html) para o Google Play.
O aplicativo Android só será criado depois que você incluir um arquivo `google-services.json` compatível do
Configuração do Firebase.

## Crie seu APK no Android Studio

1. Abra o Android Studio, selecione File > Open e escolha o arquivo root build.gradle

   * `{project_folder}/build.gradle`

2. Altere o `androidApplicationId` para o nome correto do pacote Android

   * `{project_folder}/gradle.properties`

3. Confirme se você tem o `google-services.json` mais recente da configuração do Firebase em seu
   diretório de aplicativos

   * `{project_folder}/app/google-services.json`

4. Remova o arquivo de configuração google-services.json localizado na pasta de depuração do aplicativo.

   * `{project_folder}/app/src/debug/`

5. Recompile o projeto Gradle e verifique se não há erros

6. Assine uma compilação de liberação com sua chave de liberação

   * [https://developer.android.com/studio/publish/app-signing#sign-apk](https://developer.android.com/studio/publish/app-signing#sign-apk)

7. Se você pulou a etapa de configuração do Firebase para a impressão digital SHA1, pode concluí-la
   passo agora e reconstrua com o `google-services.json` atualizado

   * Se você estiver usando o Google Play Signing, poderá obter o SHA1 no Google Play Console em
     **Configuração > Integridade do aplicativo**

# Configuração do aplicativo Qonversion

1. Crie um projeto e registre seu aplicativo
2. Navegue até a página [Novo projeto](https://dash.qonversion.io/app/start/create) e 
forneça o nome do novo projeto. Um projeto em Qonversion oferece suporte a aplicativos iOS, Android e Web.
3. Em [Configurações](https://dash.qonversion.io/project/settings), selecione a plataforma para 
seu aplicativo e forneça os dados específicos da plataforma:
   Aplicativo para Android:
   Credenciais de chave de conta de serviço, você pode encontrar mais detalhes [aqui](https://documentation.qonversion.io/docs/service-account-key-android).
   nome do pacote Android
4. Obtenha a chave do projeto Qonversion. Você pode encontrar a chave do projeto Qonversion na seção 
Configurações do Qonversion. Você precisará desta chave para configurar os SDKs
5. Substitua QONVERSION_PROJECT_KEY em gradle.properties com a sua chave
6. Criar permissão: Navegue até a seção [Centro de produtos](https://dash.qonversion.io/app/product-center). 
Toque no botão Criar e selecione Permissão.
   * Preencha os detalhes da permissão:
   * Identificador – id de permissão único, usado pelo SDK para verificar se um usuário tem acesso premium.
   * Descrição – campo de descrição personalizado curto.
   * Produtos – lista de produtos que ativam a permissão multiplataforma.
7. Crie uma oferta: Navegue até as configurações do [Centro de produtos](https://dash.qonversion.io/app/product-center) em sua conta Qonversion. Toque no botão Criar e selecione Oferta.
   * Preencha os detalhes da oferta:
   * Identificador – ID de oferta exclusivo, usado pelo Qonversion SDK para obter produtos associados
   * Descrição – campo de descrição personalizado curto.
   * Anexe produtos à sua oferta
   * Produtos - lista de produtos que estão vinculados à oferta. A ordem dos produtos será utilizada 
   no SDK ao exibir os produtos.
8. Crie um Produto
   Navegue até a seção [Centro de produtos](https://dash.qonversion.io/app/product-center). 
Toque no botão Criar e selecione Produto.
   * Preencha os dados do produto:
   * ID do produto Qonversion - crie seu ID de produto exclusivo no Qonversion que corresponda a 
   um produto exclusivo na Apple App Store e na Google Play Store. Ele será usado pelo SDK para fazer compras.
   * Identificador de produto da Apple App Store – identificador de produto na Apple App Store. 
   Você pode ler aqui como criar uma assinatura auto-renovável
   * Identificador de produto do console do Google – identificador de produto no console do Google.
   Você pode ler aqui como criar uma assinatura.
   * Permissões associadas – escolha as permissões que serão desbloqueadas após a compra deste produto.
9. Adicione os produtos ao Google Play Developer Console: Selecione seu aplicativo na lista.
   * Selecione Produtos. Você pode selecionar produtos no aplicativo ou assinaturas.
   ## 1. Produtos no aplicativo
   1. Vamos escolher a guia de produtos no aplicativo. Clique no botão Criar produto. Você deve
   fornecer ID do produto, nome e descrição.
   2. Defina o preço na parte inferior da página e aplique as alterações.
   3. Por fim, verifique as configurações de impostos e conformidade e clique em Salvar.
   4. Depois de criar um produto, ele fica com o status Inativo. Clique no botão Ativar.
   ## 2. Assinaturas
   1. Vamos escolher a guia Assinaturas. Clique no botão Criar assinatura. Forneça o ID e o nome do produto.
   2. Agora a assinatura foi criada, mas ainda não está configurada. Há quatro etapas para configurá-lo. 
   Dois deles são opcionais.
   3. A primeira etapa é opcional. Você pode adicionar até quatro strings personalizadas explicando 
   o que os usuários obtêm quando se inscrevem.
   4. Para as próximas duas etapas, você deve criar planos básicos e ofertas, se necessário. 
   O plano básico contém informações básicas sobre a assinatura, como duração, preço, tipo de renovação, 
   período de carência etc.
   5. Para criar um plano básico, clique em Incluir um plano básico na lista de tarefas ou na 
   seção planos básicos e ofertas.
   6. Insira o identificador do plano básico e configure seu tipo de renovação com cobrança e 
   períodos de carência. Você também pode adicionar tags que são usadas para distinguir os 
   planos básicos do lado da API. Isso não é necessário com um plano básico por assinatura.
   7. O último passo é definir o preço. Navegue até a seção Preços e disponibilidade e 
   clique em Definir preços, selecione as regiões em que a assinatura estará disponível e 
   pressione Definir preço.
   8. Insira o preço e clique em Atualizar. Salvar alterações.
   9. Depois de criar um plano básico, ele tem um status de rascunho. Clique no botão Ativar 
   para disponibilizá-lo aos usuários.
   ## 3. Adicionar oferta
   1. O Qonversion ainda não oferece suporte a várias ofertas. Apenas um plano base compatível 
   com versões anteriores e uma oferta compatível com versões anteriores para esse plano básico 
   são suportados no momento.
   2. Clique em Incluir oferta para criar uma oferta.
   3. Selecione o plano básico ao qual a nova oferta pertencerá e clique em Adicionar oferta.
   4. Especifique o identificador da oferta e selecione os critérios de elegibilidade. Existem 
   várias opções disponíveis: usuários que nunca compraram esta ou qualquer outra assinatura; 
   aqueles que atualizaram de outras assinaturas; determinado pelo desenvolvedor.
      (Os critérios determinados pelo desenvolvedor são incompatíveis com versões anteriores e, 
   portanto, não podem ser usados com o Qonversion.)
   5. Você também pode adicionar tags como no plano básico.
   6. A etapa final são as fases. Você pode configurar até duas fases que serão utilizadas antes 
   da compra do plano básico. Por exemplo, você pode adicionar uma avaliação gratuita por uma 
   semana e um desconto de 10% na próxima semana antes que o usuário compre a assinatura original.
      Para criar uma fase, clique no botão Adicionar fase na seção Fases.
   7. Escolha o tipo de fase, a duração e, se escolher o tipo de desconto, os preços.
   8. Pressione Aplicar e Salvar para criar uma oferta. A oferta tem um status de rascunho. Pressione Ativar.
   9. Depois de ter feito todas as etapas acima, sua assinatura está pronta para uso. 
   Certifique-se de ter tags compatíveis com versões anteriores para o plano básico e a oferta, se houver.

# Configuração da API OpenAI

1. Crie uma conta em [OpenAI](https://platform.openai.com/)
2. Vá para Gerenciar conta --> Chaves de API --> Criar nova chave secreta
3. Copie e cole-o em gradle.properties, substituindo API_KEY por suas chaves de API
4. Caso não tenha gradle.properties, crie um e coloque esse campo nele: API_KEY=(API aqui)
5. Obtenha as informações mais recentes sobre a implementação Java/Kotlin da GPT [aqui](https://github.com/TheoKanning/openai-java)

# Play Configuração do console do desenvolvedor

1. Crie um aplicativo no Google Play Developer Console

   * [https://play.google.com/console](https://play.google.com/console)

2. Carregue e publique o APK de lançamento no teste interno, teste fechado, teste aberto ou faixa de produção

   * [https://support.google.com/googleplay/android-developer/answer/113469](https://support.google.com/googleplay/android-developer/answer/113469)

3. Vincule o projeto do Google Cloud Console à conta de desenvolvedor do Google Play

   * [https://developers.google.com/android-publisher/getting_started#linking_your_api_project](https://developers.google.com/android-publisher/getting_started#linking_your_api_project)

   * Use o projeto Google Cloud Console que corresponde à sua conta de desenvolvedor do Google Play

   * Habilite o acesso à API usando uma conta de serviço

      * [https://developers.google.com/android-publisher/getting_started#using_a_service_account](https://developers.google.com/android-publisher/getting_started#using_a_service_account)

   * Crie uma nova chave privada para sua conta de serviço e baixe-a no formato JSON com o
     nome do arquivo `service-account.json`

   * Copie `service-account.json` para a pasta ClassyTaxiServer

      * `{project_folder}/ClassyTaxiServer/src/service-account.json`

# Configuração de notificações do desenvolvedor em tempo real

1. Use o projeto Google Cloud Console que corresponde ao projeto Firebase para
   ativar [Cloud PubSub](https://console.cloud.google.com/cloudpubsub?tutorial=pubsub_quickstart&_ga=2.248010692.2130588993.1635461725-2065840594.1635461725)

   * [https://developer.android.com/google/play/billing/realtime_developer_notifications#initial_cloud_pubsub_setup](https://developer.android.com/google/play/billing/realtime_developer_notifications#initial_cloud_pubsub_setup)

   * Nomeie seu tópico do PubSub: `play-subs`

   * Adicione a conta do serviço Google Play ao tópico

      * `google-play-developer-notifications@system.gserviceaccount.com`

   * Na seção Permissões, selecione **Adicionar membro** e conceda a função **Pub/Sub Publisher** para
     a conta de serviço do Google Play

   * Observação: certifique-se de definir a função como **Editor** para que o Google Play possa 
   publicar atualizações para o seu projeto

   * Copie o **Nome do tópico** para poder adicioná-lo ao Google Play Console

# Google Play Data Safety

Você precisa fornecer ao Google as informações sobre como seu aplicativo coleta e
lida com os dados do usuário, incluindo todas as bibliotecas que você usa no aplicativo.
Abaixo está a lista dos tipos de dados que o Google exige para relatar.
Indicamos se este aplicativo coleta algum tipo específico de dados.

Detalhes do tipo de dados
* Local --> Não coletado
* Saúde e condicionamento físico --> Não coletado
* Fotos e Vídeos --> Foto coletada para a página de perfil
* Arquivos e documentos --> não coletados
* Calendário --> não coletado
* Contactos --> Não recolhidos
* Conteúdo do usuário --> Outro
* Histórico de navegação --> não coletado
* Histórico de pesquisa --> não coletado
* Informações e desempenho do aplicativo --> coletados pelo Firebase
* Navegação na Web --> Não coletado
* Informações de contato --> O nome de usuário da loja de aplicativos, e-mail e outras informações de contato.
* Informações financeiras --> Qonversion coleta o histórico de compras da App Store
* Informações pessoais e identificadores --> Qonversion coleta ID do usuário e ID do dispositivo.
* Outros dados --> Qonversion coleta dados sobre o sistema operacional, marca, modelo e resolução do dispositivo.