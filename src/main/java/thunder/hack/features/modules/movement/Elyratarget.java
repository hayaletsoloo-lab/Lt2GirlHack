package com.thunderhack.modules.movement;

import com.thunderhack.events.PacketEvent;
import com.thunderhack.events.UpdateEvent;
import com.thunderhack.modules.Module;
import com.thunderhack.util.math.MathUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ElytraTarget extends Module {

    public ElytraTarget() {
        super("ElytraTarget", "Hedefin önüne geçer, Polar AC Bypass.", Category.MOVEMENT);
    }

    private final float reach = 4.5f;
    private final double speedMultiplier = 1.15; // Hedefin hızından biraz daha hızlı git ki önüne geçebilesin
    private final float rotationStep = 0.25f; // Polar için yumuşak dönüş

    private PlayerEntity target;
    private float lastYaw, lastPitch;

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck() || !mc.player.isFallFlying()) return;

        // Hedefi bul
        target = getTarget();
        if (target == null || !target.isFallFlying()) return;

        // 1. Hedefin Gelecek Pozisyonunu Hesapla (Prediction)
        Vec3d targetPos = target.getPos();
        Vec3d targetVel = target.getVelocity();
        // Hedefin 10 tick sonraki pozisyonunu tahmin et (Önüne geçmek için)
        Vec3d futureTargetPos = targetPos.add(targetVel.multiply(10));

        // 2. Rotasyon Hesapla (Hedefin önüne bakacak şekilde)
        float[] rots = calculateRotations(futureTargetPos);
        
        // GCD ve Snap fix (Polar için)
        float targetYaw = applyGCD(rots[0]);
        float targetPitch = applyGCD(rots[1]);
        
        float newYaw = MathUtil.interpolate(lastYaw, targetYaw, rotationStep);
        float newPitch = MathUtil.interpolate(lastPitch, targetPitch, rotationStep);

        // 3. Hareket (Velocity) Manipülasyonu
        Vec3d dir = futureTargetPos.subtract(mc.player.getPos()).normalize();
        
        // Hedefin mevcut hızını al ve kendi hızımıza uyarla
        double currentSpeed = Math.sqrt(targetVel.x * targetVel.x + targetVel.z * targetVel.z);
        double ourSpeed = currentSpeed * speedMultiplier; // Önüne geçmek için biraz daha hızlı

        // Yeni hız vektörünü uygula (Polar'ın ani ivme check'lerini atlatmak için yumuşatma)
        mc.player.setVelocity(
            dir.x * ourSpeed, 
            targetVel.y - 0.05, // Hafif aşağı dalış (Polar'ın havada süzülme check'i için)
            dir.z * ourSpeed
        );

        // Rotasyonları uygula
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        lastYaw = newYaw;
        lastPitch = newPitch;
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        // Polar'ın "Invalid Movement" veya "Elytra" check'ini atlatmak için
        // Sunucuya giden pozisyon paketlerini spoof'la
        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesLook()) {
                // Eğer sunucu anormal bir bakış açısı görürse flagler. Burada paketi düzenleyebilirsin.
                // Ancak yukarıdaki interpolate ve GCD zaten bunu büyük oranda çözer.
            }
        }
    }

    private PlayerEntity getTarget() {
        List<Entity> entities = mc.world.getEntities().filter(e -> 
            e instanceof PlayerEntity && 
            e != mc.player && 
            e.isAlive() && 
            e.isFallFlying() &&
            mc.player.distanceTo(e) <= 50.0f // Havada 50 blok mesafeye kadar takip
        ).sorted(Comparator.comparingDouble(mc.player::distanceTo)).collect(Collectors.toList());

        return entities.isEmpty() ? null : (PlayerEntity) entities.get(0);
    }

    private float[] calculateRotations(Vec3d pos) {
        double diffX = pos.x - mc.player.getX();
        double diffY = pos.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = pos.z - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{targetYaw, targetPitch};
    }

    private float applyGCD(float value) {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double gcd = sensitivity * (sensitivity * 8.0D);
        gcd = gcd * gcd;
        return (float) (Math.round(value / gcd) * gcd);
    }
}
