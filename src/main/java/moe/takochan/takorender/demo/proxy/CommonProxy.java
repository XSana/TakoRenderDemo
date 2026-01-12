package moe.takochan.takorender.demo.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import moe.takochan.takorender.demo.init.ModBlocks;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModBlocks.init();
        ModBlocks.registerTileEntities();
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
