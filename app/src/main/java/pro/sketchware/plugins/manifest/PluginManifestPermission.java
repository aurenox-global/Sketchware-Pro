package pro.sketchware.plugins.manifest;

public enum PluginManifestPermission {

    PROJECT_READ("project.read"),
    PROJECT_WRITE("project.write"),
    EDITOR_READ("editor.read"),
    EDITOR_WRITE("editor.write"),
    FILESYSTEM_READ("filesystem.read"),
    FILESYSTEM_WRITE("filesystem.write"),
    NETWORK("network");

    public final String id;

    PluginManifestPermission(String id) {
        this.id = id;
    }

    public static PluginManifestPermission fromId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (PluginManifestPermission permission : values()) {
            if (permission.id.equals(id)) {
                return permission;
            }
        }
        return null;
    }
}
