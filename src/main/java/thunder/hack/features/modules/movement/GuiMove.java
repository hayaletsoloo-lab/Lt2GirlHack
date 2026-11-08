package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.player.MovementUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * GuiMove rebuilt around the GuiWalk mechanism.
 * FunTime: inventory clicks made while moving are held and sent 80ms after the
 * inventory closes; movement keys are released for 100ms meanwhile.
 */
public class GuiMove extends Module {
    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Normal);
    private final Setting<Boolean> rotateOnArrows = new Setting<>("RotateOnArrows", true);
    private final Setting<Boolean> sneak = new Setting<>("sneak", false);

    private final Queue<ClickSlotC2SPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean flushing;
    private boolean wasInventoryOpen;
    private boolean resync;
    private long closeTime;

    private KeyBinding[] keys() {
        return new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey};
    }

    private void releaseKeys() {
        for (KeyBinding k : keys())
            k.setPressed(false);
        resync = true;
    }

    private void syncKeys() {
        for (KeyBinding k : keys())
            k.setPressed(isKeyPressed(InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));
    }

    @Override
    public void onUpdate() {
        if (mc.player == null) return;

        if (mc.currentScreen instanceof ChatScreen) {
            releaseKeys();
            return;
        }

        // after closing the inventory: stand still while held clicks are flushed
        if (mode.getValue() == Mode.FunTime && !packetQueue.isEmpty() && System.currentTimeMillis() - closeTime < 100) {
            releaseKeys();
            return;
        }

        if (mc.currentScreen != null || resync) {
            syncKeys();
            resync = false;
        }

        if (mc.currentScreen != null) {
            if (rotateOnArrows.getValue()) {
                float deltaX = 0;
                float deltaY = 0;

                if (isKeyPressed(264)) deltaY += 30f;
                if (isKeyPressed(265)) deltaY -= 30f;
                if (isKeyPressed(262)) deltaX += 30f;
                if (isKeyPressed(263)) deltaX -= 30f;

                if (deltaX != 0 || deltaY != 0)
                    mc.player.changeLookDirection(deltaX, deltaY);
            }

            if (sneak.getValue())
                mc.options.sneakKey.setPressed(isKeyPressed(InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode()));
        }

        boolean inventoryOpen = mc.currentScreen instanceof InventoryScreen;

        if (mode.getValue() == Mode.FunTime) {
            if (inventoryOpen) {
                wasInventoryOpen = true;
            } else if (wasInventoryOpen) {
                wasInventoryOpen = false;
                closeTime = System.currentTimeMillis();
                flushQueued();
            }
        } else {
            packetQueue.clear();
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        if (flushing || mode.getValue() != Mode.FunTime) return;
        if (!(e.getPacket() instanceof ClickSlotC2SPacket click)) return;

        if (MovementUtility.isMoving() && mc.currentScreen instanceof InventoryScreen) {
            packetQueue.add(click);
            e.cancel();
        }
    }

    private void flushQueued() {
        new Thread(() -> {
            try {
                Thread.sleep(80);
            } catch (InterruptedException ignored) {
                return;
            }
            flushing = true;
            try {
                ClickSlotC2SPacket p;
                while ((p = packetQueue.poll()) != null)
                    sendPacket(p);
            } finally {
                flushing = false;
            }
        }).start();
    }

    @Override
    public void onDisable() {
        // do not lose held clicks if the module is switched off mid-hold
        flushing = true;
        try {
            ClickSlotC2SPacket p;
            while ((p = packetQueue.poll()) != null)
                sendPacket(p);
        } finally {
            flushing = false;
        }
        wasInventoryOpen = false;
        resync = false;
    }

    private enum Mode {
        Normal, FunTime
    }
}
