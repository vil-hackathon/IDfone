package com.vil.vil_bot.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import com.vil.vil_bot.MainActivity;

import java.lang.ref.WeakReference;

import static android.content.ContentValues.TAG;

public class VoiceRecogService extends Service
{
    protected AudioManager mAudioManager;
    protected SpeechRecognizer mSpeechRecognizer;
    protected Intent mSpeechRecognizerIntent;
    protected final Messenger mServerMessenger = new Messenger(new IncomingHandler(this));

    protected boolean mIsListening;
    protected volatile boolean mIsCountDownOn;

    static final int MSG_RECOGNIZER_START_LISTENING = 1;
    static final int MSG_RECOGNIZER_CANCEL = 2;

    private int mBindFlag;
    private Messenger mServiceMessenger;
    //Context context;

    @Override
    public void onCreate()
    {
        super.onCreate();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mSpeechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
        mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                this.getPackageName());

        mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
    }

    protected static class IncomingHandler extends Handler
    {
        private WeakReference<VoiceRecogService> mtarget;

        IncomingHandler(VoiceRecogService target)
        {
            mtarget = new WeakReference<VoiceRecogService>(target);
        }


        @Override
        public void handleMessage(Message msg)
        {
            final VoiceRecogService target = mtarget.get();

            switch (msg.what)
            {
                case MSG_RECOGNIZER_START_LISTENING:

                    if (Build.VERSION.SDK_INT >= 16);//Build.VERSION_CODES.JELLY_BEAN)
                {
                    // turn off beep sound
                    target.mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
                }
                if (!target.mIsListening)
                {
                    target.mSpeechRecognizer.startListening(target.mSpeechRecognizerIntent);
                    target.mIsListening = true;
                    Log.d(TAG, "message start listening"); //$NON-NLS-1$
                }
                break;

                case MSG_RECOGNIZER_CANCEL:
                    target.mSpeechRecognizer.cancel();
                    target.mIsListening = false;
                    Log.d(TAG, "message canceled recognizer"); //$NON-NLS-1$
                    break;
            }
        }
    }

    // Count down timer for Jelly Bean work around
    protected CountDownTimer mNoSpeechCountDown = new CountDownTimer(5000, 5000)
    {

        @Override
        public void onTick(long millisUntilFinished)
        {
            // TODO Auto-generated method stub

        }

        @Override
        public void onFinish()
        {
            mIsCountDownOn = false;
            Message message = Message.obtain(null, MSG_RECOGNIZER_CANCEL);
            try
            {
                mServerMessenger.send(message);
                message = Message.obtain(null, MSG_RECOGNIZER_START_LISTENING);
                mServerMessenger.send(message);
            }
            catch (RemoteException e)
            {

            }
        }
    };

    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
        try
        {
            Message msg = new Message();
            msg.what = MSG_RECOGNIZER_START_LISTENING;
            mServerMessenger.send(msg);
        }
        catch (RemoteException e)
        {
            e.printStackTrace();
        }
        return  START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (mIsCountDownOn)
        {
            mNoSpeechCountDown.cancel();
        }
        if (mSpeechRecognizer != null)
        {
            mSpeechRecognizer.destroy();
        }
    }

    protected class SpeechRecognitionListener implements RecognitionListener
    {

        @Override
        public void onBeginningOfSpeech()
        {
            // speech input will be processed, so there is no need for count down anymore
            if (mIsCountDownOn)
            {
                mIsCountDownOn = false;
                mNoSpeechCountDown.cancel();
            }
            Log.d(TAG, "onBeginingOfSpeech"); //$NON-NLS-1$
        }

        @Override
        public void onBufferReceived(byte[] buffer)
        {
            String sTest = "";
        }

        @Override
        public void onEndOfSpeech()
        {
            Log.d("TESTING: SPEECH SERVICE", "onEndOfSpeech"); //$NON-NLS-1$
        }

        @Override
        public void onError(int error)
        {
            if (mIsCountDownOn)
            {
                mIsCountDownOn = false;
                mNoSpeechCountDown.cancel();
            }
            mIsListening = false;
            Message message = Message.obtain(null, MSG_RECOGNIZER_START_LISTENING);
            try
            {
                mServerMessenger.send(message);
            }
            catch (RemoteException e)
            {

            }
            Log.d(TAG, "error = " + error); //$NON-NLS-1$
        }

        @Override
        public void onEvent(int eventType, Bundle params)
        {

        }

        @Override
        public void onPartialResults(Bundle partialResults)
        {

        }

        @Override
        public void onReadyForSpeech(Bundle params)
        {
            if (Build.VERSION.SDK_INT >= 16);//Build.VERSION_CODES.JELLY_BEAN)
            {
                mIsCountDownOn = true;
                mNoSpeechCountDown.start();
                mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
            }
            Log.d("TESTING: SPEECH SERVICE", "onReadyForSpeech"); //$NON-NLS-1$
        }

        @Override
        public void onResults(Bundle results)
        {
            Log.d(TAG, "onResults"); //$NON-NLS-1$
            //Toast.makeText(VoiceRecogService.this, results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0), Toast.LENGTH_SHORT).show();
            Intent dialogIntent = new Intent(VoiceRecogService.this, MainActivity.class);
            String text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0);

            text = text.toLowerCase();

            //Toast.makeText(VoiceRecogService.this, text, Toast.LENGTH_SHORT).show();

            if(text.contains("vodafone") || text.contains("vodaphone")){

                if(text.contains("vodaphone")){
                    text = text.replaceAll("vodaphone", "");
                } else {
                    text = text.replaceAll("vodafone", "");
                }
                dialogIntent.putExtra("QUERY", text);
                dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                dialogIntent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                startActivity(dialogIntent);
            }

            dialogIntent.putExtra("QUERY", text);
            mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
        }

        @Override
        public void onRmsChanged(float rmsdB)
        {

        }


    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }
}
