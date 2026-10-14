package com.stunslammacro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.function.Predicate;

/**
 * Tick-based state machine for one stun slam:
 * AXE (switch + hit, breaks the shield) -> MACE (switch + hit) -> RESTORE -> IDLE.
 * With the default delay of 1 the axe hit is on tick 1 and the mace hit on tick 2.
 * It only starts via start() and always ends in IDLE, so it can never loop.
 * All messages are intentionally silent; only the toggle key shows a message.
 */
public final class StunSlamSequence {
    private enum Step { IDLE, AXE, MACE, RESTORE }

    private Step step = Step.IDLE;
    private int delay;          // ticks to skip before the next step runs
    private boolean yieldTick;  // true = end this tick's pass so the next step runs on a later tick
    private int originalSlot, axeSlot, maceSlot;
    private Entity target;

    public boolean isRunning() {
        return step != Step.IDLE;
    }

    // ------------------------------------------------------------------ start

    /** Validates everything up front; if anything is missing nothing happens. */
    public void start(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) return;

        // Must be looking at a living entity.
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || hit.getType() != HitResult.Type.ENTITY) return;
        Entity entity = hit.getEntity();
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return;

        axeSlot = findHotbar(player, s -> s.isIn(ItemTags.AXES));
        maceSlot = findHotbar(player, s -> s.isOf(Items.MACE));
        if (axeSlot < 0 || maceSlot < 0) return;

        target = entity;
        originalSlot = player.getInventory().getSelectedSlot();
        step = Step.AXE;
        delay = 0;
    }

    // ------------------------------------------------------------------- tick

    public void tick(MinecraftClient mc) {
        if (step == Step.IDLE) return;

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null || !player.isAlive()) {
            reset();
            return;
        }
        yieldTick = false;
        if (delay > 0) {
            delay--;
            return;
        }

        StunSlamConfig cfg = StunSlamConfig.get();
        // With a delay of 0 the steps chain inside the same tick.
        for (int guard = 0; guard < 6 && step != Step.IDLE; guard++) {
            Step before = step;
            switch (step) {
                case AXE -> {
                    if (!target.isAlive() || target.isRemoved()) { finish(player, cfg); return; }
                    select(player, axeSlot);
                    attack(mc, player);
                    step = Step.MACE;
                    pause(cfg);
                }
                case MACE -> {
                    if (!target.isAlive() || target.isRemoved()) { finish(player, cfg); return; }
                    select(player, maceSlot);
                    attack(mc, player);
                    step = Step.RESTORE;
                    pause(cfg);
                }
                case RESTORE -> finish(player, cfg);
                default -> reset();
            }
            if (step == Step.IDLE || yieldTick || step == before) return;
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Real attack through the interaction manager (sends the slot sync + attack packets). */
    private void attack(MinecraftClient mc, ClientPlayerEntity player) {
        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
    }

    private void select(ClientPlayerEntity player, int slot) {
        // attackEntity() syncs the selected slot to the server before it sends the attack.
        player.getInventory().setSelectedSlot(slot);
    }

    private int findHotbar(ClientPlayerEntity player, Predicate<ItemStack> test) {
        for (int i = 0; i < 9; i++) {
            if (test.test(player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    /**
     * stepDelayTicks = ticks between steps. 0 = chain instantly in the same tick,
     * 1 = next tick, 2 = every second tick, etc.
     */
    private void pause(StunSlamConfig cfg) {
        yieldTick = cfg.stepDelayTicks > 0;
        delay = Math.max(0, cfg.stepDelayTicks - 1);
    }

    private void finish(ClientPlayerEntity player, StunSlamConfig cfg) {
        if (cfg.restoreOriginalSlot) select(player, originalSlot);
        reset();
    }

    private void reset() {
        step = Step.IDLE;
        delay = 0;
        yieldTick = false;
        target = null;
    }
}
