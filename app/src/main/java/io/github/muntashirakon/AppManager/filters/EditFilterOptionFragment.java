// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_DURATION_MILLIS;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_INT;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_INT_FLAGS;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_LONG;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_NONE;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_REGEX;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_SIZE_BYTES;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_STR_MULTIPLE;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_STR_SINGLE;
import static io.github.muntashirakon.AppManager.filters.options.FilterOption.TYPE_TIME_MILLIS;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.resources.MaterialAttributes;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.filters.options.FilterOptions;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.MaterialSpinner;
import io.github.muntashirakon.widget.RecyclerView;
import mobi.upod.timedurationpicker.TimeDurationPickerDialog;

public class EditFilterOptionFragment extends DialogFragment {
    public static final String TAG = EditFilterOptionFragment.class.getSimpleName();
    public static final String ARG_OPTION = "opt";
    public static final String ARG_POSITION = "pos";

    public interface OnClickDialogButtonInterface {
        void onDeleteItem(int position, int id);

        void onUpdateItem(int position, @NonNull FilterOption item);

        void onAddItem(@NonNull FilterOption item);
    }

    private MaterialSpinner mKeySpinner;
    private TextInputLayout mGenericTextInputLayout;
    private TextInputEditText mGenericEditText;
    private TextInputLayout mDateTextInputLayout;
    private TextInputEditText mDateEditText;
    private RecyclerView mFlagsRecyclerView;
    @Nullable
    private FilterOption mFilterOption;
    @Nullable
    private FilterOption mCurrentFilterOption;
    @Nullable
    private String mCurrentKey;
    @FilterOption.KeyType
    private int mCurrentKeyType;
    @Nullable
    private ArrayAdapter<String> mKeyAdapter;
    private FilterOptionFlagsAdapter mFilterOptionFlagsAdapter;
    private OnClickDialogButtonInterface mOnClickDialogButtonInterface;
    private int mPosition;
    private long mDate;
    private final TextWatcher mGenericEditTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mCurrentKeyType != TYPE_NONE && TextUtils.isEmpty(s)) {
                mGenericTextInputLayout.setErrorEnabled(true);
                mGenericTextInputLayout.setError(getString(R.string.value_cannot_be_empty));
                return;
            }
            if (mCurrentKeyType == TYPE_REGEX) {
                try {
                    Pattern.compile(s.toString());
                } catch (PatternSyntaxException e) {
                    mGenericTextInputLayout.setErrorEnabled(true);
                    mGenericTextInputLayout.setError(getString(R.string.invalid_regex));
                    return;
                }
            } else if (mCurrentKeyType == TYPE_DURATION_MILLIS) {
                try {
                    mDate = Long.parseLong(s.toString());
                    mDateEditText.setText(DateUtils.getFormattedDuration(ContextUtils.getContext(), mDate));
                } catch (NumberFormatException ignore) {
                }
            } else if (mCurrentKeyType == TYPE_TIME_MILLIS) {
                try {
                    mDate = Long.parseLong(s.toString());
                    mDateEditText.setText(DateUtils.formatDate(ContextUtils.getContext(), mDate));
                } catch (NumberFormatException ignore) {
                }
            } else if (mCurrentKeyType == TYPE_INT_FLAGS) {
                try {
                    mFilterOptionFlagsAdapter.setFlag(Integer.parseInt(s.toString()));
                } catch (NumberFormatException ignore) {
                }
            }
            mGenericTextInputLayout.setErrorEnabled(false);
        }
    };

    public void setOnClickDialogButtonInterface(OnClickDialogButtonInterface onClickDialogButtonInterface) {
        mOnClickDialogButtonInterface = onClickDialogButtonInterface;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        Bundle args = requireArguments();
        mFilterOption = BundleCompat.getParcelable(args, ARG_OPTION, FilterOption.class);
        mPosition = args.getInt(ARG_POSITION, -1);
        boolean editMode = mFilterOption != null;
        View view = View.inflate(activity, R.layout.dialog_edit_filter_option, null);
        MaterialSpinner filterSpinner = view.findViewById(R.id.filter_selector_spinner);
        ArrayAdapter<CharSequence> filters = SelectedArrayAdapter.createFromResource(activity, R.array.finder_filters, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item);
        filterSpinner.setAdapter(filters);
        mKeySpinner = view.findViewById(R.id.type_selector_spinner);
        mKeyAdapter = new ArrayAdapter<>(requireActivity(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item);
        mKeySpinner.setAdapter(mKeyAdapter);
        mGenericEditText = view.findViewById(R.id.input_string);
        mGenericEditText.addTextChangedListener(mGenericEditTextWatcher);
        mGenericTextInputLayout = TextInputLayoutCompat.fromTextInputEditText(mGenericEditText);
        mDateEditText = view.findViewById(android.R.id.input);
        mDateEditText.setKeyListener(null);
        mDateTextInputLayout = TextInputLayoutCompat.fromTextInputEditText(mDateEditText);
        mFlagsRecyclerView = view.findViewById(R.id.recycler_view);
        mFlagsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        @SuppressLint({"RestrictedApi", "PrivateResource"})
        int layoutId = MaterialAttributes.resolveInteger(requireContext(), androidx.appcompat.R.attr.multiChoiceItemLayout,
                com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice);
        mFilterOptionFlagsAdapter = new FilterOptionFlagsAdapter(layoutId, v ->
                mGenericEditText.setText(String.valueOf(mFilterOptionFlagsAdapter.getFlag())));
        mFlagsRecyclerView.setAdapter(mFilterOptionFlagsAdapter);
        if (mFilterOption != null) {
            mCurrentFilterOption = mFilterOption;
            filterSpinner.setSelection(filters.getPosition(mCurrentFilterOption.type));
            updateUiForFilter(mCurrentFilterOption);
        } else {
            filterSpinner.setSelection(-1);
        }
        // Setup listeners
        filterSpinner.setOnItemClickListener((parent, v, position, id) -> {
            mCurrentFilterOption = FilterOptions.create(filters.getItem(position).toString());
            updateUiForFilter(mCurrentFilterOption);
        });
        mKeySpinner.setOnItemClickListener((parent, view1, position, id) -> {
            if (mKeyAdapter == null || mCurrentFilterOption == null) {
                return;
            }
            mCurrentKey = mKeyAdapter.getItem(position);
            int lastKeyType = mCurrentKeyType;
            mCurrentKeyType = Objects.requireNonNull(mCurrentFilterOption.getKeysWithType().get(mCurrentKey));
            // Reset value if the data is not of the same type
            if (lastKeyType != mCurrentKeyType) {
                mGenericEditText.setText("");
            }
            updateUiForType(mCurrentKeyType);
        });
        Objects.requireNonNull(mOnClickDialogButtonInterface);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setView(view)
                .setPositiveButton(editMode ? R.string.update : R.string.add, (dialog, which) -> {
                    if (mCurrentFilterOption == null) {
                        UIUtils.displayLongToast(R.string.key_name_cannot_be_null);
                        return;
                    }
                    Editable editable = mGenericEditText.getText();
                    try {
                        Objects.requireNonNull(mCurrentKey);
                        mCurrentFilterOption.setKeyValue(mCurrentKey, TextUtils.isEmpty(editable) ? null : editable.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                        UIUtils.displayLongToast(R.string.error_evaluating_input);
                        return;
                    }
                    if (editMode) {
                        mOnClickDialogButtonInterface.onUpdateItem(mPosition, mCurrentFilterOption);
                    } else {
                        mOnClickDialogButtonInterface.onAddItem(mCurrentFilterOption);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                });
        if (editMode) {
            builder.setNeutralButton(R.string.delete, (dialog, which) -> mOnClickDialogButtonInterface.onDeleteItem(mPosition, mFilterOption.id));
        }
        return builder.create();
    }

    private void updateUiForFilter(@NonNull FilterOption filterOption) {
        if (mKeyAdapter == null) {
            return;
        }
        // List all keys
        mKeyAdapter.clear();
        mKeyAdapter.addAll(filterOption.getKeysWithType().keySet());
        // Set default or previously set key as the current key
        mCurrentKey = filterOption.getKey();
        mCurrentKeyType = filterOption.getKeyType();
        mKeySpinner.setSelection(mKeyAdapter.getPosition(mCurrentKey));
        // Update the text field
        mGenericEditText.setText(filterOption.getValue());
        updateUiForType(mCurrentKeyType);
    }

    private void updateUiForType(@FilterOption.KeyType int type) {
        // Update visibility
        if (type == TYPE_NONE || type == TYPE_INT_FLAGS) {
            mGenericTextInputLayout.setVisibility(View.GONE);
            mDateTextInputLayout.setVisibility(View.GONE);
        } else if (type == TYPE_DURATION_MILLIS || type == TYPE_TIME_MILLIS) {
            mGenericTextInputLayout.setVisibility(View.GONE);
            mDateTextInputLayout.setVisibility(View.VISIBLE);
        } else {
            mGenericTextInputLayout.setVisibility(View.VISIBLE);
            mDateTextInputLayout.setVisibility(View.GONE);
        }
        if (type == TYPE_INT_FLAGS) {
            mFlagsRecyclerView.setVisibility(View.VISIBLE);
        } else mFlagsRecyclerView.setVisibility(View.GONE);
        // Update single-line
        mGenericEditText.setSingleLine(type != TYPE_STR_MULTIPLE);
        // Update hint, input-type
        switch (type) {
            case TYPE_NONE:
                break;
            case TYPE_INT_FLAGS:
                mGenericEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                Objects.requireNonNull(mCurrentFilterOption);
                mFilterOptionFlagsAdapter.setFlagMap(mCurrentFilterOption.getFlags(Objects.requireNonNull(mCurrentKey)));
                break;
            case TYPE_DURATION_MILLIS:
                mDateTextInputLayout.setHint(R.string.duration);
                mGenericEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                mDateEditText.setOnClickListener(v -> openDurationPicker());
                break;
            case TYPE_INT:
                mGenericTextInputLayout.setHint(R.string.integer_value);
                mGenericEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
            case TYPE_LONG:
                mGenericTextInputLayout.setHint(R.string.long_integer_value);
                mGenericEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
            case TYPE_REGEX:
                mGenericTextInputLayout.setHint(R.string.search_option_regex);
                mGenericEditText.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            case TYPE_SIZE_BYTES:
                mGenericTextInputLayout.setHint(R.string.size_in_bytes);
                mGenericEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
            case TYPE_STR_MULTIPLE:
                mGenericTextInputLayout.setHint(R.string.string_value); // newlines
                mGenericEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                break;
            case TYPE_STR_SINGLE:
                mGenericTextInputLayout.setHint(R.string.string_value);
                mGenericEditText.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            case TYPE_TIME_MILLIS:
                mDateTextInputLayout.setHint(R.string.date);
                mGenericEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                mDateEditText.setOnClickListener(v -> openDatePicker());
                break;
        }
    }

    public void openDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.date)
                .setSelection(mDate <= 0 ? MaterialDatePicker.todayInUtcMilliseconds() : mDate)
                .build();
        datePicker.addOnPositiveButtonClickListener(selection -> mGenericEditText.setText(String.valueOf(selection)));
        datePicker.show(getChildFragmentManager(), "DatePicker");
    }

    public void openDurationPicker() {
        new TimeDurationPickerDialog(requireContext(), (picker, duration) ->
                mGenericEditText.setText(String.valueOf(duration)), mDate)
                .show();
    }

    private static class FilterOptionFlagsAdapter extends RecyclerView.Adapter<FilterOptionFlagsAdapter.ViewHolder> {
        @LayoutRes
        private final int mLayoutId;
        private final View.OnClickListener mItemClickListener;
        private final List<Integer> mFlags = Collections.synchronizedList(new ArrayList<>());
        private Map<Integer, CharSequence> mFlagMap;
        private int mFlag;

        public FilterOptionFlagsAdapter(@LayoutRes int layoutId, View.OnClickListener itemClickListener) {
            mLayoutId = layoutId;
            mFlagMap = Collections.emptyMap();
            mItemClickListener = itemClickListener;
        }

        public void setFlagMap(@NonNull Map<Integer, CharSequence> flagMap) {
            mFlagMap = flagMap;
            AdapterUtils.notifyDataSetChanged(this, mFlags, new ArrayList<>(flagMap.keySet()));
        }

        public void setFlag(int flag) {
            mFlag = flag;
            notifyItemRangeChanged(0, mFlags.size(), AdapterUtils.STUB);
        }

        public int getFlag() {
            return mFlag;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int flag = mFlags.get(position);
            CharSequence flagName = mFlagMap.get(flag);
            holder.item.setText(flagName);
            holder.item.setChecked((mFlag & flag) != 0);
            holder.item.setOnClickListener(v -> {
                if ((mFlag & flag) != 0) {
                    // Already selected, deselect
                    mFlag &= ~flag;
                    holder.item.setChecked(false);
                } else {
                    // Not yet selected, select
                    mFlag |= flag;
                    holder.item.setChecked(true);
                }
                mItemClickListener.onClick(v);
            });
        }

        @Override
        public int getItemCount() {
            return mFlags.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckedTextView item;

            @SuppressLint("RestrictedApi")
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                item = itemView.findViewById(android.R.id.text1);
                int textAppearanceBodyLarge = MaterialAttributes.resolveInteger(item.getContext(), com.google.android.material.R.attr.textAppearanceBodyLarge, 0);
                TextViewCompat.setTextAppearance(item, textAppearanceBodyLarge);
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                item.setTextColor(MaterialColors.getColor(item.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, -1));
            }
        }
    }
}
