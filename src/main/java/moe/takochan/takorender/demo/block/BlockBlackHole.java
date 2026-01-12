package moe.takochan.takorender.demo.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moe.takochan.takorender.demo.TakoRenderDemoMod;
import moe.takochan.takorender.demo.tile.TileEntityBlackHole;

public class BlockBlackHole extends BlockContainer {

    public BlockBlackHole() {
        super(Material.iron);
        this.setBlockName(TakoRenderDemoMod.MODID + ".black_hole");
        this.setBlockTextureName("minecraft:bedrock");
        this.setCreativeTab(CreativeTabs.tabDecorations);
        this.setHardness(3.0f);
        this.setResistance(10.0f);
        this.setLightLevel(1.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityBlackHole();
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(net.minecraft.world.IBlockAccess world, int x, int y, int z, int side) {
        return false;
    }
}
