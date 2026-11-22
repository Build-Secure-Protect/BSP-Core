package com.mrgregles.bsp_core.compat;

import com.mrgregles.bsp_core.BSPCore;
import javax.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The only class that touches LuckPerms. Read-only: BSP-Core stores rank names on kits and never writes to LuckPerms.
 * Copied from BSP-Region Guard's bridge (the same class in BSP-RankUp), so the three mods reach LuckPerms the same way.
 *
 * <p>Two ways in. On a Forge server with the LuckPerms Forge mod, the API classes are on our class path and are used
 * directly. On a Mohist server with the LuckPerms Bukkit plugin, the plugin lives in its own class loader and our
 * mod cannot see its classes, so the bridge finds the plugin through Bukkit's plugin manager and talks to the same
 * API by reflection. Either way the rest of the mod only sees group names.
 */
public final class LuckPermsBridge {
    private static final String PROVIDER = "net.luckperms.api.LuckPermsProvider";

    /** How LuckPerms was reached. */
    public enum Route { NONE, FORGE, BUKKIT }

    @Nullable
    private static Route route;
    /** The LuckPerms API object when reached by reflection (a class from the plugin's loader). */
    @Nullable
    private static Object bukkitApi;

    private LuckPermsBridge() {
    }

    public static boolean isPresent() {
        return route() != Route.NONE;
    }

    public static Route route() {
        if (route == null) {
            route = detect();
            if (route != Route.NONE) {
                BSPCore.LOGGER.info("LuckPerms reached through the {} edition", route == Route.FORGE ? "Forge" : "Bukkit");
            }
        }
        return route;
    }

    private static Route detect() {
        try {
            Class.forName(PROVIDER);
            net.luckperms.api.LuckPermsProvider.get();
            return Route.FORGE;
        } catch (ClassNotFoundException | NoClassDefFoundError | IllegalStateException e) {
            // not on our class path: try Bukkit
        }
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            if (pluginManager == null) {
                return Route.NONE;
            }
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, "LuckPerms");
            if (plugin == null) {
                return Route.NONE;
            }
            Class<?> provider = plugin.getClass().getClassLoader().loadClass(PROVIDER);
            bukkitApi = provider.getMethod("get").invoke(null);
            return bukkitApi == null ? Route.NONE : Route.BUKKIT;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            BSPCore.LOGGER.debug("LuckPerms not reachable through Bukkit: {}", e.toString());
            return Route.NONE;
        }
    }

    /** Forgets the cached answer, for example after a reload. */
    public static void reset() {
        route = null;
        bukkitApi = null;
    }

    // ------------------------------------------------------------------ lookups

    /**
     * The groups a player is in, directly or by inheritance, lower-cased. Empty when LuckPerms is absent or the user
     * is not loaded (online players always are).
     */
    public static Set<String> groupsOf(UUID player) {
        switch (route()) {
            case FORGE -> {
                try {
                    net.luckperms.api.model.user.User user = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player);
                    if (user == null) {
                        return Collections.emptySet();
                    }
                    Set<String> out = new TreeSet<>();
                    out.add(user.getPrimaryGroup().toLowerCase(Locale.ROOT));
                    for (net.luckperms.api.model.group.Group g : user.getInheritedGroups(user.getQueryOptions())) {
                        out.add(g.getName().toLowerCase(Locale.ROOT));
                    }
                    return out;
                } catch (RuntimeException e) {
                    BSPCore.LOGGER.warn("LuckPerms user lookup failed", e);
                    return Collections.emptySet();
                }
            }
            case BUKKIT -> {
                try {
                    Object userManager = call(bukkitApi, "getUserManager");
                    Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player);
                    if (user == null) {
                        return Collections.emptySet();
                    }
                    Set<String> out = new TreeSet<>();
                    out.add(((String) call(user, "getPrimaryGroup")).toLowerCase(Locale.ROOT));
                    Object queryOptions = call(user, "getQueryOptions");
                    Method inherited = find(user.getClass(), "getInheritedGroups");
                    Collection<?> groups = (Collection<?>) inherited.invoke(user, queryOptions);
                    for (Object g : groups) {
                        out.add(((String) call(g, "getName")).toLowerCase(Locale.ROOT));
                    }
                    return out;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    BSPCore.LOGGER.warn("LuckPerms (Bukkit) user lookup failed", e);
                    return Collections.emptySet();
                }
            }
            default -> {
                return Collections.emptySet();
            }
        }
    }

    /**
     * Whether LuckPerms grants this player the node (any node, typed at any time), or null when
     * LuckPerms is absent or the user is not loaded (online players always are).
     */
    @javax.annotation.Nullable
    public static Boolean hasPermission(UUID player, String node) {
        switch (route()) {
            case FORGE -> {
                try {
                    net.luckperms.api.model.user.User user = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player);
                    return user == null ? null : user.getCachedData().getPermissionData().checkPermission(node).asBoolean();
                } catch (RuntimeException e) {
                    BSPCore.LOGGER.warn("LuckPerms permission lookup failed", e);
                    return null;
                }
            }
            case BUKKIT -> {
                try {
                    Object userManager = call(bukkitApi, "getUserManager");
                    Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player);
                    if (user == null) {
                        return null;
                    }
                    Object data = call(call(user, "getCachedData"), "getPermissionData");
                    Object result = find(data.getClass(), "checkPermission").invoke(data, node);
                    return (Boolean) call(result, "asBoolean");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    BSPCore.LOGGER.warn("LuckPerms (Bukkit) permission lookup failed", e);
                    return null;
                }
            }
            default -> {
                return null;
            }
        }
    }

    /** Names of every loaded group (rank). Empty when LuckPerms is absent. */
    public static List<String> groupNames() {
        switch (route()) {
            case FORGE -> {
                try {
                    return net.luckperms.api.LuckPermsProvider.get().getGroupManager().getLoadedGroups().stream()
                            .map(net.luckperms.api.model.group.Group::getName)
                            .sorted()
                            .toList();
                } catch (RuntimeException e) {
                    BSPCore.LOGGER.warn("LuckPerms group lookup failed", e);
                    return Collections.emptyList();
                }
            }
            case BUKKIT -> {
                try {
                    Object groupManager = call(bukkitApi, "getGroupManager");
                    Collection<?> groups = (Collection<?>) call(groupManager, "getLoadedGroups");
                    Set<String> out = new TreeSet<>();
                    for (Object g : groups) {
                        out.add((String) call(g, "getName"));
                    }
                    return List.copyOf(out);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    BSPCore.LOGGER.warn("LuckPerms (Bukkit) group lookup failed", e);
                    return Collections.emptyList();
                }
            }
            default -> {
                return Collections.emptyList();
            }
        }
    }

    // ------------------------------------------------------------------ members of groups (the VIP board)

    /**
     * Every player LuckPerms has stored as a member of one of {@code groups} (lower-case names) or of a group that inherits one,
     * online or not. Only true, unexpired {@code group.<name>} nodes count, in any context. Asks LuckPerms' storage, so it
     * completes later, off the server thread; empty at once when LuckPerms is absent. Fails when LuckPerms throws.
     */
    public static java.util.concurrent.CompletableFuture<Set<UUID>> membersOf(Set<String> groups) {
        try {
            return switch (route()) {
                case FORGE -> forgeMembers(groups);
                case BUKKIT -> bukkitMembers(groups);
                default -> java.util.concurrent.CompletableFuture.completedFuture(Set.of());
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }

    /**
     * The names LuckPerms last saw for these players (it keeps them in lower case), "" for one it has none for. Asked one by one, so
     * pass only players whose names are not known otherwise. Empty at once without LuckPerms or players.
     */
    public static java.util.concurrent.CompletableFuture<java.util.Map<UUID, String>> usernames(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.Map.of());
        }
        try {
            java.util.Map<UUID, java.util.concurrent.CompletableFuture<String>> names = new java.util.HashMap<>();
            switch (route()) {
                case FORGE -> {
                    net.luckperms.api.model.user.UserManager users = net.luckperms.api.LuckPermsProvider.get().getUserManager();
                    for (UUID id : ids) {
                        names.put(id, users.lookupUsername(id).exceptionally(e -> null));
                    }
                }
                case BUKKIT -> {
                    Object users = call(bukkitApi, "getUserManager");
                    Method lookup = find(users.getClass(), "lookupUsername");
                    for (UUID id : ids) {
                        @SuppressWarnings("unchecked")
                        var name = (java.util.concurrent.CompletableFuture<String>) lookup.invoke(users, id);
                        names.put(id, name.exceptionally(e -> null));
                    }
                }
                default -> {
                    return java.util.concurrent.CompletableFuture.completedFuture(java.util.Map.of());
                }
            }
            return namesOf(names);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }

    private static java.util.concurrent.CompletableFuture<Set<UUID>> forgeMembers(Set<String> groups) {
        net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
        java.util.Map<String, Set<String>> parents = new java.util.HashMap<>();
        for (net.luckperms.api.model.group.Group g : lp.getGroupManager().getLoadedGroups()) {
            Set<String> up = new TreeSet<>();
            for (net.luckperms.api.model.group.Group p : g.getInheritedGroups(net.luckperms.api.query.QueryOptions.nonContextual())) {
                up.add(p.getName().toLowerCase(Locale.ROOT));
            }
            parents.put(g.getName().toLowerCase(Locale.ROOT), up);
        }
        net.luckperms.api.model.user.UserManager users = lp.getUserManager();
        List<java.util.concurrent.CompletableFuture<java.util.Map<UUID, Collection<net.luckperms.api.node.Node>>>> searches = new java.util.ArrayList<>();
        for (String g : com.mrgregles.bsp_core.score.VipList.withHeirs(groups, parents)) {
            searches.add(users.searchAll(net.luckperms.api.node.matcher.NodeMatcher.key("group." + g)));
        }
        return java.util.concurrent.CompletableFuture.allOf(searches.toArray(new java.util.concurrent.CompletableFuture[0])).thenApply(done -> {
            Set<UUID> ids = new java.util.HashSet<>();
            for (var search : searches) {
                search.join().forEach((id, nodes) -> {
                    for (net.luckperms.api.node.Node n : nodes) {
                        if (n.getValue() && !n.hasExpired()) {
                            ids.add(id);
                            break;
                        }
                    }
                });
            }
            return ids;
        });
    }

    private static java.util.concurrent.CompletableFuture<Set<UUID>> bukkitMembers(Set<String> groups) throws ReflectiveOperationException {
        ClassLoader loader = bukkitApi.getClass().getClassLoader();
        Method key = loader.loadClass("net.luckperms.api.node.matcher.NodeMatcher").getMethod("key", String.class);
        Object noContext = loader.loadClass("net.luckperms.api.query.QueryOptions").getMethod("nonContextual").invoke(null);
        java.util.Map<String, Set<String>> parents = new java.util.HashMap<>();
        for (Object g : (Collection<?>) call(call(bukkitApi, "getGroupManager"), "getLoadedGroups")) {
            Set<String> up = new TreeSet<>();
            for (Object p : (Collection<?>) find(g.getClass(), "getInheritedGroups").invoke(g, noContext)) {
                up.add(((String) call(p, "getName")).toLowerCase(Locale.ROOT));
            }
            parents.put(((String) call(g, "getName")).toLowerCase(Locale.ROOT), up);
        }
        Object users = call(bukkitApi, "getUserManager");
        Method searchAll = find(users.getClass(), "searchAll");
        List<java.util.concurrent.CompletableFuture<?>> searches = new java.util.ArrayList<>();
        for (String g : com.mrgregles.bsp_core.score.VipList.withHeirs(groups, parents)) {
            searches.add((java.util.concurrent.CompletableFuture<?>) searchAll.invoke(users, key.invoke(null, "group." + g)));
        }
        return java.util.concurrent.CompletableFuture.allOf(searches.toArray(new java.util.concurrent.CompletableFuture[0])).thenApply(done -> {
            try {
                Set<UUID> ids = new java.util.HashSet<>();
                for (var search : searches) {
                    for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) search.join()).entrySet()) {
                        for (Object n : (Collection<?>) e.getValue()) {
                            if (Boolean.TRUE.equals(call(n, "getValue")) && !Boolean.TRUE.equals(call(n, "hasExpired"))) {
                                ids.add((UUID) e.getKey());
                                break;
                            }
                        }
                    }
                }
                return ids;
            } catch (ReflectiveOperationException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
    }

    /** Waits for every name; a missing one becomes "". */
    private static java.util.concurrent.CompletableFuture<java.util.Map<UUID, String>> namesOf(java.util.Map<UUID, java.util.concurrent.CompletableFuture<String>> names) {
        return java.util.concurrent.CompletableFuture.allOf(names.values().toArray(new java.util.concurrent.CompletableFuture[0])).thenApply(done -> {
            java.util.Map<UUID, String> out = new java.util.HashMap<>();
            names.forEach((id, f) -> {
                String n = f.join();
                out.put(id, n == null ? "" : n);
            });
            return out;
        });
    }

    // ------------------------------------------------------------------ reflection helpers

    /** Calls a public no-argument method by name, looking through the class and its interfaces. */
    private static Object call(Object target, String method) throws ReflectiveOperationException {
        Method m = find(target.getClass(), method);
        return m.invoke(target);
    }

    /** Methods found by {@link #find}, by class and name: the Bukkit route is asked for every locked card on a page. */
    private static final java.util.Map<String, Method> FOUND = new java.util.concurrent.ConcurrentHashMap<>();

    private static Method find(Class<?> type, String name) throws NoSuchMethodException {
        Method known = FOUND.get(type.getName() + "#" + name);
        if (known != null) {
            return known;
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                FOUND.put(type.getName() + "#" + name, m);
                return m;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
