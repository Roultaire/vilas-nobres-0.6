package br.nobrevilas;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Desenha os baloes de fala dos NPCs por cima do HUD (so texto, sem tela e sem pausar o jogo).
 * O metodo alvo e "*" com require = 0: se a HUD mudar, o mixin nao quebra o jogo.
 */
@Mixin(net.minecraft.client.gui.Gui.class)
public class ChatBubbleMixin {
    @Inject(method = "*", at = @At("TAIL"), require = 0)
    private void nobrevilas$bubbles(GuiGraphicsExtractor g, CallbackInfo ci) {
        ChatBubble.render(g);
    }
}
