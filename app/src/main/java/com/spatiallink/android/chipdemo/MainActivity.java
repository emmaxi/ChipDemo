package com.spatiallink.android.chipdemo;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.spatiallink.android.chipdemo.client.ClientActivity;
import com.spatiallink.android.chipdemo.databinding.ActivityMainBinding;
import com.spatiallink.android.chipdemo.server.ServerActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        binding.launchServerButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this,
                ServerActivity.class)));
        binding.launchClientButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this,
                ClientActivity.class)));
    }
}
