package net.creeperhost.creeperlauncher.install.tasks.modloader.forge;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.gson.JsonUtils;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.util.HashUtils;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.data.forge.installerv1.InstallProfile;
import net.creeperhost.creeperlauncher.install.tasks.TaskProgressListener;
import net.creeperhost.creeperlauncher.minecraft.jsons.VersionManifest;
import net.creeperhost.creeperlauncher.pack.CancellationToken;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Handles the installation of a Forge V1 installer.
 * <p>
 * Unlike V2, this can pretty much just extract the universal jar and install the profile.
 * However, we make a good effort to install all libraries and necessary files during the installation process here.
 * <p>
 * Created by covers1624 on 24/1/22.
 */
public class ForgeV1InstallTask extends AbstractForgeInstallTask {

    private static final Logger LOGGER = LogManager.getLogger();

    private final LocalInstance instance;
    private final Path installerJar;

    public ForgeV1InstallTask(LocalInstance instance, Path installerJar) {
        this.instance = instance;
        this.installerJar = installerJar;
    }

    @Override
    public void execute(@Nullable CancellationToken cancelToken, @Nullable TaskProgressListener listener) throws Throwable {
        Path versionsDir = Constants.BIN_LOCATION.resolve("versions");
        Path librariesDir = Constants.BIN_LOCATION.resolve("libraries");

        try (FileSystem fs = IOUtils.getJarFileSystem(installerJar, true)) {
            Path installerRoot = fs.getPath("/");
            InstallProfile profile = JsonUtils.parse(InstallProfile.GSON, installerRoot.resolve("install_profile.json"), InstallProfile.class);
            if (profile.install.transform != null) throw new IOException("Unable to process Forge v1 Installer with transforms.");

            versionName = profile.install.target;

            if (cancelToken != null) cancelToken.throwIfCancelled();

            VersionManifest vanillaManifest = downloadVanilla(versionsDir, profile.install.minecraft);
            if (profile.versionInfo.inheritsFrom == null || profile.versionInfo.jar == null) {
                Path srcJar = versionsDir.resolve(vanillaManifest.id).resolve(vanillaManifest.id + ".jar");
                Path destJar = IOUtils.makeParents(versionsDir.resolve(versionName).resolve(versionName + ".jar"));
                if (profile.install.stripMeta) {
                    stripMeta(srcJar, destJar);
                } else {
                    Files.copy(
                            srcJar,
                            destJar,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }

            for (InstallProfile.Library library : profile.versionInfo.libraries) {
                if (cancelToken != null) cancelToken.throwIfCancelled();
                if (library.clientreq == null || !library.clientreq) continue; // Skip, mirrors forge logic.
                Path libraryPath = processLibrary(cancelToken, installerRoot, librariesDir, library);

                if (library.checksums.isEmpty()) continue;

                String sha1 = HashUtils.hash(Hashing.sha1(), libraryPath).toString();

                if (!library.checksums.contains(sha1)) {
                    LOGGER.warn("Failed to validate checksums of library {}. Expected one of {}. Got: {}",
                            library.name,
                            library.checksums,
                            sha1
                    );
                    // Some libraries in older v1 installers have changed. (scala iirc), just ignore these errors for now.
                    LOGGER.warn("Continuing anyway..");
                }
            }

            if (cancelToken != null) cancelToken.throwIfCancelled();

            LOGGER.info("Extracting {} from installer jar.", profile.install.path);
            Path universalPath = profile.install.path.toPath(librariesDir);
            Files.copy(installerRoot.resolve(profile.install.filePath), IOUtils.makeParents(universalPath), StandardCopyOption.REPLACE_EXISTING);

            JsonObject profileJson = JsonUtils.parse(InstallProfile.GSON, installerRoot.resolve("install_profile.json"), JsonObject.class);
            LOGGER.info("Writing version profile {}.", versionName);
            Path versionJson = versionsDir.resolve(versionName).resolve(versionName + ".json");
            JsonUtils.write(InstallProfile.GSON, IOUtils.makeParents(versionJson), profileJson.get("versionInfo"));

            ForgeLegacyLibraryHelper.installLegacyLibs(cancelToken, instance, profile.install.minecraft);
        }
    }

    private void stripMeta(Path input, Path output) throws IOException {
        try (ZipFile zIn = new ZipFile(input.toFile());
             ZipOutputStream zOut = new ZipOutputStream(Files.newOutputStream(output))) {
            for (ZipEntry entry : ColUtils.iterable(zIn.entries())) {
                if (entry.getName().startsWith("META-INF")) continue;
                if (entry.isDirectory()) {
                    zOut.putNextEntry(entry);
                } else {
                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    newEntry.setTime(entry.getTime());
                    zOut.putNextEntry(newEntry);
                    IOUtils.copy(zIn.getInputStream(entry), zOut);
                }
            }
        }
    }
}
