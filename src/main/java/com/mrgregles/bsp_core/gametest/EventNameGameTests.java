package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Forge's event bus names the listener it makes for an {@code @SubscribeEvent} method from the package, the declaring class's simple
 * name, the method name and the event's simple name, and keeps one listener per name. Two handlers that share all four (two nested
 * classes called {@code Register} in one package, each with {@code onKeys(RegisterKeyMappingsEvent)}) collapse into one: the first
 * runs once for each of them and the others never run. That once registered the X-ray key six times and no other BSP key.
 * This reads the mod's scan data (no client class is loaded) and fails on any such pair, client handlers included.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class EventNameGameTests {
    private static String simpleName(String binaryName) {
        String s = binaryName.substring(binaryName.lastIndexOf('.') + 1);
        return s.substring(s.lastIndexOf('$') + 1);
    }

    @GameTest(template = "empty", batch = "event_names", timeoutTicks = 20)
    public void event_handlers_have_names_of_their_own(GameTestHelper h) {
        var file = ModList.get().getModFileById(BSPCore.MODID);
        h.assertTrue(file != null, "BSP-Core's mod file is listed");
        Type subscribe = Type.getType(SubscribeEvent.class);
        Map<String, List<String>> byName = new HashMap<>();
        int handlers = 0;
        for (ModFileScanData.AnnotationData a : file.getFile().getScanResult().getAnnotations()) {
            if (!a.annotationType().equals(subscribe)) {
                continue;
            }
            String member = a.memberName(); // name(descriptor)
            int paren = member.indexOf('(');
            Type[] args = Type.getArgumentTypes(member.substring(paren));
            if (args.length == 0) {
                continue;
            }
            String owner = a.clazz().getClassName();
            String pkg = owner.contains(".") ? owner.substring(0, owner.lastIndexOf('.')) : "";
            String name = pkg + ".__" + simpleName(owner) + "_" + member.substring(0, paren) + "_" + simpleName(args[0].getClassName());
            byName.computeIfAbsent(name, k -> new ArrayList<>()).add(owner + "." + member.substring(0, paren));
            handlers++;
        }
        h.assertTrue(handlers > 100, "the scan found BSP-Core's event handlers (" + handlers + ")");
        List<String> clashes = new ArrayList<>();
        byName.forEach((name, owners) -> {
            if (owners.size() > 1) {
                clashes.add(name + " " + owners);
            }
        });
        h.assertTrue(clashes.isEmpty(), "event handlers that Forge would merge into one listener: " + clashes);
        h.succeed();
    }
}
