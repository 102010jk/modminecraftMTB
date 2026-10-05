package com.descentmtb.client.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.JumpProfiles.Params;
import com.descentmtb.trail.JumpProfiles.Type;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The jump profiles of the jump screen: six built-in ones (translated, cannot be deleted) and the player's own,
 * saved by name in {@code config/descentmtb/jump_profiles.json} on this computer. The file is created when missing
 * and re-read on every load, so it can also be edited by hand.
 */
public final class JumpProfileStore {
    /** One profile; built-in ones have a language key instead of a name. */
    public record Profile(String name, String key, Params params) {
        public boolean builtIn() {
            return key != null;
        }

        public Component label() {
            return builtIn() ? Component.translatable(key) : Component.literal(name);
        }
    }

    public static final List<Profile> BUILT_IN = List.of(
            builtIn("small_kicker", new Params(Type.KICKER, 2, 3, .75, 30, 3, 5)),
            builtIn("big_kicker", new Params(Type.KICKER, 3, 3, 1.5, 35, 3, 5)),
            builtIn("table", new Params(Type.TABLE, 3, 3, 1.25, 30, 4, 6)),
            builtIn("step_up", new Params(Type.STEP_UP, 3, 3, 2, 30, 3, 5)),
            builtIn("landing", new Params(Type.LANDING, 6, 3, 1.5, 35, 3, 5)),
            builtIn("roller", new Params(Type.ROLLER, 4, 3, 1, 35, 3, 5)));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Profile builtIn(String id, Params params) {
        return new Profile(null, "descentmtb.jump.preset." + id, params);
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(DescentMtb.MODID).resolve("jump_profiles.json");
    }

    /** The built-in profiles followed by the player's own (an unreadable file counts as empty). */
    public static List<Profile> load() {
        List<Profile> all = new ArrayList<>(BUILT_IN);
        all.addAll(own());
        return all;
    }

    private static List<Profile> own() {
        Path file = file();
        List<Profile> out = new ArrayList<>();
        try {
            if (!Files.exists(file)) {
                write(out);
                return out;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var element : root.getAsJsonArray("profiles")) {
                JsonObject o = element.getAsJsonObject();
                String name = o.get("name").getAsString().trim();
                if (name.isEmpty()) {
                    continue;
                }
                Params params = new Params(Type.fromName(o.get("type").getAsString()), o.get("length").getAsInt(),
                        o.get("width").getAsInt(), o.get("height").getAsDouble(), o.get("lip").getAsInt(),
                        o.get("deck").getAsInt(), o.get("landing").getAsInt()).clamped();
                out.add(new Profile(name, null, params));
            }
        } catch (IOException | RuntimeException e) {
            DescentMtb.LOG.warn("Could not read the jump profiles in {}: {}", file, e.toString());
        }
        return out;
    }

    /** Saves the settings under {@code name}, replacing a profile of the same name. */
    public static void save(String name, Params params) {
        List<Profile> own = own();
        own.removeIf(p -> p.name().equals(name));
        own.add(new Profile(name, null, params.clamped()));
        write(own);
    }

    /** Deletes the player's profile of that name (built-in ones cannot be deleted). */
    public static void delete(String name) {
        List<Profile> own = own();
        if (own.removeIf(p -> p.name().equals(name))) {
            write(own);
        }
    }

    private static void write(List<Profile> own) {
        JsonArray list = new JsonArray();
        for (Profile profile : own) {
            Params p = profile.params();
            JsonObject o = new JsonObject();
            o.addProperty("name", profile.name());
            o.addProperty("type", p.type().name());
            o.addProperty("length", p.length());
            o.addProperty("width", p.width());
            o.addProperty("height", p.height());
            o.addProperty("lip", p.lip());
            o.addProperty("deck", p.deck());
            o.addProperty("landing", p.landing());
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("profiles", list);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DescentMtb.LOG.warn("Could not save the jump profiles to {}: {}", file, e.toString());
        }
    }

    private JumpProfileStore() {}
}
