package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeParts.HubType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * The riding sounds (hub, wind, tyres, suspension, rider, tricks). The samples are synthesised by
 * {@code tools/gen_sounds.py}; the events are declared in {@code assets/descentmtb/sounds.json}.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, DescentMtb.MODID);

    private static final Map<HubType, DeferredHolder<SoundEvent, SoundEvent>> HUB_CLICK = new EnumMap<>(HubType.class);
    private static final Map<HubType, DeferredHolder<SoundEvent, SoundEvent>> HUB_BUZZ = new EnumMap<>(HubType.class);

    static {
        for (HubType hub : HubType.values()) {
            if (hub.silent()) continue;
            HUB_CLICK.put(hub, event(hub.sound + ".click"));
            HUB_BUZZ.put(hub, event(hub.sound + ".buzz"));
        }
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = event("bike.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SOFT = event("bike.roll.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_HARD = event("bike.roll.hard");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_WOOD = event("bike.roll.wood");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUSPENSION_HISS = event("bike.suspension.hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM = event("bike.rider.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEEL_CLICK = event("bike.trick.heel_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARSPIN = event("bike.trick.barspin");
    public static final DeferredHolder<SoundEvent, SoundEvent> TAILWHIP = event("bike.trick.tailwhip");

    private static DeferredHolder<SoundEvent, SoundEvent> event(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, id)));
    }

    /** Single click of a hub (null for the silent hub). */
    public static SoundEvent hubClick(HubType hub) {
        var holder = HUB_CLICK.get(hub);
        return holder == null ? null : holder.get();
    }

    /** Looping buzz of a hub's freewheel (null for the silent hub). */
    public static SoundEvent hubBuzz(HubType hub) {
        var holder = HUB_BUZZ.get(hub);
        return holder == null ? null : holder.get();
    }

    private ModSounds() {}

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
