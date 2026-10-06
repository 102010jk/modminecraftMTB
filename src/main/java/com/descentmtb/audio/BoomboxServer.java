package com.descentmtb.audio;

import com.descentmtb.DescentMtb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server side of the boomboxes: who plays what where, validation, and relaying an owner's desktop stream to the
 * players in earshot. One active boombox per player (starting another stops the previous one).
 */
@EventBusSubscriber(modid = DescentMtb.MODID)
public final class BoomboxServer {
    private BoomboxServer() {}

    public static final ResourceLocation BOOMBOX_BLOCK = ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "boombox");
    public static final ResourceLocation HEADPHONES_ITEM = ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "headphones");

    private record Key(ResourceKey<Level> dim, Emitter emitter) {}

    private static final Map<Key, BoomboxState> STATES = new HashMap<>();
    private static final Map<UUID, Key> BY_OWNER = new HashMap<>();
    private static final Map<UUID, double[]> BUCKET = new HashMap<>(); // {tokens, lastNanos}
    private static final Map<Key, Long> DEADLINES = new HashMap<>();

    /** Reach for starting/stopping a boombox by hand. */
    private static final double REACH = 10;

    // ------------------------------------------------------------------ queries

    /** Where an emitter is in {@code level}, or null when it no longer exists (block broken, bike gone or bare). */
    public static Vec3 position(Level level, Emitter e) {
        if (e.kind() == Emitter.Kind.BLOCK) {
            BlockPos pos = e.pos();
            if (!level.isLoaded(pos)) return null;
            if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(BOOMBOX_BLOCK)) return null;
            return Vec3.atCenterOf(pos);
        }
        Entity ent = level.getEntity(e.entityId());
        if (ent == null || !ent.isAlive() || !(ent instanceof BoomboxHolder h) || !h.hasBoombox()) return null;
        return ent.position().add(0, 0.9, 0);
    }

    public static boolean isHeadphones(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(HEADPHONES_ITEM);
    }

    // ------------------------------------------------------------------ packets

    static void control(ServerPlayer player, AudioNet.ControlC2S m) {
        ServerLevel level = player.serverLevel();
        Vec3 at = position(level, m.emitter());
        if (at == null) return;
        boolean riding = m.emitter().kind() == Emitter.Kind.BIKE && player.getVehicle() != null
                && player.getVehicle().getId() == m.emitter().entityId();
        if (!riding && player.position().distanceTo(at) > REACH) return;
        Key key = new Key(level.dimension(), m.emitter());
        BoomboxState current = STATES.get(key);
        if (current != null && !current.owner().equals(player.getUUID()) && !player.getAbilities().instabuild) {
            PacketDistributor.sendToPlayer(player,new AudioNet.StateS2C(current));return;
        }
        if (!m.start()) {
            BoomboxState cur = STATES.get(key);
            if (cur != null) stop(level.getServer(), key);
            return;
        }
        if (m.mode() == BoomboxState.Mode.DISC) {
            if (m.disc().isEmpty() || level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).get(m.disc().get()) == null) return;
            boolean owns = player.getInventory().items.stream().anyMatch(stack -> net.minecraft.world.item.JukeboxSong.fromStack(level.registryAccess(),stack)
                    .flatMap(net.minecraft.core.Holder::unwrapKey).map(k -> k.location().equals(m.disc().get())).orElse(false));
            if (!owns) return;
        }
        Key previous = BY_OWNER.get(player.getUUID());
        if (previous != null && !previous.equals(key)) stop(level.getServer(), previous);
        BoomboxState old = STATES.get(key);
        if (old != null && old.active() && !old.owner().equals(player.getUUID())) BY_OWNER.remove(old.owner());
        BoomboxState s = new BoomboxState(m.emitter(), true, player.getUUID(), player.getGameProfile().getName(), m.mode(),
                m.disc(), m.radius(), m.volume(), m.label()).sanitized();
        STATES.put(key, s);
        BY_OWNER.put(player.getUUID(), key);
        DEADLINES.put(key, level.getGameTime() + (s.mode() == BoomboxState.Mode.DISC
                ? level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).get(s.disc().orElseThrow()).lengthInTicks() : 100));
        PacketDistributor.sendToPlayersInDimension(level, new AudioNet.StateS2C(s));
    }

    static void chunk(ServerPlayer player, AudioNet.ChunkC2S m) {
        if (m.data().length == 0 || m.data().length > AudioNet.MAX_CHUNK) return;
        ServerLevel level = player.serverLevel();
        Key key = new Key(level.dimension(), m.emitter());
        BoomboxState s = STATES.get(key);
        if (s == null || !s.active() || s.mode() != BoomboxState.Mode.APP || !s.owner().equals(player.getUUID())) return;
        if (!takeToken(player.getUUID())) return;
        DEADLINES.put(key,level.getGameTime()+100);
        Vec3 at = position(level, m.emitter());
        if (at == null) return;
        double range = s.radius() + 16;
        AudioNet.ChunkS2C out = new AudioNet.ChunkS2C(m.emitter(), m.seq(), m.data());
        for (ServerPlayer p : level.players()) {
            if (p != player && p.position().distanceToSqr(at) <= range * range) PacketDistributor.sendToPlayer(p, out);
        }
    }

    static void headphones(ServerPlayer player, AudioNet.HeadphonesC2S m) {
        if (m.action() != AudioNet.HeadphonesC2S.DROP) return;
        dropHeadphones(player);
    }

    public static void dropHeadphones(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!isHeadphones(head)) return;
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.2, player.getZ(), head.copy());
        Vec3 v = player.getDeltaMovement();
        item.setDeltaMovement(v.x * 0.6 + (player.getRandom().nextDouble() - .5) * .3, 0.25, v.z * 0.6 + (player.getRandom().nextDouble() - .5) * .3);
        item.setPickUpDelay(30);
        player.level().addFreshEntity(item);
    }

    /** 30 chunks per second sustained (25 needed), bursts of 15. */
    private static boolean takeToken(UUID id) {
        long now = System.nanoTime();
        double[] b = BUCKET.computeIfAbsent(id, k -> new double[]{15, now});
        b[0] = Math.min(15, b[0] + (now - b[1]) / 1e9 * 30);
        b[1] = now;
        if (b[0] < 1) return false;
        b[0] -= 1;
        return true;
    }

    private static void stop(net.minecraft.server.MinecraftServer server, Key key) {
        BoomboxState s = STATES.remove(key);
        DEADLINES.remove(key);
        if (s != null) BY_OWNER.remove(s.owner(), key);
        ServerLevel level = server.getLevel(key.dim());
        if (level != null) PacketDistributor.sendToPlayersInDimension(level, new AudioNet.StateS2C(BoomboxState.stopped(key.emitter())));
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0 || STATES.isEmpty()) return;
        List<Key> dead = new ArrayList<>();
        for (Iterator<Map.Entry<Key, BoomboxState>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, BoomboxState> en = it.next();
            ServerLevel level = event.getServer().getLevel(en.getKey().dim());
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(en.getValue().owner());
            if (level == null || owner == null || owner.level().dimension()!=en.getKey().dim()
                    || level.getGameTime()>DEADLINES.getOrDefault(en.getKey(),0L) || position(level, en.getKey().emitter()) == null) dead.add(en.getKey());
        }
        for (Key k : dead) stop(event.getServer(), k);
    }

    @SubscribeEvent
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendAll(p);
    }

    @SubscribeEvent
    static void changeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Key key=BY_OWNER.get(p.getUUID()); if(key!=null)stop(p.getServer(),key);
            sendAll(p);
        }
    }

    @SubscribeEvent static void shutdown(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        STATES.clear(); BY_OWNER.clear(); BUCKET.clear(); DEADLINES.clear();
    }

    @SubscribeEvent
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Key k = BY_OWNER.get(event.getEntity().getUUID());
        if (k != null && event.getEntity().getServer() != null) stop(event.getEntity().getServer(), k);
        BUCKET.remove(event.getEntity().getUUID());
    }

    private static void sendAll(ServerPlayer p) {
        for (Map.Entry<Key, BoomboxState> en : STATES.entrySet()) {
            if (en.getKey().dim().equals(p.level().dimension())) PacketDistributor.sendToPlayer(p, new AudioNet.StateS2C(en.getValue()));
        }
    }

    /** The active state of an emitter (server side), if any. */
    public static Optional<BoomboxState> state(Level level, Emitter e) {
        return Optional.ofNullable(STATES.get(new Key(level.dimension(), e)));
    }
}
