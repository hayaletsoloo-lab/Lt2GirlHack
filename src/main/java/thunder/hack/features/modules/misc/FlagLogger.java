package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Appends one line per server setback (PlayerPositionLook) with the module state
 * at that moment. Purpose: find which feature/setting triggers an anticheat flag.
 */
public class FlagLogger extends Module {
    private Path logFile;

    public FlagLogger() {
        super("FlagLogger", Category.MISC);
    }

    @Override
    public void onEnable() {
        logFile = mc.runDirectory.toPath().resolve("flaglog.txt");
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck() || logFile == null) return;
        if (!(e.getPacket() instanceof PlayerPositionLookS2CPacket pkt)) return;

        String line = String.format(
                "%s age=%d server=%s | setback=(%.2f,%.2f,%.2f) | pos=(%.2f,%.2f,%.2f) yaw=%.1f pitch=%.1f hurt=%d ground=%b elytra=%b | aura=%b target=%s velocity=%b elytraPlus=%b%n",
                Instant.now(), mc.player.age,
                mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "local",
                pkt.getX(), pkt.getY(), pkt.getZ(),
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch(), mc.player.hurtTime,
                mc.player.isOnGround(), mc.player.isFallFlying(),
                ModuleManager.aura.isEnabled(),
                ModuleManager.aura.target != null ? ModuleManager.aura.target.getName().getString() : "none",
                ModuleManager.velocity.isEnabled(),
                ModuleManager.elytraPlus.isEnabled());
        try {
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            // logging must never break the game loop
        }
    }
}
