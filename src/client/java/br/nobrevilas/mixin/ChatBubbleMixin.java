package br.nobrevilas.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Desenha os baloes de fala dos NPCs por cima do HUD (so texto, sem tela e sem pausar o jogo).
 * Se a HUD mudar de assinatura, o mixin nao injeta e o jogo segue normal (require = 0).
 */
@Mixin(Hud.class)
public class ChatBubbleMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void nobrevilas$bubbles(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        br.nobrevilas.ChatBubble.render(g);
    }
}
