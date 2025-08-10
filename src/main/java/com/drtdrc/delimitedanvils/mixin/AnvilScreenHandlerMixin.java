package com.drtdrc.delimitedanvils.mixin;

import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.ForgingSlotsManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {

    @Shadow @Final private Property levelCost;

    @Unique private boolean spoofingCreative = false;
    @Unique private boolean wasCreative = false;

    public AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context, ForgingSlotsManager forgingSlotsManager) {
        super(type, syncId, playerInventory, context, forgingSlotsManager);
    }

    @ModifyConstant(
            method = "updateResult",
            constant = @Constant(intValue = 40),
            slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=39"))
    )
    private int removeTooExpensiveCap(int original) {
        return Integer.MAX_VALUE;
    }

    @Inject(
            method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/screen/ScreenHandlerContext;)V",
            at = @At("TAIL")
    )
    private void afterConstructor(int syncId, PlayerInventory inventory, ScreenHandlerContext context, CallbackInfo ci) {
        if (!(this.player instanceof ServerPlayerEntity sp)) return;
        wasCreative = sp.isCreative();
        if (!wasCreative) {
            sendCreativeSpoof(sp, true);
            spoofingCreative = true;
        }
    }

    // Ensuring levels are
    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void onCanTakeOutputHead(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        if (player.getAbilities().creativeMode) return; // real creative stays creative
        int cost = this.levelCost.get();
        boolean ok = present && cost > 0 && player.experienceLevel >= cost;
        cir.setReturnValue(ok);
    }


    @Redirect(
            method = "onTakeOutput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExperienceLevels(I)V")
    )
    private void onTakeOutputXPCharge(PlayerEntity player, int ignoredDelta) {
        if (!player.getAbilities().creativeMode) {
            player.addExperienceLevels(-levelCost.get());
        }
    }

    // Have to override ForgingScreenHandler onClosed here
    // There's better ways to handle this but meh
    @Override
    public void onClosed(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (spoofingCreative && !wasCreative) {
            sendCreativeSpoof(sp, false);
            spoofingCreative = false;
        }
        super.onClosed(player);
    }

    // Makes the client believe it is in creative, thus disabling the client "Too Expensive!" message and displaying the real cost
    @Unique
    private void sendCreativeSpoof(ServerPlayerEntity sp, boolean pretendCreative) {
        PlayerAbilities src = sp.getAbilities();
        PlayerAbilities fake = new PlayerAbilities();
        fake.invulnerable = src.invulnerable;
        fake.allowFlying = false;
        fake.creativeMode = pretendCreative;
        fake.setFlySpeed(src.getFlySpeed());
        fake.setWalkSpeed(src.getWalkSpeed());
        sp.networkHandler.sendPacket(new PlayerAbilitiesS2CPacket(fake));
    }

}
