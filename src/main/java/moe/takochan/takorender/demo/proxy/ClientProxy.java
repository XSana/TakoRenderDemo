package moe.takochan.takorender.demo.proxy;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventBus;
import moe.takochan.takorender.demo.client.render.TileEntityBlackHoleRenderer;
import moe.takochan.takorender.demo.tile.TileEntityBlackHole;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        registerRenderers();
    }

    private void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBlackHole.class, new TileEntityBlackHoleRenderer());
    }

    public EventBus getEventBus() {
        return MinecraftForge.EVENT_BUS;
    }
}
