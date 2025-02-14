package net.creeperhost.creeperlauncher.minecraft.modloader.forge;

import com.google.gson.JsonObject;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.minecraft.McUtils;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import net.creeperhost.creeperlauncher.util.DownloadUtils;
import net.creeperhost.creeperlauncher.util.ForgeUtils;
import net.creeperhost.creeperlauncher.util.GsonUtils;
import net.creeperhost.creeperlauncher.util.LoaderTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ForgeInstallerModLoader extends ForgeModLoader
{
    private static final Logger LOGGER = LogManager.getLogger();

	public ForgeInstallerModLoader(List<LoaderTarget> loaderTargets)
	{
		super(loaderTargets);
	}

	@Override
	public String getName()
	{
		return "forge";
	}

	@Override
	public Path install(LocalInstance instance)
	{
		instance.modLoader = getMinecraftVersion() + "-forge-" + getForgeVersion();

		String forgeUrl = Constants.FORGE_CH + getMinecraftVersion() + "-" + getForgeVersion() + "/forge-" + getMinecraftVersion() + "-" + getForgeVersion() + "-installer.jar";
		String forgeUrlJson = Constants.FORGE_CH + getMinecraftVersion() + "-" + getForgeVersion() + "/forge-" + getMinecraftVersion() + "-" + getForgeVersion() + "-installer.json";

        LOGGER.info("Attempting to download {}.", forgeUrl);
		Path installerFile = instance.getDir().resolve("installer.jar");
        Path installerJson = instance.getDir().resolve("installer.json");

		DownloadUtils.downloadFile(installerFile, forgeUrl);
		DownloadUtils.downloadFile(installerJson, forgeUrlJson);

		if(Files.notExists(installerJson))
		{
			//If we do not have the file lets extract it from the installer jar
			ForgeUtils.extractJson(installerFile, "installer.json");
		}
        try {
            // TODO Dumb hack for Forge, it refuses to install if the profiles.json does not exist.
            //      This entire system is pending a rewrite so.. /shrug
            Path profilesJson = Constants.BIN_LOCATION.resolve("launcher_profiles.json");
            if (Files.notExists(profilesJson)) {
                JsonObject dummy = new JsonObject();
                dummy.add("profiles", new JsonObject());
                GsonUtils.saveJson(profilesJson, dummy);
            }

		    ForgeUtils.runForgeInstaller(installerFile.toAbsolutePath());

			Files.delete(installerFile);
            Files.delete(profilesJson);
		} catch (IOException e) {
            LOGGER.error("Failed to run forge installer.", e);
        }
		return installerJson;
	}

	@Override
	public boolean isApplicable() {
		int minorMcVersion = McUtils.parseMinorVersion(getTargetVersion("minecraft").orElse("0.0.0"));
		//1.13 onwards

		if (minorMcVersion == 12) {
			String[] versions = getForgeVersion().split("\\.");
			try {
				int forgeVer = Integer.parseInt(versions[versions.length - 1]);
				return super.isApplicable() && forgeVer >= 2851;
			} catch (NumberFormatException ignored) {
				// ¯\_(ツ)_/¯
			}
		}
		return super.isApplicable() && minorMcVersion >= 13;
	}
}
