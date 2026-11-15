# Colocar a v1.6 no repositório do mod

Você tem dois caminhos. O **Caminho A** é o mais simples (site do GitHub, sem instalar nada).
O **Caminho B** é por git, se o repositório já estiver clonado no seu PC.

---

## Caminho A — pelo site do GitHub (recomendado)

### 1. Abrir o repositório
Entre em `github.com` → seu repositório do Nobre Vilas.

### 2. Apagar o layout antigo (só na primeira vez)
Esta versão mudou a organização das pastas: agora o código fica em `src/br/nobrevilas/`
e o `fabric.mod.json` em `src/main/resources/`. Se você subir os arquivos novos sem apagar
os antigos, o build vai compilar tudo em dobro e quebrar.

Na raiz do repositório, **apague** (abrir cada um → ícone de lixeira → *Commit changes*):

- todos os `.java` da raiz (`Admin.java`, `Ui.java`, `NpcScreen.java`, ... os 25)
- `fabric.mod.json` da raiz
- a pasta `assets` da raiz (só se ela existir na raiz; a nova vai dentro de `src/main/resources`)

> Dica: no GitHub não dá para apagar vários de uma vez pelo site. Se preferir, apague o
> repositório inteiro e crie outro — o resultado é o mesmo e é mais rápido.

### 3. Subir a estrutura nova
1. Extraia o `nobre-vilas-v1.6-fonte.zip`.
2. No repositório: **Add file → Upload files**.
3. Arraste **o conteúdo** da pasta extraída (não a pasta em si):
   `build.gradle`, `gradle.properties`, `settings.gradle`, `LEIA-ME.md`, a pasta `src`
   e a pasta `.github`.
4. Em *Commit changes* escreva `v1.6.0` e confirme.

⚠️ A pasta `.github` é essencial: é o workflow que compila o mod. Se ela não aparecer no
seletor de arquivos do Windows, use o Caminho B ou crie o arquivo à mão pelo botão
**Add file → Create new file**, digitando o nome
`.github/workflows/build.yml` e colando o conteúdo do arquivo do zip.

### 4. Rodar o build
- Aba **Actions** → workflow **Build mod** → **Run workflow** → *Run workflow*.
- Ou apenas o push do passo 3 já dispara sozinho.
- Verde ✅ em ~3–5 min → entre na execução → **Artifacts** → baixe `nobre-vilas-jar`.

---

## Caminho B — por git, no seu PC

No Prompt de Comando, dentro da pasta do repositório clonado:

```bat
cd C:\caminho\do\seu\repositorio-do-mod
```

**1. Apagar o layout antigo**

```bat
del *.java
del fabric.mod.json
rmdir /s /q assets
```

**2. Copiar a estrutura nova** (troque o caminho pelo seu)

```bat
xcopy "C:\Users\User\Downloads\nobre-vilas-v1.6-entrega\*" . /E /H /Y
```

**3. Conferir o que mudou**

```bat
git status
```

Devem aparecer: `build.gradle`, `gradle.properties`, `settings.gradle` modificados/novos,
a pasta `src/` nova e os `.java` antigos como *deleted*.

**4. Enviar**

```bat
git add -A
git commit -m "v1.6.0: retrato 3D do NPC, carisma do rei, muralha central e fundacao escura"
git push
```

O GitHub Actions compila sozinho no push. Depois: **Actions → Build mod → Artifacts →
nobre-vilas-jar**.

---

## Resumo do que muda nesta versão

| Arquivo | Mudança |
|---|---|
| `Ui.java` | diálogos corrigidos (1ª pessoa, cargos, capital), falas por carisma |
| `VillageManager.java` | carisma, UUID do capitão, muralha só a ≤300 blocos do centro |
| `WallBuilder.java` | estilo por tier completo (deepslate tiles no 7) |
| `CapitalBuilder.java` | fundação em pedra escura + **portão central** |
| `Conquest.java` | atacar a capital derruba o carisma do rei |
| `NpcTalkPayload.java` | passa o UUID da entidade para o cliente |
| `NpcScreen.java` | **modelo 3D do mob** no retrato da tela de conversa |
| `VillageData.java`, `Ranks.java` | campos `charisma`, `captainUuid`; postos CONDE/MARQUÊS |

## Se o build falhar
Copie o trecho vermelho do log do Actions e me mande. Os pontos mais prováveis são as
chamadas de renderização em `NpcScreen.drawEntity3D(...)`, que usam API interna do
Minecraft 26.3.
