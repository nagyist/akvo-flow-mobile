/*
 *  Copyright (C) 2010-2015 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo FLOW.
 *
 *  Akvo FLOW is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo FLOW is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.flow.ui.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import org.akvo.flow.R;
import org.akvo.flow.domain.AltText;
import org.akvo.flow.domain.Option;
import org.akvo.flow.domain.Question;
import org.akvo.flow.domain.QuestionResponse;
import org.akvo.flow.domain.response.Response;
import org.akvo.flow.event.QuestionInteractionEvent;
import org.akvo.flow.event.SurveyListener;
import org.akvo.flow.util.ConstantUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Question type that supports the selection of a single option from a list of
 * choices (i.e. a radio button group).
 *
 * @author Christopher Fagiani
 */
public class OptionQuestionView extends QuestionView {
    private static final String OTHER_CODE = "OTHER";
    private final String OTHER_TEXT;
    private RadioGroup mOptionGroup;
    private List<CheckBox> mCheckBoxes;
    private TextView mOtherText;
    private Map<Integer, Option> mIdToOptionMap;
    private volatile boolean mSuppressListeners = false;
    private String mLatestOtherText;

    // A Map would be more efficient here, but we need to preserve the original order.
    // FIXME: We might not need this variable
    private List<Option> mSelectedOptions;

    public OptionQuestionView(Context context, Question q, SurveyListener surveyListener) {
        super(context, q, surveyListener);
        OTHER_TEXT = getResources().getString(R.string.othertext);
        init();
    }

    private void init() {
        // Just inflate the header. Options will be added dynamically
        setQuestionView(R.layout.question_header);

        mIdToOptionMap = new HashMap<>();
        mSelectedOptions = new ArrayList<>();

        if (mQuestion.getOptions() == null) {
            return;
        }

        mSuppressListeners = true;
        if (mQuestion.isAllowMultiple()) {
            setupCheckboxType();
        } else {
            setupRadioType();
        }

        if (mQuestion.isAllowOther()) {
            mOtherText = new TextView(getContext());
            mOtherText.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            addView(mOtherText);
        }
        mSuppressListeners = false;
    }

    private void setupRadioType() {
        mOptionGroup = new RadioGroup(getContext());
        mOptionGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                handleSelection(checkedId, true);
            }
        });
        for (Option o : mQuestion.getOptions()) {
            RadioButton rb = new RadioButton(getContext());
            rb.setLayoutParams(new RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            rb.setEnabled(!isReadOnly());
            rb.setLongClickable(true);
            rb.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    onClearAnswer();
                    return true;
                }
            });
            rb.setText(formOptionText(o), BufferType.SPANNABLE);
            mOptionGroup.addView(rb);
            mIdToOptionMap.put(rb.getId(), o);
        }
        if (mQuestion.isAllowOther()) {
            RadioButton rb = new RadioButton(getContext());
            rb.setLayoutParams(new RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            rb.setText(OTHER_TEXT);
            rb.setEnabled(!isReadOnly());
            mOptionGroup.addView(rb);
            Option other = new Option();
            other.setCode(OTHER_CODE);
            mIdToOptionMap.put(rb.getId(), other);
        }
        addView(mOptionGroup);
    }

    private void setupCheckboxType() {
        mCheckBoxes = new ArrayList<>();
        List<Option> options = mQuestion.getOptions();
        for (int i = 0; i < options.size(); i++) {
            CheckBox box = new CheckBox(getContext());
            box.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            box.setEnabled(!isReadOnly());
            box.setId(i);
            box.setText(formOptionText(options.get(i)), BufferType.SPANNABLE);
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    handleSelection(buttonView.getId(), isChecked);
                }
            });
            mCheckBoxes.add(box);
            mIdToOptionMap.put(box.getId(), options.get(i));
            addView(box);
        }
        if (mQuestion.isAllowOther()) {
            CheckBox box = new CheckBox(getContext());
            box.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            box.setEnabled(!isReadOnly());
            box.setId(options.size());
            box.setText(OTHER_TEXT);
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    handleSelection(buttonView.getId(), isChecked);
                }
            });
            mCheckBoxes.add(box);
            Option other = new Option();
            other.setCode(OTHER_CODE);
            mIdToOptionMap.put(box.getId(), other);
            addView(box);
        }
    }

    @Override
    public void notifyOptionsChanged() {
        super.notifyOptionsChanged();

        List<Option> options = mQuestion.getOptions();
        if (mQuestion.isAllowMultiple()) {
            for (int i = 0; i < mCheckBoxes.size(); i++) {
                // make sure we have a corresponding option (i.e. not the OTHER option)
                if (i < options.size()) {
                    mCheckBoxes.get(i).setText(formOptionText(options.get(i)), BufferType.SPANNABLE);
                }
            }
        } else {
            for (int i = 0; i < mOptionGroup.getChildCount(); i++) {
                // make sure we have a corresponding option (i.e. not the OTHER option)
                if (i < options.size()) {
                    ((RadioButton) (mOptionGroup.getChildAt(i))).setText(formOptionText(options.get(i)));
                }
            }
        }
    }

    /**
     * forms the text for an option based on the visible languages
     */
    private Spanned formOptionText(Option opt) {
        boolean isFirst = true;
        StringBuilder text = new StringBuilder();
        final String[] langs = getLanguages();
        for (int i = 0; i < langs.length; i++) {
            if (getDefaultLang().equalsIgnoreCase(langs[i])) {
                if (!isFirst) {
                    text.append(" / ");
                } else {
                    isFirst = false;
                }
                text.append(TextUtils.htmlEncode(opt.getText()));

            } else {
                AltText txt = opt.getAltText(langs[i]);
                if (txt != null) {
                    if (!isFirst) {
                        text.append(" / ");
                    } else {
                        isFirst = false;
                    }
                    text.append("<font color='")
                            .append(sColors[i])
                            .append("'>")
                            .append(TextUtils.htmlEncode(txt.getText()))
                            .append("</font>");
                }
            }
        }
        return Html.fromHtml(text.toString());
    }

    /**
     * populates the QuestionResponse object based on the current state of the
     * selected option(s)
     */
    private void handleSelection(int checkedId, boolean isChecked) {
        if (mSuppressListeners) {
            return;
        }

        boolean isOther = OTHER_TEXT.equals(mIdToOptionMap.get(checkedId).getText());
        if (isOther && isChecked) {
            displayOtherDialog(checkedId);
            return;
        }

        captureResponse();
    }

    /**
     * Forms a delimited string containing all selected options not including OTHER
     */
    private List<Option> getSelection() {
        List<Option> options = new ArrayList<>();
        if (mQuestion.isAllowMultiple()) {
            for (CheckBox cb: mCheckBoxes) {
                if (cb.isChecked()) {
                    Option option = mIdToOptionMap.get(cb.getId());
                    options.add(option);
                }
            }
        } else {
            Option option = mIdToOptionMap.get(mOptionGroup.getCheckedRadioButtonId());
            options.add(option);
        }

        return options;
    }

    /**
     * displays a pop-up dialog where the user can enter in a specific value for
     * the "OTHER" option in a freetext view.
     */
    private void displayOtherDialog(final int otherId) {
        LinearLayout main = new LinearLayout(getContext());
        main.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        main.setOrientation(LinearLayout.VERTICAL);
        final EditText inputView = new EditText(getContext());
        inputView.setSingleLine();
        inputView.append(mLatestOtherText);
        main.addView(inputView);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.otherinstructions);
        builder.setView(main);
        builder.setPositiveButton(R.string.okbutton,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mLatestOtherText = inputView.getText().toString().trim();
                        mIdToOptionMap.get(otherId).setText(mLatestOtherText);
                        captureResponse();
                        // update the UI with the other text
                        if (mOtherText != null) {
                            mOtherText.setText(mLatestOtherText);
                        }
                    }
                });
        builder.setNegativeButton(R.string.cancelbutton,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Deselect 'other'
                        handleSelection(otherId, false);
                    }
                });

        builder.show();
    }

    /**
     * checks off the correct option based on the response value
     */
    @Override
    public void rehydrate(QuestionResponse resp) {
        super.rehydrate(resp);

        if (resp == null || TextUtils.isEmpty(resp.getValue())) {
            return;
        }

        List<Option> selectedOptions = loadResponse(resp.getValue());
        if (selectedOptions.isEmpty()) {
            return;
        }

        mSuppressListeners = true;
        if (!mQuestion.isAllowMultiple()) {
            Option option = selectedOptions.get(0);
            for (int i=0; i<mOptionGroup.getChildCount(); i++) {
                RadioButton rb = (RadioButton) mOptionGroup.getChildAt(i);
                if (rb.getText().equals(option.getText())) {
                    mOptionGroup.check(rb.getId());
                    break;
                } else if (OTHER_TEXT.equals(rb.getText()) && mQuestion.isAllowOther()) {
                    // Assume this is the OTHER value
                    mOptionGroup.check(rb.getId());
                    mLatestOtherText = option.getText();
                    mOtherText.setText(mLatestOtherText);
                    mIdToOptionMap.get(rb.getId()).setText(mLatestOtherText);
                    break;
                }
            }
        } else {
            for (Option option : selectedOptions) {
                for (CheckBox cb : mCheckBoxes) {
                    if (option.equals(mIdToOptionMap.get(cb.getId()))) {
                        cb.setChecked(true);
                        break;
                    } else if (OTHER_TEXT.equals(cb.getText()) && mQuestion.isAllowOther()) {
                        // Assume this is the OTHER value
                        cb.setChecked(true);
                        mLatestOtherText = option.getText();
                        mOtherText.setText(mLatestOtherText);
                        mIdToOptionMap.get(cb.getId()).setText(mLatestOtherText);
                    }
                }
            }
        }
        mSuppressListeners = false;
    }

    /**
     * clears the selected option
     */
    @Override
    public void resetQuestion(boolean fireEvent) {
        super.resetQuestion(fireEvent);
        mSuppressListeners = true;
        if (mOptionGroup != null) {
            mOptionGroup.clearCheck();
        }
        if (mCheckBoxes != null) {
            for (int i = 0; i < mCheckBoxes.size(); i++) {
                mCheckBoxes.get(i).setChecked(false);
            }
        }
        mSuppressListeners = false;
    }

    @Override
    public void setTextSize(float size) {
        super.setTextSize(size);
        if (mOptionGroup != null && mOptionGroup.getChildCount() > 0) {
            for (int i = 0; i < mOptionGroup.getChildCount(); i++) {
                ((RadioButton) (mOptionGroup.getChildAt(i))).setTextSize(size);
            }
        } else if (mCheckBoxes != null && mCheckBoxes.size() > 0) {
            for (int i = 0; i < mCheckBoxes.size(); i++) {
                mCheckBoxes.get(i).setTextSize(size);
            }
        }
    }

    @Override
    public void captureResponse(boolean suppressListeners) {
        mSelectedOptions = getSelection();
        // TODO: Based on mSelectedOptions, populate Question Response
    }

    private List<Option> loadResponse(String data) {
        // TODO: Handle JSON and pipe-separated values
        return null;
    }

}
