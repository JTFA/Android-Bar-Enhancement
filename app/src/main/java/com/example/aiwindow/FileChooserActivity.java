package com.example.aiwindow;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ValueCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent helper Activity for WebView file chooser.
 * Service cannot call startActivityForResult, so this Activity acts as a proxy.
 */
public class FileChooserActivity extends Activity {

    private static final String TAG = "FileChooserActivity";
    private static final int FILE_CHOOSER_REQUEST_CODE = 10001;

    /** Static callback set by the Service before launching this Activity */
    public static ValueCallback<Uri[]> uploadCallback;

    private boolean waitingForResult = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Minimal invisible layout to keep Activity alive
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new android.widget.FrameLayout.LayoutParams(0, 0));
        setContentView(v);

        // If recreated by system, just finish (can't recover the flow)
        if (savedInstanceState != null) {
            Log.d(TAG, "Activity recreated by system, finishing");
            finish();
            return;
        }

        Intent launchIntent = getIntent();
        if (launchIntent == null) {
            Log.d(TAG, "No launch intent, finishing");
            finish();
            return;
        }

        String[] acceptTypes = launchIntent.getStringArrayExtra("accept_types");
        boolean allowMultiple = launchIntent.getBooleanExtra("allow_multiple", false);

        Log.d(TAG, "onCreate: acceptTypes=" + java.util.Arrays.toString(acceptTypes)
                + ", allowMultiple=" + allowMultiple);

        // Build file picker intent
        Intent fileIntent = buildFileIntent(acceptTypes, allowMultiple);

        try {
            waitingForResult = true;
            startActivityForResult(fileIntent, FILE_CHOOSER_REQUEST_CODE);
            Log.d(TAG, "File picker launched successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch file picker", e);
            waitingForResult = false;
            invokeCallback(null);
            finish();
        }
    }

    private Intent buildFileIntent(String[] acceptTypes, boolean allowMultiple) {
        // ACTION_GET_CONTENT is the most widely supported
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        // Set MIME type filter
        if (acceptTypes != null && acceptTypes.length > 0) {
            String mimeType = resolveMimeType(acceptTypes);
            if (mimeType != null) {
                intent.setType(mimeType);
            }
        }

        // Allow multiple selection
        if (allowMultiple) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        // Grant read permission for returned URIs
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return intent;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult: requestCode=" + requestCode
                + ", resultCode=" + resultCode
                + ", data=" + (data != null ? "yes" : "null"));

        waitingForResult = false;

        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            Log.w(TAG, "Unexpected request code: " + requestCode);
            invokeCallback(null);
            finish();
            return;
        }

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            results = extractUris(data);
        }

        Log.d(TAG, "Result: " + (results != null ? results.length + " file(s)" : "cancelled/null"));
        invokeCallback(results);
        finish();
    }

    private Uri[] extractUris(Intent data) {
        List<Uri> uriList = new ArrayList<>();

        // Try ClipData first (multi-file or some single-file pickers)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ClipData clipData = data.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null) {
                        // Grant persistent read permission
                        try {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception e) {
                            Log.w(TAG, "Cannot take persistable URI permission for " + uri, e);
                        }
                        uriList.add(uri);
                    }
                }
            }
        }

        // If no ClipData, try getData() (single file)
        if (uriList.isEmpty()) {
            Uri dataUri = data.getData();
            if (dataUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(dataUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot take persistable URI permission for " + dataUri, e);
                }
                uriList.add(dataUri);
            }
        }

        return uriList.isEmpty() ? null : uriList.toArray(new Uri[0]);
    }

    /**
     * Safely invoke the callback and clear it.
     */
    private void invokeCallback(Uri[] results) {
        ValueCallback<Uri[]> cb = uploadCallback;
        uploadCallback = null;
        if (cb != null) {
            try {
                cb.onReceiveValue(results);
                Log.d(TAG, "Callback invoked successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error invoking callback", e);
            }
        } else {
            Log.w(TAG, "Callback was null (already consumed or lost)");
        }
    }

    private String resolveMimeType(String[] acceptTypes) {
        if (acceptTypes == null || acceptTypes.length == 0) return null;

        boolean hasImage = false, hasVideo = false, hasAudio = false;
        for (String type : acceptTypes) {
            if (type == null || type.isEmpty()) continue;
            String lower = type.toLowerCase();
            if (lower.startsWith("image/") || lower.equals(".jpg")
                    || lower.equals(".jpeg") || lower.equals(".png")
                    || lower.equals(".gif") || lower.equals(".webp"))
                hasImage = true;
            else if (lower.startsWith("video/") || lower.equals(".mp4")
                    || lower.equals(".avi") || lower.equals(".mov"))
                hasVideo = true;
            else if (lower.startsWith("audio/") || lower.equals(".mp3")
                    || lower.equals(".wav"))
                hasAudio = true;
        }

        if (hasImage && !hasVideo && !hasAudio) return "image/*";
        if (hasVideo && !hasImage && !hasAudio) return "video/*";
        if (hasAudio && !hasImage && !hasVideo) return "audio/*";
        return "*/*";
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("waitingForResult", waitingForResult);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: waitingForResult=" + waitingForResult
                + ", callback=" + (uploadCallback != null ? "set" : "null"));
        super.onDestroy();

        // Only cancel if we were NOT waiting (Activity destroyed before file picker launched)
        if (!waitingForResult && uploadCallback != null) {
            Log.d(TAG, "onDestroy: cancelling callback");
            invokeCallback(null);
        }
    }
}
