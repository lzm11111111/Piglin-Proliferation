package tallestred.piglinproliferation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import tallestred.piglinproliferation.capablities.PPCapablities;
import tallestred.piglinproliferation.client.PPSounds;
import tallestred.piglinproliferation.common.blocks.PPBlocks;
import tallestred.piglinproliferation.common.enchantments.PPEnchantments;
import tallestred.piglinproliferation.common.entities.PPEntityTypes;
import tallestred.piglinproliferation.common.entities.PiglinTraveller;
import tallestred.piglinproliferation.common.entities.ai.goals.DumbBowAttackGoal;
import tallestred.piglinproliferation.common.entities.ai.goals.DumbCrossbowAttackGoal;
import tallestred.piglinproliferation.common.entities.ai.goals.PiglinCallForHelpGoal;
import tallestred.piglinproliferation.common.entities.ai.goals.PiglinSwimInLavaGoal;
import tallestred.piglinproliferation.common.items.BucklerItem;
import tallestred.piglinproliferation.common.items.PPItems;
import tallestred.piglinproliferation.common.loot.CompassLocationMap;
import tallestred.piglinproliferation.common.tags.PPTags;
import tallestred.piglinproliferation.configuration.PPConfig;
import tallestred.piglinproliferation.networking.CriticalCapabilityPacket;
import tallestred.piglinproliferation.networking.ZiglinCapablitySyncPacket;

import java.util.*;

@Mod.EventBusSubscriber(modid = PiglinProliferation.MODID)
public class PPEvents {
    private static final UUID CHARGE_SPEED_UUID = UUID.fromString("A2F995E8-B25A-4883-B9D0-93A676DC4045");
    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("93E74BB2-05A5-4AC0-8DF5-A55768208A95");
    private static final AttributeModifier CHARGE_SPEED_BOOST = new AttributeModifier(CHARGE_SPEED_UUID, "Charge speed boost", 9.0D, AttributeModifier.Operation.MULTIPLY_BASE);
    private static final AttributeModifier KNOCKBACK_RESISTANCE = new AttributeModifier(KNOCKBACK_RESISTANCE_UUID, "Knockback reduction", 1.0D, AttributeModifier.Operation.ADDITION);

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (BucklerItem.getChargeTicks(PPItems.checkEachHandForBuckler(event.getEntity())) > 0) {
            event.getEntity().setDeltaMovement(event.getEntity().getDeltaMovement().x(), 0.0D, event.getEntity().getDeltaMovement().z());
        }
    }

    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ZombifiedPiglin ziglin) {
            if (!event.getEntity().level().isClientSide) {
                PacketDistributor.TRACKING_ENTITY.with(ziglin).send(new ZiglinCapablitySyncPacket(ziglin.getId(), ziglin.getData(PPCapablities.TRANSFORMATION_TRACKER.get())));
            }
            ziglin.goalSelector.addGoal(2, new DumbBowAttackGoal(ziglin, 0.5D, 20, 15.0F));
            ziglin.goalSelector.addGoal(2, new DumbCrossbowAttackGoal(ziglin, 1.0D, 8.0F));
        }
        if (event.getEntity() instanceof AbstractPiglin piglin) {
            piglin.goalSelector.addGoal(0, new PiglinCallForHelpGoal(piglin, (piglin1) -> piglin1.isOnFire() && !piglin1.hasEffect(MobEffects.FIRE_RESISTANCE), (alchemist -> alchemist.getItemShownOnOffhand() != null && PotionUtils.getPotion(alchemist.getItemShownOnOffhand()) == Potions.FIRE_RESISTANCE)));
            piglin.goalSelector.addGoal(0, new PiglinCallForHelpGoal(piglin, (piglin1) -> piglin1.getHealth() < piglin1.getMaxHealth() && !piglin1.hasEffect(MobEffects.REGENERATION), (alchemist -> alchemist.getItemShownOnOffhand() != null && PotionUtils.getPotion(alchemist.getItemShownOnOffhand()) == Potions.STRONG_REGENERATION)));
            piglin.goalSelector.addGoal(0, new PiglinCallForHelpGoal(piglin, (piglin1) -> piglin1.getHealth() < piglin1.getMaxHealth() && !piglin1.hasEffect(MobEffects.HEAL), (alchemist -> alchemist.getItemShownOnOffhand() != null && PotionUtils.getPotion(alchemist.getItemShownOnOffhand()) == Potions.STRONG_HEALING)));
            piglin.goalSelector.addGoal(0, new PiglinCallForHelpGoal(piglin, (piglin1) -> piglin1.getHealth() < (piglin1.getMaxHealth() / 2) && piglin1.getTarget() != null && !piglin1.hasEffect(MobEffects.DAMAGE_BOOST), (alchemist -> alchemist.getItemShownOnOffhand() != null && PotionUtils.getPotion(alchemist.getItemShownOnOffhand()) == Potions.STRONG_STRENGTH)));
            piglin.goalSelector.addGoal(1, new PiglinSwimInLavaGoal(piglin));
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ZombifiedPiglin ziglin) {
            if (!event.getTarget().level().isClientSide) {
                PacketDistributor.TRACKING_ENTITY.with(ziglin).send(new ZiglinCapablitySyncPacket(ziglin.getId(), ziglin.getData(PPCapablities.TRANSFORMATION_TRACKER.get())));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        int turningLevel = PPEnchantments.getBucklerEnchantsOnHands(PPEnchantments.TURNING.get(), entity);
        ItemStack bucklerItemStack = PPItems.checkEachHandForBuckler(entity);
        boolean bucklerReadyToCharge = BucklerItem.isReady(bucklerItemStack);
        int bucklerChargeTicks = BucklerItem.getChargeTicks(bucklerItemStack);
        if (bucklerReadyToCharge) {
            BucklerItem.setChargeTicks(bucklerItemStack, bucklerChargeTicks - 1);
            if (bucklerChargeTicks > 0) {
                BucklerItem.moveFowards(entity);
                BucklerItem.spawnRunningEffectsWhileCharging(entity);
                if (turningLevel == 0 && !entity.level().isClientSide()) BucklerItem.bucklerBash(entity);
            }
        }
        if (bucklerChargeTicks <= 0 && bucklerReadyToCharge || entity.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CHARGE_SPEED_BOOST)
                && (!(bucklerItemStack.getItem() instanceof BucklerItem) || !bucklerReadyToCharge)) {
            AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            AttributeInstance knockback = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (speed == null || knockback == null) {
                return;
            }
            knockback.removeModifier(KNOCKBACK_RESISTANCE_UUID);
            speed.removeModifier(CHARGE_SPEED_UUID);
            entity.stopUsingItem();
            if (entity instanceof Player player) {
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    if (player.getInventory().getItem(slot).getItem() instanceof BucklerItem) {
                        BucklerItem.setChargeTicks(player.getInventory().getItem(slot), 0);
                        BucklerItem.setReady(player.getInventory().getItem(slot), false);
                    }
                }
            }
            BucklerItem.setChargeTicks(bucklerItemStack, 0);
            BucklerItem.setReady(bucklerItemStack, false);
        }
        boolean criticalAfterCharge = entity.getData(PPCapablities.CRITICAL.get());
        if (criticalAfterCharge) {
            if (entity.swingTime > 0) {
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), PPSounds.CRITICAL_DEACTIVATE.get(), entity.getSoundSource(), 1.0F, 0.8F + entity.getRandom().nextFloat() * 0.4F);
                entity.setData(PPCapablities.CRITICAL.get(), false);
            }
            for (int i = 0; i < 2; ++i) {
                entity.level().addParticle(ParticleTypes.CRIT, entity.getRandomX(0.5D), entity.getRandomY(), entity.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
            }
        }
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.PLAYER.with(player).send(new CriticalCapabilityPacket(player.getId(), criticalAfterCharge));
    }

    @SubscribeEvent
    public static void onEffectApplied(MobEffectEvent.Added event) {
        event.getEffectInstance();
        MobEffect mobEffect = event.getEffectInstance().getEffect();
        if (event.getEntity() instanceof AbstractPiglin piglin) {
            if (mobEffect == MobEffects.FIRE_RESISTANCE) {
                piglin.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
                piglin.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
                piglin.setPathfindingMalus(BlockPathTypes.LAVA, 0.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() == null)
            return;
        MobEffect mobEffect = event.getEffectInstance().getEffect();
        if (event.getEntity() instanceof AbstractPiglin piglin) {
            if (mobEffect == MobEffects.FIRE_RESISTANCE) {
                piglin.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
                piglin.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
                piglin.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
            }
        }
    }

    @SubscribeEvent
    public static void hurtEntity(LivingHurtEvent event) {
        if (event.getSource().getDirectEntity() instanceof Arrow arrow && PPConfig.COMMON.healingArrowDamage.get()) {
            for (MobEffectInstance mobeffectinstance : arrow.potion.getEffects()) {
                if ((mobeffectinstance.getEffect() == MobEffects.REGENERATION || mobeffectinstance.getEffect() == MobEffects.HEAL)) {
                    if ((event.getEntity() instanceof Mob && event.getEntity().isInvertedHealAndHarm()))
                        return;
                    event.setAmount(0.0F);
                    arrow.level().playSound(null, arrow.blockPosition(), PPSounds.REGEN_HEALING_ARROW_HIT.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
                    event.getEntity().setDeltaMovement(event.getEntity().getDeltaMovement().multiply(-1.0D, -1.0D, -1.0D));
                    event.getEntity().invulnerableTime = 0;
                    event.getEntity().hurtTime = 0;
                }
            }
        }
    }

    @SubscribeEvent
    public static void attackEntity(LivingAttackEvent event) {
        // Testing bygone nether compatibility lead me to discover that alchemists healing piglin hunters leads to them attacking each other since the
        // horses they're riding on are considered undead, this should work as a quick fix for that, but further discussions with the mod creator is needed.
        if (event.getEntity() instanceof Mob mob) {
            for (Entity rider : mob.getPassengers()) {
                if (mob.isInvertedHealAndHarm() && event.getSource().getEntity() instanceof AbstractPiglin && rider instanceof AbstractPiglin piglin && event.getSource().is(DamageTypes.MAGIC)) {
                    if (event.getEntity().level().isClientSide)
                        return;
                    piglin.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void targetMob(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof AbstractPiglin) {
            if (event.getOriginalTarget() instanceof AbstractPiglin) {
                event.setCanceled(true);
            }
        }
        if (event.getEntity() instanceof ZombifiedPiglin) {
            if (event.getOriginalTarget() instanceof ZombifiedPiglin) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.getData(PPCapablities.CRITICAL.get())) {
            event.setResult(Event.Result.ALLOW);
            event.setDamageModifier(1.5F);
            Entity entity = event.getEntity();
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, entity.getSoundSource(), 1.0F, 1.0F);
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), PPSounds.CRITICAL_APPLY.get(), entity.getSoundSource(), 1.0F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
            player.setData(PPCapablities.CRITICAL.get(), false);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity().getUseItem().getItem() instanceof BucklerItem)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType spawnType = event.getSpawnType();
        RandomSource rSource = event.getLevel().getRandom();
        if (event.getEntity() instanceof Strider strider && rSource.nextInt(60) == 0 && !strider.isBaby()) {
            event.setCanceled(true);
            PiglinTraveller traveller = PPEntityTypes.PIGLIN_TRAVELLER.get().create(strider.level());
            traveller.copyPosition(strider);
            traveller.startRiding(strider);
            strider.equipSaddle(null);
            traveller.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK));
        }
        if (event.getEntity() instanceof PiglinBrute piglinBrute) {
            if (!PPConfig.COMMON.BruteBuckler.get()) return;
            piglinBrute.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(PPItems.BUCKLER.get()));
            ItemStack itemstack = piglinBrute.getOffhandItem();
            if (itemstack.getItem() instanceof BucklerItem) {
                if (rSource.nextInt(300) == 0) {
                    Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(itemstack);
                    map.putIfAbsent(PPEnchantments.TURNING.get(), 1);
                    EnchantmentHelper.setEnchantments(map, itemstack);
                    piglinBrute.setItemSlot(EquipmentSlot.OFFHAND, itemstack);
                }
                if (rSource.nextInt(500) == 0) {
                    Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(itemstack);
                    map.putIfAbsent(PPEnchantments.BANG.get(), 1);
                    EnchantmentHelper.setEnchantments(map, itemstack);
                    piglinBrute.setItemSlot(EquipmentSlot.OFFHAND, itemstack);
                }
            }
        }
        if (event.getEntity().getType() == EntityType.ZOMBIFIED_PIGLIN) { // Some mods have entities that extend zombified piglins in order to make their own ziglins have custom textures
            ZombifiedPiglin zombifiedPiglin = (ZombifiedPiglin) event.getEntity();
            if (spawnType != MobSpawnType.CONVERSION) {
                if (rSource.nextFloat() < PPConfig.COMMON.zombifiedPiglinDefaultChance.get().floatValue())
                    zombifiedPiglin.setData(PPCapablities.TRANSFORMATION_TRACKER.get(), "piglin");
                if (rSource.nextFloat() < PPConfig.COMMON.piglinVariantChances.get().floatValue()) {
                    List<? extends String> piglinTypes = PPConfig.COMMON.zombifiedPiglinTypeList.get();
                    if (!piglinTypes.isEmpty())
                        zombifiedPiglin.setData(PPCapablities.TRANSFORMATION_TRACKER.get(), piglinTypes.get(rSource.nextInt(piglinTypes.size())));
                }
                float bruteChance = PPConfig.COMMON.zombifiedBruteChance.get().floatValue();
                if (zombifiedPiglin.getData(PPCapablities.TRANSFORMATION_TRACKER.get()).equalsIgnoreCase("piglin")) {
                    if (rSource.nextFloat() < bruteChance) {
                        event.setCanceled(true);
                        zombifiedPiglin.setData(PPCapablities.TRANSFORMATION_TRACKER.get(), "piglin_brute");
                        zombifiedPiglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
                        zombifiedPiglin.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(PPItems.BUCKLER.get()));
                    } else if (rSource.nextFloat() < PPConfig.COMMON.zombifiedAlchemistChance.get().floatValue()) {
                        event.setCanceled(true);
                        zombifiedPiglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                        zombifiedPiglin.setData(PPCapablities.TRANSFORMATION_TRACKER.get(), "piglin_alchemist");
                    } else if (rSource.nextFloat() < PPConfig.COMMON.crossbowChance.get().floatValue()) {
                        event.setCanceled(true);
                        zombifiedPiglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                    }
                }
                if (spawnType == MobSpawnType.JOCKEY) {
                    event.setCanceled(true);
                    zombifiedPiglin.setData(PPCapablities.TRANSFORMATION_TRACKER.get(), "piglin_traveller");
                    zombifiedPiglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK));
                }
            }
        }
    }

    @SubscribeEvent
    public static void visionPercent(LivingEvent.LivingVisibilityEvent event) {
        if (event.getLookingEntity() != null) {
            ItemStack itemstack = event.getEntity().getItemBySlot(EquipmentSlot.HEAD);
            if (event.getLookingEntity() instanceof AbstractPiglin && (itemstack.is(PPItems.PIGLIN_ALCHEMIST_HEAD_ITEM.get()) || itemstack.is(PPItems.PIGLIN_BRUTE_HEAD_ITEM.get())) || itemstack.is(PPItems.ZOMBIFIED_PIGLIN_HEAD_ITEM.get()) || itemstack.is(PPItems.PIGLIN_TRAVELLER_HEAD_ITEM.get())) {
                event.modifyVisibility(0.5D);
            }
        }
    }

    @SubscribeEvent
    public static void onConvert(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof AbstractPiglin piglin && event.getOutcome().getType() == EntityType.ZOMBIFIED_PIGLIN) { // Some mods have entities that extend zombified piglins in order to make their own ziglins have custom textures
            if (piglin.level().isClientSide)
                return;
            ZombifiedPiglin ziglin = (ZombifiedPiglin) event.getOutcome();
            Optional<Registry<EntityType<?>>> registryOptional = piglin.level().registryAccess().registry(Registries.ENTITY_TYPE);
            if (registryOptional.isPresent()) {
                ResourceLocation location = registryOptional.get().getKey(piglin.getType());
                if (location != null) {
                    String piglinName = location.getPath();
                    ziglin.setData(PPCapablities.TRANSFORMATION_TRACKER.get(), piglinName);
                    PacketDistributor.TRACKING_ENTITY.with(ziglin).send(new ZiglinCapablitySyncPacket(ziglin.getId(), piglinName));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLootDropEntity(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof Creeper creeper) {
            if (creeper.canDropMobsSkull()) {
                EntityType<?> type = event.getEntity().getType();
                if (type == EntityType.ZOMBIFIED_PIGLIN)
                    event.getEntity().spawnAtLocation(PPItems.ZOMBIFIED_PIGLIN_HEAD_ITEM.get());
                else if (type == EntityType.PIGLIN_BRUTE)
                    event.getEntity().spawnAtLocation(PPItems.PIGLIN_BRUTE_HEAD_ITEM.get());
                creeper.increaseDroppedSkulls();
            }
        }
        if (event.getSource().getDirectEntity() instanceof Fireball fireBall && fireBall.getOwner() instanceof Ghast) {
            EntityType<?> type = event.getEntity().getType();
            if (event.getEntity().getType() == EntityType.PIGLIN)
                event.getEntity().spawnAtLocation(Items.PIGLIN_HEAD);
            else if (type == EntityType.ZOMBIFIED_PIGLIN)
                event.getEntity().spawnAtLocation(PPItems.ZOMBIFIED_PIGLIN_HEAD_ITEM.get());
            else if (event.getEntity().getType() == EntityType.PIGLIN_BRUTE)
                event.getEntity().spawnAtLocation(PPItems.PIGLIN_BRUTE_HEAD_ITEM.get());
        }
        if (event.getEntity() instanceof PiglinBrute brute) {
            ItemStack itemstack = brute.getOffhandItem();
            if (itemstack.getItem() instanceof BucklerItem) {
                float f = 0.10F;
                boolean flag = f > 1.0F;
                if (!itemstack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemstack) && (event.isRecentlyHit() || flag) && Math.max(brute.getRandom().nextFloat() - (float) event.getLootingLevel() * 0.01F, 0.0F) < f) {
                    if (itemstack.isDamageableItem()) {
                        int halvedMaxDurability = Math.abs(brute.getRandom().nextInt(Math.abs(itemstack.getMaxDamage() / 2)));
                        itemstack.setDamageValue(Math.abs(brute.getRandom().nextInt(halvedMaxDurability)));
                    }
                    brute.spawnAtLocation(itemstack);
                    brute.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                }
            }
        }
    }

    @SubscribeEvent
    public static void noteBlockPlay(NoteBlockEvent.Play event) {
        BlockState stateAbove = event.getLevel().getBlockState(event.getPos().above());
        if (stateAbove.is(PPBlocks.PIGLIN_ALCHEMIST_HEAD.get())) {
            event.setCanceled(true);
            event.getLevel().playSound(null, event.getPos(), PPSounds.ALCHEMIST_ANGRY.get(), SoundSource.RECORDS);
        }
        if (stateAbove.is(PPBlocks.ZOMBIFIED_PIGLIN_HEAD.get())) {
            event.setCanceled(true);
            event.getLevel().playSound(null, event.getPos(), SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, SoundSource.RECORDS);
        }
        if (stateAbove.is(PPBlocks.PIGLIN_BRUTE_HEAD.get())) {
            event.setCanceled(true);
            event.getLevel().playSound(null, event.getPos(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.RECORDS);
        }
        if (stateAbove.is(PPBlocks.PIGLIN_TRAVELLER_HEAD.get())) {
            event.setCanceled(true);
            event.getLevel().playSound(null, event.getPos(), PPSounds.TRAVELLER_ANGRY.get(), SoundSource.RECORDS);
        }
    }

    @SubscribeEvent
    public static void modifyItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() == PPItems.BUCKLER.get()) {
            List<Component> toAdd = new ArrayList<>();
            toAdd.add(Component.empty());
            toAdd.addAll(BucklerItem.getDescription(Minecraft.getInstance(), stack));
            event.getToolTip().addAll(toAdd);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        CompassLocationMap.clearCache();
        PPTags.TRAVELLERS_COMPASS_VALID_STRUCTURES.clearCache();
    }
}
