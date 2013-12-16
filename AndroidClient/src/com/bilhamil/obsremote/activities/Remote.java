package com.bilhamil.obsremote.activities;

import java.util.ArrayList;

import com.bilhamil.obsremote.OBSRemoteApplication;
import com.bilhamil.obsremote.R;
import com.bilhamil.obsremote.RemoteUpdateListener;
import com.bilhamil.obsremote.WebSocketService;
import com.bilhamil.obsremote.R.layout;
import com.bilhamil.obsremote.R.menu;
import com.bilhamil.obsremote.WebSocketService.LocalBinder;
import com.bilhamil.obsremote.messages.ResponseHandler;
import com.bilhamil.obsremote.messages.requests.GetSceneList;
import com.bilhamil.obsremote.messages.requests.GetStreamingStatus;
import com.bilhamil.obsremote.messages.requests.SetCurrentScene;
import com.bilhamil.obsremote.messages.requests.StartStopStreaming;
import com.bilhamil.obsremote.messages.responses.GetSceneListResponse;
import com.bilhamil.obsremote.messages.responses.Response;
import com.bilhamil.obsremote.messages.responses.StreamStatusResponse;
import com.bilhamil.obsremote.messages.updates.StreamStatus;
import com.bilhamil.obsremote.messages.util.Scene;

import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class Remote extends FragmentActivity implements RemoteUpdateListener 
{

    private SceneAdapter sceneAdapter;
    private ArrayList<Scene> scenes;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.activity_remote);
        
        /* setup scene adapter */
        sceneAdapter = new SceneAdapter(this, new ArrayList<Scene>());
        ListView sceneView = (ListView)findViewById(R.id.ScenesListView);
        sceneView.setAdapter(sceneAdapter);
        
        ColorDrawable darkgray = new ColorDrawable(this.getResources().getColor(R.color.darkgray));
        sceneView.setDivider(darkgray);
        sceneView.setDividerHeight(3);
    }

    protected void onStart()
    {
        super.onStart();
        
        //hide start/stop button until after setup
        Button toggleStreamingButton = (Button) findViewById(R.id.startstopbutton);
        toggleStreamingButton.setVisibility(View.INVISIBLE);
        
        /* bind the service */
        Intent intent = new Intent(this, WebSocketService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        
    }
    
    @Override
    protected void onStop()
    {
        super.onStop();
        unbindService(mConnection);
    }
    
    @Override
    protected void onDestroy()
    {
        super.onDestroy();        
    }
    
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            getApp().service = binder.getService();
            getApp().service.addUpdateListener(Remote.this);
            
            if(getApp().service.isConnected())
            {
                if(getApp().service.needsAuth() && !getApp().service.authenticated())
                    AuthDialogFragment.startAuthentication(Remote.this,getApp());
                else
                    initialSetup();
            }
            else
            {
                getApp().service.connect();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            getApp().service.removeUpdateListener(Remote.this);
            getApp().service = null;
            
            finish();
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.remote, menu);
        return true;
    }
    
    public void initialSetup()
    {
        updateStreamStatus();
        
        updateScenes();
    }

    private void updateStreamStatus()
    {
        /* Get stream status */
        getApp().service.sendRequest(new GetStreamingStatus(), new ResponseHandler()
        {
            
            @Override
            public void handleResponse(Response resp, String jsonMessage)
            {
                StreamStatusResponse ssResp = getApp().getGson().fromJson(jsonMessage, StreamStatusResponse.class);
                
                updateStreaming(ssResp.streaming, ssResp.previewOnly);
            }
        });
    }

    private void updateScenes()
    {
        /* Get scenes */
        getApp().service.sendRequest(new GetSceneList(), new ResponseHandler()
        {
            
            @Override
            public void handleResponse(Response resp, String jsonMessage)
            {
                if(resp.isOk())
                {
                    GetSceneListResponse scenesResp = (GetSceneListResponse)getApp().getGson().fromJson(jsonMessage, GetSceneListResponse.class);
                    
                    Remote.this.scenes = scenesResp.scenes;
                    
                    sceneAdapter.setScenes(scenes);
                    sceneAdapter.setCurrentScene(scenesResp.currentScene);
                }
            }
        });
    }
    
    public class SceneAdapter extends ArrayAdapter<Scene>
    {
        public String currentScene = "";
        
        public SceneAdapter(Context context,  ArrayList<Scene> scenes)
        {
            super(context, R.layout.scene_item, R.id.scenename, scenes);
        }
        
        public void setCurrentScene(String scene)
        {
            this.currentScene = scene;
            
            this.notifyDataSetChanged();
        }
        
        public void setScenes(ArrayList<Scene> scenes)
        {
            this.clear();
            for(Scene scene: scenes)
            {
                this.add(scene);
            }
        }
        
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View view = super.getView(position, convertView, parent);
            
            String sceneName = this.getItem(position).name;
            
            if(sceneName.equals(currentScene))
            {
                view.setBackgroundResource(R.drawable.sceneselected);
                view.setOnClickListener(null);
            }
            else
            {
                view.setBackgroundResource(R.drawable.sceneunselected);
                OnClickListener listener = new OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        String sceneName = ((TextView)v.findViewById(R.id.scenename)).getText().toString();
                        
                        getApp().service.sendRequest(new SetCurrentScene(sceneName));
                    }
                };
                
                view.setOnClickListener(listener);
            }
                        
            return view;
        }        
    }
    
    public void updateStreaming(boolean streaming, boolean previewOnly)
    {
        WebSocketService serv = getApp().service;
        
        serv.streaming = streaming;
        serv.previewOnly = previewOnly;
        
        Button toggleStreamingButton = (Button) findViewById(R.id.startstopbutton);
        LinearLayout statsPanel = (LinearLayout) findViewById(R.id.statspanel);
        
        toggleStreamingButton.setVisibility(View.VISIBLE);
        
        if(serv.streaming)
        {
            toggleStreamingButton.setText(R.string.stopstreaming);
            statsPanel.setVisibility(View.VISIBLE);
        }
        else
        {
            toggleStreamingButton.setText(R.string.startstreaming);
            statsPanel.setVisibility(View.GONE);
        }
    }
    
    public void startStopStreaming(View view)
    {
        getApp().service.sendRequest(new StartStopStreaming());
    }
    
    public OBSRemoteApplication getApp()
    {
        return (OBSRemoteApplication)getApplicationContext();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        /* Finish immediately on back press */
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            finish();
            getApp().service.disconnect();
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public void onConnectionAuthenticated()
    {
        initialSetup();
    }

    @Override
    public void onConnectionClosed(int code, String reason)
    {
        this.finish();
    }

    @Override
    public void onStreamStarting(boolean previewOnly)
    {
        this.updateStreaming(true, false);
    }

    @Override
    public void onStreamStopping()
    {
        this.updateStreaming(false, false);
    }

    @Override
    public void onStreamStatus(StreamStatus status)
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onFailedAuthentication(String message)
    {
        AuthDialogFragment.startAuthentication(Remote.this,getApp(), message);
    }

    @Override
    public void onNeedsAuthentication()
    {
        AuthDialogFragment.startAuthentication(Remote.this,getApp());
    }

    public static int strainToColor(float strain)
    {
        int green = 255;
        if(strain > 50.0)
        {
            green = (int)(((50.0-(strain-50.0))/50.0)*255.0);
        }
        
        float red = strain / 50;
        if(red > 1.0)
        {
            red = 1.0f;
        }
        
        red = red * 255;
        
        return Color.rgb((int)red, green, 0);
        
    }
    
    @Override
    public void notifyStreamStatusUpdate(int totalTimeStreaming, int fps,
            float strain, int numDroppedFrames, int numTotalFrames, int bps)
    {
        TextView droppedFrames = (TextView)findViewById(R.id.droppedValue);
        TextView timeStreaming = (TextView)findViewById(R.id.timeValue);
        TextView bitrate = (TextView)findViewById(R.id.bitrateValue);
        TextView fpsLbl = (TextView)findViewById(R.id.fpsValue);
        
        fpsLbl.setText(fps + "");
        
        int sec = totalTimeStreaming / 1000;
        
        timeStreaming.setText(String.format("%02d", sec / 3600) + ":" + 
                                String.format("%02d", (sec % 3600) / 60) + ":" + 
                                String.format("%02d", sec % 60));
        
        droppedFrames.setText(numDroppedFrames + "(" + 
                              String.format("%.2f", ((float)numDroppedFrames) / numTotalFrames * 100) + 
                              "%)");
        
        bitrate.setText(bps * 8 / 1000 + " kbps");
        
        bitrate.setTextColor(strainToColor(strain));
    }

    @Override
    public void notifySceneSwitch(String sceneName)
    {
        this.sceneAdapter.setCurrentScene(sceneName);
    }

    @Override
    public void notifyScenesChanged()
    {
        this.updateScenes();
    }
    
}