package com.serveroverload.recorder.ui;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.serveroverload.recorder.R;
import com.serveroverload.recorder.customview.RecorderVisualizerView;
import com.serveroverload.recorder.util.Helper;

public class RecordAudioFragment extends Fragment {

	private String currentOutFile;
	private MediaRecorder myAudioRecorder;
	private MediaPlayer mySoundOuput;

	private boolean isRecording;
	private RecorderVisualizerView visualizerView;
	private View rootView;

	private boolean doubleBackToExitPressedOnce;
	private Handler mHandler = new Handler();

	// play PCM Sound
	private final int duration = 1; // seconds
	private final int sampleRate = 8000;
	private final int numSamples = duration * sampleRate;
	private final double sample[] = new double[numSamples];
	private final double freqOfTone = 16000; // hz
	private final byte generatedSnd[] = new byte[2 * numSamples];

	// sig_gen
	private PlayFrequencyAudio pfa;
	private boolean pfa_on = false;

	Handler soundhandler = new Handler();

	private final Runnable mRunnable = new Runnable() {
		@Override
		public void run() {
			doubleBackToExitPressedOnce = false;
		}
	};

	public static final int REPEAT_INTERVAL = 40;

	private Handler handler = new Handler(); // Handler for updating the
												// visualizer

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		pfa = new PlayFrequencyAudio();

		rootView = inflater.inflate(R.layout.record_audio_fragment, container,
				false);

		rootView.findViewById(R.id.stop_recording).setEnabled(false);
		rootView.findViewById(R.id.delete_recording).setEnabled(false);

		visualizerView = (RecorderVisualizerView) rootView
				.findViewById(R.id.visualizer);

		rootView.findViewById(R.id.start_recording).setOnTouchListener(
				new OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {

						recordSound();

						return false;
					}
				});

		rootView.findViewById(R.id.stop_recording).setOnTouchListener(
				new OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {

						Helper.getHelperInstance().makeHepticFeedback(
								getActivity());
						try {

							if (null != myAudioRecorder) {
								myAudioRecorder.stop();
								myAudioRecorder.release();
								myAudioRecorder = null;

								Toast.makeText(
										getActivity(),
										getActivity().getResources().getString(
												R.string.rec_saved)
												+ currentOutFile,
										Toast.LENGTH_SHORT).show();
							}

						} catch (Exception e) {
							e.printStackTrace();
							Toast.makeText(
									getActivity(),
									getActivity().getResources().getString(
											R.string.rec_fail),
									Toast.LENGTH_LONG).show();
						}

						rootView.findViewById(R.id.start_recording).setEnabled(
								true);
						rootView.findViewById(R.id.stop_recording).setEnabled(
								false);
						rootView.findViewById(R.id.delete_recording)
								.setEnabled(true);

						isRecording = false;

						handler.removeCallbacks(updateVisualizer);

						return false;
					}
				});

		rootView.findViewById(R.id.sound_output).setOnTouchListener(
				new OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {

						Toast.makeText(getActivity(), "sound_output",
								Toast.LENGTH_SHORT).show();

//						mySoundOuput = new MediaPlayer();
//
//						// play PCM sound
//						// Use a new tread as this can take a while
//						Thread thread = new Thread(new Runnable()
//						{
//							public void run() {
//								genTone();
//								handler.post(new Runnable()
//								{
//									public void run() {
//										playSound();
//									}
//								});
//							}
//						});
//
//						thread.start();
//
//						recordSound();

						if (pfa != null)
							if (!pfa_on) {
								pfa_on = true;
								pfa.start();
							}
							else {
								pfa_on = false;
								pfa.stop();
							}

						return false;
					}
				}

		);

		rootView.findViewById(R.id.setting_frequency).setOnTouchListener(
				new OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {

						FragmentManager fragmentManager = getActivity()
								.getSupportFragmentManager();
						FragmentTransaction fragmentTransaction = fragmentManager
								.beginTransaction();
						fragmentTransaction.replace(R.id.container,
								new SettingsFragment());
						fragmentTransaction
								.addToBackStack("SettingFragment");
						fragmentTransaction.commit();

						return false;
					}
				}

		);

		rootView.findViewById(R.id.start_analytics).setOnTouchListener(
				new OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {

						Intent intentSubActivity =
								new Intent(getActivity(), AnalyzeActivity.class);
						startActivity(intentSubActivity);

						return false;
					}
//					@Override
//					public boolean onTouch(View v, MotionEvent event) {
//
//						FragmentManager fragmentManager = getActivity()
//								.getSupportFragmentManager();
//						FragmentTransaction fragmentTransaction = fragmentManager
//								.beginTransaction();
//						fragmentTransaction.replace(R.id.container,
//								new AnalysisFragment());
//						fragmentTransaction
//								.addToBackStack("AnalysisFragment");
//						fragmentTransaction.commit();
//
//						return false;
//					}
				}

		);

		// exit 버튼
		rootView.findViewById(R.id.exit_app).setOnTouchListener(
				new OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {

						Helper.getHelperInstance().makeHepticFeedback(
								getActivity());

						try {

							if (null != myAudioRecorder) {
								myAudioRecorder.stop();
								myAudioRecorder.release();
								myAudioRecorder = null;

								Toast.makeText(
										getActivity(),
										getActivity().getResources().getString(
												R.string.rec_saved)
												+ currentOutFile,
										Toast.LENGTH_SHORT).show();
							}

						} catch (Exception e) {
							e.printStackTrace();
							Toast.makeText(
									getActivity(),
									getActivity().getResources().getString(
											R.string.rec_fail),
									Toast.LENGTH_LONG).show();
						}

						isRecording = false;

						handler.removeCallbacks(updateVisualizer);

						System.exit(0);

						return false;
					}
				});

		rootView.findViewById(R.id.browse_recording).setOnTouchListener(
				new OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {

						FragmentManager fragmentManager = getActivity()
								.getSupportFragmentManager();
						FragmentTransaction fragmentTransaction = fragmentManager
								.beginTransaction();
						fragmentTransaction.replace(R.id.container,
								new RecordingListFragment());
						fragmentTransaction
								.addToBackStack("RecordingListFragment");
						fragmentTransaction.commit();

						return false;
					}
				});


		rootView.findViewById(R.id.delete_recording).setOnTouchListener(
				new OnTouchListener() {

					@Override
					public boolean onTouch(View v, MotionEvent event) {

						File recording = new File(currentOutFile);

						if (recording.exists() && recording.delete()) {
							Toast.makeText(
									getActivity(),
									getResources().getString(
											R.string.rec_deleted)
											+ currentOutFile,
									Toast.LENGTH_SHORT).show();
						} else {
							Toast.makeText(
									getActivity(),
									getActivity().getResources().getString(
											R.string.rec_delete_fail)
											+ currentOutFile,
									Toast.LENGTH_SHORT).show();
						}

						rootView.findViewById(R.id.stop_recording).setEnabled(
								false);
						rootView.findViewById(R.id.delete_recording)
								.setEnabled(false);
						return false;
					}
				});

		rootView.setFocusableInTouchMode(true);
		rootView.requestFocus();
		rootView.setOnKeyListener(new View.OnKeyListener() {

			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {

				if (event.getAction() == KeyEvent.ACTION_UP
						&& keyCode == KeyEvent.KEYCODE_BACK) {

					if (doubleBackToExitPressedOnce) {
						// super.onBackPressed();

						if (mHandler != null) {
							mHandler.removeCallbacks(mRunnable);
						}

						getActivity().finish();

						return true;
					}

					doubleBackToExitPressedOnce = true;
					Toast.makeText(getActivity(),
							"Please click BACK again to exit",
							Toast.LENGTH_SHORT).show();

					mHandler.postDelayed(mRunnable, 2000);

				}
				return true;
			}
		});

		return rootView;

	}

	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();

		if (isRecording) {

			try {

				if (null != myAudioRecorder) {

					myAudioRecorder.stop();
					myAudioRecorder.release();
					myAudioRecorder = null;

					Toast.makeText(
							getActivity(),
							getActivity().getResources().getString(
									R.string.rec_saved)
									+ currentOutFile, Toast.LENGTH_SHORT)
							.show();

					rootView.findViewById(R.id.start_recording)
							.setEnabled(true);
					rootView.findViewById(R.id.stop_recording)
							.setEnabled(false);
					rootView.findViewById(R.id.delete_recording).setEnabled(
							true);

					handler.removeCallbacks(updateVisualizer);
				}

			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(
						getActivity(),
						getActivity().getResources().getString(
								R.string.rec_fail), Toast.LENGTH_LONG).show();

				rootView.findViewById(R.id.start_recording).setEnabled(true);
				rootView.findViewById(R.id.stop_recording).setEnabled(false);
				rootView.findViewById(R.id.delete_recording).setEnabled(true);

				handler.removeCallbacks(updateVisualizer);

			}
		}
	}

	// updates the visualizer every 50 milliseconds
	Runnable updateVisualizer = new Runnable() {
		@Override
		public void run() {
			if (isRecording) // if we are already recording
			{
				// get the current amplitude
				int x = myAudioRecorder.getMaxAmplitude();
				visualizerView.addAmplitude(x); // update the VisualizeView
				visualizerView.invalidate(); // refresh the VisualizerView

				// update in 40 milliseconds
				handler.postDelayed(this, REPEAT_INTERVAL);
			}
		}
	};

	// play PCM Sound

	void genTone(){
		// make frequency array
		double freqOfToneArr[] = new double[numSamples];
		double freqOfTones = 16000;

		for (int i = 0; i < numSamples; i++) {
			freqOfToneArr[i] = freqOfTones++;
		}


		// fill out the array
		// numSamples = 24000
		for (int i = 0; i < numSamples; ++i) {
			sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfToneArr[i]));
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

	void playSound(){
		AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
						8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,

		AudioFormat.ENCODING_PCM_16BIT, numSamples, AudioTrack.MODE_STATIC);
		audioTrack.write(generatedSnd, 0, numSamples);
		audioTrack.play();
	}

	void recordSound(){
		Helper.getHelperInstance().makeHepticFeedback(
								getActivity());

			if (Helper.getHelperInstance().createRecordingFolder()) {

				SimpleDateFormat dateFormat = new SimpleDateFormat(
						"yyyyMMdd_HH_mm_ss");
				String currentTimeStamp = dateFormat
						.format(new Date());

				currentOutFile = Helper.RECORDING_PATH
						+ "/recording_" + currentTimeStamp + ".3gp";

				myAudioRecorder = new MediaRecorder();
				myAudioRecorder
						.setAudioSource(MediaRecorder.AudioSource.MIC);
				myAudioRecorder
						.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
				myAudioRecorder
						.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
				myAudioRecorder.setOutputFile(currentOutFile);

				try {

					myAudioRecorder.prepare();
					myAudioRecorder.start();

					Toast.makeText(
							getActivity(),
							getActivity().getResources().getString(
									R.string.rec_start),
							Toast.LENGTH_LONG).show();

					rootView.findViewById(R.id.start_recording)
							.setEnabled(false);
					rootView.findViewById(R.id.stop_recording)
							.setEnabled(true);
					rootView.findViewById(R.id.delete_recording)
							.setEnabled(false);

					isRecording = true;

					handler.post(updateVisualizer);
				}

				catch (IllegalStateException e) {
					Toast.makeText(
							getActivity(),
							getActivity().getResources().getString(
									R.string.rec_fail) + " Illegal",
							Toast.LENGTH_LONG).show();
					e.printStackTrace();

					rootView.findViewById(R.id.start_recording)
							.setEnabled(true);
					rootView.findViewById(R.id.stop_recording)
							.setEnabled(false);
					rootView.findViewById(R.id.delete_recording)
							.setEnabled(true);

					isRecording = false;
				}

				catch (IOException e) {
					Toast.makeText(
							getActivity(),
							getActivity().getResources().getString(
									R.string.rec_fail) + " IOException",
							Toast.LENGTH_LONG).show();
					e.printStackTrace();

					rootView.findViewById(R.id.start_recording)
							.setEnabled(true);
					rootView.findViewById(R.id.stop_recording)
							.setEnabled(false);
					rootView.findViewById(R.id.delete_recording)
							.setEnabled(true);

					isRecording = false;
				}
			} else {

				Toast.makeText(
						getActivity(),
						getActivity().getResources().getString(
								R.string.rec_fail_mkdir),
						Toast.LENGTH_LONG).show();

				isRecording = false;
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
//            level = 16384.0;
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

//   		AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
//						8000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
//
//			AudioFormat.ENCODING_PCM_16BIT, numSamples, AudioTrack.MODE_STATIC);
            short buffer[];

            int rate =
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
            int minSize =
                AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO,
                                            AudioFormat.ENCODING_PCM_16BIT);

            // Find a suitable buffer size
            int sizes[] = {1024, 2048, 4096, 8192, 16384, 32768};
            int size = 0;

            for (int s : sizes)
            {
                if (s > minSize)
                {
                    size = s;
                    break;
                }
            }

            final double K = 2.0 * Math.PI / rate;

            // Create the audio track
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                                        AudioFormat.CHANNEL_OUT_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        size, AudioTrack.MODE_STREAM);
            // Check audioTrack

            // Check state
            int state = audioTrack.getState();

            if (state != AudioTrack.STATE_INITIALIZED)
            {
                audioTrack.release();
                return;
            }

            audioTrack.play();

            // Create the buffer
            buffer = new short[size];

            // Initialise the generator variables
            double f = frequency;
            double l = 0.0;
            double q = 0.0;

            while (thread != null)
            {
                double t = (duty * 2.0 * Math.PI) - Math.PI;

                // Fill the current buffer
                for (int i = 0; i < buffer.length; i++)
                {
                    f += (frequency - f) / 4096.0;
                    l += ((mute ? 0.0 : level) * 16384.0 - l) / 4096.0;
                    q += ((q + (f * K)) < Math.PI) ? f * K :
                        (f * K) - (2.0 * Math.PI);

                    switch (waveform)
                    {
                    case SINE:
                        buffer[i] = (short) Math.round(Math.sin(q) * l);
                        break;

                    case SQUARE:
                        buffer[i] = (short) ((q > t) ? l : -l);
                        break;

                    case SAWTOOTH:
                        buffer[i] = (short) Math.round((q / Math.PI) * l);
                        break;
                    }
               }

                audioTrack.write(buffer, 0, buffer.length);
            }

            audioTrack.stop();
            audioTrack.release();
        }
    }
}
