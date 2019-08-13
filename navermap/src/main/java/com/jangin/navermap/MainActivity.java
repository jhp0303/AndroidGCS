package com.jangin.navermap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.style.UpdateAppearance;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.mygcs.R;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;
import com.o3dr.services.android.lib.util.MathUtils;


import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.o3dr.services.android.lib.util.MathUtils.getArcInRadians;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, DroneListener, TowerListener, LinkListener {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;
    private static final String TAG = MainActivity.class.getSimpleName();

    MapFragment mNaverMapFragment = null;
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private NaverMap nMap;
    private RecyclerView recyclerView;

    private Spinner modeSelector;

    int takeoffAltitude = 0;

    final List<CardItem> dataList = new ArrayList<>();

    MyRecyclerAdapter adapter = new MyRecyclerAdapter(dataList);

    public void recyclerView(String b) {
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        final Runnable r = new Runnable() {
            public void run() {
                adapter.notifyDataSetChanged();
            }
        };
        handler.post(r);
        dataList.add(0, new CardItem(b));
        if (dataList.size() > 3) {
            for (int i = 4; i <= dataList.size(); i++) {
                dataList.remove(dataList.size() - 1);
                recyclerView.setAdapter(adapter);
            }
        }


        recyclerView.setAdapter(adapter);
        recyclerView.stopScroll();
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                return true;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
    }

    protected void UpdateItem() {
        Runnable updater = new Runnable() {

            @Override
            public void run() {
                if (dataList.size() > 0) {
                    dataList.remove(dataList.size() - 1);
                    Log.d("myLog", "하나 삭제");
                } else {
                    Log.d("myLog", "아무것도 없음");
                }
                recyclerView.setAdapter(adapter);
                adapter.notifyDataSetChanged();
            }
        };
        handler.post(updater);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        Log.d("myLog", "테스트 결과입니다.");

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }
        mNaverMapFragment.getMapAsync(this);

        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });


        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        this.recyclerView = findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        this.recyclerView.setLayoutManager(layoutManager);

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                UpdateItem();
            }
        };

        service.scheduleAtFixedRate(runnable, 0, 5, TimeUnit.SECONDS);

    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @UiThread
    @Override
    public void onMapReady(@NonNull final NaverMap naverMap) {
        naverMap.setMapType(NaverMap.MapType.Hybrid);
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        CameraPosition cameraPosition = naverMap.getCameraPosition();
        CameraUpdate cameraUpdate = CameraUpdate.toCameraPosition(cameraPosition);
        naverMap.moveCamera(cameraUpdate);
        nMap = naverMap;


        final Button buttonLockMove = (Button) findViewById(R.id.mapLockMove);
        final Button buttonLock = (Button) findViewById(R.id.mapLock);
        final Button buttonMove = (Button) findViewById(R.id.mapMove);
        final Button buttonselectMap = (Button) findViewById(R.id.selectMap);
        final Button buttonsatel = (Button) findViewById(R.id.satelliteMap);
        final Button buttontopo = (Button) findViewById(R.id.topographicMap);
        final Button buttongeneral = (Button) findViewById(R.id.generalMap);
        final Button buttonOnOff = (Button) findViewById(R.id.mapOnOff);
        final Button buttonmapOff = (Button) findViewById(R.id.mapOff);
        final Button buttonmapOn = (Button) findViewById(R.id.mapOn);
        final Button btnAltitude = (Button) findViewById(R.id.btnAlti);
        final Button buttonaltUp = (Button) findViewById(R.id.btnAltiUp);
        final Button buttonaltDown = (Button) findViewById(R.id.btnAltiDown);
        final UiSettings uiSettings = naverMap.getUiSettings();
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.CLEAR:

                        break;

                    case R.id.mapLockMove:
                        if (buttonLock.getVisibility() == View.VISIBLE) {
                            buttonLock.setVisibility(View.GONE);
                            buttonMove.setVisibility(View.GONE);
                        } else {
                            buttonLock.setVisibility(View.VISIBLE);
                            buttonMove.setVisibility(View.VISIBLE);
                        }
                        break;

                    case R.id.mapLock:
                        buttonLock.setVisibility(View.GONE);
                        buttonMove.setVisibility(View.GONE);
                        buttonLockMove.setText("맵 잠금");
                        recyclerView("맵이 잠겼습니다.");
                        uiSettings.setScrollGesturesEnabled(false);
                        break;

                    case R.id.mapMove:
                        buttonLock.setVisibility(View.GONE);
                        buttonMove.setVisibility(View.GONE);
                        buttonLockMove.setText("맵 이동");
                        recyclerView("맵잠금이 풀렸습니다.");
                        uiSettings.setScrollGesturesEnabled(true);
                        break;

                    case R.id.selectMap:
                        if (buttontopo.getVisibility() == View.VISIBLE) {
                            buttontopo.setVisibility(View.GONE);
                            buttongeneral.setVisibility(View.GONE);
                            buttonsatel.setVisibility(View.GONE);
                        } else {
                            buttontopo.setVisibility(View.VISIBLE);
                            buttongeneral.setVisibility(View.VISIBLE);
                            buttonsatel.setVisibility(View.VISIBLE);
                        }
                        break;

                    case R.id.satelliteMap:
                        buttontopo.setVisibility(View.GONE);
                        buttongeneral.setVisibility(View.GONE);
                        buttonsatel.setVisibility(View.GONE);
                        buttonselectMap.setText("위성지도");
                        recyclerView("위성지도로 변경 되었습니다.");
                        naverMap.setMapType(NaverMap.MapType.Satellite);
                        break;

                    case R.id.topographicMap:
                        buttontopo.setVisibility(View.GONE);
                        buttongeneral.setVisibility(View.GONE);
                        buttonsatel.setVisibility(View.GONE);
                        buttonselectMap.setText("지형도");
                        recyclerView("지형도로 변경 되었습니다.");
                        naverMap.setMapType(NaverMap.MapType.Terrain);
                        break;

                    case R.id.generalMap:
                        buttontopo.setVisibility(View.GONE);
                        buttongeneral.setVisibility(View.GONE);
                        buttonsatel.setVisibility(View.GONE);
                        buttonselectMap.setText("일반지도");
                        recyclerView("일반지도로 변경 되었습니다.");
                        naverMap.setMapType(NaverMap.MapType.Basic);
                        break;

                    case R.id.mapOnOff:
                        if (buttonmapOn.getVisibility() == View.VISIBLE) {
                            buttonmapOn.setVisibility(View.GONE);
                            buttonmapOff.setVisibility(View.GONE);
                        } else {
                            buttonmapOn.setVisibility(View.VISIBLE);
                            buttonmapOff.setVisibility(View.VISIBLE);
                        }
                        break;

                    case R.id.mapOff:
                        buttonmapOn.setVisibility(View.GONE);
                        buttonmapOff.setVisibility(View.GONE);
                        buttonOnOff.setText("지적도OFF");
                        recyclerView("지적도가 꺼졌습니다.");
                        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                        break;

                    case R.id.mapOn:
                        buttonmapOn.setVisibility(View.GONE);
                        buttonmapOff.setVisibility(View.GONE);
                        buttonOnOff.setText("지적도ON");
                        recyclerView("지적도가 켜졌습니다.");
                        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                        break;

                    case R.id.btnAlti:
                        if (buttonaltUp.getVisibility() == View.VISIBLE) {
                            buttonaltUp.setVisibility(View.GONE);
                            buttonaltDown.setVisibility(View.GONE);
                        } else {
                            buttonaltUp.setVisibility(View.VISIBLE);
                            buttonaltDown.setVisibility(View.VISIBLE);
                        }
                        break;

                    case R.id.btnAltiUp:
                        if (takeoffAltitude < 15) {
                            takeoffAltitude = takeoffAltitude + 3;
                            btnAltitude.setText(takeoffAltitude + "m");
                            recyclerView("이륙고도가" + takeoffAltitude + "M로 변경되었습니다.");
                        }else if (takeoffAltitude == 0 ) {
                        btnAltitude.setText("이륙고도");
                        recyclerView("이륙고도를 설정해주세요");
                    }

                        break;

                    case R.id.btnAltiDown:
                        if (takeoffAltitude > 0) {
                            takeoffAltitude = takeoffAltitude - 3;
                            btnAltitude.setText(takeoffAltitude + "M");
                            recyclerView("이륙고도가" + takeoffAltitude + "M로 변경되었습니다.");
                        }else if (takeoffAltitude == 0 ) {
                            btnAltitude.setText("이륙고도");
                            recyclerView("이륙고도를 설정해주세요");
                        }

                }
            }
        };
        buttonLockMove.setOnClickListener(listener);
        buttonLock.setOnClickListener(listener);
        buttonMove.setOnClickListener(listener);
        buttonselectMap.setOnClickListener(listener);
        buttonsatel.setOnClickListener(listener);
        buttontopo.setOnClickListener(listener);
        buttongeneral.setOnClickListener(listener);
        buttonOnOff.setOnClickListener(listener);
        buttonmapOff.setOnClickListener(listener);
        buttonmapOn.setOnClickListener(listener);
        btnAltitude.setOnClickListener(listener);
        buttonaltUp.setOnClickListener(listener);
        buttonaltDown.setOnClickListener(listener);


        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        naverMap.setOnMapLongClickListener((coord, point) -> {
            AlertDialog.Builder ad = new AlertDialog.Builder(this);
            ad.setTitle("알림");       // 제목 설정
            ad.setMessage("해당 좌표로 이동합니다.");   // 내용 설정
            // 확인 버튼 설정
            ad.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();     //닫기
                    // Event
                    guidedMode(point);
                    recyclerView("드론이 해당 좌표로 이동 중입니다.");
                }
            });
            // 취소 버튼 설정
            ad.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();     //닫기
                    // Event
                    Toast.makeText(getApplicationContext(), "You Click to No!!", Toast.LENGTH_SHORT).show();
                }
            });
            // 창 띄우기
            ad.show();
        });
    }

    public void guidedMode(LatLng latLng) {
        LatLng nowLatLng = new LatLng(latLng.latitude, latLng.longitude);
        LatLong nowLatLong = new LatLong(latLng.latitude, latLng.longitude);
        VehicleApi.getApi(this.drone).setVehicleMode((VehicleMode.COPTER_GUIDED));
        Marker nowmarker = new Marker();
        ArrayList<LatLng> nowMarkersLatLng = new ArrayList<>();
        nowMarkersLatLng.add(nowLatLng);
        nowmarker.setPosition(nowLatLng);
        nowmarker.setIcon(OverlayImage.fromResource(R.drawable.marker_24));
        nowmarker.setMap(nMap);

        ControlApi.getApi(this.drone).goTo(nowLatLong, true, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("해당 좌표로 이동합니다.");
                recyclerView("가이드 모드 : 해당 좌표로 이동");
            }

            @Override
            public void onError(int executionError) {
                alertUser("실패");
                recyclerView("가이드 모드 : 실패");
            }

            @Override
            public void onTimeout() {
                alertUser("타임아웃");
                recyclerView("가이드 모드 : 타임아웃");
            }
        });
    }


    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                recyclerView("드론이 연결 되었습니다.");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                recyclerView("드론 연결이 해제되었습니다.");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                updateArmButton();
                break;

            case AttributeEvent.GPS_POSITION:
                updateGps();
                updateArmButton();

                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;


            case AttributeEvent.GPS_COUNT:
                processGpsState();

            case AttributeEvent.BATTERY_UPDATED:
                updateVoltage();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYaw();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void updateYaw() {
        Attitude yawCondition = this.drone.getAttribute(AttributeType.ATTITUDE);
        double angle = yawCondition.getYaw();
        if (angle < 0) {
            angle = (float) (360 + angle);
        }
        Button voltageButton = (Button) findViewById(R.id.yaw);
        voltageButton.setText(String.format("Yaw " + "%3.0f", angle) + "deg");
    }

    private void updateVoltage() {
        Battery vehicleBattery = this.drone.getAttribute(AttributeType.BATTERY);
        vehicleBattery.getBatteryVoltage();
        Button voltageButton = (Button) findViewById(R.id.voltage);
        voltageButton.setText(String.format("전압 " + "%3.1f", vehicleBattery.getBatteryVoltage()) + "V");
    }

    private void processGpsState() {

        Gps vehicleGps = this.drone.getAttribute(AttributeType.GPS);
        vehicleGps.getSatellitesCount();
        Button satelliteButton = (Button) findViewById(R.id.Satellite);
        satelliteButton.setText(String.format("위성 " + vehicleGps.getSatellitesCount()));
    }

    protected void updateSpeed() {
        Button speedButton = (Button) findViewById(R.id.speed);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedButton.setText(String.format("속도 : " + "%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
                recyclerView("비행 모드가 변경 되었습니다.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
                recyclerView("비행모드 변경이 실패했습니다. 이유: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
                recyclerView("비행모드 변경 시간 초과.");
            }
        });
    }

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnARM);
        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE-OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    public void onArmButtonTap(View view) {
        final State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button btnAltitude = (Button) findViewById(R.id.btnAlti);
        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {

            int altitude = 0;
            if (takeoffAltitude == 3) {
                altitude = 3;
                Log.d("myLog", "고도3M");
            } else if (takeoffAltitude == 6) {
                altitude = 5;
                Log.d("myLog", "5M");
            } else if (takeoffAltitude == 9) {
                altitude = 8;
                Log.d("myLog", "8M");
            } else if (takeoffAltitude == 12) {
                altitude = 10;
                Log.d("myLog", "10M");
            }
            else if (takeoffAltitude == 15) {
                altitude = 15;
                Log.d("myLog", "15M");
            }

            // Take off
            ControlApi.getApi(this.drone).takeoff(altitude, new AbstractCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            AlertDialog.Builder ad = new AlertDialog.Builder(this);
            ad.setTitle("경고");       // 제목 설정
            ad.setMessage("시작시 프로펠러가 고속으로 회전합니다.");   // 내용 설정
            // 확인 버튼 설정
            ad.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();     //닫기
                    // Event
                    sidong();   //콜백부분이 문제가 되서 해당 펑션을 따로 빼서 함수명으로 불러옴
                }
            });
            // 취소 버튼 설정
            ad.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();     //닫기
                    // Event
                    Toast.makeText(getApplicationContext(), "You Click to No!!", Toast.LENGTH_SHORT).show();
                }
            });
            // 창 띄우기
            ad.show();
        }
    }

    public void sidong() {
        VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
            @Override
            public void onError(int executionError) {
                alertUser("Unable to arm vehicle.");
            }

            @Override
            public void onTimeout() {
                alertUser("Arming operation timed out.");
            }
        });
    }

    Marker marker = new Marker();
    final ArrayList<LatLng> markersLatLng = new ArrayList<>();
    final PolylineOverlay polyline = new PolylineOverlay();

    @UiThread
    protected void updateGps() {
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition();
        LatLng LatLng = new LatLng(vehiclePosition.getLatitude(), vehiclePosition.getLongitude());
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(LatLng);
        nMap.moveCamera(cameraUpdate);
        marker.setMap(null);
        marker.setPosition(LatLng);
        marker.setIcon(OverlayImage.fromResource(R.drawable.triangle_long));
        Attitude yawCondition = this.drone.getAttribute(AttributeType.ATTITUDE);
        double angle = yawCondition.getYaw();
        if (angle < 0) {
            angle = (360 + angle);
        }
        marker.setAngle((float) angle);
        marker.setMap(nMap);
        marker.setAnchor(new PointF(0.5f, 1));

        markersLatLng.add(LatLng);

        polyline.setCoords(markersLatLng);
        polyline.setColor(Color.rgb(0, 216, 255));
        polyline.setWidth(15);
        polyline.setMap(nMap);
    }

    public void clearButton(View v) {
        markersLatLng.clear();
        polyline.setMap(null);
        recyclerView("경로가 지워졌습니다.");
    }

    protected void updateAltitude() {
        Button altitudeTextView = (Button) findViewById(R.id.altitude);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("고도 :" + "%3.1f", droneAltitude.getAltitude()) + "m");
        double vehicleAltitude = droneAltitude.getAltitude();

        String tmp = Double.toString(droneAltitude.getAltitude());
        Log.d("Altitude", tmp);
    }

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText(getText(R.string.button_disconnect));
        } else {
            connectButton.setText(getText(R.string.button_connect));
        }
    }

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(params);
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch (connectionStatus.getStatusCode()) {
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras();
                String msg = null;
                if (extras != null) {
                    msg = extras.getString(LinkConnectionStatus.EXTRA_ERROR_MSG);
                }
                alertUser("Connection Failed:" + msg);
                recyclerView("연결 실패:" + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        recyclerView("DroneKit-Android가 연결 되었습니다.");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
        recyclerView("DroneKit-Android가 중단 되었습니다.");
    }

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }
}