/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.dialogs;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.utils.CommonUtils;

import java.util.*;
import java.util.function.Function;

//TODO add javadocs
public final class Questioner<ANSWER> {
    private final QuestionDialog dialog;

    @Nullable
    private List<ANSWER> answers;
    @Nullable
    private ANSWER defaultAnswer;
    @Nullable
    private Function<? super ANSWER, String> labelExtractor;

    public Questioner(@Nullable Shell shell) {
        dialog = new QuestionDialog(shell);
    }

    // ----- Builder methods

    @NotNull
    public Questioner<ANSWER> setTitle(@NotNull String title) {
        dialog.setTitle(title);
        return this;
    }

    @NotNull
    public Questioner<ANSWER> setMessage(@NotNull String message) {
        dialog.setMessage(message);
        return this;
    }

    @NotNull
    public Questioner<ANSWER> setPrimaryImage(@NotNull DBPImage image) {
        dialog.setPrimaryImage(image);
        return this;
    }

    @NotNull
    public Questioner<ANSWER> setTitleImage(@NotNull DBPImage image) {
        dialog.setTitleImage(image);
        return this;
    }

    @NotNull
    public Questioner<ANSWER> setCustomAreaProvider(@NotNull Function<? super Composite, ? extends Composite> customAreaProvider) {
        dialog.setCustomAreaProvider(customAreaProvider);
        return this;
    }

    @NotNull
    public Questioner<ANSWER> setAnswers(@NotNull List<? extends ANSWER> answers) {
        this.answers = new ArrayList<>(answers);
        return this;
    }

    @NotNull
    public Questioner<ANSWER> setDefaultAnswer(@NotNull ANSWER defaultAnswer) {
        this.defaultAnswer = defaultAnswer;
        return this;
    }

    @NotNull
    public Questioner<ANSWER> setLabelExtractor(@NotNull Function<? super ANSWER, String> labelExtractor) {
        this.labelExtractor = labelExtractor;
        return this;
    }

    // ----- Question-related methods

    @Nullable
    public ANSWER ask() {
        // create labels from answers, find default answer
        List<String> labels;
        int defaultIdx = 0;
        if (labelExtractor == null) {
            labelExtractor = ANSWER::toString;
        }
        if (answers != null) {
            labels = new ArrayList<>(answers.size());
            for (int i = 0; i < answers.size(); i++) {
                ANSWER answer = answers.get(i);
                if (Objects.equals(answer, defaultAnswer)) {
                    defaultIdx = i;
                }
                labels.add(labelExtractor.apply(answer));
            }
        } else {
            labels = Collections.emptyList();
        }
        dialog.setLabels(labels);
        dialog.setDefaultAnswerIdx(defaultIdx);

        // Open dialog, detect the answer
        final int[] answerIdx = {0};
        UIUtils.syncExec(() -> answerIdx[0] = dialog.open());
        if (answers == null || !CommonUtils.isValidIndex(answerIdx[0], answers.size())) {
            return null;
        }
        return answers.get(answerIdx[0]);
    }
}
