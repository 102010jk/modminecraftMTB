package com.descentmtb.client.model;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.BikeParts;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Records material metadata while baking, then binds it once to each baked model. */
public final class PartTable {
    public record Spec(String name, String baseName, String shapeCodes, BikeMat material, float sx, float sy, float sz) {}
    private final Map<PartDefinition,String> paths = new IdentityHashMap<>();
    private final Map<String,Spec> specs = new HashMap<>();
    private final Map<String,List<String>> children = new HashMap<>();

    public void start(PartDefinition root) { paths.clear(); specs.clear(); children.clear(); paths.put(root, ""); }
    public void bone(PartDefinition parent, PartDefinition child, String name) {
        paths.put(child, paths.get(parent) + "/" + name);
        children.computeIfAbsent(paths.get(parent), p -> new ArrayList<>()).add(name);
    }
    public void cube(PartDefinition parent, String name, String mat, float sx, float sy, float sz) {
        int split=name.indexOf("__");
        specs.put(paths.get(parent) + "/" + name, new Spec(name, split<0 ? name : name.substring(0,split),
                split<0 ? "" : name.substring(split+2), BikeMat.of(mat), sx, sy, sz));
        children.computeIfAbsent(paths.get(parent), p -> new ArrayList<>()).add(name);
    }
    public Node bind(ModelPart root) { return bind(root,"",null); }
    private Node bind(ModelPart part, String path, Spec spec) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        if (spec != null) part.visit(new PoseStack(), (pose,p,index,cube) -> cubes.add(cube));
        return new Node(part, spec, cubes.toArray(ModelPart.Cube[]::new), children.getOrDefault(path,List.of()).stream().map(name -> {
            String child = path + "/" + name;
            return bind(part.getChild(name), child, specs.get(child));
        }).toArray(Node[]::new));
    }

    public record Node(ModelPart part, Spec spec, ModelPart.Cube[] cubes, Node[] children) {
        public Node find(ModelPart target) {
            if (part == target) return this;
            for (Node child : children) {
                Node found = child.find(target);
                if (found != null) return found;
            }
            return null;
        }
        public void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                           BikeBuild build, ResourceLocation texture, ResourceLocation finish) {
            if (!part.visible || spec != null && !visible(spec,build)) return;
            pose.pushPose();
            part.translateAndRotate(pose);
            if (spec != null && !part.skipDraw) {
                var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(spec.material.frame() ? finish : texture));
                for (var cube : cubes) cube.compile(pose.last(), vc, spec.material == BikeMat.LENS ? 0xF000F0 : light,
                        overlay, spec.material.color(build));
                com.descentmtb.client.custom.BikeDecorations.render(pose,buffers,light,overlay,build,spec);
            }
            for (Node child : children) child.render(pose,buffers,light,overlay,build,texture,finish);
            pose.popPose();
        }
    }

    private static boolean visible(Spec s, BikeBuild b) {
        String name=s.name;
        if(!s.shapeCodes.isEmpty() && s.shapeCodes.indexOf(shapeCode(b.shape()))<0) return false;
        if(s.material==BikeMat.SPRING && !b.shock().coil()) return false;
        if(s.material==BikeMat.AIRCAN && b.shock().coil()) return false;
        if(!name.startsWith("acc_")) return true;
        if(name.startsWith("acc_flight_")) return b.frontLight();
        if(name.startsWith("acc_rlight_")) return b.rearLight();
        return switch(b.bell()) {
            case NONE -> false;
            case DING -> name.startsWith("acc_ding_");
            case MINI -> name.startsWith("acc_mini_");
            case CLASSIC -> name.startsWith("acc_classic_");
            case AIR_HORN -> name.startsWith("acc_horn_");
            case RUBBER_DUCK -> name.startsWith("acc_duck_");
        };
    }

    private static char shapeCode(BikeParts.FrameShape shape) {
        return switch(shape) {
            case ENDURO_CLASSIC, DJ_CLASSIC -> 'c';
            case ENDURO_HIGH_PIVOT -> 'h';
            case ENDURO_LOW_SLUNG -> 'l';
            case DJ_STRAIGHT -> 's';
            case DJ_CURVED -> 'v';
        };
    }
}
