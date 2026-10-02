# OAuth API - Spring Security + AWS Cognito

## 📋 Sobre o Projeto

Este projeto é uma API REST desenvolvida em **Spring Boot** com **Spring Security** atuando como **OAuth2 Resource Server**, delegando toda a gestão de identidade ao **AWS Cognito**. O projeto implementa cadastro com confirmação de e-mail, login com e-mail e senha, renovação e revogação de tokens e **login federado** (Microsoft, Google, Facebook, Apple e Amazon, via Cognito Hosted UI), mantendo uma cópia local dos usuários no **PostgreSQL** para relacionamento com roles e regras de negócio.

### 🎯 Objetivo

Validar e servir de referência para uma arquitetura de autenticação com:
- Identidade gerenciada pelo AWS Cognito (senhas nunca passam pelo banco local)
- Validação de JWT emitido pelo Cognito (assinatura, issuer, `token_use` e `client_id`)
- Revogação efetiva de tokens no logout (Global Sign-Out)
- Login social com provedores cadastrados no banco (Microsoft, Google, Facebook, Apple e Amazon) via Authorization Code Flow
- Gerenciamento local de usuários e roles
- Tratamento de exceções global
- Validação de dados
- Logging com mascaramento de dados sensíveis
- Testes unitários com JUnit 5 e Mockito
- Migrações de banco de dados com Flyway
- Ambiente de desenvolvimento com Dev Container

## 🛠️ Tecnologias e Dependências

### Stack Principal

- **Java 21** - Linguagem de programação
- **Spring Boot 4.1.1** - Framework principal
- **Spring Security** - Framework de segurança
- **Spring Security OAuth2 Resource Server** - Validação de JWT emitidos pelo Cognito
- **AWS SDK for Java 2 (2.55.4)** - Integração com o Cognito Identity Provider
- **AWS Cognito** - Provedor de identidade (User Pool + Hosted UI)
- **Spring Data JPA** - Abstração de acesso a dados
- **Hibernate** - ORM (Object-Relational Mapping)
- **PostgreSQL 15** - Banco de dados relacional
- **Flyway** - Controle de versão de banco de dados
- **Lombok** - Redução de boilerplate
- **Bean Validation** - Validação de dados
- **Spotless (Google Java Format)** - Formatação de código

### Dependências de Produção

```gradle
// Spring Boot Starters
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-flyway'
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-resource-server'
implementation 'org.springframework.boot:spring-boot-starter-validation'

// AWS SDK
implementation platform('software.amazon.awssdk:bom:2.55.4')
implementation 'software.amazon.awssdk:apache5-client'
implementation 'software.amazon.awssdk:cognitoidentityprovider'
implementation 'software.amazon.awssdk:cognitoidentity'
implementation 'software.amazon.awssdk:cognitosync'

// Database
implementation 'org.flywaydb:flyway-database-postgresql'
runtimeOnly 'org.postgresql:postgresql'

// Lombok
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'

// Development
developmentOnly 'org.springframework.boot:spring-boot-devtools'
```

### Dependências de Teste

```gradle
testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
testImplementation 'org.springframework.boot:spring-boot-starter-flyway-test'
testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
testCompileOnly 'org.projectlombok:lombok'
testAnnotationProcessor 'org.projectlombok:lombok'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

## 📁 Estrutura do Projeto

```
src/main/java/com/dvlprmatheus/oauth/
├── OAuthApplication.java
├── api/
│   ├── controller/                  # Controllers REST
│   │   ├── AuthController.java
│   │   ├── LinkController.java
│   │   ├── OAuthController.java
│   │   ├── SsoProviderController.java
│   │   └── UserController.java
│   ├── exception/                   # Exceções customizadas
│   │   ├── AccountLinkException.java
│   │   ├── AuthenticationFailedException.java
│   │   ├── EmailConfirmationException.java
│   │   ├── SsoProviderNotFoundException.java
│   │   ├── UserAlreadyExistsException.java
│   │   ├── UserCreationException.java
│   │   ├── UserNotExistsException.java
│   │   └── handler/
│   │       └── GlobalExceptionHandler.java
│   ├── request/                     # DTOs de requisição
│   │   ├── AuthRequest.java
│   │   ├── AuthorizeRequest.java
│   │   ├── ConfirmEmailRequest.java
│   │   ├── CreateLocalLoginRequest.java
│   │   ├── CreateSsoProviderRequest.java
│   │   ├── RefreshRequest.java
│   │   ├── RegisterRequest.java
│   │   └── ResendCodeRequest.java
│   └── response/                    # DTOs de resposta
│       ├── AuthResponse.java
│       ├── AuthorizationUrlResponse.java
│       ├── ErrorResponse.java
│       ├── LinkStateResponse.java
│       └── UserResponse.java
├── config/
│   ├── aws/                         # Cliente do Cognito (AWS SDK)
│   │   └── AwsConfig.java
│   ├── properties/                  # Propriedades tipadas do Cognito
│   │   └── CognitoProperties.java
│   └── security/                    # Configurações de segurança
│       ├── CognitoAuthenticationToken.java
│       ├── CognitoJwtAuthenticationConverter.java
│       └── SecurityConfig.java
├── entity/                          # Entidades JPA
│   ├── AbstractEntity.java
│   ├── Role.java
│   ├── SsoProvider.java
│   ├── User.java
│   └── enums/
│       └── SsoProviderType.java
├── repository/                      # Repositórios Spring Data JPA
│   ├── RoleRepository.java
│   ├── SsoProviderRepository.java
│   └── UserRepository.java
├── service/                         # Camada de serviços
│   ├── AuthenticationService.java
│   ├── LinkService.java
│   ├── OAuthService.java
│   ├── OAuthStateService.java
│   ├── SsoProviderService.java
│   ├── UserService.java
│   └── aws/                         # Integração com o Cognito
│       ├── CognitoOAuthService.java
│       ├── CognitoService.java
│       └── model/
│           ├── CognitoIdentity.java
│           ├── CognitoTokenResponse.java
│           ├── CognitoUser.java
│           └── CognitoUserInfo.java
└── util/                            # Utilitários
    └── LogSanitizer.java

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_initial_tables.sql
    ├── V2__create_sso_providers_table.sql
    └── V3__allow_null_cognito_sub.sql

.devcontainer/                       # Ambiente de desenvolvimento (Java 21 + PostgreSQL)
├── .env.example
├── devcontainer.json
├── docker-compose.yml
└── Dockerfile
```

## 🔐 Funcionalidades de Segurança

### Autenticação via AWS Cognito

- **Cadastro no Cognito**: O usuário é criado no User Pool (`SignUp`) usando o e-mail como username
- **Confirmação de E-mail**: Código de 6 dígitos enviado pelo Cognito, com opção de reenvio
- **Login com Senha**: Fluxo `USER_PASSWORD_AUTH` retornando `accessToken`, `idToken` e `refreshToken`
- **Refresh de Tokens**: Fluxo `REFRESH_TOKEN_AUTH`, mantendo o refresh token original quando o Cognito não o rotaciona
- **Logout com Revogação**: `GlobalSignOut` invalida todos os tokens do usuário no Cognito
- **Secret Hash**: Todas as chamadas ao Cognito enviam o `SECRET_HASH` (HMAC-SHA256 com o client secret)

### Login Federado

- **Provedor cadastrado no banco**: `POST /oauth2/authorize` recebe um `SsoProviderType` e só gera a URL se esse tipo existir em `sso_providers`
- **Authorization Code Flow**: A resposta traz a URL do Cognito Hosted UI com o `identity_provider` cadastrado. O cliente redireciona o navegador
- **Troca de Código**: O backend troca o `code` por tokens no endpoint `/oauth2/token` do Cognito (Basic Auth com client id/secret)
- **Provisionamento Automático**: No primeiro login, o usuário é criado no banco local a partir do `userInfo` do Cognito
- **Proteção contra Conflito de Contas**: Um e-mail já cadastrado com senha não pode entrar por um provedor externo até que as contas sejam vinculadas
- **Vínculo de contas**: Quem entrou pelo provedor pode criar senha local; quem entrou com senha pode vincular um provedor. O `state` do vínculo é assinado com HMAC-SHA256 e não é gravado no banco

### Vínculo de Contas

- **Login local a partir do provedor**: Cria no Cognito um usuário nativo com e-mail já verificado e senha permanente, remove o usuário federado, vincula a identidade com `AdminLinkProviderForUser` e troca o `cognitoSub` local
- **Provedor a partir do login local**: Exige um usuário nativo já existente. O callback com `state` não autentica; ele vincula as contas somente se o e-mail do provedor for o mesmo do usuário

### Validação de Tokens (Resource Server)

Toda requisição a uma rota protegida passa pelas seguintes verificações:

1. **Assinatura**: Validada com as chaves públicas do JWKS do User Pool
2. **Issuer**: Deve ser `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>`
3. **Tipo do Token**: Claim `token_use` deve ser `access`
4. **Client**: Claim `client_id` deve ser o app client configurado
5. **Revogação**: O token é consultado no Cognito (`GetUser`); tokens revogados por logout são rejeitados
6. **Usuário Local**: O `sub` do token deve existir na tabela `users`

Se o Cognito estiver indisponível durante a verificação de revogação, a requisição é **negada** (fail closed).

### Gerenciamento de Usuários

- **Registro de Usuários**: Cria o usuário no Cognito e depois no banco local
- **Rollback de Cadastro**: Se a gravação no banco falhar, o usuário é removido do Cognito (`AdminDeleteUser`)
- **Prevenção de Duplicatas**: Verificação de username e e-mail únicos antes do cadastro
- **Usuário Atual**: Endpoint que retorna os dados do usuário autenticado

### Autorização Baseada em Roles

- **Sistema de Roles**: Suporte a múltiplas roles por usuário
- **Many-to-Many**: Relacionamento flexível entre usuários e roles
- **Authorities**: Roles convertidas em `ROLE_<nome>` para uso com Spring Security

## 🔧 Configurações

### Application Properties

O projeto utiliza `application.yml` com as seguintes configurações principais:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:db}:5432/${POSTGRES_DB:oauth_db}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
    hikari:
      maximum-pool-size: 10

  jpa:
    hibernate:
      ddl-auto: none  # Flyway gerencia o schema
    show-sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true

aws:
  cognito:
    region: ${AWS_REGION}
    user-pool-id: ${AWS_COGNITO_USER_POOL_ID}
    client-id: ${AWS_COGNITO_CLIENT_ID}
    client-secret: ${AWS_COGNITO_CLIENT_SECRET}
    domain: ${AWS_COGNITO_DOMAIN}
    redirect-uri: ${AWS_COGNITO_REDIRECT_URI:http://localhost:8080/oauth2/callback}
```

### Variáveis de Ambiente

O modelo completo está em `.devcontainer/.env.example`.

**Banco de dados**

- `POSTGRES_HOST`: Host do PostgreSQL (padrão: `db`)
- `POSTGRES_DB`: Nome do banco de dados (padrão: `oauth_db`)
- `POSTGRES_USER`: Usuário do banco (padrão: `postgres`)
- `POSTGRES_PASSWORD`: Senha do banco (padrão: `postgres`)

**AWS Cognito** (obrigatórias)

- `AWS_REGION`: Região do User Pool (ex.: `us-east-1`)
- `AWS_COGNITO_USER_POOL_ID`: ID do User Pool (ex.: `us-east-1_AbCdEfGhI`)
- `AWS_COGNITO_CLIENT_ID`: ID do app client
- `AWS_COGNITO_CLIENT_SECRET`: Secret do app client
- `AWS_COGNITO_DOMAIN`: URL completa do domínio do Hosted UI (ex.: `https://meu-app.auth.us-east-1.amazoncognito.com`)
- `AWS_COGNITO_REDIRECT_URI`: URL de callback do login federado (padrão: `http://localhost:8080/oauth2/callback`)

O nome de cada identity provider **não** fica em variável de ambiente. Ele é cadastrado na tabela `sso_providers` por `POST /v1/sso-providers` (rota autenticada).

**Credenciais AWS**

- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`: Credenciais IAM para `AdminDeleteUser`, `AdminCreateUser`, `AdminSetUserPassword`, `AdminGetUser` e `AdminLinkProviderForUser`

### Configuração do AWS Cognito

Para o projeto funcionar, o User Pool precisa estar configurado com:

- **Login por e-mail**: E-mail como atributo de login do usuário
- **App client com secret**, com os fluxos `ALLOW_USER_PASSWORD_AUTH` e `ALLOW_REFRESH_TOKEN_AUTH` habilitados
- **Domínio do Hosted UI** (Cognito domain ou domínio customizado)
- **Identity providers** (Microsoft, Google, Facebook, Apple, Amazon ou o subconjunto usado) com mapeamento do atributo `email`
- **OAuth no app client**:
  - Grant type: `Authorization code grant`
  - Scopes: `openid`, `email`, `profile` e `aws.cognito.signin.user.admin`
  - Callback URL: o mesmo valor de `AWS_COGNITO_REDIRECT_URI` (`/oauth2/callback`)

O scope `aws.cognito.signin.user.admin` é necessário para que os tokens obtidos via provedor externo possam ser usados em `GetUser` (verificação de revogação e leitura das identidades) e `GlobalSignOut` (logout).

### CORS

As origens permitidas estão definidas em `SecurityConfig`:

- `http://localhost:4200`
- `http://localhost:3000`

## 🧪 Testes

### Estrutura de Testes

O projeto possui uma suíte de testes utilizando **JUnit 5**, **Mockito**, **MockMvc** (standalone) e **MockRestServiceServer**:

#### Testes de Serviço

- **AuthenticationServiceTest**: Testa a lógica de negócio de autenticação, organizada em classes `@Nested`
  - `Register`: criação no Cognito e no banco, duplicatas e rollback no Cognito quando o banco falha
  - `ConfirmEmail`: confirmação, código inválido, código expirado e e-mail já confirmado
  - `ResendConfirmationCode`: reenvio, e-mail já confirmado e limite de tentativas
  - `Authenticate`: credenciais válidas/inválidas, usuário não confirmado e challenges adicionais
  - `Refresh`: refresh válido, refresh token inválido e manutenção do refresh token original
  - `Logout`: Global Sign-Out e token já revogado

- **OAuthServiceTest**: Testa o login federado
  - URL de autorização a partir do provedor cadastrado
  - Provedor não cadastrado
  - Erro retornado pelo provedor e ausência de código
  - Código rejeitado e Cognito indisponível
  - Criação do usuário federado no primeiro login
  - Provedor sem e-mail e e-mail já cadastrado com senha

- **LinkServiceTest**: Criação de login local e vínculo de provedor externo, inclusive e-mail divergente

- **OAuthStateServiceTest**: Assinatura HMAC-SHA256 do `state`, adulteração e expiração

- **SsoProviderServiceTest**: Cadastro, duplicata, remoção, provedor encontrado e provedor ausente

- **UserServiceTest**: Busca do usuário atual, por id, por e-mail e por Cognito sub

- **CognitoServiceTest**: Testa as chamadas ao AWS SDK
  - Uso do e-mail como username e cálculo do `SECRET_HASH`
  - Fluxos `USER_PASSWORD_AUTH` e `REFRESH_TOKEN_AUTH`
  - `GetUser`, `GlobalSignOut` e `AdminDeleteUser`
  - Criação de usuário confirmado, senha permanente, busca administrativa e `AdminLinkProviderForUser`

- **CognitoOAuthServiceTest**: Testa as chamadas HTTP ao Hosted UI
  - URL de autorização com e sem `state`, troca de código com Basic Auth e `userInfo`

#### Testes de Segurança e Configuração

- **CognitoJwtAuthenticationConverterTest**: Conversão do JWT em autenticação
  - Token ativo com roles
  - Token revogado por logout
  - Cognito indisponível (fail closed)
  - Usuário inexistente no banco

- **CognitoPropertiesTest**: Montagem das URIs de issuer e JWKS

#### Testes de API

- **AuthControllerTest**: Testa os endpoints de autenticação
  - POST `/auth/register`, `/auth/confirm-email`, `/auth/resend-confirmation-code`
  - POST `/auth/login`, `/auth/refresh`, `/auth/logout`
  - Validação de requisições e tratamento de erros

- **OAuthControllerTest**: URL de autorização e callback de login ou de vínculo
- **LinkControllerTest**: Criação de senha local e URL para vincular um provedor
- **SsoProviderControllerTest**: Cadastro e remoção de um identity provider

- **GlobalExceptionHandlerTest**: Mapeamento de cada exceção para o status HTTP correto

- **UserResponseTest**: Mapeamento das roles e garantia de que o `cognitoSub` não é exposto

- **LogSanitizerTest**: Mascaramento de e-mails e valores, e prevenção de log injection

- **OAuthApplicationTests**: Teste de carregamento do contexto Spring (requer banco e variáveis do Cognito)

### Executando os Testes

```bash
# Executar todos os testes
./gradlew test

# Executar testes com relatório
./gradlew test --info

# Executar apenas testes de serviço
./gradlew test --tests "*ServiceTest"

# Executar apenas testes de controllers
./gradlew test --tests "*ControllerTest"

# Verificar e aplicar formatação de código
./gradlew spotlessCheck
./gradlew spotlessApply
```

O relatório HTML fica disponível em `build/reports/tests/test/index.html`.

## 📡 Endpoints da API

### Autenticação (Públicos)

#### POST `/auth/register`
Registra um novo usuário no Cognito e no banco local. O Cognito envia um código de confirmação por e-mail.

**Request Body:**
```json
{
  "username": "usuario123",
  "email": "usuario@example.com",
  "password": "Senha@123"
}
```

**Validações:** `username` entre 3 e 32 caracteres, `email` válido, `password` entre 8 e 32 caracteres (e compatível com a política de senha do User Pool).

**Response:** `204 No Content`

#### POST `/auth/confirm-email`
Confirma o e-mail do usuário com o código recebido.

**Request Body:**
```json
{
  "email": "usuario@example.com",
  "code": "123456"
}
```

**Response:** `204 No Content`

#### POST `/auth/resend-confirmation-code`
Reenvia o código de confirmação de e-mail.

**Request Body:**
```json
{
  "email": "usuario@example.com"
}
```

**Response:** `204 No Content`

#### POST `/auth/login`
Autentica um usuário com e-mail e senha e retorna os tokens do Cognito.

**Request Body:**
```json
{
  "email": "usuario@example.com",
  "password": "Senha@123"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJraWQiOiJ...",
  "idToken": "eyJraWQiOiJ...",
  "refreshToken": "eyJjdHkiOiJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

#### POST `/auth/refresh`
Gera novos tokens a partir do refresh token.

**Request Body:**
```json
{
  "email": "usuario@example.com",
  "refreshToken": "eyJjdHkiOiJ..."
}
```

**Response (200 OK):** mesmo formato do login. Se o Cognito não rotacionar o refresh token, o original é devolvido.

### Login Federado (Público)

#### POST `/oauth2/authorize`
Gera a URL do Cognito Hosted UI para o provedor informado. O provedor precisa estar cadastrado em `sso_providers`. A API não redireciona: devolve a URL para o cliente abrir.

**Request Body:**
```json
{
  "provider": "MICROSOFT"
}
```

Valores aceitos: `MICROSOFT`, `GOOGLE`, `FACEBOOK`, `APPLE`, `AMAZON`.

**Response (200 OK):**
```json
{
  "authorizationUrl": "https://meu-app.auth.us-east-1.amazoncognito.com/oauth2/authorize?..."
}
```

#### GET `/oauth2/callback`
URL de retorno configurada no Cognito.

Sem `state`, troca o código por tokens e provisiona o usuário no banco no primeiro acesso.

Com `state`, não faz login: vincula o provedor externo à conta nativa indicada no state. O state é assinado com HMAC-SHA256 (client secret), vale 10 minutos e não é persistido.

**Query Params:**
- `code`: Código de autorização
- `state`: Presente apenas no fluxo de vínculo
- `error` / `error_description`: Preenchidos pelo provedor em caso de falha

**Response:** `200 OK` com os tokens no login, ou `204 No Content` no vínculo.

### Fluxo do Login Federado

```
Cliente            API                     Cognito Hosted UI         Provedor
  │ POST /oauth2/authorize │                        │                    │
  │───────────────────────>│                        │                    │
  │ 200 { authorizationUrl }│                       │                    │
  │<───────────────────────│                        │                    │
  │─────────────────────────────────────────────────>│  redireciona      │
  │                         │                        │───────────────────>│
  │                         │                        │<───────────────────│
  │  302 /oauth2/callback?code=...                   │                    │
  │<─────────────────────────────────────────────────│                    │
  │  GET callback?code=...  │                        │                    │
  │────────────────────────>│  POST /oauth2/token    │                    │
  │                         │───────────────────────>│                    │
  │                         │  GET /oauth2/userInfo  │                    │
  │                         │───────────────────────>│                    │
  │  200 { tokens }         │                        │                    │
  │<────────────────────────│                        │                    │
```

### Vínculo de Contas (Protegido)

Exige `Authorization: Bearer <accessToken>`.

#### POST `/v1/link/local`
Para quem entrou primeiro por um provedor externo. Cria a senha local, confirma o e-mail no Cognito e passa a permitir os dois acessos.

**Request Body:**
```json
{
  "password": "Senha@123"
}
```

**Response:** `204 No Content`

#### GET `/v1/link/{provider}`
Para quem entrou primeiro com e-mail e senha. Devolve a URL de autorização com `state` assinado. O callback desse fluxo vincula as contas e não devolve tokens.

**Response (200 OK):** mesmo formato de `POST /oauth2/authorize`.

### Provedores SSO (Protegido)

Exige `Authorization: Bearer <accessToken>`. A migração cria `sso_providers` vazia; estes endpoints cadastram e removem os provedores que o login federado pode usar. O `identityProvider` é o nome do provedor no User Pool do Cognito, não o valor do enum.

#### POST `/v1/sso-providers`
Cadastra um provedor. Se o `type` já existir, a resposta é `409 Conflict`.

**Request Body:**
```json
{
  "type": "MICROSOFT",
  "identityProvider": "Microsoft"
}
```

Valores de `type`: `MICROSOFT`, `GOOGLE`, `FACEBOOK`, `APPLE`, `AMAZON`.

**Response:** `204 No Content`

#### DELETE `/v1/sso-providers/{type}`
Remove o provedor cadastrado. Se ele não existir, a resposta é `404 Not Found`.

**Response:** `204 No Content`

### Autenticação (Protegidos)

#### POST `/auth/logout`
Revoga todos os tokens do usuário no Cognito (Global Sign-Out). Após o logout, o access token deixa de ser aceito pela API.

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response:** `204 No Content`

### Usuário (Protegidos)

#### GET `/v1/users/current`
Retorna os dados do usuário autenticado.

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response (200 OK):**
```json
{
  "id": "7b1e4c1a-3f2d-4a8e-9c5b-2d6f8e0a1b2c",
  "username": "usuario123",
  "email": "usuario@example.com",
  "roles": ["USER"],
  "createdAt": "2026-01-01T12:00:00",
  "updatedAt": null
}
```

Usuários criados via provedor externo não possuem `username` (o campo retorna `null`).

## 🚨 Tratamento de Exceções

O projeto implementa um **GlobalExceptionHandler** que centraliza o tratamento das exceções da aplicação:

### Exceções Customizadas

- `UserAlreadyExistsException` (409 Conflict): Username, e-mail, login local, provedor já vinculado ou identity provider já cadastrado
- `UserCreationException` (500 Internal Server Error): Falha ao criar o usuário no Cognito, no banco ou ao vincular identidades
- `AuthenticationFailedException` (401 Unauthorized): Credenciais inválidas, usuário não confirmado, refresh token inválido, falha no login federado ou `state` de vínculo inválido/expirado
- `AccountLinkException` (400 Bad Request): Senha recusada pela política do Cognito, e-mails diferentes ou tentativa de vincular provedor sem login local
- `EmailConfirmationException` (400 Bad Request): Código inválido ou expirado, e-mail já confirmado ou limite de reenvios atingido
- `UserNotExistsException` (404 Not Found): Usuário não encontrado no banco local
- `SsoProviderNotFoundException` (404 Not Found): Provedor não cadastrado em `sso_providers`
- `MethodArgumentNotValidException` (400 Bad Request): Falha na validação do corpo da requisição

Requisições a rotas protegidas sem token, com token inválido ou revogado retornam `401 Unauthorized` sem corpo.

### Respostas de Erro Padronizadas

Todas as exceções tratadas retornam um formato padronizado:

```json
{
  "timestamp": "2026-01-01T12:00:00",
  "status": 400,
  "message": "Invalid email, Password must be between 8 and 32 characters",
  "path": "/auth/register"
}
```

Em erros de validação, as mensagens de todos os campos são concatenadas em `message`, sem expor os valores rejeitados.

## 📝 Logging

O projeto utiliza **SLF4J com Lombok** para logging, com mascaramento de dados sensíveis via **LogSanitizer**:

- **@Slf4j**: Anotação do Lombok que gera automaticamente o logger
- **LogSanitizer.sanitize()**: Mascara e-mails e identificadores, mantendo apenas o primeiro e o último caractere
- **LogSanitizer.sanitizeText()**: Mascara e-mails embutidos em texto livre e remove quebras de linha (prevenção de log injection)
- **Níveis de Log**:
  - `log.info()`: Operações importantes (registro, login, refresh, logout)
  - `log.warn()`: Avisos (credenciais inválidas, tokens revogados, códigos inválidos)
  - `log.error()`: Erros inesperados na comunicação com o Cognito ou banco

### Exemplo de Logs

```
INFO  - POST /auth/login - Authenticating user with email: u***o@e***e.com
INFO  - Attempting to authenticate user with email: u***o@e***e.com
INFO  - User with email u***o@e***e.com authenticated successfully
WARN  - Invalid credentials for email: u***o@e***e.com
WARN  - Revoked access token for Cognito sub 1***f
```

## 🗄️ Banco de Dados

### Migrações Flyway

O projeto utiliza **Flyway** para versionamento do banco de dados:

- `V1__create_initial_tables.sql`: Criação das tabelas `users`, `roles` e `user_roles`
- `V2__create_sso_providers_table.sql`: Criação da tabela `sso_providers` (`type`, `identity_provider`). Não insere nenhum provedor
- `V3__allow_null_cognito_sub.sql`: Permite `cognito_sub` nulo quando a compensação de um vínculo local falha depois de remover o usuário federado
- `V4__unique_email_ignore_case.sql`: Troca a unicidade de `email` por um índice em `lower(email)`, para a busca ignorar maiúsculas e minúsculas

### Provedores SSO

A migração **não** insere provedores. O cadastro é feito com a API autenticada:

```bash
curl -X POST http://localhost:8080/v1/sso-providers \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"type":"MICROSOFT","identityProvider":"Microsoft"}'
```

O `identityProvider` tem de ser exatamente o nome do provedor no Cognito. `type` aceita apenas `MICROSOFT`, `GOOGLE`, `FACEBOOK`, `APPLE` e `AMAZON`. Sem o registro, `POST /oauth2/authorize` e `GET /v1/link/{provider}` respondem `404`. Para remover:

```bash
curl -X DELETE http://localhost:8080/v1/sso-providers/MICROSOFT \
  -H "Authorization: Bearer <accessToken>"
```

Também é possível inserir direto no banco:

```sql
INSERT INTO sso_providers (type, identity_provider)
VALUES ('MICROSOFT', 'Microsoft');
```

### Modelo de Dados

- **User**: Entidade principal de usuário
  - Campos: id (UUID), username, email, cognitoSub, createdAt, updatedAt
  - `username` é opcional (usuários federados não possuem)
  - `cognitoSub` liga o registro local ao usuário do Cognito e pode ficar nulo se o vínculo de uma senha local falhar depois que o usuário federado já foi removido
  - Relacionamento Many-to-Many com Role

- **Role**: Entidade de permissões
  - Campos: id (UUID), name, description, createdAt, updatedAt
  - Relacionamento Many-to-Many com User

- **SsoProvider**: Provedor federado habilitado para a API
  - Campos: id (UUID), type, identityProvider, createdAt, updatedAt
  - `type` é único (`MICROSOFT`, `GOOGLE`, `FACEBOOK`, `APPLE`, `AMAZON`)
  - `identityProvider` é o nome do provedor no User Pool do Cognito
  - Nenhum registro é criado pela migração; o cadastro é feito por `POST /v1/sso-providers`

A senha **não é armazenada** no banco local; ela existe apenas no Cognito. Nenhuma role é criada automaticamente pela migração.

## 🔒 Segurança Implementada

### Spring Security Configuration

- **OAuth2 Resource Server**: Validação de JWT com `NimbusJwtDecoder` a partir do JWKS do Cognito
- **Validadores Customizados**: Issuer, `token_use=access` e `client_id`
- **CognitoJwtAuthenticationConverter**: Verifica revogação no Cognito e carrega o usuário local com suas roles
- **CORS**: Configuração de Cross-Origin Resource Sharing para `localhost:4200` e `localhost:3000`
- **CSRF**: Desabilitado (API stateless baseada em Bearer token)
- **Session Management**: Gerenciamento de sessão stateless
- **Entry Point**: Respostas `401 Unauthorized` para requisições não autenticadas

### Rotas Públicas

- `/auth/register`, `/auth/confirm-email`, `/auth/resend-confirmation-code`, `/auth/login`, `/auth/refresh`
- `/oauth2/**`
- `/health/**`, `/public/**`

Todas as demais rotas exigem autenticação, inclusive `/v1/link/**` e `/v1/sso-providers/**`.

### Validações

- **Bean Validation**: Validação de dados de entrada nos DTOs de requisição
- **Regras de Negócio**: Unicidade de username/e-mail, conflito entre login por senha e login federado, e vínculo somente quando os e-mails coincidem
- **Políticas do Cognito**: Política de senha e confirmação de e-mail aplicadas pelo User Pool

## 🚀 Como Usar Este Projeto

### 1. Clonar o Repositório

```bash
git clone <repository-url>
cd spring-security-oauth2
```

### 2. Abrir no Dev Container (recomendado)

O projeto inclui um Dev Container com **Java 21**, **Gradle** e **PostgreSQL 15**:

1. Abra a pasta no VS Code/Cursor
2. Execute **Dev Containers: Reopen in Container**
3. O banco sobe automaticamente no serviço `db` (porta `5432`, banco `oauth_db`)

Sem Dev Container, é necessário ter Java 21 e um PostgreSQL acessível, ajustando `POSTGRES_HOST`.

### 3. Configurar Variáveis de Ambiente

Copie o modelo e preencha com os dados do seu User Pool:

```bash
cp .devcontainer/.env.example .env
```

O arquivo `.env` na raiz é carregado pelas configurações de execução em `.vscode/launch.json` e está no `.gitignore`. Para rodar pelo terminal, exporte as variáveis:

```bash
export POSTGRES_HOST=db
export POSTGRES_DB=oauth_db
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=postgres
export AWS_REGION=us-east-1
export AWS_COGNITO_USER_POOL_ID=us-east-1_AbCdEfGhI
export AWS_COGNITO_CLIENT_ID=seu_client_id
export AWS_COGNITO_CLIENT_SECRET=seu_client_secret
export AWS_COGNITO_DOMAIN=https://meu-app.auth.us-east-1.amazoncognito.com
export AWS_COGNITO_REDIRECT_URI=http://localhost:8080/oauth2/callback
```

### 4. Iniciar a Aplicação

```bash
./gradlew bootRun
```

Ou use a configuração **Spring Boot: spring-security-oauth2** no painel de Run and Debug. Na subida, o Flyway cria `sso_providers` vazia.

### 5. Cadastrar os provedores

A migração não insere provedores. Com a aplicação no ar e um access token válido, cadastre cada identity provider que existe no User Pool. O `identityProvider` é o nome configurado no Cognito, não o valor do enum:

```bash
curl -X POST http://localhost:8080/v1/sso-providers \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"type":"MICROSOFT","identityProvider":"Microsoft"}'

curl -X POST http://localhost:8080/v1/sso-providers \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"type":"GOOGLE","identityProvider":"Google"}'
```

Inclua apenas os provedores que existem no User Pool. Sem esse cadastro, o authorize e o vínculo daquele provedor respondem `404`. Para remover, use `DELETE /v1/sso-providers/{type}`.

### 6. Testar os Endpoints

```bash
# Registrar usuário
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"teste","email":"teste@example.com","password":"Senha@123"}'

# Confirmar e-mail com o código recebido
curl -X POST http://localhost:8080/auth/confirm-email \
  -H "Content-Type: application/json" \
  -d '{"email":"teste@example.com","code":"123456"}'

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"teste@example.com","password":"Senha@123"}'

# Refresh
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"email":"teste@example.com","refreshToken":"<refreshToken>"}'

# Endpoint protegido
curl -X GET http://localhost:8080/v1/users/current \
  -H "Authorization: Bearer <accessToken>"

# Logout
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer <accessToken>"

# URL de login federado (o cliente redireciona o navegador para authorizationUrl)
curl -X POST http://localhost:8080/oauth2/authorize \
  -H "Content-Type: application/json" \
  -d '{"provider":"MICROSOFT"}'

# Cadastrar um provedor SSO
curl -X POST http://localhost:8080/v1/sso-providers \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"type":"MICROSOFT","identityProvider":"Microsoft"}'

# Remover um provedor SSO
curl -X DELETE http://localhost:8080/v1/sso-providers/MICROSOFT \
  -H "Authorization: Bearer <accessToken>"
```

O callback `GET /oauth2/callback` é chamado pelo Cognito. Sem `state`, a resposta é o JSON dos tokens. Com `state`, a resposta é `204` e a conta é vinculada.

## 📦 Extensibilidade

Este projeto pode ser facilmente estendido com:

- **Novos Endpoints**: Adicionar controllers na pasta `api/controller`
- **Novas Entidades**: Criar entidades em `entity` e repositórios em `repository`
- **Novos Serviços**: Implementar serviços em `service`
- **Novos Provedores Federados**: Cadastrar o provedor no Cognito, incluir o valor no enum `SsoProviderType` se ainda não existir, e registrar com `POST /v1/sso-providers`
- **Novas Roles**: Adicionar roles no banco e usar `@PreAuthorize("hasRole('...')")` ou regras em `SecurityConfig`
- **Novas Migrações**: Adicionar arquivos `V<n>__descricao.sql` em `db/migration`
- **Novas Origens CORS**: Ajustar `corsConfigurationSource()` em `SecurityConfig`

## 📄 Licença

Este projeto está sob a licença **MIT**. Consulte o arquivo `LICENSE` para mais detalhes.

## 👥 Contribuindo

Esta é uma POC destinada a validar a integração com o AWS Cognito. Para contribuir:

1. Fazer fork do projeto
2. Criar uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Formatar o código (`./gradlew spotlessApply`)
4. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
5. Push para a branch (`git push origin feature/AmazingFeature`)
6. Abrir um Pull Request

## 📚 Recursos Adicionais

- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Amazon Cognito Developer Guide](https://docs.aws.amazon.com/cognito/latest/developerguide/what-is-amazon-cognito.html)
- [Cognito - Adicionar provedores OIDC ao User Pool](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-oidc-idp.html)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [Flyway Documentation](https://documentation.red-gate.com/flyway)
- [JWT.io](https://jwt.io/) - Documentação sobre JWT

---
