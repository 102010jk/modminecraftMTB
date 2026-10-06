package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.entity.BikeType;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Player library outside world saves. Copy the UUID folder to bring designs to another installation. */
final class BikeDesignStore {
    record Design(String name,BikeType type,BikeBuild build) {}
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("descentmtb/players")
                .resolve(Minecraft.getInstance().getUser().getProfileId().toString()).resolve("bike_designs.json");
    }
    static List<Design> load() {
        Path file=file();
        if(!Files.exists(file)) return List.of();
        try {
            if(Files.size(file)>2_000_000) throw new IllegalStateException("Design library is too large");
            var root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            List<Design> out=new ArrayList<>();
            for(var element:root.getAsJsonArray("designs")) {
                var o=element.getAsJsonObject();
                var type=BikeType.valueOf(o.get("type").getAsString());
                var build=BikeBuild.CODEC.parse(JsonOps.INSTANCE,o.get("build")).getOrThrow().sanitized(type==BikeType.ENDURO);
                out.add(new Design(BikeBuild.cleanText(o.get("name").getAsString(),48),type,build));
                if(out.size()>=100) break;
            }
            return List.copyOf(out);
        } catch(Exception e) { throw new IllegalStateException("Could not read bike designs; original file preserved",e); }
    }
    static void save(String name,BikeType type,BikeBuild build) {
        name=BikeBuild.cleanText(name,48);
        if(name.isEmpty()) throw new IllegalArgumentException("Name required");
        List<Design> all=new ArrayList<>(load());
        String key=name;
        all.removeIf(d->d.name.equalsIgnoreCase(key) && d.type==type);
        if(all.size()>=100) throw new IllegalStateException("100 designs maximum");
        all.add(new Design(name,type,build.sanitized(type==BikeType.ENDURO)));
        write(all);
    }
    static void delete(String name,BikeType type) {
        List<Design> all=new ArrayList<>(load());
        all.removeIf(d->d.name.equalsIgnoreCase(name.strip()) && d.type==type);
        write(all);
    }
    private static void write(List<Design> all) {
        var root=new JsonObject();root.addProperty("version",1);var array=new JsonArray();
        for(var d:all) {
            var o=new JsonObject();o.addProperty("name",d.name);o.addProperty("type",d.type.name());
            o.add("build",BikeBuild.CODEC.encodeStart(JsonOps.INSTANCE,d.build).getOrThrow());array.add(o);
        }
        root.add("designs",array);Path target=file(),temp=target.resolveSibling("bike_designs.json.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temp,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);
            try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch(Exception e) { throw new IllegalStateException("Could not save bike designs",e); }
    }
    private BikeDesignStore() {}
}
