/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *	Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *	Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *  
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ponysdk.ui.server.list;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ponysdk.core.activity.AbstractActivity;
import com.ponysdk.core.event.EventBus;
import com.ponysdk.core.event.EventBusAware;
import com.ponysdk.impl.theme.PonySDKTheme;
import com.ponysdk.ui.server.basic.IsPWidget;
import com.ponysdk.ui.server.basic.PAcceptsOneWidget;
import com.ponysdk.ui.server.basic.PSimplePanel;
import com.ponysdk.ui.server.list.event.RowDeletedEvent;
import com.ponysdk.ui.server.list.event.RowInsertedEvent;

public class SimpleListActivity<T> extends AbstractActivity {

    protected SimpleListView listView;

    private List<ListColumnDescriptor<T, ?>> listFields;

    private final String ID;

    private String debugID;

    private List<T> data;

    private Map<Integer, Integer> subListSizeByFather = new HashMap<Integer, Integer>();

    private final EventBus eventBus;

    private int colCount;

    public SimpleListActivity(String ID, SimpleListView listView, List<ListColumnDescriptor<T, ?>> listFields, EventBus eventBus) {
        this.ID = ID;
        this.listFields = listFields;
        this.listView = listView;
        this.eventBus = eventBus;
        buildHeaders();
    }

    public String getID() {
        return ID;
    }

    public void addCustomDescriptor(ListColumnDescriptor<T, ?> customDescriptor) {
        listFields.add(customDescriptor);
        listView.removeCellStyle(0, colCount, PonySDKTheme.FILL_COLUMN);
        listView.setColumns(colCount);
        listView.addWidget(customDescriptor.renderHeader(), colCount, 0);
        final PSimplePanel widget = new PSimplePanel();
        listView.addCellStyle(0, colCount + 1, PonySDKTheme.FILL_COLUMN);

        int rowIndex = 1;
        for (final T t : data) {
            final IsPWidget renderCell = customDescriptor.renderCell(rowIndex, t);
            listView.addWidget(renderCell, colCount, rowIndex);
            listView.removeCellStyle(rowIndex, colCount - 1, PonySDKTheme.FILL_COLUMN);
            listView.addCellStyle(rowIndex, colCount, PonySDKTheme.FILL_COLUMN);
            rowIndex++;
        }
        if (customDescriptor.getHeaderCellRenderer() instanceof EventBusAware) {
            ((EventBusAware) customDescriptor.getHeaderCellRenderer()).setEventBus(eventBus);
        }
        colCount++;
        listView.addWidget(widget, colCount, 0);
    }

    private void buildHeaders() {
        colCount = 0;

        listView.setColumns(listFields.size());
        // listView.insertRow(0);

        for (final ListColumnDescriptor<T, ?> field : listFields) {
            listView.addWidget(field.renderHeader(), colCount, 0);
            if (field.getWidth() != null) {
                listView.setColumnWidth(colCount, field.getWidth());
            }
            colCount++;
        }
        final PSimplePanel widget = new PSimplePanel();
        listView.addWidget(widget, colCount, 0);
        listView.addCellStyle(0, colCount, PonySDKTheme.FILL_COLUMN);
    }

    public void rebuild(List<ListColumnDescriptor<T, ?>> listFields, List<T> data) {
        reset();
        this.listView.removeRow(0);
        this.listFields = listFields;
        buildHeaders();
        setData(data);
    }

    private void reset() {
        subListSizeByFather.clear();
        listView.clearList();
        data = null;
    }

    public void setData(List<T> data) {
        assert listView != null : "Cannot remove field before binding listView";
        reset();
        this.data = data;
        int rowIndex = 1;
        for (final T t : data) {
            int col = 0;
            // listView.insertRow(rowCount);
            for (final ListColumnDescriptor<T, ?> field : listFields) {
                final IsPWidget renderCell = field.renderCell(rowIndex, t);

                if (debugID != null) {
                    String headerCaption;
                    if (field.getHeaderCellRenderer().getCaption() != null) {
                        headerCaption = field.getHeaderCellRenderer().getCaption();
                    } else {
                        headerCaption = String.valueOf(col);
                    }
                    renderCell.asWidget().ensureDebugId(debugID + "[" + rowIndex + "][" + headerCaption + "]");
                }
                listView.addWidget(renderCell, col++, rowIndex);
            }
            listView.addWidget(new PSimplePanel(), col, rowIndex);
            listView.addRowStyle(rowIndex, PonySDKTheme.SIMPLELIST_ROW);
            rowIndex++;
        }
    }

    public void insertSubList(int row, java.util.List<T> datas) {
        if (datas.isEmpty()) return;
        int subRow = row + 1;
        for (final T data : datas) {
            listView.insertRow(subRow); // create a new row after
            listView.addRowStyle(subRow, PonySDKTheme.SIMPLELIST_SUBROW);
            int col = 0;
            for (final ListColumnDescriptor<T, ?> field : listFields) {
                listView.addWidget(field.renderSubCell(subRow, data), col++, subRow);
            }
            subRow++;
        }
        updateSubListOnRowInserted(row, datas.size());
        eventBus.fireEvent(new RowInsertedEvent(this, row, datas.size()));
    }

    public void removeSubList(int fatherRow) {
        final Integer subListSize = subListSizeByFather.remove(fatherRow);
        if (subListSize != null) {
            for (int i = 1; i <= subListSize; i++) {
                listView.removeRow(fatherRow + 1);
            }
            eventBus.fireEvent(new RowDeletedEvent(this, fatherRow, subListSize));
            updateSubListOnRowDeleted(fatherRow, subListSize);
        }
    }

    private void updateSubListOnRowInserted(int row, int insertedRowCount) {
        final Map<Integer, Integer> temp = new HashMap<Integer, Integer>();
        for (final Map.Entry<Integer, Integer> entry : subListSizeByFather.entrySet()) {
            final int size = entry.getValue();
            int subRow = entry.getKey();
            if (subRow > row) {
                subRow += insertedRowCount;
            }
            temp.put(subRow, size);
        }
        subListSizeByFather = temp;
        subListSizeByFather.put(row, insertedRowCount);
    }

    private void updateSubListOnRowDeleted(int row, int deletedRowCount) {
        final Map<Integer, Integer> temp = new HashMap<Integer, Integer>();
        for (final Map.Entry<Integer, Integer> entry : subListSizeByFather.entrySet()) {
            final int size = entry.getValue();
            int subRow = entry.getKey();
            if (subRow > row + deletedRowCount) {
                subRow -= deletedRowCount;
            }
            temp.put(subRow, size);
        }
        subListSizeByFather = temp;
    }

    public void selectRow(int row) {
        listView.selectRow(row);
    }

    public void unSelectRow(int row) {
        listView.unSelectRow(row);
    }

    public List<T> getData() {
        return data;
    }

    @Override
    public void start(PAcceptsOneWidget container) {
        container.setWidget(listView);
    }

    public void ensureDebugId(String debugID) {
        this.debugID = debugID;
    }
}
