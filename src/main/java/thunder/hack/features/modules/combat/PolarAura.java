package com.thunderhack.modules.combat;

import com.thunderhack.events.UpdateEvent;
import com.thunderhack.modules.Module;
import com.thunderhack.util.math.MathUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PolarAura extends Module {

    public PolarAura() {
        super("PolarAura", "Polar AC tam bypassli KillAura.", Category.COMBAT);
    }

    // --- AYARLAR ---
    private final float range = 2.9f; // Polar 3.0'da flagler, 2.9 güvenli sınırdır
    private final float rotationSpeed = 0.35f; // Snap (ani dönüş) engelleme hızı
    private final long attackDelay = 500; // Polar CPS limitini aşmamak için (Milisaniye)

    // --- DEĞİŞKENLER ---
    private LivingEntity target;
    private float lastYaw, lastPitch;
    private long lastAttackTime = 0;

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        // 1. Hedef Bulma (En yakın geçerli oyuncu)
        target = getTarget();
        if (target == null) {
            return;
        }

        // 2. Rotasyon Bypass (Polar GCD & Snap Fix)
        float[] targetRots = calculateRotations(target);
        
        // GCD Fix uygula (Polar'in aimbot tespitini engeller)
        float targetYaw = applyGCD(targetRots[0]);
        float targetPitch = applyGCD(targetRots[1]);

        // Yumuşak geçiş (Polar'in "instant snap" kontrolünü atlatır)
        float newYaw = MathUtil.interpolate(lastYaw, targetYaw, rotationSpeed);
        float newPitch = MathUtil.interpolate(lastPitch, targetPitch, rotationSpeed);

        // Rotasyonu oyuncuya uygula
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        lastYaw = newYaw;
        lastPitch = newPitch;

        // 3. Saldırı Zamanlaması (Polar CPS & Transaction Bypass)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttackTime >= attackDelay) {
            
            // Polar'in NoSwing kontrolüne takılmamak için el sallama zorunlu
            mc.player.swingHand(Hand.MAIN_HAND);
            
            // Vuruşu yap
            mc.interactionManager.attackEntity(mc.player, target);
            
            lastAttackTime = currentTime;
        }
    }

    // --- YARDIMCI METOTLAR ---

    private LivingEntity getTarget() {
        List<Entity> entities = mc.world.getEntities().filter(e -> 
            e instanceof LivingEntity && 
            e != mc.player && 
            e.isAlive() && 
            mc.player.distanceTo(e) <= range // Polar Reach bypass (2.9)
        ).sorted(Comparator.comparingDouble(mc.player::distanceTo)).collect(Collectors.toList());

        return entities.isEmpty() ? null : (LivingEntity) entities.get(0);
    }

    private float[] calculateRotations(Entity target) {
        double diffX = target.getX() - mc.player.getX();
        double diffY = (target.getY() + target.getBoundingBox().getYLength() / 2.0) - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = target.getZ() - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{targetYaw, targetPitch};
    }

    private float applyGCD(float value) {
        // Mouse hassasiyetine göre GCD (Greatest Common Divisor) yuvarlaması
        // Polar'in hassas rotasyon kontrollerini atlatmak için kritiktir
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double gcd = sensitivity * (sensitivity * 8.0D);
        gcd = gcd * gcd;
        return (float) (Math.round(value / gcd) * gcd);
    }
}
