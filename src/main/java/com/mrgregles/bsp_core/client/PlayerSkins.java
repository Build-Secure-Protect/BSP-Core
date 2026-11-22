package com.mrgregles.bsp_core.client;

import com.mojang.authlib.GameProfile;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Totem owners' skins for the "my head" look. An owner on this server's player list gives theirs straight away; anyone else is
 * looked up the way a player head block looks up its owner (the profile, then the skin download), once per session. Until the
 * skin is known the totem keeps its own face. Render thread only.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class PlayerSkins {
    private PlayerSkins() {}

    private static final Map<UUID, ResourceLocation> KNOWN = new HashMap<>();
    private static final Set<UUID> ASKED = new HashSet<>();

    /** The owner's skin, or null while it is not known yet. */
    @Nullable
    public static ResourceLocation get(TotemOwner owner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(owner.uuid());
            if (info != null && info.isSkinLoaded()) {
                return info.getSkinLocation();
            }
        }
        ResourceLocation known = KNOWN.get(owner.uuid());
        if (known != null || !ASKED.add(owner.uuid())) {
            return known;
        }
        SkullBlockEntity.updateGameprofile(new GameProfile(owner.uuid(), owner.name()),
                profile -> KNOWN.put(owner.uuid(), Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(profile)));
        return null;
    }

    /** Leaving a server: look skins up again next time (they may have changed). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /** Forgets every looked-up skin (a new session may bring new skins). */
    public static void clear() {
        KNOWN.clear();
        ASKED.clear();
    }
}
