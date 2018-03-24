
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private TextView currentX, currentY, currentZ, maxX, maxY, maxZ, maxMagnitudeAccView, accidentDetectionFlagView, dBValueView, changeL, changeW;


    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float lastX, lastY, lastZ;
    private float deltaX;
    private float deltaY;
    private float deltaZ;
    private float deltaXMax;
    private float deltaYMax;
    private float deltaZMax;
    private float magnitudeAccMax;
    private float magnitudeAcc;

    LocationManager lm;
    Criteria kr;
    Location loc;
    String najlepszyDostawca;
    double difference, l1, l2, l3, l4;
    float speed;
   // private float l5,l6,l7,l8;

    private boolean accidentDetectionFlag;

    public static final float G = SensorManager.GRAVITY_EARTH;
    public static final double REFRENCE_AMPLITUDE = Math.pow(10, Math.exp(-7));
    public static final float magnitudeAccThreshold = 1 * SensorManager.GRAVITY_EARTH;
    public static final int dBValueThreshold = 60;

    private static final String SERVER_URL = "http://104.131.161.226:8000/api/incidents/";
    private static final String KEY_TOKEN = "Authorization";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_MAX_ACCELERATION_MAGNITUDE = "max_acceleration_magnitude";

    private double dBValue;

    private MediaRecorder mRecorder;
    private Thread runner;

    private static double mEMA = 0.0;
    static final private double EMA_FILTER = 0.6;

    final Runnable updater = new Runnable() {
        public void run() {
            updateTv();
        }
    };
    final Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            //  we dont have an accelerometer!
        }

        if (runner == null) {
            runner = new Thread() {
                public void run() {
                    while (runner != null) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                        mHandler.post(updater);
                    }
                }
            };
            runner.start();
        }
        kr = new Criteria();
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        refresh();

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            ;

        lm.requestLocationUpdates(najlepszyDostawca, 1000, 1, this);
    }

    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        startRecorder();
    }

    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        stopRecorder();
    }

    public void initializeViews() {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);

        changeL = (TextView) findViewById(R.id.changeL);
        changeW = (TextView) findViewById(R.id.changeW);

        maxX = (TextView) findViewById(R.id.maxX);
        maxY = (TextView) findViewById(R.id.maxY);
        maxZ = (TextView) findViewById(R.id.maxZ);

        accidentDetectionFlagView = (TextView) findViewById(R.id.detectionFlag);
        maxMagnitudeAccView = (TextView) findViewById(R.id.maxMagnitudeAcc);
        dBValueView = (TextView) findViewById(R.id.statusDB);
    }

    public void displayCleanValues() {
        currentX.setText("0.0");
        currentY.setText("0.0");
        currentZ.setText("0.0");
    }

    // display the current x,y,z accelerometer values
    public void displayCurrentValues() {
        currentX.setText(Float.toString(deltaX));
        currentY.setText(Float.toString(deltaY));
        currentZ.setText(Float.toString(deltaZ));
    }

    // display the max x,y,z accelerometer values
    public void displayMaxValues() {
        if (accidentDetectionFlag) {
            accidentDetectionFlagView.setText("ACCIDENT DETECTED");
            accidentDetectionFlagView.setTextColor(Color.RED);
        }
        if (deltaX > deltaXMax) {
            deltaXMax = deltaX;
            maxX.setText(Float.toString(deltaXMax));
        }
        if (deltaY > deltaYMax) {
            deltaYMax = deltaY;
            maxY.setText(Float.toString(deltaYMax));
        }
        if (deltaZ > deltaZMax) {
            deltaZMax = deltaZ;
            maxZ.setText(Float.toString(deltaZMax));
        }
//        if (magnitudeAcc > magnitudeAccMax) {
//            magnitudeAccMax = magnitudeAcc;
        maxMagnitudeAccView.setText(Float.toString(magnitudeAcc));
//        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {

        displayCleanValues();
        displayCurrentValues();
        displayMaxValues();

//        final float alpha = 0.8f;
//
//        float[] gravity = new float[]{0, 0, 0};
//
//        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
//        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
//        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
//
//        float[] linear_acceleration = new float[]{0, 0, 0};
//
//        linear_acceleration[0] = event.values[0] - gravity[0];
//        linear_acceleration[1] = event.values[1] - gravity[1];
//        linear_acceleration[2] = event.values[2] - gravity[2];

        magnitudeAcc = ((float) Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])) - G;

        if (magnitudeAcc > magnitudeAccThreshold && dBValue > dBValueThreshold) {
            if (l3 == l1) {
                if (l2 == l4) {

                    accidentDetectionFlag = true;
                    sendData();
                }
            }
        }

        // get the change of the x,y,z values of the accelerometer
        deltaX = Math.abs(lastX - event.values[0]);
        deltaY = Math.abs(lastY - event.values[1]);
        deltaZ = Math.abs(lastZ - event.values[2]);

        // if the change is below 2, it is just plain noise
        if (deltaX < 2)
            deltaX = 0;
        if (deltaY < 2)
            deltaY = 0;
        if (deltaZ < 2)
            deltaZ = 0;

        // set the last know values of x,y,z
        lastX = event.values[0];
        lastY = event.values[1];
        lastZ = event.values[2];

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void sendData() {
        final String token = "Token 8f12919ffe3caa4eb20a594dc4bc460765fc9423";
        final String longitude = String.valueOf(loc.getLongitude());
        final String latitude = String.valueOf(loc.getLatitude());
        final String volume = String.valueOf(dBValue);
        final String max_acceleration_magnitude = String.valueOf(magnitudeAcc);

        StringRequest stringRequest = new StringRequest(Request.Method.POST, SERVER_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
//                        Toast.makeText(MainActivity.this, response, Toast.LENGTH_LONG).show();
//                        System.out.println(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
//                        Toast.makeText(MainActivity.this, error.toString(), Toast.LENGTH_LONG).show();
                        String message = null;
                        if (volleyError instanceof NetworkError) {
                            message = "Cannot connect to Internet...Please check your connection!";
                        } else if (volleyError instanceof ServerError) {
                            message = "The server could not be found. Please try again after some time!!";
                        } else if (volleyError instanceof AuthFailureError) {
                            message = "Cannot connect to Internet...Please check your connection!";
                        } else if (volleyError instanceof ParseError) {
                            message = "Parsing error! Please try again after some time!!";
                        } else if (volleyError instanceof NoConnectionError) {
                            message = "Cannot connect to Internet...Please check your connection!";
                        } else if (volleyError instanceof TimeoutError) {
                            message = "Connection TimeOut! Please check your internet connection.";
                        }
                        System.out.println(message);
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put(KEY_LONGITUDE, longitude);
                params.put(KEY_LATITUDE, latitude);
                params.put(KEY_VOLUME, volume);
                params.put(KEY_MAX_ACCELERATION_MAGNITUDE, max_acceleration_magnitude);
                return params;
            }


            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put(KEY_TOKEN, token);
                headers.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                return headers;
            }

        };

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(stringRequest);
    }


    public void startRecorder() {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("/dev/null");
            try {
                mRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mRecorder.start();
        }

    }

    public void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    public void updateTv() {
        dBValue = soundDb(REFRENCE_AMPLITUDE);
        dBValueView.setText(String.format("%.2f dB", dBValue));
    }

    public double soundDb(double ampl) {
        return 20 * Math.log10(getAmplitudeEMA() / ampl);
    }

    public double getAmplitude() {
        if (mRecorder != null)
            return (mRecorder.getMaxAmplitude());
        else
            return 0;
    }

    public double getAmplitudeEMA() {
        double amp = getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return mEMA;
    }

    private void refresh() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            ;

        najlepszyDostawca = lm.getBestProvider(kr, true);
        loc = lm.getLastKnownLocation(najlepszyDostawca);

    }

    public void displayLocalization() {
        changeL.setText(Double.toString(loc.getLongitude()));
        l3 = loc.getLongitude();
        //l7 = double.toFloat(l3);
        changeW.setText(Double.toString(loc.getLatitude()));
        l4 = loc.getLatitude();
        //l8 = double.toFloat(l4);
    }



    @Override
    public void onLocationChanged(Location location) {
        refresh();
        displayLocalization();
        locationSpeed();
        l1 = loc.getLongitude();
        l2 = loc.getLatitude();
    }

    float locationSpeed() {
        if(loc.hasSpeed()) {
            speed = loc.getSpeed();
        }
        return speed;
    }

    //LocationListener
    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub

    }
}
