package com.serveroverload.recorder.ui;

import java.util.ArrayList;

import android.media.MediaPlayer;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.serveroverload.recorder.R;
import com.serveroverload.recorder.util.PreferenceManager;

public class HomeActivity extends FragmentActivity {

	MediaPlayer mMediaPlayer;

	private ArrayList<String> recordings = new ArrayList<String>();

	public int RecordingNumber;

	/**
	 * @return the mMediaPlayer
	 */
	public MediaPlayer getmMediaPlayer() {
		return mMediaPlayer;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		mMediaPlayer = new MediaPlayer();

		// sharedPreference
//		if (PreferenceManager.getInt(this, "start_freq") )
		PreferenceManager.setInt(this, "start_freq", 12000);
		PreferenceManager.setInt(this, "end_freq", 16000);
		PreferenceManager.setInt(this, "duration_freq", 1);
		PreferenceManager.setInt(this, "interval_freq", 3);

		androidx.fragment.app.FragmentManager fragmentManager= getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.replace(R.id.container, new RecordAudioFragment());
		fragmentTransaction.addToBackStack("RecordAudioFragment");
		fragmentTransaction.commit();
		
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer = null;
		}

	}

	public ArrayList<String> getRecordings() {
		return recordings;
	}

	public void setRecordings(ArrayList<String> recordings) {
		this.recordings = recordings;
	}

}
