package net.kaicong.ipcam.device.seeworld;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import net.kaicong.ipcam.R;
import net.kaicong.ipcam.bean.GetCameraModel;
import net.kaicong.ipcam.device.CgiControlParams;
import net.kaicong.ipcam.device.cgi.CgiUtils;
import net.kaicong.ipcam.device.mjpeg.MjpegInputStream;
import net.kaicong.ipcam.device.mjpeg.MjpegView;
import net.kaicong.ipcam.device.record.Record;
import net.kaicong.ipcam.utils.LogUtil;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONObject;

import java.net.URI;

/**
 * Created by LingYan on 14-12-26.
 */
public class SeeSip1211DeviceActivity extends BaseSeeWorldActivity {

    private MjpegView monitor;
    private String URL;

    private String ip;
    private int port;
    private String account;
    private String password;

    private boolean isShowProgressBar = true;
    private boolean suspending = false;
    private CgiControlParams cgiControlParams;

    private MjpegInputStream stream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentModelId = GetCameraModel.CAMERA_MODEL_SIP1211;
        setContentView(R.layout.activity_see_sip1601_device);

        ip = getIntent().getStringExtra("ip");
        port = getIntent().getIntExtra("port", 0);
        account = getIntent().getStringExtra("account");
        password = getIntent().getStringExtra("password");

        StringBuilder sb = new StringBuilder();
        sb.append("http://");
        sb.append(ip);
        sb.append(":");
        sb.append(port);
        sb.append("/cgi-bin/videostream.cgi?user=");
        sb.append(account);
        sb.append("&pwd=");
        sb.append(password);
        URL = sb.toString();
        monitor = (MjpegView) findViewById(R.id.monitor);
        monitor.setOnTouchListener(this);

        initCommonView();

        cgiControlParams = new CgiControlParams(account, password);
        String urlGetResolution = "http://" + ip + ":" + port + "/cgi-bin/get_real_status.cgi";
        cgiControlParams.doCgiGet(urlGetResolution, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int i, Header[] headers, byte[] bytes) {
                JSONObject object = CgiUtils.convertCgiStr2Json(new String(bytes));
                if (object != null) {
                    mVideoWidth = object.optInt("ret_realstatus_videoW");
                    mVideoHeight = object.optInt("ret_realstatus_videoH");
                    LogUtil.d("chu", "widht=" + mVideoWidth);
                    new DoRead().execute(URL);
                } else {
                    Toast.makeText(SeeSip1211DeviceActivity.this, getString(R.string.connstus_connection_failed), Toast.LENGTH_LONG).show();
//                    SeeSip1211DeviceActivity.this.finish();
                }
            }

            @Override
            public void onFailure(int i, Header[] headers, byte[] bytes, Throwable throwable) {
                Toast.makeText(SeeSip1211DeviceActivity.this, getString(R.string.connstus_connection_failed), Toast.LENGTH_LONG).show();
//                SeeSip1211DeviceActivity.this.finish();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isShowProgressBar) {
            LogUtil.d("chu", "--hasFocus--");
            animationImageView.setVisibility(View.VISIBLE);
            progressBarText.setVisibility(View.VISIBLE);
            animationDrawable.start();
        } else if (!isShowProgressBar) {
            LogUtil.d("chu", "--loseFocus--");
            animationDrawable.stop();
            animationImageView.setVisibility(View.GONE);
            progressBarText.setVisibility(View.GONE);
        }
    }

    @Override
    public void quit(boolean isFinish) {
        if (monitor != null) {
            if (monitor.isStreaming()) {
                monitor.stopPlayback();
                suspending = true;
            }
        }
        if (isFinish) {
            finish();
        }
    }

    @Override
    public void share() {

    }

    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {

        protected MjpegInputStream doInBackground(String... url) {
            //TODO: if camera has authentication deal with it and don't just not work
            HttpResponse res = null;
            /**
             * add basic authentication
             */
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(ip, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(account, password));
            DefaultHttpClient httpclient = new DefaultHttpClient();
            httpclient.setCredentialsProvider(credsProvider);
            HttpParams httpParams = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 5 * 1000);
            HttpConnectionParams.setSoTimeout(httpParams, 5 * 1000);
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                if (res.getStatusLine().getStatusCode() == 401) {
                    return null;
                }
                LogUtil.d("chu", "status code=" + res.getStatusLine().getStatusCode());
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (Exception e) {
                LogUtil.d("chu", "ioexception" + e.toString());
            }
            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            if (result == null) {
                Toast.makeText(SeeSip1211DeviceActivity.this, getString(R.string.connstus_connection_failed), Toast.LENGTH_LONG).show();
//                SeeSip1211DeviceActivity.this.finish();
            } else {
                monitor.setVideoRadio((mVideoWidth * 1.0f) / mVideoHeight);
                doSetLayoutOnOrientation(mVideoWidth, mVideoHeight);
                record = new Record(SeeSip1211DeviceActivity.this, mVideoWidth, mVideoHeight, summary.deviceId);
                monitor.setSource(result);
                stream = result;
                result.setSkip(1);
                isShowProgressBar = false;
                onWindowFocusChanged(false);
            }
        }
    }

    public void onResume() {
        super.onResume();
        if (monitor != null && !isRewardSuccess) {
            if (suspending) {
                new DoRead().execute(URL);
                suspending = false;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (monitor != null) {
            if (monitor.isStreaming()) {
                monitor.stopPlayback();
                suspending = true;
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        doSetLayoutOnOrientation(mVideoWidth, mVideoHeight);
        umengShareUtils.dismissShare();
    }

    @Override
    public Bitmap getBitmap() {
        if (stream != null) {
            try {
                return stream.readMjpegFrame();
            } catch (Exception e) {

            }
        }
        return null;
    }

    @Override
    public void startRecording() {
        record.startRecording(monitor);
    }

    @Override
    public void stopRecording() {
        record.stopRecording(monitor);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_CODE_SHARE_BACK) {
            resumePlay();
            postShareComment();
        }
    }

    @Override
    public void resumePlay() {
        if (monitor != null) {
            if (suspending) {
                new DoRead().execute(URL);
                suspending = false;
            }
        }
    }

    @Override
    public void changePlay(String ip, int port, String account, String password, String UID) {
        //暂停播放
        if (monitor != null) {
            if (monitor.isStreaming()) {
                monitor.stopPlayback();
                suspending = true;
            }
        }
        //重新设置参数
        StringBuilder sb = new StringBuilder();
        sb.append("http://");
        sb.append(ip);
        sb.append(":");
        sb.append(port);
        sb.append("/cgi-bin/videostream.cgi?user=");
        sb.append(account);
        sb.append("&pwd=");
        sb.append(password);
        URL = sb.toString();
        //重新播放
        if (monitor != null) {
            if (suspending) {
                new DoRead().execute(URL);
                suspending = false;
            }
        }
    }

}
