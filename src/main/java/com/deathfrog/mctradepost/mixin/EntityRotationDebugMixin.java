package com.deathfrog.mctradepost.mixin;

import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityRotationDebugMixin
{
    static {
        System.err.println("### EntityRotationDebugMixin LOADED ###");
    }

    @Redirect(
        method = "setYRot(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/Util;logAndPauseIfInIde(Ljava/lang/String;)V"
        ),
        require = 1
    )
    private void mctp$traceInvalidYaw(String msg)
    {
        Entity self = (Entity) (Object) this;
        System.err.println("### Vanilla invalid rotation (yaw) about to log: " + msg);
        System.err.println("### Entity: type=" + self.getType() + " id=" + self.getId() + " name=" + self.getName().getString());
        new RuntimeException("Stack trace for invalid yaw").printStackTrace();

        // preserve vanilla behavior
        Util.logAndPauseIfInIde(msg + "");
    }

    @Redirect(
        method = "setXRot(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/Util;logAndPauseIfInIde(Ljava/lang/String;)V"
        ),
        require = 1
    )
    private void mctp$traceInvalidPitch(String msg)
    {
        Entity self = (Entity) (Object) this;
        System.err.println("### Vanilla invalid rotation (pitch) about to log: " + msg);
        System.err.println("### Entity: type=" + self.getType() + " id=" + self.getId() + " name=" + self.getName().getString());
        new RuntimeException("Stack trace for invalid pitch").printStackTrace();

        // preserve vanilla behavior
        Util.logAndPauseIfInIde(msg + "");
    }
}
