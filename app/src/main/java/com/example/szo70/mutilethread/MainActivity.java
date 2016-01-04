package com.example.szo70.mutilethread;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    protected static final int DOWNLOAD_ERROR = 1;
    private static final int THREAD_ERROR = 2;
    public static final int DOWNLOAD_FINISH = 3;
    private EditText et_path;
    private EditText et_count;

    /**
     *
     * 存放进度条的布局
     */
    private LinearLayout ll_container;

    /**
     *
     * 进度条的集合
     */
    private List<ProgressBar> pbs;

    /**
     * android下的消息处理器,在主线程创建,才可以更新ui
     */
    private Handler handler = new Handler()

        {
        public void handlerMessage(Message msg) {
            switch (msg.what) {
                case DOWNLOAD_ERROR:
                    Toast.makeText(getApplicationContext(), "download error", Toast.LENGTH_LONG).show();
                    break;
                case THREAD_ERROR:
                    Toast.makeText(getApplicationContext(), "download error,please try again", Toast.LENGTH_LONG).show();
                    break;
                case DOWNLOAD_FINISH:
                    Toast.makeText(getApplicationContext(), "download finish", Toast.LENGTH_LONG).show();
                    break;
            }
        };
    };

    /**
     *
     * 线程的数量
     */
    private int threadCount = 3;

    /**
     *
     * 每个下载区块的大小
     */
    private long blocksize;

    /**
     *
     * 正在运行的线程的数量
     */
    private int runningThreadCount;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        et_path = (EditText) findViewById(R.id.et_path);
        et_count = (EditText) findViewById(R.id.et_count);
        ll_container = (LinearLayout) findViewById(R.id.ll_container);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    /**
     * 下载按钮的点击事件
     * @param view
     */
    public void downLoad(View view){
        // 下载文件的路径
        final String path = et_path.getText().toString().trim();
        if(TextUtils.isEmpty(path)) {
            Toast.makeText(this, "对不起下载路径不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String count = et_count.getText().toString().trim();
        if(TextUtils.isEmpty(path)) {
            Toast.makeText(this, "对不起,线程数量不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        threadCount = Integer.parseInt(count);
        //清空掉旧的进度条
        ll_container.removeAllViews();
        //在界面里面添加count个进度条
        pbs = new ArrayList<ProgressBar>();
        for (int j = 0; j<threadCount; j++){
            ProgressBar pb = (ProgressBar) View.inflate(this, R.layout.pb, null);
            ll_container.addView(pb);
            pbs.add(pb);
        }
        Toast.makeText(this, "开始下载", Toast.LENGTH_SHORT).show();
        new Thread() {
            public void run() {
                try {
                    URL url = new URL(path);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        long size = conn.getContentLength();     // 得到服务端返回的文件的大小
                        System.out.println("服务器文件的大小: " + size);
                        blocksize = size / threadCount;
                        // 1.首先在本地创建一个大小跟服务器一摸一样的空白文件.
                        File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
                        RandomAccessFile raf = new RandomAccessFile(file, "rw");
                        raf.setLength(size);
                        // 2.开启若干个子线程分别去下载对应的资源.
                        runningThreadCount = threadCount;
                        for(int i = 1; i<= threadCount;i++) {
                            long startIndex = (i - 1) * blocksize;
                            long endIndex = i * blocksize - 1;
                            if (i == threadCount) {
                                // 最后一个线程
                                endIndex = size - 1;
                            }
                            System.out.println("开启线程: " + i + "下载的位置: " + startIndex + "~" + endIndex);
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = Message.obtain();
                    msg.what = DOWNLOAD_ERROR;
                    handler.sendMessage(msg);
                }

            };
        }.start();


    }
    private class DOWNLOADThread extends Thread {

        private int threadId;
        private long startIndex;
        private long endIndex;
        private String path;

        public DOWNLOADThread(String path, int threadId, long startIndex, long endIndex) {
            this.path = path;
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public void run() {
            try {
                // 当前线程下载的总大小
                int total = 0;
                File positionFile = new File(Environment.getExternalStorageDirectory(),getFileName(path)+threadId + ".txt");
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // 接着从上一次的位置继续下载数据
                if (positionFile.exists() && positionFile.length() > 0) { // 判断是否有纪录
                    FileInputStream fis = new FileInputStream(positionFile);
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(fis));
                    // 获取当前线程上次下载的总大小是多少
                    String lasttotalstr = br.readLine();
                    int lastTotal = Integer.valueOf(lasttotalstr);
                    System.out.println("上次线程" + threadId + "下载的总大小: " + lastTotal);
                    startIndex += lastTotal;
                    total += lastTotal;  // 加上上次下载的总大小.
                    fis.close();
                    // 存数据库
                    // _id path threadid total
                }

                conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                conn.setConnectTimeout(5000);
                int code = conn.getResponseCode();
                System.out.println("code=" + code);
                InputStream is = conn.getInputStream();
                File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                // 指定文件开始写的位置
                raf.seek(startIndex);
                System.out.println("第" + threadId + "个线程:写文件的开始位置:" + String.valueOf(startIndex));
                int len = 0;
                byte[] buffer = new byte[1024];
                while ((len = is.read(buffer)) != -1) {
                    RandomAccessFile rf = new RandomAccessFile(positionFile, "rwd");
                    raf.write(buffer, 0, len);
                    total += len;
                    rf.write(String.valueOf(total).getBytes());
                    rf.close();
                    pbs.get(threadId-1).setProgress(total);
                }
                is.close();
                raf.close();

            } catch (Exception e) {
                e.printStackTrace();
                Message msg = Message.obtain();
                msg.what = THREAD_ERROR;
                handler.sendMessage(msg);
            } finally {
                // 只有所有的线程豆下载完毕后 才可以删除纪录文件.
                synchronized (MainActivity.class) {
                    System.out.println("线程" + threadId + "下载完毕了");
                    runningThreadCount--;
                    if (runningThreadCount < 1) {
                        System.out.println("所有的线程都工作完毕了.删除临时纪录的文件");
                        for (int i = 1; i <= threadCount; i++) {
                            File f = new File(Environment.getExternalStorageDirectory(),getFileName(path)+ i + ".txt");
                            System.out.println(f.delete());
                        }
                        Message msg = Message.obtain();
                        msg.what = DOWNLOAD_FINISH;
                        handler.sendMessage(msg);
                    }
                }
            }
        }

    }
    //http://192.168.1.100:8080/
    private String getFileName(String path) {
        int start = path.lastIndexOf("/")+1;
        return path.substring(start);
    }

}
