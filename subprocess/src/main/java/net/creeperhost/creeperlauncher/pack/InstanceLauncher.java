package net.creeperhost.creeperlauncher.pack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.covers1624.jdkutils.JavaInstall;
import net.covers1624.jdkutils.JavaVersion;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.maven.MavenNotation;
import net.covers1624.quack.util.DataUtils;
import net.covers1624.quack.util.SneakyUtils.ThrowingConsumer;
import net.covers1624.quack.util.SneakyUtils.ThrowingRunnable;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.accounts.AccountManager;
import net.creeperhost.creeperlauncher.accounts.AccountProfile;
import net.creeperhost.creeperlauncher.api.data.instances.LaunchInstanceData;
import net.creeperhost.creeperlauncher.install.tasks.InstallAssetsTask;
import net.creeperhost.creeperlauncher.install.tasks.NewDownloadTask;
import net.creeperhost.creeperlauncher.install.tasks.TaskProgressAggregator;
import net.creeperhost.creeperlauncher.install.tasks.TaskProgressListener;
import net.creeperhost.creeperlauncher.minecraft.jsons.AssetIndexManifest;
import net.creeperhost.creeperlauncher.minecraft.jsons.VersionListManifest;
import net.creeperhost.creeperlauncher.minecraft.jsons.VersionManifest;
import net.creeperhost.creeperlauncher.minecraft.jsons.VersionManifest.AssetIndex;
import net.creeperhost.creeperlauncher.util.DNSUtils;
import net.creeperhost.creeperlauncher.util.QuackProgressAdapter;
import net.creeperhost.creeperlauncher.util.StreamGobblerLog;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.covers1624.quack.collection.ColUtils.iterable;
import static net.covers1624.quack.util.SneakyUtils.sneak;
import static net.creeperhost.creeperlauncher.minecraft.jsons.VersionManifest.LEGACY_ASSETS_VERSION;

/**
 * Responsible for launching a specific instance.
 * <p>
 * Created by covers1624 on 17/11/21.
 */
public class InstanceLauncher {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker MINECRAFT_MARKER = MarkerManager.getMarker("MINECRAFT");
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final LocalInstance instance;
    private Phase phase = Phase.NOT_STARTED;

    private final List<VersionManifest> manifests = new ArrayList<>();
    private final List<Path> tempDirs = new LinkedList<>();
    @Nullable
    private Thread processThread;
    @Nullable
    private Process process;
    @Nullable
    private LogThread logThread;

    private boolean forceStopped;

    private final ProgressTracker progressTracker = new ProgressTracker();
    private final List<ThrowingConsumer<LaunchContext, IOException>> startTasks = new LinkedList<>();
    private final List<ThrowingRunnable<IOException>> exitTasks = new LinkedList<>();

    private static final int NUM_STEPS = 5;

    public InstanceLauncher(LocalInstance instance) {
        this.instance = instance;
    }

    /**
     * Adds a task to execute when this Instance starts.
     *
     * @param task The task.
     */
    public void withStartTask(ThrowingConsumer<LaunchContext, IOException> task) {
        startTasks.add(task);
    }

    /**
     * Adds a task to execute when this Instance exits.
     *
     * @param task The task.
     */
    public void withExitTask(ThrowingRunnable<IOException> task) {
        exitTasks.add(task);
    }

    /**
     * If the instance has already been started and is currently running.
     *
     * @return If the instance is already running.
     */
    public boolean isRunning() {
        return processThread != null;
    }

    /**
     * Get the current phase.
     *
     * @return The phase.
     */
    public Phase getPhase() {
        return phase;
    }

    /**
     * Attempt to start launching the configured instance.
     * <p>
     * It is illegal to call this method if {@link #isRunning()} returns true.
     *
     * @throws InstanceLaunchException If there was a direct error preparing the instance to be launched.
     */
    public synchronized void launch(CancellationToken token, @Nullable String offlineUsername) throws InstanceLaunchException {
        assert !isRunning();
        DNSUtils.logImportantHosts();
        LOGGER.info("Attempting to launch instance {}({})", instance.getName(), instance.getUuid());
        setPhase(Phase.INITIALIZING);
        progressTracker.reset(NUM_STEPS);

        Path assetsDir = Constants.BIN_LOCATION.resolve("assets");
        Path versionsDir = Constants.BIN_LOCATION.resolve("versions");
        Path librariesDir = Constants.BIN_LOCATION.resolve("libraries");

        Set<String> features = new HashSet<>();
        if (instance.width != 0 && instance.height != 0) {
            features.add("has_custom_resolution");
        }

        Set<String> privateTokens = new HashSet<>();

        // This is run outside the future, as whatever is calling this method should immediately handle any errors
        // preparing the instance to be launched. It is not fun to propagate exceptions/errors across threads.
        ProcessBuilder builder = prepareProcess(token, offlineUsername, assetsDir, versionsDir, librariesDir, features, privateTokens);

        // Start thread.
        processThread = new Thread(() -> {
            try {
                try {
                    String logMessage = String.join(" ", builder.command());
                    for (String privateToken : privateTokens) {
                        logMessage = logMessage.replaceAll(privateToken, "*****");
                    }
                    LOGGER.info("Starting Minecraft with command '{}'", logMessage);
                    process = builder.start();
                } catch (IOException e) {
                    LOGGER.error("Failed to start minecraft process!", e);
                    setPhase(Phase.ERRORED);
                    Settings.webSocketAPI.sendMessage(new LaunchInstanceData.Stopped(instance.getUuid(), "launch_failed", -1));
                    process = null;
                    processThread = null;
                    return;
                }
                setPhase(Phase.STARTED);

                // Start up the logging threads.
                logThread = new LogThread(IOUtils.makeParents(instance.getDir().resolve("logs/console.log")));
                Logger logger = LogManager.getLogger("Minecraft");
                StreamGobblerLog stdoutGobbler = new StreamGobblerLog()
                        .setName("Instance STDOUT log Gobbler")
                        .setInput(process.getInputStream())
                        .setOutput(message -> {
                            logThread.bufferMessage(message);
                            logger.info(MINECRAFT_MARKER, message);
                        });
                StreamGobblerLog stderrGobbler = new StreamGobblerLog()
                        .setName("Instance STDERR log Gobbler")
                        .setInput(process.getErrorStream())
                        .setOutput(message -> {
                            logThread.bufferMessage(message);
                            logger.error(MINECRAFT_MARKER, message);
                        });
                stdoutGobbler.start();
                stderrGobbler.start();
                logThread.start();

                process.onExit().thenRunAsync(() -> {
                    // Not strictly necessary, but exits the log threads faster.
                    stdoutGobbler.stop();
                    stderrGobbler.stop();
                    logThread.stop = true;
                    logThread.interrupt();
                });

                while (process.isAlive()) {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    }
                }
                int exit = process.exitValue();
                LOGGER.info("Minecraft exited with status code: " + exit);
                setPhase(exit != 0 ? Phase.ERRORED : Phase.STOPPED);
                Settings.webSocketAPI.sendMessage(new LaunchInstanceData.Stopped(instance.getUuid(), exit != 0 && !forceStopped ? "errored" : "stopped", exit));
                forceStopped = false;
                process = null;
                processThread = null;
            } catch (Throwable t) {
                LOGGER.error("Minecraft thread exited with an unrecoverable error.", t);
                if (process != null) {
                    // Something is very broken, force kill minecraft and at least return to a recoverable state.
                    LOGGER.error("Force quitting Minecraft. Unrecoverable error occurred!");
                    process.destroyForcibly();
                    process = null;
                }
                processThread = null;
                setPhase(Phase.ERRORED);
                Settings.webSocketAPI.sendMessage(new LaunchInstanceData.Stopped(instance.getUuid(), "internal_error", -1));
            }
        });
        processThread.setName("Instance Thread [" + THREAD_COUNTER.getAndIncrement() + "]");
        processThread.setDaemon(true);
        processThread.start();
    }

    /**
     * Triggers a force stop of the running instance.
     * <p>
     * Does nothing if the instance is not running.`
     */
    public void forceStop() {
        if (!isRunning()) return;

        Process process = this.process;
        if (process == null) return;
        LOGGER.info("Force quitting Minecraft..");
        forceStopped = true;
        process.destroyForcibly();
    }

    /**
     * Resets the instance to a runnable state.
     */
    public void reset() throws InstanceLaunchException {
        if (isRunning()) throw new InstanceLaunchException("Instance is currently running. Stop it first.");
        if (phase == Phase.NOT_STARTED) return;

        assert process == null;
        assert processThread == null;

        manifests.clear();
        startTasks.clear();
        exitTasks.clear();
        setPhase(Phase.NOT_STARTED);
    }

    public void setLogStreaming(boolean state) {
        if (logThread == null) return;

        logThread.setStreamingEnabled(state);
    }

    private void setPhase(Phase newPhase) {
        if (newPhase == Phase.STOPPED || newPhase == Phase.ERRORED) {
            onStopped();
        }
        LOGGER.info("Setting phase: {}", newPhase);
        phase = newPhase;
    }

    private void onStopped() {
        for (ThrowingRunnable<IOException> exitTask : exitTasks) {
            try {
                exitTask.run();
            } catch (IOException e) {
                LOGGER.error("Failed to execute exit task for instance {}({})", instance.getName(), instance.getUuid(), e);
            }
        }

        for (Path tempDir : tempDirs) {
            if (Files.notExists(tempDir)) continue;
            LOGGER.info("Cleaning up temporary directory: {}", tempDir);
            try (Stream<Path> files = Files.walk(tempDir)) {
                files
                        .filter(Files::exists)
                        .sorted(Comparator.reverseOrder())
                        .forEach(sneak(Files::delete));
            } catch (IOException e) {
                LOGGER.warn("Failed to delete temp directory. Scheduling for app exit.");
                tempDir.toFile().deleteOnExit();
            }
        }
        tempDirs.clear();
    }

    private ProcessBuilder prepareProcess(CancellationToken token, String offlineUsername, Path assetsDir, Path versionsDir, Path librariesDir, Set<String> features, Set<String> privateTokens) throws InstanceLaunchException {
        try {
            progressTracker.startStep("Pre-Start Tasks"); // TODO locale support.
            Path gameDir = instance.getDir().toAbsolutePath();
            LaunchContext context = new LaunchContext();
            for (ThrowingConsumer<LaunchContext, IOException> startTask : startTasks) {
                startTask.accept(context);
            }
            progressTracker.finishStep();

            token.throwIfCancelled();

            progressTracker.startStep("Validate Java Runtime");
            Path javaExecutable;
            if (instance.embeddedJre) {
                String javaTarget = instance.versionManifest.getTargetVersion("runtime");
                Path javaHome;
                if (javaTarget == null) {
                    LOGGER.warn("VersionManifest does not specify java runtime version. Falling back to Vanilla major version, latest.");
                    JavaVersion version = getJavaVersion();
                    javaHome = Constants.JDK_INSTALL_MANAGER.provisionJdk(
                            version,
                            null,
                            true,
                            new QuackProgressAdapter(progressTracker.listenerForStep(true))
                    );
                } else {
                    javaHome = Constants.JDK_INSTALL_MANAGER.provisionJdk(
                            javaTarget,
                            true,
                            new QuackProgressAdapter(progressTracker.listenerForStep(true))
                    );
                }
                javaExecutable = JavaInstall.getJavaExecutable(javaHome, true);
            } else {
                javaExecutable = instance.jrePath;
            }
            progressTracker.finishStep();

            prepareManifests(token, versionsDir);

            token.throwIfCancelled();

            progressTracker.startStep("Validate assets");
            Pair<AssetIndex, AssetIndexManifest> assetPair = checkAssets(token, versionsDir);
            Path virtualAssets = buildVirtualAssets(assetPair.getLeft(), assetPair.getRight(), gameDir, assetsDir);
            progressTracker.finishStep();

            token.throwIfCancelled();

            List<VersionManifest.Library> libraries = collectLibraries(features);
            // Mojang may change libraries mid version.

            progressTracker.startStep("Validate libraries");
            validateLibraries(token, librariesDir, libraries);
            progressTracker.finishStep();

            token.throwIfCancelled();

            progressTracker.startStep("Validate client");
            validateClient(token, versionsDir);
            progressTracker.finishStep();

            token.throwIfCancelled();

            Path nativesDir = versionsDir.resolve(instance.modLoader).resolve(instance.modLoader + "-natives-" + System.nanoTime());
            extractNatives(nativesDir, librariesDir, libraries);

            Map<String, String> subMap = new HashMap<>();
            AccountProfile profile = AccountManager.get().getActiveProfile();
            if (offlineUsername != null || profile == null) {
                // Offline
                subMap.put("auth_player_name", offlineUsername);
                subMap.put("auth_uuid", new UUID(0, 0).toString());
                subMap.put("user_type", "legacy");
                subMap.put("auth_access_token", "null");
                subMap.put("user_properties", "{}");
                subMap.put("auth_session", "null");
            } else {
                subMap.put("auth_player_name", profile.username);
                subMap.put("auth_uuid", profile.uuid.toString());
                subMap.put("user_properties", "{}"); // TODO, we may need to provide this all the time.
                String accessToken;
                if (profile.msAuth != null) {
                    subMap.put("user_type", "msa");
                    subMap.put("xuid", profile.msAuth.xblUserHash);
                    accessToken = profile.msAuth.minecraftToken;
                } else {
                    assert profile.mcAuth != null;
                    subMap.put("user_type", "mojang");
                    accessToken = profile.mcAuth.accessToken;
                }
                String sessionToken = "token:" + accessToken + ":" + profile.uuid.toString().replace("-", "");
                subMap.put("auth_session", sessionToken);
                subMap.put("auth_access_token", accessToken);
                privateTokens.add(sessionToken);
                privateTokens.add(accessToken);
            }

            subMap.put("version_name", instance.modLoader);
            subMap.put("game_directory", gameDir.toString());
            subMap.put("assets_root", assetsDir.toAbsolutePath().toString());
            subMap.put("game_assets", virtualAssets.toAbsolutePath().toString());
            subMap.put("assets_index_name", manifests.get(0).assets);
            subMap.put("version_type", manifests.get(0).type);

            subMap.put("launcher_name", "FTBApp");
            subMap.put("launcher_version", Constants.APPVERSION);
            subMap.put("primary_jar", getGameJar(versionsDir).toAbsolutePath().toString());
            subMap.put("memory", String.valueOf(instance.memory));

            subMap.put("resolution_width", String.valueOf(instance.width));
            subMap.put("resolution_height", String.valueOf(instance.height));

            subMap.put("natives_directory", nativesDir.toAbsolutePath().toString());
            List<Path> classpath = collectClasspath(librariesDir, versionsDir, libraries);
            subMap.put("classpath", classpath.stream().distinct().map(e -> e.toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator)));
            subMap.put("classpath_separator", File.pathSeparator);
            subMap.put("library_directory", librariesDir.toAbsolutePath().toString());

            AssetIndexManifest.AssetObject icon = assetPair.getRight().objects.get("icons/minecraft.icns");
            if (icon != null) {
                Path path = Constants.BIN_LOCATION.resolve("assets")
                        .resolve("objects")
                        .resolve(icon.getPath());
                subMap.put("minecraft_icon", path.toAbsolutePath().toString());
            }

            StrSubstitutor sub = new StrSubstitutor(new StrLookup<>() {
                @Override
                public String lookup(String key) {
                    String value = subMap.get(key);
                    if (value == null) {
                        LOGGER.fatal("Unmapped token key '{}' in Minecraft arguments!! ", key);
                        return null;
                    }
                    return value;
                }
            });

            List<String> jvmArgs = VersionManifest.collectJVMArgs(manifests, features).stream()
                    .map(sub::replace)
                    .collect(Collectors.toList());
            List<String> progArgs = VersionManifest.collectProgArgs(manifests, features).stream()
                    .map(sub::replace)
                    .collect(Collectors.toList());

            List<String> command = new ArrayList<>(jvmArgs.size() + progArgs.size() + 2);
            command.addAll(context.shellArgs);
            command.add(javaExecutable.toAbsolutePath().toString());
            command.addAll(jvmArgs);
            command.addAll(context.extraJVMArgs);
            // TODO, these should be the defaults inside the app, not added here.
            for (String arg : Constants.MOJANG_DEFAULT_ARGS) {
                command.add(sub.replace(arg));
            }
            command.add("-Duser.language=en");
            command.add("-Duser.country=US");
            command.add(getMainClass());
            command.addAll(progArgs);
            command.addAll(context.extraProgramArgs);
            ProcessBuilder builder = new ProcessBuilder()
                    .directory(gameDir.toFile())
                    .command(command);

            Map<String, String> env = builder.environment();
            // Apparently this can override our passed in Java arguments.
            env.remove("_JAVA_OPTIONS");
            env.remove("JAVA_TOOL_OPTIONS");
            env.remove("JAVA_OPTIONS");

            return builder;
        } catch (Throwable ex) {
            if (ex instanceof CancellationToken.Cancellation cancellation) {
                throw cancellation;
            }
            throw new InstanceLaunchException("Failed to prepare instance '" + instance.getName() + "'(" + instance.getUuid() + ").", ex);
        }
    }

    private void prepareManifests(CancellationToken token, Path versionsDir) throws IOException, InstanceLaunchException {
        manifests.clear();
        VersionListManifest versions = VersionListManifest.update(versionsDir);
        Set<String> seen = new HashSet<>();
        String id = instance.modLoader;
        while (id != null) {
            token.throwIfCancelled();
            if (!seen.add(id)) throw new IllegalStateException("Circular VersionManifest reference. Root: " + instance.modLoader);
            LOGGER.info("Preparing manifest {}", id);
            VersionManifest manifest = versions.resolveOrLocal(versionsDir, id);
            if (manifest == null) {
                throw new InstanceLaunchException("Failed to prepare instance. Missing installed version " + id + ". Please validate/re-install.");
            }
            // Build in reverse order. First in list should be Minecraft's.
            manifests.add(0, manifest);
            id = manifest.inheritsFrom;
        }
    }

    private Pair<AssetIndex, AssetIndexManifest> checkAssets(CancellationToken token, Path versionsDir) throws IOException, InstanceLaunchException {
        assert !manifests.isEmpty();

        LOGGER.info("Updating assets..");
        VersionManifest manifest = manifests.get(0);
        AssetIndex index = manifest.assetIndex;
        if (index == null) {
            LOGGER.info("Old manifest with broken assets. Querying vanilla manifest for assets..");
            if (manifest.assets == null) {
                LOGGER.warn("Version '{}' does not have an assetIndex. Assuming Legacy. (Harvesting from {})", manifest.id, LEGACY_ASSETS_VERSION);
                index = VersionManifest.assetsFor(versionsDir, LEGACY_ASSETS_VERSION);
            } else {
                index = VersionManifest.assetsFor(versionsDir, manifest.assets);
            }
            if (index == null) {
                LOGGER.error("Unable to find assets for '{}'.", manifest.id);
                throw new InstanceLaunchException("Unable to prepare Legacy/Unknown assets for '" + manifest.id + "'.");
            }
        }

        InstallAssetsTask assetsTask = new InstallAssetsTask(index);
        if (!assetsTask.isRedundant()) {
            try {
                assetsTask.execute(token, progressTracker.listenerForStep(true));
            } catch (Throwable ex) {
                throw new IOException("Failed to execute asset update task.", ex);
            }
        }
        return Pair.of(index, assetsTask.getResult());
    }

    private Path buildVirtualAssets(AssetIndex index, AssetIndexManifest assetManifest, Path gameDir, Path assetsDir) throws IOException {
        Path objects = assetsDir.resolve("objects");
        Path virtual = assetsDir.resolve("virtual").resolve(index.getId());
        Path resourcesDir = gameDir.resolve("resources");

        if (assetManifest.virtual || assetManifest.mapToResources) {
            Path vAssets = assetManifest.virtual ? virtual : resourcesDir;
            LOGGER.info("Building virtual assets into {}..", vAssets);
            for (Map.Entry<String, AssetIndexManifest.AssetObject> entry : assetManifest.objects.entrySet()) {
                String name = entry.getKey();
                AssetIndexManifest.AssetObject object = entry.getValue();

                Path virtualPath = vAssets.resolve(name);
                Path objectPath = objects.resolve(object.getPath());
                if (Files.exists(objectPath)) {
                    Files.copy(objectPath, IOUtils.makeParents(virtualPath), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return vAssets;
        }
        return virtual;
    }

    private void validateClient(CancellationToken token, Path versionsDir) throws IOException {
        VersionManifest vanillaManifest = manifests.get(0);
        NewDownloadTask task = vanillaManifest.getClientDownload(versionsDir, getClientId());
        if (task != null) {
            LOGGER.info("Validating client download for {}", vanillaManifest.id);
            task.execute(token, progressTracker.listenerForStep(true));
        }
    }

    private void validateLibraries(CancellationToken token, Path librariesDir, List<VersionManifest.Library> libraries) throws IOException {
        LOGGER.info("Validating minecraft libraries...");
        List<NewDownloadTask> tasks = new LinkedList<>();
        for (VersionManifest.Library library : libraries) {
            NewDownloadTask task = library.createDownloadTask(librariesDir, true);
            if (task != null && !task.isRedundant()) {
                tasks.add(task);
            }
        }

        long totalLen = tasks.stream()
                .mapToLong(e -> {
                    if (e.getValidation().expectedSize == -1) {
                        // Try and HEAD request the content length.
                        return NewDownloadTask.getContentLength(e.getUrl());
                    }
                    return e.getValidation().expectedSize;
                })
                .sum();

        TaskProgressListener rootListener = progressTracker.listenerForStep(true);
        rootListener.start(totalLen);
        TaskProgressAggregator progressAggregator = new TaskProgressAggregator(rootListener);
        if (!tasks.isEmpty()) {
            LOGGER.info("{} dependencies failed to validate or were missing.", tasks.size());
            for (NewDownloadTask task : tasks) {
                token.throwIfCancelled();
                LOGGER.info("Downloading {}", task.getUrl());
                task.execute(token, progressAggregator);
            }
        }
        rootListener.finish(progressAggregator.getProcessed());
        LOGGER.info("Libraries validated!");
    }

    private void extractNatives(Path nativesDir, Path librariesDir, List<VersionManifest.Library> libraries) throws IOException {
        LOGGER.info("Extracting natives...");
        tempDirs.add(nativesDir);
        VersionManifest.OS current = VersionManifest.OS.current();
        for (VersionManifest.Library library : libraries) {
            if (library.natives == null) continue;
            String classifier = library.natives.get(current);
            if (classifier == null) continue;
            MavenNotation notation = library.name.withClassifier(classifier);
            Path nativesJar = notation.toPath(librariesDir);
            if (Files.notExists(nativesJar)) {
                LOGGER.warn("Missing natives jar! " + nativesJar.toAbsolutePath());
                continue;
            }
            LOGGER.info(" Extracting from '{}'.", nativesJar);
            // TODO: move to NIO File system for zip?
            try (ZipFile zipFile = new ZipFile(nativesJar.toFile())) {
                for (ZipEntry entry : iterable(zipFile.entries())) {
                    if (entry.isDirectory()) continue;
                    if (library.extract != null && !library.extract.shouldExtract(entry.getName())) continue;
                    Path dest = nativesDir.resolve(entry.getName());
                    if (Files.notExists(dest.getParent())) {
                        Files.createDirectories(dest.getParent());
                    }
                    try (OutputStream out = Files.newOutputStream(dest)) {
                        IOUtils.copy(zipFile.getInputStream(entry), out);
                    }
                }
            }
        }
    }

    private String getMainClass() {
        return manifests.stream()
                .map(e -> e.mainClass)
                .reduce((a, b) -> b != null ? b : a) // Last on the list, gets priority.
                .orElseThrow(() -> new IllegalStateException("Version manifest chain does not have mainClass attribute??? uwot?"));
    }

    private JavaVersion getJavaVersion() {
        JavaVersion ret = JavaVersion.JAVA_1_8;
        for (VersionManifest e : manifests) {
            JavaVersion parse = e.getJavaVersionOrDefault(null);
            if (parse == null) continue;

            if (parse.ordinal() > ret.ordinal()) {
                ret = parse;
            }
        }
        return ret;
    }

    private List<VersionManifest.Library> collectLibraries(Set<String> features) {
        // Reverse list, as last on manifest list gets put on the classpath first.
        return Lists.reverse(manifests)
                .stream()
                .flatMap(e -> e.getLibraries(features))
                .collect(Collectors.toList());
    }

    private List<Path> collectClasspath(Path librariesDir, Path versionsDir, List<VersionManifest.Library> libraries) {
        List<Path> classpath = libraries.stream()
                .filter(e -> e.natives == null)
                .map(e -> e.name.toPath(librariesDir))
                .collect(Collectors.toList());
        classpath.add(getGameJar(versionsDir));
        return classpath;
    }

    private Path getGameJar(Path versionsDir) {
        String id = getClientId();
        return versionsDir.resolve(id).resolve(id + ".jar");
    }

    private String getClientId() {
        for (VersionManifest manifest : Lists.reverse(manifests)) {
            if (manifest.jar != null) {
                return manifest.jar;
            }
        }
        return manifests.get(manifests.size() - 1).id;
    }

    public static class LaunchContext {

        public final List<String> extraJVMArgs = new ArrayList<>();
        public final List<String> extraProgramArgs = new ArrayList<>();
        public final List<String> shellArgs = new ArrayList<>();
    }

    public enum Phase {
        /**
         * The Instance has not been started.
         */
        NOT_STARTED,
        /**
         * The Instance is initializing.
         * - Setting up instance.
         * - Downloading Minecraft assets.
         */
        INITIALIZING,
        /**
         * The Instance has been started.
         */
        STARTED,
        /**
         * The Instance has been stopped.
         */
        STOPPED,
        /**
         * The instance errored.
         * <p>
         * This could mean it exited with a non-zero exit code.
         * Or the process failed to start.
         */
        ERRORED,
    }

    private static class ProgressTracker {

        private static final boolean DEBUG = Boolean.getBoolean("InstanceLauncher.ProgressTracker.debug");

        private int totalSteps = 0;
        private int currStep = 0;

        private String stepDesc = "";

        private float stepProgress;
        private String humanDesc = null;
        private long lastNonImportant = -1;

        public void reset(int totalSteps) {
            this.totalSteps = totalSteps;
            currStep = 0;
            stepDesc = "";
            stepProgress = 0.0F;
        }

        public void startStep(String stepDesc) {
            currStep++;
            this.stepDesc = stepDesc;
            humanDesc = null;
            sendUpdate(true);
        }

        public void updateDesc(String desc) {
            this.stepDesc = desc;
            sendUpdate(true);
        }

        public TaskProgressListener listenerForStep(boolean isDownload) {
            return new TaskProgressListener() {
                private long total;

                @Override
                public void start(long total) {
                    this.total = total;
                }

                @Override
                public void update(long processed) {
                    stepProgress = (float) ((double) processed / (double) total);
                    if (stepProgress == Float.NEGATIVE_INFINITY || stepProgress == Float.POSITIVE_INFINITY) {
                        // Wat?
                        stepProgress = 0;
                    }
                    if (isDownload) {
                        humanDesc = DataUtils.humanSize(processed) + " / " + DataUtils.humanSize(total);
                    }
                    sendUpdate(false);
                }

                @Override
                public void finish(long total) {
                }
            };
        }

        public void finishStep() {
            stepProgress = 1.0F;
            sendUpdate(true);
        }

        private void sendUpdate(boolean important) {
            if (!important) {
                // Rate limit non-important messages to every 100 millis
                if (lastNonImportant != -1 && System.currentTimeMillis() - 100 < lastNonImportant) {
                    return;
                }
                lastNonImportant = System.currentTimeMillis();
            } else {
                lastNonImportant = -1;
            }
            if (DEBUG) {
                LOGGER.info("Progress [{}/{}] {}: {} {}", currStep, totalSteps, stepProgress, stepDesc, humanDesc);
            }

            if (Settings.webSocketAPI == null) return;
            Settings.webSocketAPI.sendMessage(new LaunchInstanceData.Status(currStep, totalSteps, stepProgress, stepDesc, humanDesc));
        }
    }

    private class LogThread extends Thread {

        private static final boolean DEBUG = Boolean.getBoolean("InstanceLauncher.LogThread.debug");
        /**
         * The time in milliseconds between bursts of logging output.
         */
        private static final long INTERVAL = 250;

        private boolean streamingEnabled = true;

        private boolean stop = false;
        private final List<String> pendingMessages = new ArrayList<>(100);
        @Nullable
        private final PrintStream pw;

        public LogThread(Path path) {
            super("Instance Logging Thread");
            setDaemon(true);

            PrintStream pw = null;
            try {
                pw = new PrintStream(Files.newOutputStream(path), true);
            } catch (IOException ex) {
                LOGGER.error("Failed to create console log for instance {}.", instance.getUuid(), ex);
            }
            this.pw = pw;
        }

        @Override
        @SuppressWarnings ("BusyWait")
        public void run() {
            while (!stop && phase == Phase.STARTED) {
                if (!pendingMessages.isEmpty()) {
                    synchronized (pendingMessages) {
                        List<String> toSend = ImmutableList.copyOf(pendingMessages);
                        pendingMessages.clear();
                        if (DEBUG) {
                            LOGGER.info("Flushing {} messages.", toSend.size());
                        }

                        Settings.webSocketAPI.sendMessage(new LaunchInstanceData.Logs(instance.getUuid(), toSend));
                    }
                }
                try {
                    Thread.sleep(INTERVAL);
                } catch (InterruptedException ignored) {
                    if (Thread.interrupted()) {
                        if (stop) {
                            break;
                        }
                    }
                }
            }
            if (pw != null) {
                pw.close();
            }
        }

        private void bufferMessage(String message) {
            if (streamingEnabled) {
                synchronized (pendingMessages) {
                    pendingMessages.add(message);
                }
            }
            if (pw != null) {
                pw.println(message);
            }
        }

        public void setStreamingEnabled(boolean state) {
            streamingEnabled = state;
            if (!state) {
                synchronized (pendingMessages) {
                    pendingMessages.clear();
                }
            }
        }
    }
}
