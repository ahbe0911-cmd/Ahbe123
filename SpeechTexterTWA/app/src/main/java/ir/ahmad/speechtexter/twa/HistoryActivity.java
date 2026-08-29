package ir.ahmad.speechtexter.twa;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HistoryActivity extends AppCompatActivity {
    public static final String EXTRA_CONTENT = "ir.ahmad.speechtexter.twa.CONTENT";
    private static final long SEARCH_DELAY_MS = 250L;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::loadCurrentQuery;
    private TranscriptRepository repository;
    private HistoryAdapter adapter;
    private EditText searchEditor;
    private Button deleteAllButton;
    private boolean destroyed;
    private int queryGeneration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        boolean darkMode = getSharedPreferences("speechtexter_settings", MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        getDelegate().setLocalNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        repository = new TranscriptRepository(this);
        adapter = new HistoryAdapter();
        searchEditor = findViewById(R.id.searchEditor);
        deleteAllButton = findViewById(R.id.deleteAllButton);
        ListView historyList = findViewById(R.id.historyList);
        TextView emptyText = findViewById(R.id.emptyText);
        historyList.setAdapter(adapter);
        historyList.setEmptyView(emptyText);

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        deleteAllButton.setOnClickListener(view -> confirmDeleteAll());
        searchEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                mainHandler.removeCallbacks(searchRunnable);
                mainHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        loadCurrentQuery();
    }

    private void loadCurrentQuery() {
        String query = searchEditor.getText().toString();
        int generation = ++queryGeneration;
        databaseExecutor.execute(() -> {
            List<TranscriptRepository.Entry> result;
            try {
                result = repository.search(query);
            } catch (RuntimeException error) {
                result = Collections.emptyList();
            }
            List<TranscriptRepository.Entry> finalResult = result;
            if (!destroyed) {
                runOnUiThread(() -> {
                    if (generation == queryGeneration) {
                        adapter.replace(finalResult);
                        deleteAllButton.setEnabled(!finalResult.isEmpty());
                    }
                });
            }
        });
    }

    private void useEntry(TranscriptRepository.Entry entry) {
        Intent result = new Intent().putExtra(EXTRA_CONTENT, entry.content);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void copyEntry(TranscriptRepository.Entry entry) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.clipboard_label), entry.content)
        );
        Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(TranscriptRepository.Entry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        databaseExecutor.execute(() -> {
                            repository.delete(entry.id);
                            if (!destroyed) {
                                runOnUiThread(this::loadCurrentQuery);
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_all_title)
                .setMessage(R.string.confirm_delete_all_message)
                .setPositiveButton(R.string.delete_all, (dialog, which) ->
                        databaseExecutor.execute(() -> {
                            repository.deleteAll();
                            if (!destroyed) {
                                runOnUiThread(() -> {
                                    searchEditor.setText("");
                                    loadCurrentQuery();
                                });
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    private final class HistoryAdapter extends BaseAdapter {
        private List<TranscriptRepository.Entry> items = Collections.emptyList();

        void replace(List<TranscriptRepository.Entry> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public TranscriptRepository.Entry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id;
        }

        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            View row = convertView;
            ViewHolder holder;
            if (row == null) {
                row = LayoutInflater.from(HistoryActivity.this)
                        .inflate(R.layout.item_history, parent, false);
                holder = new ViewHolder(row);
                row.setTag(holder);
            } else {
                holder = (ViewHolder) row.getTag();
            }

            TranscriptRepository.Entry entry = getItem(position);
            holder.content.setText(entry.content);
            holder.date.setText(getString(
                    R.string.saved_at_format,
                    PersianDateFormatter.format(entry.updatedAt)
            ));
            holder.use.setOnClickListener(view -> useEntry(entry));
            holder.copy.setOnClickListener(view -> copyEntry(entry));
            holder.delete.setOnClickListener(view -> confirmDelete(entry));
            row.setOnClickListener(view -> useEntry(entry));
            return row;
        }
    }

    private static final class ViewHolder {
        private final TextView content;
        private final TextView date;
        private final Button use;
        private final Button copy;
        private final Button delete;

        private ViewHolder(View row) {
            content = row.findViewById(R.id.historyContent);
            date = row.findViewById(R.id.historyDate);
            use = row.findViewById(R.id.useButton);
            copy = row.findViewById(R.id.copyHistoryButton);
            delete = row.findViewById(R.id.deleteHistoryButton);
        }
    }
}
