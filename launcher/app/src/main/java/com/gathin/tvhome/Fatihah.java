package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;

/** Al-Fatihah, once before each prayer: fatihah.mp3 from the theme folder. */
final class Fatihah {
    private static final String TAG = "TvHome.Fatihah";
    private static MediaPlayer player;

    private Fatihah() {
    }

    static File sound(Context ctx) {
        File dir = Config.dir(ctx);
        if (dir == null) return null;
        File f = new File(dir, "fatihah.mp3");
        return f.isFile() && f.length() > 0 ? f : null;
    }

    /** Plays the recitation unless it was already given for this key. */
    static synchronized void startOnce(Context ctx, String key) {
        File sound = sound(ctx);
        if (sound == null) return;
        SharedPreferences p = ctx.getSharedPreferences("fatihah", Context.MODE_PRIVATE);
        if (key.equals(p.getString("given", ""))) return;
        p.edit().putString("given", key).commit();
        release();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(sound.getPath());
            mp.setOnCompletionListener(Fatihah::end);
            mp.setOnErrorListener((m, what, extra) -> {
                Log.w(TAG, "sound failed: " + what + "/" + extra);
                end(m);
                return true;
            });
            mp.setOnPreparedListener(MediaPlayer::start);
            player = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "cannot play " + sound + ": " + e.getMessage());
            release();
        }
    }

    static synchronized void stop() {
        release();
    }

    private static synchronized void end(MediaPlayer m) {
        if (player == m) {
            release();
        } else {
            m.release();
        }
    }

    private static void release() {
        if (player == null) return;
        try {
            player.stop();
        } catch (IllegalStateException ignored) {
            // not prepared yet
        }
        player.release();
        player = null;
    }
}
