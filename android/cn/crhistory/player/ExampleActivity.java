package cn.crhistory.player;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import org.json.JSONObject;

/** Minimal local host. Inputs are staged through adb into this app's external files. */
public final class ExampleActivity extends Activity {
    private TextView status;
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32,64,32,32);
        status = new TextView(this); status.setText("Stage resources.pack and replay.json in the app's external files, then play.");
        Button play = new Button(this); play.setText("Play local replay"); layout.addView(status); layout.addView(play); setContentView(layout);
        play.setOnClickListener(v -> {
            play.setEnabled(false); status.setText("Checking local materials…");
            new Thread(() -> {
                boolean prepared = false;
                try {
                    ReplayRun.reclaim(this); LocalResources.prepare(this);
                    File source = new File(getExternalFilesDir(null), "replay.json");
                    if (!source.isFile() || source.length() > 16*1024*1024) throw new IOException("Missing replay or input exceeds 16 MiB");
                    byte[] input = Files.readAllBytes(source.toPath());
                    JSONObject replay = new JSONObject(new String(input, java.nio.charset.StandardCharsets.UTF_8)); ReplayBootstrap.validate(replay);
                    ReplayRun.prepare(this, "local", "0:0");
                    prepared = true;
                    Files.write(new File(ReplayRun.current(this), "files/source.json").toPath(), input);
                    runOnUiThread(() -> { try { ReplayRun.launch(this, new Intent(this,ReplayEntry.class)); status.setText("Replay launched"); } catch (Exception e) { failed(e); } finally { play.setEnabled(true); } });
                } catch (Exception e) { if (prepared) try { ReplayRun.abandon(this); } catch (Exception cleanup) { e.addSuppressed(cleanup); } runOnUiThread(() -> { failed(e); play.setEnabled(true); }); }
            }, "local-prepare").start();
        });
    }
    private void failed(Exception e) { status.setText(e.getClass().getSimpleName()+": "+e.getMessage()); android.util.Log.e("CRReplay", "Local preparation failed", e); }
}
