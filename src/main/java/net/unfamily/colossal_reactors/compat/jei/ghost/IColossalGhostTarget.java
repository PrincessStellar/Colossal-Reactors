package net.unfamily.colossal_reactors.compat.jei.ghost;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Implement on a Screen to accept JEI ghost ingredient drops.
 * Highlighting is left entirely to JEI ({@code shouldHighlightTargets() == true}).
 */
public interface IColossalGhostTarget {

    @Nullable
    default IGhostIngredientConsumer getGhostHandler() {
        return null;
    }

    @Nullable
    default Rect2i getGhostTargetArea() {
        return null;
    }

    /**
     * Prefer multi-target drops for visible slots only.
     * Hidden elements must not be listed — JEI must not highlight or render them.
     */
    default List<GhostDropTarget> getGhostDropTargets() {
        return Collections.emptyList();
    }

    record GhostDropTarget(Rect2i area, Consumer<ItemStack> accept) {}

    interface IGhostIngredientConsumer {
        @Nullable
        Object supportedTarget(Object ingredient);

        void accept(Object ingredient);
    }

    interface IGhostItemConsumer extends IGhostIngredientConsumer {
        @Nullable
        @Override
        default ItemStack supportedTarget(Object ingredient) {
            return ingredient instanceof ItemStack stack && !stack.isEmpty() ? stack : null;
        }
    }
}
