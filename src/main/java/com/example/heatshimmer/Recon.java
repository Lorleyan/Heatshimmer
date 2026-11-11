package com.example.heatshimmer;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Recon only: writes the real class/method signatures of this Minecraft install to
 * heatshimmer-recon.txt in the game folder. It touches no rendering.
 * Uses only string class names, so it compiles regardless of the game's API.
 */
final class Recon {
	private static final int MAX_FILE_CHARS = 8000;

	private static final String[] CLASSES = {
		"net.minecraft.client.renderer.GameRenderer",
		"net.minecraft.client.renderer.LevelRenderer",
		"net.minecraft.client.renderer.LevelTargetBundle",
		"net.minecraft.client.renderer.PostChain",
		"net.minecraft.client.renderer.PostPass",
		"net.minecraft.client.renderer.PostChainConfig",
		"net.minecraft.client.renderer.ShaderManager",
		"net.minecraft.client.renderer.RenderPipelines",
		"net.minecraft.client.renderer.BindGroupLayouts",
		"net.minecraft.client.renderer.GlobalSettingsUniform",
		"net.minecraft.client.renderer.DynamicGpuData",
		"net.minecraft.client.renderer.state.GameRenderState",
		"net.minecraft.client.renderer.state.level.CameraRenderState",
		"net.minecraft.client.renderer.state.level.LevelRenderState",
		"net.minecraft.client.Camera",
		"com.mojang.blaze3d.pipeline.RenderTarget",
		"com.mojang.blaze3d.pipeline.TextureTarget",
		"com.mojang.blaze3d.systems.RenderSystem",
		"com.mojang.renderpearl.frontend.FrontendRenderPass",
		"com.mojang.renderpearl.frontend.FrontendCommandEncoder",
		"com.mojang.renderpearl.frontend.FrontendGpuDevice",
		"com.mojang.renderpearl.api.commands.RenderPass",
		"com.mojang.renderpearl.api.commands.CommandEncoder",
		"com.mojang.renderpearl.api.commands.RenderPassDescriptor",
		"com.mojang.renderpearl.api.commands.RenderPassDescriptor$Builder",
		"com.mojang.renderpearl.api.device.GpuDevice",
		"com.mojang.renderpearl.api.pipeline.RenderPipeline",
		"com.mojang.renderpearl.api.pipeline.RenderPipeline$Builder",
		"com.mojang.renderpearl.api.pipeline.RenderPipeline$Snippet",
		"com.mojang.renderpearl.api.pipeline.BindGroupLayout",
		"com.mojang.renderpearl.api.pipeline.BindGroupLayout$Builder",
		"com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline",
		"com.mojang.renderpearl.api.pipeline.DepthStencilState",
		"com.mojang.renderpearl.api.pipeline.BlendFunction",
		"com.mojang.renderpearl.api.pipeline.PrimitiveTopology",
		"com.mojang.renderpearl.api.pipeline.UniformType",
		"com.mojang.renderpearl.api.textures.GpuTexture",
		"com.mojang.renderpearl.api.textures.GpuTextureView",
		"com.mojang.renderpearl.api.textures.GpuSampler",
		"com.mojang.renderpearl.api.textures.FilterMode",
		"com.mojang.renderpearl.api.textures.AddressMode",
		"com.mojang.renderpearl.api.GpuFormat",
		"com.mojang.renderpearl.api.buffers.GpuBuffer",
		"com.mojang.renderpearl.api.buffers.GpuBufferSlice",
		"net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents",
		"net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents",
		"net.fabricmc.fabric.api.client.rendering.v1.LevelRenderEvents",
	};

	static void run() throws Exception {
		StringBuilder sb = new StringBuilder(1 << 20);
		sb.append("HeatShimmer recon\n");
		sb.append("Java: ").append(System.getProperty("java.version")).append('\n');

		sb.append("\n##### LOADED MODS #####\n");
		for (ModContainer mc : FabricLoader.getInstance().getAllMods()) {
			sb.append(mc.getMetadata().getId()).append(' ')
				.append(mc.getMetadata().getVersion().getFriendlyString()).append('\n');
		}

		sb.append("\n##### CLASS LIST: minecraft rendering packages #####\n");
		FabricLoader.getInstance().getModContainer("minecraft").ifPresent(mc ->
			listClasses(sb, mc, n ->
				n.startsWith("net/minecraft/client/renderer/")
					|| n.startsWith("com/mojang/renderpearl/api/")
					|| n.startsWith("com/mojang/renderpearl/frontend/")
					|| n.startsWith("com/mojang/blaze3d/pipeline/")
					|| n.startsWith("com/mojang/blaze3d/systems/")));

		sb.append("\n##### CLASS LIST: Fabric API rendering modules #####\n");
		for (ModContainer mc : FabricLoader.getInstance().getAllMods()) {
			String id = mc.getMetadata().getId();
			if (id.startsWith("fabric-rendering") || id.startsWith("fabric-renderer")) {
				sb.append("-- ").append(id).append('\n');
				listClasses(sb, mc, n -> n.contains("/api/"));
			}
		}

		sb.append("\n##### POST EFFECT / SHADER FILES #####\n");
		FabricLoader.getInstance().getModContainer("minecraft").ifPresent(mc ->
			dumpResources(sb, mc, n ->
				n.startsWith("assets/minecraft/post_effect/")
					|| n.startsWith("assets/minecraft/shaders/post/")
					|| n.startsWith("assets/minecraft/shaders/core/blit")
					|| n.startsWith("assets/minecraft/shaders/core/screenquad")));

		sb.append("\n##### CLASS DETAILS #####\n");
		for (String name : CLASSES) {
			dumpClass(sb, name);
		}

		Path out = FabricLoader.getInstance().getGameDir().resolve("heatshimmer-recon.txt");
		Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
		HeatShimmerClient.LOGGER.info("[HeatShimmer] Recon written to {}", out.toAbsolutePath());
	}

	private static void listClasses(StringBuilder sb, ModContainer mc, Predicate<String> keep) {
		for (Path root : mc.getRootPaths()) {
			try (Stream<Path> s = Files.walk(root)) {
				List<String> names = new ArrayList<>();
				s.filter(Files::isRegularFile).forEach(p -> {
					String n = root.relativize(p).toString().replace('\\', '/');
					if (n.endsWith(".class") && keep.test(n)) {
						names.add(n.substring(0, n.length() - 6));
					}
				});
				Collections.sort(names);
				for (String n : names) {
					sb.append(n).append('\n');
				}
			} catch (Throwable t) {
				sb.append("(could not list: ").append(t).append(")\n");
			}
		}
	}

	private static void dumpResources(StringBuilder sb, ModContainer mc, Predicate<String> keep) {
		for (Path root : mc.getRootPaths()) {
			try (Stream<Path> s = Files.walk(root)) {
				List<Path> files = new ArrayList<>();
				s.filter(Files::isRegularFile).forEach(p -> {
					String n = root.relativize(p).toString().replace('\\', '/');
					if (keep.test(n)) {
						files.add(p);
					}
				});
				files.sort(null);
				for (Path p : files) {
					sb.append("\n--- ").append(root.relativize(p).toString().replace('\\', '/')).append(" ---\n");
					try {
						String text = Files.readString(p, StandardCharsets.UTF_8);
						if (text.length() > MAX_FILE_CHARS) {
							text = text.substring(0, MAX_FILE_CHARS) + "\n...(truncated)";
						}
						sb.append(text).append('\n');
					} catch (Throwable t) {
						sb.append("(unreadable: ").append(t).append(")\n");
					}
				}
			} catch (Throwable t) {
				sb.append("(could not list resources: ").append(t).append(")\n");
			}
		}
	}

	private static void dumpClass(StringBuilder sb, String name) {
		sb.append("\n=== ").append(name).append(" ===\n");
		Class<?> c;
		try {
			c = Class.forName(name, false, Recon.class.getClassLoader());
		} catch (Throwable t) {
			sb.append("NOT FOUND\n");
			return;
		}
		try {
			String kind = c.isInterface() ? "interface" : c.isEnum() ? "enum" : c.isRecord() ? "record" : "class";
			sb.append(Modifier.toString(c.getModifiers())).append(' ').append(kind).append(' ').append(c.getName()).append('\n');
			if (c.getSuperclass() != null) {
				sb.append("  extends ").append(c.getSuperclass().getName()).append('\n');
			}
			for (Class<?> i : c.getInterfaces()) {
				sb.append("  implements ").append(i.getName()).append('\n');
			}
		} catch (Throwable t) {
			sb.append("ERROR reading header: ").append(t).append('\n');
		}

		sb.append("  -- fields\n");
		try {
			for (Field f : c.getDeclaredFields()) {
				if (f.isSynthetic()) {
					continue;
				}
				try {
					sb.append("  ").append(f.toGenericString()).append('\n');
				} catch (Throwable t) {
					sb.append("  (field ").append(f.getName()).append(" unreadable)\n");
				}
			}
		} catch (Throwable t) {
			sb.append("  ERROR reading fields: ").append(t).append('\n');
		}

		sb.append("  -- constructors\n");
		try {
			for (Constructor<?> k : c.getDeclaredConstructors()) {
				try {
					sb.append("  ").append(k.toGenericString()).append('\n');
				} catch (Throwable t) {
					sb.append("  (constructor unreadable)\n");
				}
			}
		} catch (Throwable t) {
			sb.append("  ERROR reading constructors: ").append(t).append('\n');
		}

		sb.append("  -- methods\n");
		try {
			List<String> lines = new ArrayList<>();
			for (Method m : c.getDeclaredMethods()) {
				if (m.isSynthetic() || m.isBridge()) {
					continue;
				}
				try {
					lines.add(m.toGenericString());
				} catch (Throwable t) {
					lines.add("(method " + m.getName() + " unreadable)");
				}
			}
			Collections.sort(lines);
			for (String l : lines) {
				sb.append("  ").append(l).append('\n');
			}
		} catch (Throwable t) {
			sb.append("  ERROR reading methods: ").append(t).append('\n');
		}
	}
}
