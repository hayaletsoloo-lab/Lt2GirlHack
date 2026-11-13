package thunder.hack.features.modules.combat;

import baritone.api.BaritoneAPI;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.NotNull;

import thunder.hack.core.Core;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.gui.notification.Notification;
import thunder.hack.injection.accesors.ILivingEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.interfaces.IOtherClientPlayerEntity;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;

import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;
import thunder.hack.utility.render.animation.CaptureMark;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.minecraft.util.UseAction.BLOCK;
import static net.minecraft.util.math.MathHelper.wrapDegrees;
import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.utility.math.MathUtility.random;

public class Aura extends Module {
    public final Setting<Float> attackRange = new Setting<>("Range", 3.1f, 1f, 6.0f);
    public final Setting<Float> wallRange = new Setting<>("ThroughWallsRange", 3.1f, 0f, 6.0f);
    public final Setting<Boolean> elytra = new Setting<>("ElytraOverride", false);
    public final Setting<Float> elytraAttackRange = new Setting<>("ElytraRange", 3.1f, 1f, 6.0f, v -> elytra.getValue());
    public final Setting<Float> elytraWallRange = new Setting<>("ElytraThroughWallsRange", 3.1f, 0f, 6.0f, v -> elytra.getValue());
    public final Setting<WallsBypass> wallsBypass = new Setting<>("WallsBypass", WallsBypass.Off, v -> getWallRange() > 0);
    public final Setting<Integer> fov = new Setting<>("FOV", 180, 1, 180);
    public final Setting<Mode> rotationMode = new Setting<>("RotationMode", Mode.Track);
    public final Setting<Integer> interactTicks = new Setting<>("InteractTicks", 3, 1, 10, v -> rotationMode.getValue() == Mode.Interact);
    public final Setting<Switch> switchMode = new Setting<>("AutoWeapon", Switch.None);
    public final Setting<Boolean> onlyWeapon = new Setting<>("OnlyWeapon", false, v -> switchMode.getValue() != Switch.Silent);
    public final Setting<BooleanSettingGroup> smartCrit = new Setting<>("SmartCrit", new BooleanSettingGroup(true));
    public final Setting<Boolean> onlySpace = new Setting<>("OnlyCrit", false).addToGroup(smartCrit);
    public final Setting<Boolean> autoJump = new Setting<>("AutoJump", false).addToGroup(smartCrit);
    public final Setting<Boolean> shieldBreaker = new Setting<>("ShieldBreaker", true);
    
    // --- POLAR BYPASS EKLENTİSİ ---
    public final Setting<Boolean> polarBypass = new Setting<>("PolarBypass", true);
    
    public final Setting<Boolean> polarShield = new Setting<>("PolarShield", false);
    public final Setting<Boolean> pauseWhileEating = new Setting<>("PauseWhileEating", false);
    public final Setting<Boolean> tpsSync = new Setting<>("TPSSync", false);
    public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false);
    public final Setting<Boolean> pauseBaritone = new Setting<>("PauseBaritone", false);
    public final Setting<BooleanSettingGroup> oldDelay = new Setting<>("OldDelay", new BooleanSettingGroup(false));
    public final Setting<Integer> minCPS = new Setting<>("MinCPS", 7, 1, 20).addToGroup(oldDelay);
    public final Setting<Integer> maxCPS = new Setting<>("MaxCPS", 12, 1, 20).addToGroup(oldDelay);

    public final Setting<ESP> esp = new Setting<>("ESP", ESP.ThunderHack);
    public final Setting<SettingGroup> espGroup = new Setting<>("ESPSettings", new SettingGroup(false, 0), v -> esp.is(ESP.ThunderHackV2));
    public final Setting<Integer> espLength = new Setting<>("ESPLength", 14, 1, 40, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Integer> espFactor = new Setting<>("ESPFactor", 8, 1, 20, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Float> espShaking = new Setting<>("ESPShaking", 1.8f, 1.5f, 10f, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Float> espAmplitude = new Setting<>("ESPAmplitude", 3f, 0.1f, 8f, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);

    public final Setting<Sort> sort = new Setting<>("Sort", Sort.LowestDistance);
    public final Setting<Boolean> lockTarget = new Setting<>("LockTarget", true);
    
    // --- ELYTRA TARGET EKLENTİSİ ---
    public final Setting<Boolean> elytraTarget = new Setting<>("ElytraTarget", true);

    /*   ADVANCED   */
    public final Setting<SettingGroup> advanced = new Setting<>("Advanced", new SettingGroup(false, 0));
    public final Setting<Float> aimRange = new Setting<>("AimRange", 3.1f, 0f, 6.0f).addToGroup(advanced);
    public final Setting<Boolean> randomHitDelay = new Setting<>("RandomHitDelay", false).addToGroup(advanced);
    public final Setting<Boolean> pauseInInventory = new Setting<>("PauseInInventory", true).addToGroup(advanced);
    public final Setting<Boolean> dropSprint = new Setting<>("DropSprint", true).addToGroup(advanced);
    public final Setting<Boolean> returnSprint = new Setting<>("ReturnSprint", true, v -> dropSprint.getValue()).addToGroup(advanced);
    public final Setting<RayTrace> rayTrace = new Setting<>("RayTrace", RayTrace.OnlyTarget).addToGroup(advanced);
    public final Setting<Boolean> grimRayTrace = new Setting<>("GrimRayTrace", true).addToGroup(advanced);
    public final Setting<Boolean> unpressShield = new Setting<>("UnpressShield", true).addToGroup(advanced);
    public final Setting<Boolean> deathDisable = new Setting<>("DisableOnDeath", true).addToGroup(advanced);
    public final Setting<Boolean> tpDisable = new Setting<>("TPDisable", false).addToGroup(advanced);
    public final Setting<Boolean> pullDown = new Setting<>("FastFall", false).addToGroup(advanced);
    public final Setting<Boolean> onlyJumpBoost = new Setting<>("OnlyJumpBoost", false, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<Float> pullValue = new Setting<>("PullValue", 3f, 0f, 20f, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<AttackHand> attackHand = new Setting<>("AttackHand", AttackHand.MainHand).addToGroup(advanced);
    public final Setting<Resolver> resolver = new Setting<>("Resolver", Resolver.Advantage).addToGroup(advanced);
    public final Setting<Integer> backTicks = new Setting<>("BackTicks", 4, 1, 20, v -> resolver.is(Resolver.BackTrack)).addToGroup(advanced);
    public final Setting<Boolean> resolverVisualisation = new Setting<>("ResolverVisualisation", false, v -> !resolver.is(Resolver.Off)).addToGroup(advanced);
    public final Setting<AccelerateOnHit> accelerateOnHit = new Setting<>("AccelerateOnHit", AccelerateOnHit.Off).addToGroup(advanced);
    public final Setting<Integer> minYawStep = new Setting<>("MinYawStep", 65, 1, 180).addToGroup(advanced);
    public final Setting<Integer> maxYawStep = new Setting<>("MaxYawStep", 75, 1, 180).addToGroup(advanced);
    public final Setting<Float> aimedPitchStep = new Setting<>("AimedPitchStep", 1f, 0f, 90f).addToGroup(advanced);
    public final Setting<Float> maxPitchStep = new Setting<>("MaxPitchStep", 8f, 1f, 90f).addToGroup(advanced);
    public final Setting<Float> pitchAccelerate = new Setting<>("PitchAccelerate", 1.65f, 1f, 10f).addToGroup(advanced);
    public final Setting<Float> attackCooldown = new Setting<>("AttackCooldown", 0.9f, 0.5f, 1f).addToGroup(advanced);
    public final Setting<Float> attackBaseTime = new Setting<>("AttackBaseTime", 0.5f, 0f, 2f).addToGroup(advanced);
    public final Setting<Integer> attackTickLimit = new Setting<>("AttackTickLimit", 11, 0, 20).addToGroup(advanced);
    public final Setting<Float> critFallDistance = new Setting<>("CritFallDistance", 0f, 0f, 1f).addToGroup(advanced);


    /*   TARGETS   */
    public final Setting<SettingGroup> targets = new Setting<>("Targets", new SettingGroup(false, 0));
    public final Setting<Boolean> Players = new Setting<>("Players", true).addToGroup(targets);
    public final Setting<Boolean> Mobs = new Setting<>("Mobs", true).addToGroup(targets);
    public final Setting<Boolean> Animals = new Setting<>("Animals", true).addToGroup(targets);
    public final Setting<Boolean> Villagers = new Setting<>("Villagers", true).addToGroup(targets);
    public final Setting<Boolean> Slimes = new Setting<>("Slimes", true).addToGroup(targets);
    public final Setting<Boolean> hostiles = new Setting<>("Hostiles", true).addToGroup(targets);
    public final Setting<Boolean> onlyAngry = new Setting<>("OnlyAngryHostiles", true, v -> hostiles.getValue()).addToGroup(targets);
    public final Setting<Boolean> Projectiles = new Setting<>("Projectiles", true).addToGroup(targets);
    public final Setting<Boolean> ignoreInvisible = new Setting<>("IgnoreInvisibleEntities", false).addToGroup(targets);
    public final Setting<Boolean> ignoreNamed = new Setting<>("IgnoreNamed", false).addToGroup(targets);
    public final Setting<Boolean> ignoreTeam = new Setting<>("IgnoreTeam", false).addToGroup(targets);
    public final Setting<Boolean> ignoreCreative = new Setting<>("IgnoreCreative", true).addToGroup(targets);
    public final Setting<Boolean> ignoreNaked = new Setting<>("IgnoreNaked", false).addToGroup(targets);
    public final Setting<Boolean> ignoreShield = new Setting<>("AttackShieldingEntities", true).addToGroup(targets);

    public static Entity target;

    public float rotationYaw;
    public float rotationPitch;
    public float pitchAcceleration = 1f;

    private Vec3d rotationPoint = Vec3d.ZERO;
    private Vec3d rotationMotion = Vec3d.ZERO;

    private int hitTicks;
    private int trackticks;
    private boolean lookingAtHitbox;

    private final Timer delayTimer = new Timer();
    private final Timer pauseTimer = new Timer();

    public Box resolvedBox;
    static boolean wasTargeted = false;

    public Aura() {
        super("Aura", Category.COMBAT);
    }

    private float getRange() {
        if (polarBypass.getValue()) return 2.95f; // POLAR REACH BYPASS
        return elytra.getValue() && mc.player.isFallFlying() ? elytraAttackRange.getValue() : attackRange.getValue();
    }

    private float getWallRange() {
        return elytra.getValue() && mc.player != null && mc.player.isFallFlying() ? elytraWallRange.getValue() : wallRange.getValue();
    }

    public void auraLogic() {
        if (!haveWeapon()) {
            target = null;
            return;
        }

        handleKill();
        updateTarget();

        if (target == null) {
            return;
        }

        if (!mc.options.jumpKey.isPressed() && mc.player.isOnGround() && autoJump.getValue())
            mc.player.jump();

        // --- ELYTRA INTERCEPT BYPASS ---
        if (elytraTarget.getValue() && target instanceof PlayerEntity playerTarget && mc.player.isFallFlying() && playerTarget.isFallFlying()) {
            Vec3d targetPos = playerTarget.getPos();
            Vec3d targetVel = playerTarget.getVelocity();
            Vec3d futureTargetPos = targetPos.add(targetVel.multiply(10)); // 10 tick sonrasını tahmin et

            Vec3d dir = futureTargetPos.subtract(mc.player.getPos()).normalize();
            double currentSpeed = Math.sqrt(targetVel.x * targetVel.x + targetVel.z * targetVel.z);
            double ourSpeed = currentSpeed * 1.15; // Önüne geçmek için %15 daha hızlı

            // Polar için yumuşatılmış hız uygulaması
            mc.player.setVelocity(dir.x * ourSpeed, targetVel.y - 0.05, dir.z * ourSpeed);
        }

        boolean readyForAttack;

        if (grimRayTrace.getValue()) {
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
            calcRotations(autoCrit());
        } else {
            calcRotations(autoCrit());
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
        }

        if (readyForAttack) {
            if (shieldBreaker(false))
                return;

            boolean[] playerState = preAttack();
            if (!(target instanceof PlayerEntity pl) || !(pl.isUsingItem() && pl.getOffHandStack().getItem() == Items.SHIELD) || ignoreShield.getValue())
                attack();

            postAttack(playerState[0], playerState[1]);
        }
    }

    private boolean haveWeapon() {
        Item handItem = mc.player.getMainHandStack().getItem();
        if (onlyWeapon.getValue()) {
            if (switchMode.getValue() == Switch.None) {
                return handItem instanceof SwordItem || handItem instanceof AxeItem || handItem instanceof TridentItem;
            } else {
                return (InventoryUtility.getSwordHotBar().found() || InventoryUtility.getAxeHotBar().found());
            }
        }
        return true;
    }

    private boolean skipRayTraceCheck() {
        return rotationMode.getValue() == Mode.None || rayTrace.getValue() == RayTrace.OFF
                || rotationMode.is(Mode.Grim)
                || (rotationMode.is(Mode.Interact) && (interactTicks.getValue() <= 1
                || mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1, 0.0)).iterator().hasNext()));
    }

    public void attack() {
        Criticals.cancelCrit = true;
        ModuleManager.criticals.doCrit();
        int prevSlot = switchMethod();
        mc.interactionManager.attackEntity(mc.player, target);
        Criticals.cancelCrit = false;
        swingHand();
        hitTicks = getHitTicks();
        if (prevSlot != -1)
            InventoryUtility.switchTo(prevSlot);
    }

    private boolean[] preAttack() {
        boolean blocking = mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getUseAction(mc.player.getActiveItem()) == BLOCK;
        if (blocking && unpressShield.getValue())
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));

        boolean sprint = Core.serverSprint;
        if (sprint && dropSprint.getValue())
            disableSprint();

        if (rotationMode.is(Mode.Grim))
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.isOnGround()));

        return new boolean[]{blocking, sprint};
    }

    public void postAttack(boolean block, boolean sprint) {
        if (sprint && returnSprint.getValue() && dropSprint.getValue())
            enableSprint();

        if (block && unpressShield.getValue())
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, rotationYaw, rotationPitch));

        if (rotationMode.is(Mode.Grim))
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
    }

    private void disableSprint() {
        mc.player.setSprinting(false);
        mc.options.sprintKey.setPressed(false);
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
    }

    private void enableSprint() {
        mc.player.setSprinting(true);
        mc.options.sprintKey.setPressed(true);
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
    }

    public void resolvePlayers() {
        if (resolver.not(Resolver.Off))
            for (PlayerEntity player : mc.world.getPlayers())
                if (player instanceof OtherClientPlayerEntity)
                    ((IOtherClientPlayerEntity) player).resolve(resolver.getValue());
    }

    public void restorePlayers() {
        if (resolver.not(Resolver.Off))
            for (PlayerEntity player : mc.world.getPlayers())
                if (player instanceof OtherClientPlayerEntity)
                    ((IOtherClientPlayerEntity) player).releaseResolver();
    }

    public void handleKill() {
        if (target instanceof LivingEntity && (((LivingEntity) target).getHealth() <= 0 || ((LivingEntity) target).isDead()))
            Managers.NOTIFICATION.publicity("Aura", isRu() ? "Цель успешно нейтрализована!" : "Target successfully neutralized!", 3, Notification.Type.SUCCESS);
    }

    private int switchMethod() {
        int prevSlot = -1;
        SearchInvResult swordResult = InventoryUtility.getSwordHotBar();
        if (swordResult.found() && switchMode.getValue() != Switch.None) {
            if (switchMode.getValue() == Switch.Silent)
                prevSlot = mc.player.getInventory().selectedSlot;
            swordResult.switchTo();
        }

        return prevSlot;
    }

    private int getHitTicks() {
        // --- POLAR CPS BYPASS ---
        if (polarBypass.getValue()) return 12; // 600ms delay
        
        return oldDelay.getValue().isEnabled() ? 1 + (int) (20f / random(minCPS.getValue(), maxCPS.getValue())) : (shouldRandomizeDelay() ? (int) MathUtility.random(11, 13) : attackTickLimit.getValue());
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem() && pauseWhileEating.getValue())
            return;
        if (pauseBaritone.getValue() && ThunderHack.baritone) {
            boolean isTargeted = (target != null);
            if (isTargeted && !wasTargeted) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
                wasTargeted = true;
            } else if (!isTargeted && wasTargeted) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("resume");
                wasTargeted = false;
            }
        }

        resolvePlayers();
        auraLogic();
        restorePlayers();
        hitTicks--;
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem() && pauseWhileEating.getValue())
           
