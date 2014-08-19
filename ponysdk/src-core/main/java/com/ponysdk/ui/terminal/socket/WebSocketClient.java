
package com.ponysdk.ui.terminal.socket;

public class WebSocketClient {

    final WebSocketCallback callback;

    private boolean responseReceived = false;

    public WebSocketClient(final WebSocketCallback callback) {
        this.callback = callback;
    }

    private final void onopen() {
        responseReceived = true;
        callback.connected();
    }

    private final void onclose() {
        responseReceived = true;
        callback.disconnected();
    }

    private final void onmessage(final String message) {
        responseReceived = true;
        callback.message(message);
    }

    private final void ontimeout() {
        if (!responseReceived) {
            callback.disconnected();
        }
    }

    public static native boolean isSupported()/*-{
                                              if (!$wnd.WebSocket) return false;
                                              return true;
                                              }-*/;

    public native void connect(String server) /*-{
                                              
                                              var that = this;
                                              if (!$wnd.WebSocket) {
                                                  alert("WebSocket connections not supported by this browser");
                                                  return;
                                              }

                                              that._ws = new $wnd.WebSocket(server);

                                              that._ws.onopen = function() {
                                                  if(!that._ws) {
                                                      return;
                                                  }
                                                  that.@com.ponysdk.ui.terminal.socket.WebSocketClient::onopen()();
                                              };

                                              that._ws.onmessage = function(response) {
                                                  if (response.data) {
                                                      that.@com.ponysdk.ui.terminal.socket.WebSocketClient::onmessage(Ljava/lang/String;)( response.data );
                                                  }
                                              };

                                              that._ws.onclose = function(m) {
                                                  that.@com.ponysdk.ui.terminal.socket.WebSocketClient::onclose()();
                                              };
                                              
                                              setTimeout(function() {
                                                  that.@com.ponysdk.ui.terminal.socket.WebSocketClient::ontimeout()();
                                              }, 3000);
                                              
                                              }-*/;

    public native void send(String message) /*-{
                                            if (this._ws) {
                                            this._ws.send(message);
                                            } else {
                                            alert("not connected!" + this._ws);
                                            }
                                            }-*/;

    public native void close() /*-{
                               this._ws.close();
                               }-*/;

}
