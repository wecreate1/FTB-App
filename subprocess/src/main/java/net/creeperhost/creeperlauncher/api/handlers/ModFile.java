package net.creeperhost.creeperlauncher.api.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.JsonAdapter;
import net.covers1624.quack.gson.PathTypeAdapter;
import net.creeperhost.creeperlauncher.Constants;
import net.creeperhost.creeperlauncher.util.DownloadUtils;
import net.creeperhost.creeperlauncher.util.FileUtils;
import net.creeperhost.creeperlauncher.util.WebUtils;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

public class ModFile {
    private final transient String realName;
    private final String name;
    private final String version;
    private final long size;
    private String sha1;
    private boolean expected;
    private boolean exists;
    private boolean enabled;
    @JsonAdapter(PathTypeAdapter.class)
    private Path realPath;

    private long curseProject = -1;
    private long curseFile = -1;

    private final transient int hashCode;

    public ModFile(String name, String version, long size, String sha1) {
        this.realName = name;
        this.name = name.replace(".disabled", "");
        this.version = version;
        this.size = size;
        this.sha1 = sha1;
        this.enabled = !realName.endsWith(".disabled");
        this.hashCode = this.realName.toLowerCase().hashCode();
    }

    public ModFile setExpected(boolean expected)  {
        this.expected = expected;
        return this;
    }

    public ModFile setExists(boolean exists) {
        this.exists = exists;
        return this;
    }

    public ModFile setPath(Path path) {
        realPath = path;
        return this;
    }

    public boolean setEnabled(boolean state) {
        if (realPath == null) {
            return false;
        }
        if (enabled == state) {
            return true;
        }
        Path parent = realPath.getParent();
        String oldName = realPath.getFileName().toString();
        String newFileName = state ? oldName.replace(".disabled", "") : oldName + ".disabled";
        Path newPath = parent.resolve(newFileName);
        try {
            Files.move(realPath, newPath);
        } catch (IOException exception) {
            return false;
        }

        enabled = state;
        realPath = newPath;
        return true;
    }

    public String getRealName() {
        return realName;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o.getClass() == this.getClass() && o.hashCode() == this.hashCode();
    }

    public String getSha1() {
        if (this.sha1.length() > 0) return this.sha1;
        if (Files.exists(this.realPath)) this.sha1 = FileUtils.getHash(this.realPath, "SHA-1");
        return this.sha1;
    }

    public long getCurseProject() {
        if (this.curseProject > 0) return this.curseProject;
        String url = Constants.CREEPERHOST_MODPACK + "/public/mod/lookup/" + this.getSha1();
        LogManager.getLogger().info("Querying: {}", url);
        String resp = WebUtils.getAPIResponse(url);
        JsonElement jElement = new JsonParser().parse(resp);
        if (jElement.isJsonObject()) {
            JsonObject object = jElement.getAsJsonObject();
            if (!object.getAsJsonPrimitive("status").getAsString().equalsIgnoreCase("error")) {
                JsonObject meta = object.getAsJsonObject("meta");
                this.curseProject = meta.get("curseProject").getAsInt();
                this.curseFile = meta.get("curseFile").getAsInt();
            }
        }
        return this.curseProject;
    }

    public void setCurseProject(long curseProject) {
        this.curseProject = curseProject;
    }

    public long getCurseFile() {
        if (this.curseFile > 0) return this.curseFile;
        String url = Constants.CREEPERHOST_MODPACK + "/public/mod/lookup/" + this.getSha1();
        LogManager.getLogger().info("Querying: {}", url);
        String resp = WebUtils.getAPIResponse(url);
        JsonElement jElement = new JsonParser().parse(resp);
        if (jElement.isJsonObject()) {
            JsonObject object = jElement.getAsJsonObject();
            if (!object.getAsJsonPrimitive("status").getAsString().equalsIgnoreCase("error")) {
                JsonObject meta = object.getAsJsonObject("meta");
                this.curseProject = meta.get("curseProject").getAsInt();
                this.curseFile = meta.get("curseFile").getAsInt();
            }
        }
        return this.curseFile;
    }

    public void setCurseFile(long curseFile) {
        this.curseFile = curseFile;
    }

    public long getSize() {
        return size;
    }

    public String getVersion() {
        return version;
    }

    public static boolean isPotentialMod(String name) {
        String replace = name.replace(".disabled", "");
        return replace.endsWith(".jar") || replace.endsWith(".zip");
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ModFile.class.getSimpleName() + "[", "]")
                .add("realName='" + realName + "'")
                .add("name='" + name + "'")
                .add("version='" + version + "'")
                .add("size=" + size)
                .add("sha1='" + sha1 + "'")
                .add("expected=" + expected)
                .add("exists=" + exists)
                .add("enabled=" + enabled)
                .add("realPath=" + realPath)
                .add("hashCode=" + hashCode)
                .toString();
    }
}
