package cn.crhistory.player;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.file.Files;
import org.json.JSONObject;

/** Local, digest-checked preparation used by the example host. */
public final class LocalResources {
    private static boolean checked;
    private LocalResources() {}
    private static File archive(Context c) { return new File(c.getFilesDir(), "resources.pack"); }
    private static JSONObject registration(Context c) throws Exception {
        try (InputStream in = c.getAssets().open("component/resource-registration.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) { if (out.size()+n > 16384) throw new IOException("Registration too large"); out.write(b,0,n); }
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    public static File runtimeDirectory(Context c) { return new File(c.getFilesDir(), "resource-runtime"); }
    public static synchronized JSONObject ready(Context c) throws Exception {
        // EngineContext redirects files; use the package's stable private data directory.
        Context base = c.createPackageContext(c.getPackageName(), 0);
        JSONObject r = registration(base); File pack = archive(base);
        if (!checked) {
            if (Files.isSymbolicLink(pack.toPath())) throw new IOException("Resource symlink rejected");
            JSONObject inventory = ReplayResourcePack.validate(pack, r.getString("sha256"), r.getLong("bytes"));
            if (!r.getString("engine").equals(inventory.getString("resourceSetId"))) throw new IOException("Wrong resource identity");
            File ready = new File(runtimeDirectory(base), "prepared.json");
            try (InputStream in = new AtomicFile(ready).openRead()) {
                byte[] marker = new byte[16385]; int length = 0, n;
                while (length < marker.length && (n = in.read(marker, length, marker.length-length)) != -1) length += n;
                if (length > 16384 || !r.getString("sha256").equals(new JSONObject(new String(marker, 0, length, java.nio.charset.StandardCharsets.UTF_8)).getString("sha256"))) throw new IOException("Resources not prepared");
            }
            checked = true;
        }
        return r;
    }
    public static File readyArchive(Context c) throws Exception {
        ready(c); return archive(c.createPackageContext(c.getPackageName(), 0));
    }
    public static synchronized void prepare(Context c) throws Exception {
        File pack = archive(c); JSONObject r = registration(c);
        if (!pack.isFile()) {
            File source = new File(c.getExternalFilesDir(null), "resources.pack");
            ReplayResourcePack.validate(source, r.getString("sha256"), r.getLong("bytes"));
            File temp = new File(c.getFilesDir(), "resources.pack.tmp");
            try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] b = new byte[1024*1024]; int n; while ((n=in.read(b))!=-1) out.write(b,0,n); out.getFD().sync();
            }
            ReplayResourcePack.validate(temp, r.getString("sha256"), r.getLong("bytes"));
            if (!temp.renameTo(pack)) throw new IOException("Resource rename failed");
        }
        ReplayResourcePack.validate(pack, r.getString("sha256"), r.getLong("bytes"));
        File runtime = runtimeDirectory(c); if (!runtime.isDirectory() && !runtime.mkdirs()) throw new IOException("Resource directory unavailable");
        File marker = new File(runtime, "prepared.json");
        if (!marker.exists() && !new File(marker+".bak").exists()) {
            ReplayResourcePack.extractUpdate(pack, new File(runtime, "update"), r.getLong("runtimeBytes"));
            AtomicFile atomic = new AtomicFile(marker); FileOutputStream out = null;
            try { out = atomic.startWrite(); out.write(r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); atomic.finishWrite(out); }
            catch (Exception e) { if (out != null) atomic.failWrite(out); throw e; }
        }
        checked = false; ready(c);
    }
}
