package unist.ivader.visimageclient;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CLIENT TIME";

    public RadioGroup processNumberGroup;
    public RadioButton process1, process10;
    public Button connectButton, resetButton;

    private Runnable networkRunnable = new NetworkRunnable();
    private Thread networkThread = new Thread(networkRunnable);
    private Runnable singlePortRunnable = new SinglePortRunnable();
    private Thread singlePortThread = new Thread(singlePortRunnable);
    private Runnable multiPortRunnable = new MultiPortRunnable();
    private Thread multiPortThread = new Thread(multiPortRunnable);

    String ipAddr = "127.0.0.1";
    int processNum = 1;
    int portNum = 8000;

    File[] file;
    List<String> savingData = new ArrayList<>(10);
    long entireTimeStart, entireTimeEnd;
    long[] communicationStart = new long[10];
    long[] communicationEnd = new long[10];

    private ImageView[] img = new ImageView[10];

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        processNumberGroup = findViewById(R.id.process_num_in);
        process1 = findViewById(R.id.process_num_1);
        process10 = findViewById(R.id.process_num_10);
        connectButton = findViewById(R.id.connectButton);
        resetButton = findViewById(R.id.resetButton);

        img[0] = findViewById(R.id.img01);
        img[1] = findViewById(R.id.img02);
        img[2] = findViewById(R.id.img03);
        img[3] = findViewById(R.id.img04);
        img[4] = findViewById(R.id.img05);
        img[5] = findViewById(R.id.img06);
        img[6] = findViewById(R.id.img07);
        img[7] = findViewById(R.id.img08);
        img[8] = findViewById(R.id.img09);
        img[9] = findViewById(R.id.img10);

        process1.toggle();

        processNumberGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i) {
                    case R.id.process_num_1:
                        processNum = 1;
                        break;
                    case R.id.process_num_10:
                        processNum = 10;
                        break;
                }
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "Click RESET after use.", Toast.LENGTH_SHORT).show();

                String path = getApplicationContext().getFilesDir().getPath() + "/";
                file = new File[10];

                Log.d(TAG, "Mode : Normal");

                for (int i = 0 ; i < 10 ; i++) {
                    file[i] = new File(path + "img" + i + ".jpg");
                    savingData.add(i, "");
                }
                Log.d(TAG, "File created.");

                networkThread.start();
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "RESET", Toast.LENGTH_SHORT).show();
                Intent restartIntent = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(getBaseContext().getPackageName());
                if (restartIntent != null) {
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                }
                startActivity(restartIntent);
            }
        });
    }

    @SuppressLint("HandlerLeak")
    Handler imageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //Log.d(TAG, "imageHandler run");
            int num = Integer.parseInt(msg.obj.toString());
            img[num].setImageBitmap(BitmapFactory.decodeFile(getApplicationContext().getFilesDir().getPath() + "/img" + num + ".jpg"));
        }
    };

    public void connectServer(final String ipAddr, final int portNum) {
        try {
            final Socket sock = new Socket(ipAddr, portNum);
            Log.d(TAG, "Main server connected.");

            final PrintWriter makeConnection = new PrintWriter(sock.getOutputStream());
            makeConnection.print("Connection set");
            makeConnection.flush();

            InputStream portInput = sock.getInputStream();
            int openPortRead;
            byte[] openPortReadBuffer = new byte[128];
            openPortRead = portInput.read(openPortReadBuffer);
            processNum = Integer.parseInt(new String(openPortReadBuffer, 0, openPortRead, UTF_8));
            Log.d(TAG, "Process number : " + processNum);

            portInput.close();
            makeConnection.close();
            sock.close();

            networkThread.interrupt();
            Log.d(TAG, "Starting parallel port...");
            if (processNum == 10) {
                multiPortThread.start();
            } else if (processNum == 1) {
                singlePortThread.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class NetworkRunnable extends Thread implements Runnable {
        @Override
        public void run() {
            super.run();
            connectServer(ipAddr, portNum);
        }
    }

    public class SinglePortRunnable extends Thread implements Runnable {
        @Override
        public void run() {
            super.run();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final ExecutorService executorService = Executors.newFixedThreadPool(processNum);

                    final Runnable singleRunnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                                StrictMode.setThreadPolicy(policy);

                                Log.d(TAG, "Waiting for server open...");
                                sleep(1500);

                                Socket parallelPort = new Socket(ipAddr, 9000);

                                entireTimeStart = System.currentTimeMillis();
                                for (int i = 0 ; i < 10 ; i++) {
                                    InputStream sizeInput = parallelPort.getInputStream();
                                    InputStream parallelInput = parallelPort.getInputStream();
                                    FileOutputStream fos = new FileOutputStream(file[i]);
                                    PrintWriter eof = new PrintWriter(parallelPort.getOutputStream(), true);

                                    //communicationStart[i] = System.currentTimeMillis();
                                    int sizeRead;
                                    byte[] sizeReadBuffer = new byte[128];
                                    sizeRead = sizeInput.read(sizeReadBuffer);
                                    int size = Integer.parseInt(new String(sizeReadBuffer, 0, sizeRead, UTF_8));
                                    Log.d(TAG, "size : " + size);

                                    eof.println("ready");

                                    byte[] contents = new byte[size];
                                    Log.d(TAG, "Getting files...");
                                    while (file[i].length() != size) {
                                        fos.write(contents, 0, parallelInput.read(contents));
                                    }
                                    fos.flush();
                                    //Log.d(TAG, "saved");

                                    //Log.d(TAG, "EOF...");
                                    eof.println("End");
                                    //Log.d(TAG, "send eof");
                                    communicationEnd[i] = System.currentTimeMillis();

                                    Log.d(TAG, "Communication time of " + i + " : " + (communicationEnd[i] - entireTimeStart) + "ms");

                                    imageHandler.sendMessage(Message.obtain(imageHandler, 1, i));
                                }

                                entireTimeEnd = System.currentTimeMillis();
                                //Log.d(TAG, "Entire communication time : " + (entireTimeEnd - entireTimeStart) + "ms");

                            } catch (InterruptedException | IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    executorService.execute(singleRunnable);
                }
            });
        }
    }


    public class MultiPortRunnable extends Thread implements Runnable {
        @Override
        public void run() {
            super.run();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final ExecutorService executorService = Executors.newFixedThreadPool(processNum);

                    for (int i = 0 ; i < processNum ; i++) {
                        final Runnable poolRunnable = new Runnable() {
                            @Override
                            public void run() {
                                int threadNumber = Integer.parseInt(Thread.currentThread().getName().substring(14)) - 1;
                                Log.d(TAG, "threadNumber : " + threadNumber);

                                try {
                                    Log.d(TAG, "Waiting for server open...");
                                    sleep(1500);

                                    Socket parallelPort = new Socket(ipAddr, 9000 + threadNumber);
                                    Log.d(TAG, (9000+threadNumber) + " connected.");

                                    InputStream sizeInput = parallelPort.getInputStream();
                                    InputStream parallelInput = parallelPort.getInputStream();
                                    FileOutputStream fos = new FileOutputStream(file[threadNumber]);
                                    PrintWriter eof = new PrintWriter(parallelPort.getOutputStream(), true);

                                    communicationStart[threadNumber] = System.currentTimeMillis();

                                    int sizeRead;
                                    byte[] sizeReadBuffer = new byte[128];
                                    sizeRead = sizeInput.read(sizeReadBuffer);
                                    int size = Integer.parseInt(new String(sizeReadBuffer, 0, sizeRead, UTF_8));
                                    Log.d(TAG, "size : " + size);

                                    eof.println("ready");

                                    byte[] contents = new byte[size];
                                    Log.d(TAG, "Getting files...");
                                    while (file[threadNumber].length() != size) {
                                        fos.write(contents, 0, parallelInput.read(contents));
                                    }
                                    communicationEnd[threadNumber] = System.currentTimeMillis();
                                    Log.d(TAG, "Communication time of " + threadNumber + " and size " + size + " : " + (communicationEnd[threadNumber] - communicationStart[threadNumber]) + "ms");

                                    imageHandler.sendMessage(Message.obtain(imageHandler, 1, threadNumber));

                                    eof.close();
                                    fos.close();
                                    sizeInput.close();
                                    parallelInput.close();
                                    parallelPort.close();
                                } catch (IOException | InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        executorService.execute(poolRunnable);
                    }

                    // 전송이 모두 완료될 때까지 대기
                    try {
                        do {
                            try {
                                if (!executorService.isShutdown()) {
                                    Log.d(TAG, "종료 대기 중...");
                                    executorService.shutdown();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                executorService.shutdownNow();
                            }
                        } while (!executorService.awaitTermination(100, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
