package ir.ahmad.speechtexter.twa;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements RecognitionListener {
    private static final String PREFS_NAME = "speechtexter_settings";
    private static final String PREF_DRAFT = "draft";
    private static final String PREF_LANGUAGE = "language";
    private static final String PREF_AUTO_COPY = "auto_copy";
    private static final String PREF_CONTINUOUS = "continuous";
    private static final String PREF_PUNCTUATION = "punctuation";
    private static final String PREF_DARK_MODE = "dark_mode";
    private static final String PREF_MIC_REQUESTED = "microphone_requested";
    private static final String STATE_EXPORT_TEXT = "export_text";
    private static final long DRAFT_SAVE_DELAY_MS = 500L;
    private static final long DUPLICATE_WINDOW_MS = 4_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Runnable restartRunnable = this::beginListening;
    private final Runnable draftSaveRunnable = this::saveDraftNow;

    private SharedPreferences preferences;
    private TranscriptRepository repository;
    private EditText transcriptEditor;
    private TextView statusText;
    private TextView partialText;
    private TextView textStats;
    private Button microphoneButton;
    private Spinner languageSpinner;
    private SwitchCompat autoCopySwitch;
    private SwitchCompat continuousSwitch;
    private SwitchCompat punctuationSwitch;
    private SwitchCompat darkModeSwitch;
    private SpeechRecognizer speechRecognizer;
    private ActivityResultLauncher<String> microphonePermissionLauncher;
    private ActivityResultLauncher<String> txtExportLauncher;
    private ActivityResultLauncher<String> pdfExportLauncher;
    private ActivityResultLauncher<Intent> historyLauncher;
    private boolean sessionRequested;
    private boolean recognizerActive;
    private boolean destroyed;
    private int emptyCycles;
    private String lastFinalResult = "";
    private long lastFinalResultAt;
    private String pendingExportText = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkMode = preferences.getBoolean(PREF_DARK_MODE, false);
        getDelegate().setLocalNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        super.onCreate(savedInstanceState);

        registerResultLaunchers();
        setContentView(R.layout.activity_main);
        repository = new TranscriptRepository(this);
        bindViews();
        restoreSettings(savedInstanceState);
        configureControls();
        updateStats(transcriptEditor.getText());
    }

    private void registerResultLaunchers() {
        microphonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    preferences.edit().putBoolean(PREF_MIC_REQUESTED, true).apply();
                    if (granted) {
                        startSession();
                    } else {
                        showToast(R.string.permission_denied);
                    }
                }
        );
        txtExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/plain"),
                uri -> {
                    if (uri != null) {
                        exportTxt(uri, exportTextSnapshot());
                    }
                }
        );
        pdfExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                uri -> {
                    if (uri != null) {
                        exportPdf(uri, exportTextSnapshot());
                    }
                }
        );
        historyLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        return;
                    }
                    String content = result.getData().getStringExtra(HistoryActivity.EXTRA_CONTENT);
                    if (content != null) {
                        transcriptEditor.setText(content);
                        transcriptEditor.setSelection(content.length());
                    }
                }
        );
    }

    private void bindViews() {
        transcriptEditor = findViewById(R.id.transcriptEditor);
        statusText = findViewById(R.id.statusText);
        partialText = findViewById(R.id.partialText);
        textStats = findViewById(R.id.textStats);
        microphoneButton = findViewById(R.id.microphoneButton);
        languageSpinner = findViewById(R.id.languageSpinner);
        autoCopySwitch = findViewById(R.id.autoCopySwitch);
        continuousSwitch = findViewById(R.id.continuousSwitch);
        punctuationSwitch = findViewById(R.id.punctuationSwitch);
        darkModeSwitch = findViewById(R.id.darkModeSwitch);
    }

    private void restoreSettings(@Nullable Bundle savedInstanceState) {
        String draft = preferences.getString(PREF_DRAFT, "");
        transcriptEditor.setText(draft);
        transcriptEditor.setSelection(draft == null ? 0 : draft.length());
        autoCopySwitch.setChecked(preferences.getBoolean(PREF_AUTO_COPY, true));
        continuousSwitch.setChecked(preferences.getBoolean(PREF_CONTINUOUS, true));
        punctuationSwitch.setChecked(preferences.getBoolean(PREF_PUNCTUATION, true));
        darkModeSwitch.setChecked(preferences.getBoolean(PREF_DARK_MODE, false));

        String selectedLanguage = preferences.getString(PREF_LANGUAGE, "fa-IR");
        String[] languageCodes = getResources().getStringArray(R.array.speech_language_codes);
        int selectedIndex = 0;
        for (int index = 0; index < languageCodes.length; index++) {
            if (languageCodes[index].equals(selectedLanguage)) {
                selectedIndex = index;
                break;
            }
        }
        languageSpinner.setSelection(selectedIndex, false);
        if (savedInstanceState != null) {
            pendingExportText = savedInstanceState.getString(STATE_EXPORT_TEXT, "");
        }
        if (draft != null && !draft.isEmpty()) {
            statusText.setText(R.string.draft_restored);
        }
    }

    private void configureControls() {
        transcriptEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                updateStats(value);
                mainHandler.removeCallbacks(draftSaveRunnable);
                mainHandler.postDelayed(draftSaveRunnable, DRAFT_SAVE_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });

        microphoneButton.setOnClickListener(view -> {
            if (sessionRequested) {
                stopSession(true);
            } else {
                requestMicrophoneAndStart();
            }
        });
        findViewById(R.id.openSiteButton).setOnClickListener(view -> openOriginalSite());
        findViewById(R.id.copyButton).setOnClickListener(view -> copyCurrentText(true));
        findViewById(R.id.saveButton).setOnClickListener(view -> saveCurrentText());
        findViewById(R.id.shareButton).setOnClickListener(view -> shareCurrentText());
        findViewById(R.id.historyButton).setOnClickListener(view ->
                historyLauncher.launch(new Intent(this, HistoryActivity.class))
        );
        findViewById(R.id.exportTxtButton).setOnClickListener(view -> requestTxtExport());
        findViewById(R.id.exportPdfButton).setOnClickListener(view -> requestPdfExport());
        findViewById(R.id.clearButton).setOnClickListener(view -> confirmClear());

        autoCopySwitch.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PREF_AUTO_COPY, checked).apply()
        );
        continuousSwitch.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PREF_CONTINUOUS, checked).apply()
        );
        punctuationSwitch.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PREF_PUNCTUATION, checked).apply()
        );
        darkModeSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PREF_DARK_MODE, checked).apply();
            getDelegate().setLocalNightMode(
                    checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                preferences.edit().putString(PREF_LANGUAGE, selectedLanguageCode()).apply();
                if (sessionRequested) {
                    restartRecognition(300L);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void requestMicrophoneAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startSession();
            return;
        }

        boolean requestedBefore = preferences.getBoolean(PREF_MIC_REQUESTED, false);
        if (requestedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.microphone_permission_title)
                    .setMessage(R.string.permission_denied_permanently)
                    .setPositiveButton(R.string.open_settings, (dialog, which) -> openAppSettings())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.microphone_permission_title)
                .setMessage(R.string.microphone_permission_message)
                .setPositiveButton(R.string.allow_microphone, (dialog, which) ->
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startSession() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.recognizer_unavailable_title)
                    .setMessage(R.string.recognizer_unavailable_message)
                    .setPositiveButton(R.string.open_original_site, (dialog, which) -> openOriginalSite())
                    .setNegativeButton(R.string.close, null)
                    .show();
            return;
        }
        sessionRequested = true;
        emptyCycles = 0;
        setListeningUi(true);
        beginListening();
    }

    private void beginListening() {
        if (!sessionRequested || recognizerActive || destroyed) {
            return;
        }
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(this);
            }
            Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageCode())
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguageCode())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizerActive = true;
            speechRecognizer.startListening(recognizerIntent);
            statusText.setText(R.string.speech_status_listening);
            partialText.setText(R.string.partial_result_hint);
        } catch (RuntimeException error) {
            recognizerActive = false;
            stopSession(false);
            showToast(R.string.speech_generic_error);
        }
    }

    private void restartRecognition(long delayMillis) {
        mainHandler.removeCallbacks(restartRunnable);
        if (speechRecognizer != null && recognizerActive) {
            try {
                speechRecognizer.cancel();
            } catch (RuntimeException ignored) {
                // The recognizer process may already have ended; the delayed restart is still safe.
            }
        }
        recognizerActive = false;
        if (sessionRequested && !destroyed) {
            mainHandler.postDelayed(restartRunnable, delayMillis);
        }
    }

    private void stopSession(boolean userInitiated) {
        sessionRequested = false;
        mainHandler.removeCallbacks(restartRunnable);
        if (speechRecognizer != null && recognizerActive) {
            try {
                speechRecognizer.cancel();
            } catch (RuntimeException ignored) {
                // A remote recognition service can disappear while the activity is stopping.
            }
        }
        recognizerActive = false;
        setListeningUi(false);
        if (userInitiated) {
            statusText.setText(R.string.speech_status_paused);
        }
        partialText.setText(R.string.partial_result_hint);
    }

    private void setListeningUi(boolean listening) {
        microphoneButton.setText(listening ? R.string.stop_listening : R.string.start_listening);
        languageSpinner.setEnabled(!listening);
    }

    @Override
    public void onReadyForSpeech(Bundle parameters) {
        statusText.setText(R.string.speech_status_listening);
    }

    @Override
    public void onBeginningOfSpeech() {
        emptyCycles = 0;
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        statusText.setText(R.string.speech_status_processing);
    }

    @Override
    public void onError(int error) {
        recognizerActive = false;
        if (!sessionRequested) {
            return;
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            emptyCycles++;
            if (continuousSwitch.isChecked() && emptyCycles <= 3) {
                restartRecognition(550L);
            } else {
                stopSession(false);
                statusText.setText(R.string.speech_no_input);
            }
            return;
        }
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            if (emptyCycles++ < 2) {
                restartRecognition(1_000L);
            } else {
                stopSession(false);
                showToast(R.string.speech_busy_error);
            }
            return;
        }
        stopSession(false);
        if (error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER) {
            showToast(R.string.network_speech_error);
        } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            showToast(R.string.permission_denied);
        } else {
            showToast(R.string.speech_generic_error);
        }
    }

    @Override
    public void onResults(Bundle results) {
        recognizerActive = false;
        ArrayList<String> candidates = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String result = candidates == null || candidates.isEmpty() ? "" : candidates.get(0).trim();
        if (result.isEmpty()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH);
            return;
        }

        emptyCycles = 0;
        if (punctuationSwitch.isChecked()) {
            result = PunctuationNormalizer.normalize(result);
        }
        long now = System.currentTimeMillis();
        boolean duplicate = result.equals(lastFinalResult)
                && now - lastFinalResultAt < DUPLICATE_WINDOW_MS;
        if (!duplicate) {
            appendRecognizedText(result);
            lastFinalResult = result;
            lastFinalResultAt = now;
            if (autoCopySwitch.isChecked()) {
                copyCurrentText(false);
                statusText.setText(R.string.speech_status_copied);
            } else {
                statusText.setText(R.string.speech_result_added);
            }
        }
        partialText.setText(R.string.partial_result_hint);
        if (sessionRequested && continuousSwitch.isChecked()) {
            restartRecognition(350L);
        } else {
            stopSession(false);
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> candidates = partialResults.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
        );
        if (candidates != null && !candidates.isEmpty()) {
            partialText.setText(candidates.get(0));
        }
    }

    @Override
    public void onEvent(int eventType, Bundle parameters) {
    }

    private void appendRecognizedText(String value) {
        Editable editable = transcriptEditor.getText();
        if (editable.length() > 0) {
            char lastCharacter = editable.charAt(editable.length() - 1);
            if (!Character.isWhitespace(lastCharacter)) {
                editable.append(' ');
            }
        }
        editable.append(value);
        transcriptEditor.setSelection(editable.length());
    }

    private void saveCurrentText() {
        String content = currentTextOrNotify();
        if (content == null) {
            return;
        }
        String language = selectedLanguageCode();
        ioExecutor.execute(() -> {
            long id = repository.save(content, language);
            if (!destroyed) {
                runOnUiThread(() -> showToast(
                        id >= 0 ? R.string.text_saved : R.string.export_failed
                ));
            }
        });
    }

    private void copyCurrentText(boolean showConfirmation) {
        String content = transcriptEditor.getText().toString().trim();
        if (content.isEmpty()) {
            if (showConfirmation) {
                showToast(R.string.empty_text);
            }
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipboard_label), content));
        if (showConfirmation) {
            showToast(R.string.text_copied);
        }
    }

    private void shareCurrentText() {
        String content = currentTextOrNotify();
        if (content == null) {
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, content);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser)));
    }

    private void requestTxtExport() {
        String content = currentTextOrNotify();
        if (content == null) {
            return;
        }
        pendingExportText = content;
        txtExportLauncher.launch(getString(R.string.file_name_txt, fileTimestamp()));
    }

    private void requestPdfExport() {
        String content = currentTextOrNotify();
        if (content == null) {
            return;
        }
        pendingExportText = content;
        pdfExportLauncher.launch(getString(R.string.file_name_pdf, fileTimestamp()));
    }

    private void exportTxt(Uri uri, String content) {
        ioExecutor.execute(() -> {
            boolean success = false;
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IOException("The document provider returned no output stream");
                }
                output.write(content.getBytes(StandardCharsets.UTF_8));
                output.flush();
                success = true;
            } catch (IOException | RuntimeException ignored) {
                // The user receives a concise error; no partial success is reported.
            }
            showExportResult(success);
        });
    }

    private void exportPdf(Uri uri, String content) {
        ioExecutor.execute(() -> {
            boolean success = false;
            PdfDocument document = new PdfDocument();
            try {
                writePdfPages(document, content);
                try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) {
                        throw new IOException("The document provider returned no output stream");
                    }
                    document.writeTo(output);
                    output.flush();
                    success = true;
                }
            } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
                // Export failure is handled on the main thread below.
            } finally {
                document.close();
            }
            showExportResult(success);
        });
    }

    private void writePdfPages(PdfDocument document, String content) {
        final int pageWidth = 595;
        final int pageHeight = 842;
        final int margin = 48;
        final int bodyTop = 88;
        final int bodyBottom = 48;
        final int bodyWidth = pageWidth - margin * 2;
        final int bodyHeight = pageHeight - bodyTop - bodyBottom;

        Typeface typeface = ResourcesCompat.getFont(this, R.font.vazirmatn_regular);
        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.BLACK);
        bodyPaint.setTextSize(15f);
        if (typeface != null) {
            bodyPaint.setTypeface(typeface);
        }
        StaticLayout bodyLayout = StaticLayout.Builder
                .obtain(content, 0, content.length(), bodyPaint, bodyWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(2f, 1.18f)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .build();

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.rgb(0, 109, 119));
        titlePaint.setTextSize(18f);
        titlePaint.setTextAlign(Paint.Align.RIGHT);
        titlePaint.setTypeface(typeface == null ? Typeface.DEFAULT_BOLD : typeface);

        int firstLine = 0;
        int pageNumber = 1;
        while (firstLine < bodyLayout.getLineCount()) {
            int firstLineTop = bodyLayout.getLineTop(firstLine);
            int lastLine = bodyLayout.getLineForVertical(firstLineTop + bodyHeight);
            if (lastLine <= firstLine) {
                lastLine = firstLine + 1;
            } else if (bodyLayout.getLineBottom(lastLine) > firstLineTop + bodyHeight) {
                lastLine--;
            }
            lastLine = Math.min(lastLine, bodyLayout.getLineCount() - 1);

            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    pageNumber
            ).create();
            PdfDocument.Page page = document.startPage(info);
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            canvas.drawText(getString(R.string.pdf_title), pageWidth - margin, 50f, titlePaint);
            canvas.save();
            canvas.clipRect(margin, bodyTop, pageWidth - margin, pageHeight - bodyBottom);
            canvas.translate(margin, bodyTop - firstLineTop);
            bodyLayout.draw(canvas);
            canvas.restore();
            document.finishPage(page);

            firstLine = lastLine + 1;
            pageNumber++;
        }
    }

    private void showExportResult(boolean success) {
        if (!destroyed) {
            runOnUiThread(() -> showToast(
                    success ? R.string.export_saved : R.string.export_failed
            ));
        }
    }

    private void confirmClear() {
        if (transcriptEditor.getText().toString().trim().isEmpty()) {
            showToast(R.string.empty_text);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_clear_title)
                .setMessage(R.string.confirm_clear_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    transcriptEditor.setText("");
                    preferences.edit().remove(PREF_DRAFT).apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openOriginalSite() {
        Intent intent = new Intent(this, SafeLauncherActivity.class)
                .setData(Uri.parse(getString(R.string.launch_url)));
        startActivity(intent);
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }

    @Nullable
    private String currentTextOrNotify() {
        String content = transcriptEditor.getText().toString().trim();
        if (content.isEmpty()) {
            showToast(R.string.empty_text);
            return null;
        }
        return content;
    }

    private String exportTextSnapshot() {
        if (!pendingExportText.isEmpty()) {
            String result = pendingExportText;
            pendingExportText = "";
            return result;
        }
        return transcriptEditor.getText().toString();
    }

    private String selectedLanguageCode() {
        String[] codes = getResources().getStringArray(R.array.speech_language_codes);
        int position = languageSpinner == null ? 0 : languageSpinner.getSelectedItemPosition();
        if (position < 0 || position >= codes.length) {
            position = 0;
        }
        return codes[position];
    }

    private void updateStats(CharSequence value) {
        String text = value == null ? "" : value.toString();
        String words = PersianDateFormatter.toPersianDigits(
                Integer.toString(TextStats.countWords(text))
        );
        String characters = PersianDateFormatter.toPersianDigits(
                Integer.toString(TextStats.countCharacters(text))
        );
        textStats.setText(getString(R.string.word_count_format, words, characters));
    }

    private void saveDraftNow() {
        if (transcriptEditor != null) {
            preferences.edit().putString(PREF_DRAFT, transcriptEditor.getText().toString()).apply();
        }
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    private void showToast(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_EXPORT_TEXT, pendingExportText);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        saveDraftNow();
        stopSession(false);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (RuntimeException ignored) {
                // No work remains for a recognition service that already exited.
            }
            speechRecognizer = null;
        }
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
