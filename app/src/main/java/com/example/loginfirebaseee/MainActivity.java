package com.example.loginfirebaseee;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.net.Socket;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText editTextInput;
    private DatabaseReference databaseInputData;
    private DatabaseReference databaseControl;
    private ListView listViewData;
    private List<InputData> listData;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String SERVER_IP = "192.168.164.168"; // Ganti dengan alamat IP server
    private static final int SERVER_PORT = 123;
    private Handler delayHandler = new Handler(Looper.getMainLooper());
    private TextView textViewStatusInternet;
    AppDatabase databaseLocal;
    DataDao dataDao;

    List<InputData> temporaryLocal;

    double latitude, longitude;
    int lock = 0;
    FirebaseAuth auth;
    Button button, buttonProfil, buttonReset;
    TextView textView;
    FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listViewData = findViewById(R.id.listView_Data);
        listData = new ArrayList<>();

        temporaryLocal = new ArrayList<>();

        databaseLocal = AppDatabase.getInstance(this);
        dataDao = databaseLocal.dataDao();


        editTextInput = findViewById(R.id.editTextInput);

        textViewStatusInternet = findViewById(R.id.textViewStatusInternet);

        //memanggil instansi dari firebase dan dimasukkan ke variabel Data
        databaseInputData = FirebaseDatabase.getInstance().getReference("Data");
        databaseControl = FirebaseDatabase.getInstance().getReference("Control");

        auth = FirebaseAuth.getInstance();
        button = findViewById(R.id.logout);
        buttonProfil = findViewById(R.id.buttonProfil);
//        buttonReset = findViewById(R.id.buttonReset);
        textView = findViewById(R.id.user_details);
        user = auth.getCurrentUser();

        // Memeriksa status koneksi internet
        if (isInternetConnected()) {
            textViewStatusInternet.setText("Koneksi Internet Tersedia");
        } else {
            textViewStatusInternet.setText("Tidak Ada Koneksi Internet");
        }


        //periksa ada akun yang login atau tidak, jika ada tetap pada halaman mainactivity atau jika tidak ada kembali ke halaman login
        if (user == null) {
            Intent intent = new Intent(getApplicationContext(), Login.class);
            startActivity(intent);
            finish();
        } else {
            textView.setText("Welcome " + user.getEmail());
        }

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                builder.setTitle("Konfirmasi Logout");
                builder.setMessage("Apakah Anda yakin ingin logout?");

                builder.setPositiveButton("Ya", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isInternetConnected()==true){
                            FirebaseAuth.getInstance().signOut();
                            Intent intent = new Intent(getApplicationContext(), Login.class);
                            startActivity(intent);
                            finish();
                            Toast.makeText(MainActivity.this, "Berhasil logout", Toast.LENGTH_SHORT).show();
                        }else {
                            Toast.makeText(MainActivity.this, "Gagal logout, tidak ada internet", Toast.LENGTH_SHORT).show();
                        }

                    }
                });

                builder.setNegativeButton("Batal", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Tutup dialog jika pengguna memilih "Batal"
                        dialog.dismiss();
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        buttonProfil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent profil = new Intent(getApplicationContext(), Profil.class);
                startActivity(profil);
//                finish();
            }
        });

        // setup untuk mendapatkan data lokasi
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();

                // Lakukan sesuatu dengan data latitude dan longitude di sini
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        // Meminta izin lokasi pada saat aplikasi dibuka jika belum diberikan
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Meminta pembaruan lokasi dari layanan GPS
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

            // Atau meminta pembaruan lokasi dari layanan jaringan (NETWORK_PROVIDER)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Hentikan pembaruan lokasi saat keluar aplikasi
        locationManager.removeUpdates(locationListener);
    }

    private class LoadDataAsyncTask extends AsyncTask<Void, Void, List<InputData>> {
        @Override
        protected List<InputData> doInBackground(Void... voids) {
            return dataDao.getAllData();
        }

        @Override
        protected void onPostExecute(List<InputData> inputData) {
            temporaryLocal.clear();
            temporaryLocal = inputData;
            setDataListView(temporaryLocal);
        }
    }

    private class InsertDataAsyncTask extends AsyncTask<InputData, Void, Void> {
        @Override
        protected Void doInBackground(InputData... inputData) {
            dataDao.insert(inputData[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // Setelah data berhasil diinsert, kita bisa melakukan operasi lainnya
            editTextInput.setText("");
            if (isInternetConnected() == false){
                updateListView();
            }

        }
    }

    public void buttonAdd(View view) {
        String data = editTextInput.getText().toString();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userId = user.getEmail();

        if (!TextUtils.isEmpty(data)){
            String id = UUID.randomUUID().toString();
            InputData inputData = new InputData(id,data,userId,latitude,longitude);

            if(lock == 0){
                new InsertDataAsyncTask().execute(inputData);

                databaseInputData.child(id).setValue(inputData)
                        .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                editTextInput.setText("");
                                Toast.makeText(MainActivity.this,"Data berhasil ditambahkan",Toast.LENGTH_SHORT).show();
                            }
                        });

                // mengirim data ke alat melalui socket jika tidak ada internet
                if (isInternetConnected() == false) {
                    Executors.newSingleThreadExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                //mengirim data melalui socket
                                Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                                OutputStream outputStream = socket.getOutputStream();
                                Log.i("SOCKET", "Mencoba kirim data");
                                String send_data = data + "\n";
                                outputStream.write(send_data.getBytes());
                                outputStream.close();
                                Log.i("SOCKET", "BERHASIL KIRIM DATA");
                                //alat berjalan
                                lock = 1;
                                socket.close();
                                int delay_time = Integer.parseInt(data) * 5000;
                                delayHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        //siap input
                                        lock = 0;
                                    }
                                }, delay_time);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } else {
                    //jika ada internet data dikirim ke firebase
                    databaseControl.child("data").setValue(Integer.parseInt(data));
                }
            } else {
                Toast.makeText(this,"Tidak dapat menambah data karena alat masih berjalan",Toast.LENGTH_SHORT).show();
            }
        }else {
            Toast.makeText(this,"Data wajib diisi",Toast.LENGTH_SHORT).show();
        }
    }

//    public void resetDatabase(View view) {
//        if (isInternetConnected() == true) {
//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle("Konfirmasi Reset Data");
//            builder.setMessage("Apakah Anda yakin ingin mereset data? Semua data akan dihapus.");
//
//            builder.setPositiveButton("Ya", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    // Jalankan aksi reset data di sini
//                    Executors.newSingleThreadExecutor().execute(new Runnable() {
//                        @Override
//                        public void run() {
//                            // Operasi hapus data dalam database di sini
//                            dataDao.deleteTable();
//                        }
//                    });
//                    databaseInputData.removeValue();
//                    Toast.makeText(MainActivity.this, "Data berhasil direset", Toast.LENGTH_SHORT).show();
//                }
//            });
//
//            builder.setNegativeButton("Batal", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    // Tutup dialog jika pengguna memilih "Batal"
//                    dialog.dismiss();
//                }
//            });
//
//            AlertDialog dialog = builder.create();
//            dialog.show();
//        } else {
//            // alasan tidak dapat reset data tanpa internet karena akan menyebabkan ketidaksinkronan data firebase dengan local
//            Toast.makeText(MainActivity.this, "Tidak dapat reset data tanpa internet", Toast.LENGTH_LONG).show();
//        }
//    }

    // fungsi untuk sinkronasi data firebase dan lokal
    public void buttonSync(View view) {
        if (isInternetConnected() == true) {
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    // Operasi database di sini
                    temporaryLocal.clear();
                    temporaryLocal = dataDao.getAllData();
                    Log.i("TOTAL", String.valueOf(temporaryLocal.size()));
                    // sinkronasi firebase dengan room (room menjadi patokan)
                    for (InputData local : temporaryLocal) {
                        boolean found = false;
                        for (InputData fb : listData) {
                            if (local.getData_Id().equals(fb.getData_Id())) {
                                found = true;
                                break;
                            }
                        }
                        if (found == false) {
                            Log.i("SYNC", "ID : " + local.getData_Id() + " tidak ada pada firebase");
                            databaseInputData.child(local.getData_Id()).setValue(local);
                        }
                    }
                    // sinkronasi room dengan firebase (firebase menjadi patokan)
                    for(InputData fb : listData){
                        boolean found = false;
                        for(InputData local : temporaryLocal){
                            if(fb.getData_Id().equals(local.getData_Id())){
                                found = true;
                                break;
                            }
                        }
                        if(found == false){
                            Log.i("SYNC", "ID : " + fb.getData_Id() + " tidak ada pada room");
                            dataDao.insert(fb);
                        }
                    }
                }
            });
            Toast.makeText(MainActivity.this, "Sinkronasi data berhasil", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "Sinkronasi data gagal dikarenakan tidak terdapat koneksi internet", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        if (isInternetConnected() == true) {
            databaseInputData.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    listData.clear();
                    //perulangan untuk menampilkan semua data dari firebase
                    for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                        InputData inputData = postSnapshot.getValue(InputData.class);
                        listData.add(inputData);
                    }
                    //menampilkan list yang berisi data dari firebase ke dalam listview
                    setDataListView(listData);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.i("DB_ERR", error.getMessage());
                }
            });
            databaseControl.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    //update data lock pada firebase
                    lock = snapshot.child("lock").getValue(Integer.class) != null ? snapshot.child("lock").getValue(Integer.class) : 0;
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        } else {
            new LoadDataAsyncTask().execute();
        }
    }

    public void setDataListView(List<InputData> listData){
        Collections.sort(listData, new InputDataTimestampComparator());
        listview_inputdata inputDataList_Adapter = new listview_inputdata(MainActivity.this, listData);
        listViewData.setAdapter(inputDataList_Adapter);
    }

    public void updateListView() {
        new LoadDataAsyncTask().execute();
    }

    // Fungsi untuk memeriksa koneksi internet
    private boolean isInternetConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }
}