package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import thunder.hack.events.impl.EventClickSlot;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.player.MovementUtility;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class GuiMove extends Module {
    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
    }

    private final Setting<Bypass> clickBypass = new Setting<>("Bypass", Bypass.None);
    private final Setting<Boolean> rotateOnArrows = new Setting<>("RotateOnArrows", true);
    private final Setting<Boolean> sneak = new Setting<>("sneak", false);

    private final Queue<ClickSlotC2SPacket> storedClicks = new LinkedList<>();
    private AtomicBoolean pause = new AtomicBoolean();

    // FunTime mode state
    private final Queue<ClickSlotC2SPacket> funQueue = new ConcurrentLinkedQueue<>();
    private boolean funWasOpen;
    private boolean funResync;
    private long funCloseTime;

    private KeyBinding[] moveKeys() {
        return new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey};
    }

    private void syncKeys() {
        for (KeyBinding k : moveKeys())
            k.setPressed(isKeyPressed(InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));
    }

    private void flushFunQueue() {
        new Thread(() -> {
            try {
                Thread.sleep(80);
            } catch (InterruptedException ignored) {
                return;
            }
            ClickSlotC2SPacket p;
            while ((p = funQueue.poll()) != null)
                sendPacket(p);
        }).start();
    }

    @Override
    public void onUpdate() {
        if (clickBypass.is(Bypass.FunTime)) {
            boolean invOpen = mc.currentScreen instanceof InventoryScreen;
            if (invOpen) {
                funWasOpen = true;
            } else if (funWasOpen) {
                funWasOpen = false;
                funCloseTime = System.currentTimeMillis();
                flushFunQueue();
            }

            // after closing: stand still while queued clicks are flushed
            if (!invOpen && !funQueue.isEmpty() && System.currentTimeMillis() - funCloseTime < 100) {
                for (KeyBinding k : moveKeys())
                    k.setPressed(false);
                funResync = true;
                return;
            }

            if (funResync && mc.currentScreen == null) {
                syncKeys();
                funResync = false;
            }
        }

        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) {
            syncKeys();

            float deltaX = 0;
            float deltaY = 0;

            if (rotateOnArrows.getValue()) {
                if (isKeyPressed(264))
                    deltaY += 30f;

                if (isKeyPressed(265))
                    deltaY -= 30f;

                if (isKeyPressed(262))
                    deltaX += 30f;

                if (isKeyPressed(263))
                    deltaX -= 30f;

                if (deltaX != 0 || deltaY != 0)
                    mc.player.changeLookDirection(deltaX, deltaY);
            }

            if (sneak.getValue())
                mc.options.sneakKey.setPressed(isKeyPressed(InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode()));
        }
    }

    @EventHandler
    public void onClickSlot(EventClickSlot e) {
        if (clickBypass.is(Bypass.DisableClicks) && (MovementUtility.isMoving() || mc.options.jumpKey.isPressed()))
            e.cancel();
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        // FunTime: hold own-inventory clicks made while moving, send them after the screen is closed
        if (clickBypass.is(Bypass.FunTime) && e.getPacket() instanceof ClickSlotC2SPacket funClick
                && MovementUtility.isMoving() && mc.currentScreen instanceof InventoryScreen) {
            funQueue.add(funClick);
            e.cancel();
            return;
        }

        if (!MovementUtility.isMoving() || !mc.options.jumpKey.isPressed() || pause.get())
            return;

        if (e.getPacket() instanceof ClickSlotC2SPacket click) {
            switch (clickBypass.getValue()) {
                case GrimSwap -> {
                    if (click.getActionType() != SlotActionType.PICKUP && click.getActionType() != SlotActionType.PICKUP_ALL)
                        sendPacket(new CloseHandledScreenC2SPacket(0));
                }

                case StrictNCP -> {
                    if (mc.player.isOnGround() && !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, 0.0656, 0.0)).iterator().hasNext()) {
                        if (mc.player.isSprinting())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.0656, mc.player.getZ(), false));
                    }
                }

                case StrictNCP2 -> {
                    if (mc.player.isOnGround() && !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, 0.000000271875, 0.0)).iterator().hasNext()) {
                        if (mc.player.isSprinting())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.000000271875, mc.player.getZ(), false));
                    }
                }

                case MatrixNcp -> {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    mc.options.forwardKey.setPressed(false);
                    mc.player.input.movementForward = 0;
                    mc.player.input.pressingForward = false;
                }

                case Delay -> {
                    storedClicks.add(click);
                    e.cancel();
                }
            }
        }

        if (e.getPacket() instanceof CloseHandledScreenC2SPacket) {
            if (clickBypass.is(Bypass.Delay)) {
                pause.set(true);
                while (!storedClicks.isEmpty())
                    sendPacket(storedClicks.poll());
                pause.set(false);
            }
        }
    }

    @EventHandler
    public void onPacketSendPost(PacketEvent.SendPost e) {
        if (e.getPacket() instanceof ClickSlotC2SPacket) {
            if (mc.player.isSprinting() && clickBypass.is(Bypass.StrictNCP))
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
    }

    private enum Bypass {
        DisableClicks, None, StrictNCP, GrimSwap, MatrixNcp, Delay, StrictNCP2, FunTime
    }
}
