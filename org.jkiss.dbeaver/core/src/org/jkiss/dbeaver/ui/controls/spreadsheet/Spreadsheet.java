/*
 * Copyright (c) 2010, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.ui.controls.spreadsheet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.IWorkbenchActionDefinitionIds;
import org.jkiss.dbeaver.ui.controls.lightgrid.GridColumn;
import org.jkiss.dbeaver.ui.controls.lightgrid.GridEditor;
import org.jkiss.dbeaver.ui.controls.lightgrid.GridItem;
import org.jkiss.dbeaver.ui.controls.lightgrid.GridPos;
import org.jkiss.dbeaver.ui.controls.lightgrid.IGridContentProvider;
import org.jkiss.dbeaver.ui.controls.lightgrid.LightGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ResultSetControl
 */
public class Spreadsheet extends Composite implements Listener {
    static Log log = LogFactory.getLog(Spreadsheet.class);

    public static final int MAX_DEF_COLUMN_WIDTH = 300;
    public static final int MAX_INLINE_EDIT_WITH = 300;

    private static final int Event_ChangeCursor = 1000;

    private LightGrid grid;
    private GridEditor tableEditor;
    private List<GridColumn> curColumns = new ArrayList<GridColumn>();

    //private GridPanel gridPanel;

    private IWorkbenchPartSite site;
    private IGridDataProvider dataProvider;
    private GridSelectionProvider selectionProvider;

    private Clipboard clipboard;
    private ActionInfo[] actionsInfo;

    private Color foregroundNormal;
    private Color foregroundLines;
    private Color foregroundSelected;
    private Color backgroundModified;
    private Color backgroundNormal;
    private Color backgroundControl;
    private Color backgroundSelected;

    private transient LazyGridRow lazyRow;
    private SelectionListener gridSelectionListener;

    public Spreadsheet(
        Composite parent,
        int style,
        IWorkbenchPartSite site,
        IGridDataProvider dataProvider)
    {
        super(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, true);
        layout.numColumns = 1;
        layout.makeColumnsEqualWidth = false;
        layout.marginWidth = 0;
        this.setLayout(layout);

        this.site = site;
        this.dataProvider = dataProvider;
        this.selectionProvider = new GridSelectionProvider(this);

        foregroundNormal = getDisplay().getSystemColor(SWT.COLOR_LIST_FOREGROUND);
        foregroundLines = getDisplay().getSystemColor(SWT.COLOR_GRAY);
        foregroundSelected = getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
        backgroundModified = new Color(getDisplay(), 0xFF, 0xE4,
                                       0xB5);//getDisplay().getSystemColor(SWT.COLOR_DARK_RED);
        backgroundNormal = getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
        backgroundSelected = getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
        backgroundControl = getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);

        clipboard = new Clipboard(getDisplay());

        actionsInfo = new ActionInfo[]{
            new ActionInfo(new GridAction(IWorkbenchActionDefinitionIds.COPY) {
                public void run()
                {
                    copySelectionToClipboard();
                }
            }),
            new ActionInfo(new CursorMoveAction(ITextEditorActionDefinitionIds.LINE_START)),
            new ActionInfo(new CursorMoveAction(ITextEditorActionDefinitionIds.LINE_END)),
            new ActionInfo(new CursorMoveAction(ITextEditorActionDefinitionIds.TEXT_START)),
            new ActionInfo(new CursorMoveAction(ITextEditorActionDefinitionIds.TEXT_END)),
            new ActionInfo(new GridAction(ITextEditorActionDefinitionIds.SELECT_ALL) {
                public void run()
                {
                    grid.selectAll();
                }
            }),
        };

        this.createControl(style);
    }

    public LightGrid getGrid()
    {
        return grid;
    }

    public Color getForegroundNormal()
    {
        return foregroundNormal;
    }

    public Color getForegroundLines()
    {
        return foregroundLines;
    }

    public Color getForegroundSelected()
    {
        return foregroundSelected;
    }

    public void setForegroundSelected(Color foregroundSelected)
    {
        this.foregroundSelected = foregroundSelected;
        this.grid.redraw();
    }

    public Color getBackgroundNormal()
    {
        return backgroundNormal;
    }

    public Color getBackgroundControl()
    {
        return backgroundControl;
    }

    public Color getBackgroundSelected()
    {
        return backgroundSelected;
    }

    public void setBackgroundSelected(Color backgroundSelected)
    {
        this.backgroundSelected = backgroundSelected;
        this.grid.redraw();
    }

    public void setFont(Font font)
    {
        grid.setFont(font);
        //gridPanel.setFont(font);
    }

    public List<GridPos> getSelection()
    {
        return Collections.emptyList();
    }

    public Point getCursorPosition()
    {
        if (grid.isDisposed() || grid.getItemCount() <= 0 || grid.getColumnCount() <= 0) {
            return new Point(-1, -1);
        }
        return grid.getFocusCell();
    }

    public void setRowHeaderWidth(int width)
    {
        grid.setItemHeaderWidth(width);
    }

    public void shiftCursor(int xOffset, int yOffset, boolean keepSelection)
    {
        if (xOffset == 0 && yOffset == 0) {
            return;
        }
        Point curPos = getCursorPosition();
        if (curPos == null) {
            return;
        }
        Point newPos = new Point(curPos.x, curPos.y);
        Event fakeEvent = new Event();
        fakeEvent.widget = grid;
        SelectionEvent selectionEvent = new SelectionEvent(fakeEvent);
        // Move row
        if (yOffset != 0) {
            int newRow = curPos.y + yOffset;
            if (newRow < 0) {
                newRow = 0;
            }
            if (newRow >= getItemCount()) {
                newRow = getItemCount() - 1;
            }
            newPos.y = newRow;
            selectionEvent.data = newRow;
            grid.setFocusItem(newRow);
            grid.showItem(newRow);
        }
        // Move column
        if (xOffset != 0) {
            int newCol = curPos.x + xOffset;
            if (newCol < 0) {
                newCol = 0;
            }
            if (newCol >= getColumnsCount()) {
                newCol = getColumnsCount() - 1;
            }
            newPos.x = newCol;
            GridColumn column = grid.getColumn(newCol);
            if (column != null) {
                grid.setFocusColumn(column);
                grid.showColumn(column);
            }
        }
        if (!keepSelection) {
            grid.deselectAll();
        }
        grid.selectCell(newPos);
        //spreadsheet.s
        grid.redraw();

        // Change selection event
        selectionEvent.x = newPos.x;
        selectionEvent.y = newPos.y;
        gridSelectionListener.widgetSelected(selectionEvent);
/*
        if (currentPosition == null) {
            currentPosition = cursorPosition;
        }
        if (newCol != currentPosition.col || newRow != currentPosition.row) {
            changeSelection(newCol, newRow, false, inKeyboardSelection, keepSelection, false);
            // Ensure seletion is visible
            TableItem tableItem = table.getItem(newRow);
            if (newCol != currentPosition.col) {
                TableColumn newColumn = table.getColumn(newCol);
                table.showColumn(newColumn);
            }
            if (newRow != currentPosition.row) {
                table.showItem(tableItem);
                gridPanel.redraw();
            }

            currentPosition = new GridPos(newCol, newRow);
        }
*/
    }

    public void addCursorChangeListener(Listener listener)
    {
        super.addListener(Event_ChangeCursor, listener);
    }

    public void removeCursorChangeListener(Listener listener)
    {
        super.removeListener(Event_ChangeCursor, listener);
    }

    private void createControl(int style)
    {
        Composite group = new Composite(this, SWT.NONE);
        GridData gd = new GridData(GridData.FILL_BOTH);
        group.setLayoutData(gd);

        GridLayout layout = new GridLayout(1, true);
        layout.numColumns = 2;
        layout.makeColumnsEqualWidth = false;
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 0;
        group.setLayout(layout);

        grid = new LightGrid(group, style);
        grid.setRowHeaderVisible(true);
        //grid.setFooterVisible(true);
        //spreadsheet.set
        //spreadsheet.setRowHeaderRenderer(new IGridRenderer() {
        //});

        grid.setLinesVisible(true);
        grid.setHeaderVisible(true);

        gd = new GridData(GridData.FILL_BOTH);
        grid.setLayoutData(gd);

        grid.addListener(SWT.MouseDoubleClick, this);
        grid.addListener(SWT.MouseDown, this);
        grid.addListener(SWT.KeyDown, this);
        grid.addListener(SWT.FocusIn, this);
        grid.addListener(SWT.FocusOut, this);

        if ((style & SWT.VIRTUAL) != 0) {
            lazyRow = new LazyGridRow();
            grid.addListener(SWT.SetData, this);
        }

        gridSelectionListener = new SelectionListener() {
            public void widgetSelected(SelectionEvent e)
            {
                Integer row = (Integer) e.data;
                Point focusCell = grid.getFocusCell();
                if (focusCell != null) {
                    Event event = new Event();
                    event.data = row;
                    event.data = e.data;
                    event.x = focusCell.x;
                    event.y = focusCell.y;
                    notifyListeners(Event_ChangeCursor, event);
                }
            }

            public void widgetDefaultSelected(SelectionEvent e)
            {
            }
        };
        grid.addSelectionListener(gridSelectionListener);

        tableEditor = new GridEditor(grid);
        tableEditor.horizontalAlignment = SWT.LEFT;
        tableEditor.verticalAlignment = SWT.TOP;
        tableEditor.grabHorizontal = true;
        tableEditor.grabVertical = true;
        tableEditor.minimumWidth = 50;

        hookContextMenu();

        grid.setContentProvider(new IGridContentProvider() {
            public Point getSize()
            {
                return new Point(100, 100);
            }

            public Object[] getElements(Object inputElement)
            {
                return null;
            }

            public void dispose()
            {
            }

            public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
            {
            }
        });
        grid.setContentLabelProvider(new LabelProvider() {
            @Override
            public Image getImage(Object element)
            {
                return null;
            }

            @Override
            public String getText(Object element)
            {
                return "CELL " + element;
            }
        });
        grid.setColumnLabelProvider(new LabelProvider() {
            @Override
            public Image getImage(Object element)
            {
                return null;
            }

            @Override
            public String getText(Object element)
            {
                return "COLUMN " + element;
            }
        });
    }

    public void dispose()
    {
        this.clearGrid();
        super.dispose();
    }

    public void handleEvent(Event event)
    {
        switch (event.type) {
            case SWT.SetData: {
                lazyRow.item = (GridItem) event.data;
                lazyRow.index = event.index;
                if (dataProvider != null) {
                    dataProvider.fillRowData(lazyRow);
                }
                break;
            }
            case SWT.KeyDown:
                switch (event.keyCode) {
                    case SWT.CR:
                        openCellViewer(true);
                        break;
                    default:
                        return;
                }
                break;
            case SWT.MouseDoubleClick:
                openCellViewer(false);
                break;
            case SWT.MouseDown:
                cancelInlineEditor();
                break;
            case SWT.FocusIn:
                registerActions(true);
                break;
            case SWT.FocusOut:
                registerActions(false);
                break;
        }
    }

    public GridColumn getColumn(int index)
    {
        return curColumns.get(index);
    }

    public int getColumnsNum()
    {
        return curColumns.size();
    }

    public GridColumn addColumn(String text, String toolTipText, Image image)
    {
        GridColumn column = new GridColumn(grid, SWT.NONE);
        column.setText(text);
        if (toolTipText != null) {
            column.setHeaderTooltip(toolTipText);
        }
        if (image != null) {
            column.setImage(image);
        }

        curColumns.add(column);
        return column;
    }

    public void reinitState()
    {
        cancelInlineEditor();
        // Repack columns
        if (curColumns.size() == 1) {
            curColumns.get(0).setWidth(grid.getSize().x);
        } else {
            for (GridColumn curColumn : curColumns) {
                curColumn.pack();
                if (curColumn.getWidth() > MAX_DEF_COLUMN_WIDTH) {
                    curColumn.setWidth(MAX_DEF_COLUMN_WIDTH);
                }
            }
        }
    }

    private void clearColumns()
    {
        if (!curColumns.isEmpty()) {
            for (GridColumn column : curColumns) {
                column.dispose();
            }
            curColumns.clear();
        }
    }

    public int getVisibleRowsCount()
    {
        Rectangle clientArea = grid.getClientArea();
        int itemHeight = grid.getItemHeight();
        int count = (clientArea.height - grid.getHeaderHeight() + itemHeight - 1) / itemHeight;
        if (count == 0) {
            count = 1;
        }
        return count;
    }

    public void clearGrid()
    {
        //spreadsheet.setSelection(new int[0]);

        cancelInlineEditor();
        grid.removeAll();
        this.clearColumns();
    }

    private void copySelectionToClipboard()
    {
        String lineSeparator = System.getProperty("line.separator");
        List<Integer> colsSelected = new ArrayList<Integer>();
        int firstCol = Integer.MAX_VALUE, lastCol = Integer.MIN_VALUE;
        int firstRow = Integer.MAX_VALUE;
        List<GridPos> selection = getSelection();
        for (GridPos pos : selection) {
            if (firstCol > pos.col) {
                firstCol = pos.col;
            }
            if (lastCol < pos.col) {
                lastCol = pos.col;
            }
            if (firstRow > pos.row) {
                firstRow = pos.row;
            }
            if (!colsSelected.contains(pos.col)) {
                colsSelected.add(pos.col);
            }
        }
        StringBuilder tdt = new StringBuilder();
        int prevRow = firstRow;
        int prevCol = firstCol;
        for (GridPos pos : selection) {
            GridItem tableItem = grid.getItem(pos.row);
            if (pos.row > prevRow) {
                if (prevCol < lastCol) {
                    for (int i = prevCol; i < lastCol; i++) {
                        tdt.append("\t");
                    }
                }
                tdt.append(lineSeparator);
                prevRow = pos.row;
                prevCol = firstCol;
            }
            if (pos.col > prevCol) {
                for (int i = 0; i < pos.col - prevCol; i++) {
                    tdt.append("\t");
                }
                prevCol = pos.col;
            }
            String text = tableItem.getText(pos.col);
            tdt.append(text == null ? "" : text);
        }
        TextTransfer textTransfer = TextTransfer.getInstance();
        clipboard.setContents(
            new Object[]{tdt.toString()},
            new Transfer[]{textTransfer});
    }

    private void hookContextMenu()
    {
        MenuManager menuMgr = new MenuManager();
        Menu menu = menuMgr.createContextMenu(grid);
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager)
            {
                IAction copyAction = new Action("Copy selection") {
                    public void run()
                    {
                        copySelectionToClipboard();
                    }
                };
                copyAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.COPY);
                copyAction.setId(IWorkbenchActionDefinitionIds.COPY);
                IAction selectAllAction = new Action("Select All") {
                    public void run()
                    {
                        grid.selectAll();
                    }
                };
                copyAction.setEnabled(grid.getCellSelectionCount() > 0);
                manager.add(copyAction);
                manager.add(selectAllAction);
                manager.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
            }
        });
        menuMgr.setRemoveAllWhenShown(true);
        grid.setMenu(menu);
        site.registerContextMenu(menuMgr, selectionProvider);
    }

    public void openCellViewer(boolean inline)
    {
        if (dataProvider == null) {
            return;
        }
        // The control that will be the editor must be a child of the Table
        Point focusCell = grid.getFocusCell();
        //GridPos pos = getPosFromPoint(event.x, event.y);
        if (focusCell == null || focusCell.y < 0 || focusCell.x < 0) {
            return;
        }
        if (!dataProvider.isEditable() || !dataProvider.isCellEditable(focusCell.x, focusCell.y)) {
            return;
        }
        GridItem item = grid.getItem(focusCell.y);

        Composite placeholder = null;
        if (inline) {
            cancelInlineEditor();

            placeholder = new Composite(grid, SWT.BORDER);
            placeholder.setFont(grid.getFont());
            GridLayout layout = new GridLayout(1, true);
            layout.marginWidth = 0;
            layout.marginHeight = 0;
            layout.horizontalSpacing = 0;
            layout.verticalSpacing = 0;
            placeholder.setLayout(layout);

            GridData gd = new GridData(GridData.FILL_BOTH);
            gd.horizontalIndent = 0;
            gd.verticalIndent = 0;
            gd.grabExcessHorizontalSpace = true;
            gd.grabExcessVerticalSpace = true;
            placeholder.setLayoutData(gd);
        }
        lazyRow.index = focusCell.y;
        lazyRow.column = focusCell.x;
        lazyRow.item = item;
        boolean editSuccess = dataProvider.showCellEditor(lazyRow, inline, placeholder);
        if (inline) {
            if (editSuccess) {
                int minHeight, minWidth;
                Point editorSize = placeholder.computeSize(SWT.DEFAULT, SWT.DEFAULT);
                minHeight = editorSize.y;
                minWidth = editorSize.x;
                if (minWidth > MAX_INLINE_EDIT_WITH) {
                    minWidth = MAX_INLINE_EDIT_WITH;
                }
                tableEditor.minimumHeight = minHeight;// + placeholder.getBorderWidth() * 2;//placeholder.getBounds().height;
                tableEditor.minimumWidth = minWidth;
/*
                if (pos.row == 0) {
                    tableEditor.verticalAlignment = SWT.TOP;
                } else {
                    tableEditor.verticalAlignment = SWT.CENTER;
                }
*/
                tableEditor.setEditor(placeholder, item, focusCell.x);
            } else {
                // No editor was created so just drop placeholder
                placeholder.dispose();
            }
        }
    }

    public void cancelInlineEditor()
    {
        Control oldEditor = tableEditor.getEditor();
        if (oldEditor != null) oldEditor.dispose();
    }

    public int getItemCount()
    {
        return grid.getItemCount();
    }

    public void setItemCount(int count)
    {
        grid.setItemCount(count);
    }

    public int getColumnsCount()
    {
        return grid.getColumnCount();
    }

    private void registerActions(boolean register)
    {
        IHandlerService service = (IHandlerService) site.getService(IHandlerService.class);
        for (ActionInfo actionInfo : actionsInfo) {
            if (register) {
                assert (actionInfo.handlerActivation == null);
                ActionHandler handler = new ActionHandler(actionInfo.action);
                actionInfo.handlerActivation = service.activateHandler(
                    actionInfo.action.getActionDefinitionId(),
                    handler);
            } else {
                assert (actionInfo.handlerActivation != null);
                service.deactivateHandler(actionInfo.handlerActivation);
                actionInfo.handlerActivation = null;
            }
            // TODO: want to remove but can't
            // where one editor page have many controls each with its own behavior
            if (register) {
                site.getKeyBindingService().registerAction(actionInfo.action);
            } else {
                site.getKeyBindingService().unregisterAction(actionInfo.action);
            }
        }
    }

    public void redrawGrid()
    {
        Rectangle bounds = grid.getBounds();
        grid.redraw(bounds.x, bounds.y, bounds.width, bounds.height, true);
    }

    private static class ActionInfo {
        IAction action;
        IHandlerActivation handlerActivation;

        private ActionInfo(IAction action)
        {
            this.action = action;
        }
    }

    private abstract class GridAction extends Action {
        GridAction(String actionId)
        {
            setActionDefinitionId(actionId);
        }

        public abstract void run();
    }

    private class CursorMoveAction extends GridAction {
        private CursorMoveAction(String actionId)
        {
            super(actionId);
        }

        public void run()
        {
            Event event = new Event();
            event.doit = true;
            String actionId = getActionDefinitionId();
            boolean keepSelection = (event.stateMask & SWT.SHIFT) != 0;
            if (actionId.equals(ITextEditorActionDefinitionIds.LINE_START)) {
                shiftCursor(-grid.getColumnCount(), 0, keepSelection);
            } else if (actionId.equals(ITextEditorActionDefinitionIds.LINE_END)) {
                shiftCursor(grid.getColumnCount(), 0, keepSelection);
            } else if (actionId.equals(ITextEditorActionDefinitionIds.TEXT_START)) {
                shiftCursor(-grid.getColumnCount(), -grid.getItemCount(), keepSelection);
            } else if (actionId.equals(ITextEditorActionDefinitionIds.TEXT_END)) {
                shiftCursor(grid.getColumnCount(), grid.getItemCount(), keepSelection);
            }
        }
    }

    private class LazyGridRow implements IGridRowData {

        private GridItem item;
        private int index;
        private int column;

        public int getIndex()
        {
            return index;
        }

        public int getColumn()
        {
            return column;
        }

        public void setImage(int column, Image image)
        {
            item.setImage(column, image);
        }

        public String getText(int column)
        {
            return item.getText(column);
        }

        public void setText(int column, String text)
        {
            item.setText(column, text);
        }

        public void setHeaderText(String text)
        {
            item.setHeaderText(text);
        }

        public void setHeaderImage(Image image)
        {
            item.setHeaderImage(image);
        }

        public void setModified(int column, boolean modified)
        {
            item.setBackground(column, modified ? backgroundModified : backgroundNormal);
        }

        public Object getData()
        {
            return item.getData();
        }

        public void setData(Object data)
        {
            item.setData(data);
        }

    }

}
