package com.kelp.campchecklist;

import com.google.gson.Gson;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

public final class Network {
    private static final Gson GSON=new Gson();
    /** Installed only on the physical client. No rendering references are loaded by the server. */
    public static Consumer<Message> clientReceiver=m -> {};
    public record Message(String kind,String text,boolean completed) implements CustomPacketPayload {
        public static final Type<Message> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CampChecklist.ID,"message"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Message> CODEC=new StreamCodec<>() {
            public Message decode(RegistryFriendlyByteBuf b) { return new Message(b.readUtf(16),b.readUtf(262144),b.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf b,Message m) { b.writeUtf(m.kind,16); b.writeUtf(m.text,262144); b.writeBoolean(m.completed); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent e) {
        var registrar=e.registrar("1");
        registrar.playToClient(Message.TYPE,Message.CODEC,(m,ctx) -> ctx.enqueueWork(() -> clientReceiver.accept(m)));
        registrar.playToServer(Manual.TYPE,Manual.CODEC,(m,ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) CampChecklist.runtime(p.server).manual(m.id,m.completed);
            });
        });
        registrar.playToServer(Open.TYPE,Open.CODEC,(m,ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) ChecklistUi.open(p);
            });
        });
    }
    public record Manual(String id,boolean completed) implements CustomPacketPayload {
        public static final Type<Manual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CampChecklist.ID,"manual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Manual> CODEC=new StreamCodec<>() {
            public Manual decode(RegistryFriendlyByteBuf b) { return new Manual(b.readUtf(256),b.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf b,Manual m) { b.writeUtf(m.id,256); b.writeBoolean(m.completed); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CampChecklist.ID,"open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.unit(new Open());
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static ViewModel decode(String json) { return GSON.fromJson(json,ViewModel.class); }
    public static void send(ServerPlayer p,ViewModel model) { PacketDistributor.sendToPlayer(p,new Message("snapshot",GSON.toJson(model),false)); }
    public static void broadcast(MinecraftServer s,ViewModel model) { for (ServerPlayer p:s.getPlayerList().getPlayers()) send(p,model); }
    public static void toast(MinecraftServer s,String title) { for (ServerPlayer p:s.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p,new Message("toast",title,false)); }
    public static void requestManual(String id,boolean completed) { PacketDistributor.sendToServer(new Manual(id,completed)); }
    public static void requestOpen() { PacketDistributor.sendToServer(new Open()); }
}
