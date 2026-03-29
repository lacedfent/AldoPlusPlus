package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.InventoryItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ItemSearchIndex;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public class Inventory extends Module {
    private final SliderSetting targetCPS;
    private final InventoryItemListSetting items;

    private final Deque<PlannedClick> pendingClicks = new ArrayDeque<PlannedClick>();
    private int cursorRecoveryInventoryIndex = -1;
    private double windowClickBudget;

    private static final Comparator<ActionCandidate> ACTION_COMPARATOR =
        Comparator.comparingInt((ActionCandidate candidate) -> candidate.cost)
            .thenComparingInt(candidate -> candidate.targetHotbarSlot)
            .thenComparingInt(candidate -> candidate.priorityIndex)
            .thenComparing((ActionCandidate candidate) -> -candidate.resultingSize)
            .thenComparingInt(candidate -> candidate.sourcePreference)
            .thenComparingInt(candidate -> candidate.sourceInventoryIndex);

    public Inventory() {
        super("Inventory", category.player);
        this.registerSetting(targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 40.0, 0.5));
        this.registerSetting(items = new InventoryItemListSetting("Items"));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        windowClickBudget = 0.0;
    }

    @Override
    public void onDisable() {
        if (Utils.nullCheck() && Utils.inInventory()) {
            tryRecoverCursor(InventorySnapshot.capture());
        }
        pendingClicks.clear();
        cursorRecoveryInventoryIndex = -1;
        windowClickBudget = 0.0;
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            clearPendingState(false);
            return;
        }

        if (!(mc.currentScreen instanceof GuiInventory) || !(mc.thePlayer.openContainer instanceof ContainerPlayer) || !Utils.inInventory()) {
            clearPendingState(true);
            return;
        }

        int clickBudget = consumeWindowClickBudget();
        if (clickBudget <= 0) {
            return;
        }

        InventorySnapshot snapshot = InventorySnapshot.capture();

        while (clickBudget > 0) {
            if (!pendingClicks.isEmpty()) {
                if (executePendingClick(snapshot)) {
                    clickBudget--;
                    snapshot = InventorySnapshot.capture();
                    continue;
                }
                snapshot = InventorySnapshot.capture();
            }

            if (snapshot.carried != null) {
                if (tryRecoverCursor(snapshot)) {
                    clickBudget--;
                    snapshot = InventorySnapshot.capture();
                    continue;
                }
                return;
            }

            SlotAssignment[] assignments = resolveAssignments(snapshot);
            ActionCandidate candidate = findBestCandidate(snapshot, assignments);
            if (candidate == null) {
                return;
            }

            pendingClicks.clear();
            pendingClicks.addAll(candidate.steps);
            if (!executePendingClick(snapshot)) {
                return;
            }

            clickBudget--;
            snapshot = InventorySnapshot.capture();
        }
    }

    private void clearPendingState(boolean preserveRecovery) {
        pendingClicks.clear();
        windowClickBudget = 0.0;
        if (!preserveRecovery) {
            cursorRecoveryInventoryIndex = -1;
        }
    }

    private int consumeWindowClickBudget() {
        windowClickBudget += Math.max(0.0, targetCPS.getInput()) / 20.0;
        int clicks = (int) windowClickBudget;
        if (clicks <= 0) {
            return 0;
        }

        windowClickBudget -= clicks;
        windowClickBudget = Math.min(windowClickBudget, 2.0);
        return clicks;
    }

    private boolean executePendingClick(InventorySnapshot snapshot) {
        PlannedClick step = pendingClicks.peekFirst();
        if (step == null) {
            return false;
        }

        if (!step.validator.isValid(snapshot, this)) {
            pendingClicks.clear();
            if (snapshot.carried != null) {
                return tryRecoverCursor(snapshot);
            }
            if (snapshot.carried == null) {
                cursorRecoveryInventoryIndex = -1;
            }
            return false;
        }

        step.beforeExecute.run(this);
        click(step.slotId, step.button, step.mode);
        pendingClicks.pollFirst();
        step.afterExecute.run(this);
        return true;
    }

    private boolean tryRecoverCursor(InventorySnapshot snapshot) {
        if (cursorRecoveryInventoryIndex < 0 || snapshot.carried == null) {
            if (snapshot.carried == null) {
                cursorRecoveryInventoryIndex = -1;
            }
            return false;
        }

        ItemStack recoverySlot = snapshot.getSlot(cursorRecoveryInventoryIndex);
        if (recoverySlot != null && !canStacksMerge(recoverySlot, snapshot.carried)) {
            return false;
        }

        click(toContainerSlot(cursorRecoveryInventoryIndex), 0, 0);
        pendingClicks.clear();
        cursorRecoveryInventoryIndex = -1;
        return true;
    }

    private void click(int slotId, int button, int mode) {
        mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slotId, button, mode, mc.thePlayer);
    }

    private SlotAssignment[] resolveAssignments(InventorySnapshot snapshot) {
        SlotAssignment[] assignments = new SlotAssignment[InventoryPlayer.getHotbarSize()];
        List<String> orderedItems = items.getItems();

        for (int priorityIndex = 0; priorityIndex < orderedItems.size(); priorityIndex++) {
            String storageId = orderedItems.get(priorityIndex);
            Integer assignedSlot = items.getAssignedSlot(storageId);
            if (assignedSlot == null) {
                continue;
            }

            int hotbarSlot = assignedSlot - 1;
            if (hotbarSlot < 0 || hotbarSlot >= InventoryPlayer.getHotbarSize() || assignments[hotbarSlot] != null) {
                continue;
            }

            if (hasMatchingStack(snapshot, storageId)) {
                assignments[hotbarSlot] = new SlotAssignment(hotbarSlot, storageId, priorityIndex);
            }
        }

        return assignments;
    }

    private boolean hasMatchingStack(InventorySnapshot snapshot, String storageId) {
        for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
            if (ItemSearchIndex.matches(storageId, snapshot.getSlot(inventoryIndex))) {
                return true;
            }
        }
        return false;
    }

    private ActionCandidate findBestCandidate(InventorySnapshot snapshot, SlotAssignment[] assignments) {
        List<ActionCandidate> candidates = new ArrayList<ActionCandidate>();

        for (SlotAssignment assignment : assignments) {
            if (assignment == null) {
                continue;
            }

            ItemStack targetStack = snapshot.getSlot(assignment.hotbarSlot);
            boolean targetMatchesRule = ItemSearchIndex.matches(assignment.storageId, targetStack);

            if (targetMatchesRule && targetStack != null && isPartialStack(targetStack)) {
                for (int sourceInventoryIndex = 0; sourceInventoryIndex < InventorySnapshot.INVENTORY_SIZE; sourceInventoryIndex++) {
                    if (sourceInventoryIndex == assignment.hotbarSlot) {
                        continue;
                    }

                    ItemStack sourceStack = snapshot.getSlot(sourceInventoryIndex);
                    if (!canStacksMerge(sourceStack, targetStack)) {
                        continue;
                    }

                    if (sourceInventoryIndex < InventoryPlayer.getHotbarSize() && isSatisfiedHotbarSlot(snapshot, assignments, sourceInventoryIndex)) {
                        continue;
                    }

                    ActionCandidate quickMove = buildQuickMoveMergeCandidate(snapshot, assignment, sourceInventoryIndex, sourceStack, targetStack);
                    if (quickMove != null) {
                        candidates.add(quickMove);
                    }

                    ActionCandidate stagedMerge = buildStagedHotbarMergeCandidate(snapshot, assignment, sourceInventoryIndex, sourceStack, targetStack);
                    if (stagedMerge != null) {
                        candidates.add(stagedMerge);
                    }

                    ActionCandidate cursorMerge = buildCursorMergeCandidate(snapshot, assignment, sourceInventoryIndex, sourceStack, targetStack);
                    if (cursorMerge != null) {
                        candidates.add(cursorMerge);
                    }
                }
            }

            if (!targetMatchesRule) {
                for (int sourceInventoryIndex = 0; sourceInventoryIndex < InventorySnapshot.INVENTORY_SIZE; sourceInventoryIndex++) {
                    if (sourceInventoryIndex == assignment.hotbarSlot) {
                        continue;
                    }

                    ItemStack sourceStack = snapshot.getSlot(sourceInventoryIndex);
                    if (!ItemSearchIndex.matches(assignment.storageId, sourceStack)) {
                        continue;
                    }

                    if (sourceInventoryIndex < InventoryPlayer.getHotbarSize() && isSatisfiedHotbarSlot(snapshot, assignments, sourceInventoryIndex)) {
                        continue;
                    }

                    candidates.add(buildPlacementCandidate(snapshot, assignment, sourceInventoryIndex, sourceStack));
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(ACTION_COMPARATOR);
        return candidates.get(0);
    }

    private ActionCandidate buildPlacementCandidate(InventorySnapshot snapshot, SlotAssignment assignment, int sourceInventoryIndex, ItemStack sourceStack) {
        int sourcePreference = sourceInventoryIndex < InventoryPlayer.getHotbarSize() ? 1 : 0;
        if (sourceInventoryIndex < InventoryPlayer.getHotbarSize() && isSatisfiedHotbarSlot(snapshot, resolveAssignments(snapshot), sourceInventoryIndex)) {
            sourcePreference = 2;
        }

        List<PlannedClick> steps = new ArrayList<PlannedClick>(1);
        steps.add(new PlannedClick(
            toContainerSlot(sourceInventoryIndex),
            assignment.hotbarSlot,
            2,
            (current, module) -> current.carried == null && ItemSearchIndex.matches(assignment.storageId, current.getSlot(sourceInventoryIndex)),
            NO_OP,
            NO_OP
        ));

        return new ActionCandidate(1, assignment.hotbarSlot, assignment.priorityIndex, sourceStack != null ? sourceStack.stackSize : 0, sourcePreference, sourceInventoryIndex, steps);
    }

    private ActionCandidate buildQuickMoveMergeCandidate(InventorySnapshot snapshot, SlotAssignment assignment, int sourceInventoryIndex, ItemStack sourceStack, ItemStack targetStack) {
        if (sourceInventoryIndex < InventoryPlayer.getHotbarSize()) {
            return null;
        }

        if (!isQuickMoveMergeSafe(snapshot, assignment.hotbarSlot, sourceStack, targetStack, -1)) {
            return null;
        }

        List<PlannedClick> steps = new ArrayList<PlannedClick>(1);
        steps.add(new PlannedClick(
            toContainerSlot(sourceInventoryIndex),
            0,
            1,
            (current, module) -> current.carried == null
                && canStacksMerge(current.getSlot(sourceInventoryIndex), current.getSlot(assignment.hotbarSlot))
                && isPartialStack(current.getSlot(assignment.hotbarSlot))
                && isQuickMoveMergeSafe(current, assignment.hotbarSlot, current.getSlot(sourceInventoryIndex), current.getSlot(assignment.hotbarSlot), -1),
            NO_OP,
            NO_OP
        ));

        return new ActionCandidate(0, assignment.hotbarSlot, assignment.priorityIndex, getMergedStackSize(sourceStack, targetStack), 0, sourceInventoryIndex, steps);
    }

    private ActionCandidate buildStagedHotbarMergeCandidate(InventorySnapshot snapshot, SlotAssignment assignment, int sourceInventoryIndex, ItemStack sourceStack, ItemStack targetStack) {
        if (sourceInventoryIndex >= InventoryPlayer.getHotbarSize()) {
            return null;
        }

        int emptyMainIndex = snapshot.firstEmptyMainInventoryIndex;
        if (emptyMainIndex < 0) {
            return null;
        }

        int room = getRemainingRoom(targetStack);
        if (room <= 0 || sourceStack == null || sourceStack.stackSize > room) {
            return null;
        }

        if (!isQuickMoveMergeSafe(snapshot, assignment.hotbarSlot, sourceStack, targetStack, sourceInventoryIndex)) {
            return null;
        }

        List<PlannedClick> steps = new ArrayList<PlannedClick>(2);
        steps.add(new PlannedClick(
            toContainerSlot(emptyMainIndex),
            sourceInventoryIndex,
            2,
            (current, module) -> current.carried == null
                && current.getSlot(emptyMainIndex) == null
                && canStacksMerge(current.getSlot(sourceInventoryIndex), targetStack),
            NO_OP,
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(emptyMainIndex),
            0,
            1,
            (current, module) -> current.carried == null
                && canStacksMerge(current.getSlot(emptyMainIndex), current.getSlot(assignment.hotbarSlot))
                && isPartialStack(current.getSlot(assignment.hotbarSlot))
                && isQuickMoveMergeSafe(current, assignment.hotbarSlot, current.getSlot(emptyMainIndex), current.getSlot(assignment.hotbarSlot), -1),
            NO_OP,
            NO_OP
        ));

        return new ActionCandidate(2, assignment.hotbarSlot, assignment.priorityIndex, getMergedStackSize(sourceStack, targetStack), 1, sourceInventoryIndex, steps);
    }

    private ActionCandidate buildCursorMergeCandidate(InventorySnapshot snapshot, SlotAssignment assignment, int sourceInventoryIndex, ItemStack sourceStack, ItemStack targetStack) {
        if (sourceInventoryIndex >= InventoryPlayer.getHotbarSize()) {
            return null;
        }

        if (sourceStack == null || targetStack == null || !canStacksMerge(sourceStack, targetStack) || !isPartialStack(targetStack)) {
            return null;
        }

        int leftover = Math.max(0, sourceStack.stackSize - getRemainingRoom(targetStack));
        List<PlannedClick> steps = new ArrayList<PlannedClick>(leftover > 0 ? 3 : 2);
        steps.add(new PlannedClick(
            toContainerSlot(sourceInventoryIndex),
            0,
            0,
            (current, module) -> current.carried == null
                && canStacksMerge(current.getSlot(sourceInventoryIndex), current.getSlot(assignment.hotbarSlot))
                && isPartialStack(current.getSlot(assignment.hotbarSlot)),
            module -> module.cursorRecoveryInventoryIndex = sourceInventoryIndex,
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(assignment.hotbarSlot),
            0,
            0,
            (current, module) -> current.carried != null
                && canStacksMerge(current.carried, current.getSlot(assignment.hotbarSlot))
                && isPartialStack(current.getSlot(assignment.hotbarSlot)),
            NO_OP,
            leftover == 0 ? module -> module.cursorRecoveryInventoryIndex = -1 : NO_OP
        ));

        if (leftover > 0) {
            steps.add(new PlannedClick(
                toContainerSlot(sourceInventoryIndex),
                0,
                0,
                (current, module) -> current.carried != null
                    && (current.getSlot(sourceInventoryIndex) == null || canStacksMerge(current.carried, current.getSlot(sourceInventoryIndex))),
                NO_OP,
                module -> module.cursorRecoveryInventoryIndex = -1
            ));
        }

        return new ActionCandidate(3, assignment.hotbarSlot, assignment.priorityIndex, getMergedStackSize(sourceStack, targetStack), sourceInventoryIndex < InventoryPlayer.getHotbarSize() ? 1 : 0, sourceInventoryIndex, steps);
    }

    private boolean isSatisfiedHotbarSlot(InventorySnapshot snapshot, SlotAssignment[] assignments, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= InventoryPlayer.getHotbarSize()) {
            return false;
        }
        SlotAssignment assignment = assignments[hotbarSlot];
        return assignment != null && ItemSearchIndex.matches(assignment.storageId, snapshot.getSlot(hotbarSlot));
    }

    private static boolean isQuickMoveMergeSafe(InventorySnapshot snapshot, int targetHotbarSlot, ItemStack sourceStack, ItemStack targetStack, int ignoredHotbarSlot) {
        if (!canStacksMerge(sourceStack, targetStack) || !isPartialStack(targetStack)) {
            return false;
        }

        for (int hotbarSlot = 0; hotbarSlot < InventoryPlayer.getHotbarSize(); hotbarSlot++) {
            if (hotbarSlot == targetHotbarSlot || hotbarSlot == ignoredHotbarSlot) {
                continue;
            }

            ItemStack hotbarStack = snapshot.getSlot(hotbarSlot);
            if (hotbarStack != null && canStacksMerge(hotbarStack, sourceStack) && isPartialStack(hotbarStack) && hotbarSlot < targetHotbarSlot) {
                return false;
            }
        }

        return true;
    }

    private static boolean canStacksMerge(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getItem() != second.getItem()) {
            return false;
        }
        if (first.getHasSubtypes() && first.getMetadata() != second.getMetadata()) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(first, second);
    }

    private static boolean isPartialStack(ItemStack stack) {
        return stack != null && stack.isStackable() && stack.stackSize < stack.getMaxStackSize();
    }

    private static int getRemainingRoom(ItemStack stack) {
        return stack == null ? 0 : Math.max(0, stack.getMaxStackSize() - stack.stackSize);
    }

    private static int getMergedStackSize(ItemStack sourceStack, ItemStack targetStack) {
        if (sourceStack == null) {
            return targetStack != null ? targetStack.stackSize : 0;
        }
        if (targetStack == null) {
            return sourceStack.stackSize;
        }
        return Math.min(targetStack.getMaxStackSize(), targetStack.stackSize + sourceStack.stackSize);
    }

    private static int toContainerSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= InventorySnapshot.INVENTORY_SIZE) {
            return -1;
        }
        return inventoryIndex < InventoryPlayer.getHotbarSize() ? inventoryIndex + 36 : inventoryIndex;
    }

    private interface StepValidator {
        boolean isValid(InventorySnapshot snapshot, Inventory module);
    }

    private interface StepHook {
        void run(Inventory module);
    }

    private static final StepHook NO_OP = module -> {};

    private static final class PlannedClick {
        final int slotId;
        final int button;
        final int mode;
        final StepValidator validator;
        final StepHook beforeExecute;
        final StepHook afterExecute;

        PlannedClick(int slotId, int button, int mode, StepValidator validator, StepHook beforeExecute, StepHook afterExecute) {
            this.slotId = slotId;
            this.button = button;
            this.mode = mode;
            this.validator = validator;
            this.beforeExecute = beforeExecute;
            this.afterExecute = afterExecute;
        }
    }

    private static final class ActionCandidate {
        final int cost;
        final int targetHotbarSlot;
        final int priorityIndex;
        final int resultingSize;
        final int sourcePreference;
        final int sourceInventoryIndex;
        final List<PlannedClick> steps;

        ActionCandidate(int cost, int targetHotbarSlot, int priorityIndex, int resultingSize, int sourcePreference, int sourceInventoryIndex, List<PlannedClick> steps) {
            this.cost = cost;
            this.targetHotbarSlot = targetHotbarSlot;
            this.priorityIndex = priorityIndex;
            this.resultingSize = resultingSize;
            this.sourcePreference = sourcePreference;
            this.sourceInventoryIndex = sourceInventoryIndex;
            this.steps = steps;
        }
    }

    private static final class SlotAssignment {
        final int hotbarSlot;
        final String storageId;
        final int priorityIndex;

        SlotAssignment(int hotbarSlot, String storageId, int priorityIndex) {
            this.hotbarSlot = hotbarSlot;
            this.storageId = storageId;
            this.priorityIndex = priorityIndex;
        }
    }

    private static final class InventorySnapshot {
        static final int INVENTORY_SIZE = 36;

        final ItemStack[] slots = new ItemStack[INVENTORY_SIZE];
        final ItemStack carried;
        final int firstEmptyMainInventoryIndex;

        private InventorySnapshot(ItemStack[] slots, ItemStack carried, int firstEmptyMainInventoryIndex) {
            System.arraycopy(slots, 0, this.slots, 0, slots.length);
            this.carried = carried;
            this.firstEmptyMainInventoryIndex = firstEmptyMainInventoryIndex;
        }

        static InventorySnapshot capture() {
            ItemStack[] slots = new ItemStack[INVENTORY_SIZE];
            int firstEmptyMain = -1;
            for (int inventoryIndex = 0; inventoryIndex < INVENTORY_SIZE; inventoryIndex++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(inventoryIndex);
                slots[inventoryIndex] = stack != null ? stack.copy() : null;
                if (inventoryIndex >= InventoryPlayer.getHotbarSize() && firstEmptyMain == -1 && stack == null) {
                    firstEmptyMain = inventoryIndex;
                }
            }

            ItemStack carried = mc.thePlayer.inventory.getItemStack();
            return new InventorySnapshot(slots, carried != null ? carried.copy() : null, firstEmptyMain);
        }

        ItemStack getSlot(int inventoryIndex) {
            if (inventoryIndex < 0 || inventoryIndex >= INVENTORY_SIZE) {
                return null;
            }
            return slots[inventoryIndex];
        }
    }
}
