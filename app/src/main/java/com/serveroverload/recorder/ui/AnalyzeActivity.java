package com.serveroverload.recorder.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
import static android.media.AudioTrack.*;
import static java.security.AccessController.getContext;

import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import ca.uol.aig.fftpack.RealDoubleFFT;
import android.media.audiofx.NoiseSuppressor;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import org.jtransforms.fft.DoubleFFT_1D;
import org.w3c.dom.Text;

import com.serveroverload.recorder.R;
import com.serveroverload.recorder.util.PreferenceManager;

public class AnalyzeActivity extends Activity implements OnClickListener {

    // PreferenceManager
    private double START_FREQ = 0.0;
    private double END_FREQ = 0.0;
    private int DURATION_FREQ = 1;
    private int INTERVAL_FREQ = 3;
    private int CHIRP_SEQ = 1;


//    int frequency = 8192; //주파수가 8192일 경우 4096 까지 측정이 가능함
    int frequency = 48000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

    private RealDoubleFFT transformer;
//    int blockSize = 4096; // 2048->1024개의 배열이 나옴. 배열 한 칸당 4hz의 범위를 포함하고 있음. //4096->배열 2048이고 한칸당 2hz //배열 번호 1씩 증가-> hz는 2씩 증가한다.
    //배열이 40일때 hz는 80헤르츠를 가지고있다는것.
    int blockSize = 24000;
    DoubleFFT_1D fft = new DoubleFFT_1D(blockSize); //JTransform 라이브러리로 FFT 수행

    String scale2 ;
    //frequency -> 측정 주파수 대역으로 퓨리에 변환 시 f/2 만큼의 크기의 주파수를 분석 할 수 있음.
    //blockSize -> 한 분기마다 측정하는 사이즈로 double 배열로 저장 시 , b/2 개의 배열이 나옴. f/b -> 배열 하나에 할당되는 주파수 범위로 8192/2048 -> 4Hz임

    ImageButton backButton;
    Button analyzeButton;
    Button chirpButton;
    TextView analyzeText;
    TextView chirpText;

    boolean started = false;
    boolean chirping = false;
    // RecordAudio는 여기에서 정의되는 내부 클래스로서 AsyncTask를 확장한다.
    RecordAudio recordTask;
    // PlayAudio는 여기에서 정의되는 내부 클래스로서 AsyncTask를 확장한다.
    PlayAudio playTask;
    // Bitmap 이미지를 표시하기 위해 ImageView를 사용한다. 이 이미지는 현재 오디오 스트림에서 주파수들의 레벨을 나타낸다.
    // 이 레벨들을 그리려면 Bitmap에서 구성한 Canvas 객체와 Paint객체가 필요하다.
    private static final String TAG = AnalyzeActivity.class.getSimpleName();

    // play PCM Sound (genTone)
    private final int duration = 1; // seconds
    private final int sampleRate = 8000;
    private final int numSamples = duration * sampleRate;
    private double sample[] = null;
    private final double freqOfTone = 6000; // hz
    private byte[] generatedSnd = null;

    // play PCM sound (makeTone)
    private int sample_size = numSamples;
    private final byte pcm[] = new byte[2 * numSamples];

    private AudioTrack audioTrack = null;

    // sig_gen
    private PlayFrequencyAudio pfa;

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
    TextView t1 ;
    TextView t2 ;

    // Detector View
    FrequencyDetector detector;
    TextView count_view;
    TextView timestamp_view;
    TableLayout detector_table;


    //스레드 관련 부분 1
    // scaleThread scThr = new scaleThread();
    double[] mag = new double[blockSize/2];

    private Handler handler = new Handler();

    Context context;

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
        bitmap = Bitmap.createBitmap((int) blockSize/2, (int) 200, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        //  imageView.setImageBitmap(bitmap);

        t0 = (TextView)findViewById(R.id.HzText0);
        t1 = (TextView)findViewById(R.id.HzText1);
        t2 = (TextView)findViewById(R.id.HzText2);


        // Detector View
        detector = new FrequencyDetector();

        count_view = (TextView) findViewById(R.id.DetectorCount);
        timestamp_view = (TextView) findViewById(R.id.DetectorTimestamp);
        detector_table = (TableLayout) findViewById(R.id.DetectorTable);

        detector.makeDetectorTable();

        // TODO
        // 테이블 아이템을 ArrayList 에 넣어서 setText() & background 편하게 할 수 있게?


        chart =(BarChart)findViewById(R.id.chart);
        YAxis leftYAxis = chart.getAxisLeft();
        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinValue(0);
        xAxis.setAxisMaxValue((float)20000); // 기존 1024
        leftYAxis.setAxisMaxValue((float)200);
        leftYAxis.setAxisMinValue(0);
        chart.getAxisRight().setEnabled(false);

        //chart 그리기
        int xChart=0;
        //x축 라벨 추가
        //4096 / 16 =256 씩 16칸으로 할거임
        //for(int i=0; i<1024; i++){
        for(int i=0; i<chart_max_xrange; i++){
            xlabels.add(Integer.toString(xChart));
            xChart=xChart+1;
        }

        //초기 데이터
//        ylabels.add(new BarEntry(2.2f,0));
//        ylabels.add(new BarEntry(10f,512));
//        ylabels.add(new BarEntry(63.f,800));
//        ylabels.add(new BarEntry(70.f,900));

        BarDataSet barDataSet = new BarDataSet(ylabels,"Hz");
        barDataSet.setColor(Color.YELLOW);
        barDataSet.setDrawValues(false);
        //  chart.animateY(5000);
        data = new BarData(xlabels,barDataSet); //MPAndroidChart v3.1 에서 오류나서 다른 버전 사용
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
        int center = (int) START_FREQ;
        chart.centerViewTo(center,0, YAxis.AxisDependency.LEFT);
        chart.setScaleMinima(0f,1.5f);

        Toast.makeText(this, Double.toString(START_FREQ) + " / " +Double.toString(END_FREQ),
                Toast.LENGTH_SHORT).show();


        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AnalyzeActivity.this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
        //오디오 녹음을 사용할 것인지 권한 여부를 체크해주는 코드로, 없으면 동작 안됨! +) AndroidManifest에도 오디오 권한 부분 추가되있음
    }

    @Override
    public void onDestroy() {

        if(pfa != null && handler != null)
            handler.removeCallbacks(pfa);

        pfa = null;
        handler = null;
        super.onDestroy();
    }

    // 이 액티비티의 작업들은 대부분 RecordAudio라는 클래스에서 진행된다. 이 클래스는 AsyncTask를 확장한다.
    // AsyncTask를 사용하면 사용자 인터페이스를 멍하니 있게 하는 메소드들을 별도의 스레드로 실행한다.
    // doInBackground 메소드에 둘 수 있는 것이면 뭐든지 이런 식으로 실행할 수 있다.

    private class RecordAudio extends AsyncTask<Void, double[], Void> {

        //스레드 관련 부분 2
        // scaleThread scThread = new scaleThread();

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // AudioRecord를 설정하고 사용한다.
                int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
                // short로 이뤄진 배열인 buffer는 원시 PCM 샘플을 AudioRecord 객체에서 받는다.
                // double로 이뤄진 배열인 toTransform은 같은 데이터를 담지만 double 타입인데, FFT 클래스에서는 double타입이 필요해서이다.
                short[] buffer = new short[blockSize];
                double[] toTransform = new double[blockSize];
                // double[] mag = new double[blockSize/2];

                NoiseSuppressor noiseSuppressor = null;
                if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN)
                {
                    noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
                    Log.d(TAG, "NoiseSuppressor.isAvailable() " + NoiseSuppressor.isAvailable());
                }

                audioRecord.startRecording();

                while (started) {
                    int bufferReadResult = audioRecord.read(buffer, 0, blockSize);

                    //FFT는 Double 형 데이터를 사용하므로 short로 읽은 데이터를 형변환 시켜줘야함. short / short.MAX_VALUE = double
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

            int xChart=0;

            for(int i=0; i<chart_max_xrange; i++){

//                if (i < 1024) {
                    xlabels.add(Integer.toString(xChart));
                    xChart = xChart + 1;
//                }
            }

            for(int i=43; i<chart_max_xrange; i++){
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
            for(int i=43; i<toTransform[0].length; i++){
                if(toTransform[0][i]>30){
                    hzList.add(i);   //list에는 대역대가들어감 배열 i 순서
                    hzSize.add(toTransform[0][i]); //list에는 toTransform[][i]의 안에있는 값(크기) 가들어감
                    detector.addFrequencyData(i, toTransform[0][i]);
                }
            }
            detector.show();

            Iterator iter = hzList.iterator();
            if(iter.hasNext()==true){
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
            BarDataSet barDataSet = new BarDataSet(ylabels,"Hz");
            barDataSet.setColor(Color.YELLOW);
            barDataSet.setDrawValues(false);
            data = new BarData(xlabels,barDataSet);

            chart.setVisibleXRangeMaximum(6400);
            chart.moveViewToX(8);

            int center = (int) START_FREQ;
            chart.centerViewTo(center,0, YAxis.AxisDependency.LEFT);
            chart.setScaleMinima(0f,1.5f);

            chart.setData(data);
            chart.invalidate();

        }
    }

    public String whichScale2(double[]... toTransform){

        if(toTransform[0][111]>99999){

        }
        else if(toTransform[0][259]>55 ||toTransform[0][260]>55 || toTransform[0][261]>55  ){
            scale2 = "C4"; //도
        }
        else if(toTransform[0][293]>15 || toTransform[0][292]>30 || toTransform[0][294]>20 ||
                toTransform[0][295]>30 || toTransform[0][296]>30 ){
            scale2 = "D4"; //레
        }
        else if(toTransform[0][329]>50 ||toTransform[0][328]>50 || toTransform[0][330]>50  ){
            scale2 = "E4"; //미
        }
        else if(toTransform[0][349]>50 || toTransform[0][348]>50 || toTransform[0][347]>50 ||
                toTransform[0][346]>50 || toTransform[0][350]>50 || toTransform[0][351]>50 ){
            scale2 = "F4"; //파
        }
        else if(toTransform[0][391]>55 ||toTransform[0][390]>60 || toTransform[0][389]>60 ||
                toTransform[0][392]>60  ){
            scale2 = "G4"; //솔
        }
        else if(toTransform[0][440]>30 || toTransform[0][441]>30 || toTransform[0][442]>55 ||
                toTransform[0][438]>30 || toTransform[0][436]>55 || toTransform[0][437]>55){
            scale2 = "A4"; //라
        }
        else if(toTransform[0][493]>80 ||toTransform[0][494]>80 || toTransform[0][495]>80 ||
                toTransform[0][496]>80  ){
            scale2 = "B4"; //솔
        }

        else if(toTransform[0][523]>44 ||toTransform[0][524]>44 || toTransform[0][521]>44  ){
            scale2 = "C5";
        }
        else if(toTransform[0][587]>44 ||toTransform[0][588]>44 || toTransform[0][589]>44  ){
            scale2 = "D5";
        }
        else if(toTransform[0][660]>15 ||toTransform[0][659]>20 || toTransform[0][662]>20 ||
                toTransform[0][663]>20 ||toTransform[0][658]>15 || toTransform[0][657]>28 ){
            scale2 = "E5";
        }
        else if(toTransform[0][697]>60 ||toTransform[0][698]>60 ||  toTransform[0][699]>60 || toTransform[0][700]>60  ){
            scale2 = "F5";
        }
        else if(toTransform[0][783]>55 ||toTransform[0][784]>55 ){
            scale2 = "G5";
        }
        else if(toTransform[0][880]>60 ||toTransform[0][881]>60 || toTransform[0][882]>60 ){
            scale2 = "A5";
        }
        else if(toTransform[0][987]>33 ||toTransform[0][988]>33 || toTransform[0][989]>33 ){
            scale2 = "B5";
        }
        //3옥타브
        else if(toTransform[0][129]>18 ||toTransform[0][130]>18){
            scale2 = "C3";
        }

        else if(toTransform[0][145]>18 ||toTransform[0][144]>18 ||toTransform[0][146]>18 ){
            scale2 = "D3";
        }
        else if(toTransform[0][164]>18 ||toTransform[0][163]>18 ||toTransform[0][165]>18 ){
            scale2 = "E3";
        }
        else if(toTransform[0][174]>18 ||toTransform[0][173]>18 ||toTransform[0][175]>18 ){
            scale2 = "F3";
        }
        else if(toTransform[0][195]>18 ||toTransform[0][196]>18 ||toTransform[0][194]>18 ){
            scale2 = "G3";
        }
        else if(toTransform[0][220]>18 ||toTransform[0][221]>18 ||toTransform[0][119]>18 ){
            scale2 = "A3";
        } else if(toTransform[0][246]>18 ||toTransform[0][245]>18 ||toTransform[0][247]>18 ){
            scale2 = "B3";
        }



        else{

        }

        return scale2;
    }

    private class PlayAudio extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            try {
//                makeTone();
                while(!isCancelled()) {
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

    @Override
    public void onClick(View arg0) {


        if (arg0.getId() == R.id.BackButton) {
            AnalyzeActivity.this.finish();
        }
        else if(arg0.getId() == R.id.AnalyzeButton) {

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
        }
        else if (arg0.getId() == R.id.ChirpButton) {

            Toast.makeText(this.getApplicationContext(), "chrip_button",
                    Toast.LENGTH_SHORT).show();

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
                handler.post(pfa);
            }
        }
    }

    public void genTone(){
        // numSamples = duration * sampleRate;
        // numSamples = 8000

        // make frequency array
        double[] freqOfToneArr = new double[numSamples * DURATION_FREQ];
        double freqOfTones = START_FREQ;
        double sigma = 0.8;

        for (int i = 0; i < numSamples * DURATION_FREQ; i++) {
            freqOfToneArr[i] = freqOfTones++;
//            freqOfToneArr[i] = freqOfTones;
//            freqOfToneArr[i] = freqOfTones - (i*(END_FREQ - START_FREQ))/(numSamples*DURATION_FREQ);
        }


        // fill out the array
        for (int i = 0; i < numSamples * DURATION_FREQ; ++i) {
//            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate) * freqOfToneArr[i]);
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfToneArr[i]));
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
        for(int i = 0; i<sample_size;i++) {
            double s = (i - (sample_size - 1) / 2) / (sigma*(sample_size - 1) / 2);
            short val = (short) (Math.sin(freqHz * 2 * Math.PI * i / sampleRate)*Math.exp(-(s*s)/ 2) * 32767);
            pcm[pcm_i++] = (byte) (val & 0xff);
            pcm[pcm_i++] = (byte) ((val & 0xff00) >> 8);
        }
    }

    public void clearTone() {
        Arrays.fill(generatedSnd, (byte) 0);
        Arrays.fill(pcm, (byte) 0);
    }

    public void playSound(){
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, numSamples * DURATION_FREQ, AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, numSamples * DURATION_FREQ);
        audioTrack.play();
    }

    public void playSound2() {

        try {
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
                    .build();

            audioTrack.setVolume(1.0f);
            audioTrack.write(pcm,0,pcm.length);
            audioTrack.play();
        } catch (Exception e) {
            Log.e("AnalyzeActivity", "playSound2() + " + e.toString());
        }
    }

    protected class PlayFrequencyAudio implements Runnable
    {
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

        protected PlayFrequencyAudio()
        {
            frequency = 1440.0;
            level = 0.1;
            waveform = SINE;
            duty = 0.5f;
        }

        // Start
        protected void start()
        {
            thread = new Thread(this, "Audio");
            thread.start();
        }

        // Stop
        protected void stop()
        {
            Thread t = thread;
            thread = null;

            try
            {
                // Wait for the thread to exit
                if (t != null && t.isAlive())
                    t.join();
            }

            catch (Exception e) {}
        }

        public void run()
        {
            processAudio();
        }

        // Process audio
        @SuppressWarnings("deprecation")
        protected void processAudio()
        {

            short buffer[];

            int rate = 48000;   // max rate
            int minSize =
                    AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);

            // Duration = size / rate
            int size = 48000 * DURATION_FREQ;

            final double K = 2.0 * Math.PI / rate;

            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size, AudioTrack.MODE_STREAM);

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
            for (int i = 0; i < buffer.length; i++) {

                frequency = START_FREQ + i * ((END_FREQ-START_FREQ) / size);
                f += (frequency - f) / 4096.0;
                l += ((mute ? 0.0 : level) * 16384.0 - l) / 4096.0;
                q += ((q + (f * K)) < Math.PI) ? f * K :
                        (f * K) - (2.0 * Math.PI);

                buffer[i] = (short) Math.round(Math.sin(q) * l);
            }

            CHIRP_SEQ++;
            audioTrack.write(buffer, 0, buffer.length);

            audioTrack.stop();
            audioTrack.release();

            // repeat
            if (chirping)
                handler.postDelayed(pfa, 1000 * INTERVAL_FREQ);
        }
    }

    private class FrequencyDetector {

        public class FrequencyData {
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
        }

        // TODO
        // 감지할 10~20 개 점 표현 및 활용
        public ArrayList<FrequencyData> data_list;
        public int data_list_size;
        public String last_timestamp;
//        ArrayList<Integer> hzList = new ArrayList<Integer>();
//        ArrayList<Double> hzSize = new ArrayList<Double>();

        public FrequencyDetector() {
            data_list = new ArrayList<FrequencyData>();
            data_list_size = 0;
        }

        public void addFrequencyData(Integer freq, Double size) {

            Timestamp current = new Timestamp(System.currentTimeMillis());
            SimpleDateFormat sdf = new SimpleDateFormat ("yyyy-MM-dd hh:mm:ss");
            String timestamp = sdf.format(current);

            FrequencyData temp = new FrequencyData(freq, size, timestamp);

            data_list.add(temp);
        }

        public int getDataListSizeInRange() {
            int count = 0;

            for (int i = 0; i < data_list.size(); i++) {
                FrequencyData tmp = data_list.get(i);
                if ((tmp.freq >= START_FREQ) && (tmp.freq <= END_FREQ)) {
                    if (tmp.size > 0) {
                        // TODO : fix detected count
                        count++;
                        data_list_size++;
                    }
                }
            }

            return data_list_size;
        }

        public void show() {
            int count = getDataListSizeInRange();
            count_view.setText("CHIRP : " + Integer.toString(CHIRP_SEQ-1) + " / TOTAL : " + Integer.toString(count));
//            timestamp_view.setText();
        }

        public void makeDetectorTable() {
            // TODO
            // https://stackoverflow.com/questions/5391624/how-to-scroll-tableview-in-android
            // https://stackoverflow.com/questions/6513718/how-to-make-a-scrollable-tablelayout

            // https://4z7l.github.io/2020/09/17/android-context.html
            TableRow row = new TableRow(getApplicationContext());

            TextView tv1 = new TextView(getApplicationContext());
            TextView tv2 = new TextView(getApplicationContext());
            TextView tv3 = new TextView(getApplicationContext());

            row.addView(tv1);
            row.addView(tv2);
            row.addView(tv3);

            detector_table.addView(row, new TableLayout.LayoutParams(TableLayout.LayoutParams.WRAP_CONTENT, TableLayout.LayoutParams.WRAP_CONTENT));
        }
    }

}  //activity