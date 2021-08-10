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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.LayoutConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class ConfirmationDialogModern<ANSWER> extends Dialog {
    @Nullable
    private String title;
    @Nullable
    private String message;
    @Nullable
    private List<ANSWER> answers;
    private int defaultAnswerIdx;
    @NotNull
    private Function<ANSWER, String> labelExtractor = ANSWER::toString;
    @NotNull
    private Function<Composite, Composite> customAreaProvider = (c) -> null;
    @Nullable
    private DBPImage image;
    @Nullable
    private DBPImage titleImage;

    @Nullable
    private List<Button> buttons;

    ConfirmationDialogModern(@Nullable Shell parentShell) {
        super(parentShell);
    }

    protected Control createContents(Composite parent) {
        initializeDialogUnits(parent);
        Point defaultSpacing = LayoutConstants.getSpacing();
        GridLayoutFactory.fillDefaults().margins(LayoutConstants.getMargins())
                .spacing(defaultSpacing.x * 2,
                        defaultSpacing.y).numColumns(2).applyTo(parent); //fixme

        GridDataFactory.fillDefaults().grab(true, true).applyTo(parent); //fixme
        dialogArea = createDialogArea(parent);
        buttonBar = createButtonBar(parent);
        applyDialogFont(parent);
        return parent;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        // add main image
        if (image != null) {
            Image swtImage = DBeaverIcons.getImage(image);
            Label imageLabel = new Label(parent, SWT.NULL);
            swtImage.setBackground(imageLabel.getBackground());
            imageLabel.setImage(swtImage);
            GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.BEGINNING).applyTo(imageLabel); //fixme
        }

        //add message
        if (message != null) {
            Label messageLabel = new Label(parent, SWT.WRAP);
            messageLabel.setText(message);
            GridDataFactory
                    .fillDefaults()
                    .align(SWT.FILL, SWT.BEGINNING)
                    .grab(true, false)
                    .hint(
                            convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH),
                            SWT.DEFAULT).applyTo(messageLabel);
        }

        // create the top level composite for the dialog area fixme
        Composite composite = new Composite(parent, SWT.NONE); //fixme
        GridLayout layout = new GridLayout();
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        composite.setLayout(layout);
        GridData data = new GridData(GridData.FILL_BOTH);
        data.horizontalSpan = 2;
        composite.setLayoutData(data);

        Composite customArea = customAreaProvider.apply(composite);
//        if (customArea == null) {
//            //If it is null create a dummy label for spacing purposes
//            customArea = new Label(composite, SWT.NULL); TODO
//        }

        return composite;
    }

    @Override
    protected Control createButtonBar(@NotNull Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(0) // this is incremented
                // by createButton
                .equalWidth(true).applyTo(composite); //fixme

        GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER).span(2, 1)
                .applyTo(composite); //fixme
        composite.setFont(parent.getFont());
        createButtonsForButtonBar(composite);
        return composite;
    }

    @Override
    protected void configureShell(@NotNull Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
        if (titleImage != null) {
            shell.setImage(DBeaverIcons.getImage(titleImage));
        }
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        if (answers == null) {
            return;
        }
        buttons = new ArrayList<>(answers.size());
        for (int i = 0; i < answers.size(); i++) {
            ANSWER answer = answers.get(i);
            String label = labelExtractor.apply(answer);
            buttons.add(createButton(parent, i, label, defaultAnswerIdx == i));
        }
    }

    @Nullable
    @Override
    protected Button getButton(int index) {
        if (buttons != null && CommonUtils.isValidIndex(index, buttons.size())) {
            return buttons.get(index);
        }
        return null;
    }

    @Override
    protected void buttonPressed(int buttonId) {
        setReturnCode(buttonId);
        close();
    }

    @Override
    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
        Button button = super.createButton(parent, id, label, defaultButton);
        if (defaultButton) {
            button.setFocus();
        }
        return button;
    }

    @Override
    protected void handleShellCloseEvent() {
        super.handleShellCloseEvent();
        setReturnCode(SWT.DEFAULT);
    }
}
