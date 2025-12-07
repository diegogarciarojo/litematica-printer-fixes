package me.aleksilassila.litematica.printer.v1_21_4.guides.placement;

import me.aleksilassila.litematica.printer.v1_21_4.SchematicBlockState;
import me.aleksilassila.litematica.printer.v1_21_4.implementation.PrinterPlacementContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TorchGuide extends PropertySpecificGuesserGuide {
    public TorchGuide(SchematicBlockState state) {
        super(state);
    }

    @Override
    protected List<Direction> getPossibleSides() {
        // Wall torches (including redstone wall torches) use HORIZONTAL_FACING
        // facing=east means the torch is attached to a block to the WEST (facing away from the wall)
        Optional<Direction> facing = getProperty(targetState, Properties.HORIZONTAL_FACING);

        return facing
                .map(direction -> Collections.singletonList(direction.getOpposite()))
                .orElseGet(() -> Collections.singletonList(Direction.DOWN));
    }

    @Override
    public @Nullable PrinterPlacementContext getPlacementContext(ClientPlayerEntity player) {
        // For wall torches, we need to be very specific about the placement
        Optional<Direction> facingOpt = getProperty(targetState, Properties.HORIZONTAL_FACING);
        
        if (facingOpt.isEmpty()) {
            // Floor torch - use default behavior
            return super.getPlacementContext(player);
        }
        
        Direction facing = facingOpt.get();
        // The torch is attached to the block in the opposite direction of facing
        Direction attachedSide = facing.getOpposite();
        BlockPos neighborPos = state.blockPos.offset(attachedSide);
        
        BlockState neighborState = state.world.getBlockState(neighborPos);
        
        // Check if there's a valid block to attach to
        if (!canBeClicked(state.world, neighborPos) || neighborState.isReplaceable()) {
            return null;
        }
        
        ItemStack requiredItem = getRequiredItem(player).stream().findFirst().orElse(ItemStack.EMPTY);
        int slot = getRequiredItemStackSlot(player);
        if (slot == -1) return null;
        
        boolean requiresShift = isInteractive(neighborState.getBlock());
        
        // Hit the center of the face we want to place on
        Vec3d hitVec = Vec3d.ofCenter(neighborPos).add(Vec3d.of(facing.getVector()).multiply(0.5));
        
        // The side we click on the neighbor block is the direction pointing towards where the torch will be
        BlockHitResult hitResult = new BlockHitResult(hitVec, facing, neighborPos, false);
        
        PrinterPlacementContext context = new PrinterPlacementContext(player, hitResult, requiredItem, slot, facing, requiresShift);
        context.canStealth = true;
        
        // Verify the placement would result in the correct torch
        BlockState result = getRequiredItemAsBlock(player)
                .orElse(targetState.getBlock())
                .getPlacementState(context);
        
        if (result != null && statesEqual(result, targetState)) {
            return context;
        }
        
        return null;
    }

    @Override
    protected boolean statesEqual(BlockState resultState, BlockState targetState) {
        // For torches, we must ensure the block type matches exactly
        // (wall torch vs floor torch are different blocks)
        if (resultState.getBlock() != targetState.getBlock()) {
            return false;
        }
        return super.statesEqual(resultState, targetState);
    }

    @Override
    protected Optional<Block> getRequiredItemAsBlock(ClientPlayerEntity player) {
        return Optional.of(state.targetState.getBlock());
    }
}
