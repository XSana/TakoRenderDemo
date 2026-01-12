package moe.takochan.takorender.demo.init;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;
import moe.takochan.takorender.demo.TakoRenderDemoMod;
import moe.takochan.takorender.demo.block.BlockBlackHole;
import moe.takochan.takorender.demo.tile.TileEntityBlackHole;

public class ModBlocks {

    public static Block blockBlackHole;

    public static void init() {
        blockBlackHole = new BlockBlackHole();
        GameRegistry.registerBlock(blockBlackHole, "black_hole");
        TakoRenderDemoMod.LOG.info("Registered block: black_hole");
    }

    public static void registerTileEntities() {
        GameRegistry.registerTileEntity(TileEntityBlackHole.class, TakoRenderDemoMod.MODID + ":black_hole");
        TakoRenderDemoMod.LOG.info("Registered tile entity: black_hole");
    }
}
