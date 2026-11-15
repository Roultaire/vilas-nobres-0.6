# Nobre Vilas 1.6.0 — como gerar o .jar no GitHub

## 1. Criar o repositório
1. No GitHub, crie um repositório novo (pode ser privado), **sem** README.
2. Extraia o `nobre-vilas-v1.6-fonte.zip`.
3. Envie **todo o conteúdo** da pasta extraída para a raiz do repositório
   (o `build.gradle`, `gradle.properties`, `settings.gradle`, a pasta `src` e a pasta oculta `.github`).

   Pelo site: *Add file → Upload files*, arraste tudo. A pasta `.github` é obrigatória —
   é ela que contém o build automático.

## 2. Rodar o build
- Vá em **Actions** → workflow **"Build mod"** → **Run workflow**.
- Ou simplesmente dê um commit: o build roda sozinho a cada push.
- Espera ~3–5 minutos.

## 3. Baixar o jar
- Abra a execução verde → em **Artifacts** baixe **nobre-vilas-jar**.
- Dentro vem `nobre-vilas-1.6.0.jar` (e o `-sources.jar`).
- Coloque o `nobre-vilas-1.6.0.jar` na pasta `mods` do seu perfil Fabric 26.3.

## Estrutura de pastas (já pronta)
```
build.gradle            (sourceSets apontando para src/)
gradle.properties       (mod_version=1.6.0, MC 26.3, Java 25)
settings.gradle
.github/workflows/build.yml
src/br/nobrevilas/*.java          (25 classes)
src/main/resources/fabric.mod.json
src/main/resources/assets/nobrevilas/lang/*.json
```

## Se o build falhar
O log do Actions diz a linha e o arquivo. Os pontos mais prováveis de ajuste,
porque usam API interna do Minecraft 26.3:

- `NpcScreen.java` → método `drawEntity3D(...)`: usa
  `GuiGraphicsExtractor.entity(...)` + `EntityRenderDispatcher.getRenderer(...)`
  + `EntityRenderer.createRenderState(...)`. Se a assinatura tiver mudado, o
  compilador aponta o método exato.
- `NpcTalkPayload.java` → o record tem 4 campos; se o cliente e o servidor
  estiverem com versões diferentes do mod, a conversa não abre.

Me mande o log de erro que eu corrijo.

## O que mudou nesta versão
- **Diálogos**: aldeão da capital não fala mais em distância ("capital a 13 blocos");
  rei/rainha e capitão falam na 1ª pessoa; cargos (duque, marquês, conde) respeitados.
- **Carisma do rei**: novo valor por capital (-10 a +10). Atacar a capital derruba o
  carisma; as falas do rei e dos moradores mudam conforme o povo está com raiva ou não.
- **Muralha da capital**: fundação em pedra escura (polished blackstone bricks) em vez
  de deepslate; portão no **meio exato** de cada lado; a muralha só é construída
  quando há jogador a **≤300 blocos** do centro.
- **Retrato 3D**: a tela de conversa agora desenha o **modelo 3D do próprio NPC**
  (qualquer mob) no quadro do retrato, girando levemente conforme o mouse, no lugar
  do boneco em pixel art. Se o modelo não puder ser desenhado, cai no retrato antigo.
