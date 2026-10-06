package com.descentmtb.client.audio;

import com.descentmtb.audio.*;
import com.descentmtb.client.audio.win.WinAudioSessions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class AudioSettingsScreen extends Screen {
    private final Emitter emitter;
    private List<WinAudioSessions.Session> sources=List.of();
    private String status="";
    private int radius=20, page;
    private float volume=1;
    public AudioSettingsScreen(Emitter emitter) {
        super(Component.translatable("descentmtb.audio.title")); this.emitter=emitter;
        if (emitter == null) volume=AudioClient.personalVolume();
        else {
            BoomboxState state=AudioClient.stateOf(emitter);
            if (state != null) { radius=state.radius(); volume=state.volume(); }
        }
    }
    @Override protected void init() { refresh(); layout(); }
    private Component t(String key) { return Component.translatable("descentmtb.audio."+key); }
    private void refresh() {
        status=DesktopCapture.supported()?t("loading").getString():t("windows").getString();
        DesktopCapture.listSources().whenComplete((list,error)->minecraft.execute(()->{
            if(minecraft.screen!=this)return;
            sources=error==null?list:List.of(); status=error==null?(sources.isEmpty()?t("empty").getString():""):error.getMessage(); layout();
        }));
    }
    private void layout() {
        clearWidgets(); int x=width/2-155,y=height/2-85;
        addRenderableWidget(Button.builder(t("refresh"),b->refresh()).bounds(x,y,95,20).build());
        addRenderableWidget(Button.builder(Component.literal(radius+" m"),b->{radius=radius>=32?10:Math.min(32,radius+2);layout();}).bounds(x+100,y,100,20).build());
        addRenderableWidget(Button.builder(Component.literal(Math.round(volume*100)+" %"),b->{volume=volume>=1.99f?.2f:Math.round((volume+.2f)*10)/10f;layout();}).bounds(x+205,y,105,20).build());
        for(int i=0;i<4;i++) {
            int index=page*4+i;if(index>=sources.size())break;
            var source=sources.get(index);String label=AudioApps.cleanTitle(source.title());if(label.isBlank())label=AudioApps.displayName(source.exePath());
            Button button=Button.builder(Component.literal(label),b->{AudioClient.start(emitter,source,radius,volume);onClose();}).bounds(x,y+27+i*24,310,20).build();
            button.active=emitter!=null||AudioClient.wearing(); addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(t("next"),b->{page=(page+1)%Math.max(1,(sources.size()+3)/4);layout();}).bounds(x,y+126,75,20).build());
        addRenderableWidget(Button.builder(t("stop"),b->AudioClient.stop(emitter)).bounds(x+80,y+126,105,20).build());
        Button disc=Button.builder(t("disc"),b->playDisc()).bounds(x+190,y+126,120,20).build();disc.active=emitter!=null;addRenderableWidget(disc);
        addRenderableWidget(Button.builder(t("close"),b->onClose()).bounds(x+80,y+151,150,20).build());
    }
    private void playDisc() {
        for(var stack:minecraft.player.getInventory().items) {
            var song=JukeboxSong.fromStack(minecraft.level.registryAccess(),stack);
            if(song.isEmpty()||song.get().unwrapKey().isEmpty())continue;
            AudioClient.stop(null);
            String label=song.get().value().description().getString();if(label.length()>64)label=label.substring(0,64);
            PacketDistributor.sendToServer(new AudioNet.ControlC2S(emitter,true,BoomboxState.Mode.DISC,Optional.of(song.get().unwrapKey().get().location()),radius,volume,label));
            onClose();return;
        }
        status=t("no_disc").getString();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g,mouseX,mouseY,partialTick);
        g.drawCenteredString(font,title,width/2,height/2-112,0xfff1bf);
        g.drawCenteredString(font,status,width/2,height/2+95,0xffc078);
        if(emitter==null&&!AudioClient.wearing())g.drawCenteredString(font,t("equip"),width/2,height/2-99,0xffc078);
        // Screen.render renders the blurred background again; draw widgets without covering our text.
        for (var widget : renderables) widget.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
