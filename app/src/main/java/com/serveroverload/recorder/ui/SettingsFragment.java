package com.serveroverload.recorder.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.serveroverload.recorder.R;
import com.serveroverload.recorder.util.PreferenceManager;


public class SettingsFragment extends Fragment implements View.OnClickListener {

    private View rootView;

    Button startFreqButton;
    Button endFreqButton;
    Button durationFreqButton;
    Button intervalFreqButton;
    Button saveButton;
    Button defaultButton;
    Button exitButton;

    EditText startFreqEditText;
    EditText endFreqEditText;
    EditText durationFreqEditText;
    EditText intervalFreqEditText;

    int before_start_freq = 12000;
    int before_end_freq = 16000;
    int before_duration = 1;
    int before_interval = 3;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.setting_fragment, container,
                false);

        startFreqButton = (Button) rootView.findViewById(R.id.start_button);
        startFreqButton.setOnClickListener(this);
        endFreqButton = (Button) rootView.findViewById(R.id.end_button);
        endFreqButton.setOnClickListener(this);
        durationFreqButton = (Button) rootView.findViewById(R.id.duration_button);
        durationFreqButton.setOnClickListener(this);
        intervalFreqButton = (Button) rootView.findViewById(R.id.interval_button);
        intervalFreqButton.setOnClickListener(this);

        before_start_freq = PreferenceManager.getInt(getActivity(), "start_freq");
        before_end_freq = PreferenceManager.getInt(getActivity(), "end_freq");
        before_duration = PreferenceManager.getInt(getActivity(), "duration_freq");
        before_interval = PreferenceManager.getInt(getActivity(), "interval_freq");

        startFreqEditText = (EditText) rootView.findViewById(R.id.start_frequency);
        startFreqEditText.setText(Integer.toString(before_start_freq));
        startFreqEditText.setOnClickListener(this);
        endFreqEditText = (EditText) rootView.findViewById(R.id.end_frequency);
        endFreqEditText.setText(Integer.toString(before_end_freq));
        endFreqEditText.setOnClickListener(this);
        durationFreqEditText = (EditText) rootView.findViewById(R.id.duration_frequency);
        durationFreqEditText.setText(Integer.toString(before_duration));
        durationFreqEditText.setOnClickListener(this);
        intervalFreqEditText = (EditText) rootView.findViewById(R.id.interval_frequency);
        intervalFreqEditText.setText(Integer.toString(before_interval));
        intervalFreqEditText.setOnClickListener(this);

        saveButton = (Button) rootView.findViewById(R.id.save_button);
        saveButton.setOnClickListener(this);
        defaultButton = (Button) rootView.findViewById(R.id.default_button);
        defaultButton.setOnClickListener(this);
        exitButton = (Button) rootView.findViewById(R.id.exit_button);
        exitButton.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onClick(View arg0) {

        if (arg0.getId() == R.id.start_button) {
            int current_start = PreferenceManager.getInt(getActivity(),"start_freq");
            startFreqEditText.setText(Integer.toString(current_start));
        } else if (arg0.getId() == R.id.end_button) {
            int current_end = PreferenceManager.getInt(getActivity(), "end_freq");
            endFreqEditText.setText(Integer.toString(current_end));
        } else if (arg0.getId() == R.id.duration_button) {
            int current_duration = PreferenceManager.getInt(getActivity(), "duration_freq");
            durationFreqEditText.setText(Integer.toString(current_duration));
        } else if (arg0.getId() == R.id.interval_button) {
            int current_interval = PreferenceManager.getInt(getActivity(), "interval_freq");
            intervalFreqEditText.setText(Integer.toString(current_interval));
        } else if (arg0.getId() == R.id.default_button) {
            PreferenceManager.clear(getActivity());
            PreferenceManager.setInt(getActivity(), "start_freq", 12000);
            PreferenceManager.setInt(getActivity(), "end_freq", 16000);
            PreferenceManager.setInt(getActivity(), "duration_freq", 1);
            PreferenceManager.setInt(getActivity(), "interval_freq", 3);
            startFreqEditText.setText(Integer.toString(12000));
            endFreqEditText.setText(Integer.toString(16000));
            durationFreqEditText.setText(Integer.toString(1));
            intervalFreqEditText.setText(Integer.toString(3));
        } else if (arg0.getId() == R.id.save_button) {
            int new_start = before_start_freq;
            int new_end = before_end_freq;
            String start_str = startFreqEditText.getText().toString();
            String end_str = endFreqEditText.getText().toString();
            if (start_str != null)
                new_start = Integer.parseInt(start_str);
            if (end_str != null)
                new_end = Integer.parseInt(end_str);

            PreferenceManager.setInt(getActivity(), "start_freq", new_start);
            PreferenceManager.setInt(getActivity(), "end_freq", new_end);
            Toast.makeText(getActivity(), "save_button",
                    Toast.LENGTH_SHORT).show();
            closeFragment();
        } else if (arg0.getId() == R.id.exit_button) {
            Log.d("SettingFragment", "exit_button");
            Toast.makeText(getActivity(), "exit_button",
                    Toast.LENGTH_SHORT).show();
            closeFragment();
        }

        switch (arg0.getId()) {
            case R.id.start_frequency:
                startFreqEditText.getText().clear();
            case R.id.end_frequency:
                endFreqEditText.getText().clear();
            case R.id.duration_frequency:
                durationFreqEditText.getText().clear();
            case R.id.interval_frequency:
                intervalFreqEditText.getText().clear();
            default:
                break;
        }

    }

    private void closeFragment() {
        FragmentManager fragmentManager = getActivity()
                .getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager
                .beginTransaction();
        fragmentTransaction.remove(SettingsFragment.this).commit();
        fragmentManager.popBackStack();
    }


}