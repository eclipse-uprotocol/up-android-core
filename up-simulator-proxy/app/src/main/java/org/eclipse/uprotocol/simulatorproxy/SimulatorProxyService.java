package org.eclipse.uprotocol.simulatorproxy;


import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.cloudevent.serialize.Base64ProtobufSerializer;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.simulatorproxy.utils.Constants;
import org.eclipse.uprotocol.simulatorproxy.utils.Utils;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimulatorProxyService extends Service {

    public static final String LOG_TAG = "AndroidProxy";

    private static final String CHANNEL_ID = "AndroidProxyServiceChannel";
    private static final UEntity AP_ENTITY = UEntity.newBuilder().setName("android.proxy").setVersionMajor(1).build();
    private static final UListener mUListener = SimulatorProxyService::handleMessage;
    @SuppressLint("StaticFieldLeak")
    static Context context;
    private static USubscription.Stub mUSubscriptionStub;
    private static UPClient mUPClient;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private ServerSocket serverSocket;
    private Thread serverThread;

    public static @NonNull UStatus logStatus(@NonNull String method, @NonNull UStatus status, Object... args) {
        println(isOk(status) ? Log.INFO : Log.ERROR, LOG_TAG, status(method, status, args));

        return status;
    }

    public static void println(int priority, String tag, String message) {
        switch (priority) {
            case Log.ASSERT, Log.ERROR -> Log.e(tag, message);
            case Log.WARN -> Log.w(tag, message);
            case Log.INFO -> Log.i(tag, message);
            case Log.DEBUG -> Log.d(tag, message);
            default -> Log.v(tag, message);
        }
    }

    @SuppressWarnings("SameParameterValue")
    public static CompletableFuture<UStatus> subscribe(@NonNull UUri topic, Socket clientSocket) {
        final CompletionStage<SubscriptionResponse> subscribeStage = mUSubscriptionStub.subscribe(SubscriptionRequest.newBuilder().setTopic(topic).setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.getUri()).build()).build()).whenComplete((response, exception) -> {
            if (exception != null) { // Communication failure
                final UStatus status = toStatus(exception);
                logStatus("subscribe", status, Key.TOPIC, stringify(topic));

            }
        });
        return registerListener(topic).thenCombine(subscribeStage, (registerStatus, subscriptionResponse) -> {
            if (!isOk(registerStatus)) {
                return registerStatus;
            }
            String topic_uri = LongUriSerializer.instance().serialize(topic);
            ArrayList<Socket> arr = new ArrayList<>();
            if (Constants.TOPIC_SOCKET_LIST.containsKey(topic_uri)) {
                arr = Constants.TOPIC_SOCKET_LIST.get(topic_uri);
            }
            if (arr != null && !arr.contains(clientSocket)) {
                arr.add(clientSocket);
            }
            Constants.TOPIC_SOCKET_LIST.put(topic_uri, arr);
            final SubscriptionStatus status = subscriptionResponse.getStatus();
            logStatus("subscribe", buildStatus(status.getCode(), status.getMessage()), Key.TOPIC, stringify(topic), Key.STATE, status.getState());
            return UStatus.newBuilder().setCode(status.getCode()).setMessage(status.getMessage()).build();
        });
    }

    public static @NonNull CompletableFuture<UStatus> registerListener(@NonNull UUri topic) {
        return CompletableFuture.supplyAsync(() -> {

            final UStatus status = mUPClient.registerListener(topic, mUListener);
            return logStatus("registerListener", status, Key.TOPIC, stringify(topic));
        });
    }

    static synchronized void sendStatusToHost(Socket mClientSocket, UStatus status, String action) {
        JSONObject jsonObj = new JSONObject();
        try {
            PrintWriter wr = new PrintWriter(mClientSocket.getOutputStream());
            String serializedMsg = Base64ProtobufSerializer.deserialize(status.toByteArray());
            jsonObj.put(Constants.ACTION, action);
            jsonObj.put(Constants.ACTION_DATA, serializedMsg);
            Log.d(LOG_TAG, "Sending " + action + " to host: " + jsonObj);
            wr.println(jsonObj);
            wr.flush();
        } catch (JSONException | IOException e) {
            Log.e(LOG_TAG, Objects.requireNonNullElse(e.getMessage(), "Exception occurs while sending " + action + " to host"));
        }
    }

    static synchronized void sendRpcResponseToHost(Socket clientSocket, UMessage msg) {
        JSONObject jsonObj = new JSONObject();
        String serializedMsg = Base64ProtobufSerializer.deserialize(msg.toByteArray());
        try {
            jsonObj.put(Constants.ACTION, Constants.ACTION_RPC_RESPONSE);
            jsonObj.put(Constants.ACTION_DATA, serializedMsg);
            try {
                PrintWriter wr = new PrintWriter(clientSocket.getOutputStream());
                wr.println(jsonObj);
                wr.flush();
                Log.d(LOG_TAG, "Sending rpc response to host: " + jsonObj);

            } catch (IOException e) {
                e.printStackTrace();
                // Log the exception and continue with the next socket
                Log.e(LOG_TAG, "Exception occurs while sending rpc response to host", e);
            }

        } catch (JSONException e) {
            Log.e(LOG_TAG, Objects.requireNonNullElse(e.getMessage(), "Exception occurs while sending topic status to host"));
        }
    }

    static void sendTopicUpdateToHost(UMessage umessage) {
        String topic = LongUriSerializer.instance().serialize(umessage.getSource());
        JSONObject jsonObj = new JSONObject();
        String serializedMsg = Base64ProtobufSerializer.deserialize(umessage.toByteArray());
        try {
            jsonObj.put(Constants.ACTION, Constants.UPDATE_TOPIC);
            jsonObj.put(Constants.ACTION_DATA, serializedMsg);
            List<Socket> socketList = Constants.TOPIC_SOCKET_LIST.get(topic);
            if (socketList != null) {
                socketList.forEach(socket -> {
                    try {
                        PrintWriter wr = new PrintWriter(socket.getOutputStream());
                        wr.println(jsonObj);
                        wr.flush();
                        Log.d(LOG_TAG, "Sending topic update to host: " + jsonObj);

                    } catch (IOException e) {
                        e.printStackTrace();
                        // Log the exception and continue with the next socket
                        Log.e(LOG_TAG, "Exception occurs while sending topic update to host", e);
                    }
                });
            }

        } catch (JSONException e) {
            Log.e(LOG_TAG, Objects.requireNonNullElse(e.getMessage(), "Exception occurs while sending topic status to host"));
        }
    }

    public static void handleMessage(@NonNull UUri source, @NonNull UPayload payload, @NonNull UAttributes attributes) {
        UMessage umsg = UMessage.newBuilder().setSource(source).setAttributes(attributes).setPayload(payload).build();
        sendTopicUpdateToHost(umsg);

    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        createNotificationChannel();

        mUPClient = UPClient.create(getApplicationContext(), AP_ENTITY, mExecutor, (client, ready) -> {
            if (ready) {
                Log.i(LOG_TAG, join(Key.EVENT, "Simulator proxy client connected"));
            } else {
                Log.w(LOG_TAG, join(Key.EVENT, "up client unexpectedly disconnected"));
            }
        });
        mUSubscriptionStub = USubscription.newStub(mUPClient);
        mUPClient.connect().thenCompose(status -> {
            logStatus("Simulator Proxy up client connect", status);
            return isOk(status) ? CompletableFuture.completedFuture(status) : CompletableFuture.failedFuture(new UStatusException(status));
        });

        startServer();
        Utils.readResourceCatalog(this);


    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();
        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(6095); // Use your desired port
                while (!Thread.currentThread().isInterrupted()) {
                    Socket socket = serverSocket.accept();
                    CommunicationThread commThread = new CommunicationThread(socket);
                    new Thread(commThread).start();
                }
            } catch (IOException e) {
                Log.e("SocketServerService", "Error starting server: " + e.getMessage());
            }
        });
        serverThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    private void stopServer() {
        if (serverThread != null) {
            serverThread.interrupt();
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e("SocketServerService", "Error stopping server: " + e.getMessage());
            }
        }
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "Android Proxy Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        builder = new Notification.Builder(this, CHANNEL_ID);

        return builder.setContentTitle("Foreground Service").setContentText("Running...").build();
    }

    public @NonNull CompletableFuture<UStatus> unregisterListener(@NonNull UUri topic) {
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUPClient.unregisterListener(topic, mUListener);
            return logStatus("unregisterListener", status, Key.TOPIC, stringify(topic));
        });
    }

    @SuppressWarnings("SameParameterValue")
    public CompletableFuture<UStatus> unsubscribe(@NonNull UUri topic) {
        final CompletionStage<UStatus> unsubscribeStage = mUSubscriptionStub.unsubscribe(UnsubscribeRequest.newBuilder().setTopic(topic).setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.getUri()).build()).build()).whenComplete((status, exception) -> {
            if (exception != null) { // Communication failure
                status = toStatus(exception);
                logStatus("unsubscribe", status, Key.TOPIC, stringify(topic));
            }
        });
        return unregisterListener(topic).thenCombine(unsubscribeStage, (unregisterStatus, unsubscribeStatus) -> {
            if (!isOk(unregisterStatus)) {
                return unregisterStatus;
            }
            return logStatus("unsubscribe", unsubscribeStatus, Key.TOPIC, stringify(topic));
        });
    }

    private static class CommunicationThread implements Runnable {
        private static final int BUFFER_SIZE = 32767; // Adjust the buffer size as needed

        private final Socket clientSocket;
        private InputStream inputStream;

        CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                this.inputStream = clientSocket.getInputStream();
            } catch (IOException e) {
                Log.e("SocketServerService", "Error creating communication thread: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes;

            try {
                while ((bytes = inputStream.read(buffer)) != -1) {
                    String receivedData = new String(buffer, 0, bytes);
                    // Handle received data as needed
                    Log.d("SocketServerService", "Received data: " + receivedData);
                    try {
                        JSONObject jsonObj = new JSONObject(receivedData);
                        String action = jsonObj.getString("action");
                        String data = jsonObj.getString("data");

                        switch (action) {
                            case Constants.ACTION_PUBLISH -> performSend(data);
                            case Constants.ACTION_START_SERVICE -> startVehicleService(data);
                            case Constants.ACTION_SUBSCRIBE -> performSubscribe(data);
                            case Constants.ACTION_REGISTER_RPC -> performRegisterRPC(data);
                            case Constants.ACTION_RPC_RESPONSE -> performRpcResponse(data);
                            case Constants.ACTION_INVOKE_METHOD -> performInvokeMethod(data);

                            default -> {
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
//                        throw new RuntimeException(e);
                    }


                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("SocketServerService", "Error during communication: " + e.getMessage());
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    Log.e("SocketServerService", "Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void performInvokeMethod(String data) {
            byte[] umsgBytes = Base64ProtobufSerializer.serialize(data);
            try {
                UMessage message = UMessage.parseFrom(umsgBytes);
                CompletionStage<UMessage> payloadCompletionStage = mUPClient.invokeMethod(message.getSource(), message.getPayload(), CallOptions.DEFAULT);
                payloadCompletionStage.whenComplete((responseData, exception) -> {
                    Log.i(LOG_TAG, "received response");
                    if (exception != null) {
                        UStatus status = toStatus(exception);

                        Log.e(LOG_TAG, "Failed to get response "+status.getMessage());

                        return;
                    }

                    UAttributes reqAttr = responseData.getAttributes();
                    UMessage responseMsg = responseData.toBuilder().setAttributes(reqAttr.toBuilder().setReqid(message.getAttributes().getId()).build()).build();

                    sendRpcResponseToHost(this.clientSocket, responseMsg);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }


        private void performRpcResponse(String data) {
            byte[] umsgBytes = Base64ProtobufSerializer.serialize(data);
            try {
                UMessage message = UMessage.parseFrom(umsgBytes);
                String methodUri = LongUriSerializer.instance().serialize(message.getSource());
                //set rpc response to bus
                if (Constants.COMPLETE_FUTURE_REQ_RES.containsKey(methodUri)) {

                    CompletableFuture<UPayload> future = Constants.COMPLETE_FUTURE_REQ_RES.get(methodUri);
                    if (future != null) {
                        future.complete(message.getPayload());
                    }
                }


            } catch (InvalidProtocolBufferException ex) {
                ex.printStackTrace();
            }
        }

        private void performRegisterRPC(String data) {
            byte[] uriBytes = Base64ProtobufSerializer.serialize(data);
            UUri uUri = UUri.getDefaultInstance();
            try {
                uUri = UUri.parseFrom(uriBytes);
                BaseService serviceClass = Constants.ENTITY_BASESERVICE.get(uUri.getEntity().getName());
                if (serviceClass != null) {
                    UStatus uStatus = serviceClass.registerMethod(uUri);
                    if (uStatus.getCode() == UCode.OK) {
                        //save socket to send received rpc request to mock service
                        Constants.RPC_SOCKET_LIST.put(LongUriSerializer.instance().serialize(uUri), this.clientSocket);
                    }
                    //write status to client socket
                    sendStatusToHost(this.clientSocket, uStatus, Constants.STATUS_REGISTER_RPC);

                } else {
                    Log.i(LOG_TAG, "Can't register rpc, Android service not found for entity: " + uUri.getEntity().getName());


                }


            } catch (Exception ex) {
                Log.e(LOG_TAG, Objects.requireNonNullElse(ex.getMessage(), "Exception occurs while registering rpc/method uri " + uUri.getEntity().getName()));
            }
        }

        private void performSubscribe(String data) {
            byte[] uriBytes = Base64ProtobufSerializer.serialize(data);
            UUri uUri = UUri.getDefaultInstance();
            try {
                uUri = UUri.parseFrom(uriBytes);
                CompletableFuture<UStatus> uStatusCompletableFuture = subscribe(uUri, this.clientSocket);
                uStatusCompletableFuture.whenComplete((uStatus, throwable) -> {
                    if (throwable != null) {
                        Log.e(LOG_TAG, "Failed to perform subscribe", throwable);
                        uStatus = toStatus(throwable);

                    }
                    sendStatusToHost(clientSocket, uStatus, Constants.STATUS_SUBSCRIBE);
                });


            } catch (Exception ex) {
                Log.e(LOG_TAG, Objects.requireNonNullElse(ex.getMessage(), "Exception occurs while suscribing to topic " + uUri.getEntity().getName()));
            }
        }

        private void startVehicleService(String entity) {
            PackageManager packageManager = context.getPackageManager();
            Class<? extends Service> serviceClass = Constants.ENTITY_SERVICE_MAP.get(entity);
            if (serviceClass != null) {
                // For now its not needed but once we have the real service, we should disable all the service from the android proxy manifest and enable it only when
                // there is start service request from host. This is because if we enable it by default, then the real service
                // information will be replaced in ubus and then ubus wont be able to start the real vehicle service.

                /**
                 ComponentName componentName = new ComponentName(context, serviceClass);
                 packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                 **/
                Intent serviceIntent = new Intent(context, serviceClass);
                ContextCompat.startForegroundService(context, serviceIntent);
                Log.i(LOG_TAG, "Starting service for entity: " + entity);
                Constants.ENTITY_SOCKET.put(entity, this.clientSocket);
            } else {
                Log.i(LOG_TAG, "Android service not found for entity: " + entity);

            }
        }


        private void performSend(String data) {
            byte[] umsgBytes = Base64ProtobufSerializer.serialize(data);
            try {
                UMessage message = UMessage.parseFrom(umsgBytes);
                String entity = message.getSource().getEntity().getName();
                //perform send
                Log.i(LOG_TAG, "Call Publish api (send)");
                BaseService serviceClass = Constants.ENTITY_BASESERVICE.get(entity);
                if (serviceClass != null) {
                    UStatus status = serviceClass.publish(message);
                    //write status to client socket
                    sendStatusToHost(clientSocket, status, Constants.STATUS_PUBLISH);

                } else {
                    Log.i(LOG_TAG, "Can't publish, Android service not found for entity: " + entity);


                }

            } catch (InvalidProtocolBufferException ex) {
                ex.printStackTrace();
            }


        }
    }

}
