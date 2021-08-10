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
package org.jkiss.dbeaver.ui.dialogs.confirmations;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.ui.UIUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

//TODO add javadocs
public final class ConfirmatorBuilder<ANSWER> {
    @Nullable
    private Shell shell = UIUtils.getActiveWorkbenchShell();

    @NotNull
    private String title = "";

    @NotNull
    private String message = "";

    @NotNull
    private Collection<ANSWER> answers = Collections.emptyList();

    @Nullable
    private ANSWER defaultAnswer;

    @NotNull
    private Function<? super ANSWER, String> displayStringExtractor = ANSWER::toString;

    @Nullable
    private DBPImage image;

    @Nullable
    private Consumer<Composite> compositeSupplier;

    //------ Builder methods

    @NotNull
    public Confirmator<ANSWER> build() {
        throw new UnsupportedOperationException("Implement me!"); //TODO
    }

    @NotNull
    public ConfirmatorBuilder<ANSWER> withShell(@Nullable Shell shell) {
        this.shell = shell;
        return this;
    }

    @NotNull
    public ConfirmatorBuilder<ANSWER> withTitle(@NotNull String title) {
        this.title = title;
        return this;
    }

    @NotNull
    public ConfirmatorBuilder<ANSWER> withMessage(@NotNull String message) {
        this.message = message;
        return this;
    }

    @NotNull
    private ConfirmatorBuilder<ANSWER> withAnswers(@NotNull Collection<? extends ANSWER> answers) {
        this.answers = new ArrayList<>(answers);
        return this;
    }

    @NotNull
    public ConfirmatorBuilder<ANSWER> withDefaultAnswer(@NotNull ANSWER defaultAnswer) {
        this.defaultAnswer = defaultAnswer;
        return this;
    }

    @NotNull
    public ConfirmatorBuilder<ANSWER> withDisplayStringExtractor(@NotNull Function<? super ANSWER, String> displayStringExtractor) {
        this.displayStringExtractor = displayStringExtractor;
        return this;
    }

    @NotNull
    public ConfirmatorBuilder<ANSWER> withImage(@NotNull DBPImage image) {
        this.image = image;
        return this;
    }

    @NotNull
    public ConfirmatorBuilder<ANSWER> withExtension(@NotNull Consumer<Composite> compositeSupplier) {
        this.compositeSupplier = compositeSupplier;
        return this;
    }
}
