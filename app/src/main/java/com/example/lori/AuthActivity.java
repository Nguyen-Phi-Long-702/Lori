package com.example.lori;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lori.utils.ThemeHelper;

//Màn hình chứa hai Fragment Đăng nhập / Đăng ký (điều hướng bằng nav_auth_graph)
public class AuthActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Áp dụng giao diện tối đã lưu, giống các activity khác
        ThemeHelper.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        View root = findViewById(R.id.authRoot);
        //Chừa chỗ cho thanh hệ thống và cả bàn phím để ô nhập không bị che
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    //Đăng nhập/đăng ký thành công: vào màn hình chính và xóa hết các màn trước để Back không quay lại màn Đăng nhập
    public void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}