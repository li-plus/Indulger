package com.inftyloop.indulger.fragment;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.inftyloop.indulger.R;
import com.inftyloop.indulger.api.Definition;
import com.inftyloop.indulger.ui.AutoWrapLayout;
import com.inftyloop.indulger.util.ConfigManager;
import com.inftyloop.indulger.util.DisplayHelper;
import com.qmuiteam.qmui.arch.QMUIFragment;
import com.qmuiteam.qmui.widget.QMUITopBarLayout;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.qmuiteam.qmui.widget.dialog.QMUITipDialog;

import butterknife.BindView;
import butterknife.ButterKnife;

public class HomeSearchFragment extends QMUIFragment {
    private static final String TAG = HomeSearchFragment.class.getSimpleName();
    @BindView(R.id.topbar)
    QMUITopBarLayout mTopBar;
    @BindView(R.id.img_delete)
    ImageView mDelHistory;
    @BindView(R.id.search_history)
    AutoWrapLayout mSearchHistory;
    @BindView(R.id.search_history_bar)
    LinearLayout mSearchHistoryBar;
    /* Top Bar stuffs */
    ImageButton mTopImgButton;
    ImageView mClearEditText;
    EditText mSearch;
    TextView mSearchButton;
    Gson mGson = new Gson();

    public static String keyword = "";

    @Override
    public View onCreateView() {
        View root = LayoutInflater.from(getActivity()).inflate(R.layout.home_search, null);
        ButterKnife.bind(this, root);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(25, 25, 25, 25);
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.searchbar_input_home, null);
        mTopImgButton = view.findViewById(R.id.img_search);
        mSearch = view.findViewById(R.id.edit_search);
        mClearEditText = view.findViewById(R.id.icon_clear);
        view.post(() -> {
            Rect area = new Rect();
            mTopImgButton.getHitRect(area);
            final int offset = 30;
            area.right += offset;
            area.left -= offset;
            area.bottom += offset;
            area.left -= offset;
            ((View) mTopImgButton.getParent()).setTouchDelegate(new TouchDelegate(area, mTopImgButton));
        });
        mTopImgButton.setOnClickListener(v -> {
            popBackStack();
            DisplayHelper.hideKeyboard(getContext(), mSearch);
        });
        mTopImgButton.setEnabled(true);
        mSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mSearch.getText().toString().isEmpty())
                    mClearEditText.setVisibility(View.VISIBLE);
                else
                    mClearEditText.setVisibility(View.INVISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        mSearch.setOnKeyListener((v, keyCode, evt) -> {
            if (evt.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    return startSearch();
                }
            }
            return false;
        });
        mSearchButton = view.findViewById(R.id.tv_search);
        mSearchButton.setOnClickListener((View v) -> {
            startSearch();
        });
        mSearchHistory.setSizeLimit(20); // search history limit
        mClearEditText.setVisibility(View.INVISIBLE);
        mClearEditText.setOnClickListener(v -> mSearch.setText(""));
        mSearch.setOnFocusChangeListener((v, hasFocus) -> {
            mClearEditText.setVisibility(hasFocus && !mSearch.getText().toString().isEmpty() ? View.VISIBLE : View.INVISIBLE);
            if (hasFocus)
                DisplayHelper.showKeyboard(getContext(), mSearch);
            else
                DisplayHelper.hideKeyboard(getContext(), mSearch);
        });
        mTopBar.addRightView(view, R.id.topbar_search_input, lp);
        // init search history stuffs
        String historyJson = ConfigManager.getString(Definition.SETTINGS_SEARCH_HISTORY, "");
        if (TextUtils.isEmpty(historyJson)) {
            historyJson = mGson.toJson(new String[0]);
            ConfigManager.putString(Definition.SETTINGS_SEARCH_HISTORY, historyJson);
            mSearchHistoryBar.setVisibility(View.INVISIBLE);
        } else {
            String[] history = mGson.fromJson(historyJson, new TypeToken<String[]>() {
            }.getType());
            if (history.length > 0) {
                mSearchHistoryBar.setVisibility(View.VISIBLE);
                mSearchHistory.loadData(history);
            } else {
                mSearchHistoryBar.setVisibility(View.INVISIBLE);
            }
        }
        mSearchHistory.setItemListener((pos, v) -> {
            mSearch.setText(v.getText().toString());
            mSearch.setSelection(v.getText().length());
        });
        mDelHistory.setOnClickListener(v -> {
            new QMUIDialog.MessageDialogBuilder(getActivity())
                    .setMessage(getString(R.string.confirm_clear_search_history))
                    .addAction(getString(R.string.cancel), (QMUIDialog dialog, int index) -> {
                        dialog.dismiss();
                    })
                    .addAction(0, getString(R.string.confirm), QMUIDialogAction.ACTION_PROP_NEGATIVE,
                            (QMUIDialog dialog, int index) -> {
                                // clear history
                                mSearchHistoryBar.setVisibility(View.INVISIBLE);
                                mSearchHistory.clearAllItems();
                                // save
                                String temp = mGson.toJson(new String[0]);
                                ConfigManager.putString(Definition.SETTINGS_SEARCH_HISTORY, temp);
                                dialog.dismiss();
                            })
                    .create(R.style.QMUI_Dialog).show();
        });
        return root;
    }

    private boolean startSearch() {
        if (mSearch.getText().toString().isEmpty()) {
            Toast toast = QMUITipDialog.Builder.makeToast(getContext(), QMUITipDialog.Builder.ICON_TYPE_FAIL, getString(R.string.search_empty_input_not_allowed), Toast.LENGTH_SHORT);
            toast.show();
            return false;
        }
        // start search !
        mSearchHistory.pushFront(mSearch.getText().toString());
        String[] arr = mSearchHistory.getItemArray();
        String temp = mGson.toJson(arr);
        ConfigManager.putStringNow(Definition.SETTINGS_SEARCH_HISTORY, temp);
        if (arr.length > 0)
            mSearchHistoryBar.setVisibility(View.VISIBLE);
        keyword = mSearch.getText().toString();
        SearchResultFragment res = new SearchResultFragment();
        startFragment(res);
        return true;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSearch.requestFocus();
    }

    @Override
    public TransitionConfig onFetchTransitionConfig() {
        return FADE_TRANSITION_CONFIG;
    }
}
