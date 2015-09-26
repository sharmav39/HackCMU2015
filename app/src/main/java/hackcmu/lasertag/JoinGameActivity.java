package hackcmu.lasertag;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AppIdentifier;
import com.google.android.gms.nearby.connection.AppMetadata;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JoinGameActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        Connections.MessageListener,
        Connections.EndpointDiscoveryListener {

    // Identify if the device is the host
    private boolean mIsHost = false;

    private String hostEndpointId = null;
    private String hostDeviceId = null;
    private String hostName = null;
    private GoogleApiClient mGoogleApiClient;
    private ArrayList<String> availableEndpointIds = new ArrayList<String>();
    private ArrayList<String> availableEndpointNames = new ArrayList<String>();

    private static int[] NETWORK_TYPES = {ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_ETHERNET};

    private AvailableGamesFragment availableGamesFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_game);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.CONNECTIONS_API)
                .build();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        availableGamesFragment = AvailableGamesFragment.newInstance();

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.available_games_fragment_holder, availableGamesFragment);
        transaction.commit();

    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }





    private boolean isConnectedToNetwork() {
        ConnectivityManager connManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        for (int networkType : NETWORK_TYPES) {
            NetworkInfo info = connManager.getNetworkInfo(networkType);
            if (info != null && info.isConnectedOrConnecting()) {
                return true;
            }
        }
        return false;
    }

    private void startDiscovery() {
        if (!isConnectedToNetwork()) {
            // Implement logic when device is not connected to a network
        }
        String serviceId = getString(R.string.service_id);

        // Set an appropriate timeout length in milliseconds
        long DISCOVER_TIMEOUT = 100000L;

        // Discover nearby apps that are advertising with the required service ID.
        Nearby.Connections.startDiscovery(mGoogleApiClient, serviceId, DISCOVER_TIMEOUT, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            // Device is discovering
                            Log.d("Client", "Now discovering.");
                        } else {
                            int statusCode = status.getStatusCode();
                            // Discovery failed - see statusCode for more details
                        }
                    }
                });
    }

    @Override
    public void onEndpointFound(final String endpointId, String deviceId,
                                String serviceId, final String endpointName) {
        // This device is discovering endpoints and has located an advertiser.
        // Write your logic to initiate a connection with the device at
        // the endpoint ID
        Toast.makeText(getApplicationContext(),
                "endPoint found: "+endpointId+", "+deviceId+", "+serviceId+", "+endpointName , Toast.LENGTH_LONG)
                .show();

        availableEndpointIds.add(endpointId);
        availableEndpointNames.add(endpointName);
        Log.d("Client", "Endpoint found - name is " + endpointName);

        availableGamesFragment.updateList(availableEndpointNames);
    }

    @Override
    public void onEndpointLost(String endpointId) {
        Log.d("Client", "onEndpointLost() called");
        // oh no the connection was lost do something
        int i = availableEndpointIds.indexOf(endpointId);
        availableEndpointIds.remove(i);
        availableEndpointNames.remove(i);

        availableGamesFragment.updateList(availableEndpointNames);
    }

    private void sendJson(JsonElement obj) {
        Gson gson = new Gson();
        Nearby.Connections.sendReliableMessage(
                mGoogleApiClient,
                hostEndpointId,
                gson.toJson(obj).getBytes()
        );
    }

    public void selectHost(int index) {
        connectTo(availableEndpointIds.get(index), availableEndpointNames.get(index));
    }

    private void connectTo(String endpointId, final String endpointName) {
        // Send a connection request to a remote endpoint. By passing 'null' for
        // the name, the Nearby Connections API will construct a default name
        // based on device model such as 'LGE Nexus 5'.
        Log.d("Client", "endpointId="+endpointId+", "+"endpointName="+endpointName);
        String myName = null;
        byte[] myPayload = null;
        Nearby.Connections.sendConnectionRequest(mGoogleApiClient, myName,
                endpointId, myPayload, new Connections.ConnectionResponseCallback() {
                    @Override
                    public void onConnectionResponse(String remoteEndpointId, Status status,
                                                     byte[] bytes) {
                        if (status.isSuccess()) {
                            // send message for testing purposes
                            byte[] payload = ("Hello, World! This is a message from client to the host named " + hostName).getBytes();
                            Nearby.Connections.sendReliableMessage(mGoogleApiClient, hostEndpointId, payload);
                            Log.d("Client", "Message sent.");
                        } else {
                            Log.d("Client", "Connection request unsuccessful.");
                        }
                    }
                }, this);
    }

    @Override
    public void onDisconnected(String endpointId) {
        // handle disconnection
        Log.d("Client", "onDisconnected() called");
    }

    @Override
    public void onMessageReceived(String endpointId, byte[] payload, boolean isReliable) {
        // Implement parsing logic to process message
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Client", "onConnectionSuspended() called");
        // add logic about handling suspended connection

        // Try to re-connect
        mGoogleApiClient.reconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        // connected now -- maybe do stuff
        startDiscovery();
        Log.d("Client", "Discovery started.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("Client", "onConnectionFailed() called");
        // connection failed -- do something maybe
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_join_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
