package com.example.hotbarsaver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HotbarSaverClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("hotbarsaver.json");

    private static KeyBinding saveKey;
    private static KeyBinding applyKey;

    /** 9 item ids, "minecraft:air" = empty slot */
    private static List<String> saved = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        saveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hotbarsaver.save", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.hotbarsaver"));
        applyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hotbarsaver.apply", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.hotbarsaver"));

        loadConfig();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (saveKey.wasPressed()) saveHotbar(client);
            while (applyKey.wasPressed()) applyHotbar(client);
        });
    }

    private static void saveHotbar(MinecraftClient client) {
        if (client.player == null) return;
        saved = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            saved.add(Registries.ITEM.getId(s.getItem()).toString());
        }
        saveConfig();
        client.player.sendMessage(Text.literal("Hotbar layout saved."), true);
    }

    private static void applyHotbar(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        if (saved.size() != 9) {
            client.player.sendMessage(Text.literal("No saved hotbar yet (press K first)."), true);
            return;
        }

        PlayerInventory inv = client.player.getInventory();
        int syncId = client.player.playerScreenHandler.syncId;
        int missing = 0;

        for (int i = 0; i < 9; i++) {
            Item wanted = itemOf(saved.get(i));
            if (wanted == net.minecraft.item.Items.AIR) continue;
            if (inv.getStack(i).isOf(wanted)) continue;

            int from = findSource(inv, wanted, i);
            if (from == -1) { missing++; continue; }

            // PlayerScreenHandler slot ids: main inventory 9-35 -> 9-35, hotbar 0-8 -> 36-44
            int slotId = from < 9 ? 36 + from : from;
            // SWAP with button = hotbar index swaps the clicked slot with that hotbar slot
            client.interactionManager.clickSlot(syncId, slotId, i, SlotActionType.SWAP, client.player);
        }

        client.player.sendMessage(Text.literal(missing == 0
                ? "Hotbar restored."
                : "Hotbar restored, " + missing + " item(s) not found in inventory."), true);
    }

    /** Finds an inventory index holding `wanted`, skipping hotbar slots that already hold their own correct item. */
    private static int findSource(PlayerInventory inv, Item wanted, int targetSlot) {
        // Prefer main inventory first
        for (int j = 9; j < 36; j++) {
            if (inv.getStack(j).isOf(wanted)) return j;
        }
        for (int j = 0; j < 9; j++) {
            if (j == targetSlot) continue;
            if (!inv.getStack(j).isOf(wanted)) continue;
            Item jWanted = itemOf(saved.get(j));
            if (jWanted == wanted) continue; // that slot is already correct, leave it
            return j;
        }
        return -1;
    }

    private static Item itemOf(String id) {
        Identifier ident = Identifier.tryParse(id);
        return ident == null ? net.minecraft.item.Items.AIR : Registries.ITEM.get(ident);
    }

    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG)) {
                String[] arr = GSON.fromJson(Files.readString(CONFIG), String[].class);
                if (arr != null && arr.length == 9) saved = new ArrayList<>(List.of(arr));
            }
        } catch (Exception ignored) {}
    }

    private static void saveConfig() {
        try {
            Files.writeString(CONFIG, GSON.toJson(saved));
        } catch (IOException ignored) {}
    }
}
