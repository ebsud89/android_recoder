package com.onethefull.frequency.ui;

import static android.media.AudioTrack.getMinBufferSize;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.onethefull.frequency.R;
import com.onethefull.frequency.util.PreferenceManager;

import org.jtransforms.fft.DoubleFFT_1D;

import java.sql.Array;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import ca.uol.aig.fftpack.RealDoubleFFT;

public class AnalyzeActivity extends FragmentActivity implements OnClickListener {

    // Bitmap 이미지를 표시하기 위해 ImageView를 사용한다. 이 이미지는 현재 오디오 스트림에서 주파수들의 레벨을 나타낸다.
    // 이 레벨들을 그리려면 Bitmap에서 구성한 Canvas 객체와 Paint객체가 필요하다.
    private static final String TAG = AnalyzeActivity.class.getSimpleName();
    // play PCM Sound (genTone)
    private final int duration = 1; // seconds
    private final int sampleRate = 8000;
    private final int numSamples = duration * sampleRate;
    private final double freqOfTone = 6000; // hz
    private final byte pcm[] = new byte[2 * numSamples];
    //    int frequency = 8192; //주파수가 8192일 경우 4096 까지 측정이 가능함
    int frequency = 48000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    //    int blockSize = 4096; // 2048->1024개의 배열이 나옴. 배열 한 칸당 4hz의 범위를 포함하고 있음. //4096->배열
    //    2048이고 한칸당 2hz //배열 번호 1씩 증가-> hz는 2씩 증가한다.
    //배열이 40일때 hz는 80헤르츠를 가지고있다는것.
    int blockSize = 24000;
    DoubleFFT_1D fft = new DoubleFFT_1D(blockSize); //JTransform 라이브러리로 FFT 수행

    String scale2;
    //frequency -> 측정 주파수 대역으로 퓨리에 변환 시 f/2 만큼의 크기의 주파수를 분석 할 수 있음.
    //blockSize -> 한 분기마다 측정하는 사이즈로 double 배열로 저장 시 , b/2 개의 배열이 나옴. f/b -> 배열 하나에 할당되는 주파수 범위로
    // 8192/2048 -> 4Hz임

    ImageButton backButton;
    Button analyzeButton;
    Button chirpButton;
    TextView analyzeText;
    TextView chirpText;
    ImageButton settingButton;
    ImageButton testButton;
    ImageButton refreshButton;

    boolean started = false;
    boolean chirping = false;
    // RecordAudio는 여기에서 정의되는 내부 클래스로서 AsyncTask를 확장한다.
    RecordAudio recordTask;
    // PlayAudio는 여기에서 정의되는 내부 클래스로서 AsyncTask를 확장한다.
    PlayAudio playTask;
    ImageView imageView;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;
    BarChart chart;
    int chart_max_xrange = 20000;
    ArrayList xlabels = new ArrayList();
    ArrayList ylabels = new ArrayList();
    BarData data;
    TextView t0;
    TextView t1;
    TextView t2;
    // Detector View
    FrequencyDetector detector;
    TextView count_view;
    TextView timestamp_view;
    TableLayout detector_table_header;
    TableLayout detector_table_content;
    ArrayList<ArrayList<TextView>> tv_lists;
    ArrayList<TextView> check_lists;
    ArrayList<Integer> green_list;
    ArrayList<Integer> red_list;
    ArrayList<Integer> check_point = new ArrayList<Integer>();
    ArrayList<Integer> real_point = new ArrayList<Integer>();
    boolean real_flag = true;
    ArrayList<ArrayList<FrequencyData>> raw_list = new ArrayList<ArrayList<FrequencyData>>();
    ArrayList<ArrayList<Double>> diff_lists = new ArrayList<ArrayList<Double>>();
    int LASTEST_COL = 0;

    int TABLE_ROW = 10;
    int TABLE_COL = 12;
    //스레드 관련 부분 1
    // scaleThread scThr = new scaleThread();
    double[] mag = new double[blockSize / 2];
    Context context;
    // PreferenceManager
    private double START_FREQ = 0.0;
    private double END_FREQ = 0.0;
    private int DURATION_FREQ = 1;
    private int INTERVAL_FREQ = 3;
    private int CHIRP_SEQ = 0;
    private RealDoubleFFT transformer;
    private double sample[] = null;
    private byte[] generatedSnd = null;
    // play PCM sound (makeTone)
    private int sample_size = numSamples;
    private AudioTrack audioTrack = null;
    // sig_gen
    private PlayFrequencyAudio pfa;
    private AnalyzeFrequencyAudio afa;
    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_analyze);

        context = this;

        backButton = (ImageButton) findViewById(R.id.BackButton);
        backButton.setOnClickListener(this);

        analyzeButton = (Button) findViewById(R.id.AnalyzeButton);
        analyzeButton.setOnClickListener(this);

        analyzeText = (TextView) findViewById(R.id.AnalyzeText);

        chirpButton = (Button) findViewById(R.id.ChirpButton);
        chirpButton.setOnClickListener(this);

        chirpText = (TextView) findViewById(R.id.ChirpText);

        settingButton = (ImageButton) findViewById(R.id.SettingButton);
        settingButton.setOnClickListener(this);

        testButton = (ImageButton) findViewById(R.id.TestButton);
        testButton.setOnClickListener(this);

        refreshButton = (ImageButton) findViewById(R.id.RefreshButton);
        refreshButton.setOnClickListener(this);

        // PreferenceManager
        START_FREQ = (double) PreferenceManager.getInt(this, "start_freq");
        END_FREQ = (double) PreferenceManager.getInt(this, "end_freq");
        DURATION_FREQ = PreferenceManager.getInt(this, "duration_freq");
        INTERVAL_FREQ = PreferenceManager.getInt(this, "interval_freq");

        sample = new double[numSamples * DURATION_FREQ];
        generatedSnd = new byte[2 * numSamples * DURATION_FREQ];

        // RealDoubleFFT 클래스 컨스트럭터는 한번에 처리할 샘플들의 수를 받는다. 그리고 출력될 주파수 범위들의 수를 나타낸다.
        transformer = new RealDoubleFFT(blockSize);

        // ImageView 및 관련 객체 설정 부분
        // imageView = (ImageView) findViewById(R.id.ImageView01);
        bitmap = Bitmap.createBitmap((int) blockSize / 2, (int) 200, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        //  imageView.setImageBitmap(bitmap);

        t0 = (TextView) findViewById(R.id.HzText0);
        t1 = (TextView) findViewById(R.id.HzText1);
        t2 = (TextView) findViewById(R.id.HzText2);


        // Detector View
        detector = new FrequencyDetector();
        tv_lists = new ArrayList<ArrayList<TextView>>();
        check_lists = new ArrayList<TextView>();

        count_view = (TextView) findViewById(R.id.DetectorCount);
        timestamp_view = (TextView) findViewById(R.id.DetectorTimestamp);
        detector_table_header = (TableLayout) findViewById(R.id.DetectorTableHeader);
        detector_table_content = (TableLayout) findViewById(R.id.DetectorTableContent);

        check_point = new ArrayList<Integer>();
        detector.init();

        chart = (BarChart) findViewById(R.id.chart);
        YAxis leftYAxis = chart.getAxisLeft();
        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinValue(0);
        xAxis.setAxisMaxValue((float) 20000); // 기존 1024
        leftYAxis.setAxisMaxValue((float) 200);
        leftYAxis.setAxisMinValue(0);
        chart.getAxisRight().setEnabled(false);

        //chart 그리기
        int xChart = 0;
        //x축 라벨 추가
        //4096 / 16 =256 씩 16칸으로 할거임
        //for(int i=0; i<1024; i++){
        for (int i = 0; i < chart_max_xrange; i++) {
            xlabels.add(Integer.toString(xChart));
            xChart = xChart + 1;
        }

        //초기 데이터
//        ylabels.add(new BarEntry(2.2f,0));
//        ylabels.add(new BarEntry(10f,512));
//        ylabels.add(new BarEntry(63.f,800));
//        ylabels.add(new BarEntry(70.f,900));

        BarDataSet barDataSet = new BarDataSet(ylabels, "Hz");
        barDataSet.setColor(Color.YELLOW);
        barDataSet.setDrawValues(false);
        //  chart.animateY(5000);
        data = new BarData(xlabels, barDataSet); //MPAndroidChart v3.1 에서 오류나서 다른 버전 사용
        // barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);

        chart.setData(data);
        chart.setDescription("");

        // support scrollview - viewport
        chart.setVisibleXRangeMaximum(1600);
        chart.moveViewToX(8);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getXAxis().setGridColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisLeft().setGridColor(Color.WHITE);
        chart.setBorderColor(Color.WHITE);

        // change MP chart ViewPort
//        chart.moveViewToX(4048);
        int center = (int) (START_FREQ + END_FREQ) / 2;
        chart.centerViewTo(center, 0, YAxis.AxisDependency.LEFT);
        chart.setScaleMinima(0f, 1.5f);

//        Toast.makeText(this, Double.toString(START_FREQ) + " / " +Double.toString(END_FREQ),
//                Toast.LENGTH_SHORT).show();
//        Toast.makeText(this, "Size : " + Integer.toString(tv_lists.size()) + " / " + green_list.size(), Toast.LENGTH_SHORT).show();
        Toast.makeText(this, "Size : " + Integer.toString(tv_lists.size()), Toast.LENGTH_SHORT).show();

        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AnalyzeActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

    }

    @Override
    public void onDestroy() {

        if (pfa != null && handler != null) handler.removeCallbacks(pfa);

        pfa = null;
        handler = null;
        super.onDestroy();
    }

    // 이 액티비티의 작업들은 대부분 RecordAudio라는 클래스에서 진행된다. 이 클래스는 AsyncTask를 확장한다.
    // AsyncTask를 사용하면 사용자 인터페이스를 멍하니 있게 하는 메소드들을 별도의 스레드로 실행한다.
    // doInBackground 메소드에 둘 수 있는 것이면 뭐든지 이런 식으로 실행할 수 있다.

    public String whichScale2(double[]... toTransform) {

        if (toTransform[0][111] > 99999) {

        } else if (toTransform[0][259] > 55 || toTransform[0][260] > 55 || toTransform[0][261] > 55) {
            scale2 = "C4"; //도
        } else if (toTransform[0][293] > 15 || toTransform[0][292] > 30 || toTransform[0][294] > 20 || toTransform[0][295] > 30 || toTransform[0][296] > 30) {
            scale2 = "D4"; //레
        } else if (toTransform[0][329] > 50 || toTransform[0][328] > 50 || toTransform[0][330] > 50) {
            scale2 = "E4"; //미
        } else if (toTransform[0][349] > 50 || toTransform[0][348] > 50 || toTransform[0][347] > 50 || toTransform[0][346] > 50 || toTransform[0][350] > 50 || toTransform[0][351] > 50) {
            scale2 = "F4"; //파
        } else if (toTransform[0][391] > 55 || toTransform[0][390] > 60 || toTransform[0][389] > 60 || toTransform[0][392] > 60) {
            scale2 = "G4"; //솔
        } else if (toTransform[0][440] > 30 || toTransform[0][441] > 30 || toTransform[0][442] > 55 || toTransform[0][438] > 30 || toTransform[0][436] > 55 || toTransform[0][437] > 55) {
            scale2 = "A4"; //라
        } else if (toTransform[0][493] > 80 || toTransform[0][494] > 80 || toTransform[0][495] > 80 || toTransform[0][496] > 80) {
            scale2 = "B4"; //솔
        } else if (toTransform[0][523] > 44 || toTransform[0][524] > 44 || toTransform[0][521] > 44) {
            scale2 = "C5";
        } else if (toTransform[0][587] > 44 || toTransform[0][588] > 44 || toTransform[0][589] > 44) {
            scale2 = "D5";
        } else if (toTransform[0][660] > 15 || toTransform[0][659] > 20 || toTransform[0][662] > 20 || toTransform[0][663] > 20 || toTransform[0][658] > 15 || toTransform[0][657] > 28) {
            scale2 = "E5";
        } else if (toTransform[0][697] > 60 || toTransform[0][698] > 60 || toTransform[0][699] > 60 || toTransform[0][700] > 60) {
            scale2 = "F5";
        } else if (toTransform[0][783] > 55 || toTransform[0][784] > 55) {
            scale2 = "G5";
        } else if (toTransform[0][880] > 60 || toTransform[0][881] > 60 || toTransform[0][882] > 60) {
            scale2 = "A5";
        } else if (toTransform[0][987] > 33 || toTransform[0][988] > 33 || toTransform[0][989] > 33) {
            scale2 = "B5";
        }
        //3옥타브
        else if (toTransform[0][129] > 18 || toTransform[0][130] > 18) {
            scale2 = "C3";
        } else if (toTransform[0][145] > 18 || toTransform[0][144] > 18 || toTransform[0][146] > 18) {
            scale2 = "D3";
        } else if (toTransform[0][164] > 18 || toTransform[0][163] > 18 || toTransform[0][165] > 18) {
            scale2 = "E3";
        } else if (toTransform[0][174] > 18 || toTransform[0][173] > 18 || toTransform[0][175] > 18) {
            scale2 = "F3";
        } else if (toTransform[0][195] > 18 || toTransform[0][196] > 18 || toTransform[0][194] > 18) {
            scale2 = "G3";
        } else if (toTransform[0][220] > 18 || toTransform[0][221] > 18 || toTransform[0][119] > 18) {
            scale2 = "A3";
        } else if (toTransform[0][246] > 18 || toTransform[0][245] > 18 || toTransform[0][247] > 18) {
            scale2 = "B3";
        } else {

        }

        return scale2;
    }

    @Override
    public void onClick(View arg0) {


        if (arg0.getId() == R.id.BackButton) {
            //AnalyzeActivity.this.finish();

//            Intent intentSubActivity =
//                    new Intent(this, HomeActivity.class);
            startActivity(new Intent(this, HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (arg0.getId() == R.id.AnalyzeButton) {

            if (started) {
                Toast.makeText(this.getApplicationContext(), "stop analyzing",
                        Toast.LENGTH_SHORT).show();
                started = false;
                analyzeText.setText("OFF");
                analyzeText.setTextColor(Color.parseColor("#DA334D"));
                recordTask.cancel(true);
            } else {
                Toast.makeText(this.getApplicationContext(), "start analyzing",
                        Toast.LENGTH_SHORT).show();
                started = true;
                analyzeText.setText("ON");
                analyzeText.setTextColor(Color.parseColor("#33DA6D"));
                recordTask = new RecordAudio();
//                recordTask.execute();
                recordTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        } else if (arg0.getId() == R.id.ChirpButton) {

            Toast.makeText(this.getApplicationContext(), "chrip_button", Toast.LENGTH_SHORT).show();

            if (chirping) {
                chirping = false;
                chirpText.setText("OFF");
                chirpText.setTextColor(Color.parseColor("#DA334D"));
//                clearTone();
//                playTask.cancel(true);
                handler.removeCallbacks(pfa);
            } else {
                chirping = true;
                chirpText.setText("ON");
                chirpText.setTextColor(Color.parseColor("#33DA6D"));
//                genTone();
//                makeTone();
//                playTask = new PlayAudio();
////                playTask.execute();
//                playTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                pfa = new PlayFrequencyAudio();
                afa = new AnalyzeFrequencyAudio();
                handler.post(pfa);
            }
        } else if (arg0.getId() == R.id.SettingButton) {
            Toast.makeText(this.getApplicationContext(), "setting_button", Toast.LENGTH_SHORT).show();

//            FragmentManager fragmentManager = getSupportFragmentManager();
//            FragmentTransaction fragmentTransaction = fragmentManager
//                    .beginTransaction();
//            fragmentTransaction.replace(R.id.container,
//                    new SettingsFragment());
//            fragmentTransaction
//                    .addToBackStack("SettingFragment");
//            fragmentTransaction.commit();
        } else if (arg0.getId() == R.id.RefreshButton) {
//            detector.refreshTableContent(this.getApplicationContext());
            detector.refreshAllData();
        } else if (arg0.getId() == R.id.TestButton) {
            detector.updateCheckPoint();
            Log.d("FrequencyData", String.valueOf(real_point.size()));
        }
    }

    public void genTone() {
        // numSamples = duration * sampleRate;
        // numSamples = 8000

        // make frequency array
        double[] freqOfToneArr = new double[numSamples * DURATION_FREQ];
        double freqOfTones = START_FREQ;
        double sigma = 0.8;

        for (int i = 0; i < numSamples * DURATION_FREQ; i++) {
            freqOfToneArr[i] = freqOfTones++;
//            freqOfToneArr[i] = freqOfTones;
//            freqOfToneArr[i] = freqOfTones - (i*(END_FREQ - START_FREQ))/
//            (numSamples*DURATION_FREQ);
        }


        // fill out the array
        for (int i = 0; i < numSamples * DURATION_FREQ; ++i) {
//            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate) * freqOfToneArr[i]);
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfToneArr[i]));
//            double s = (i - (sample_size - 1) / 2) / (sigma*(sample_size - 1) / 2);
//            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTones))*Math.exp(-(s*s)/ 2);
//            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/START_FREQ));
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.

        int idx = 0;
        for (double dVal : sample) {
            short val = (short) (dVal * 32767);
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
    }

    public void makeTone() {
        int pcm_i = 0;
        double sigma = 0.8;
        double freqHz = 4000;
//        if(pcm ==null || sample_size <= 0) return;
        // make signal
        sample_size = numSamples * DURATION_FREQ;
        for (int i = 0; i < sample_size; i++) {
            double s = (i - (sample_size - 1) / 2) / (sigma * (sample_size - 1) / 2);
            short val =
                    (short) (Math.sin(freqHz * 2 * Math.PI * i / sampleRate) * Math.exp(-(s * s) / 2) * 32767);
            pcm[pcm_i++] = (byte) (val & 0xff);
            pcm[pcm_i++] = (byte) ((val & 0xff00) >> 8);
        }
    }

    public void clearTone() {
        Arrays.fill(generatedSnd, (byte) 0);
        Arrays.fill(pcm, (byte) 0);
    }

    public void playSound() {
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT,
                numSamples * DURATION_FREQ, AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, numSamples * DURATION_FREQ);
        audioTrack.play();
    }

    public void playSound2() {

        try {
            audioTrack =
                    new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)).build();

            audioTrack.setVolume(1.0f);
            audioTrack.write(pcm, 0, pcm.length);
            audioTrack.play();
        } catch (Exception e) {
            Log.e("AnalyzeActivity", "playSound2() + " + e.toString());
        }
    }

    private class RecordAudio extends AsyncTask<Void, double[], Void> {

        //스레드 관련 부분 2
        // scaleThread scThread = new scaleThread();

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // AudioRecord를 설정하고 사용한다.
                int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration,
                        audioEncoding);
                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        frequency, channelConfiguration, audioEncoding, bufferSize);
                // short로 이뤄진 배열인 buffer는 원시 PCM 샘플을 AudioRecord 객체에서 받는다.
                // double로 이뤄진 배열인 toTransform은 같은 데이터를 담지만 double 타입인데, FFT 클래스에서는 double타입이
                // 필요해서이다.
                short[] buffer = new short[blockSize];
                double[] toTransform = new double[blockSize];
                // double[] mag = new double[blockSize/2];

                NoiseSuppressor noiseSuppressor = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
                    Log.d(TAG, "NoiseSuppressor.isAvailable() " + NoiseSuppressor.isAvailable());
                }

                audioRecord.startRecording();

                while (started) {
                    int bufferReadResult = audioRecord.read(buffer, 0, blockSize);

                    //FFT는 Double 형 데이터를 사용하므로 short로 읽은 데이터를 형변환 시켜줘야함. short / short.MAX_VALUE
                    // = double
                    for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
                        toTransform[i] = (double) buffer[i] / Short.MAX_VALUE; // 부호 있는 16비트
                    }


                    //두개의 FFT 코드를 사용 잡음잡는것은 RealDoubleFFT가 훨씬 더 잘잡는다.
                    //RealDoubleFFT 부분
                    transformer.ft(toTransform);

                    //-> JTransform 부분
                    //Jtransform 은 입력에 실수부 허수부가 들어가야하므로 허수부 임의로 0으로 채워서 생성해줌
//                    double y[] = new double[blockSize];
//                    for (int i = 0; i < blockSize; i++) {
//                        y[i] = 0;
//                    }
//                    //실수 허수를 넣으므로 연산에는 blockSize의 2배인 배열 필요
//                    double[] summary = new double[2 * blockSize];
//                    for (int k = 0; k < blockSize; k++) {
//                        summary[2 * k] = toTransform[k]; //실수부
//                        summary[2 * k + 1] = y[k]; //허수부 0으로 채워넣음.
//                    }
//
//                    fft.complexForward(summary);
//                    for(int k=0;k<blockSize/2;k++){
//                        mag[k] = Math.sqrt(Math.pow(summary[2*k],2)+Math.pow(summary[2*k+1],2));
//                    }
                    //Jtrans 끝

                    // publishProgress를 호출하면 onProgressUpdate가 호출된다.

                    publishProgress(toTransform);
                    //publishProgress(mag); //1D 로 쓸거면 mag, realdouble이면 toTrans
                }
                noiseSuppressor.release();
                audioRecord.stop();
            } catch (Throwable t) {
                Log.e("AudioRecord", "Recording Failed");
            }
            return null;
        }
        // onProgressUpdate는 우리 엑티비티의 메인 스레드로 실행된다. 따라서 아무런 문제를 일으키지 않고 사용자 인터페이스와 상호작용할 수 있다.
        // 이번 구현에서는 onProgressUpdate가 FFT 객체를 통해 실행된 다음 데이터를 넘겨준다. 이 메소드는 최대 100픽셀의 높이로 일련의 세로선으로

        @Override
        protected void onProgressUpdate(double[]... toTransform) {
            //차트 삭제 부분
            xlabels.clear();
            ylabels.clear();

            int xChart = 0;

            for (int i = 0; i < chart_max_xrange; i++) {

//                if (i < 1024) {
                xlabels.add(Integer.toString(xChart));
                xChart = xChart + 1;
//                }
            }

            for (int i = 43; i < chart_max_xrange; i++) {
                if (i < blockSize) {
                    if (toTransform[0][i] > 0) {
                        ylabels.add(new BarEntry((float) toTransform[0][i], i));
                        //ylabels.add(new BarEntry((float)i,i));
                    }
                }
            }

            ArrayList<Integer> hzList = new ArrayList<Integer>();
            ArrayList<Double> hzSize = new ArrayList<Double>();

            //i 가 14부터 시작하는 이유: 배열한칸이 2hz 가지고있는상태이고, 20대에서 에어컨소리때문에 방해가생김 28부터 측정한다는뜻
            for (int i = 43; i < toTransform[0].length; i++) {
                if (toTransform[0][i] > 30) {
                    hzList.add(i);   //list에는 대역대가들어감 배열 i 순서
                    hzSize.add(toTransform[0][i]); //list에는 toTransform[][i]의 안에있는 값(크기) 가들어감
                    detector.addFrequencyData(i, toTransform[0][i]);
                }
            }
            detector.showAcumulativeCount();
            detector.updateTableContent(getApplicationContext());

            Iterator iter = hzList.iterator();
            if (iter.hasNext() == true) {
                t0.setText(Integer.toString(hzList.get(0))); //대역대
                t1.setText(Double.toString(hzSize.get(0)));      //소리 크기
                //t2.setText(whichScale(hzList.get(0)*4));
                //t2.setText(whichScale2(toTransform));
            }

            //생각해보니까 Transform 배열 전체를 다 넘겨준다음에. double[]... toTransform
            //거기 함수 안에서 if(도) toTransform[262] 값이랑 뭐뭐 200이상 뛰면으로 바꾸면될거같음

            hzSize.clear();
            hzList.clear();

            //차트 없애는 부분 여기
            BarDataSet barDataSet = new BarDataSet(ylabels, "Hz");
            barDataSet.setColor(Color.YELLOW);
            barDataSet.setDrawValues(false);
            data = new BarData(xlabels, barDataSet);

            chart.setVisibleXRangeMaximum(6400);
            chart.moveViewToX(8);

            int center = (int) (START_FREQ + END_FREQ) / 2;
            chart.centerViewTo(center, 0, YAxis.AxisDependency.LEFT);
            chart.setScaleMinima(0f, 1.5f);

            chart.setData(data);
            chart.invalidate();

        }
    }

    private class PlayAudio extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            try {
//                makeTone();
                while (!isCancelled()) {
                    int value = 1;
                    Thread.sleep(2000);
//                    Handler toast_handler = new Handler(getMainLooper());
//                    toast_handler.post(new Runnable() {
//                        @Override
//                        public void run() { Toast.makeText(getApplicationContext(), "PlayAudio",
//                    Toast.LENGTH_SHORT).show();
//                        }
//                    });
//                    playSound();
                    playSound2();
                    Thread.sleep(2000);
                    Log.d("RUNNABLE", "repeat chrip");
                }

            } catch (Throwable t) {
                Log.e("PlayAudio", "PlayAudio Failed");
            }
            return null;
        }
    }

    protected class PlayFrequencyAudio implements Runnable {
        protected static final int SINE = 0;
        protected static final int SQUARE = 1;
        protected static final int SAWTOOTH = 2;

        protected int waveform;
        protected boolean mute;

        protected double frequency;
        protected double level;

        protected float duty;

        protected Thread thread;

        private AudioTrack audioTrack;

        protected PlayFrequencyAudio() {
            frequency = 1440.0;
            level = 0.1;
            waveform = SINE;
            duty = 0.5f;
        }

        // Start
        protected void start() {
            thread = new Thread(this, "Audio");
            thread.start();
        }

        // Stop
        protected void stop() {
            Thread t = thread;
            thread = null;

            try {
                // Wait for the thread to exit
                if (t != null && t.isAlive()) t.join();
            } catch (Exception e) {
            }
        }

        public void run() {
            processAudio();
        }

        // Process audio
        @SuppressWarnings("deprecation")
        protected void processAudio() {

            short buffer[];

            int rate = 48000;   // max rate
            int minSize = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            // Duration = size / rate
            int size = 48000 * DURATION_FREQ;

            final double K = 2.0 * Math.PI / rate;

            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, size,
                    AudioTrack.MODE_STREAM);

            // Check state
            int state = audioTrack.getState();

            if (state != AudioTrack.STATE_INITIALIZED) {
                audioTrack.release();
                return;
            }

            audioTrack.play();

            // Create the buffer
            buffer = new short[size];

            // Initialise the generator variables
            double f = START_FREQ;
            double l = 0.0;
            double q = 0.0;

            double t = (duty * 2.0 * Math.PI) - Math.PI;

            // Fill the current buffer
            double refined_start_freq = START_FREQ - 700;
            double refined_end_freq = END_FREQ + 2800;
            for (int i = 0; i < buffer.length; i++) {

                frequency = refined_start_freq + i * ((refined_end_freq - refined_start_freq) / size);

                f += (frequency - f) / 4096.0;
                l += ((mute ? 0.0 : level) * 16384.0 - l) / 4096.0;
                q += ((q + (f * K)) < Math.PI) ? f * K : (f * K) - (2.0 * Math.PI);

                buffer[i] = (short) Math.round(Math.sin(q) * l);
            }

            CHIRP_SEQ += 1;
            audioTrack.write(buffer, 0, buffer.length);

            audioTrack.stop();
            audioTrack.release();

            detector.updateRawList();

            handler.postDelayed(afa, 1000 * DURATION_FREQ);
            // repeat
            if (chirping) {
                handler.postDelayed(pfa, 1000 * INTERVAL_FREQ);
            }

        }
    }

    private class FrequencyData {
        private int freq;
        private double size;
        private String timestamp;
        private int seq;

        public FrequencyData(int freq, double size, String timestamp) {
            this.freq = freq;
            this.size = size;
            this.timestamp = timestamp;
            this.seq = CHIRP_SEQ;
        }

        public int getFrequency() {
            return this.freq;
        }

        public double getSize() {
            return this.size;
        }

        public String getTimestamp() {
            return this.timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof FrequencyData) {
                FrequencyData p = (FrequencyData) o;
                return (this.freq == p.freq);
            } else
                return false;
        }
    }

    private class FrequencyDetector {

        // TODO
        // 감지할 10~20 개 점 표현 및 활용
        public ArrayList<FrequencyData> data_list;
        public int data_list_size;
        public int tmp_count;
        public String last_timestamp;

        public FrequencyDetector() {
            data_list = new ArrayList<FrequencyData>();
            data_list_size = 0;
            tmp_count = 0;
        }
//        ArrayList<Integer> hzList = new ArrayList<Integer>();
//        ArrayList<Double> hzSize = new ArrayList<Double>();

        public void init() {
            this.makeDetectorTableHeaderView();
            this.makeDetectorTableContentView();
            this.makeColorlists();
            this.makeCheckPointHz();
            this.refreshTableContent(getApplicationContext());
        }

        public void addFrequencyData(Integer freq, Double size) {

            Timestamp current = new Timestamp(System.currentTimeMillis());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            String timestamp = sdf.format(current);

            FrequencyData temp = new FrequencyData(freq, size, timestamp);

            if (freq > START_FREQ && freq < END_FREQ) {
                if (CHIRP_SEQ > 0) {
                    real_point.add(freq);
                }

                data_list.add(temp);
            }
        }

        public int getDataListSizeInRange() {

            for (int i = 0; i < data_list.size(); i++) {
                FrequencyData tmp = data_list.get(i);
                if ((tmp.freq >= START_FREQ) && (tmp.freq <= END_FREQ)) {
                    if (tmp.size > 0) {
                        data_list_size++;
                    }
                }
            }
            return data_list_size;
        }

        public void showAcumulativeCount() {
            int count = getDataListSizeInRange();
            count_view.setText("CHIRP : " + Integer.toString(CHIRP_SEQ) + " / TOTAL : " + Integer.toString(count));
//            timestamp_view.setText();
        }

        public void refreshTableContent(Context context) {

            for (int i = 0; i < TABLE_ROW ; i++) {
                int point = check_point.get(i);
                TextView tv = check_lists.get(i);
                String str = Integer.toString(point) + "hz";
                tv.setText(str);
//                check_point.add(point);
                Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
            }
        }

        public void updateTableContent(Context context) {

            for (int i = 0; i < TABLE_ROW ; i++) {
                // search round
                for (int j = 0; j < 1000 ; j++) {
                    if (data_list.contains(check_point.get(i)+j)) {
                        tmp_count++;
                    }
                }
            }
//            Toast.makeText(context, Integer.toString(data_list.get(0).getFrequency()), Toast.LENGTH_SHORT).show();
//            Toast.makeText(context, Integer.toString(tmp_count), Toast.LENGTH_SHORT).show();
            Log.d("Detector", "tmp_count " + Integer.toString(check_point.get(5)));
        }

        public void makeDetectorTableHeaderView() {

            TableRow row = new TableRow(getApplicationContext());
            TableRow.LayoutParams params = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(params);
            row.setGravity(Gravity.CENTER);

            params.setMargins(10, 10, 0, 0);

            TextView tv = new TextView(getApplicationContext());
            tv.setHeight(getResources().getDimensionPixelSize(R.dimen.detector_header_height));
            tv.setWidth(getResources().getDimensionPixelSize(R.dimen.detector_header_width) * 3 / 2);
            tv.setBackgroundResource(R.color.color_blue_3);
            tv.setText("Hz");
            tv.setTextColor(Color.WHITE);

            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(params);
            row.addView(tv);

            for (int i = 0; i < TABLE_COL; i++) {

                tv = new TextView(getApplicationContext());
                tv.setHeight(getResources().getDimensionPixelSize(R.dimen.detector_header_height));
                tv.setWidth(getResources().getDimensionPixelSize(R.dimen.detector_header_width));
                tv.setBackgroundResource(R.color.color_blue_2);
                tv.setText(Integer.toString((i + 1) * (DURATION_FREQ + INTERVAL_FREQ)) + "s");
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.detector_header_text_size));
                tv.setTextColor(Color.WHITE);
                tv.setGravity(Gravity.CENTER);
                tv.setLayoutParams(params);
                row.addView(tv);
            }

            detector_table_header.addView(row);
        }

        public void makeDetectorTableContentView() {


            for (int i = 0; i < TABLE_COL; i++) {
                ArrayList<TextView> tv_list = new ArrayList<TextView>();
                tv_lists.add(tv_list);
            }

            ArrayList<ArrayList<TextView>> tmp_lists = new ArrayList<ArrayList<TextView>>();

            for (int i = 0; i < TABLE_ROW; i++) {

                ArrayList<TextView> tv_list = new ArrayList<TextView>();

                TableRow row = new TableRow(getApplicationContext());
                TableRow.LayoutParams params = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(params);
                row.setGravity(Gravity.CENTER | Gravity.CENTER_HORIZONTAL);

                params.setMargins(10, 0, 0, 10);

                TextView pin_tv = new TextView(getApplicationContext());
                pin_tv.setHeight(getResources().getDimensionPixelSize(R.dimen.detector_element_height));
                pin_tv.setWidth(getResources().getDimensionPixelSize(R.dimen.detector_element_width) * 3 / 2);
                pin_tv.setBackgroundResource(R.color.color_blue_3);
                pin_tv.setText("Hz " + (i + 1));
                pin_tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.detector_element_text_size));
                pin_tv.setTextColor(Color.WHITE);
                pin_tv.setGravity(Gravity.CENTER);
                pin_tv.setLayoutParams(params);
                pin_tv.setLayoutParams(params);

                row.addView(pin_tv);
                check_lists.add(pin_tv);

                for (int j = 0; j < TABLE_COL; j++) {
                    TextView tv = new TextView(getApplicationContext());
                    tv.setHeight(getResources().getDimensionPixelSize(R.dimen.detector_element_height));
                    tv.setWidth(getResources().getDimensionPixelSize(R.dimen.detector_element_width));
                    tv.setBackgroundResource(R.color.white);
                    tv.setLayoutParams(params);
                    row.addView(tv);
                    tv_list.add(tv);
                }
                tmp_lists.add(tv_list);
                detector_table_content.addView(row);
            }

            // convert
            for (int i = 0 ; i < TABLE_COL ; i++) {
                for (int j = 0 ; j < TABLE_ROW ; j++) {
                    tv_lists.get(i).add(tmp_lists.get(j).get(i));
                }
            }
        }

        public void makeColorlists() {
            green_list = new ArrayList<Integer>();
            red_list = new ArrayList<Integer>();

            for (int i = 1; i < 10; i++) {
                Integer green_id = getResources().getIdentifier("tc_green_" + i + "00", "color", getPackageName());
                Integer red_id = getResources().getIdentifier("tc_red_" + i + "00", "color", getPackageName());
                green_list.add(green_id);
                red_list.add(red_id);
            }
        }

        public void makeCheckPointHz () {
            // TODO : 실제 Chirp 음 범위 기준으로 업데이트 필요
            double frequency = START_FREQ;

            for (int i = 0; i < 48000; i++) {

                frequency = START_FREQ + i * ((END_FREQ - START_FREQ) / 48000);

                for (int j = 0 ; j < TABLE_ROW ; j++) {
                    if (frequency >= (START_FREQ + (j * (END_FREQ - START_FREQ) / TABLE_ROW))
                            && frequency <= (START_FREQ + ((j + 1) * (END_FREQ - START_FREQ) / TABLE_ROW))) {
                        check_point.add((int) frequency);
                        break;
                    }
                }
            }
        }

        public void updateCheckPoint() {
            int size = real_point.size();

            for (int i = 1 ; i < 11 ; i++) {
                int point = real_point.get(i * (size/10));
                check_point.add(point);
                if (real_point.contains(point)) {
                    Log.d("FrequencyData", "OK : " + String.valueOf(point));
                }
            }
        }

        public void updateRawList() {
            if(data_list.size() > 0) {
                Log.d("AnalyzeFrequencyAudio", "FIRST : " + String.valueOf(data_list.get(0).getFrequency()));
                raw_list.add(data_list);
//                data_list.clear();
                data_list = new ArrayList<FrequencyData>();
            }
        }

        public void refreshAllData() {
            raw_list.clear();
            CHIRP_SEQ = 0;

            for (int i = 0; i < TABLE_COL; i++) {
                ArrayList<TextView> tv_list = tv_lists.get(i);
                for (int j = 0; j < TABLE_ROW; j++) {
                    tv_list.get(j).setBackgroundResource(R.color.white);
                }
            }
        }

        public void updateTableColor() {
            int latest_idx = diff_lists.size() - 1;

            for (int i = LASTEST_COL ; i > -1 ; i--) {
                for (int j = 0 ; j < TABLE_ROW ; j++) {
                    try {
                        tv_lists.get(i + 1).get(j).setBackground(tv_lists.get(i).get(j).getBackground());
                    } catch (Exception e) {

                    }
                }
            }

            Random rn = new Random();
            for (int k = 0 ; k < TABLE_ROW ; k++) {
//                int color_id = green_list.get(rn.nextInt(5));
                int color_id = calculateColorId(diff_lists.get(latest_idx).get(k));
                tv_lists.get(0).get(k).setBackgroundResource(color_id);
            }

            if (LASTEST_COL < TABLE_COL - 1)
                LASTEST_COL += 1;
        }

        public int calculateColorId(double diff) {

            int color_id = 0;
            int converted_diff = (int) diff;

            if (converted_diff < 0)
                converted_diff *= -1;

//            if (converted_diff > 0) {
                // green
                switch ((int)converted_diff / 18) {
                    case 1:
                        color_id = green_list.get(8);
                        break;
                    case 2:
                        color_id = green_list.get(7);
                        break;
                    case 3:
                        color_id = green_list.get(6);
                        break;
                    case 4:
                        color_id = green_list.get(5);
                        break;
                    case 5:
                        color_id = green_list.get(4);
                        break;
                    case 6:
                        color_id = green_list.get(3);
                        break;
                    case 7:
                        color_id = green_list.get(2);
                        break;
                    case 8:
                        color_id = green_list.get(1);
                        break;
                    case 9:
                        color_id = green_list.get(0);
                        break;
                    case 10:
                        color_id = red_list.get(0);
                        break;
                    case 11:
                        color_id = red_list.get(1);
                        break;
                    case 12:
                        color_id = red_list.get(2);
                        break;
                    case 13:
                        color_id = red_list.get(3);
                        break;
                    case 14:
                        color_id = red_list.get(4);
                        break;
                    case 15:
                        color_id = red_list.get(5);
                        break;
                    case 16:
                        color_id = red_list.get(6);
                        break;
                    case 17:
                        color_id = red_list.get(7);
                        break;
                    default:
                        color_id = red_list.get(8);
                }
//            } else {
//                // red
//                switch ((int) (-1) * converted_diff / 10) {
//                    case 1:
//                        color_id = red_list.get(0);
//                        break;
//                    case 2:
//                        color_id = red_list.get(1);
//                        break;
//                    case 3:
//                        color_id = red_list.get(2);
//                        break;
//                    case 4:
//                        color_id = red_list.get(3);
//                        break;
//                    case 5:
//                        color_id = red_list.get(4);
//                        break;
//                    default:
//                        color_id = red_list.get(5);
//                }
//            }

            return color_id;
        }
    }

    protected class AnalyzeFrequencyAudio implements Runnable {

        @Override
        public void run() {
            int size = raw_list.size();

            Log.d("AnalyzeFrequencyAudio", "SEQ : " + String.valueOf(CHIRP_SEQ) + " / size : " + String.valueOf(size));
            if (size > 1) {
                ArrayList<FrequencyData> before_list = raw_list.get(size-1);
                ArrayList<FrequencyData> current_list = raw_list.get(size-2);
                ArrayList<Integer> before_freqs = new ArrayList<Integer>();
                ArrayList<Integer> current_freqs = new ArrayList<Integer>();
                ArrayList<Integer> before_idxs = new ArrayList<Integer>();
                ArrayList<Integer> current_idxs = new ArrayList<Integer>();
                ArrayList<Double> diff_list = new ArrayList<Double>();

                if (before_list.size() > 0 && current_list.size() > 0) {
                    for (int i = 0; i < before_list.size(); i++) {
                        before_freqs.add(before_list.get(i).getFrequency());
                    }

                    for (int i = 0; i < current_list.size(); i++) {
                        current_freqs.add(current_list.get(i).getFrequency());
                    }

                    for (int i = 1; i < 11; i++) {

                        int before_point = before_list.get(i * (before_list.size() / 10) - 1).getFrequency();
                        int before_idx = before_freqs.indexOf(before_point);
                        before_idxs.add(before_idx);

                        int current_point = current_list.get(i * (current_list.size() / 10) - 1).getFrequency();
                        int current_idx = current_freqs.indexOf(current_point);
                        current_idxs.add(current_idx);

                        Log.d("AnalyzeFrequencyAudio", "BE (idx) : " + String.valueOf(before_idx) + " / FE (idx) : " + String.valueOf(current_idx));
                        Log.d("AnalyzeFrequencyAudio", "BE (freq) : " + String.valueOf(before_point) + " / FE (freq) : " + String.valueOf(current_point));
                    }

                    // update Detector Table
                    for (int i = 0; i < 10; i++) {
                        double diff;
                        double before_hz = before_list.get(before_idxs.get(i)).getSize();
                        double current_hz = current_list.get(current_idxs.get(i)).getSize();
                        diff = current_hz - before_hz;
                        diff_list.add(diff);
                        Log.d("AnalyzeFrequencyAudio", "DIFF : " + diff);
                    }

                    diff_lists.add(diff_list);

                    detector.updateTableColor();
                }

                // clear ArrayList
                before_freqs.clear();
                current_freqs.clear();
                before_idxs.clear();
                current_idxs.clear();
                diff_list.clear();
            }
        }
    }

}  //activity