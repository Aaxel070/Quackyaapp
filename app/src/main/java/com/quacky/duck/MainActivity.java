package com.quacky.duck;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY  = 1001;
    private static final int REQ_MIC      = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestOverlayPermission();
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                "Quacky necesita permiso para flotar sobre otras apps.\n¡Actívalo y vuelve!",
                Toast.LENGTH_LONG).show();
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            requestMicPermission();
        }
    }

    private void requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            launchDuck();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        launchDuck(); // Lanzamos igual aunque nieguen el mic
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                requestMicPermission();
            } else {
                Toast.makeText(this,
                    "Sin ese permiso Quacky no puede flotar 🦆",
                    Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void launchDuck() {
        Intent service = new Intent(this, DuckOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        Toast.makeText(this, "¡Quacky está vivo! 🐥", Toast.LENGTH_SHORT).show();
        finish(); // Cierra la Activity; el pato flota solo
    }
}
