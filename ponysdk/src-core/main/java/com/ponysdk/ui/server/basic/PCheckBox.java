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

package com.ponysdk.ui.server.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.ponysdk.core.instruction.AddHandler;
import com.ponysdk.core.stm.Txn;
import com.ponysdk.core.tools.Objects;
import com.ponysdk.ui.server.basic.event.PValueChangeEvent;
import com.ponysdk.ui.server.basic.event.PValueChangeHandler;
import com.ponysdk.ui.terminal.Dictionnary.HANDLER;
import com.ponysdk.ui.terminal.Dictionnary.PROPERTY;
import com.ponysdk.ui.terminal.WidgetType;

/**
 * A standard check box widget. This class also serves as a base class for {@link PRadioButton}. <h3>CSS Style
 * Rules</h3>
 * <dl>
 * <dt>.gwt-CheckBox</dt>
 * <dd>the outer element</dd>
 * <dt>.gwt-CheckBox-disabled</dt>
 * <dd>applied when PCheckbox is disabled</dd>
 * </dl>
 */
public class PCheckBox extends PButtonBase implements HasPValue<Boolean>, PValueChangeHandler<Boolean> {

    private final List<PValueChangeHandler<Boolean>> handlers = new ArrayList<PValueChangeHandler<Boolean>>();

    private Boolean value = Boolean.FALSE;

    /**
     * Creates a check box with no label.
     */
    public PCheckBox() {
        this(null);
    }

    /**
     * Creates a check box with the specified text label.
     * 
     * @param label
     *            the check box's label
     */
    public PCheckBox(final String label) {
        setText(label);
        final AddHandler addHandler = new AddHandler(getID(), HANDLER.KEY_.BOOLEAN_VALUE_CHANGE_HANDLER);
        Txn.get().getTxnContext().save(addHandler);
    }

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.CHECKBOX;
    }

    @Override
    public void addValueChangeHandler(final PValueChangeHandler<Boolean> handler) {
        handlers.add(handler);
    }

    @Override
    public void removeValueChangeHandler(final PValueChangeHandler<Boolean> handler) {
        handlers.remove(handler);
    }

    @Override
    public Collection<PValueChangeHandler<Boolean>> getValueChangeHandlers() {
        return Collections.unmodifiableCollection(handlers);
    }

    /**
     * Determines whether this check box is currently checked.
     * 
     * @return <code>true</code> if the check box is checked, false otherwise. Will not return null
     */
    @Override
    public Boolean getValue() {
        return value;
    }

    /**
     * Checks or unchecks the check box.
     * 
     * @param value
     *            true to check, false to uncheck; null value implies false
     */
    @Override
    public void setValue(final Boolean value) {
        if (Objects.equals(this.value, value)) return;
        this.value = value;
        saveUpdate(PROPERTY.VALUE, this.value);
    }

    @Override
    public void onValueChange(final PValueChangeEvent<Boolean> event) {
        this.value = event.getValue();
        for (final PValueChangeHandler<Boolean> handler : handlers) {
            handler.onValueChange(event);
        }
    }

    @Override
    public void onClientData(final JSONObject e) throws JSONException {
        if (e.getString(HANDLER.KEY).equals(HANDLER.KEY_.BOOLEAN_VALUE_CHANGE_HANDLER)) {
            onValueChange(new PValueChangeEvent<Boolean>(this, e.getBoolean(PROPERTY.VALUE)));
        } else {
            super.onClientData(e);
        }
    }

}
