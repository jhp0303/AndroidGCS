package com.jangin.navermap;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;

import java.util.Arrays;

public class MainActivity extends FragmentActivity
        implements OnMapReadyCallback {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment)fm.findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mapFragment).commit();
        }

        mapFragment.getMapAsync(this);

        LatLng coord = new LatLng(35.945282, 126.68215299999997);

        Toast.makeText(this,
                "위도: " + coord.latitude + ", 경도: " + coord.longitude,
                Toast.LENGTH_SHORT).show();


    }

    @UiThread
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        naverMap.setMapType(NaverMap.MapType.Terrain);
    }
}
