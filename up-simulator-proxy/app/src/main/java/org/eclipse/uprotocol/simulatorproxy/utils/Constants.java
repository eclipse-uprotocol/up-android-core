package org.eclipse.uprotocol.simulatorproxy.utils;

import android.app.Service;

import org.eclipse.uprotocol.simulatorproxy.BaseService;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UUri;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Constants {
    public static final HashMap<String, CompletableFuture<UPayload>> COMPLETE_FUTURE_REQ_RES = new HashMap<>();
    public static final HashMap<String, BaseService> ENTITY_BASESERVICE = new HashMap<>();
    public static final Map<String, Class<? extends BaseService>> ENTITY_SERVICE_MAP = new HashMap<>();
    public static final Map<String, Socket> ENTITY_SOCKET = new HashMap<>();
    public static final String ACTION = "action";
    public static final String ACTION_DATA = "data";
    public static final String STATUS_PUBLISH = "publish_status";
    public static final String UPDATE_TOPIC = "topic_update";
    public static final String STATUS_SUBSCRIBE = "subscribe_status";
    public static final String STATUS_REGISTER_RPC= "register_rpc_status";
    public static final String STATUS_SEND_RPC= "send_rpc_status";

    public static final String ACTION_START_SERVICE = "start_service";
    public static final String ACTION_PUBLISH = "publish";
    public static final String ACTION_RPC_REQUEST = "rpc_request";
    public static final String ACTION_RPC_RESPONSE ="rpc_response";
    public static final String ACTION_SUBSCRIBE = "subscribe";
    public static final String ACTION_REGISTER_RPC = "register_rpc";
    public static final String ACTION_INVOKE_METHOD = "send_rpc";
    public static final Map<String, ArrayList<Socket>> TOPIC_SOCKET_LIST = new HashMap<>();
    public static final Map<String, Socket> RPC_SOCKET_LIST = new HashMap<>();

    public static HashMap<String, String> RESOURCE_CATALOG = new HashMap<>();


}
