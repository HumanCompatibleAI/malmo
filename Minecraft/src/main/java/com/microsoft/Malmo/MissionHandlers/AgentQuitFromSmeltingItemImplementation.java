package com.microsoft.Malmo.MissionHandlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import com.microsoft.Malmo.MissionHandlerInterfaces.IWantToQuit;
import com.microsoft.Malmo.MissionHandlers.RewardForItemBase.ItemMatcher;
import com.microsoft.Malmo.Schemas.AgentQuitFromSmeltingItem;
import com.microsoft.Malmo.Schemas.BlockOrItemSpecWithDescription;
import com.microsoft.Malmo.Schemas.MissionInit;

/**
 * @author Cayden Codel, Carnegie Mellon University
 * <p>
 * Gives agents rewards when items are smelted. Handles variants and colors.
 */
public class AgentQuitFromSmeltingItemImplementation extends HandlerBase implements IWantToQuit {

    private AgentQuitFromSmeltingItem params;
    private HashMap<String, Integer> smeltedItems;
    private List<ItemQuitMatcher> matchers;
    private String quitCode = "";
    private boolean wantToQuit = false;
    private int callSmelt = 0;

    public static class ItemQuitMatcher extends RewardForItemBase.ItemMatcher {
        String description;

        ItemQuitMatcher(BlockOrItemSpecWithDescription spec) {
            super(spec);
            this.description = spec.getDescription();
        }

        String description() {
            return this.description;
        }
    }

    @Override
    public boolean parseParameters(Object params) {
        if (!(params instanceof AgentQuitFromSmeltingItem))
            return false;

        this.params = (AgentQuitFromSmeltingItem) params;
        this.matchers = new ArrayList<ItemQuitMatcher>();
        for (BlockOrItemSpecWithDescription bs : this.params.getItem())
            this.matchers.add(new ItemQuitMatcher(bs));
        return true;
    }

    @Override
    public boolean doIWantToQuit(MissionInit missionInit) {
        return this.wantToQuit;
    }

    @Override
    public String getOutcome() {
        return this.quitCode;
    }

    @Override
    public void prepare(MissionInit missionInit) {
        MinecraftForge.EVENT_BUS.register(this);
        smeltedItems = new HashMap<String, Integer>();
    }

    @Override
    public void cleanup() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onItemSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (callSmelt % 4 == 0)
            checkForMatch(event.smelting);

        callSmelt = (callSmelt + 1) % 4;
    }

    /**
     * Checks whether the ItemStack matches a variant stored in the item list. If
     * so, returns true, else returns false.
     *
     * @param is The item stack
     * @return If the stack is allowed in the item matchers and has color or
     * variants enabled, returns true, else false.
     */
    private boolean getVariant(ItemStack is) {
        for (ItemMatcher matcher : matchers) {
            if (matcher.allowedItemTypes.contains(is.getItem().getUnlocalizedName())) {
                if (matcher.matchSpec.getColour() != null && matcher.matchSpec.getColour().size() > 0)
                    return true;
                if (matcher.matchSpec.getVariant() != null && matcher.matchSpec.getVariant().size() > 0)
                    return true;
            }
        }

        return false;
    }

    private int getSmeltedItemCount(ItemStack is) {
        boolean variant = getVariant(is);

        if (variant)
            return (smeltedItems.get(is.getUnlocalizedName()) == null) ? 0 : smeltedItems.get(is.getUnlocalizedName());
        else
            return (smeltedItems.get(is.getItem().getUnlocalizedName()) == null) ? 0
                    : smeltedItems.get(is.getItem().getUnlocalizedName());

    }

    private void addSmeltedItemCount(ItemStack is) {
        boolean variant = getVariant(is);

        if (variant) {
            int prev = (smeltedItems.get(is.getUnlocalizedName()) == null ? 0
                    : smeltedItems.get(is.getUnlocalizedName()));
            smeltedItems.put(is.getUnlocalizedName(), prev + is.getCount());
        } else {
            int prev = (smeltedItems.get(is.getItem().getUnlocalizedName()) == null ? 0
                    : smeltedItems.get(is.getItem().getUnlocalizedName()));
            smeltedItems.put(is.getItem().getUnlocalizedName(), prev + is.getCount());
        }
    }

    private void checkForMatch(ItemStack is) {
        int savedSmelted = getSmeltedItemCount(is);
        for (ItemQuitMatcher matcher : this.matchers) {
            if (matcher.matches(is)) {
                if (savedSmelted != 0) {
                    if (is.getCount() + savedSmelted >= matcher.matchSpec.getAmount()) {
                        this.quitCode = matcher.description();
                        this.wantToQuit = true;
                    }
                } else if (is.getCount() >= matcher.matchSpec.getAmount()) {
                    this.quitCode = matcher.description();
                    this.wantToQuit = true;
                }
            }
        }

        addSmeltedItemCount(is);
    }
}