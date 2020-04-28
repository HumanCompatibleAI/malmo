package com.microsoft.Malmo.MissionHandlers;

import com.microsoft.Malmo.MissionHandlerInterfaces.IRewardProducer;
import com.microsoft.Malmo.Schemas.*;
import com.microsoft.Malmo.Utils.MinecraftTypeHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Gives a reward when the Minecraft player is within a certain distance of blocks.
 *
 *  Each block matcher can give its own reward for a given set of block types.
 *      It can choose to aggregate or take the highest reward.
 *      It can choose to only reward once every (x) ticks.
 *
 *  Reward can be aggregated over matcher entries or the highest can be taken.
 *
 *  The reward is higher the closer the player is to the matched block. The reward
 *      is a fraction of the reward chosen for the block matcher.
 *
 *  The default radius is {@value RADIUS}
 */
public class RewardForNearbyBlockTypeImplementation extends RewardBase implements IRewardProducer {
    public static final int RADIUS = 10;

    private class BlockMatcher {
        BlockSpecWithReward spec;
        ArrayList<String> allowedBlockNames;
        int fired_timer;

        public BlockMatcher(BlockSpecWithReward spec) {
            this.spec = spec;
            this.fired_timer = 0;
            // Get the allowed blocks:
            // (Convert from the enum name to the unlocalised name.)
            this.allowedBlockNames = new ArrayList<String>();
            List<BlockType> allowedTypes = spec.getType();
            if (allowedTypes != null) {
                for (BlockType bt : allowedTypes) {
                    Block b = Block.getBlockFromName(bt.value());
                    this.allowedBlockNames.add(b.getUnlocalizedName());
                }
            }
        }

        boolean applies(BlockPos bp) {
            return fired_timer >= this.spec.getCooldownInTicks();
        }

        boolean matches(BlockPos bp, IBlockState bs) {
            boolean match = false;

            // See whether the blockstate matches our specification:
            for (String allowedbs : this.allowedBlockNames) {
                if (allowedbs.equals(bs.getBlock().getUnlocalizedName()))
                    match = true;
            }

            // This type of block is a match, but does the colour match?
            if (match && this.spec.getColour() != null && !this.spec.getColour().isEmpty())
                match = MinecraftTypeHelper.blockColourMatches(bs, this.spec.getColour());

            // Matches type and colour, but does the variant match?
            if (match && this.spec.getVariant() != null && !this.spec.getVariant().isEmpty())
                match = MinecraftTypeHelper.blockVariantMatches(bs, this.spec.getVariant());

            if (match)
            {
                // We're firing.
                this.fired_timer = 0;
            }

            return match;
        }

        float reward() {
            return this.spec.getReward().floatValue();
        }

        boolean singleBlock() {
            return this.spec.isSingleBlock();
        }

        void tick() {
            this.fired_timer += 1;
        }
    }


    private RewardForNearbyBlockType params;
    ArrayList<BlockMatcher> matchers = new ArrayList<BlockMatcher>();
    boolean max;

    @Override
    public boolean parseParameters(Object params) {
        super.parseParameters(params);
        if (params == null || !(params instanceof RewardForNearbyBlockType))
            return false;

        this.params = (RewardForNearbyBlockType) params;
        for (BlockSpecWithReward spec : this.params.getBlock())
            this.matchers.add(new BlockMatcher(spec));

        max = this.params.isMax();
        return true;
    }

    /* I'm copying this over from RewardForTouchingBlockImplementation, not because I think it will be used,
    *   but for compatibility's sake.
    * */
    @SubscribeEvent
    public void onDiscretePartialMoveEvent(DiscreteMovementCommandsImplementation.DiscretePartialMoveEvent event)
    {
        MultidimensionalReward reward = new MultidimensionalReward();
        calculateReward(reward);
        addCachedReward(reward);
    }


    private void calculateReward(MultidimensionalReward reward) {
        float[] matcherRewards = new float[this.matchers.size()];
        boolean[] matcherFires = new boolean[this.matchers.size()];
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        Vec3d playerPos = player.getPositionVector();
        BlockPos playerBlock = player.getPosition();

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    final BlockPos pos = playerBlock.add(x, y, z);
                    IBlockState blockState = player.world.getBlockState(pos);

                    for (int i = 0; i < this.matchers.size(); i++) {
                        BlockMatcher bm = this.matchers.get(i);

                        if (bm.applies(pos) && bm.matches(pos, blockState)) {
                            float reward_value = bm.reward();

                            float adjusted_reward = adjustAndDistributeReward(reward_value, this.params.getDimension(), bm.spec.getDistribution());
                            float dist = (float) playerPos.subtract(new Vec3d(pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5)).lengthVector();
                            float invDist = (RADIUS - dist) / RADIUS;
                            if (invDist < 0) {
                                // More than RADIUS blocks away
                                continue;
                            }

                            float scaled_reward = adjusted_reward * invDist;
                            if (bm.singleBlock()) {
                                if (matcherRewards[i] < scaled_reward) {
                                    matcherRewards[i] = scaled_reward;
                                }
                            } else {
                                matcherRewards[i] += scaled_reward;
                            }
                            matcherFires[i] = true;
                        }
                    }
                }
            }
        }
        float final_reward = 0;
        for (int i = 0; i < matcherRewards.length; i++) {
            if (this.max && matcherRewards[i] > final_reward) {
                final_reward = matcherRewards[i];
            } else {
                final_reward += matcherRewards[i];
            }

            if (!matcherFires[i]) {
                this.matchers.get(i).tick();
            }
        }
        reward.add(this.params.getDimension(), final_reward);
    }


    @Override
    public void getReward(MissionInit missionInit, MultidimensionalReward reward)
    {
        super.getReward(missionInit, reward);
        calculateReward(reward);
    }

    @Override
    public void prepare(MissionInit missionInit)
    {
        super.prepare(missionInit);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void cleanup()
    {
        super.cleanup();
        MinecraftForge.EVENT_BUS.unregister(this);
    }


}
