package com.mrgregles.bsp_core.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mrgregles.bsp_core.BSPCore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Adds an item to a loot table by chance. Used to put rare Shatter Coins in structure chests; which
 * chests, which coin and how often are data (data/bsp_core/loot_modifiers), so a pack can change them.
 */
public class AddItemLootModifier extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BSPCore.MODID);

    public static final Supplier<Codec<AddItemLootModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(inst -> codecStart(inst)
            .and(ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item))
            .and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance))
            .and(Codec.INT.optionalFieldOf("max", 1).forGetter(m -> m.max))
            .apply(inst, AddItemLootModifier::new)));

    public static final RegistryObject<Codec<AddItemLootModifier>> ADD_ITEM = SERIALIZERS.register("add_item", CODEC);

    private final Item item;
    private final float chance;
    private final int max;

    public AddItemLootModifier(LootItemCondition[] conditions, Item item, float chance, int max) {
        super(conditions);
        this.item = item;
        this.chance = chance;
        this.max = Math.max(1, max);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getRandom().nextFloat() < chance) {
            loot.add(new ItemStack(item, 1 + context.getRandom().nextInt(max)));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
