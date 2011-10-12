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
package com.ponysdk.ui.terminal;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.InvocationException;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.ponysdk.ui.terminal.exception.PonySessionException;
import com.ponysdk.ui.terminal.instruction.Add;
import com.ponysdk.ui.terminal.instruction.AddHandler;
import com.ponysdk.ui.terminal.instruction.Close;
import com.ponysdk.ui.terminal.instruction.Create;
import com.ponysdk.ui.terminal.instruction.EventInstruction;
import com.ponysdk.ui.terminal.instruction.GC;
import com.ponysdk.ui.terminal.instruction.Instruction;
import com.ponysdk.ui.terminal.instruction.Remove;
import com.ponysdk.ui.terminal.instruction.Update;
import com.ponysdk.ui.terminal.ui.PTRootLayoutPanel;

public class UIBuilder implements ValueChangeHandler<String>, UIService {

    private final PonyEngineServiceAsync ponyService = GWT.create(PonyEngineService.class);

    private final Map<String, AddonFactory> addonByKey = new HashMap<String, AddonFactory>();
    private final UIFactory uiFactory = new UIFactory();

    private final Map<Long, UIObject> objectByID = new HashMap<Long, UIObject>();

    private SimplePanel loadingMessageBox;
    private PopupPanel communicationErrorMessagePanel;

    private Timer timer;
    private int numberOfrequestInProgress;

    private Frame frame;

    private boolean updateMode;

    private final List<Instruction> stackedInstructions = new ArrayList<Instruction>();

    private boolean pendingClose;

    public static long sessionID;

    public UIBuilder(long ID) {
        UIBuilder.sessionID = ID;
        History.addValueChangeHandler(this);

        final AddonList addonList = GWT.create(PonyAddonList.class);

        final List<AddonFactory> addonFactoryList = addonList.getAddonFactoryList();
        for (final AddonFactory addonFactory : addonFactoryList) {
            addonByKey.put(addonFactory.getSignature(), addonFactory);
        }
    }

    public void init() {

        final PTRootLayoutPanel rootPanel = new PTRootLayoutPanel();
        rootPanel.create(null, null);

        objectByID.put(0l, rootPanel);

        loadingMessageBox = new SimplePanel();

        communicationErrorMessagePanel = new PopupPanel(false, true);
        communicationErrorMessagePanel.setStyleName("pony-CommunicationErrorMessage");
        communicationErrorMessagePanel.setGlassEnabled(true);
        communicationErrorMessagePanel.setAnimationEnabled(true);

        RootPanel.get().add(loadingMessageBox);

        loadingMessageBox.setStyleName("pony-LoadingMessageBox");
        loadingMessageBox.getElement().getStyle().setVisibility(Visibility.HIDDEN);
        loadingMessageBox.getElement().setInnerText("Loading ...");

        /* Frame for stream resource handling */
        frame = new Frame();

        frame.setWidth("0px");
        frame.setHeight("0px");
        frame.getElement().getStyle().setProperty("visibility", "hidden");
        frame.getElement().getStyle().setProperty("position", "fixed");

        // hide loading component
        final Widget w = RootPanel.get("loading");
        if (w == null) {
            Window.alert("Include splash screen html element into your index.html with id=\"loading\"");
        } else {
            w.setSize("0px", "0px");
            w.setVisible(false);
        }

        RootPanel.get().add(frame);
    }

    public void update(List<Instruction> instructions) {
        updateMode = true;
        try {

            for (final Instruction instruction : instructions) {
                if (instruction instanceof Close) {
                    pendingClose = true;
                    triggerEvent(instruction);

                    final ScheduledCommand command = new ScheduledCommand() {

                        @Override
                        public void execute() {
                            reload();
                        }
                    };

                    Scheduler.get().scheduleDeferred(command);
                    return;
                }
                if (instruction instanceof Create) {
                    final Create create = (Create) instruction;

                    if (WidgetType.COOKIE.equals(create.getWidgetType())) {
                        final String name = create.getMainProperty().getStringProperty(PropertyKey.NAME);
                        final String value = create.getMainProperty().getStringProperty(PropertyKey.VALUE);
                        final Property expires = create.getMainProperty().getChildProperty(PropertyKey.COOKIE_EXPIRE);
                        if (expires != null) {
                            final Long time = expires.getLongValue();
                            final Date date = new Date(time);
                            Cookies.setCookie(name, value, date);
                        } else {
                            Cookies.setCookie(name, value);
                        }
                    } else {

                        // GWT.log("Create: " + create.getObjectID() + ", " + create.getWidgetType().name());
                        UIObject uiObject;
                        if (create.getAddOnSignature() != null) {
                            final AddonFactory addonFactory = addonByKey.get(create.getAddOnSignature());
                            uiObject = addonFactory.newAddon();
                            if (uiObject == null) {
                                Window.alert("UIBuilder: AddOn factory not found, type : " + create.getWidgetType());
                            }
                            uiObject.create(create, this);
                        } else {
                            uiObject = uiFactory.newUIObject(this, create);
                            uiObject.create(create, this);
                        }

                        objectByID.put(create.getObjectID(), uiObject);
                    }

                } else if (instruction instanceof Add) {

                    final Add add = (Add) instruction;
                    // GWT.log("Add: " + add.getObjectID() + ", " + add.getParentID() + ", " + add.getMainProperty());

                    final UIObject uiObject = objectByID.get(add.getParentID());
                    uiObject.add(add, this);

                } else if (instruction instanceof AddHandler) {

                    final AddHandler addHandler = (AddHandler) instruction;
                    // GWT.log("AddHandler: " + addHandler.getType() + ", " + addHandler.getObjectID() + ", " + addHandler.getMainProperty());

                    if (HandlerType.STREAM_REQUEST_HANDLER.equals(addHandler.getType())) {
                        frame.setUrl(GWT.getModuleBaseURL() + "stream?" + "ponySessionID=" + UIBuilder.sessionID + "&" + PropertyKey.STREAM_REQUEST_ID.getKey() + "="
                                + addHandler.getMainProperty().getValue());
                    } else {
                        final UIObject uiObject = objectByID.get(addHandler.getObjectID());
                        uiObject.addHandler(addHandler, this);
                    }

                } else if (instruction instanceof Remove) {
                    final Remove remove = (Remove) instruction;
                    UIObject uiObject;

                    if (PropertyKey.COOKIE.equals(instruction.getMainProperty().getKey())) { // TODO nciaravola merge with PTCookie ?
                        Cookies.removeCookie(instruction.getMainProperty().getValue());
                    } else {
                        if (remove.getParentID() == -1)
                            uiObject = objectByID.get(remove.getObjectID());
                        else {
                            uiObject = objectByID.get(remove.getParentID());
                        }
                        uiObject.remove(remove, this);
                    }
                } else if (instruction instanceof GC) {
                    final GC remove = (GC) instruction;
                    GWT.log("GC: " + remove.getObjectID());
                    final UIObject uiObject = objectByID.remove(remove.getObjectID());
                    uiObject.gc(remove, this);
                } else if (instruction instanceof Update) {

                    final Update update = (Update) instruction;
                    // GWT.log("Update " + update.getMainProperty().getKey().getKey() + " / " + update.getMainProperty().getValue());

                    final UIObject uiObject = objectByID.get(update.getObjectID());
                    uiObject.update(update, this);

                } else if (instruction instanceof com.ponysdk.ui.terminal.instruction.History) {
                    final com.ponysdk.ui.terminal.instruction.History history = (com.ponysdk.ui.terminal.instruction.History) instruction;
                    final String oldToken = History.getToken();
                    if (oldToken != null && history.getToken().equals(oldToken)) {
                        History.fireCurrentHistoryState();
                    } else {
                        History.newItem(history.getToken(), true);
                    }
                }
            }
        } catch (final Throwable e) {
            GWT.log("Failed to process instruction", e);
        } finally {
            flushEvent();
            updateMode = false;
        }
    }

    private void updateTimer(Timer object, Property mainProperty) {
        object.scheduleRepeating(mainProperty.getIntValue());
    }

    public void stackEvent(Instruction instruction) {
        if (!updateMode)
            triggerEvent(instruction);
        else
            stackedInstructions.add(instruction);
    }

    public void flushEvent() {
        if (stackedInstructions.isEmpty())
            return;
        fireEvents(stackedInstructions);
    }

    private void fireEvents(final List<Instruction> instructions) {
        numberOfrequestInProgress++;
        if (timer == null) {
            timer = scheduleLoadingMessageBox();
        }
        ponyService.fireInstructions(sessionID, instructions, new AsyncCallback<List<Instruction>>() {

            @Override
            public void onFailure(Throwable caught) {
                if (pendingClose)
                    return;
                GWT.log("fireInstruction failed", caught);
                numberOfrequestInProgress--;
                hideLoadingMessageBox();
                instructions.clear();

                if (caught instanceof PonySessionException) {
                    reload();
                    return;
                }
                showCommunicationErrorMessage(caught);
            }

            @Override
            public void onSuccess(List<Instruction> result) {
                numberOfrequestInProgress--;
                hideLoadingMessageBox();
                instructions.clear();
                update(result);
            }
        });
    }

    @Override
    public void triggerEvent(Instruction instruction) {
        final List<Instruction> instructions = new ArrayList<Instruction>();
        instructions.add(instruction);
        fireEvents(instructions);
    }

    private Timer scheduleLoadingMessageBox() {
        final Timer timer = new Timer() {

            @Override
            public void run() {
                loadingMessageBox.getElement().getStyle().setVisibility(Visibility.VISIBLE);
            }
        };
        timer.schedule(500);
        return timer;
    }

    private void showCommunicationErrorMessage(Throwable caught) {
        final VerticalPanel content = new VerticalPanel();
        if (caught instanceof StatusCodeException) {
            final StatusCodeException exception = (StatusCodeException) caught;
            content.add(new HTML("Server connection failed <br/>Code : " + exception.getStatusCode() + "<br/>" + "cause : " + exception.getMessage()));
        } else if (caught instanceof InvocationException) {
            content.add(new HTML("Exception durring server invocation : " + caught.getMessage()));
        } else {
            content.add(new HTML("Failure : " + caught == null ? "" : caught.getMessage()));
        }

        final HorizontalPanel actionPanel = new HorizontalPanel();
        actionPanel.setSize("100%", "100%");

        final Anchor reloadAnchor = new Anchor("reload");
        reloadAnchor.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(ClickEvent event) {
                History.newItem("");
                reload();
            }
        });

        final Anchor closeAnchor = new Anchor("close");
        closeAnchor.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(ClickEvent event) {
                communicationErrorMessagePanel.hide();
            }
        });

        actionPanel.add(reloadAnchor);
        actionPanel.add(closeAnchor);

        content.add(actionPanel);

        communicationErrorMessagePanel.setWidget(content);
        communicationErrorMessagePanel.setPopupPositionAndShow(new PositionCallback() {

            @Override
            public void setPosition(int offsetWidth, int offsetHeight) {
                final int left = (Window.getClientWidth() - offsetWidth) >> 1;
                communicationErrorMessagePanel.setPopupPosition(left, 0);
            }
        });
    }

    protected void hideLoadingMessageBox() {
        if (numberOfrequestInProgress < 1) {
            timer.cancel();
            timer = null;
            loadingMessageBox.getElement().getStyle().setVisibility(Visibility.HIDDEN);
        }
    }

    @Override
    public void onValueChange(ValueChangeEvent<String> event) {
        if (event.getValue() != null && !event.getValue().isEmpty()) {
            final EventInstruction eventInstruction = new EventInstruction(-1, HandlerType.HISTORY);
            eventInstruction.setMainPropertyValue(PropertyKey.VALUE, event.getValue());
            stackEvent(eventInstruction);
        }
    }

    @Override
    public UIObject getUIObject(Long ID) {
        return objectByID.get(ID);
    }

    private native void reload() /*-{$wnd.location.reload();}-*/;

}