package moe.takochan.takorender.demo.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityBlackHole extends TileEntity {

    private long creationTime;

    public TileEntityBlackHole() {
        this.creationTime = System.currentTimeMillis();
    }

    @Override
    public void updateEntity() {
        // TileEntity更新逻辑（如果需要）
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    public float getTime() {
        return (System.currentTimeMillis() - creationTime) / 1000.0f;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        // 无限渲染距离
        return Double.MAX_VALUE;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        // 使用无限大的边界框，确保始终渲染
        return INFINITE_EXTENT_AABB;
    }
}
